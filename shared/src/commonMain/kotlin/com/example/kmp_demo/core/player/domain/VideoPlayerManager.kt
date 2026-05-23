package com.example.kmp_demo.core.player.domain

import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 视频播放器业务编排层 (Orchestrator)
 *
 * 职责：
 * 1. 将 IPlayerController 的多个 StateFlow 聚合为单一的 VideoPlayerUiState
 * 2. 提供自动隐藏控制栏的计时逻辑
 * 3. 提供位置轮询（部分底层播放器可能不主动推送位置更新）
 * 4. 作为业务逻辑的编排点，UI 层只需与此类交互
 * 5. S3: Seek 操作时通知缓存拦截器重新定位预加载
 *
 * 设计原则：UI 层不直接操作 IPlayerController，而是通过 VideoPlayerManager
 * 进行间接控制，降低耦合度。
 */
class VideoPlayerManager(
    private val controller: IPlayerController,
    /** S3: 可选的缓存拦截器引用，用于 Seek 时重新定位预加载 */
    private val cacheInterceptor: M3u8CacheInterceptor? = null,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isControlsVisible = MutableStateFlow(true)
    private var controlsAutoHideJob: Job? = null

    /** 自动隐藏控制栏的延迟时间（毫秒） */
    private var autoHideDelayMs: Long = 3000L

    /** 单一事实来源：聚合后的 UI 状态 */
    // 将 7 个 Flow 拆分为两个子聚合
    val uiState: StateFlow<VideoPlayerUiState> = combine(
        // 组 1：核心播放状态
        combine(
            controller.playbackState,
            controller.currentPosition,
            controller.duration,
            controller.bufferedPercent
        ) { playbackState, position, duration, buffered ->
            // 临时包装对象或直接传递
            Triple(playbackState, position, duration) to buffered
        },
        // 组 2：UI 交互状态
        combine(
            controller.volume,
            controller.isFullScreen,
            _isControlsVisible
        ) { volume, isFullScreen, controlsVisible ->
            Triple(volume, isFullScreen, controlsVisible)
        }
    ) { core, ui ->
        val (playback, pos, dur) = core.first
        val buffered = core.second
        val (vol, full, visible) = ui

        VideoPlayerUiState(
            playbackState = playback,
            currentPosition = pos,
            duration = dur,
            bufferedPercent = buffered,
            volume = vol,
            isFullScreen = full,
            isControlsVisible = visible,
            error = if (playback == VideoPlaybackState.ERROR) "播放出错" else null
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VideoPlayerUiState()
    )


    // ========== 播放控制 ==========

    fun open(url: String, headers: Map<String, String>? = null) {
        scope.launch {
            try {
                controller.open(url, headers)
                showControls()
            } catch (e: Exception) {
                // 错误已通过 playbackState 传播
            }
        }
    }

    fun play() {
        scope.launch {
            try {
                controller.play()
            } catch (_: Exception) { }
        }
    }

    fun pause() {
        scope.launch {
            try {
                controller.pause()
            } catch (_: Exception) { }
        }
    }

    fun togglePlayPause() {
        scope.launch {
            try {
                controller.togglePlayPause()
            } catch (_: Exception) { }
        }
    }

    fun seekToFraction(fraction: Float) {
        val targetMs = (fraction * uiState.value.duration).toLong()
        seekTo(targetMs)
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            try {
                controller.seekTo(positionMs)
                // S3: Seek 后通知缓存拦截器重新定位预加载
                // targetDuration 使用默认值 10 秒，实际值在 M3U8 解析后已知
                cacheInterceptor?.onSeek(positionMs, targetDuration = 10.0)
            } catch (_: Exception) { }
        }
    }

    fun seekForward(seconds: Long = 10) {
        scope.launch {
            try {
                controller.seekForward(seconds)
            } catch (_: Exception) { }
        }
    }

    fun seekBackward(seconds: Long = 10) {
        scope.launch {
            try {
                controller.seekBackward(seconds)
            } catch (_: Exception) { }
        }
    }

    fun setVolume(volume: Float) {
        scope.launch {
            try {
                controller.setVolume(volume)
            } catch (_: Exception) { }
        }
    }

    fun toggleFullScreen() {
        scope.launch {
            try {
                controller.toggleFullScreen()
            } catch (_: Exception) { }
        }
    }

    // ========== 控制栏显隐 ==========

    /** 显示控制栏并启动自动隐藏倒计时 */
    fun showControls() {
        _isControlsVisible.value = true
        restartAutoHideTimer()
    }

    /** 隐藏控制栏 */
    fun hideControls() {
        _isControlsVisible.value = false
        controlsAutoHideJob?.cancel()
    }

    /** 切换控制栏显隐 */
    fun toggleControls() {
        if (_isControlsVisible.value) {
            hideControls()
        } else {
            showControls()
        }
    }

    /** 设置自动隐藏延迟 */
    fun setAutoHideDelay(delayMs: Long) {
        autoHideDelayMs = delayMs
    }

    private fun restartAutoHideTimer() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = scope.launch {
            delay(autoHideDelayMs)
            _isControlsVisible.value = false
        }
    }

    // ========== 资源管理 ==========

    fun release() {
        controlsAutoHideJob?.cancel()
        scope.cancel()
        controller.release()
    }
}
