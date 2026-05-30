package com.example.kmp_demo.core.player.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.example.kmp_demo.core.player.cache.CacheProxyServer
import com.example.kmp_demo.core.player.cache.DebugLog
import com.example.kmp_demo.core.player.domain.FullscreenController
import com.example.kmp_demo.core.player.domain.IVideoPlayerController
import com.example.kmp_demo.core.player.domain.PlayerError
import com.example.kmp_demo.core.player.domain.PlayerErrorType
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

/**
 * Desktop 视频播放器 — 基于 VLCJ (libvlc) 的 CallbackVideoSurface 渲染。
 * * 优化版说明：
 * 1. 修复了 VLC 底层 playing() 触发过早导致 BUFFERING 被提前覆盖的问题。
 * 2. 建立了状态自愈防抖，收敛了 open/seek/buffering 对状态的竞争。
 */
class DesktopVideoPlayerController(
    private val mediaPlayerFactory: MediaPlayerFactory,
    private val proxyServer: CacheProxyServer? = null,
    private val fullscreenController: FullscreenController? = null
) : IVideoPlayerController {


    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 250L
        /**
         * seek 后等待 VLC buffering 事件的超时时间。
         * 如果在此时间内 VLC 没有触发 buffering 事件（说明数据已缓存，seek 瞬间完成），
         * 则自动将状态恢复为 PLAYING。
         */
        private const val SEEK_BUFFER_TIMEOUT_MS = 500L
        /**
         * 打开媒体后的缓冲超时时间（毫秒）。
         * 如果在此时间内 VLC 没有成功播放（即 buffering 未达到 100%），
         * 则判定为连接超时错误，触发错误弹框。
         * 设置为 15 秒，给慢速网络留出足够时间。
         */
        private const val OPEN_BUFFER_TIMEOUT_MS = 15_000L
    }

    // ==================== VLCJ 核心组件 ====================

    /** VLCJ 嵌入式媒体播放器 */
    val mediaPlayer: EmbeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()

    /** 视频帧状态流，供 Compose UI 订阅并渲染 */
    private val _videoFrame = MutableStateFlow<ImageBitmap?>(null)
    val videoFrame: StateFlow<ImageBitmap?> = _videoFrame.asStateFlow()

    /** 临时缓存 BufferedImage，用于像素转换 */
    @Volatile
    private var bufferedImage: BufferedImage? = null

    /** 状态自愈核心锁：VLC 核心引擎是否真正处于就绪且应当播放的状态 */
    private var isVlcPlayingReady = false

    /** 标记 Seek 缓冲是否已被接管 */
    private var seekBufferingTriggered = false

    init {
        // 配置回调视频表面
        val videoSurface = CallbackVideoSurface(
            DesktopBufferFormatCallback(),
            DesktopRenderCallback(),
            true, // lockVideoSurface
            null  // videoSurfaceAdapter
        )
        mediaPlayer.videoSurface().set(videoSurface)

        // 注册优化后的 VLCJ 事件监听器
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun mediaChanged(mediaPlayer: MediaPlayer,media: MediaRef) {
                // 如果已经处于 ERROR 状态，忽略 mediaChanged 事件
                if (_playbackState.value == VideoPlaybackState.ERROR) {
                    return
                }
                isVlcPlayingReady = false
                _playbackState.value = VideoPlaybackState.BUFFERING
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                // 如果已经处于 ERROR 状态，忽略 playing 事件
                // VLCJ 在无效链接上重试时可能会误触发 playing 事件
                if (_playbackState.value == VideoPlaybackState.ERROR) {
                    return
                }
                isVlcPlayingReady = true
                // 只有当进度缓冲拉满，或者确认不在发生卡顿阻断时，才切换为 PLAYING
                if (_bufferedPercent.value >= 100 || mediaPlayer.status().isPlaying) {
                    _playbackState.value = VideoPlaybackState.PLAYING
                }
                startPositionPolling()
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _playbackState.value = VideoPlaybackState.PAUSED
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                isVlcPlayingReady = false
                _playbackState.value = VideoPlaybackState.IDLE
                stopPositionPolling()
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                isVlcPlayingReady = false
                _playbackState.value = VideoPlaybackState.ENDED
                stopPositionPolling()
            }

            override fun error(mediaPlayer: MediaPlayer) {
                isVlcPlayingReady = false
                _playbackState.value = VideoPlaybackState.ERROR
                _playerError.value = PlayerError(
                    type = PlayerErrorType.UNKNOWN,
                    message = "视频加载失败",
                    detail = "VLC 无法加载此媒体资源，可能链接已失效或格式不支持",
                    retryable = true,
                )
                stopPositionPolling()
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                // 如果已经处于 ERROR 状态，忽略后续 buffering 事件
                // 防止 VLCJ 在无效链接上无限重试时覆盖超时检测设置的 ERROR 状态
                if (_playbackState.value == VideoPlaybackState.ERROR) {
                    return
                }

                seekBufferingTriggered = true

                // 将 0.0 ~ 1.0 归一化为 0 ~ 100 的整数进度
                val percent = newCache.toInt().coerceIn(0, 100)
                _bufferedPercent.value = percent

                if (percent < 100) {
                    // 只要未缓冲完，且当前不是 BUFFERING，则立即展出 Loading 圈
                    if (_playbackState.value != VideoPlaybackState.BUFFERING) {
                        _playbackState.value = VideoPlaybackState.BUFFERING
                    }
                } else {
                    // 缓冲达到 100，结合底层核心就绪状态，安全恢复 PLAYING
                    if (isVlcPlayingReady || mediaPlayer.status().isPlaying) {
                        _playbackState.value = VideoPlaybackState.PLAYING
                    }
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0) {
                    _duration.value = newLength
                }
            }
        })
    }

    // ==================== 渲染回调实现 ====================

    private inner class DesktopBufferFormatCallback : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            bufferedImage = BufferedImage(
                sourceWidth,
                sourceHeight,
                BufferedImage.TYPE_INT_ARGB_PRE
            )
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
            // 缓冲区分配完成
        }
    }

    private inner class DesktopRenderCallback : RenderCallback {
        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<out ByteBuffer>,
            bufferFormat: BufferFormat
        ) {
            val img = bufferedImage ?: return
            val buffer = nativeBuffers[0]

            val pixelData = (img.raster.dataBuffer as java.awt.image.DataBufferInt).data
            buffer.asIntBuffer().get(pixelData)

            _videoFrame.value = img.toComposeImageBitmap()
        }
    }

    // ==================== 状态管理 ====================

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var positionPollingJob: Job? = null

    private val _playbackState = MutableStateFlow(VideoPlaybackState.IDLE)
    override val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _bufferedPercent = MutableStateFlow(0)
    override val bufferedPercent: StateFlow<Int> = _bufferedPercent.asStateFlow()

    private val _playerError = MutableStateFlow<PlayerError?>(null)
    override val playerError: StateFlow<PlayerError?> = _playerError.asStateFlow()

    // ==================== 位置轮询 ====================

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                val vlcMediaPlayer = mediaPlayer
                try {
                    if (vlcMediaPlayer.status().isPlaying) {
                        _currentPosition.value = vlcMediaPlayer.status().time()
                        val len = vlcMediaPlayer.status().length()
                        if (len > 0) {
                            _duration.value = len
                        }
                    }
                } catch (_: Exception) {
                    // 状态临界期保护
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    // ==================== Seek 缓冲处理 ====================

    private fun performSeek(targetTimeMs: Long) {
        seekBufferingTriggered = false
        _playbackState.value = VideoPlaybackState.BUFFERING
        mediaPlayer.controls().setTime(targetTimeMs)

        // 启动超时边界阀：如果 VLC 没有抛出 buffering 变更，说明直接命中了本地代理缓存，瞬间完成跳转
        scope.launch {
            delay(SEEK_BUFFER_TIMEOUT_MS)
            if (!seekBufferingTriggered && _playbackState.value == VideoPlaybackState.BUFFERING) {
                if (mediaPlayer.status().isPlaying || isVlcPlayingReady) {
                    _playbackState.value = VideoPlaybackState.PLAYING
                }
            }
        }
    }

    // ==================== IPlayerController 实现 ====================

    override suspend fun open(url: String, headers: Map<String, String>?) {
        DebugLog.d("DesktopVideoPlayerController", "open() called, url=$url")
        isVlcPlayingReady = false
        _playbackState.value = VideoPlaybackState.BUFFERING
        _currentPosition.value = 0L
        _bufferedPercent.value = 0
        _playerError.value = null  // 清除之前的错误

        DebugLog.d("DesktopVideoPlayerController", "Playing URL: $url")
        mediaPlayer.media().play(url)
        DebugLog.d("DesktopVideoPlayerController", "mediaPlayer.media().play() returned")

        // ========== 缓冲超时检测 ==========
        // VLCJ 对于无效链接（如不存在的服务器）不会触发 error() 事件，
        // 而是无限重试连接，导致播放器永远卡在 BUFFERING 状态。
        // 这里启动一个超时协程：如果在 OPEN_BUFFER_TIMEOUT_MS 内
        // 没有进入 PLAYING/ERROR/ENDED 状态，则自动判定为连接超时错误。
        scope.launch {
            delay(OPEN_BUFFER_TIMEOUT_MS)
            val currentState = _playbackState.value
            if (currentState == VideoPlaybackState.BUFFERING) {
                DebugLog.d("DesktopVideoPlayerController", "open() timeout after ${OPEN_BUFFER_TIMEOUT_MS}ms, transitioning to ERROR")
                _playbackState.value = VideoPlaybackState.ERROR
                _playerError.value = PlayerError(
                    type = PlayerErrorType.TIMEOUT,
                    message = "连接超时，请稍后重试",
                    detail = "播放器在 ${OPEN_BUFFER_TIMEOUT_MS / 1000} 秒内未能加载视频，可能链接已失效或网络不可达",
                    retryable = true,
                )
                stopPositionPolling()
            }
        }
    }

    override suspend fun play() {
        mediaPlayer.controls().play()
    }

    override suspend fun pause() {
        mediaPlayer.controls().pause()
    }

    override suspend fun togglePlayPause() {
        if (mediaPlayer.status().isPlaying) {
            mediaPlayer.controls().pause()
        } else {
            mediaPlayer.controls().play()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        performSeek(positionMs)
    }

    override suspend fun seekForward(seconds: Long) {
        val currentTime = mediaPlayer.status().time()
        performSeek(currentTime + seconds * 1000)
    }

    override suspend fun seekBackward(seconds: Long) {
        val currentTime = mediaPlayer.status().time()
        performSeek((currentTime - seconds * 1000).coerceAtLeast(0L))
    }

    override suspend fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        mediaPlayer.audio().setVolume((_volume.value * 100).toInt())
    }



    override suspend fun setFullscreen(isFullScreen: Boolean) {
        _isFullScreen.value = isFullScreen
        if (isFullScreen) {
            fullscreenController?.enterFullscreen()
        } else {
            fullscreenController?.exitFullscreen()
        }
    }

    override fun release() {
        stopPositionPolling()

        // 停止代理服务器（使用 NonCancellable 确保安全执行）
        runBlocking(NonCancellable) {
            try {
                proxyServer?.stop()
            } catch (_: Exception) { }
        }

        scope.cancel()

        // 安全异步释放 VLCJ 资源
        Thread {
            try {
                if (mediaPlayer.status().isPlaying) {
                    mediaPlayer.controls().stop()
                }
            } catch (_: Exception) {}

            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            try {
                mediaPlayer.release()
            } catch (_: Exception) {}
        }.apply {
            isDaemon = true
            name = "vlc-release-thread"
            start()
            try {
                join(2000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
