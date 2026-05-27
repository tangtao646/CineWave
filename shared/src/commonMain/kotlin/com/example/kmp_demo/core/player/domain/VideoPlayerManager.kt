package com.example.kmp_demo.core.player.domain

import com.example.kmp_demo.core.network.createHttpClient
import com.example.kmp_demo.core.player.cache.CacheProxyServer
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.cache.SegmentInfo
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
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
 *   ├─ 启动 CacheProxyServer（本地 HTTP 代理）
 *   ├─ 获取代理 M3U8 URL：http://localhost:PORT/m3u8?url=...
 *   └─ 播放器打开代理 URL → 所有切片请求经过本地缓存
 * ```
 *
 * ## 切片请求流程
 * ```
 * 播放器 → 本地代理 → 检查 DiskLruCache
 *   ├─ 命中 → 直接从磁盘返回（毫秒级，零网络）
 *   └─ 未命中 → 回源 CDN → 写入缓存 → 返回
 * ```
 *
 * @param controller 底层播放器控制器
 * @param proxyServer 本地 HTTP 代理服务器，让播放器所有请求经过缓存
 * @param segmentCacheTracker 切片缓存状态追踪器，用于在 SeekBar 上标记已缓存/未缓存区域
 */
class VideoPlayerManager(
    private val controller: IPlayerController,
    /** 本地 HTTP 代理服务器，让播放器所有请求经过缓存 */
    private val proxyServer: CacheProxyServer? = null,
    /** 切片缓存状态追踪器，用于在 SeekBar 上标记已缓存/未缓存区域 */
    private val segmentCacheTracker: SegmentCacheTracker? = null,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isControlsVisible = MutableStateFlow(true)
    private var controlsAutoHideJob: Job? = null

    /** 自动隐藏控制栏的延迟时间（毫秒） */
    private var autoHideDelayMs: Long = 3000L

    /** 切片缓存状态 */
    private val _cachedSegments = MutableStateFlow<List<SegmentInfo>>(emptyList())

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
        },
        _cachedSegments  // 合并缓存状态
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
     * 流程：
     * 1. 启动本地代理服务器（如果可用）
     * 2. 获取代理 M3U8 URL
     * 3. 启动缓存追踪（如果可用）
     * 4. 播放器打开（代理后的）URL
     *
     * ⚠️ 注意：proxyServer.start() 和 controller.open() 内部都会调用 start()，
     * 但 CacheProxyServer.start() 是幂等的（内部先 stop() 再 start()），所以重复调用安全。
     */
    fun open(url: String, headers: Map<String, String>? = null) {
        scope.launch {
            try {
                // 步骤1：启动本地代理服务器（如果可用）
                proxyServer?.let { server ->
                    try {
                        server.start()
                    } catch (_: Exception) {
                        // 代理启动失败，回退到直接播放
                    }
                }

                // 步骤2：获取代理 URL（如果代理服务器可用），传递请求头
                val finalUrl = getProxiedUrl(url, headers)

                // 步骤3：启动缓存追踪（如果可用）
                segmentCacheTracker?.let { tracker ->
                    launch {
                        try {
                            // 下载 M3U8 内容并开始追踪
                            val m3u8Content = downloadM3u8Content(url, headers)
                            tracker.startTracking(m3u8Content, url)
                            // 订阅缓存状态变化（在独立协程中收集，不阻塞主流程）
                            launch {
                                tracker.cachedSegments.collect { segments ->
                                    _cachedSegments.value = segments
                                }
                            }
                        } catch (_: Exception) {
                            // 缓存追踪失败不影响播放
                        }
                    }
                }

                // 步骤4：播放器打开视频
                // Android：controller.open() 直接给 ExoPlayer，CacheDataSource 透明处理缓存
                // Desktop：controller.open() 给 VLCJ，URL 已通过 getProxiedUrl 转为代理 URL
                controller.open(finalUrl, headers)
                showControls()
            } catch (e: Exception) {
                // 错误已通过 playbackState 传播
            }
        }
    }

    /**
     * 下载 M3U8 内容，用于解析切片列表。
     * 使用 Ktor HttpClient 直接下载原始 M3U8。
     */
    private suspend fun downloadM3u8Content(url: String, headers: Map<String, String>?): String {
        val httpClient = createHttpClient()
        try {
            val response = httpClient.get(url) {
                headers?.forEach { (k, v) ->
                    header(k, v)
                }
            }
            return response.bodyAsText()
        } finally {
            httpClient.close()
        }
    }

    /**
     * 获取代理 URL。
     *
     * 如果 proxyServer 可用，则调用 getProxiedM3u8Url() 获取经过本地缓存的 M3U8 URL。
     * 否则返回原始 URL。
     * @param originalUrl 原始 M3U8 URL
     * @param headers 原始请求头，会被编码到代理 URL 中
     */
    private fun getProxiedUrl(originalUrl: String, headers: Map<String, String>? = null): String {
        val server = proxyServer ?: return originalUrl
        return try {
            server.getProxiedM3u8Url(originalUrl, headers)
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

    /**
     * 释放所有资源。
     *
     * 释放顺序（重要）：
     * 1. 取消控制栏自动隐藏定时器
     * 2. 释放缓存追踪器
     * 3. 停止代理服务器（使用 NonCancellable 确保执行，不被 scope.cancel 中断）
     * 4. 取消协程作用域
     * 5. 释放播放器控制器
     */
    fun release() {
        controlsAutoHideJob?.cancel()
        segmentCacheTracker?.release()
        // 使用 NonCancellable 确保 proxyServer.stop() 不被 scope.cancel() 取消
        runBlocking(NonCancellable) {
            try {
                proxyServer?.stop()
            } catch (_: Exception) { }
        }
        scope.cancel()
        controller.release()
    }
}
