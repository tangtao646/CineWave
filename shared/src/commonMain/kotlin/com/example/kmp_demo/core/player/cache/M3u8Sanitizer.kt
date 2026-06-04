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

        // 1. 优先填入全局头部标头
        result.addAll(structure.globalHeaders)

        val blocks = structure.blocks
        var validBlockProcessedCount = 0
        var pendingDiscontinuity = false

        for (block in blocks) {
            if (block.isAdBlock) {
                // ========== 命中广告大区间块 ==========
                if (cleanStrategy == AdCleanStrategy.REPLACE_WITH_GAP) {
                    // 【Android 策略】：保持大块完整，使用不连续标签和 GAP 占位
                    if (block.hasLeadingDiscontinuity && result.size > structure.globalHeaders.size) {
                        result.add("#EXT-X-DISCONTINUITY")
                    }
                    block.segments.forEach { segment ->
                        segment.infLine?.let { result.add(it) }
                        result.add("#EXT-X-GAP")
                    }
                } else {
                    // 【Desktop 策略】：直接丢弃整个广告大块
                    // 告知状态机接下来的正片大块开头需要感知到有广告被切了
                    pendingDiscontinuity = true
                }
                continue
            }

            // ========== 健康的正片大块区间 ==========
            if (block.segments.isEmpty()) continue
            validBlockProcessedCount++

            // 计算正片大块开头是否需要补不连续标记
            val needDiscontinuity = if (cleanStrategy == AdCleanStrategy.DROP_COMPLETELY) {
                // 桌面端策略：无情隐瞒由丢广告产生的断层(pendingDiscontinuity=true时返回false)。
                // 只有流自带、且我们没动过它的原生多视角不连续标记才允许保留。
                block.hasLeadingDiscontinuity && !pendingDiscontinuity && validBlockProcessedCount > 1
            } else {
                // Android 策略：如果是原生自带断层，或者前面刚丢过广告大块（虽Android不丢大块，这里留作状态闭环），正常补齐
                (block.hasLeadingDiscontinuity || pendingDiscontinuity) &&
                        validBlockProcessedCount > 1 &&
                        result.size > structure.globalHeaders.size
            }

            if (needDiscontinuity && result.size > structure.globalHeaders.size) {
                if (!result.last().trim().startsWith("#EXT-X-DISCONTINUITY")) {
                    result.add("#EXT-X-DISCONTINUITY")
                }
                // 消费掉信号
                pendingDiscontinuity = false
            } else if (cleanStrategy == AdCleanStrategy.DROP_COMPLETELY) {
                // 🌟 核心对齐：桌面端如果因为截断广告产生了 pendingDiscontinuity 信号，
                // 在跨过 block 开头后，必须在这里将其安全消费并重置，完成桌面端状态自愈
                pendingDiscontinuity = false
            }

            // 写入正片块中的具体切片（瘦身版：彻底拿掉切片级死代码）
            block.segments.forEach { segment ->
                val isAd = adSegmentFilter.isAdSegment(segment.urlLine, m3u8BaseUrl)

                if (isAd) {
                    // 遇到零散的独立广告切片
                    if (cleanStrategy == AdCleanStrategy.REPLACE_WITH_GAP) {
                        segment.infLine?.let { result.add(it) }
                        result.add("#EXT-X-GAP")
                    }
                    // 桌面端（DROP_COMPLETELY）直接忽略，不写入 result
                } else {
                    // 遇到真正的正片切片，无论哪个平台，直接顺次写入
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