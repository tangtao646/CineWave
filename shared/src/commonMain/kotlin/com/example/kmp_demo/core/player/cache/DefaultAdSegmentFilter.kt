package com.example.kmp_demo.core.player.cache

/**
 * 基于特征规则的广告切片默认过滤器。
 *
 * ## 检测策略（按优先级）
 *
 * 1. **非 .ts 行放行**：只检测包含 `.ts` 的 URL 行
 * 2. **关键词黑名单**：URL 路径中包含 `ad`、`gg`、`macau`、`casino` 等广告特征词
 * 3. **域名跨源检测**：切片域名与 M3U8 域名不同 → 判定为广告（正片切片通常与 M3U8 同域）
 * 4. **广告域名模式**：匹配已知的广告/盗版域名正则模式
 *
 * ## 线程安全
 *
 * 无状态单例，所有字段为 `val` 不可变，可安全并发调用。
 *
 * ## 可扩展性
 *
 * 如需自定义规则，可实现 [AdSegmentFilter] 接口，
 * 或在构造函数中传入额外的关键词/域名模式。
 *
 * @param additionalKeywords 额外广告关键词，追加到默认黑名单之后
 * @param additionalDomainPatterns 额外广告域名正则，追加到默认模式之后
 */
class DefaultAdSegmentFilter(
    private val additionalKeywords: List<String> = emptyList(),
    private val additionalDomainPatterns: List<Regex> = emptyList(),
) : AdSegmentFilter {

    /** 广告关键词黑名单（小写，匹配时忽略大小写） */
    private val adKeywords: List<String> = listOf(
        "ad", "ads", "adv", "advertisement",
        "gg", "ggao", "guanggao",
        "macau", "casino", "gambling", "bet", "poker",
        "sex", "porn", "xxx", "adult",
        "banner", "popup", "float",
        "track", "analytics", "monitor",
        "tj", "tongji", "stat",
    ) + additionalKeywords.map { it.lowercase() }

    /** 广告域名正则模式 */
    private val adDomainPatterns: List<Regex> = listOf(
        // 常见免费/黑产域名后缀
        Regex("^https?://[^/]*\\.(top|xyz|club|work|gq|ml|tk|cf|ga|gdn|bid|trade|webcam|download|review|stream|racing|win|date|science|party|loan|men|mom|lol|kim|link|click|rest|bar|country|faith|quest|racing|accountant|pics|trade|website|space|site|live|online|tech|store|blog|fun|host|press|wiki|name|info|pro|cc|tv|pw|vc|wang|xin|我爱你)\b/.*"),
        // 随机字母域名（黑产常用：asdf.top, qwer.xyz 等）
        Regex("^https?://[^/]*[a-z]{4,6}\\.(top|xyz|club|work|gq|ml|tk)/.*"),
        // 数字域名（黑产常用：123.xyz, 456.club 等）
        Regex("^https?://[^/]*\\d+\\.(top|xyz|club|work|gq|ml|tk)/.*"),
        // 包含 ip 地址的域名（黑产常用：http://192.168.x.x/...）
        Regex("^https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/.*"),
    ) + additionalDomainPatterns

    override fun isAdSegment(line: String, m3u8BaseUrl: String): Boolean {
        val trimmed = line.trim()

        // 只检测 .ts 切片 URL
        if (!trimmed.contains(".ts", ignoreCase = true)) return false

        // 只检测 HTTP(S) URL（忽略相对路径、注释等）
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)) return false

        val lower = trimmed.lowercase()

        // 策略 1：关键词黑名单检测
        if (adKeywords.any { keyword -> lower.contains(keyword) }) {
            return true
        }

        // 策略 2：域名跨源检测
        val m3u8Domain = extractDomain(m3u8BaseUrl)
        val segmentDomain = extractDomain(trimmed)
        if (m3u8Domain != null && segmentDomain != null && segmentDomain != m3u8Domain) {
            return true
        }

        // 策略 3：广告域名模式匹配
        if (adDomainPatterns.any { pattern -> pattern.matches(trimmed) }) {
            return true
        }

        return false
    }

    /**
     * 从 URL 中提取域名部分。
     *
     * 例如：`https://vod.example.com/path/file.ts` → `vod.example.com`
     *
     * @param url 完整 URL
     * @return 域名，如果无法解析则返回 null
     */
    private fun extractDomain(url: String): String? {
        return try {
            val afterProtocol = url.substringAfter("://")
            afterProtocol.substringBefore("/").substringBefore(":").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
