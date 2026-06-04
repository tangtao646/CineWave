package com.example.kmp_demo.di

import com.example.kmp_demo.core.player.cache.AdCleanStrategy
import com.example.kmp_demo.core.player.cache.AdSegmentFilter
import com.example.kmp_demo.core.player.cache.CacheMaintenanceStrategy
import com.example.kmp_demo.core.player.cache.CacheProxyServer
import com.example.kmp_demo.core.player.cache.CacheProxyServerJvm
import com.example.kmp_demo.core.player.cache.DefaultAdSegmentFilter
import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.LruCacheMaintenanceStrategy
import com.example.kmp_demo.core.player.cache.M3u8Sanitizer
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.IVideoPlayerController
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import com.example.kmp_demo.features.radio.domain.player.IRadioPlayerController
import com.example.kmp_demo.features.radio.player.DesktopRadioPlayerController
import org.koin.core.module.Module
import org.koin.dsl.module
import uk.co.caprica.vlcj.factory.MediaPlayerFactory

/**
 * Desktop (JVM) 平台特定的 Koin 模块
 *
 * ⚠️ 注意：Desktop 端不使用 Room 数据库。
 * 所有数据直接从网络加载，不持久化。
 *
 * 提供 Desktop 上所有平台相关依赖的实现：
 * - 磁盘缓存
 * - M3U8 缓存拦截器
 * - 缓存维护策略
 */
actual val platformModule: Module = module {

    // === Disk Cache ===
    single<DiskLruCache> {
        val cacheDir = "${System.getProperty("user.home")}/.cinewave/video_cache"
        DiskLruCache(cacheDir = cacheDir)
    }

    // === Cache Maintenance Strategy ===
    // 统一的 LRU 缓存维护策略，在播放完毕、进入首页等时机调用 checkAndTrim()
    single<CacheMaintenanceStrategy> {
        LruCacheMaintenanceStrategy(delegate = get<DiskLruCache>())
    }

    // === Ad Segment Filter ===
    // 广告切片过滤器，用于在 M3U8 代理中过滤脏切片广告
    single<AdSegmentFilter> {
        DefaultAdSegmentFilter()
    }

    // === M3U8 Sanitizer ===
    // M3U8 播放列表清洗器，移除广告切片及其 #EXTINF 标签
    single<M3u8Sanitizer> {
        M3u8Sanitizer(
            adSegmentFilter = get(),
            cleanStrategy = AdCleanStrategy.DROP_COMPLETELY
        )
    }

    // === Cache Proxy Server (Netty) ===
    single<CacheProxyServer> {
        CacheProxyServerJvm(
            diskCache = get(),
            httpClient = get(),
            m3u8Sanitizer = get(),
        )
    }

    // === Segment Cache Tracker ===
    // 追踪 M3U8 切片缓存状态，用于在 SeekBar 上标记已缓存/未缓存区域
    single<SegmentCacheTracker> {
        SegmentCacheTracker(
            diskCache = get(),
        )
    }

    // === Video Player Controller (VLCJ) ===
    // 使用 VLCJ (libvlc) 实现桌面端视频播放，支持广泛的音视频格式。

    // 1. MediaPlayerFactory 是重量级对象，全局共享一个实例以节省资源并避免多次加载 native 库
    single<MediaPlayerFactory> {
        MediaPlayerFactory(
            "--no-video-title-show",
            "--quiet",
            "--no-snapshot-preview",
            // 🌟 解决时间戳紊乱/音视频不同步的真正合法参数
            "--audio-desync=10000",      // 允许音频和视频时间戳最大有 10 秒的漂移而不触发强制死锁/重置
            "--cr-average=80",           // 增大时钟参考信号(PCR)的平均权重窗口（默认40），平滑恶劣流的时间戳跳变

            // 🌟 针对 HLS (M3U8) 的专项抗抖动优化
            "--adaptive-logic=highest",  // 或者是 "bandwidth", "nearoptimal"。强制指定 HLS 自适应逻辑

            // 🌟 缓存与网络防卡顿
            "--network-caching=1500",    // 网络缓存拉到 1.5 秒。1 秒有时在切片丢失时太极限，1.5 秒更稳健
            "--live-caching=1500",       // 如果是直播/动态 M3U8 流，同步控制直播缓存

            // 🌟 强行容错了解复用器（Demuxer）
            "--demux=adaptive,any"       // 现代 VLC 负责 HLS/DASH 的模块叫 adaptive，而不是单纯的 hls
        )
    }

    // 2. 播放器控制器必须是 factory，因为每次进入播放页都需要一个新的 MediaPlayer 实例。
    // 旧的实例会在 PlatformVideoPlayerScreen 退出时被 release() 销毁。
    // fullscreenController 由平台适配器通过 parametersOf() 传入
    factory<IVideoPlayerController> { params ->
        DesktopVideoPlayerController(
            mediaPlayerFactory = get(),
            proxyServer = get(),
            cacheMaintenance = get<CacheMaintenanceStrategy>(),
            fullscreenController = params.getOrNull(),
        )
    }

    // === Radio Player Controller (VLCJ) ===
    single<IRadioPlayerController> {
        DesktopRadioPlayerController(mediaPlayerFactory = get())
    }
}
