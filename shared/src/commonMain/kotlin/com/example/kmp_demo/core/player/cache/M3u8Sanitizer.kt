package com.example.kmp_demo.core.player.cache

/**
 * 广告被清洗后的时间轴处理策略
 */
enum class AdCleanStrategy {
    DROP_COMPLETELY, // 适用于桌面端 VLCJ
    REPLACE_WITH_GAP // 适用于 Android 端 ExoPlayer
}

/**
 * M3U8 播放列表清洗器 (终极高稳定性、零头部丢失重构版)
 */
class M3u8Sanitizer(
    private val adSegmentFilter: AdSegmentFilter,
    private val cleanStrategy: AdCleanStrategy = AdCleanStrategy.DROP_COMPLETELY
) {

    companion object {
        // 精准的 10s ~ 30s 广告区间特征审计指标
        private const val MIN_AD_DURATION = 10.0
        private const val MAX_AD_DURATION = 31.0
        private const val MIN_AD_SEGMENTS = 2
        private const val MAX_AD_SEGMENTS = 6
        private const val MAX_AVG_SEGMENT_DURATION = 8.0
    }

    private data class MediaSegment(
        val infLine: String?,
        val urlLine: String,
        val duration: Double,
        val extraLines: List<String> = emptyList()
    )

    private data class PlaylistBlock(
        val hasLeadingDiscontinuity: Boolean,
        val segments: List<MediaSegment>
    ) {
        val totalDuration: Double = segments.sumOf { it.duration }
        val segmentCount: Int = segments.size
        val averageSegmentDuration: Double = if (segmentCount > 0) totalDuration / segmentCount else 0.0

        val isAdBlock: Boolean
            get() = totalDuration in MIN_AD_DURATION..MAX_AD_DURATION &&
                    segmentCount in MIN_AD_SEGMENTS..MAX_AD_SEGMENTS &&
                    averageSegmentDuration <= MAX_AVG_SEGMENT_DURATION
    }

    /**
     * 解析包装体：将全局头与局部块彻底解耦
     */
    private data class M3u8Structure(
        val globalHeaders: List<String>, // 🌟 独立抽离出的全局头部，确保永远不会被抹除！
        val blocks: List<PlaylistBlock>
    )

    fun sanitize(rawContent: String, m3u8BaseUrl: String): String {
        val structure = parseM3u8Structure(rawContent)
        val result = mutableListOf<String>()

        // 1. 无论如何，最优先无缝填入全局头部标头，保证 M3U8 语法绝对合法
        result.addAll(structure.globalHeaders)

        val blocks = structure.blocks
        var validBlockProcessedCount = 0

        for (block in blocks) {
            if (block.isAdBlock) {
                // ========== 命中广告区间块 ==========
                if (cleanStrategy == AdCleanStrategy.REPLACE_WITH_GAP) {
                    // 【Android 策略】：广告块整体退化为 GAP 占位，并严格对齐断层标记
                    if (block.hasLeadingDiscontinuity && result.size > structure.globalHeaders.size) {
                        result.add("#EXT-X-DISCONTINUITY")
                    }
                    block.segments.forEach { segment ->
                        segment.infLine?.let { result.add(it) }
                        result.add("#EXT-X-GAP")
                    }
                }
                // DROP_COMPLETELY 模式下直接 continue 蒸发
                continue
            }

            // ========== 健康的正片区间 ==========
            if (block.segments.isEmpty()) continue
            validBlockProcessedCount++

            // 只有当这不是第一个正片块，且前序有断层意图时，才补入唯一的 #EXT-X-DISCONTINUITY
            if (block.hasLeadingDiscontinuity && validBlockProcessedCount > 1 && result.size > structure.globalHeaders.size) {
                result.add("#EXT-X-DISCONTINUITY")
            }

            // 写入正片块切片
            block.segments.forEach { segment ->
                if (adSegmentFilter.isAdSegment(segment.urlLine, m3u8BaseUrl)) {
                    if (cleanStrategy == AdCleanStrategy.REPLACE_WITH_GAP) {
                        segment.infLine?.let { result.add(it) }
                        result.add("#EXT-X-GAP")
                    }
                } else {
                    result.addAll(segment.extraLines)
                    segment.infLine?.let { result.add(it) }
                    result.add(segment.urlLine)
                }
            }
        }

        // 强力防跳尾保护锁：强制清除末尾可能残存的悬空断层标记
        while (result.size > structure.globalHeaders.size &&
            (result.last().trim().startsWith("#EXT-X-DISCONTINUITY") || result.last().trim().isEmpty())) {
            result.removeAt(result.lastIndex)
        }

        // 补回 HLS 要求的末尾闭合标签（如果有的话）
        if (rawContent.contains("#EXT-X-ENDLIST") && !result.contains("#EXT-X-ENDLIST")) {
            result.add("#EXT-X-ENDLIST")
        }

        return result.joinToString("\n")
    }

    fun countAdSegments(rawContent: String, m3u8BaseUrl: String): Int {
        val structure = parseM3u8Structure(rawContent)
        val adBlockSegmentsCount = structure.blocks.filter { it.isAdBlock }.sumOf { it.segmentCount }
        if (adBlockSegmentsCount > 0) return adBlockSegmentsCount

        return structure.blocks.flatMap { it.segments }
            .count { adSegmentFilter.isAdSegment(it.urlLine, m3u8BaseUrl) }
    }

    /**
     * 完美鲁棒性语法解析器
     */
    private fun parseM3u8Structure(rawContent: String): M3u8Structure {
        val lines = rawContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val globalHeaders = mutableListOf<String>()
        val blocks = mutableListOf<PlaylistBlock>()

        var hasDiscontinuity = false
        var currentSegments = mutableListOf<MediaSegment>()

        var activeInfLine: String? = null
        val activeExtraLines = mutableListOf<String>()
        var hasEncounteredFirstSegment = false

        for (line in lines) {
            when {
                // 全局基础非媒体定义标签归入 Global Headers
                (line.startsWith("#EXTM3U") ||
                        line.startsWith("#EXT-X-VERSION") ||
                        line.startsWith("#EXT-X-TARGETDURATION") ||
                        line.startsWith("#EXT-X-MEDIA-SEQUENCE") ||
                        line.startsWith("#EXT-X-PLAYLIST-TYPE")) -> {
                    globalHeaders.add(line)
                }

                line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    if (currentSegments.isNotEmpty() || !hasEncounteredFirstSegment) {
                        if (currentSegments.isNotEmpty()) {
                            blocks.add(PlaylistBlock(hasDiscontinuity, currentSegments))
                            currentSegments = mutableListOf()
                        }
                        hasEncounteredFirstSegment = true
                    }
                    hasDiscontinuity = true
                }

                line.startsWith("#EXTINF:") -> {
                    hasEncounteredFirstSegment = true
                    activeInfLine = line
                }

                line.startsWith("#") -> {
                    if (!line.startsWith("#EXT-X-ENDLIST")) {
                        activeExtraLines.add(line)
                    }
                }

                else -> { // 遭遇真实 TS URL 行
                    hasEncounteredFirstSegment = true
                    val duration = activeInfLine?.substringAfter("#EXTINF:")
                        ?.substringBefore(",")?.toDoubleOrNull() ?: 0.0

                    currentSegments.add(
                        MediaSegment(
                            infLine = activeInfLine,
                            urlLine = line,
                            duration = duration,
                            extraLines = activeExtraLines.toList()
                        )
                    )
                    activeInfLine = null
                    activeExtraLines.clear()
                }
            }
        }

        if (currentSegments.isNotEmpty()) {
            blocks.add(PlaylistBlock(hasDiscontinuity, currentSegments))
        }

        return M3u8Structure(globalHeaders, blocks)
    }
}