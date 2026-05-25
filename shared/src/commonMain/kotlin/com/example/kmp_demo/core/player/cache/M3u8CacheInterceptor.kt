package com.example.kmp_demo.core.player.cache

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * M3U8 缓存拦截器。
 *
 * 解法A：intercept 零等待返回原始 URL，后台异步预加载切片到 DiskLruCache。
 * ExoPlayer 通过 HybridDataSource 桥接读取缓存，未命中则回退 HTTP。
 *
 * @param httpClient Ktor HttpClient
 * @param diskCache LRU 磁盘缓存
 * @param cacheDir 缓存目录
 * @param fileSystem Okio 文件系统
 */
class M3u8CacheInterceptor(
    private val httpClient: HttpClient,
    private val diskCache: DiskLruCache,
    private val cacheDir: String,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    private val parser = M3u8Parser()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var preloadJob: Job? = null
    private var liveRefreshJob: Job? = null

    /** 当前 M3U8 URL，直播刷新用 */
    private var currentM3u8Url: String = ""
    /** 当前请求头，透传所有子请求 */
    private var currentHeaders: Map<String, String>? = null

    /** S11: 预加载进度 */
    data class CacheProgress(
        val cachedSegments: Int = 0,
        val totalSegments: Int = 0,
        val cacheSizeBytes: Long = 0L,
    )

    private val _cacheProgress = MutableStateFlow(CacheProgress())
    val cacheProgress: StateFlow<CacheProgress> = _cacheProgress.asStateFlow()

    /**
     * 拦截 M3U8 URL，零等待返回。
     *
     * 后台异步启动预加载，不阻塞主链。
     * 播放器通过 HybridDataSource 读取缓存或回退 HTTP。
     *
     * @param originalUrl 原始 M3U8 URL
     * @param headers 请求头（S8）
     * @return 原始 URL
     */
    suspend fun intercept(
        originalUrl: String,
        headers: Map<String, String>? = null,
    ): String {
        currentM3u8Url = originalUrl
        currentHeaders = headers

        // 异步启动预加载，绝不阻塞主链
        scope.launch {
            try {
                preloadInternal(originalUrl, headers)
            } catch (e: Exception) {
                // 保证预加载异常不崩主流程
            }
        }

        // 极其重要：直接无脑返回原始链接，起播零等待！
        return originalUrl
    }

    /**
     * S3: Seek 后重新定位预加载起点。
     *
     * @param positionMs 播放位置（毫秒）
     * @param targetDuration 切片时长（秒）
     */
    fun onSeek(positionMs: Long, targetDuration: Double) {
        if (targetDuration <= 0.0) return
        val segmentIndex = (positionMs / 1000.0 / targetDuration).toInt()
        // 取消当前预加载，从新位置重新开始
        preloadJob?.cancel()
        preloadJob = scope.launch {
            preloadSegmentsFrom(segmentIndex)
        }
    }

    /**
     * S9: 重置状态（切换剧集时调用）。
     * 取消预加载和直播刷新，保留磁盘缓存。
     */
    fun reset() {
        preloadJob?.cancel()
        preloadJob = null
        liveRefreshJob?.cancel()
        liveRefreshJob = null
        currentM3u8Url = ""
        currentHeaders = null
        _cacheProgress.value = CacheProgress()
    }

    /**
     * 完全停止，释放协程作用域。
     * 与 [reset] 区别：stop 取消 scope，实例不可复用。
     */
    fun stop() {
        reset()
        scope.cancel()
    }

    // ==================== 内部方法 ====================

    /**
     * 内部预加载入口。
     * 下载 M3U8 → 解析 → 按类型分发（多码率/加密/直播/点播）。
     * 异常吞掉，不崩主流程。
     */
    private suspend fun preloadInternal(
        m3u8Url: String,
        headers: Map<String, String>?,
    ) {
        try {
            // 1. 下载 M3U8 文件（S8: 透传 headers）
            val response: HttpResponse = httpClient.get(m3u8Url) {
                headers?.forEach { (key, value) -> header(key, value) }
            }
            val content = response.bodyAsText()

            // 2. 解析切片列表
            val baseUrl = m3u8Url.substringBeforeLast("/")
            val playlist = parser.parse(content, baseUrl)

            // S6: 多码率处理 — 递归下载子播放列表
            if (playlist.isMultivariant && playlist.variantUrls.isNotEmpty()) {
                handleMultivariantInternal(playlist, headers)
                return
            }

            // S7: 加密流处理 — 仅预加载
            if (playlist.isEncrypted) {
                downloadSegments(playlist, headers)
                return
            }

            // S4: 直播流 — 启动定期刷新
            if (playlist.isLive) {
                startLiveRefresh(m3u8Url, headers)
            }

            // 3. 下载切片（流式写入 DiskLruCache）
            downloadSegments(playlist, headers)
        } catch (_: Exception) {
            // 预加载异常不崩主流程
        }
    }

    /** S6: 处理多码率，选第一个子列表递归预加载。 */
    private suspend fun handleMultivariantInternal(
        playlist: M3u8Parser.M3u8Playlist,
        headers: Map<String, String>?,
    ) {
        val selectedVariant = playlist.variantUrls.firstOrNull() ?: return
        preloadInternal(selectedVariant, headers)
    }

    /** S4: 直播流定期刷新，每 30s 轮询新切片。 */
    private fun startLiveRefresh(m3u8Url: String, headers: Map<String, String>?) {
        liveRefreshJob?.cancel()
        liveRefreshJob = scope.launch {
            while (isActive) {
                delay(30_000L) // 每 30 秒刷新一次
                try {
                    val response: HttpResponse = httpClient.get(m3u8Url) {
                        headers?.forEach { (key, value) -> header(key, value) }
                    }
                    val content = response.bodyAsText()
                    val baseUrl = m3u8Url.substringBeforeLast("/")
                    val playlist = parser.parse(content, baseUrl)
                    downloadSegments(playlist, headers)
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * 下载切片到磁盘缓存（流式写入）。
     *
     * Ktor ByteReadChannel → Okio Buffer → DiskLruCache.putStream() → Sink。
     * 跳过已缓存，单切片失败不影响整体，最多取 50 个。
     *
     * @param playlist 播放列表
     * @param headers 请求头（S8）
     */
    private suspend fun downloadSegments(
        playlist: M3u8Parser.M3u8Playlist,
        headers: Map<String, String>? = null,
    ) {
        val totalSegments = playlist.segments.size
        val segmentsToDownload = playlist.segments.take(15)
        var cachedCount = 0

        for (segment in segmentsToDownload) {
            if (!currentCoroutineContext().isActive) break

            // 跳过已缓存的切片
            if (diskCache.contains(segment.url)) {
                cachedCount++
                updateProgress(cachedCount, totalSegments)
                continue
            }

            try {
                // S8: 透传 headers
                // 使用 Ktor 3.x 流式读取：prepareGet + execute
                httpClient.prepareGet(segment.url) {
                    headers?.forEach { (key, value) -> header(key, value) }
                }.execute { response ->
                    val channel = response.bodyAsChannel()
                    // 缝合：将 Ktor 的 Channel 写入到 DiskLruCache 的 Sink 中
                    diskCache.putStream(segment.url) { sink ->
                        runBlocking {
                            val okioBuffer = Buffer()
                            val byteArray = ByteArray(8192)
                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(byteArray, 0, byteArray.size)
                                if (read <= 0) break
                                // 写入 Okio Buffer，然后 flush 到 Sink
                                okioBuffer.write(byteArray, 0, read)
                                sink.write(okioBuffer, read.toLong())
                            }
                        }
                    }
                }
                cachedCount++
                updateProgress(cachedCount, totalSegments)

            } catch (_: Exception) {
                // 单个切片下载失败不影响整体
            }
        }
    }

    /**
     * S3: 从指定索引开始预加载（Seek 后重新定位）。
     * 重新下载 M3U8，取 startIndex 后 30 个切片。
     */
    private suspend fun preloadSegmentsFrom(startIndex: Int) {
        try {
            val response: HttpResponse = httpClient.get(currentM3u8Url) {
                currentHeaders?.forEach { (key, value) -> header(key, value) }
            }
            val content = response.bodyAsText()
            val baseUrl = currentM3u8Url.substringBeforeLast("/")
            val playlist = parser.parse(content, baseUrl)

            // 从 startIndex 开始取后续 30 个切片
            val segmentsToPreload = playlist.segments
                .drop(startIndex)
                .take(30)

            for (segment in segmentsToPreload) {
                if (!currentCoroutineContext().isActive) break
                if (diskCache.contains(segment.url)) continue

                try {
                    httpClient.prepareGet(segment.url) {
                        currentHeaders?.forEach { (key, value) -> header(key, value) }
                    }.execute { response ->
                        val channel = response.bodyAsChannel()
                        diskCache.putStream(segment.url) { sink ->
                            runBlocking {
                                val okioBuffer = Buffer()
                                val byteArray = ByteArray(8192)
                                while (!channel.isClosedForRead) {
                                    val read = channel.readAvailable(byteArray, 0, byteArray.size)
                                    if (read <= 0) break
                                    okioBuffer.write(byteArray, 0, read)
                                    sink.write(okioBuffer, read.toLong())
                                }
                            }
                        }
                    }
                    delay((segment.duration * 500).toLong())
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /** S11: 更新缓存进度。cacheSizeBytes 暂未更新，需调用 stats() 获取。 */
    private fun updateProgress(cached: Int, total: Int) {
        _cacheProgress.value = CacheProgress(
            cachedSegments = cached,
            totalSegments = total,
            cacheSizeBytes = _cacheProgress.value.cacheSizeBytes,
        )
    }
}
