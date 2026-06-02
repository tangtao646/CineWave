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
 * ## 缓存维护
 * 实现 [CacheMaintenanceStrategy] 接口，通过 [LruCacheMaintenanceStrategy]
 * 执行统一的 LRU 清理算法（90% 触发 → 清理到 70%）。
 * 在播放完毕、进入首页、打开新视频等时机调用 [checkAndTrim]。
 *
 * @param context Application Context
 * @param maxBytes 最大缓存字节数，默认 5GB
 */
@OptIn(UnstableApi::class)
class ExoPlayerCache(
    context: Context,
    override val maxBytes: Long = 5L * 1024 * 1024 * 1024,
) : CacheMaintenanceStrategy {

    companion object {
        private const val TAG = "ExoPlayerCache"
    }

    /** ExoPlayer SimpleCache 实例 */
    val cache: SimpleCache

    /** 当前缓存已用字节数，委托给 SimpleCache */
    override val cacheSpace: Long
        get() = cache.cacheSpace

    private val maintenanceStrategy = LruCacheMaintenanceStrategy(this)

    init {
        val cacheDir = File(context.cacheDir, "exo_video_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(cacheDir, evictor, databaseProvider)
    }

    // ==================== CacheMaintenanceStrategy 实现 ====================

    /**
     * 按最后访问时间升序排列的缓存 key 列表。
     * 最久未访问的 key 排在最前面。
     */
    override suspend fun keysSortedByLastAccess(): List<String> {
        return cache.keys.mapNotNull { key ->
            val cachedSpans = cache.getCachedSpans(key)
            val lastTouch = cachedSpans.maxOfOrNull { it.lastTouchTimestamp } ?: 0L
            if (lastTouch > 0L) key to lastTouch else null
        }.sortedBy { it.second }.map { it.first }
    }

    /**
     * 指定 key 占用的缓存字节数。
     */
    override suspend fun resourceLength(key: String): Long {
        return cache.getCachedSpans(key).sumOf { it.length }
    }

    /**
     * 从缓存中移除指定 key 的资源。
     */
    override suspend fun removeResource(key: String) {
        cache.removeResource(key)
    }

    /**
     * 执行缓存检查与主动清理。
     *
     * 委托给 [LruCacheMaintenanceStrategy] 执行统一的 LRU 清理算法：
     * 1. 检查当前缓存是否达到 90% 触发线
     * 2. 如果是，按最后访问时间升序排列所有 key
     * 3. 从最久未访问的开始删除，直到降至 70% 水位线
     */
    override fun checkAndTrim() {
        maintenanceStrategy.checkAndTrim()
    }

    /**
     * 释放缓存资源。
     * 应在 Application 销毁时调用，通常由 Koin 容器管理。
     */
    fun release() {
        cache.release()
    }
}
