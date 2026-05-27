package com.example.kmp_demo.core.player.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
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
    maxBytes: Long = 5L * 1024 * 1024 * 1024,
) {
    val cache: SimpleCache

    init {
        val cacheDir = File(context.cacheDir, "exo_video_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        // 使用 StandaloneDatabaseProvider 作为 SimpleCache 的数据库提供者
        val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /**
     * 释放缓存资源。
     * 应在 Application 销毁时调用，通常由 Koin 容器管理。
     */
    fun release() {
        cache.release()
    }
}
