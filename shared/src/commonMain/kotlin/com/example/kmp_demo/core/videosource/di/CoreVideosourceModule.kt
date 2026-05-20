package com.example.kmp_demo.core.videosource.di

import com.example.kmp_demo.core.videosource.ComposeResourceSiteConfigProvider
import com.example.kmp_demo.core.videosource.VideoSourceApiClient
import com.example.kmp_demo.core.videosource.VideoSourceSearchEngine
import com.example.kmp_demo.core.videosource.VideoSourceSiteConfigProvider
import com.example.kmp_demo.core.videosource.VideoSourceSiteLoader
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Core videosource 模块的 Koin DI 注册。
 *
 * 提供 [VideoSourceSearchEngine] 及其依赖的跨模块共享实例。
 * [filmModule] 和 [domesticModule] 都平级依赖此模块。
 */
val coreVideosourceModule = module {
    // 站点配置加载器
    single { VideoSourceSiteLoader() }

    // 站点配置提供者（从 Compose Resources 读取 db.json）
    single<VideoSourceSiteConfigProvider> { ComposeResourceSiteConfigProvider() }

    // 资源站 API 客户端
    single { VideoSourceApiClient(get<HttpClient>()) }

    // 视频源搜索引擎（协调多站点并行搜索）
    single { VideoSourceSearchEngine(get(), get(), get()) }
}
