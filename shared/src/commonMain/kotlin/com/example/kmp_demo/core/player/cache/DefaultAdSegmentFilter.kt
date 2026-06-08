package com.example.kmp_demo.core.player.cache

import com.example.kmp_demo.core.PlatformLogger

/**
 * 基于特征规则的广告切片默认过滤器。
 * * 已修正：放行切片与M3U8不同域名的三方CDN正片情况，避免误伤。
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

    /** 广告域名特征正则模式 */
    private val adDomainPatterns: List<Regex> = listOf(
        // 常见免费/黑产域名后缀
        Regex("^https?://[^/]*\\.(top|xyz|club|work|gq|ml|tk|cf|ga|gdn|bid|trade|webcam|download|review|stream|racing|win|date|science|party|loan|men|mom|lol|kim|link|click|rest|bar|country|faith|quest|racing|accountant|pics|trade|website|space|site|live|online|tech|store|blog|fun|host|press|wiki|name|info|pro|cc|tv|pw|vc|wang|xin|我爱你)\b/.*"),
        // 随机字母域名（黑产常用：asdf.top, qwer.xyz 等）
        Regex("^https?://[^/]*[a-z]{4,6}\\.(top|xyz|club|work|gq|ml|tk)/.*"),
        // 数字域名（黑产常用：123.xyz, 456.club 等）
        Regex("^https?://[^/]*\\d+\\.(top|xyz|club|work|gq|ml|tk)/.*")
        // 注意：移除了纯 IP 匹配，因为很多 CDN 切片节点会直接使用带有端口的公网 IP
    ) + additionalDomainPatterns

    /** * 明确的已知广告/黑产域名黑名单关键字（用于代替误伤率极高的跨源检测）
     * 只有切片包含这些特征，且与 M3U8 异域时，才判定为广告。
     */
    private val knownAdDomainKeywords = listOf(
        "ad", "gg", "api", "stat", "click", "pop", "traffic", "telemetry"
    )

    override fun isAdSegment(line: String, m3u8BaseUrl: String): Boolean {
        val trimmed = line.trim()

        // 1. 严格守门：只检测包含 .ts 的行
        if (!trimmed.contains(".ts", ignoreCase = true)) return false

        val lower = trimmed.lowercase()

        // ==================== 【第一层：新型缝合怪硬核拦截】 ====================
        if (lower.contains("adjump") || lower.contains("jump") || lower.contains("ad_jump")) {
            return true
        }

        // ==================== 【第二层：全局关键词扫描】 ====================
        if (adKeywords.any { keyword -> lower.contains(keyword) }) {
            return true
        }

        // ==================== 【第三层：绝对路径的高级域名清洗与安全放行】 ====================
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {

            val m3u8Domain = extractDomain(m3u8BaseUrl)
            val segmentDomain = extractDomain(trimmed)

            if (m3u8Domain != null && segmentDomain != null && segmentDomain != m3u8Domain) {
                // 【核心重构点】：即便域名跨源（不同域），也不再直接一刀切误杀。
                // 只有当异域的切片域名中同时包含了明确的广告特征词时（如 xxx.ad-system.com），才触发拦截。
                val isSuspiciousDomain = knownAdDomainKeywords.any { segmentDomain.contains(it) }
                if (isSuspiciousDomain) {
                    PlatformLogger.d("AdFilter", "拦截跨域广告域名: $segmentDomain")
                    return true
                }

                // 否则，视为合法的第三方视频托管 CDN (如 p.jisuts.com)，安全放行！
            }

            // 策略 B：全套广告域名正则模式匹配
            if (adDomainPatterns.any { pattern -> pattern.matches(trimmed) }) {
                return true
            }
        }

        return false
    }

    private fun extractDomain(url: String): String? {
        return try {
            val afterProtocol = url.substringAfter("://")
            afterProtocol.substringBefore("/").substringBefore(":").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}