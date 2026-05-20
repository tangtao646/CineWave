package com.example.kmp_demo.core.player.platform

import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * compose-media-player 库的适配器实现
 *
 * 将第三方库的 [VideoPlayerState] 适配到我们定义的 [IPlayerController] 接口。
 * 如果需要替换播放引擎（如换成 ExoPlayer、AVPlayer 等），
 * 只需新建一个实现类，UI 层无需任何改动。
 *
 * API 映射说明：
 * - compose-media-player 使用 [sliderPos] (0..1000) 表示进度，[currentTime] 表示当前秒数
 * - 我们统一使用毫秒 (Long) 作为内部进度单位
 */
class ComposeMediaPlayerController(
    private val playerState: VideoPlayerState
) : IPlayerController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
        // 同步初始状态
        _volume.value = playerState.volume
        _duration.value = (playerState.duration * 1000).toLong()

        // 启动位置轮询（compose-media-player 不主动推送位置变化）
        startPositionPolling()
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                try {
                    // currentTime 单位是秒，转为毫秒
                    val posMs = (playerState.currentTime * 1000).toLong()
                    _currentPosition.value = posMs

                    // duration 单位是秒，转为毫秒
                    val durMs = (playerState.duration * 1000).toLong()
                    if (durMs > 0) _duration.value = durMs

                    // 映射播放状态
                    // 使用 playerState.isPlaying 作为 PLAYING/PAUSED 判断的主要依据，
                    // 避免依赖位置范围检测（posMs in 1 until durMs）导致的状态映射错误。
                    // 当播放器明确处于非播放、非加载状态时，应优先判断为 PAUSED 而非 READY，
                    // 以确保播放/暂停按钮图标正确切换。
                    val isPlaying = playerState.isPlaying
                    val isLoading = playerState.isLoading
                    _playbackState.value = when {
                        isLoading -> VideoPlaybackState.BUFFERING
                        isPlaying -> VideoPlaybackState.PLAYING
                        !isPlaying && durMs > 0 -> {
                            // 非播放状态且已有时长信息 → 判断是暂停还是结束
                            if (posMs >= durMs) VideoPlaybackState.ENDED
                            else VideoPlaybackState.PAUSED
                        }
                        else -> VideoPlaybackState.READY
                    }

                    // 估算缓冲进度
                    if (durMs > 0) {
                        _bufferedPercent.value = ((posMs.toFloat() / durMs) * 100).toInt().coerceIn(0, 100)
                    }
                } catch (_: Exception) {
                    // 播放器尚未就绪
                }
                delay(250)
            }
        }
    }

    override suspend fun open(url: String, headers: Map<String, String>?) {
        _playbackState.value = VideoPlaybackState.BUFFERING
        try {
            playerState.openUri(url)
            // 不在此处设置 READY，让轮询循环根据 playerState.isLoading/isPlaying
            // 自动映射正确的状态，避免状态闪烁
        } catch (e: Exception) {
            _playbackState.value = VideoPlaybackState.ERROR
        }
    }

    override suspend fun play() {
        playerState.play()
        // 不在此处设置 _playbackState，让轮询循环自动检测 isPlaying 并映射为 PLAYING
    }

    override suspend fun pause() {
        playerState.pause()
        // 不在此处设置 _playbackState，让轮询循环自动检测 !isPlaying 并映射为 PAUSED
    }

    override suspend fun togglePlayPause() {
        // 委托给底层播放器的 play()/pause()，轮询循环会自动检测状态变化
        if (playerState.isPlaying) {
            playerState.pause()
        } else {
            playerState.play()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        // sliderPos 范围是 0f..1000f，代表 0%..100%
        val durationMs = (playerState.duration * 1000).toLong()
        if (durationMs > 0) {
            val fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            playerState.seekTo(fraction * 1000f)
        }
        _currentPosition.value = positionMs
    }

    override suspend fun seekForward(seconds: Long) {
        val currentSec = playerState.currentTime
        val durationSec = playerState.duration
        val newSec = (currentSec + seconds).coerceAtMost(durationSec)
        if (durationSec > 0) {
            val fraction = (newSec / durationSec).toFloat().coerceIn(0f, 1f)
            playerState.seekTo(fraction * 1000f)
        }
        _currentPosition.value = (newSec * 1000).toLong()
    }

    override suspend fun seekBackward(seconds: Long) {
        val currentSec = playerState.currentTime
        val durationSec = playerState.duration
        val newSec = (currentSec - seconds).coerceAtLeast(0.0)
        if (durationSec > 0) {
            val fraction = (newSec / durationSec).toFloat().coerceIn(0f, 1f)
            playerState.seekTo(fraction * 1000f)
        }
        _currentPosition.value = (newSec * 1000).toLong()
    }

    override suspend fun setVolume(volume: Float) {
        playerState.volume = volume
        _volume.value = volume
    }

    override suspend fun toggleFullScreen() {
        playerState.toggleFullscreen()
        _isFullScreen.value = playerState.isFullscreen
    }

    override fun release() {
        positionPollingJob?.cancel()
        scope.cancel()
        // playerState.dispose() 由 rememberVideoPlayerState 的 DisposableEffect 自动调用
    }
}
