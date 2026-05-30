package com.example.kmp_demo.core.videosource

import com.example.kmp_demo.core.videosource.domain.VideoSource

/**
 * 视频源解析器。
 *
 * 将资源站点 API 返回的原始数据（vod_play_url）解析为领域模型 [VideoSource] 列表。
 *
 * vod_play_url 格式说明：
 *   资源站使用 "$" 分隔剧集名与 URL，"#" 分隔不同剧集。
 *   例如: "第01集$https://example.com/1.mp4#第02集$https://example.com/2.mp4"
 *
 * 职责单一：仅负责数据格式的解析与转换。
 */
object VideoSourceParser {
    private val DIRECT_EXTENSIONS = setOf(".m3u8", ".mp4", ".flv", ".ts", ".webm", ".mkv")


    /**
     * 将单个站点的 API 响应解析为 [VideoSource] 列表。
     *
     * @param response 资源站 API 响应
     * @param siteName 站点名称（用于标记来源）
     * @return 解析后的播放源列表
     */
    fun parse(response: VideoSourceApiResponse, siteName: String): List<VideoSource> {
        val items = response.list ?: return emptyList()
        return items.flatMap { item ->
            parsePlayUrl(item.vodPlayUrl.orEmpty(), siteName)
        }
    }


    /**
     * 核心清洗算法：精准提取最后一个有效的直链地址
     */
    fun sanitizeVideoUrl(urlRaw: String): String {
        if (urlRaw.isBlank()) return urlRaw

        // 1. 抓取该脏串中所有潜伏的 http(s) 链接
        val httpUrlPattern = Regex("""https?://[^\s"'<>，。、；：（）()【】\[\]{}#]+""")
        val allMatches = httpUrlPattern.findAll(urlRaw).map { it.value.trim() }.toList()

        if (allMatches.isEmpty()) return urlRaw

        // 2. 核心鲁棒策略：从后往前找（倒序遍历），谁带视频后缀，谁就是真的视频流
        for (i in allMatches.indices.reversed()) {
            val currentUrl = allMatches[i]
            val lowerUrl = currentUrl.lowercase()
            if (DIRECT_EXTENSIONS.any { lowerUrl.contains(it) }) {
                return currentUrl
            }
        }

        // 3. 兜底策略：如果都没带后缀（某些防盗链动态接口），直接拿最后一个 http 链接
        return allMatches.last()
    }

    /**
     * 解析整个剧集文本
     */
    fun parsePlayUrl(playUrlRaw: String, siteName: String): List<VideoSource> {
        if (playUrlRaw.isBlank()) return emptyList()

        return playUrlRaw.split("#")
            .filter { it.isNotBlank() }
            .mapNotNull { episodeRaw ->
                val trimmed = episodeRaw.trim()

                // 1. 直接用 "$" 把这一个剧集的所有片段切开，并过滤掉空碎片
                // 例如切成: ["https://vip.../share/xxx", "第01集", "https://vip.../index.m3u8"]
                val tokens = trimmed.split("$").filter { it.isNotBlank() }
                if (tokens.isEmpty()) return@mapNotNull null

                // 2. 寻找真正的视频直链（带 m3u8/.mp4 等后缀的碎片）
                // 资源站的真直链必然在最后面，我们从后往前找
                var realVideoUrl = ""
                for (i in tokens.indices.reversed()) {
                    val token = tokens[i].trim()
                    if (DIRECT_EXTENSIONS.any { token.lowercase().contains(it) }) {
                        realVideoUrl = token
                        break
                    }
                }

                // 3. 如果没找到带后缀的，就退而求其次，拿最后一个以 http 开头的碎片（兜底方案）
                if (realVideoUrl.isBlank()) {
                    realVideoUrl = tokens.lastOrNull { it.trim().startsWith("http", ignoreCase = true) }?.trim() ?: ""
                }

                // 如果连一个合法的 URL 碎片都没捞到，说明这行数据彻底报废
                if (realVideoUrl.isBlank()) return@mapNotNull null

                // 4. 寻找剧集名称
                // 既然找到了 realVideoUrl，那么只要不是这个 URL，且不以 http 开头的非空字符串，就是我们要的剧集名（比如 "第01集"）
                // 我们同样从后往前找离视频链接最近的那个人类文本
                var episodeName = "默认"
                for (i in tokens.indices.reversed()) {
                    val token = tokens[i].trim()
                    if (token != realVideoUrl && !token.startsWith("http", ignoreCase = true)) {
                        episodeName = token
                        break
                    }
                }

                // 5. 组装输出
                VideoSource(
                    url = realVideoUrl,
                    quality = episodeName, // 成功提取出 "第01集"
                    format = detectFormat(realVideoUrl),
                    size = 0L,
                    sourceSite = siteName,
                )
            }
    }

    private fun detectFormat(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") || lower.contains("m3u8") -> "m3u8"
            lower.contains(".mp4") -> "mp4"
            lower.contains(".flv") -> "flv"
            lower.contains(".ts") -> "ts"
            lower.contains("magnet:") -> "magnet"
            else -> "unknown"
        }
    }
}
