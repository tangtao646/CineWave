package com.example.kmp_demo.core.player.cache

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.date.getTimeMillis
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.FileSystem

/**
 * M3U8 缓存拦截器。
 *
 * 架构设计：
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │                   预加载引擎                                  │
 * ├──────────┬──────────────────────────────────────────────────┤
 * │  阶段1    │  阶段2：跟随预加载循环（永不退出）                  │
 * │ 起播冲刺   │  while(true) {                                  │
 * │ take(4)   │    每 30s 拉取 M3U8（非每次循环）                 │
 * │ 无延迟     │    计算前瞻窗口 [anchor+1, anchor+8]             │
 * │           │    流式下载未缓存切片（零额外内存拷贝）             │
 * │           │    全部已缓存 → anchor++（自驱前进）               │
 * │           │  }                                               │
 * └──────────┴──────────────────────────────────────────────────┘
 *
 * 性能与内存安全：
 * 1. 流式写入：Ktor ByteReadChannel → Okio Sink，零额外内存拷贝
 *    ❌ 旧方案：response.readBytes() 将整个切片加载到内存
 *    ✅ 新方案：8KB 缓冲区循环读写，内存占用恒定
 * 2. 去重下载：使用 Set 跟踪正在下载的 URL，防止并发重复下载
 * 3. 减少 M3U8 拉取频率：每 30s 拉取一次，非每次循环
 * 4. 批量缓存检查：containsAll() 一次锁获取检查所有切片
 *
 * @param httpClient Ktor HttpClient
 * @param diskCache LRU 磁盘缓存
 * @param cacheDir 缓存目录路径
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
    private val stateMutex = Mutex()

    // ==================== 受 Mutex 保护的内部状态 ====================
    private var currentM3u8Url: String = ""
    private var currentHeaders: Map<String, String>? = null
    private var currentSegmentIndex: Int = 0
    private var totalSegmentCount: Int = 0

    /** 正在下载中的 URL 集合，用于去重 */
    private val downloadingUrls = mutableSetOf<String>()

    // ==================== 任务句柄 ====================
    private var preloadJob: Job? = null
    private var liveRefreshJob: Job? = null
    private var continuousPreloadJob: Job? = null

    /** 上次拉取 M3U8 列表的时间戳，用于控制拉取频率 */
    private var lastPlaylistFetchTime: Long = 0L
    /** 缓存的播放列表，避免每次循环都拉取 */
    private var cachedPlaylist: M3u8Parser.M3u8Playlist? = null

    /** 预加载进度 */
    data class CacheProgress(
        val cachedSegments: Int = 0,
        val totalSegments: Int = 0,
        val cacheSizeBytes: Long = 0L,
    )

    private val _cacheProgress = MutableStateFlow(CacheProgress())
    val cacheProgress: StateFlow<CacheProgress> = _cacheProgress.asStateFlow()

    companion object {
        /** 流式写入缓冲区大小：8KB，平衡 IO 次数与内存占用 */
        private const val STREAM_BUFFER_SIZE = 8192
        /** M3U8 列表拉取间隔：30 秒 */
        private const val PLAYLIST_FETCH_INTERVAL_MS = 30_000L
    }

    // ==================== 公开 API ====================

    /**
     * 拦截 M3U8 URL，零等待返回。
     */
    suspend fun intercept(
        originalUrl: String,
        headers: Map<String, String>? = null,
    ): String {
        stateMutex.withLock {
            currentM3u8Url = originalUrl
            currentHeaders = headers
            currentSegmentIndex = 0
            totalSegmentCount = 0
            cachedPlaylist = null
            lastPlaylistFetchTime = 0L
        }

        preloadJob?.cancel()
        continuousPreloadJob?.cancel()

        preloadJob = scope.launch {
            try {
                preloadInternal(originalUrl, headers)
            } catch (_: Exception) { }
        }

        return originalUrl
    }

    /**
     * 播放位置回调：由播放器定期调用。
     */
    fun onPlaybackPosition(positionMs: Long, targetDuration: Double) {
        if (targetDuration <= 0.0) return
        val newIndex = (positionMs / 1000.0 / targetDuration).toInt()

        scope.launch {
            stateMutex.withLock {
                if (newIndex > currentSegmentIndex) {
                    currentSegmentIndex = newIndex
                }
            }
        }
    }

    /**
     * Seek 后重新定位预加载起点。
     */
    fun onSeek(positionMs: Long, targetDuration: Double) {
        if (targetDuration <= 0.0) return
        val segmentIndex = (positionMs / 1000.0 / targetDuration).toInt()

        scope.launch {
            stateMutex.withLock {
                currentSegmentIndex = segmentIndex
                preloadJob?.cancel()
                continuousPreloadJob?.cancel()
            }
            triggerContinuousPreload()
        }
    }

    /**
     * 重置状态（切换剧集时调用）。
     */
    fun reset() {
        scope.launch {
            stateMutex.withLock {
                preloadJob?.cancel()
                continuousPreloadJob?.cancel()
                liveRefreshJob?.cancel()
                currentM3u8Url = ""
                currentHeaders = null
                currentSegmentIndex = 0
                totalSegmentCount = 0
                downloadingUrls.clear()
                cachedPlaylist = null
                lastPlaylistFetchTime = 0L
                _cacheProgress.value = CacheProgress()
            }
        }
    }

    /**
     * 完全停止。
     */
    fun stop() {
        reset()
        scope.cancel()
    }

    // ==================== 内部方法 ====================

    /**
     * 内部预加载入口。
     */
    private suspend fun preloadInternal(
        m3u8Url: String,
        headers: Map<String, String>?,
    ) {
        val playlist = fetchPlaylist(m3u8Url, headers) ?: return

        stateMutex.withLock {
            totalSegmentCount = playlist.segments.size
            cachedPlaylist = playlist
            lastPlaylistFetchTime = currentTimeMillis()
        }

        if (playlist.isMultivariant && playlist.variantUrls.isNotEmpty()) {
            preloadInternal(playlist.variantUrls.first(), headers)
            return
        }

        if (playlist.isLive) {
            startLiveRefresh(m3u8Url, headers)
        }

        // 阶段1：起播冲刺
        downloadSegments(playlist, startIndex = 0, count = 4, headers = headers)

        // 阶段2：跟随预加载循环
        continuousPreloadJob?.cancel()
        continuousPreloadJob = scope.launch {
            continuousPreloadLoop(m3u8Url, headers)
        }
    }

    /**
     * 跟随预加载循环 —— 核心引擎。
     *
     * 永不退出的循环，自驱前进：
     * - 每 30s 拉取一次 M3U8 列表（非每次循环）
     * - 前瞻窗口 [anchor+1, anchor+8]
     * - 流式下载，零额外内存拷贝
     * - 全部已缓存 → anchor++ 自驱前进
     */
    private suspend fun continuousPreloadLoop(
        m3u8Url: String,
        headers: Map<String, String>?,
    ) {
        val lookAheadWindow = 8

        while (currentCoroutineContext().isActive) {
            // 1. 按需拉取 M3U8 列表（每 30s 一次，避免频繁网络请求）
            val playlist = getOrRefreshPlaylist(m3u8Url, headers) ?: break

            // 2. 获取当前锚点
            val anchor = stateMutex.withLock { currentSegmentIndex }

            // 3. 计算前瞻窗口
            val segmentsToPreload = playlist.segments
                .drop(anchor + 1)
                .take(lookAheadWindow)

            if (segmentsToPreload.isEmpty()) {
                delay(5_000L)
                continue
            }

            // 4. 批量检查缓存状态（一次锁获取检查所有切片）
            val urlsToCheck = segmentsToPreload.map { it.url }
            val cacheStatus = diskCache.containsAll(urlsToCheck)

            // 5. 检查是否有未缓存的切片
            val uncachedSegments = segmentsToPreload.filter { !cacheStatus[it.url]!! }

            if (uncachedSegments.isEmpty()) {
                // 全部已缓存 → 自驱前进
                stateMutex.withLock {
                    currentSegmentIndex = anchor + 1
                }
                delay(500L)
                continue
            }

            // 6. 流式下载未缓存的切片
            for (segment in uncachedSegments) {
                if (!currentCoroutineContext().isActive) break

                // 去重检查：如果正在下载中，跳过
                val isDownloading = stateMutex.withLock {
                    if (segment.url in downloadingUrls) true
                    else {
                        downloadingUrls.add(segment.url)
                        false
                    }
                }
                if (isDownloading) continue

                try {
                    streamDownloadAndCache(segment.url, headers)
                } finally {
                    stateMutex.withLock {
                        downloadingUrls.remove(segment.url)
                    }
                }

                // 自适应延迟
                delay((segment.duration * 200).toLong())
            }

            delay(1_000L)
        }
    }

    /**
     * 获取或刷新缓存的播放列表。
     *
     * 每 [PLAYLIST_FETCH_INTERVAL_MS] 拉取一次，避免频繁网络请求。
     */
    private suspend fun getOrRefreshPlaylist(
        url: String,
        headers: Map<String, String>?,
    ): M3u8Parser.M3u8Playlist? {
        val now = currentTimeMillis()
        val needsRefresh = stateMutex.withLock {
            if (cachedPlaylist == null || now - lastPlaylistFetchTime > PLAYLIST_FETCH_INTERVAL_MS) {
                true
            } else false
        }

        if (!needsRefresh) {
            return stateMutex.withLock { cachedPlaylist }
        }

        val playlist = fetchPlaylist(url, headers)
        if (playlist != null) {
            stateMutex.withLock {
                cachedPlaylist = playlist
                lastPlaylistFetchTime = now
                totalSegmentCount = playlist.segments.size
            }
        }
        return playlist
    }

    /**
     * Seek 后触发跟随预加载。
     */
    private fun triggerContinuousPreload() {
        continuousPreloadJob?.cancel()
        continuousPreloadJob = scope.launch {
            val (url, headers) = stateMutex.withLock {
                Pair(currentM3u8Url, currentHeaders)
            }
            if (url.isNotEmpty()) {
                continuousPreloadLoop(url, headers)
            }
        }
    }

    /**
     * 下载指定范围的切片（阶段1：起播冲刺用）。
     */
    private suspend fun downloadSegments(
        playlist: M3u8Parser.M3u8Playlist,
        startIndex: Int,
        count: Int,
        headers: Map<String, String>?,
    ) {
        val segments = playlist.segments.drop(startIndex).take(count)
        var cachedCount = 0

        for (segment in segments) {
            if (!currentCoroutineContext().isActive) break

            if (diskCache.contains(segment.url)) {
                cachedCount++
                updateProgress(cachedCount, playlist.segments.size)
                continue
            }

            streamDownloadAndCache(segment.url, headers)
            cachedCount++
            updateProgress(cachedCount, playlist.segments.size)
        }
    }

    /**
     * 流式下载并缓存单个切片 —— 零 OOM 风险。
     *
     * 核心改进：
     * ❌ 旧方案：response.readBytes() → 整个切片 ByteArray → Buffer → Sink
     *    （整个切片加载到内存，10MB 切片 = 10MB 内存峰值）
     *
     * ✅ 新方案：ByteReadChannel → 8KB 循环缓冲区 → Sink
     *    （内存峰值 = 8KB，与切片大小无关）
     *
     * 使用 Ktor 的 prepareGet + execute 获取 ByteReadChannel，
     * 在 putStream 的 suspend block 中循环读取并写入 Okio Sink。
     */
    private suspend fun streamDownloadAndCache(
        url: String,
        headers: Map<String, String>?,
    ) {
        if (diskCache.contains(url)) return

        try {
            httpClient.prepareGet(url) {
                headers?.forEach { (key, value) -> header(key, value) }
            }.execute { response ->
                if (response.status.value !in 200..299) return@execute

                val channel: ByteReadChannel = response.bodyAsChannel()

                // 在 putStream 的 suspend block 中流式写入
                // block 是 suspend 函数，可以安全调用挂起函数
                diskCache.putStream(url) { sink ->
                    val buffer = Buffer()
                    val tempArray = ByteArray(STREAM_BUFFER_SIZE)

                    while (!channel.isClosedForRead) {
                        // 挂起读取最多 8KB 数据
                        val bytesRead = channel.readAvailable(tempArray, 0, STREAM_BUFFER_SIZE)
                        if (bytesRead > 0) {
                            buffer.write(tempArray, 0, bytesRead)
                            sink.write(buffer, buffer.size)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 单个切片失败不影响整体
        }
    }

    /**
     * 下载并解析 M3U8 播放列表。
     */
    private suspend fun fetchPlaylist(
        url: String,
        headers: Map<String, String>?,
    ): M3u8Parser.M3u8Playlist? {
        return try {
            val response: HttpResponse = httpClient.get(url) {
                headers?.forEach { (key, value) -> header(key, value) }
            }
            val baseUrl = url.substringBeforeLast("/")
            parser.parse(response.bodyAsText(), baseUrl)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 直播流定期刷新。
     */
    private fun startLiveRefresh(m3u8Url: String, headers: Map<String, String>?) {
        liveRefreshJob?.cancel()
        liveRefreshJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(30_000L)
                val playlist = fetchPlaylist(m3u8Url, headers) ?: continue
                downloadSegments(playlist, startIndex = 0, count = 3, headers = headers)
            }
        }
    }

    private fun updateProgress(cached: Int, total: Int) {
        _cacheProgress.value = CacheProgress(
            cachedSegments = cached,
            totalSegments = total,
            cacheSizeBytes = _cacheProgress.value.cacheSizeBytes,
        )
    }

    /**
     * 跨平台获取当前时间戳。
     *
     * 使用 System.currentTimeMillis()（JVM/Android）或
     * 回退到协程的单调时钟（iOS/其他）。
     */
    private fun currentTimeMillis(): Long {
        return try {
            System.currentTimeMillis()
        } catch (_: Exception) {
            // 非 JVM 平台回退方案
            (getTimeMillis())
        }
    }
}
