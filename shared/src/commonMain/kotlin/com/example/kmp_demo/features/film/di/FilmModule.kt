package com.example.kmp_demo.features.film.di

import com.example.kmp_demo.core.data.local.room.AppDatabase
import com.example.kmp_demo.core.data.local.room.CoreRemoteKeyDao
import com.example.kmp_demo.features.film.data.local.FilmDao
import com.example.kmp_demo.features.film.data.local.FilmLocalDataSource
import com.example.kmp_demo.features.film.data.remote.ApiKeyProvider
import com.example.kmp_demo.features.film.data.remote.FilmApi
import com.example.kmp_demo.features.film.data.remote.SnifferDataSource
import com.example.kmp_demo.features.film.data.repository.FilmRepositoryImpl
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import com.example.kmp_demo.features.film.ui.FilmDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmViewModel
import com.example.kmp_demo.features.film.ui.search.FilmSearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val filmModule = module {
    // API Key Provider（从 Compose Resources 读取 tmdb_api_key.txt）
    single { ApiKeyProvider() }

    // API
    single { FilmApi(get(), get()) }

    // Sniffer DataSource (依赖 coreVideosourceModule 提供的 VideoSourceSearchEngine)
    single { SnifferDataSource(get()) }

    // Room DAOs
    single<FilmDao> { get<AppDatabase>().filmDao() }
    single<CoreRemoteKeyDao> { get<AppDatabase>().remoteKeyDao() }

    // Data Sources
    single { FilmLocalDataSource(get<FilmDao>(), get<CoreRemoteKeyDao>()) }

    // Repositories
    single<FilmRepository> { FilmRepositoryImpl(get(), get(), get()) }

    // ViewModels
    viewModelOf(::FilmViewModel)
    viewModelOf(::FilmSearchViewModel)
    viewModelOf(::FilmDetailViewModel)


}
