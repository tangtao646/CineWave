package com.example.kmp_demo.features.domestic.di

import com.example.kmp_demo.core.videosource.VideoSourceSearchEngine
import com.example.kmp_demo.core.videosource.VideoSourceSiteConfigProvider
import com.example.kmp_demo.core.videosource.VideoSourceSiteLoader
import com.example.kmp_demo.features.domestic.data.remote.DomesticApi
import com.example.kmp_demo.features.domestic.data.remote.DomesticSearchEngine
import com.example.kmp_demo.features.domestic.data.repository.DomesticRepositoryJvm
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticSearchViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticViewModel
import io.ktor.client.HttpClient
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Desktop 版国内影视模块的 Koin DI 注册。
 *
 * 与 Android 版 [domesticModule] 的区别：
 * - ❌ 不注册 Room DAO 和 LocalDataSource
 * - ✅ 使用 [DomesticRepositoryJvm] 替代 [DomesticRepositoryImpl]
 * - ✅ 复用 commonMain 的 ViewModel
 */
val domesticModuleJvm = module {
    // DomesticApi — 基于 ffzyapi 的两步数据拉取
    single { DomesticApi(get<HttpClient>(), get<VideoSourceSiteLoader>(), get<VideoSourceSiteConfigProvider>()) }

    // DomesticSearchEngine 包装 VideoSourceSearchEngine（由 core/videosource 提供）
    single { DomesticSearchEngine(get<VideoSourceSearchEngine>()) }

    // Repository — 无 Room 缓存，直接使用 InMemoryPagingSource
    single<DomesticRepository> { DomesticRepositoryJvm(get(), get()) }

    // ViewModels
    viewModelOf(::DomesticViewModel)
    viewModelOf(::DomesticSearchViewModel)

    viewModelOf(::DomesticDetailViewModel)
}
