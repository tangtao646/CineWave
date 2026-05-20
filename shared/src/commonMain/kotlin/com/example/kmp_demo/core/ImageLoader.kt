package com.example.kmp_demo.core

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toPath
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade

/**
 * 影视项目全局共享的 ImageLoader 初始化配置
 */
fun initializeCoil(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            // 1. 致命关键：注入跨平台网络加载器（使用 Ktor 3），负责抓取海报
            add(KtorNetworkFetcherFactory())
        }
        .memoryCache {
            // 2. 内存缓存策略：App 进程内快速滑动的性能保驾护航
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25) // 占用可用内存的 25%
                .build()
        }
        .diskCache {
            // 3. 磁盘缓存策略：脱离 JVM，全平台使用 Okio 实现本地持久化缓存
            DiskCache.Builder()
                .directory(
                    context.getPlatformCachePath().toPath() / "movie_poster_cache"
                ) // 跨平台的缓存目录
                .maxSizeBytes(50L * 1024 * 1024) // 限制 50MB 磁盘空间
                .build()
        }
        // 4. 视觉动画：加载成功时启用淡入淡出（在大图/封面展现时用户体验极佳）
        .crossfade(true)
        .build()
}
