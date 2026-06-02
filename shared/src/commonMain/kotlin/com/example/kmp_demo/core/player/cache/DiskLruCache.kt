package com.example.kmp_demo.core.player.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer

/**
 * 基于文件系统的 LRU 磁盘缓存。
 *
 * 设计要点：
 * - 最大容量 5GB，适用于影音切片缓存
 * - 使用文件最后修改时间（lastModifiedAtMillis）实现 LRU 淘汰
 * - 线程安全（Mutex 保护）
 * - 缓存目录由外部指定，用户可手动清理
 * - 使用 okio.FileSystem.atomicMove() 进行原子写入，避免写入中断导致文件损坏
 * - 惰性淘汰 + 定期检查（S5 优化），避免每次写入 O(n log n) 排序
 * - 流式读写支持：getSource() / putStream() 避免大文件 OOM
 *
 * 性能与内存安全：
 * - 所有全量 ByteArray 方法（get/put）标记为 @Deprecated，引导使用流式方法
 * - putStream 使用 Okio BufferedSink + emit() 确保数据落盘
 * - evictIfNeeded 在锁内完成（但 IO 写入已在锁外完成）
 * - scanCurrentSize 延迟到首次使用时执行，不阻塞构造
 *
 * ## 锁优化（Bug 3 修复）
 *
 * 旧版本：getSource() 在 mutex.withLock 内执行文件读取，阻塞所有写入路径。
 * 新版本：Mutex 仅保护元数据（currentSize、路径映射），IO 操作移到锁外。
 *
 * - getSource(): 锁外执行文件读取，锁内仅做存在性检查
 * - putStream(): IO 写入在锁外执行，仅元数据更新在锁内
 * - contains(): 使用 tryLock 避免在非协程上下文中阻塞
 *
 * @param cacheDir 缓存目录路径（如 /data/data/.../cache/video_cache/）
 * @param maxSizeBytes 最大缓存大小，默认 5GB
 * @param fileSystem 文件系统实现，默认使用 okio.FileSystem.SYSTEM（跨平台）
 */
class DiskLruCache(
    private val cacheDir: String,
    override val maxBytes: Long = 5L * 1024 * 1024 * 1024, // 5GB
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : CacheMaintenanceStrategy {
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 增量跟踪当前缓存总大小，避免每次遍历计算 */
    private var currentSize: Long = 0L
    override val cacheSpace: Long
        get() = currentSize
    private var sizeInitialized = false

    data class CacheStats(
        val totalSizeBytes: Long,
        val fileCount: Int,
        val maxSizeBytes: Long,
    )

    init {
        fileSystem.createDirectories(cacheDir.toOkioPath())
        // 延迟初始化：scanCurrentSize 在首次使用时执行，不阻塞构造
        scope.launch {
            scanCurrentSize()
            sizeInitialized = true
        }
        // 启动定期淘汰协程（每 30 秒检查一次，避免阻塞写入路径）
        scope.launch {
            while (isActive) {
                delay(30_000L)
                evictIfNeeded()
            }
        }
    }

    // ==================== 流式方法（推荐） ====================

    /**
     * 流式获取缓存文件，返回 Okio [Source]，避免一次性加载 ByteArray 导致 OOM。
     *
     * ## 锁优化
     *
     * 旧版本：整个方法在 mutex.withLock 内执行，文件读取期间阻塞所有写入路径。
     * 新版本：Mutex 仅保护存在性检查和元数据更新，文件读取在锁外执行。
     *
     * 读取后异步触发轻量级时间戳更新（[touchFile]），绝不阻塞本次读取。
     *
     * @param key 缓存键（通常是 URL）
     * @return Okio Source，调用方需自行 close()；缓存不存在时返回 null
     */
    suspend fun getSource(key: String): Source? {
        // 仅在锁内做存在性检查（极短操作）
        val path = mutex.withLock {
            val p = keyToPath(key)
            if (!fileSystem.exists(p)) return@withLock null
            p
        } ?: return null

        // 文件读取在锁外执行，不阻塞写入路径
        // 异步触发轻量级时间戳更新，绝不阻塞本次读取
        scope.launch {
            touchFile(path)
        }

        return fileSystem.source(path)
    }

    /**
     * 流式写入缓存，通过 [block] 回调提供 Okio [Sink]，调用方直接写入数据。
     *
     * 写入流程：
     * 1. 先写入临时文件（.tmp）
     * 2. 原子移动到目标路径（避免写入中断导致文件损坏）
     * 3. 更新 currentSize
     * 4. 触发惰性淘汰检查
     *
     * ## 锁优化（Bug 3 修复）
     *
     * 旧版本：整个方法在 mutex.withLock 内执行，网络 IO 写入期间阻塞所有读取路径。
     * 新版本：IO 写入在 Mutex 外执行，仅元数据更新在锁内。
     *
     * block 是 suspend 函数，允许调用方在写入前执行挂起操作（如网络读取），
     * 但写入 Sink 本身是同步操作，不会阻塞协程调度器。
     *
     * @param key 缓存键（通常是 URL）
     * @param block 接收 [Sink] 的回调，在此回调中执行写入操作
     */
    suspend fun putStream(key: String, block: suspend (Sink) -> Unit) {
        val path = keyToPath(key)
        val tmpPath = cacheDir.toOkioPath() / "${path.name}.tmp"

        // ── IO 写入在 Mutex 外执行，避免长时间持锁阻塞读取路径 ──
        val sink = fileSystem.sink(tmpPath).buffer()
        sink.use { bufferedSink ->
            block(bufferedSink)
            bufferedSink.emit()
        }

        // ── 仅元数据更新在 Mutex 内 ──
        mutex.withLock {
            if (fileSystem.exists(path)) {
                currentSize -= fileSystem.metadataOrNull(path)?.size ?: 0L
            }
            fileSystem.atomicMove(tmpPath, path)
            currentSize += fileSystem.metadataOrNull(path)?.size ?: 0L
            checkAndEvict()
        }
    }

    // ==================== ByteArray 方法（保留向后兼容，但不推荐） ====================

    /**
     * 从缓存读取数据（全量 ByteArray 模式）。
     *
     * ⚠️ 对于大文件（如视频切片），请使用 [getSource] 流式读取以避免 OOM。
     *
     * 优化：移除原有的原子重写整个文件逻辑，改为轻量级异步时间戳更新，
     * 消除磁盘 IO 剧烈抖动。
     */
    @Deprecated("Use getSource() for streaming read to avoid OOM on large files")
    suspend fun get(key: String): ByteArray? = mutex.withLock {
        val path = keyToPath(key)
        if (!fileSystem.exists(path)) return@withLock null

        val data = fileSystem.read(path) {
            readByteArray()
        }

        scope.launch {
            touchFile(path)
        }
        data
    }

    /**
     * 将数据写入缓存（全量 ByteArray 模式）。
     *
     * ⚠️ 对于大文件（如视频切片），请使用 [putStream] 流式写入以避免 OOM。
     */
    @Deprecated("Use putStream() for streaming write to avoid OOM on large files")
    suspend fun put(key: String, data: ByteArray) = mutex.withLock {
        val path = keyToPath(key)
        val tmpPath = cacheDir.toOkioPath() / "${path.name}.tmp"

        fileSystem.write(tmpPath) {
            write(data)
        }
        if (fileSystem.exists(path)) {
            currentSize -= fileSystem.metadataOrNull(path)?.size ?: 0L
        }
        fileSystem.atomicMove(tmpPath, path)
        currentSize += data.size
    }

    // ==================== 查询与维护 ====================

    /**
     * 检查缓存中是否存在指定 key。
     *
     * 非挂起函数，可在非协程上下文（如 ExoPlayer DataSource 回调）中直接调用。
     * 内部使用 [Mutex.tryLock] 尝试获取锁，若锁被占用则返回 false（保守策略）。
     */
    fun contains(key: String): Boolean {
        // 使用 tryLock 避免在非协程上下文中阻塞
        if (!mutex.tryLock()) return false
        try {
            return fileSystem.exists(keyToPath(key))
        } finally {
            mutex.unlock()
        }
    }

    /**
     * 批量检查缓存状态（高性能版本）。
     *
     * 相比多次调用 [contains]，此方法只获取一次锁，减少锁竞争。
     *
     * @param keys 需要检查的 key 列表
     * @return Map<key, exists>
     */
    suspend fun containsAll(keys: List<String>): Map<String, Boolean> = mutex.withLock {
        keys.associateWith { key -> fileSystem.exists(keyToPath(key)) }
    }

    /**
     * 获取缓存统计信息。
     */
    suspend fun stats(): CacheStats = mutex.withLock {
        var totalSize = 0L
        var count = 0
        fileSystem.list(cacheDir.toOkioPath()).forEach { path ->
            if (!path.name.endsWith(".tmp")) {
                totalSize += fileSystem.metadataOrNull(path)?.size ?: 0L
                count++
            }
        }
        CacheStats(totalSizeBytes = totalSize, fileCount = count, maxSizeBytes = maxBytes)
    }

    /**
     * 清空所有缓存。
     */
    suspend fun clear() = mutex.withLock {
        fileSystem.deleteRecursively(cacheDir.toOkioPath())
        fileSystem.createDirectories(cacheDir.toOkioPath())
        currentSize = 0L
    }

    // ==================== CacheMaintenanceStrategy 实现 ====================

    override suspend fun keysSortedByLastAccess(): List<String> = mutex.withLock {
        fileSystem.list(cacheDir.toOkioPath())
            .filter { !it.name.endsWith(".tmp") }
            .map { path ->
                val key = pathToKey(path)
                val lastTouch = fileSystem.metadataOrNull(path)?.lastModifiedAtMillis ?: 0L
                key to lastTouch
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    override suspend fun resourceLength(key: String): Long = mutex.withLock {
        val path = keyToPath(key)
        fileSystem.metadataOrNull(path)?.size ?: 0L
    }

    override suspend fun removeResource(key: String) {
        mutex.withLock {
            val path = keyToPath(key)
            if (fileSystem.exists(path)) {
                currentSize -= fileSystem.metadataOrNull(path)?.size ?: 0L
                fileSystem.delete(path)
            }
        }
    }

    override fun checkAndTrim() {
        // 委托给 LruCacheMaintenanceStrategy 执行统一的 LRU 清理算法
        LruCacheMaintenanceStrategy(this).checkAndTrim()
    }

    fun release() {
        scope.cancel()
    }

    // ==================== 内部方法 ====================

    /**
     * 轻量化更新文件最后修改时间，避免原子重写整个大文件的巨大 IO 开销。
     */
    private fun touchFile(path: Path) {
        try {
            path.toFile().setLastModified(System.currentTimeMillis())
        } catch (_: Exception) {
            // 非 JVM 平台（如 iOS）不支持 toFile()，静默忽略
        }
    }

    /**
     * LRU 淘汰：当总大小超过上限时，删除最早访问的文件。
     *
     * 性能优化：在锁内完成排序（文件列表快照），但 IO 删除操作在锁内执行。
     * 由于淘汰操作不频繁（每 30 秒检查一次），锁持有时间可接受。
     */
    private suspend fun evictIfNeeded() = mutex.withLock {
        if (!sizeInitialized) return@withLock
        if (currentSize <= maxBytes) return@withLock

        // 在锁内获取文件列表快照并排序
        val files = fileSystem.list(cacheDir.toOkioPath())
            .filter { !it.name.endsWith(".tmp") }
            .map { path -> path to (fileSystem.metadataOrNull(path)?.lastModifiedAtMillis ?: 0L) }
            .sortedBy { it.second } // 最旧的在前

        for ((path, _) in files) {
            if (currentSize <= maxBytes) break
            currentSize -= fileSystem.metadataOrNull(path)?.size ?: 0L
            fileSystem.delete(path)
        }
    }

    /**
     * 快速检查并触发淘汰（在 putStream 写入后调用）。
     */
    private suspend fun checkAndEvict() {
        if (currentSize > maxBytes) {
            evictIfNeeded()
        }
    }

    /** 启动时扫描缓存目录，初始化 currentSize */
    private suspend fun scanCurrentSize() {
        mutex.withLock {
            currentSize = fileSystem.list(cacheDir.toOkioPath())
                .filter { !it.name.endsWith(".tmp") }
                .sumOf { fileSystem.metadataOrNull(it)?.size ?: 0L }
        }
    }

    /** URL → 合法文件名 */
    private fun keyToPath(key: String): Path {
        val fileName = key
            .replace("https://", "").replace("http://", "")
            .replace("/", "_").replace("?", "_")
            .replace("&", "_").replace("=", "_").replace(":", "_")
        return cacheDir.toOkioPath() / fileName
    }

    /** 文件名 → URL（[keyToPath] 的逆操作） */
    private fun pathToKey(path: Path): String {
        val fileName = path.name
        // 逆向替换：_ → / 等，但无法完美还原原始 URL，仅用于排序和统计
        // 这里直接返回文件名作为 key 标识
        return fileName
    }
}

/**
 * 将字符串路径转换为 okio.Path。
 */
internal fun String.toOkioPath(): Path = this.toPath(normalize = false)

/**
 * 安全获取 FileSystem.metadata，不存在时返回 null。
 */
private fun FileSystem.metadataOrNull(path: Path): okio.FileMetadata? {
    return try {
        metadata(path)
    } catch (_: Exception) {
        null
    }
}
