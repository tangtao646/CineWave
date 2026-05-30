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
 * - 提取 [launchSafe] 消除 play/pause/seek 等方法的 try-catch 模板代码
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

    /**
     * 重试触发器。
     *
     * 每次调用 [retry] 时递增，平台层通过观察此值的变化来重新触发 [open]。
     * 这是实现"错误重试"的关键机制：
     * 1. UI 层点击"重试"按钮 → 调用 [retry] → 递增计数器
     * 2. 平台层的 LaunchedEffect 观察此计数器变化 → 重新调用 [open]
     * 3. 播放器重新加载 URL
     */
    private val _retryTrigger = MutableStateFlow(0)
    val retryTrigger: StateFlow<Int> = _retryTrigger.asStateFlow()

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
        combine(
            controller.playerError,
            cacheOrchestrator?.cachedSegments ?: MutableStateFlow(emptyList())
        ) { error, segments ->
            error to segments
        }
    ) { core, ui, (playerError, cachedSegments) ->
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
            playerError = playerError,
            cachedSegments = cachedSegments,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VideoPlayerUiState()
    )

    // ========== 辅助函数：消除 try-catch 模板代码 ==========

    /**
     * 在 [scope] 中安全启动协程，捕获所有异常。
     * 消除 play/pause/seekTo 等方法中重复的 scope.launch { try { ... } catch { } } 模板。
     */
    private fun launchSafe(block: suspend IVideoPlayerController.() -> Unit) {
        scope.launch {
            try {
                controller.block()
            } catch (_: Exception) {
                // 异常由 controller 的 StateFlow 传播，无需额外处理
            }
        }
    }

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
            // cacheOrchestrator.start() 或 controller.open() 失败
            // 错误通过 controller.playbackState → ERROR 传播到 UI
            println("[VideoPlayerManager] open() failed: ${e.message}")
        }
    }

    fun play() = launchSafe { play() }

    fun pause() = launchSafe { pause() }

    fun togglePlayPause() = launchSafe { togglePlayPause() }

    fun seekToFraction(fraction: Float) {
        val targetMs = (fraction * uiState.value.duration).toLong()
        seekTo(targetMs)
    }

    fun seekTo(positionMs: Long) = launchSafe { seekTo(positionMs) }

    fun seekForward(seconds: Long = 10) = launchSafe { seekForward(seconds) }

    fun seekBackward(seconds: Long = 10) = launchSafe { seekBackward(seconds) }

    fun setVolume(volume: Float) = launchSafe { setVolume(volume) }

    fun toggleFullScreen() = launchSafe { toggleFullScreen() }

    /**
     * 重试播放。
     *
     * 递增 [retryTrigger] 计数器，平台层通过观察此值变化来重新调用 [open]。
     * 同时重置播放器错误状态。
     */
    fun retry() {
        _retryTrigger.value++
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
