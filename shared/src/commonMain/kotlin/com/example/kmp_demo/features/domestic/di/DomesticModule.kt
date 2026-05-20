package com.example.kmp_demo.features.domestic.di

import com.example.kmp_demo.core.data.local.room.AppDatabase
import com.example.kmp_demo.core.data.local.room.CoreRemoteKeyDao
import com.example.kmp_demo.core.videosource.VideoSourceSearchEngine
import com.example.kmp_demo.core.videosource.VideoSourceSiteConfigProvider
import com.example.kmp_demo.core.videosource.VideoSourceSiteLoader
import com.example.kmp_demo.features.domestic.data.local.DomesticDao
import com.example.kmp_demo.features.domestic.data.local.DomesticLocalDataSource
import com.example.kmp_demo.features.domestic.data.remote.DomesticApi
import com.example.kmp_demo.features.domestic.data.remote.DomesticSearchEngine
import com.example.kmp_demo.features.domestic.data.repository.DomesticRepositoryImpl
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticSearchViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticViewModel
import io.ktor.client.HttpClient
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * 国内影视模块的 Koin DI 注册。
 *
 * 依赖 [coreVideosourceModule] 提供的 [VideoSourceSearchEngine]、[VideoSourceSiteLoader]、[VideoSourceSiteConfigProvider]。
 * 依赖 Room [AppDatabase] 提供的 [DomesticDao] 和 [CoreRemoteKeyDao]。
 */
val domesticModule = module {
    // DomesticApi — 基于 ffzyapi 的两步数据拉取
    single { DomesticApi(get<HttpClient>(), get<VideoSourceSiteLoader>(), get<VideoSourceSiteConfigProvider>()) }

    // DomesticSearchEngine 包装 VideoSourceSearchEngine（由 core/videosource 提供）
    single { DomesticSearchEngine(get<VideoSourceSearchEngine>()) }

    // Room DAOs
    single<DomesticDao> { get<AppDatabase>().domesticDao() }
    single<CoreRemoteKeyDao> { get<AppDatabase>().remoteKeyDao() }

    // Local DataSource
    single { DomesticLocalDataSource(get<DomesticDao>(), get<CoreRemoteKeyDao>()) }

    // Repository — 注入 DomesticApi + DomesticSearchEngine + DomesticLocalDataSource
    single<DomesticRepository> { DomesticRepositoryImpl(get(), get(), get()) }

    // ViewModels
    viewModelOf(::DomesticViewModel)
    viewModelOf(::DomesticSearchViewModel)
    viewModelOf(::DomesticDetailViewModel)

}
