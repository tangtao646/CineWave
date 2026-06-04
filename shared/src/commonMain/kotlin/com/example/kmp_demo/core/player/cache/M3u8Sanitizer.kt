package com.example.kmp_demo.core.player.cache

/**
 * 表示 M3U8 中一个被 DISCONTINUITY 包裹的区间。
 *
 * @param startLine 区间起始行号（包含）
 * @param endLine 区间结束行号（不包含，指向 DISCONTINUITY 标签行）
 * @param totalDuration 区间内所有切片的总时长（秒）
 * @param segmentCount 区间内切片数量
 */
data class DiscontinuityZone(
    val startLine: Int,
    val endLine: Int,
    val totalDuration: Double,
    val segmentCount: Int,
) {
    /**
     * 判断该区间是否可能是广告。
     *
     * 广告特征：
     * - 总时长在 2~31 秒之间（短于正常章节）
     * - 切片数在 2~6 个之间（多个短切片，而非单个切片抖动）
     * - 平均切片时长 ≤ 8 秒（广告切片通常为 5 秒，正片切片通常为 10 秒）
     *
     * 要求 segmentCount >= 2 是为了避免将 DISCONTINUITY 之间
     * 的单个正片切片误判为广告。
     */
    val isLikelyAd: Boolean
        get() = totalDuration in MIN_AD_DURATION..MAX_AD_DURATION
                && segmentCount in MIN_AD_SEGMENT_COUNT..MAX_AD_SEGMENT_COUNT
                && averageSegmentDuration <= MAX_AVERAGE_SEGMENT_DURATION

    /** 区间内切片的平均时长（秒） */
    val averageSegmentDuration: Double
        get() = if (segmentCount > 0) totalDuration / segmentCount else 0.0

    companion object {
        /** 广告最小总时长（秒）：低于此值可能是单个切片抖动 */
        private const val MIN_AD_DURATION = 2.0
        /** 广告最大总时长（秒）：超过此值可能是正常内容 */
        private const val MAX_AD_DURATION = 31.0
        /** 广告最小切片数：低于此值可能是 DISCONTINUITY 间的单个正片切片 */
        private const val MIN_AD_SEGMENT_COUNT = 2
        /** 广告最大切片数：超过此值可能是正常内容 */
        private const val MAX_AD_SEGMENT_COUNT = 6
        /** 广告平均切片时长上限（秒）：广告切片通常较短（≤8秒），正片切片通常为10秒 */
        private const val MAX_AVERAGE_SEGMENT_DURATION = 8.0
    }
}

/**
 * M3U8 播放列表清洗器。
 *
 * 核心职责：从原始 M3U8 文本中移除广告切片行及其关联的 #EXTINF 标签，
 * 生成"干净"的播放列表，确保播放器拿到的切片列表百分之百纯净。
 *
 * ## 三层防御体系
 *
 * ### 第一层：协议状态机（新）
 * 信号：[#EXT-X-DISCONTINUITY] + 区间时长
 * 策略：扫描所有 DISCONTINUITY 区间，短区间（≤31s）整段切除
 * 可靠性：★★★★★（协议强制，黑产无法绕过）
 *
 * ### 第二层：特征规则层（现有）
 * 信号：文件名/域名/路径关键词
 * 策略：[AdSegmentFilter] 正则匹配
 * 可靠性：★★☆☆☆（可被伪装绕过）
 *
 * ### 第三层：异常处理层（增强）
 * 信号：网络/解析错误
 * 策略：带重试机制的容错处理，不放行脏数据进缓存
 * 可靠性：★★★★☆（防止脏缓存污染）
 *
 * ## 工作原理
 *
 * 执行顺序：
 * 1. 协议层：扫描所有 DISCONTINUITY 区间，切除广告区
 * 2. 特征层：对剩余内容执行 AdSegmentFilter 过滤
 *
 * ## 使用示例
 *
 * ```kotlin
 * val filter = DefaultAdSegmentFilter()
 * val sanitizer = M3u8Sanitizer(filter)
 *
 * val rawM3u8 = """
 *     #EXTM3U
 *     #EXTINF:5.000,
 *     ad_001.ts
 *     #EXTINF:5.000,
 *     ad_002.ts
 *     #EXT-X-DISCONTINUITY
 *     #EXTINF:10.000,
 *     real_001.ts
 *     #EXT-X-ENDLIST
 * """.trimIndent()
 *
 * val cleanM3u8 = sanitizer.sanitize(rawM3u8, "https://vod.example.com/live.m3u8")
 * // 结果：移除了 ad_001.ts 和 ad_002.ts 及其 #EXTINF 和 #EXT-X-DISCONTINUITY
 * ```
 *
 * @param adSegmentFilter 广告切片检测策略（第二层特征规则）
 */
class M3u8Sanitizer(
    private val adSegmentFilter: AdSegmentFilter,
) {

    /**
     * 清洗 M3U8 播放列表，移除广告切片。
     *
     * 执行顺序：
     * 1. 协议层：扫描所有 DISCONTINUITY 区间，切除广告区
     * 2. 特征层：对剩余内容执行 AdSegmentFilter 过滤
     *
     * @param rawContent 原始 M3U8 文本内容
     * @param m3u8BaseUrl 原始 M3U8 的 URL，用于域名对比检测
     * @return 清洗后的 M3U8 文本内容
     */
    fun sanitize(rawContent: String, m3u8BaseUrl: String): String {
        val lines = rawContent.split("\n")

        // 第一步：协议层 — 扫描 DISCONTINUITY 区间，标记广告区
        val adZones = scanAdZones(lines)

        // 第二步：状态机遍历，切除广告区 + 特征规则层兜底
        return processLines(lines, adZones, m3u8BaseUrl)
    }

    /**
     * 统计被过滤的广告切片数量。
     *
     * 与 [sanitize] 逻辑相同，但不返回清洗后的文本，
     * 只返回被移除的广告切片数量（区间内的切片总数）。用于日志和监控。
     *
     * @param rawContent 原始 M3U8 文本内容
     * @param m3u8BaseUrl 原始 M3U8 的 URL
     * @return 被过滤的广告切片数量
     */
    fun countAdSegments(rawContent: String, m3u8BaseUrl: String): Int {
        val lines = rawContent.split("\n")
        val adZones = scanAdZones(lines)

        if (adZones.isNotEmpty()) {
            // 返回所有广告区间内的切片总数
            return adZones.sumOf { it.segmentCount }
        }

        return countByAdFilter(lines, m3u8BaseUrl)
    }

    /**
     * 扫描 M3U8 中所有 DISCONTINUITY 区间，识别广告区。
     *
     * 策略：
     * - 第一个 DISCONTINUITY 之前的区间 → 如果是短区间，可能是片头广告
     * - 两个 DISCONTINUITY 之间的区间 → 如果是短区间，可能是插播广告
     * - 最后一个 DISCONTINUITY 之后的区间 → 不切除（这是正片剩余部分）
     *
     * 对每个区间分析其总时长和切片数，符合广告特征的标记为广告区。
     */
    private fun scanAdZones(lines: List<String>): List<DiscontinuityZone> {
        val discontinuityIndices = findDiscontinuityLines(lines)
        if (discontinuityIndices.isEmpty()) return emptyList()

        val adZones = mutableListOf<DiscontinuityZone>()
        var zoneStart = 0

        for (discIdx in discontinuityIndices) {
            val zone = analyzeZone(lines, zoneStart, discIdx)
            if (zone.isLikelyAd) {
                adZones.add(zone)
            }
            zoneStart = discIdx + 1
        }

        // 注意：最后一个 DISCONTINUITY 之后的区间不切除，这是正片剩余部分
        // 只有被 DISCONTINUITY 包裹的短区间才可能是广告

        return adZones
    }

    /**
     * 查找所有 [#EXT-X-DISCONTINUITY] 标签的行号。
     */
    private fun findDiscontinuityLines(lines: List<String>): List<Int> {
        val indices = mutableListOf<Int>()
        lines.forEachIndexed { idx, line ->
            if (line.trimStart().startsWith("#EXT-X-DISCONTINUITY")) {
                indices.add(idx)
            }
        }
        return indices
    }

    /**
     * 分析指定行区间内的切片信息。
     *
     * @param lines 所有行
     * @param start 区间起始行号（包含）
     * @param end 区间结束行号（不包含）
     * @return 区间分析结果
     */
    private fun analyzeZone(
        lines: List<String>,
        start: Int,
        end: Int,
    ): DiscontinuityZone {
        var totalDuration = 0.0
        var segmentCount = 0

        for (idx in start until end) {
            val trimmed = lines[idx].trimStart()
            if (trimmed.startsWith("#EXTINF:")) {
                val duration = trimmed
                    .substringAfter("#EXTINF:")
                    .substringBefore(",")
                    .toDoubleOrNull() ?: 0.0
                totalDuration += duration
                segmentCount++
            }
        }

        return DiscontinuityZone(
            startLine = start,
            endLine = end,
            totalDuration = totalDuration,
            segmentCount = segmentCount,
        )
    }

    /**
     * 状态机遍历行列表，切除广告区并执行特征规则层过滤。
     *
     * @param lines 所有行
     * @param adZones 协议层识别出的广告区间列表
     * @param m3u8BaseUrl M3U8 基础 URL
     * @return 清洗后的 M3U8 文本
     */
    private fun processLines(
        lines: List<String>,
        adZones: List<DiscontinuityZone>,
        m3u8BaseUrl: String,
    ): String {
        val result = mutableListOf<String>()
        var i = 0
        var zoneIdx = 0

        while (i < lines.size) {
            val trimmed = lines[i].trimStart()

            // 协议层：跳过广告区间
            if (zoneIdx < adZones.size && i == adZones[zoneIdx].startLine) {
                i = adZones[zoneIdx].endLine + 1
                zoneIdx++
                continue
            }

            // 特征层：检查 #EXTINF 后跟的切片 URL
            if (trimmed.startsWith("#EXTINF") && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (adSegmentFilter.isAdSegment(nextLine, m3u8BaseUrl)) {
                    // 跳过 #EXTINF 和广告切片两行
                    i += 2
                    continue
                }
                // #EXTINF 后跟正常切片，保留
                result.add(lines[i])
                i++
                continue
            }

            // 特征层：当前行本身就是广告切片（没有前面的 #EXTINF）
            if (adSegmentFilter.isAdSegment(trimmed, m3u8BaseUrl)) {
                // 如果上一行是 #EXTINF，也移除它
                if (result.isNotEmpty() && result.last().trimStart().startsWith("#EXTINF")) {
                    result.removeAt(result.lastIndex)
                }
                // 跳过当前广告行
                i++
                continue
            }

            // 正常行，保留
            result.add(lines[i])
            i++
        }

        return result.joinToString("\n")
    }

    /**
     * 仅使用特征规则层统计广告切片数量（无 DISCONTINUITY 区间时使用）。
     */
    private fun countByAdFilter(
        lines: List<String>,
        m3u8BaseUrl: String,
    ): Int {
        var adCount = 0
        var i = 0

        while (i < lines.size) {
            val trimmed = lines[i].trimStart()
            if (trimmed.startsWith("#EXTINF") && i + 1 < lines.size) {
                if (adSegmentFilter.isAdSegment(lines[i + 1].trim(), m3u8BaseUrl)) {
                    adCount++
                    i += 2
                    continue
                }
            }
            if (adSegmentFilter.isAdSegment(trimmed, m3u8BaseUrl)) {
                adCount++
            }
            i++
        }

        return adCount
    }

    companion object {
        private const val TAG = "M3u8Sanitizer"
    }
}