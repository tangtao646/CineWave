package com.example.kmp_demo.core.player.domain

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * 分享链接解析器 - 增强修复版
 */
class ShareUrlResolver(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val MAX_RECURSION_DEPTH = 3

        private val DIRECT_VIDEO_EXTENSIONS = setOf(
            ".m3u8", ".mp4", ".flv", ".ts", ".webm",
            ".mkv", ".avi", ".mov", ".wmv", ".3gp"
        )

        private val SHARE_PATH_PATTERNS = listOf(
            Regex("""/(?:share|play|player|vod|video|v)/(?:[\w-]+)"""),
            Regex("""/(?:index\.php)?\?.*[&?](?:vid|id|url)=.*"""),
        )

        private val VIDEO_URL_PATTERNS = listOf(
            Regex("""(?:var|let|const)\s+(?:main|url|vurl|play_url|m3u8_url)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""data[-.]?(?:src|source|url|config)\s*=\s*["']([^"']+\.(?:m3u8|mp4|flv)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""<(?:video|source)[^>]*\s+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](?:url|src|link|video|videoUrl|playUrl)["']\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        )

        private val JSON_URL_FIELDS = listOf(
            "url", "src", "link", "video", "videoUrl", "videoSrc",
            "playUrl", "playSrc", "m3u8", "hls", "stream", "source", "data"
        )
    }

    fun isDirectVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        if (DIRECT_VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }) return true
        if (lowerUrl.startsWith("rtmp://") || lowerUrl.startsWith("rtsp://") || lowerUrl.startsWith("mms://")) return true
        return false
    }

    fun isShareUrl(url: String): Boolean {
        if (isDirectVideoUrl(url)) return false
        val lowerUrl = url.lowercase()
        return SHARE_PATH_PATTERNS.any { it.containsMatchIn(lowerUrl) } ||
                lowerUrl.contains("/share/") || lowerUrl.contains("/play/")
    }

    suspend fun resolve(
        url: String,
        headers: Map<String, String>? = null,
        depth: Int = 0
    ): String {
        // 每次递归前检查当前协程是否还活着，及时收手
        currentCoroutineContext().ensureActive()

        if (depth > MAX_RECURSION_DEPTH) return url
        if (isDirectVideoUrl(url)) return url

        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
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
                contentType?.match(ContentType.Application.Json) == true ||
                        (body.trimStart().startsWith("{") && body.trimEnd().endsWith("}")) -> {
                    val jsonUrl = resolveFromJson(body)
                    // 修复：清洗可能夹带的多余特殊字符（如日志里的 $$$ 干扰）
                    val cleanJsonUrl = sanitizeUrl(jsonUrl)
                    if (cleanJsonUrl != body && cleanJsonUrl.isNotBlank()) {
                        resolve(cleanJsonUrl, headers, depth + 1)
                    } else url
                }

                contentType?.match(ContentType.Text.Html) == true || body.contains("<html") -> {
                    val htmlUrl = resolveFromHtml(body, url)
                    val cleanHtmlUrl = sanitizeUrl(htmlUrl)
                    if (cleanHtmlUrl != url && cleanHtmlUrl.isNotBlank()) {
                        resolve(cleanHtmlUrl, headers, depth + 1)
                    } else {
                        url
                    }
                }

                else -> url
            }
        } catch (e: CancellationException) {
            // 关键：必须重新抛出取消异常，通知上层“我是因为界面销毁才退出的”
            throw e
        } catch (e: Exception) {
            println("[ShareUrlResolver] Resolution failed for $url: ${e.message}")
            url
        }
    }

    private fun resolveFromHtml(html: String, pageUrl: String): String {
        for (pattern in VIDEO_URL_PATTERNS) {
            val match = pattern.find(html)
            if (match != null) {
                val rawUrl = match.groupValues[1].trim()
                val decodedUrl = smartDecode(rawUrl)
                if (decodedUrl.isNotBlank()) {
                    return resolveRelativeUrl(decodedUrl, pageUrl)
                }
            }
        }

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

    private fun smartDecode(input: String): String {
        var result = input
        if (result.contains("%")) {
            try {
                result = result.decodeURLQueryComponent()
            } catch (_: Exception) {}
        }

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

    /**
     * 清洗从不规范的 HTML/JSON 变量中误抓取的非 URL 脏字符。
     *
     * 处理场景：
     * 1. `$$$` 投毒：如 `share/xxx$$$第01集$https://real.m3u8` → 提取 `https://real.m3u8`
     * 2. `$` 分隔符残留：如 `第01集$https://real.m3u8` → 提取 `https://real.m3u8`
     * 3. 剧集名前缀：如 `第01集https://real.m3u8` → 提取 `https://real.m3u8`
     * 4. 多重 URL 拼接：取最后一个有效的 http(s) URL
     * 5. 非 URL 字符前后缀：trim 掉空白和不可见字符
     */
    private fun sanitizeUrl(url: String): String {
        var result = url.trim()

        // 如果已经是纯净的 URL，直接返回
        if (isDirectVideoUrl(result)) return result

        // 策略1：从字符串中提取所有 http(s) URL，取最后一个（最可能是真正的视频地址）
        val httpUrlPattern = Regex("""https?://[^\s"'<>，。、；：（）()【】\[\]{}#]+""")
        val allMatches = httpUrlPattern.findAll(result).toList()
        if (allMatches.isNotEmpty()) {
            // 取最后一个 http URL，因为前面的可能是被污染的分享页 URL
            val candidate = allMatches.last().value.trimEnd('.', ',', ';', ':', ')', '】', '】')
            if (isDirectVideoUrl(candidate) || candidate.contains(".m3u8") || candidate.contains(".mp4") || candidate.contains(".flv")) {
                return candidate
            }
            // 如果不是直接视频 URL，但至少是一个合法的 http URL，也返回它
            return candidate
        }

        // 策略2：如果包含 $$$，尝试提取 $$$ 之后的内容
        if (result.contains("$$$")) {
            val afterDollar = result.substringAfterLast("$$$")
            // 检查 $$$ 之后是否包含 http URL
            val urlInAfter = httpUrlPattern.find(afterDollar)
            if (urlInAfter != null) {
                return urlInAfter.value
            }
            // 否则返回 $$$ 之后的内容（可能已经是纯净 URL）
            if (afterDollar.isNotBlank()) return afterDollar.trim()
        }

        // 策略3：如果包含 $ 分隔符（如 "第01集$https://..."），取 $ 之后的部分
        if (result.contains("$") && !result.startsWith("http")) {
            val parts = result.split("$")
            // 从后往前找第一个包含 http 的部分
            for (i in parts.lastIndex downTo 0) {
                val part = parts[i].trim()
                if (part.startsWith("http://") || part.startsWith("https://")) {
                    return part
                }
            }
        }

        // 策略4：去除常见的剧集名前缀（如 "第01集"、"第1集"、"第01话" 等）
        val episodePrefixPattern = Regex("""^第\d+[集话期季]""")
        result = result.replace(episodePrefixPattern, "").trim()

        // 如果去除前缀后变成了有效的 http URL，返回它
        if (result.startsWith("http://") || result.startsWith("https://")) {
            return result
        }

        return url.trim()
    }
}
