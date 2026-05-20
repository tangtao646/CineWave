package com.example.kmp_demo.core.player.cache

/**
 * M3U8 播放列表解析器。
 *
 * 解析 M3U8 文件，提取切片 URL 列表和相关信息。
 * 支持标准 M3U8、#EXTINF 标签、多码率（#EXT-X-STREAM-INF）、加密（#EXT-X-KEY）。
 *
 * @see [S6] 多码率支持：检测 #EXT-X-STREAM-INF，提取子播放列表 URL
 * @see [S7] 加密支持：检测 #EXT-X-KEY，提取加密信息
 */
class M3u8Parser {

    data class M3u8Playlist(
        val segments: List<Segment>,
        val isVod: Boolean,
        val isLive: Boolean = false,
        val isMultivariant: Boolean = false,
        val variantUrls: List<String> = emptyList(),
        val isEncrypted: Boolean = false,
        val encryption: EncryptionInfo? = null,
        val targetDuration: Double,
        val mediaSequence: Int,
    )

    data class Segment(
        val url: String,
        val duration: Double,
        val sequence: Int,
    )

    data class EncryptionInfo(
        val method: String, // "AES-128", "SAMPLE-AES"
        val uri: String,
        val iv: String? = null,
    )

    /**
     * 解析 M3U8 内容。
     *
     * @param content M3U8 文件原始内容
     * @param baseUrl 基础 URL（用于拼接相对路径）
     */
    fun parse(content: String, baseUrl: String): M3u8Playlist {
        val lines = content.lines()
        val segments = mutableListOf<Segment>()
        val variantUrls = mutableListOf<String>()
        var currentDuration = 0.0
        var isVod = false
        var targetDuration = 0.0
        var mediaSequence = 0
        var isMultivariantLine = false
        var encryption: EncryptionInfo? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-PLAYLIST-TYPE:VOD") -> isVod = true

                line.startsWith("#EXT-X-TARGETDURATION:") -> {
                    targetDuration = line.substringAfter(":").trim().toDoubleOrNull() ?: 0.0
                }

                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSequence = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }

                line.startsWith("#EXTINF:") -> {
                    val durationStr = line.substringAfter(":").substringBefore(",").trim()
                    currentDuration = durationStr.toDoubleOrNull() ?: 0.0
                }

                // S6: 多码率检测
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    isMultivariantLine = true
                }

                // S7: 加密检测
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = extractAttribute(line, "METHOD") ?: ""
                    val uri = extractAttribute(line, "URI") ?: ""
                    val iv = extractAttribute(line, "IV")
                    if (method.isNotBlank() && uri.isNotBlank()) {
                        encryption = EncryptionInfo(
                            method = method,
                            uri = resolveUrl(baseUrl, uri),
                            iv = iv,
                        )
                    }
                }

                // 多码率子播放列表 URL（#EXT-X-STREAM-INF 的下一行）
                isMultivariantLine && !line.startsWith("#") && line.isNotBlank() -> {
                    variantUrls.add(resolveUrl(baseUrl, line.trim()))
                    isMultivariantLine = false
                }

                // 切片 URL
                !line.startsWith("#") && line.isNotBlank() -> {
                    val segmentUrl = resolveUrl(baseUrl, line.trim())
                    segments.add(Segment(
                        url = segmentUrl,
                        duration = currentDuration,
                        sequence = mediaSequence + segments.size
                    ))
                    currentDuration = 0.0
                }
            }
        }

        val isMultivariant = variantUrls.isNotEmpty()
        val isEncrypted = encryption != null

        return M3u8Playlist(
            segments = segments,
            isVod = isVod,
            isLive = !isVod,
            isMultivariant = isMultivariant,
            variantUrls = variantUrls,
            isEncrypted = isEncrypted,
            encryption = encryption,
            targetDuration = targetDuration,
            mediaSequence = mediaSequence,
        )
    }

    /**
     * 智能改写 M3U8 内容。
     *
     * 仅将已缓存的切片 URL 替换为 file:// 本地路径（S2 优化），
     * 未缓存的保留原始 CDN URL 作为回退。
     *
     * @param content 原始 M3U8 内容
     * @param baseUrl 基础 URL
     * @param cacheDir 缓存目录路径
     * @param isCached 判断某 URL 是否已缓存的函数
     * @return 改写后的 M3U8 内容
     */
    fun rewriteWithCachePaths(
        content: String,
        baseUrl: String,
        cacheDir: String,
        isCached: (String) -> Boolean,
    ): String {
        return content.lines().joinToString("\n") { line ->
            if (!line.startsWith("#") && line.isNotBlank()) {
                val segmentUrl = resolveUrl(baseUrl, line.trim())
                val cachePath = "$cacheDir/${urlToFileName(segmentUrl)}"
                if (isCached(segmentUrl)) {
                    "file://$cachePath"
                } else {
                    // S2: 未缓存，保留原始 URL 作为回退
                    line.trim()
                }
            } else {
                line
            }
        }
    }

    companion object {
        fun resolveUrl(baseUrl: String, relativeUrl: String): String {
            return if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                // 处理相对路径：去掉 baseUrl 末尾的路径部分
                val normalizedBase = baseUrl.trimEnd('/')
                val normalizedRelative = relativeUrl.trimStart('/')
                "$normalizedBase/$normalizedRelative"
            }
        }

        fun urlToFileName(url: String): String = url
            .replace("https://", "").replace("http://", "")
            .replace("/", "_").replace("?", "_")
            .replace("&", "_").replace("=", "_").replace(":", "_")

        /**
         * 从 M3U8 标签行中提取属性值。
         * 例如：extractAttribute("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"", "URI") → "key.bin"
         */
        fun extractAttribute(line: String, attrName: String): String? {
            val regex = Regex("""$attrName="([^"]*)"""")
            val matchResult = regex.find(line)
            if (matchResult != null) return matchResult.groupValues[1]

            // 也支持无引号的属性值
            val simpleRegex = Regex("""$attrName=([^,"\s]+)""")
            val simpleMatch = simpleRegex.find(line)
            return simpleMatch?.groupValues?.get(1)
        }
    }
}
