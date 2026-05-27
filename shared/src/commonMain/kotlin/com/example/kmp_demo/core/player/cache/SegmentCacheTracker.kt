package com.example.kmp_demo.core.player.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 单个切片信息。
 *
 * @param url 原始 CDN URL
 * @param durationMs 切片时长（毫秒），从 M3U8 的 #EXTINF 解析
 * @param startMs 切片在视频中的起始时间（毫秒）
 * @param endMs 切片在视频中的结束时间（毫秒）
 * @param isCached 是否已缓存到本地磁盘
 */
data class SegmentInfo(
    val url: String,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val isCached: Boolean = false,
) {
    /** 切片在视频中的百分比位置 [0f, 1f] */
    fun fraction(totalDurationMs: Long): Float {
        if (totalDurationMs <= 0) return 0f
        return (startMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
    }

    /** 切片宽度占比 [0f, 1f] */
    fun widthFraction(totalDurationMs: Long): Float {
        if (totalDurationMs <= 0) return 0f
        return (durationMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
    }
}

/**
 * 切片缓存状态追踪器。
 *
 * 职责：
 * 1. 解析 M3U8 内容，提取所有切片 URL 及其时长
 * 2. 定期检查每个切片是否已缓存到 [DiskLruCache]
 * 3. 通过 [cachedSegments] StateFlow 暴露给 UI 层
 *
 * UI 层订阅此 StateFlow，在 SeekBar 上绘制缓存标记。
 *
 * 线程安全：所有状态修改在 [scope] 协程中执行。
 */
class SegmentCacheTracker(
    private val diskCache: DiskLruCache,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 所有切片列表（含缓存状态） */
    private val _cachedSegments = MutableStateFlow<List<SegmentInfo>>(emptyList())
    val cachedSegments: StateFlow<List<SegmentInfo>> = _cachedSegments.asStateFlow()

    /** 定期检查任务 */
    private var checkJob: Job? = null

    /**
     * 解析 M3U8 内容并开始追踪缓存状态。
     *
     * @param m3u8Content 原始 M3U8 内容
     * @param baseUrl 基础 URL，用于拼接相对路径
     */
    fun startTracking(m3u8Content: String, baseUrl: String) {
        // 取消之前的追踪
        stopTracking()

        // 解析切片
        val segments = parseSegments(m3u8Content, baseUrl)

        // 初始化缓存状态
        _cachedSegments.value = segments

        // 立即检查一次缓存状态
        scope.launch {
            checkCacheStatus()
        }

        // 定期检查（每 2 秒检查一次，因为下载是异步的）
        checkJob = scope.launch {
            while (isActive) {
                delay(2_000L)
                checkCacheStatus()
            }
        }
    }

    /**
     * 停止追踪。
     */
    fun stopTracking() {
        checkJob?.cancel()
        checkJob = null
        _cachedSegments.value = emptyList()
    }

    /**
     * 手动触发一次缓存状态检查。
     * 当有新的切片下载完成时，外部可调用此方法立即更新 UI。
     */
    fun refreshNow() {
        scope.launch {
            checkCacheStatus()
        }
    }

    /**
     * 检查所有切片的缓存状态。
     */
    private suspend fun checkCacheStatus() {
        val current = _cachedSegments.value
        if (current.isEmpty()) return

        // 批量检查缓存状态
        val urls = current.map { it.url }
        val cacheStatus = diskCache.containsAll(urls)

        // 更新缓存状态
        val updated = current.map { segment ->
            segment.copy(isCached = cacheStatus[segment.url] ?: false)
        }

        // 只有状态发生变化时才更新 Flow
        if (updated != current) {
            _cachedSegments.value = updated
        }
    }

    /**
     * 解析 M3U8 内容，提取切片信息。
     *
     * 支持标准 HLS M3U8 格式：
     * ```
     * #EXTINF:10.000,
     * https://example.com/segment-1.ts
     * #EXTINF:10.000,
     * https://example.com/segment-2.ts
     * ```
     */
    private fun parseSegments(content: String, baseUrl: String): List<SegmentInfo> {
        val lines = content.lines()
        val segments = mutableListOf<SegmentInfo>()
        var currentDuration = 0.0
        var accumulatedMs = 0L

        for (line in lines) {
            val trimmed = line.trim()

            // 解析 #EXTINF 时长
            if (trimmed.startsWith("#EXTINF:")) {
                val durationStr = trimmed.removePrefix("#EXTINF:")
                    .substringBefore(",")
                    .trim()
                currentDuration = durationStr.toDoubleOrNull() ?: 0.0
                continue
            }

            // 跳过注释和标签
            if (trimmed.startsWith("#") || trimmed.isEmpty()) continue

            // 这是一个切片 URL
            val url = resolveUrl(trimmed, baseUrl)
            val durationMs = (currentDuration * 1000).toLong()

            segments.add(
                SegmentInfo(
                    url = url,
                    durationMs = durationMs,
                    startMs = accumulatedMs,
                    endMs = accumulatedMs + durationMs,
                )
            )

            accumulatedMs += durationMs
            currentDuration = 0.0
        }

        return segments
    }

    /**
     * 解析相对 URL。
     */
    private fun resolveUrl(url: String, baseUrl: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        // 相对路径
        val base = baseUrl.substringBeforeLast("/")
        return "$base/$url"
    }

    fun release() {
        stopTracking()
        scope.cancel()
    }
}
