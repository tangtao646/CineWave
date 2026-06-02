package com.example.kmp_demo.core.player.cache

import android.content.Context
import com.example.kmp_demo.core.PlatformLogger
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * ExoPlayer 原生磁盘缓存管理器（应用级单例）。
 *
 * ## 为什么是单例？
 * [SimpleCache] 要求同一时刻只能有一个实例指向同一目录。
 * 多实例会导致 "Cache UID mismatch" 崩溃或数据损坏。
 * 通过 companion object 保证全局唯一，由 Koin single{} 管理生命周期。
 *
 * ## 缓存策略
 * - 最大容量：5GB（LRU 淘汰，超出时自动删除最久未访问的切片）
 * - 缓存目录：{cacheDir}/exo_video_cache（与旧 DiskLruCache 目录隔离）
 *
 * @param context Application Context
 * @param maxBytes 最大缓存字节数，默认 5GB
 */
@OptIn(UnstableApi::class)
class ExoPlayerCache(
    context: Context,
    private val maxBytes: Long = 5L * 1024 * 1024 * 1024,
) {
    companion object {
        private const val TAG = "ExoPlayerCache"
    }
    val cache: SimpleCache
    private val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val cacheDir = File(context.cacheDir, "exo_video_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        // 使用 StandaloneDatabaseProvider 作为 SimpleCache 的数据库提供者
        val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /**
     * 核心检测与主动清理函数
     * 建议在：1. 每次视频播放完毕 2. 或进入 app 首页时 3. 或 open 新视频时 异步调用。
     */
    fun checkAndTrimCache() {
        cacheScope.launch {
            try {
                val currentBytes = cache.cacheSpace
                val triggerThreshold = (maxBytes * 0.90).toLong() // 90% 触发线
                val targetBytes = (maxBytes * 0.70).toLong()    // 清理到 70% 停止（留出1.5GB空白）

                val currentMB = currentBytes / (1024 * 1024)
                val triggerMB = triggerThreshold / (1024 * 1024)

                PlatformLogger.d(TAG, "缓存状态检查: 当前 ${currentMB}MB / 触发线 ${triggerMB}MB")

                if (currentBytes >= triggerThreshold) {
                    PlatformLogger.w(TAG, "⚠️ 缓存已达到 90% 临界点，启动后台大缓存清理...")

                    // 获取当前所有缓存资源的 key，并按最后访问时间升序排序（最久未访问的在前）
                    val sortedKeys = cache.keys.mapNotNull { key ->
                        val cachedSpans = cache.getCachedSpans(key)
                        val lastTouch = cachedSpans.maxOfOrNull { it.lastTouchTimestamp } ?: 0L
                        if (lastTouch > 0L) key to lastTouch else null
                    }.sortedBy { it.second } // 按时间从小到大排序

                    var bytesToFree = cache.cacheSpace - targetBytes
                    val freedMBBefore = (cache.cacheSpace / (1024 * 1024))

                    // 开始循环删除最旧的资源，直到降至 70% 水位线
                    for ((key, _) in sortedKeys) {
                        if (bytesToFree <= 0) break

                        // 计算该 key 占用的空间，防止删错
                        val resourceLength = cache.getCachedSpans(key).sumOf { it.length }

                        // 从 SimpleCache 中彻底移除该视频资源
                        cache.removeResource(key)
                        bytesToFree -= resourceLength
                    }

                    val freedMBAfter = (cache.cacheSpace / (1024 * 1024))
                    PlatformLogger.i(TAG, "✅ 后台清理完成！缓存已从 ${freedMBBefore}MB 降至 ${freedMBAfter}MB，已释放大片连续写入空间。")
                }
            } catch (e: Exception) {
                PlatformLogger.e(TAG, "主动清理缓存时发生异常: ${e.message}", e)
            }
        }
    }

    /**
     * 释放缓存资源。
     * 应在 Application 销毁时调用，通常由 Koin 容器管理。
     */
    fun release() {
        cache.release()
    }
}
