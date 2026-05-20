package com.example.kmp_demo.features.radio.di

import com.example.kmp_demo.core.data.local.room.AppDatabase
import com.example.kmp_demo.features.radio.data.local.RadioLocalDataSource
import com.example.kmp_demo.features.radio.data.remote.IpApiService
import com.example.kmp_demo.features.radio.data.remote.RadioApiService
import com.example.kmp_demo.features.radio.data.repository.RadioRepositoryImpl
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository
import com.example.kmp_demo.features.radio.player.RadioPlayerManager
import com.example.kmp_demo.features.radio.ui.list.RadioListViewModel
import com.example.kmp_demo.features.radio.ui.search.RadioSearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val radioModule = module {
    // === API Services ===
    factory { RadioApiService(get()) }
    factory { IpApiService(get()) }

    // === Room Database & DAOs ===
    // AppDatabase 由 platformModule 提供（因为 getDatabaseBuilder() 是 expect 函数）
    single { get<AppDatabase>().radioDao() }
    single { get<AppDatabase>().remoteKeyDao() }

    // === Local Data Sources ===
    factory { RadioLocalDataSource(get(), get()) }

    // === Repositories ===
    single<RadioRepository> { RadioRepositoryImpl(get(), get(), get()) }

    // === Player ===
    // IPlayerController 由 platformModule 提供
    single { RadioPlayerManager(get()) }

    // === ViewModels ===
    viewModelOf(::RadioListViewModel)
    viewModelOf(::RadioSearchViewModel)
}