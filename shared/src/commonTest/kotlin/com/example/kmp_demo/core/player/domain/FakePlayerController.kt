package com.example.kmp_demo.core.player.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 用于测试的假播放器控制器
 *
 * 模拟 [IPlayerController] 的行为，不依赖任何平台特定的播放引擎。
 * 所有状态变化通过协程和 Flow 驱动，支持手动推进时间线以测试 seek、play/pause 等操作。
 */
class FakePlayerController : IPlayerController {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _playbackState = MutableStateFlow(VideoPlaybackState.IDLE)
    override val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(120_000L) // 默认 2 分钟
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _bufferedPercent = MutableStateFlow(0)
    override val bufferedPercent: StateFlow<Int> = _bufferedPercent.asStateFlow()

    /** 模拟播放进度推进的 Job */
    private var progressJob: Job? = null

    /** 记录最后一次调用的方法名，用于断言 */
    var lastCalledMethod: String? = null
        private set

    /** 记录最后一次 seek 的目标位置（毫秒） */
    var lastSeekPositionMs: Long = -1L
        private set

    /** 记录最后一次 open 的 URL */
    var lastOpenedUrl: String? = null
        private set

    /** 记录最后一次 open 的 headers */
    var lastOpenedHeaders: Map<String, String>? = null
        private set

    /** 是否已释放 */
    var isReleased: Boolean = false
        private set

    /** 模拟播放时的进度增量（毫秒/帧），默认每帧前进 250ms */
    var progressIncrementMs: Long = 250L

    override suspend fun open(url: String, headers: Map<String, String>?) {
        lastCalledMethod = "open"
        lastOpenedUrl = url
        lastOpenedHeaders = headers
        _playbackState.value = VideoPlaybackState.BUFFERING
        // 模拟缓冲完成
        delay(50)
        _playbackState.value = VideoPlaybackState.READY
        _currentPosition.value = 0L
        _bufferedPercent.value = 100
    }

    override suspend fun play() {
        lastCalledMethod = "play"
        _playbackState.value = VideoPlaybackState.PLAYING
        startProgressSimulation()
    }

    override suspend fun pause() {
        lastCalledMethod = "pause"
        _playbackState.value = VideoPlaybackState.PAUSED
        stopProgressSimulation()
    }

    override suspend fun togglePlayPause() {
        lastCalledMethod = "togglePlayPause"
        if (_playbackState.value == VideoPlaybackState.PLAYING) {
            pause()
        } else {
            play()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        lastCalledMethod = "seekTo"
        lastSeekPositionMs = positionMs
        val clampedPosition = positionMs.coerceIn(0L, _duration.value)
        _currentPosition.value = clampedPosition
    }

    override suspend fun seekForward(seconds: Long) {
        lastCalledMethod = "seekForward"
        val newPos = (_currentPosition.value + seconds * 1000).coerceAtMost(_duration.value)
        _currentPosition.value = newPos
        lastSeekPositionMs = newPos
    }

    override suspend fun seekBackward(seconds: Long) {
        lastCalledMethod = "seekBackward"
        val newPos = (_currentPosition.value - seconds * 1000).coerceAtLeast(0L)
        _currentPosition.value = newPos
        lastSeekPositionMs = newPos
    }

    override suspend fun setVolume(volume: Float) {
        lastCalledMethod = "setVolume"
        _volume.value = volume.coerceIn(0f, 1f)
    }

    override suspend fun toggleFullScreen() {
        lastCalledMethod = "toggleFullScreen"
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun release() {
        lastCalledMethod = "release"
        isReleased = true
        stopProgressSimulation()
        scope.cancel()
    }

    // ========== 辅助方法 ==========

    /** 手动设置播放状态（用于测试特定场景） */
    fun setPlaybackState(state: VideoPlaybackState) {
        _playbackState.value = state
    }

    /** 手动设置当前进度（毫秒） */
    fun setPosition(positionMs: Long) {
        _currentPosition.value = positionMs
    }

    /** 手动设置总时长（毫秒） */
    fun setDuration(durationMs: Long) {
        _duration.value = durationMs
    }

    /** 手动设置缓冲百分比 */
    fun setBufferedPercent(percent: Int) {
        _bufferedPercent.value = percent
    }

    /** 手动设置全屏状态 */
    fun setFullScreen(fullScreen: Boolean) {
        _isFullScreen.value = fullScreen
    }

    /** 模拟播放进度推进 */
    private fun startProgressSimulation() {
        stopProgressSimulation()
        progressJob = scope.launch {
            while (isActive) {
                delay(progressIncrementMs)
                val newPos = (_currentPosition.value + progressIncrementMs).coerceAtMost(_duration.value)
                _currentPosition.value = newPos
                if (newPos >= _duration.value) {
                    _playbackState.value = VideoPlaybackState.ENDED
                    stopProgressSimulation()
                }
            }
        }
    }

    /** 停止进度模拟 */
    private fun stopProgressSimulation() {
        progressJob?.cancel()
        progressJob = null
    }

    /** 重置所有状态 */
    fun reset() {
        stopProgressSimulation()
        _playbackState.value = VideoPlaybackState.IDLE
        _currentPosition.value = 0L
        _duration.value = 120_000L
        _volume.value = 1.0f
        _isFullScreen.value = false
        _bufferedPercent.value = 0
        lastCalledMethod = null
        lastSeekPositionMs = -1L
        lastOpenedUrl = null
        lastOpenedHeaders = null
        isReleased = false
    }
}
