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
     * 解析 vod_play_url 字符串。
     *
     * 格式: "episode1$url1#episode2$url2#episode3$url3"
     * 其中 "$" 分隔剧集名与 URL，"#" 分隔不同剧集。
     *
     * @param playUrlRaw 原始播放 URL 字符串
     * @param siteName 站点名称
     * @return 解析后的 [VideoSource] 列表
     */
    fun parsePlayUrl(playUrlRaw: String, siteName: String): List<VideoSource> {
        if (playUrlRaw.isBlank()) return emptyList()

        // 按 "#" 分割不同剧集
        val episodes = playUrlRaw.split("#")
        val results = mutableListOf<VideoSource>()

        for (episode in episodes) {
            val parts = episode.split("$", limit = 2)
            if (parts.size == 2) {
                val episodeName = parts[0].trim()
                val url = parts[1].trim()

                if (url.isNotBlank()) {
                    results.add(
                        VideoSource(
                            url = url,
                            quality = episodeName,
                            format = detectFormat(url),
                            size = 0L,
                            sourceSite = siteName,
                        )
                    )
                }
            }
        }

        return results
    }

    /**
     * 根据 URL 后缀检测视频格式。
     */
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
