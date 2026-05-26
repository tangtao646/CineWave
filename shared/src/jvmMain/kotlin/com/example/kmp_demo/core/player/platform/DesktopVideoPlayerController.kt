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
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.ComponentVideoSurface
import java.awt.Canvas

/**
 * Desktop 视频播放器 — 基于 VLCJ (libvlc) 的 AWT Canvas 原生视频输出。
 *
 * 使用 [MediaPlayerFactory] + [EmbeddedMediaPlayer] 手动管理 VLCJ 播放器，
 * 通过 [VideoSurfaceAdapters] 获取平台适配器，将视频渲染到 AWT [Canvas] 上。
 *
 * ## 架构优势（对比 CallbackVideoSurface）
 * - **不依赖 `sun.misc.Unsafe`**：AWT 视频输出使用 VLC 原生渲染管道，
 *   不需要 CallbackVideoSurface 的帧回调，彻底避免 JDK 17+ 上的 Unsafe 问题
 * - **硬件加速**：VLC 内部使用 OpenGL/VideoToolbox/DirectX 等硬件加速
 * - **macOS 兼容**：使用 caopengllayer 原生视频输出层
 * - **性能更好**：无需每帧从 ByteBuffer → BufferedImage → ImageBitmap 转换
 *
 * ## 渲染流程
 * ```
 * VLCJ EmbeddedMediaPlayer
 *     → VideoSurfaceAdapter.attach(mediaPlayer, canvasPeer)
 *     → AWT Canvas（硬件加速）
 *     → SwingPanel → Compose Desktop 布局
 * ```
 *
 * ## 前置条件
 * - 需要用户安装 VLC 播放器
 *   - macOS: `brew install vlc`
 *   - Linux: `sudo apt install vlc`
 *   - Windows: 从 https://www.videolan.org/vlc/ 下载安装
 *
 * @see IPlayerController
 * @see EmbeddedMediaPlayer
 * @see VideoSurfaceAdapters
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

    /** AWT Canvas 用于视频输出，通过 SwingPanel 嵌入 Compose 布局 */
    val videoCanvas: Canvas = Canvas().apply {
        background = java.awt.Color.BLACK
        isFocusable = true
    }

    init {
        // 创建组件视频表面并绑定到 Canvas
        // 使用 ComponentVideoSurface 将视频渲染到 AWT Canvas 上
        // 注意：此时 Canvas 还没有被添加到 AWT 组件树中，
        // libvlc 会在 Canvas 被添加到窗口后自动找到有效的视频输出
        val videoSurface = mediaPlayerFactory.videoSurfaces().newVideoSurface(videoCanvas)
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

            try {
                mediaPlayerFactory.release()
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
