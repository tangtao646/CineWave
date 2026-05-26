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
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Canvas
import javax.swing.JPanel

/**
 * Desktop 视频播放器 — 基于 VLCJ (libvlc) 的 AWT Canvas 原生视频输出。
 *
 * 使用 [MediaPlayerFactory] + [EmbeddedMediaPlayerComponent] 管理 VLCJ 播放器，
 * 通过 [EmbeddedMediaPlayerComponent] 内部自动处理平台差异（macOS/Linux/Windows）。
 *
 * ## 架构优势（对比 CallbackVideoSurface）
 * - **不依赖 `sun.misc.Unsafe`**：AWT 视频输出使用 VLC 原生渲染管道，
 *   不需要 CallbackVideoSurface 的帧回调，彻底避免 JDK 17+ 上的 Unsafe 问题
 * - **硬件加速**：VLC 内部使用 OpenGL/VideoToolbox/DirectX 等硬件加速
 * - **跨平台兼容**：EmbeddedMediaPlayerComponent 内部自动处理平台差异
 *   - macOS: 使用 Window 作为视频表面（macOS 不支持 AWT Canvas 直接渲染）
 *   - Linux: 使用 X11 Window ID
 *   - Windows: 使用 HWND
 * - **性能更好**：无需每帧从 ByteBuffer → BufferedImage → ImageBitmap 转换
 *
 * ## 渲染流程
 * ```
 * VLCJ EmbeddedMediaPlayerComponent (JPanel)
 *     → 内部 ComponentVideoSurface
 *     → VLC 原生渲染管道（硬件加速）
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
 * @see EmbeddedMediaPlayerComponent
 * @see EmbeddedMediaPlayer
 */
class DesktopVideoPlayerController(
    private val mediaPlayerFactory: MediaPlayerFactory
) : IPlayerController {

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 250L
    }

    // ==================== VLCJ 核心组件 ====================

    /**
     * EmbeddedMediaPlayerComponent 封装了 MediaPlayerFactory、EmbeddedMediaPlayer
     * 和视频表面组件，内部自动处理平台差异。
     *
     * - macOS: 使用 Window 作为视频表面
     * - Linux: 使用 X11 Window ID
     * - Windows: 使用 HWND
     */
    val mediaPlayerComponent: EmbeddedMediaPlayerComponent = EmbeddedMediaPlayerComponent(
        mediaPlayerFactory,
        null,  // videoSurfaceComponent - null 表示使用默认 Canvas
        null,  // fullScreenStrategy - null 表示不使用全屏策略
        null,  // inputEvents - null 表示使用默认输入事件处理
        null   // overlayWindow - null 表示不使用覆盖窗口
    )

    /** VLCJ 嵌入式媒体播放器 */
    val mediaPlayer: EmbeddedMediaPlayer = mediaPlayerComponent.mediaPlayer()

    /** 视频表面组件（JPanel），通过 SwingPanel 嵌入 Compose 布局 */
    val videoSurfaceComponent: JPanel = mediaPlayerComponent

    init {
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
                mediaPlayerComponent.release()
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
