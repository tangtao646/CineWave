package com.example.kmp_demo.core.player.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 视频播放器业务编排层 (Orchestrator)
 *
 * 重构后职责：
 * 1. 将 [IVideoPlayerController] 的多个 StateFlow 聚合为单一的 [VideoPlayerUiState]
 * 2. 提供播放控制方法（play/pause/seek/volume/fullscreen）
 * 3. 组合 [ControlsAutoHideService] 管理控制栏显隐
 * 4. 组合 [CacheOrchestrator] 管理缓存
 *
 * 关键变化：
 * - [open] 改为 suspend fun，消除协程嵌套
 * - 控制栏自动隐藏委托给 [ControlsAutoHideService]
 * - 缓存逻辑委托给 [CacheOrchestrator]
 * - 移除 runBlocking
 *
 * ## 缓存架构（纯代理模式）
 *
 * 播放器所有请求都经过本地 HTTP 代理服务器 [CacheProxyServer]：
 * - 代理是唯一的缓存层，负责缓存读写
 * - 播放器使用标准的 HTTP 数据源直连本地代理
 * - 不再使用 HybridDataSource（已废弃），避免双缓存路径冲突
 *
 * ## 播放流程
 * ```
 * open(url)
 *   ├─ CacheOrchestrator.start(url) → 启动代理 + 获取代理 URL
 *   └─ controller.open(proxiedUrl) → 播放器打开代理 URL
 * ```
 *
 * @param controller 底层播放器控制器
 * @param controlsAutoHideService 控制栏自动隐藏服务
 * @param cacheOrchestrator 缓存编排器（可选）
 */
class VideoPlayerManager(
    private val controller: IVideoPlayerController,
    private val controlsAutoHideService: ControlsAutoHideService = ControlsAutoHideService(),
    private val cacheOrchestrator: CacheOrchestrator? = null,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ========== 状态聚合 ==========

    /** 单一事实来源：聚合后的 UI 状态 */
    val uiState: StateFlow<VideoPlayerUiState> = combine(
        combine(
            controller.playbackState,
            controller.currentPosition,
            controller.duration,
            controller.bufferedPercent
        ) { playbackState, position, duration, buffered ->
            Triple(playbackState, position, duration) to buffered
        },
        combine(
            controller.volume,
            controller.isFullScreen,
            controlsAutoHideService.isControlsVisible
        ) { volume, isFullScreen, controlsVisible ->
            Triple(volume, isFullScreen, controlsVisible)
        },
        cacheOrchestrator?.cachedSegments ?: MutableStateFlow(emptyList())
    ) { core, ui, cachedSegments ->
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
            error = if (playback == VideoPlaybackState.ERROR) "播放出错" else null,
            cachedSegments = cachedSegments,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VideoPlayerUiState()
    )

    // ========== 播放控制 ==========

    /**
     * 打开视频源。
     *
     * suspend 函数，由调用方（LaunchedEffect）管理协程。
     * 消除了旧版中 scope.launch 嵌套 LaunchedEffect 的问题。
     *
     * 流程：
     * 1. 通过 CacheOrchestrator 启动缓存服务并获取代理 URL
     * 2. 播放器打开（代理后的）URL
     * 3. 显示控制栏
     */
    suspend fun open(url: String, headers: Map<String, String>? = null) {
        try {
            val finalUrl = cacheOrchestrator?.start(url, headers) ?: url
            controller.open(finalUrl, headers)
            controlsAutoHideService.show()
        } catch (e: Exception) {
            // 错误已通过 playbackState 传播
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

    // ========== 控制栏显隐（委托给 ControlsAutoHideService）==========

    fun showControls() = controlsAutoHideService.show()
    fun hideControls() = controlsAutoHideService.hide()
    fun toggleControls() = controlsAutoHideService.toggle()

    fun setAutoHideDelay(delayMs: Long) {
        controlsAutoHideService.setAutoHideDelay(delayMs)
    }

    // ========== 资源管理 ==========

    /**
     * 释放所有资源。
     *
     * 释放顺序：
     * 1. 取消控制栏自动隐藏定时器
     * 2. 释放缓存编排器
     * 3. 取消协程作用域
     * 4. 释放播放器控制器
     */
    fun release() {
        controlsAutoHideService.cancelTimer()
        cacheOrchestrator?.release()
        scope.coroutineContext.cancel()
        controller.release()
    }
}
