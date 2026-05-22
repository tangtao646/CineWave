package com.example.kmp_demo.core.player.platform

import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import io.github.kdroidfilter.composemediaplayer.AudioMode
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Desktop 视频播放器 — 基于 ComposeMediaPlayer
 *
 * 使用 [io.github.kdroidfilter.composemediaplayer] 库实现视频播放。
 * 底层在 macOS 上使用 AVFoundation，在 Windows/Linux 上使用 JavaFX MediaPlayer。
 *
 * 优势：
 * - 纯 Kotlin/Compose 实现，无原生依赖
 * - 跨平台一致 API（macOS/Windows/Linux）
 * - 与 Compose UI 完美集成（VideoPlayerSurface）
 * - 无需安装 VLC
 */
class DesktopVideoPlayerController : IPlayerController {

    companion object {
        private const val POLL_INTERVAL_MS = 250L
    }

    /** ComposeMediaPlayer 的 VideoPlayerState 实例 */
    private var _videoPlayerState: VideoPlayerState? = null

    /** 设置 VideoPlayerState（由 PlatformVideoPlayerScreen 在 Composable 中创建后注入） */
    fun setVideoPlayerState(state: VideoPlayerState) {
        _videoPlayerState = state
        observeState(state)
    }

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

    private var _headers: Map<String, String>? = null

    /**
     * 观察 VideoPlayerState 的状态变化，映射到 IPlayerController 的 StateFlow
     */
    private fun observeState(state: VideoPlayerState) {
        scope.launch {
            // 轮询位置和时长（VideoPlayerState 没有直接的 StateFlow 暴露这些值）
            while (isActive) {
                _currentPosition.value = (state.currentTime * 1000).toLong()
                _duration.value = (state.duration * 1000).toLong()
                _bufferedPercent.value = (state.sliderPos * 100).toInt().coerceIn(0, 100)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun open(url: String, headers: Map<String, String>?) {
        _headers = headers
        _playbackState.value = VideoPlaybackState.BUFFERING

        val state = _videoPlayerState
        if (state != null) {
            // 如果已经有 VideoPlayerState，直接打开
            state.openUri(url, InitialPlayerState.PLAY)
        }
    }

    override suspend fun play() {
        _videoPlayerState?.play()
    }

    override suspend fun pause() {
        _videoPlayerState?.pause()
    }

    override suspend fun togglePlayPause() {
        val state = _videoPlayerState ?: return
        if (state.isPlaying) {
            state.pause()
        } else {
            state.play()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        val state = _videoPlayerState ?: return
        val fraction = if (state.duration > 0) {
            (positionMs.toFloat() / (state.duration * 1000).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        state.seekTo(fraction)
    }

    override suspend fun seekForward(seconds: Long) {
        val state = _videoPlayerState ?: return
        val currentMs = state.currentTime * 1000
        val targetMs = currentMs + seconds * 1000
        val fraction = if (state.duration > 0) {
            (targetMs.toFloat() / (state.duration * 1000).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        state.seekTo(fraction)
    }

    override suspend fun seekBackward(seconds: Long) {
        val state = _videoPlayerState ?: return
        val currentMs = state.currentTime * 1000
        val targetMs = (currentMs - seconds * 1000).coerceAtLeast(0.0)
        val fraction = if (state.duration > 0) {
            (targetMs.toFloat() / (state.duration * 1000).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        state.seekTo(fraction)
    }

    override suspend fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        _videoPlayerState?.volume = _volume.value
    }

    override suspend fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
        _videoPlayerState?.isFullscreen = _isFullScreen.value
    }

    override fun release() {
        positionPollingJob?.cancel()
        scope.cancel()
        _videoPlayerState?.dispose()
        _videoPlayerState = null
    }
}
