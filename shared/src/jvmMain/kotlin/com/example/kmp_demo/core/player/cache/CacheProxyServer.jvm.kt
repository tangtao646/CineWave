package com.example.kmp_demo.core.player.cache

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

    override suspend fun start(): Int {
        stop()

        DebugLog.d(TAG, "start() called, binding to port $PROXY_PORT...")

        val engine = embeddedServer(Netty, port = PROXY_PORT) {
            routing {

                // ─── M3U8 路由 ───
                get("/m3u8") {
                    val originalUrl = call.request.queryParameters["url"]
                        ?: return@get call.respondText(
                            "Missing 'url' parameter",
                            status = HttpStatusCode.BadRequest
                        )
                    DebugLog.d(TAG, "/m3u8 request received, originalUrl=$originalUrl")
                    val headers = call.request.queryParameters.entries()
                        .filter { it.key != "url" }
                        .associate { it.key to it.value.joinToString(",") }
                        .ifEmpty { null }

                    val content = m3u8Handler.value.handle(originalUrl, headers, PROXY_PORT)
                    DebugLog.d(TAG, "/m3u8 response prepared, length=${content.length}")
                    call.respondText(
                        content,
                        contentType = ContentType("application", "vnd.apple.mpegurl")
                    )
                }

                // ─── TS 切片路由（流式响应，核心修复） ───
                get("/ts") {
                    val originalUrl = call.request.queryParameters["url"]
                        ?: return@get call.respondText(
                            "Missing 'url' parameter",
                            status = HttpStatusCode.BadRequest
                        )
                    DebugLog.d(TAG, "/ts request received, originalUrl=$originalUrl")
                    val extraHeaders = call.request.queryParameters.entries()
                        .filter { it.key != "url" }
                        .associate { it.key to it.value.joinToString(",") }
                        .ifEmpty { null }

                    // ── 缓存命中：流式读取 ──
                    val cachedSource = diskCache.getSource(originalUrl)
                    if (cachedSource != null) {
                        DebugLog.d(TAG, "/ts cache HIT for $originalUrl")
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
                        DebugLog.d(TAG, "/ts cache HIT response completed")
                        return@get
                    }

                    // ── 缓存未命中：边下边发，响应结束后异步写缓存 ──
                    DebugLog.d(TAG, "/ts cache MISS for $originalUrl, fetching upstream...")
                    statsCollector.recordMiss(0L)
                    val collectedChunks = mutableListOf<ByteArray>()

                    try {
                        val upstreamResponse = httpClient.get(originalUrl) {
                            extraHeaders?.forEach { (k, v) -> header(k, v) }
                        }
                        DebugLog.d(TAG, "/ts upstream response status=${upstreamResponse.status}")
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
                            DebugLog.d(TAG, "/ts streaming completed, totalBytes=$totalBytes")
                        }

                        // 响应结束后异步写入磁盘缓存（不阻塞 VLCJ）
                        if (collectedChunks.isNotEmpty()) {
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    diskCache.putStream(originalUrl) { sink: Sink ->
                                        val buffer = Buffer()
                                        collectedChunks.forEach { chunk ->
                                            buffer.write(chunk)
                                        }
                                        sink.write(buffer, buffer.size)
                                    }
                                    statsCollector.recordCachedSegment()
                                    DebugLog.d(TAG, "/ts cache write completed for $originalUrl")
                                } catch (e: Exception) {
                                    DebugLog.d(TAG, "/ts cache write failed: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        DebugLog.d(TAG, "/ts upstream error: ${e.message}")
                        if (!call.response.isSent) {
                            call.respondText(
                                "Upstream error: ${e.message}",
                                status = HttpStatusCode.BadGateway
                            )
                        }
                    }
                }
            }
        }.start(wait = false)

        // 固定端口，直接赋值，无需等待异步绑定
        _port = PROXY_PORT
        server = engine
        statsCollector.setRunning(_port)
        DebugLog.d(TAG, "start() completed, port=$_port")
        return _port
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
     */
    suspend fun handle(originalUrl: String, headers: Map<String, String>?, proxyPort: Int): String {
        // 1. 从 CDN 下载原始 M3U8
        val response = httpClient.get(originalUrl) {
            headers?.forEach { (k, v) -> header(k, v) }
        }
        val rawContent = response.bodyAsText()

        // 2. 解析并替换 URL
        val modifiedContent = replaceSegmentUrls(rawContent, originalUrl, headers, proxyPort)

        // 3. 后台预取前 3 个切片（播放器几乎一定会请求前几个切片）
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
     */
    private fun resolveUrl(url: String, baseUrl: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
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

// ========================================================================
// TsRequestHandler — TS 切片请求处理器（流式版本）
// ========================================================================

/**
 * TS 切片请求处理器。
 *
 * ## Bug 2 修复：全量读内存 → 流式处理
 *
 * 旧版本：先把整个 TS 切片（2~8MB）下载完再返回，VLCJ 3 秒超时直接报错。
 * 新版本：使用回调模式，边从 CDN 读边回调给调用方，首字节延迟降为毫秒级。
 *
 * 核心逻辑：
 * 1. 检查缓存 → 命中直接流式读取
 * 2. 未命中 → 从 CDN 流式下载 → 同时收集数据 → 响应结束后异步写缓存
 *
 * 注意：此类不再返回 [ProxyResponse]（全量 ByteArray），
 * 而是通过 [StreamCallback] 逐块回调，由调用方（平台路由处理器）负责流式转发。
 */
class TsRequestHandler(
    private val diskCache: DiskLruCache,
    private val httpClient: HttpClient,
    private val statsCollector: CacheStatsCollector? = null,
) {
    /**
     * 流式回调接口，由平台路由处理器实现。
     */
    fun interface StreamCallback {
        /**
         * 当有数据块可用时回调。
         * @param chunk 数据块
         * @param isLast 是否为最后一个数据块
         */
        suspend fun onChunk(chunk: ByteArray, isLast: Boolean)
    }

    /**
     * 处理 TS 切片请求（流式版本）。
     *
     * @param originalUrl 原始 CDN URL
     * @param rangeHeader 可选的 Range 请求头
     * @param headers 原始请求头
     * @param callback 流式回调，每收到一个数据块就回调一次
     */
    suspend fun handleStreaming(
        originalUrl: String,
        rangeHeader: String?,
        headers: Map<String, String>? = null,
        callback: StreamCallback,
    ) {
        // 1. 检查缓存
        val cachedSource = diskCache.getSource(originalUrl)
        if (cachedSource != null) {
            serveFromCacheStreaming(cachedSource, callback)
            return
        }

        // 2. 从 CDN 下载并缓存
        downloadAndCacheStreaming(originalUrl, rangeHeader, headers, callback)
    }

    /**
     * 从缓存流式读取。
     */
    private suspend fun serveFromCacheStreaming(
        source: okio.Source,
        callback: StreamCallback,
    ) {
        var totalBytes = 0L
        source.buffer().use { bufferedSource ->
            val buf = ByteArray(65_536)
            while (true) {
                val n = bufferedSource.read(buf).toInt()
                if (n == -1) break
                callback.onChunk(buf.copyOf(n), isLast = false)
                totalBytes += n
            }
        }
        callback.onChunk(ByteArray(0), isLast = true)
        statsCollector?.recordHit(totalBytes)
    }

    /**
     * 从 CDN 流式下载，同时收集数据用于异步写缓存。
     */
    private suspend fun downloadAndCacheStreaming(
        url: String,
        rangeHeader: String?,
        headers: Map<String, String>? = null,
        callback: StreamCallback,
    ) {
        val response = httpClient.get(url) {
            rangeHeader?.let { header(HttpHeaders.Range, it) }
            headers?.forEach { (k, v) -> header(k, v) }
        }

        val channel = response.bodyAsChannel()
        val collectedChunks = mutableListOf<ByteArray>()
        var totalBytes = 0L

        try {
            val buf = ByteArray(65_536)
            while (!channel.isClosedForRead) {
                val n = channel.readAvailable(buf, 0, buf.size)
                if (n > 0) {
                    val chunk = buf.copyOf(n)
                    callback.onChunk(chunk, isLast = false)
                    collectedChunks.add(chunk)
                    totalBytes += n
                }
            }
            callback.onChunk(ByteArray(0), isLast = true)

            statsCollector?.recordMiss(totalBytes)

            // 响应结束后异步写入磁盘缓存（不阻塞播放器）
            if (collectedChunks.isNotEmpty()) {
                scope.launch {
                    try {
                        diskCache.putStream(url) { sink ->
                            val buffer = Buffer()
                            collectedChunks.forEach { chunk -> buffer.write(chunk) }
                            sink.write(buffer, buffer.size)
                        }
                        statsCollector?.recordCachedSegment()
                    } catch (_: Exception) {
                        // 缓存写入失败不影响播放
                    }
                }
            }
        } catch (e: Exception) {
            // 确保即使出错也通知回调结束
            callback.onChunk(ByteArray(0), isLast = true)
            throw e
        }
    }

    companion object {
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

// ========================================================================
// URL 编码工具函数
// ========================================================================

/**
 * URL 编码工具函数。
 */
internal fun String.encodeURL(): String {
    return buildString {
        this@encodeURL.forEach { c ->
            when (c) {
                in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_', '.', '~' -> append(c)
                else -> {
                    append('%')
                    append(c.code.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
