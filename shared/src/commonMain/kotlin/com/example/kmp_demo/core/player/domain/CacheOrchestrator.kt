package com.example.kmp_demo.core.player.domain

import com.example.kmp_demo.core.player.cache.CacheProxyServer
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.cache.SegmentInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 缓存编排器。
 *
 * 职责：
 * 1. 启动/停止 [CacheProxyServer]（本地 HTTP 代理）
 * 2. 管理 [SegmentCacheTracker] 生命周期
 * 3. 提供代理 URL 转换
 * 4. 提供切片缓存状态（用于 SeekBar 上的缓存区域标记）
 *
 * 独立于 [VideoPlayerManager]，可被测试和替换。
 * 遵循单一职责原则，只负责缓存相关逻辑。
 *
 * ## 使用方式
 * ```kotlin
 * val orchestrator = CacheOrchestrator(proxyServer, segmentCacheTracker, httpClient)
 * val proxiedUrl = orchestrator.start(originalUrl, headers)
 * // ...
 * orchestrator.release()
 * ```
 */
class CacheOrchestrator(
    private val proxyServer: CacheProxyServer? = null,
    private val segmentCacheTracker: SegmentCacheTracker? = null,
    private val httpClient: HttpClient? = null,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _cachedSegments = MutableStateFlow<List<SegmentInfo>>(emptyList())
    
    /** 切片缓存状态列表，用于在 SeekBar 上标记已缓存/未缓存区域 */
    val cachedSegments: StateFlow<List<SegmentInfo>> = _cachedSegments.asStateFlow()

    /**
     * 启动缓存服务并获取代理 URL。
     *
     * 流程：
     * 1. 启动本地代理服务器（如果可用）
     * 2. 获取代理 M3U8 URL
     * 3. 启动缓存追踪（如果可用）
     *
     * @param url 原始 M3U8 URL
     * @param headers 原始请求头
     * @return 代理后的 URL（如果代理不可用则返回原始 URL）
     */
    suspend fun start(url: String, headers: Map<String, String>? = null): String {
        // 1. 启动代理服务器
        proxyServer?.let { server ->
            try {
                server.start()
            } catch (_: Exception) {
                // 代理启动失败，回退到直接播放
            }
        }

        // 2. 获取代理 URL
        val finalUrl = getProxiedUrl(url, headers)

        // 3. 启动缓存追踪（非阻塞，在独立协程中收集）
        segmentCacheTracker?.let { tracker ->
            try {
                val m3u8Content = downloadM3u8Content(url, headers)
                tracker.startTracking(m3u8Content, url)
                // 在独立协程中订阅缓存状态变化，不阻塞 start() 返回
                scope.launch {
                    tracker.cachedSegments.collect { segments ->
                        _cachedSegments.value = segments
                    }
                }
            } catch (_: Exception) {
                // 缓存追踪失败不影响播放
            }
        }

        return finalUrl
    }

    /**
     * 释放所有缓存资源。
     *
     * 释放顺序：
     * 1. 释放缓存追踪器
     * 2. 停止代理服务器
     */
    fun release() {
        segmentCacheTracker?.release()
        runBlocking {
            try {
                proxyServer?.stop()
            } catch (_: Exception) { }
        }
    }

    /**
     * 获取代理 URL。
     *
     * 如果 proxyServer 可用，则调用 getProxiedM3u8Url() 获取经过本地缓存的 M3U8 URL。
     * 否则返回原始 URL。
     *
     * @param originalUrl 原始 M3U8 URL
     * @param headers 原始请求头，会被编码到代理 URL 中
     */
    private suspend fun getProxiedUrl(
        originalUrl: String,
        headers: Map<String, String>? = null
    ): String {
        val server = proxyServer ?: return originalUrl
        return try {
            server.getProxiedM3u8Url(originalUrl, headers)
        } catch (_: Exception) {
            originalUrl
        }
    }

    /**
     * 下载 M3U8 内容，用于解析切片列表。
     * 优先使用注入的 HttpClient（复用 Koin 配置），否则创建临时 client。
     */
    private suspend fun downloadM3u8Content(
        url: String,
        headers: Map<String, String>?
    ): String {
        val client = httpClient ?: com.example.kmp_demo.core.network.createHttpClient()
        try {
            return client.get(url) {
                headers?.forEach { (k, v) ->
                    header(k, v)
                }
            }.bodyAsText()
        } finally {
            if (httpClient == null) {
                (client as io.ktor.client.HttpClient).close()
            }
        }
    }
}
