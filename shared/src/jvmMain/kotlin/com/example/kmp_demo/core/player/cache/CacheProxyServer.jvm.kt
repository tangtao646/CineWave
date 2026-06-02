package com.example.kmp_demo.core.player.cache

import com.example.kmp_demo.core.PlatformLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okio.Buffer
import okio.buffer
import okio.Sink

/**
 * Desktop (JVM) 平台实现：使用 Ktor Netty 引擎。
 *
 * ## 关键修复（相比旧版本）
 *
 * ### 修复1：端口竞态 → 改用固定端口
 * 旧版本：`start(wait=false)` 后立刻读 `resolvedConnectors()`，
 * Netty 尚未完成端口绑定导致获取失败。
 * 新版本：固定端口 [PROXY_PORT]，桌面单进程无冲突风险，彻底消除竞态。
 *
 * ### 修复2：TS 切片全量加载 → 真正流式转发
 * 旧版本：先把整个 TS 切片（2~8MB）下载完再 respondBytes，VLCJ 3 秒超时直接报错。
 * 新版本：`respondBytesWriter { }` 边从 CDN 读边写给 VLCJ，首字节延迟降为毫秒级。
 *
 * ### 修复3：DiskLruCache 锁优化
 * 旧版本：getSource() 在 mutex.withLock 内执行文件读取，阻塞所有写入路径。
 * 新版本：Mutex 仅保护元数据，IO 操作移到锁外。
 *
 * ### 修复4：无效 M3U8 检测
 * 当上游返回的 M3U8 内容无效（无 #EXTM3U 头、无切片 URL、或内容为空）时，
 * 返回 HTTP 502 Bad Gateway，让 VLC 触发 error 事件，避免无限重试死循环。
 */
class CacheProxyServerJvm(
    private val diskCache: DiskLruCache,
    private val httpClient: HttpClient,
) : CacheProxyServer {
    companion object {
        /** 固定代理端口，选用 19876 避开常见端口冲突 */
        private const val PROXY_PORT = 19876
        private const val TAG = "CacheProxyServerJvm"
    }

    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    private var _port: Int = -1

    override val port: Int
        get() = _port

    private val statsCollector = CacheStatsCollector()

    override val stats: StateFlow<CacheStats>
        get() = statsCollector.stats

    private val m3u8Handler = lazy { M3u8RequestHandler(httpClient, diskCache) }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun start(): Int {
        stop()
        PlatformLogger.d(TAG, "start() called, binding to port $PROXY_PORT...")

        val engine = embeddedServer(Netty, port = PROXY_PORT) {
            routing {
                m3u8Route(m3u8Handler)
                tsRoute(diskCache, httpClient, statsCollector)
            }
        }.start(wait = false)

        _port = PROXY_PORT
        server = engine
        statsCollector.setRunning(_port)
        PlatformLogger.d(TAG, "start() completed, port=$_port")
        return _port
    }

    // ========================================================================
    // 路由定义（抽离为扩展函数，保持 start() 简洁）
    // ========================================================================

    private fun Routing.m3u8Route(handler: Lazy<M3u8RequestHandler>) {
        get("/m3u8") {
            val originalUrl = call.request.queryParameters["url"]
                ?: return@get call.respondText("Missing 'url' parameter", status = HttpStatusCode.BadRequest)
            PlatformLogger.d(TAG, "/m3u8 request received, originalUrl=$originalUrl")
            val headers = parseHeaders(call)

            try {
                val content = handler.value.handle(originalUrl, headers, PROXY_PORT)
                PlatformLogger.d(TAG, "/m3u8 response prepared, length=${content.length}")
                call.respondText(content, contentType = ContentType("application", "vnd.apple.mpegurl"))
            } catch (e: Exception) {
                PlatformLogger.d(TAG, "/m3u8 error: ${e.message}")
                call.respondText("#EXTM3U\n#EXT-X-ENDLIST", contentType = ContentType("application", "vnd.apple.mpegurl"), status = HttpStatusCode.BadGateway)
            }
        }
    }

    private fun Routing.tsRoute(diskCache: DiskLruCache, httpClient: HttpClient, statsCollector: CacheStatsCollector) {
        get("/ts") {
            val originalUrl = call.request.queryParameters["url"]
                ?: return@get call.respondText("Missing 'url' parameter", status = HttpStatusCode.BadRequest)
            PlatformLogger.d(TAG, "/ts request received, originalUrl=$originalUrl")
            val extraHeaders = parseHeaders(call)

            // ── 缓存命中：流式读取 ──
            val cachedSource = diskCache.getSource(originalUrl)
            if (cachedSource != null) {
                PlatformLogger.d(TAG, "/ts cache HIT for $originalUrl")
                statsCollector.recordHit(0L)
                call.respondBytesWriter(ContentType.parse("video/MP2T")) {
                    cachedSource.buffer().use { src ->
                        val buf = ByteArray(65_536)
                        while (true) {
                            val n = src.read(buf).toInt()
                            if (n == -1) break
                            writeFully(buf, 0, n)
                        }
                    }
                }
                PlatformLogger.d(TAG, "/ts cache HIT response completed")
                return@get
            }

            // ── 缓存未命中：边下边发，响应结束后异步写缓存 ──
            PlatformLogger.d(TAG, "/ts cache MISS for $originalUrl, fetching upstream...")
            statsCollector.recordMiss(0L)
            val collectedChunks = mutableListOf<ByteArray>()

            try {
                val upstreamResponse = httpClient.get(originalUrl) {
                    extraHeaders?.forEach { (k, v) -> header(k, v) }
                }
                PlatformLogger.d(TAG, "/ts upstream response status=${upstreamResponse.status}")
                val upstreamChannel = upstreamResponse.bodyAsChannel()
                val contentTypeStr = upstreamResponse.contentType()?.toString() ?: "video/MP2T"

                call.respondBytesWriter(ContentType.parse(contentTypeStr)) {
                    val buf = ByteArray(65_536)
                    var totalBytes = 0L
                    while (!upstreamChannel.isClosedForRead) {
                        val n = upstreamChannel.readAvailable(buf, 0, buf.size)
                        if (n > 0) {
                            writeFully(buf, 0, n)
                            collectedChunks.add(buf.copyOf(n))
                            totalBytes += n
                        }
                    }
                    PlatformLogger.d(TAG, "/ts streaming completed, totalBytes=$totalBytes")
                }

                // 响应结束后异步写入磁盘缓存（不阻塞 VLCJ）
                if (collectedChunks.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            diskCache.putStream(originalUrl) { sink: Sink ->
                                val buffer = Buffer()
                                collectedChunks.forEach { chunk -> buffer.write(chunk) }
                                sink.write(buffer, buffer.size)
                            }
                            statsCollector.recordCachedSegment()
                            PlatformLogger.d(TAG, "/ts cache write completed for $originalUrl")
                        } catch (e: Exception) {
                            PlatformLogger.d(TAG, "/ts cache write failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                PlatformLogger.d(TAG, "/ts upstream error: ${e.message}")
                if (!call.response.isSent) {
                    call.respondText("Upstream error: ${e.message}", status = HttpStatusCode.BadGateway)
                }
            }
        }
    }

    /**
     * 从请求中提取自定义请求头（排除 "url" 参数）。
     * 消除 M3U8 和 TS 路由中的重复解析逻辑。
     */
    private fun parseHeaders(call: io.ktor.server.application.ApplicationCall): Map<String, String>? {
        return call.request.queryParameters.entries()
            .filter { it.key != "url" }
            .associate { it.key to it.value.joinToString(",") }
            .ifEmpty { null }
    }

    override fun getProxiedM3u8Url(originalUrl: String, headers: Map<String, String>?): String {
        check(_port > 0) { "Proxy server not started" }
        var url = "http://localhost:$_port/m3u8?url=${originalUrl.encodeURL()}"
        headers?.forEach { (key, value) ->
            url += "&${key.encodeURL()}=${value.encodeURL()}"
        }
        return url
    }

    override suspend fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        server = null
        _port = -1
        statsCollector.setStopped()
    }
}

// ========================================================================
// M3u8RequestHandler — M3U8 播放列表请求处理器
// ========================================================================

/**
 * M3U8 播放列表请求处理器。
 *
 * 职责：
 * 1. 从 CDN 拉取原始 M3U8
 * 2. 解析并替换所有切片 URL 为本地代理 URL
 * 3. 后台预取前几个切片（真正的有效预加载）
 *
 * ⚠️ 重要：proxyPort 通过函数参数传入，而非构造函数捕获，
 * 避免因 lazy 初始化时端口尚未分配导致捕获到 -1。
 */
class M3u8RequestHandler(
    private val httpClient: HttpClient,
    private val diskCache: DiskLruCache,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 处理 M3U8 请求。
     * @param originalUrl 原始 M3U8 URL
     * @param headers 原始请求头
     * @param proxyPort 当前代理服务器端口（通过参数传入，避免 lazy 捕获问题）
     * @return 修改后的 M3U8 内容（切片 URL 已被替换为本地代理 URL）
     * @throws InvalidM3u8Exception 如果 M3U8 内容无效
     */
    suspend fun handle(originalUrl: String, headers: Map<String, String>?, proxyPort: Int): String {
        // 1. 从 CDN 下载原始 M3U8
        val response = httpClient.get(originalUrl) {
            headers?.forEach { (k, v) -> header(k, v) }
        }

        // 检查 HTTP 状态码
        if (response.status.value !in 200..299) {
            throw InvalidM3u8Exception(
                "上游服务器返回 HTTP ${response.status.value}",
                originalUrl,
                response.status.value
            )
        }

        val rawContent = response.bodyAsText()

        // 2. 验证 M3U8 内容有效性
        validateM3u8Content(rawContent, originalUrl)

        // 3. 解析并替换 URL
        val modifiedContent = replaceSegmentUrls(rawContent, originalUrl, headers, proxyPort)

        // 4. 后台预取前 3 个切片（播放器几乎一定会请求前几个切片）
        val segments = parseSegmentUrls(rawContent, originalUrl)
        if (segments.isNotEmpty()) {
            scope.launch {
                segments.take(3).forEach { url ->
                    if (!diskCache.contains(url)) {
                        prefetchSegment(url, headers)
                    }
                }
            }
        }

        return modifiedContent
    }

    /**
     * 验证 M3U8 内容是否有效。
     *
     * 有效的 M3U8 播放列表必须满足以下条件之一：
     * 1. 包含 #EXTM3U 头部
     * 2. 包含至少一个切片 URL（非 # 开头的行）
     * 3. 内容不为空
     *
     * 如果内容无效，抛出 [InvalidM3u8Exception]。
     */
    private fun validateM3u8Content(content: String, originalUrl: String) {
        // 检查内容是否为空
        if (content.isBlank()) {
            throw InvalidM3u8Exception(
                "M3U8 内容为空",
                originalUrl
            )
        }

        // 检查是否包含 #EXTM3U 头部
        if (!content.contains("#EXTM3U")) {
            throw InvalidM3u8Exception(
                "M3U8 格式无效：缺少 #EXTM3U 头部",
                originalUrl
            )
        }

        // 检查是否包含至少一个切片 URL 或二级 M3U8 URL
        // 有效的 M3U8 应该包含 #EXTINF 标签（切片）或 #EXT-X-STREAM-INF 标签（二级 M3U8）
        val hasExtInf = content.contains("#EXTINF")
        val hasStreamInf = content.contains("#EXT-X-STREAM-INF")

        if (!hasExtInf && !hasStreamInf) {
            // 进一步检查是否有任何非注释行（可能是切片 URL）
            val hasSegmentUrl = content.lines().any { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("#")
            }

            if (!hasSegmentUrl) {
                throw InvalidM3u8Exception(
                    "M3U8 内容无效：没有可播放的切片或子播放列表",
                    originalUrl
                )
            }
        }
    }

    /**
     * 替换 M3U8 内容中的切片 URL。
     * 将 CDN URL 替换为本地代理 URL。
     */
    private fun replaceSegmentUrls(
        content: String,
        baseUrl: String,
        headers: Map<String, String>?,
        proxyPort: Int,
    ): String {
        return content.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                rewriteUriAttributes(line, baseUrl, headers, proxyPort)
            } else {
                buildProxyUrl(resolveUrl(trimmed, baseUrl), headers, proxyPort)
            }
        }
    }

    /**
     * 解析 M3U8 内容中所有切片 URL。
     */
    private fun parseSegmentUrls(content: String, baseUrl: String): List<String> {
        val urls = mutableListOf<String>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val url = resolveUrl(trimmed, baseUrl)
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url)
            }
        }
        return urls
    }

    private fun rewriteUriAttributes(
        line: String,
        baseUrl: String,
        headers: Map<String, String>?,
        proxyPort: Int,
    ): String {
        return line.replace(Regex("URI=(\"([^\"]+)\"|'([^']+)')")) { match ->
            val rawUri = match.groups[2]?.value ?: match.groups[3]?.value ?: return@replace match.value
            val absoluteUri = resolveUrl(rawUri, baseUrl)
            val proxiedUri = buildProxyUrl(absoluteUri, headers, proxyPort)
            "URI=\"$proxiedUri\""
        }
    }

    private fun buildProxyUrl(originalUrl: String, headers: Map<String, String>?, proxyPort: Int): String {
        // 根据文件扩展名区分 M3U8 和 TS 路由
        // 二级 M3U8（如 mixed.m3u8）必须走 /m3u8 路由，以便再次替换其中的切片 URL
        // TS 切片走 /ts 路由进行流式缓存
        val route = if (originalUrl.endsWith(".m3u8", ignoreCase = true)) "m3u8" else "ts"
        var url = "http://localhost:$proxyPort/$route?url=${originalUrl.encodeURL()}"
        headers?.forEach { (key, value) ->
            url += "&${key.encodeURL()}=${value.encodeURL()}"
        }
        return url
    }

    /**
     * 解析相对 URL。
     *
     * 处理三种情况：
     * 1. 完整 URL（http/https 开头）→ 直接返回
     * 2. 以 / 开头的绝对路径 → 用协议+域名拼接（避免双斜杠）
     * 3. 相对路径 → 用 base URL 的目录部分拼接
     */
    private fun resolveUrl(url: String, baseUrl: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        // 以 / 开头的绝对路径：直接用协议+域名拼接，避免双斜杠
        if (url.startsWith("/")) {
            // 提取协议和域名，例如 "https://vod2.maowushi.com"
            val protocolEnd = baseUrl.indexOf("://")
            if (protocolEnd == -1) {
                // 没有协议，直接拼接
                return "$baseUrl$url"
            }
            val protocol = baseUrl.substring(0, protocolEnd + 3) // 包含 "://"
            val afterProtocol = baseUrl.substring(protocolEnd + 3)
            val domain = afterProtocol.substringBefore("/")
            return "$protocol$domain$url"
        }
        // 相对路径：用 base URL 的目录部分拼接
        val base = baseUrl.substringBeforeLast("/")
        return "$base/$url"
    }

    /**
     * 预取单个切片到缓存。
     * 流式下载并写入 DiskLruCache。
     */
    private suspend fun prefetchSegment(url: String, headers: Map<String, String>?) {
        try {
            val response = httpClient.get(url) {
                headers?.forEach { (k, v) -> header(k, v) }
            }
            if (response.status.value !in 200..299) return
            val channel = response.bodyAsChannel()
            diskCache.putStream(url) { sink ->
                val buffer = Buffer()
                val temp = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(temp, 0, 8192)
                    if (read > 0) {
                        buffer.write(temp, 0, read)
                        sink.write(buffer, buffer.size)
                    }
                }
            }
        } catch (_: Exception) {
            // 预取失败不影响主流程
        }
    }
}

/**
 * M3U8 内容无效异常。
 *
 * 当上游返回的 M3U8 内容不满足有效播放列表格式时抛出此异常。
 * 例如：内容为空、缺少 #EXTM3U 头部、没有切片 URL 等。
 */
class InvalidM3u8Exception(
    message: String,
    val originalUrl: String,
    val httpStatusCode: Int? = null,
) : Exception(message)



// ========================================================================
// URL 编码工具函数
// ========================================================================

/**
 * URL 编码工具函数。
 *
 * 使用 UTF-8 编码对 URL 中的非 ASCII 字符和特殊字符进行百分比编码。
 * 例如：中文字符 "中字" 会被编码为 "%E4%B8%AD%E5%AD%97"。
 *
 * 注意：使用 Java 的 URLEncoder 可以正确处理 UTF-8 编码，
 * 但需要额外处理空格（应编码为 %20 而非 +）。
 */
internal fun String.encodeURL(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
        .replace("+", "%20")
}
