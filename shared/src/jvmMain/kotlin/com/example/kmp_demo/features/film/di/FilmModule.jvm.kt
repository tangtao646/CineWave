package com.example.kmp_demo.features.film.di

import com.example.kmp_demo.features.film.data.remote.FilmApi
import com.example.kmp_demo.features.film.data.remote.SnifferDataSource
import com.example.kmp_demo.features.film.data.repository.FilmRepositoryJvm
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import com.example.kmp_demo.features.film.ui.FilmDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmViewModel
import com.example.kmp_demo.features.film.ui.search.FilmSearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Desktop 版电影模块的 Koin DI 注册。
 *
 * 与 Android 版 [filmModule] 的区别：
 * - ❌ 不注册 Room DAO 和 LocalDataSource
 * - ✅ 使用 [FilmRepositoryJvm] 替代 [FilmRepositoryImpl]
 * - ✅ 复用 commonMain 的 ViewModel
 */
val filmModuleJvm = module {
    // API
    single { FilmApi(get()) }

    // Sniffer DataSource (依赖 coreVideosourceModule 提供的 VideoSourceSearchEngine)
    single { SnifferDataSource(get()) }

    // Repository — 无 Room 缓存，直接使用 InMemoryPagingSource
    single<FilmRepository> { FilmRepositoryJvm(get(), get()) }

    // ViewModels
    viewModelOf(::FilmViewModel)
    viewModelOf(::FilmSearchViewModel)

    // FilmDetailViewModel 需要 movieId 参数，由各平台通过 parametersOf 传入
    viewModel { params ->
        FilmDetailViewModel(
            repository = get(),
            movieId = params.get(),
        )
    }
}
