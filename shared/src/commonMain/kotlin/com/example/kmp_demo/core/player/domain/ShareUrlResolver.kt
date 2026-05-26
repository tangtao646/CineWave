package com.example.kmp_demo.core.player.domain

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * 分享链接解析器 - 增强版。
 * 支持递归解析、Base64 解码、URL 解码以及针对资源站（如非凡资源）的深度嗅探。
 */
class ShareUrlResolver(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val MAX_RECURSION_DEPTH = 3

        // 直接的视频文件扩展名
        private val DIRECT_VIDEO_EXTENSIONS = setOf(
            ".m3u8", ".mp4", ".flv", ".ts", ".webm",
            ".mkv", ".avi", ".mov", ".wmv", ".3gp"
        )

        // 已知的分享链接路径模式
        private val SHARE_PATH_PATTERNS = listOf(
            Regex("""/(?:share|play|player|vod|video|v)/(?:[\w-]+)"""),
            Regex("""/(?:index\.php)?\?.*[&?](?:vid|id|url)=.*"""),
        )

        // HTML 中提取视频 URL 的增强正则模式
        private val VIDEO_URL_PATTERNS = listOf(
            // 常见的 JS 变量赋值 (如 var main = "...", var url = "...")
            Regex("""(?:var|let|const)\s+(?:main|url|vurl|play_url|m3u8_url)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            // 常见的 video.js / plyr / dplayer 等播放器的 data-source 或 data-config
            Regex("""data[-.]?(?:src|source|url|config)\s*=\s*["']([^"']+\.(?:m3u8|mp4|flv)[^"']*)["']""", RegexOption.IGNORE_CASE),
            // 直接的 video/source 标签
            Regex("""<(?:video|source)[^>]*\s+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            // JavaScript 对象中的视频 URL
            Regex("""["'](?:url|src|link|video|videoUrl|playUrl)["']\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            // 通用的 m3u8 字符串匹配（最后兜底）
            Regex("""["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        )

        // 已知的 JSON API 响应中视频 URL 的字段名
        private val JSON_URL_FIELDS = listOf(
            "url", "src", "link", "video", "videoUrl", "videoSrc",
            "playUrl", "playSrc", "m3u8", "hls", "stream", "source", "data"
        )
    }

    /**
     * 检测 URL 是否为直接的视频流 URL。
     */
    fun isDirectVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        if (DIRECT_VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }) return true
        if (lowerUrl.startsWith("rtmp://") || lowerUrl.startsWith("rtsp://") || lowerUrl.startsWith("mms://")) return true
        return false
    }

    /**
     * 检测 URL 是否为分享链接（需要解析）。
     */
    fun isShareUrl(url: String): Boolean {
        if (isDirectVideoUrl(url)) return false
        val lowerUrl = url.lowercase()
        return SHARE_PATH_PATTERNS.any { it.containsMatchIn(lowerUrl) } || 
               lowerUrl.contains("/share/") || lowerUrl.contains("/play/")
    }

    /**
     * 解析分享链接，提取实际的视频流 URL。
     * 
     * @param url 分享链接
     * @param headers 可选的自定义请求头
     * @param depth 当前递归深度
     */
    suspend fun resolve(
        url: String, 
        headers: Map<String, String>? = null, 
        depth: Int = 0
    ): String {
        if (depth > MAX_RECURSION_DEPTH) return url
        if (isDirectVideoUrl(url)) return url

        return try {
            val response: HttpResponse = httpClient.get(url) {
                // 伪装成现代 Chrome 浏览器
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
                
                // 必须传完整的 Referer，而不仅仅是域名
                header(HttpHeaders.Referrer, url)
                
                headers?.forEach { (k, v) -> header(k, v) }
            }

            if (response.status != HttpStatusCode.OK) {
                println("[ShareUrlResolver] HTTP Error: ${response.status} for $url")
                return url
            }

            val contentType = response.contentType()
            val body = response.bodyAsText()

            when {
                // 处理 JSON 响应
                contentType?.match(ContentType.Application.Json) == true || 
                (body.trimStart().startsWith("{") && body.trimEnd().endsWith("}")) -> {
                    val jsonUrl = resolveFromJson(body)
                    if (jsonUrl != body) resolve(jsonUrl, headers, depth + 1) else url
                }
                
                // 处理 HTML 响应
                contentType?.match(ContentType.Text.Html) == true || body.contains("<html") -> {
                    val htmlUrl = resolveFromHtml(body, url)
                    if (htmlUrl != url) {
                        // 发现新链接，递归解析（可能是 iframe 或者是动态跳转）
                        resolve(htmlUrl, headers, depth + 1)
                    } else {
                        url
                    }
                }
                
                else -> url
            }
        } catch (e: Exception) {
            println("[ShareUrlResolver] Resolution failed for $url: ${e.message}")
            url
        }
    }

    private fun resolveFromHtml(html: String, pageUrl: String): String {
        // 1. 尝试从增强的正则模式中匹配
        for (pattern in VIDEO_URL_PATTERNS) {
            val match = pattern.find(html)
            if (match != null) {
                val rawUrl = match.groupValues[1].trim()
                val decodedUrl = smartDecode(rawUrl)
                if (decodedUrl.isNotBlank()) {
                    val absoluteUrl = resolveRelativeUrl(decodedUrl, pageUrl)
                    // 如果已经是视频地址，直接返回；否则可能是一个嵌套页面
                    return absoluteUrl
                }
            }
        }

        // 2. 专门处理嵌套 iframe
        val iframePattern = Regex("""<iframe[^>]*\s+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val iframeMatch = iframePattern.find(html)
        if (iframeMatch != null) {
            val iframeSrc = iframeMatch.groupValues[1].trim()
            if (iframeSrc.isNotBlank() && !iframeSrc.startsWith("about:")) {
                return resolveRelativeUrl(iframeSrc, pageUrl)
            }
        }

        return pageUrl
    }

    private fun resolveFromJson(json: String): String {
        for (field in JSON_URL_FIELDS) {
            val pattern = Regex("""["']$field["']\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val match = pattern.find(json)
            if (match != null) {
                val url = smartDecode(match.groupValues[1].trim())
                if (url.isNotBlank()) return url
            }
        }
        return json
    }

    /**
     * 智能解码：尝试 URL 解码和 Base64 解码。
     */
    private fun smartDecode(input: String): String {
        var result = input
        
        // 1. 尝试 URL 解码 (针对 %3A%2F%2F 等)
        if (result.contains("%")) {
            try {
                result = result.decodeURLQueryComponent()
            } catch (_: Exception) {}
        }

        // 2. 尝试 Base64 解码 (针对加密地址)
        // 典型的 Base64 视频地址长度较长且没有明显的特殊字符
        if (!result.contains("/") && !result.contains(":") && result.length > 20) {
            try {
                val decoded = result.decodeBase64String()
                if (decoded.startsWith("http") || decoded.contains(".m3u8")) {
                    result = decoded
                }
            } catch (_: Exception) {}
        }

        return result
    }

    private fun resolveRelativeUrl(videoUrl: String, pageUrl: String): String {
        if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://") ||
            videoUrl.startsWith("rtmp://") || videoUrl.startsWith("rtsp://")
        ) {
            return videoUrl
        }

        val domain = extractDomain(pageUrl) ?: return videoUrl

        return when {
            videoUrl.startsWith("//") -> "https:$videoUrl"
            videoUrl.startsWith("/") -> "${domain.removeSuffix("/")}$videoUrl"
            else -> {
                val basePath = pageUrl.substringBeforeLast("/")
                "$basePath/$videoUrl"
            }
        }
    }

    private fun extractDomain(url: String): String? {
        val pattern = Regex("""(https?://[^/]+)""")
        return pattern.find(url)?.groupValues?.get(1)
    }
}
