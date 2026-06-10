package com.example.kmp_demo.core.player.cache

import com.example.kmp_demo.core.PlatformLogger

/**
 * 基于特征规则的广告切片过滤器 (极简安全版)
 * * 核心微调：
 * 1. 彻底移除高误杀率的短词全局扫描（如 ad, gg, api 等），全面释放正片误杀。
 * 2. 移除泛化域名后缀正则，防止误伤套了免费或新通用顶级域名的正片 CDN 节点。
 * 3. 仅保留资源站特有的插播跳转特征（如 adjump, jump）和显式黑产广告词。
 */
class DefaultAdSegmentFilter(
    private val additionalKeywords: List<String> = emptyList(),
) : AdSegmentFilter {

    /** * 极其严苛、极低误杀率的广告关键词黑名单
     * 仅保留带有明显“广告插播/跳转”意图的词，以及极难在正片命中的菠菜词
     */
    private val adKeywords: List<String> = listOf(
        "adjump", "ad_jump", "jumpad",
        "casino", "gambling", "guanggao"
    ) + additionalKeywords.map { it.lowercase() }

    override fun isAdSegment(line: String, m3u8BaseUrl: String): Boolean {
        val trimmed = line.trim()

        // 1. 严格守门：只检测包含 .ts 的行
        if (!trimmed.contains(".ts", ignoreCase = true)) return false

        val lower = trimmed.lowercase()

        // 2. 核心特征扫描：仅在包含特定广告跳转行为词时触发拦截
        if (adKeywords.any { keyword -> lower.contains(keyword) }) {
            PlatformLogger.d("AdFilter", "精确命中广告特征词，拦截切片: $trimmed")
            return true
        }

        // 3. 其余情况（包括所有的跨域 CDN 节点、动态数字/字母域名等）一律视为正片，安全放行！
        return false
    }
}