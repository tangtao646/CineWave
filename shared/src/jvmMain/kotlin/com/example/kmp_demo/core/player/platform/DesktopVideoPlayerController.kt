package com.example.kmp_demo.core.player.platform

import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

/**
 * Desktop 视频播放器 — 基于 VLCJ (libvlc) 的 CallbackVideoSurface 模式。
 *
 * 使用 VLCJ 的 [CallbackVideoSurface] 获取视频帧的 RGBA 数据，
 * 通过 Compose 的 Canvas 手动渲染，避免 macOS 上 AWT/Swing 的
 * `caopengllayer` 视频输出兼容性问题。
 *
 * ## 架构说明
 * - 视频渲染：VLCJ 回调返回 RGBA ByteBuffer → Compose Canvas 绘制
 * - 播放控制：通过 [IPlayerController] 接口与 commonMain 解耦
 * - 状态同步：VLCJ 的 [MediaPlayerEventAdapter] 监听 + 协程轮询位置
 * - 生命周期：DisposableEffect 管理资源创建与释放
 *
 * ## 前置条件
 * - 需要用户安装 VLC 播放器
 *   - macOS: `brew install vlc`
 *   - Linux: `sudo apt install vlc`
 *   - Windows: 从 https://www.videolan.org/vlc/ 下载安装
 *
 * @see IPlayerController
 * @see CallbackVideoSurface
 */
class DesktopVideoPlayerController : IPlayerController {

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 250L
    }

    // ==================== VLCJ 核心组件 ====================

    /** VLCJ 媒体播放器工厂 */
    private val mediaPlayerFactory: MediaPlayerFactory = MediaPlayerFactory(
        "--no-video-title-show",
        "--quiet",
        "--no-snapshot-preview",
    )

    /** VLCJ 嵌入式媒体播放器 */
    val mediaPlayer: EmbeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()

    /** 最新的视频帧 RGBA 数据 */
    @Volatile
    var latestVideoFrame: BufferedImage? = null
        internal set

    /** 视频帧宽度 */
    @Volatile
    var videoWidth: Int = 0
        private set

    /** 视频帧高度 */
    @Volatile
    var videoHeight: Int = 0
        private set

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

    init {
        // 使用 CallbackVideoSurface 获取视频帧 RGBA 数据
        // 避免 macOS 上 AWT Canvas 的 caopengllayer 兼容性问题
        val surfaceAdapter: VideoSurfaceAdapter = VideoSurfaceAdapters.getVideoSurfaceAdapter()
        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                videoWidth = sourceWidth
                videoHeight = sourceHeight
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }

            override fun allocatedBuffers(buffers: Array<ByteBuffer>) {
                // 不需要额外处理
            }
        }
        val callbackVideoSurface = CallbackVideoSurface(
            bufferFormatCallback,
            RenderCallbackImpl(this),
            true,
            surfaceAdapter
        )
        mediaPlayer.videoSurface().set(callbackVideoSurface)

        // 注册 VLCJ 事件监听器
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _playbackState.value = VideoPlaybackState.PLAYING
                startPositionPolling()
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _playbackState.value = VideoPlaybackState.PAUSED
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _playbackState.value = VideoPlaybackState.IDLE
                stopPositionPolling()
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _playbackState.value = VideoPlaybackState.ENDED
                stopPositionPolling()
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _playbackState.value = VideoPlaybackState.ERROR
                stopPositionPolling()
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                if (_playbackState.value != VideoPlaybackState.PLAYING &&
                    _playbackState.value != VideoPlaybackState.PAUSED
                ) {
                    _playbackState.value = VideoPlaybackState.BUFFERING
                }
                _bufferedPercent.value = (newCache * 100).toInt().coerceIn(0, 100)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _duration.value = newLength
            }
        })
    }

    // ==================== 位置轮询 ====================

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                val vlcMediaPlayer = mediaPlayer
                if (vlcMediaPlayer.status().isPlaying) {
                    _currentPosition.value = vlcMediaPlayer.status().time()
                    _duration.value = vlcMediaPlayer.status().length()
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    // ==================== IPlayerController 实现 ====================

    override suspend fun open(url: String, headers: Map<String, String>?) {
        _playbackState.value = VideoPlaybackState.BUFFERING
        _currentPosition.value = 0L
        latestVideoFrame = null
        mediaPlayer.media().play(url)
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
        mediaPlayer.controls().setTime(positionMs)
    }

    override suspend fun seekForward(seconds: Long) {
        val currentTime = mediaPlayer.status().time()
        mediaPlayer.controls().setTime(currentTime + seconds * 1000)
    }

    override suspend fun seekBackward(seconds: Long) {
        val currentTime = mediaPlayer.status().time()
        mediaPlayer.controls().setTime((currentTime - seconds * 1000).coerceAtLeast(0L))
    }

    override suspend fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        mediaPlayer.audio().setVolume((_volume.value * 100).toInt())
    }

    override suspend fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun release() {
        stopPositionPolling()
        scope.cancel()
        mediaPlayer.release()
        mediaPlayerFactory.release()
        latestVideoFrame = null
    }
}

/**
 * VLCJ RenderCallback 实现 — 将视频帧从 ByteBuffer 转换为 BufferedImage。
 *
 * 作为独立类避免 lambda 类型推断问题。
 */
private class RenderCallbackImpl(
    private val controller: DesktopVideoPlayerController
) : RenderCallback {

    override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<ByteBuffer>, bufferFormat: BufferFormat) {
        try {
            val width = bufferFormat.width
            val height = bufferFormat.height
            if (width <= 0 || height <= 0) return

            val nativeBuffer = nativeBuffers.firstOrNull() ?: return

            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val pixels = IntArray(width * height)

            nativeBuffer.rewind()
            for (i in pixels.indices) {
                val r = nativeBuffer.get().toInt() and 0xFF
                val g = nativeBuffer.get().toInt() and 0xFF
                val b = nativeBuffer.get().toInt() and 0xFF
                val a = nativeBuffer.get().toInt() and 0xFF
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }

            image.setRGB(0, 0, width, height, pixels, 0, width)
            controller.latestVideoFrame = image
        } catch (_: Exception) {
            // 帧渲染异常不崩主流程
        }
    }
}
