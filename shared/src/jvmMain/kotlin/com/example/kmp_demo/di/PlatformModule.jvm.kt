package com.example.kmp_demo.di

import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import com.example.kmp_demo.core.player.domain.IPlayerController as VideoPlayerController
import com.example.kmp_demo.core.player.platform.VlcjVideoPlayerController
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

    // === Video Player Controller (VLCJ) ===
    // 使用 VLCJ (libvlc) 实现桌面端视频播放，支持广泛的音视频格式。
    // 使用 single 而非 factory，因为 VLCJ 的 MediaPlayerFactory 是重量级对象，
    // 每次创建新实例会创建新的 libvlc 实例，浪费资源。
    // PlatformVideoPlayerScreen 的 DisposableEffect 会在退出时调用 release()。
    single<VideoPlayerController> {
        VideoPlayerController()
    }


    // === Radio Player Controller (ComposeMediaPlayer) ===
    single<RadioPlayerController> {
        DesktopRadioPlayerController()
    }
}
