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
 * 3. 缓存预加载与播放器启动的协调
 *
 * 缓存架构（Desktop 端）：
 * ┌─────────────────────────────────────────────────────────────┐
 * │ open(url)                                                   │
 * │   ├─ 启动 CacheProxyServer（本地 HTTP 代理）                 │
 * │   ├─ 获取代理 M3U8 URL：http://localhost:PORT/m3u8?url=...  │
 * │   ├─ 启动 M3u8CacheInterceptor 后台预加载                    │
 * │   └─ 播放器打开代理 URL → 所有切片请求经过本地缓存            │
 * │                                                             │
 * │ 切片请求流程：                                               │
 * │ 播放器 → 本地代理 → 检查 DiskLruCache                       │
 * │   ├─ 命中 → 直接从磁盘返回（毫秒级，零网络）                 │
 * │   └─ 未命中 → 回源 CDN → 写入缓存 → 返回                    │
 * └─────────────────────────────────────────────────────────────┘
 *
 * @param controller 底层播放器控制器
 * @param cacheInterceptor 缓存拦截器（可选，用于预加载和 Seek 定位）
 * @param proxyServer 本地 HTTP 代理服务器（可选，Desktop 端使用）
 */
class VideoPlayerManager(
    private val controller: IPlayerController,
    /** 缓存拦截器，用于启动预加载和 Seek 时重新定位 */
    private val cacheInterceptor: M3u8CacheInterceptor? = null,
    /** 本地 HTTP 代理服务器（JVM Desktop 端），让播放器所有请求经过缓存 */
    private val proxyServer: Any? = null,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isControlsVisible = MutableStateFlow(true)
    private var controlsAutoHideJob: Job? = null

    /** 自动隐藏控制栏的延迟时间（毫秒） */
    private var autoHideDelayMs: Long = 3000L

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

    /**
     * 打开视频源。
     *
     * 流程：
     * 1. 启动缓存预加载（后台协程）
     * 2. 如果代理服务器可用，获取代理 M3U8 URL
     * 3. 播放器打开（代理后的）URL
     */
    fun open(url: String, headers: Map<String, String>? = null) {
        scope.launch {
            try {
                // 步骤1：启动缓存预加载（零等待，后台异步）
                cacheInterceptor?.intercept(url, headers)

                // 步骤2：获取代理 URL（如果代理服务器可用）
                val finalUrl = getProxiedUrl(url)

                // 步骤3：等待起播冲刺完成（让预加载领先播放器几步）
                delay(1_500L)

                // 步骤4：播放器打开视频
                controller.open(finalUrl, headers)
                showControls()
            } catch (e: Exception) {
                // 错误已通过 playbackState 传播
            }
        }
    }

    /**
     * 获取代理 URL。
     *
     * 如果 proxyServer 是 com.example.kmp_demo.core.player.cache.CacheProxyServer 类型，
     * 则调用 getProxiedM3u8Url() 获取经过本地缓存的 M3U8 URL。
     * 否则返回原始 URL。
     */
    private fun getProxiedUrl(originalUrl: String): String {
        if (proxyServer == null) return originalUrl

        return try {
            val method = proxyServer::class.java.getMethod("getProxiedM3u8Url", String::class.java)
            method.invoke(proxyServer, originalUrl) as? String ?: originalUrl
        } catch (_: Exception) {
            originalUrl
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

    fun showControls() {
        _isControlsVisible.value = true
        restartAutoHideTimer()
    }

    fun hideControls() {
        _isControlsVisible.value = false
        controlsAutoHideJob?.cancel()
    }

    fun toggleControls() {
        if (_isControlsVisible.value) {
            hideControls()
        } else {
            showControls()
        }
    }

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
