package com.example.kmp_demo.core.player.cache

/**
 * M3U8 播放列表清洗器。
 *
 * 核心职责：从原始 M3U8 文本中移除广告切片行及其关联的 #EXTINF 标签，
 * 生成"干净"的播放列表，确保 VLCJ 拿到的切片列表百分之百纯净。
 *
 * ## 工作原理
 *
 * M3U8 播放列表中，每个切片通常由两行组成：
 * ```
 * #EXTINF:10.000,          ← 切片描述（时长、标题等）
 * out001.ts                ← 切片 URL
 * ```
 *
 * 广告切片过滤的逻辑是：
 * 1. 遍历 M3U8 的每一行
 * 2. 如果某行是广告切片 URL（由 [AdSegmentFilter] 判定）
 * 3. 同时移除该行及其上一行的 #EXTINF 标签
 * 4. 如果 #EXTINF 行后面跟着广告切片，也跳过 #EXTINF 行
 *
 * ## 使用示例
 *
 * ```kotlin
 * val filter = DefaultAdSegmentFilter()
 * val sanitizer = M3u8Sanitizer(filter)
 *
 * val rawM3u8 = """
 *     #EXTM3U
 *     #EXTINF:10.000,
 *     out001.ts
 *     #EXTINF:10.000,
 *     https://asdf.top/gucheng.ts
 *     #EXTINF:10.000,
 *     out002.ts
 *     #EXT-X-ENDLIST
 * """.trimIndent()
 *
 * val cleanM3u8 = sanitizer.sanitize(rawM3u8, "https://vod.example.com/live.m3u8")
 * // 结果：移除了广告切片行及其 #EXTINF
 * ```
 *
 * @param adSegmentFilter 广告切片检测策略
 */
class M3u8Sanitizer(
    private val adSegmentFilter: AdSegmentFilter,
) {

    /**
     * 清洗 M3U8 播放列表，移除广告切片。
     *
     * @param rawContent 原始 M3U8 文本内容
     * @param m3u8BaseUrl 原始 M3U8 的 URL，用于域名对比检测
     * @return 清洗后的 M3U8 文本内容
     */
    fun sanitize(rawContent: String, m3u8BaseUrl: String): String {
        val lines = rawContent.split("\n")
        val result = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 情况 1：当前行是 #EXTINF，检查下一行是否是广告切片
            if (trimmed.startsWith("#EXTINF") && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (adSegmentFilter.isAdSegment(nextLine, m3u8BaseUrl)) {
                    // 跳过 #EXTINF 和广告切片两行
                    i += 2
                    continue
                }
                // #EXTINF 后跟正常切片，保留
                result.add(line)
                i++
                continue
            }

            // 情况 2：当前行本身就是广告切片（没有前面的 #EXTINF）
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
            result.add(line)
            i++
        }

        return result.joinToString("\n")
    }

    /**
     * 统计被过滤的广告切片数量。
     *
     * 与 [sanitize] 逻辑相同，但不返回清洗后的文本，
     * 只返回被移除的广告切片数量。用于日志和监控。
     *
     * @param rawContent 原始 M3U8 文本内容
     * @param m3u8BaseUrl 原始 M3U8 的 URL
     * @return 被过滤的广告切片数量
     */
    fun countAdSegments(rawContent: String, m3u8BaseUrl: String): Int {
        val lines = rawContent.split("\n")
        var adCount = 0
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.startsWith("#EXTINF") && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (adSegmentFilter.isAdSegment(nextLine, m3u8BaseUrl)) {
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
}
