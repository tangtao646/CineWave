package com.example.kmp_demo.core.player.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

/**
 * Desktop 视频播放器 — 基于 VLCJ (libvlc) 的 CallbackVideoSurface 渲染。
 *
 * ## 为什么在 macOS 上使用 CallbackVideoSurface？
 * 1. **解决窗口脱离问题**：macOS 上的 AWT 不支持将 VLC 直接渲染到 Canvas，默认使用窗口叠加（Window Overlay），
 *    会导致视频窗口脱离 Compose 窗口或始终置顶遮挡 UI。
 * 2. **Compose 完美集成**：通过帧回调获取像素数据并转换为 [ImageBitmap]，使视频像普通 Compose 组件一样渲染，
 *    支持在视频上方放置任何 Compose UI（如控制条、字幕）。
 * 3. **硬件加速**：虽然是通过回调获取帧，但 VLC 内部依然可以使用硬件解码。
 *
 * ## 渲染流程
 * ```
 * VLC 解码帧 → CallbackVideoSurface 回调 → BufferedImage → ImageBitmap → Compose Image
 * ```
 *
 * @see IPlayerController
 * @see CallbackVideoSurface
 */
class DesktopVideoPlayerController(
    private val mediaPlayerFactory: MediaPlayerFactory
) : IPlayerController {

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 250L
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

    init {
        // 配置回调视频表面
        val videoSurface = CallbackVideoSurface(
            DesktopBufferFormatCallback(),
            DesktopRenderCallback(),
            true, // lockVideoSurface
            null  // videoSurfaceAdapter
        )
        mediaPlayer.videoSurface().set(videoSurface)

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

    // ==================== 渲染回调实现 ====================

    private inner class DesktopBufferFormatCallback : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            // 使用 RV32 (ARGB) 格式，方便转换为 BufferedImage
            // 立即初始化 BufferedImage，确保渲染开始时它已经就绪
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

            // 将像素数据从 Native Buffer 复制到 BufferedImage
            // 这里使用 IntBuffer 批量复制像素数据，比字节复制快得多
            val pixelData = (img.raster.dataBuffer as java.awt.image.DataBufferInt).data
            buffer.asIntBuffer().get(pixelData)

            // 转换为 Compose 可用的 ImageBitmap
            // toComposeImageBitmap() 在 JVM 上只是简单包装 BufferedImage，性能开销极小
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

    // ==================== 位置轮询 ====================

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                val vlcMediaPlayer = mediaPlayer
                if (vlcMediaPlayer.status().isPlaying) {
                    _currentPosition.value = vlcMediaPlayer.status().time()
                    _duration.value = vlcMediaPlayer.status().length()
                    if (_playbackState.value == VideoPlaybackState.BUFFERING) {
                        _playbackState.value = VideoPlaybackState.PLAYING
                    }
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
        _playbackState.value = VideoPlaybackState.BUFFERING
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

        // 安全释放 VLCJ 资源
        // 注意：不释放 mediaPlayerFactory，因为它是全局单例，由 DI 容器管理生命周期
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
