package com.example.kmp_demo.di

import com.example.kmp_demo.core.data.local.room.AndroidAppContext
import com.example.kmp_demo.core.data.local.room.AppDatabase
import com.example.kmp_demo.core.data.local.room.getDatabaseBuilder
import com.example.kmp_demo.core.data.local.room.getRoomDatabase
import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.platform.ExoPlayerController
import com.example.kmp_demo.core.player.platform.getDefaultCacheDir
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android 平台特定的 Koin 模块
 */
actual val platformModule: Module = module {
    // === Room Database ===
    // 使用 AndroidAppContext 获取 Context，绕过 Koin 在 Common 层初始化时的上下文注入限制
    single<AppDatabase> {
        getRoomDatabase(getDatabaseBuilder())
    }

    // === Disk Cache ===
    single<DiskLruCache> {
        val cacheDir = getDefaultCacheDir(AndroidAppContext.context) + "/video_cache"
        DiskLruCache(cacheDir = cacheDir)
    }

    // === M3U8 Cache Interceptor ===
    single<M3u8CacheInterceptor> {
        val cacheDir = getDefaultCacheDir(AndroidAppContext.context) + "/video_cache"
        M3u8CacheInterceptor(
            httpClient = get(),
            diskCache = get(),
            cacheDir = cacheDir,
        )
    }

    // === Video Player Controller (ExoPlayer) ===
    single<IPlayerController> {
        ExoPlayerController(
            context = AndroidAppContext.context,
            diskCache = get(),
        )
    }
}
