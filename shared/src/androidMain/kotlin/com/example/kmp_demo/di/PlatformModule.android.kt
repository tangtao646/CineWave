package com.example.kmp_demo.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.kmp_demo.core.data.local.room.AndroidAppContext
import com.example.kmp_demo.core.data.local.room.AppDatabase
import com.example.kmp_demo.core.data.local.room.getDatabaseBuilder
import com.example.kmp_demo.core.data.local.room.getRoomDatabase
import com.example.kmp_demo.core.player.cache.AdCleanStrategy
import com.example.kmp_demo.core.player.cache.AdSegmentFilter
import com.example.kmp_demo.core.player.cache.CacheMaintenanceStrategy
import com.example.kmp_demo.core.player.cache.DefaultAdSegmentFilter
import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.ExoPlayerCache
import com.example.kmp_demo.core.player.cache.LruCacheMaintenanceStrategy
import com.example.kmp_demo.core.player.cache.M3u8Sanitizer
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.FullscreenController
import com.example.kmp_demo.core.player.domain.IVideoPlayerController
import com.example.kmp_demo.core.player.platform.ExoPlayerController
import com.example.kmp_demo.core.player.platform.getDefaultCacheDir
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android 平台特定的 Koin 模块。
 *
 * ## 缓存架构变更
 * 从旧的「本地 HTTP 代理 (CacheProxyServer)」切换为
 * ExoPlayer 原生「SimpleCache + CacheDataSource」方案。
 *
 * - [ExoPlayerCache]：管理 SimpleCache 生命周期，全局单例，同时实现 [CacheMaintenanceStrategy]
 * - [DiskLruCache]：仍保留，供 SegmentCacheTracker 的 SeekBar 缓存可视化使用
 */
@OptIn(UnstableApi::class)
actual val platformModule: Module = module {

    // === Room Database ===
    single<AppDatabase> {
        getRoomDatabase(getDatabaseBuilder())
    }

    // === ExoPlayer 原生磁盘缓存（SimpleCache，全局单例） ===
    // ⚠️ 必须是 single{}，SimpleCache 不允许同一目录有多个实例
    single<ExoPlayerCache> {
        ExoPlayerCache(context = AndroidAppContext.context)
    }

    // === Cache Maintenance Strategy ===
    // 统一的 LRU 缓存维护策略，委托给 ExoPlayerCache 实现
    single<CacheMaintenanceStrategy> {
        LruCacheMaintenanceStrategy(delegate = get<ExoPlayerCache>())
    }

    // === DiskLruCache（供 SegmentCacheTracker SeekBar 可视化使用） ===
    // ExoPlayer 的 SimpleCache 已接管实际缓存，DiskLruCache 只用于读取缓存状态展示
    single<DiskLruCache> {
        val cacheDir = getDefaultCacheDir(AndroidAppContext.context) + "/video_cache"
        DiskLruCache(cacheDir = cacheDir)
    }

    // === Segment Cache Tracker（SeekBar 缓存标记可视化） ===
    single<SegmentCacheTracker> {
        SegmentCacheTracker(diskCache = get())
    }

    // === Ad Segment Filter ===
    // 广告切片过滤器，复用 commonMain 的过滤规则
    single<AdSegmentFilter> {
        DefaultAdSegmentFilter()
    }

    // === M3U8 Sanitizer ===
    // M3U8 播放列表清洗器，复用 commonMain 的过滤逻辑
    single<M3u8Sanitizer> {
        M3u8Sanitizer(
            adSegmentFilter = get(),
            cleanStrategy = AdCleanStrategy.REPLACE_WITH_GAP,
        )
    }

    // === Video Player Controller (ExoPlayer + SimpleCache + 广告过滤) ===
    // factory{} 而非 single{}：每次进入播放页创建新实例，退出时 release() 销毁
    // fullscreenController 由平台适配器通过 parametersOf() 传入
    factory<IVideoPlayerController> { params ->
        ExoPlayerController(
            context = AndroidAppContext.context,
            exoCache = get(),
            m3u8Sanitizer = get(),
            httpClient = get(),
            fullscreenController = params.getOrNull(),
        )
    }

}
