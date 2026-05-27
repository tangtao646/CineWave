package com.example.kmp_demo.core.player.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 缓存统计信息，用于监控代理+下载是否正常工作。
 *
 * 通过 [CacheProxyServer.stats] 获取实时统计。
 * 可在 UI 上展示调试信息，或打印到日志。
 */
data class CacheStats(
    /** 缓存命中次数 */
    val hitCount: Long = 0,
    /** 缓存未命中次数（回源 CDN） */
    val missCount: Long = 0,
    /** 已缓存的切片数量 */
    val cachedSegmentCount: Long = 0,
    /** 已下载的总字节数 */
    val totalBytesDownloaded: Long = 0,
    /** 从缓存读取的总字节数 */
    val totalBytesFromCache: Long = 0,
    /** 代理服务器是否正在运行 */
    val isRunning: Boolean = false,
    /** 代理服务器端口 */
    val port: Int = 0,
) {
    /** 缓存命中率（0.0 ~ 1.0） */
    val hitRate: Float
        get() {
            val total = hitCount + missCount
            return if (total > 0) hitCount.toFloat() / total else 0f
        }

    /** 格式化的统计摘要 */
    val summary: String
        get() = buildString {
            appendLine("═══ Cache Proxy Stats ═══")
            appendLine("Status: ${if (isRunning) "🟢 Running" else "🔴 Stopped"}")
            appendLine("Port: $port")
            appendLine("Hit: $hitCount | Miss: $missCount")
            appendLine("Hit Rate: ${"%.1f".format(hitRate * 100)}%")
            appendLine("Cached Segments: $cachedSegmentCount")
            appendLine("Downloaded: ${formatBytes(totalBytesDownloaded)}")
            appendLine("From Cache: ${formatBytes(totalBytesFromCache)}")
            appendLine("══════════════════════════")
        }

    companion object {
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
                else -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
            }
        }
    }
}

/**
 * 缓存统计管理器，线程安全。
 */
class CacheStatsCollector {
    private val _stats = MutableStateFlow(CacheStats())
    val stats: StateFlow<CacheStats> = _stats.asStateFlow()

    fun recordHit(bytesRead: Long) {
        _stats.value = _stats.value.copy(
            hitCount = _stats.value.hitCount + 1,
            totalBytesFromCache = _stats.value.totalBytesFromCache + bytesRead,
        )
    }

    fun recordMiss(bytesDownloaded: Long) {
        _stats.value = _stats.value.copy(
            missCount = _stats.value.missCount + 1,
            totalBytesDownloaded = _stats.value.totalBytesDownloaded + bytesDownloaded,
        )
    }

    fun recordCachedSegment() {
        _stats.value = _stats.value.copy(
            cachedSegmentCount = _stats.value.cachedSegmentCount + 1,
        )
    }

    fun setRunning(port: Int) {
        _stats.value = _stats.value.copy(isRunning = true, port = port)
    }

    fun setStopped() {
        _stats.value = _stats.value.copy(isRunning = false)
    }

    fun reset() {
        _stats.value = CacheStats()
    }
}
