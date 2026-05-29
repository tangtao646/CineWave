package com.example.kmp_demo.features.radio.di

import com.example.kmp_demo.features.radio.data.remote.IpApiService
import com.example.kmp_demo.features.radio.data.remote.RadioApiService
import com.example.kmp_demo.features.radio.data.repository.RadioRepositoryJvm
import com.example.kmp_demo.features.radio.domain.player.IRadioPlayerController
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository
import com.example.kmp_demo.features.radio.player.DesktopRadioPlayerController
import com.example.kmp_demo.features.radio.player.RadioPlayerManager
import com.example.kmp_demo.features.radio.ui.list.RadioListViewModel
import com.example.kmp_demo.features.radio.ui.search.RadioSearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Desktop 版电台模块的 Koin DI 注册。
 *
 * 与 Android 版 [radioModule] 的区别：
 * - ❌ 不注册 Room DAO 和 LocalDataSource
 * - ✅ 使用 [RadioRepositoryJvm] 替代 [RadioRepositoryImpl]
 * - ✅ 使用 [DesktopRadioPlayerController]（JavaFX）替代 Android 的 ExoPlayer 实现
 * - ✅ 复用 commonMain 的 ViewModel 和 RadioPlayerManager
 */
val radioModuleJvm = module {
    // === API Services ===
    factory { RadioApiService(get()) }
    factory { IpApiService(get()) }

    // === Repository — 无 Room 缓存 ===
    single<RadioRepository> { RadioRepositoryJvm(get(), get()) }

    // === Player Controller — VLCJ MediaPlayer ===
    // 使用全局单例 MediaPlayerFactory 创建电台播放器
    single<IRadioPlayerController> { DesktopRadioPlayerController(mediaPlayerFactory = get()) }

    // === Player Manager ===
    single { RadioPlayerManager(get()) }

    // === ViewModels ===
    viewModelOf(::RadioListViewModel)
    viewModelOf(::RadioSearchViewModel)
}
