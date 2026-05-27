package com.example.kmp_demo.core.player.cache

import kotlinx.coroutines.flow.StateFlow

/**
 * 本地 HTTP 代理服务器接口，用于桌面端视频缓存加速。
 *
 * Android 端使用 ExoPlayer 原生 SimpleCache + CacheDataSource 方案，
 * 不需要本地代理，注入 [VideoPlayerManager] 时传 `null` 即可。
 *
 * @see CacheProxyServerJvm JVM 平台实现
 */
interface CacheProxyServer {
    /** 代理服务器端口，启动后可用 */
    val port: Int

    /** 缓存统计信息 */
    val stats: StateFlow<CacheStats>

    /** 启动本地代理服务器 */
    suspend fun start(): Int

    /** 获取代理后的 M3U8 URL */
    fun getProxiedM3u8Url(originalUrl: String, headers: Map<String, String>? = null): String

    /** 停止代理服务器 */
    suspend fun stop()
}
