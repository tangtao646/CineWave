package com.example.kmp_demo.core.player.cache

import com.example.kmp_demo.core.PlatformLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 缓存维护策略接口。
 *
 * 定义缓存检查与主动清理的契约，使 Android（ExoPlayer SimpleCache）
 * 和 Desktop（DiskLruCache）两端可以复用相同的 LRU 清理算法。
 *
 * ## 使用方式
 *
 * 在播放完毕、进入首页、打开新视频等时机调用 [checkAndTrim]：
 * ```kotlin
 * cacheMaintenance.checkAndTrim()
 * ```
 *
 * ## 实现要求
 *
 * 实现类必须提供：
 * - [cacheSpace]：当前缓存已用字节数
 * - [maxBytes]：缓存容量上限
 * - [keysSortedByLastAccess]：按最后访问时间升序排列的 key 列表
 * - [resourceLength]：指定 key 占用的字节数
 * - [removeResource]：从缓存中移除指定 key
 */
interface CacheMaintenanceStrategy {

    /** 当前缓存已用字节数 */
    val cacheSpace: Long

    /** 缓存容量上限（字节） */
    val maxBytes: Long

    /**
     * 按最后访问时间升序排列的缓存 key 列表。
     * 最久未访问的 key 排在最前面。
     */
    suspend fun keysSortedByLastAccess(): List<String>

    /** 指定 key 占用的缓存字节数 */
    suspend fun resourceLength(key: String): Long

    /** 从缓存中移除指定 key 的资源 */
    suspend fun removeResource(key: String)

    /**
     * 执行缓存检查与主动清理。
     *
     * 当缓存使用量达到 [maxBytes] 的 90% 时触发清理，
     * 按 LRU 顺序删除最久未访问的资源，直到降至 70% 水位线。
     *
     * 此方法在 [Dispatchers.IO] 协程中异步执行，不会阻塞调用方。
     */
    fun checkAndTrim()
}

/**
 * LRU 缓存维护策略的默认实现。
 *
 * 封装了通用的 LRU 清理算法：
 * 1. 检查当前缓存是否达到 90% 触发线
 * 2. 如果是，按最后访问时间升序排列所有 key
 * 3. 从最久未访问的开始删除，直到降至 70% 水位线
 *
 * 此算法提取自 [com.example.kmp_demo.core.player.cache.ExoPlayerCache.checkAndTrimCache]，
 * 使 Desktop 端的 [DiskLruCache] 也能复用相同的精细化清理策略。
 */
class LruCacheMaintenanceStrategy(
    private val delegate: CacheMaintenanceStrategy,
) : CacheMaintenanceStrategy by delegate {

    companion object {
        private const val TAG = "LruCacheMaintenance"
        /** 触发清理的阈值：缓存使用量达到上限的 90% */
        private const val TRIGGER_THRESHOLD_RATIO = 0.90
        /** 清理目标：降至上限的 70% */
        private const val TARGET_RATIO = 0.70
    }

    private val scope = CoroutineScope(SupervisorJob())

    override fun checkAndTrim() {
        scope.launch {
            try {
                val currentBytes = delegate.cacheSpace
                val triggerThreshold = (delegate.maxBytes * TRIGGER_THRESHOLD_RATIO).toLong()
                val targetBytes = (delegate.maxBytes * TARGET_RATIO).toLong()

                val currentMB = currentBytes / (1024 * 1024)
                val triggerMB = triggerThreshold / (1024 * 1024)

                PlatformLogger.d(TAG, "缓存状态检查: 当前 ${currentMB}MB / 触发线 ${triggerMB}MB")

                if (currentBytes < triggerThreshold) {
                    PlatformLogger.d(TAG, "缓存未达到触发线，无需清理")
                    return@launch
                }

                PlatformLogger.w(TAG, "⚠️ 缓存已达到 90% 临界点，启动后台大缓存清理...")

                val sortedKeys = delegate.keysSortedByLastAccess()
                var bytesToFree = currentBytes - targetBytes
                val freedMBBefore = currentMB

                for (key in sortedKeys) {
                    if (bytesToFree <= 0) break

                    val length = delegate.resourceLength(key)
                    if (length <= 0) continue

                    delegate.removeResource(key)
                    bytesToFree -= length
                }

                val freedMBAfter = delegate.cacheSpace / (1024 * 1024)
                PlatformLogger.i(
                    TAG,
                    "✅ 后台清理完成！缓存已从 ${freedMBBefore}MB 降至 ${freedMBAfter}MB，" +
                        "已释放大片连续写入空间。"
                )
            } catch (e: Exception) {
                PlatformLogger.e(TAG, "主动清理缓存时发生异常: ${e.message}", e)
            }
        }
    }
}
