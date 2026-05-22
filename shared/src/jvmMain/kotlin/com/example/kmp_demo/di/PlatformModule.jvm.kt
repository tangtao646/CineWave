package com.example.kmp_demo.di

import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import com.example.kmp_demo.core.player.domain.IPlayerController as VideoPlayerController
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import com.example.kmp_demo.features.radio.domain.player.IPlayerController as RadioPlayerController
import com.example.kmp_demo.features.radio.player.DesktopRadioPlayerController
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop (JVM) 平台特定的 Koin 模块
 *
 * ⚠️ 注意：Desktop 端不使用 Room 数据库。
 * 所有数据直接从网络加载，不持久化。
 *
 * 提供 Desktop 上所有平台相关依赖的实现：
 * - 磁盘缓存
 * - M3U8 缓存拦截器
 * - 视频播放器（JavaFX MediaPlayer）
 * - 电台播放器（JavaFX MediaPlayer）
 */
actual val platformModule: Module = module {
    // ❌ 移除 Room Database — Desktop 不需要数据库缓存

    // === Disk Cache ===
    single<DiskLruCache> {
        val cacheDir = "${System.getProperty("user.home")}/.cinewave/video_cache"
        DiskLruCache(cacheDir = cacheDir)
    }

    // === M3U8 Cache Interceptor ===
    single<M3u8CacheInterceptor> {
        val cacheDir = "${System.getProperty("user.home")}/.cinewave/video_cache"
        M3u8CacheInterceptor(
            httpClient = get(),
            diskCache = get(),
            cacheDir = cacheDir,
        )
    }

    // === Video Player Controller (ComposeMediaPlayer) ===
    single<VideoPlayerController> {
        DesktopVideoPlayerController()
    }

    // === Radio Player Controller (ComposeMediaPlayer) ===
    single<RadioPlayerController> {
        DesktopRadioPlayerController()
    }
}
