package com.example.kmp_demo.di

import androidx.lifecycle.SavedStateHandle
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.radio.domain.player.IRadioPlayerController
import com.example.kmp_demo.features.radio.player.Media3PlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Android 应用模块的 Koin 模块
 */
val androidAppModule: Module = module {
    // === Radio Player Controller ===
    // 推荐使用 androidContext() 获取 Context，更加符合 Koin 最佳实践且更安全
    single<IRadioPlayerController> {
        Media3PlayerController(androidContext())
    }

    viewModel {
        // 1. 从 Koin 的注入上下文中拿到 Android 平台特有的 SavedStateHandle
        val savedStateHandle: SavedStateHandle = get()

        // 2. 从 handle 中安全地提取出传递过来的参数 "title"
        val title = savedStateHandle.get<String>("title")
            ?: throw IllegalArgumentException("title is required from SavedStateHandle")

        // 3. 实例化你的共享层 ViewModel，只把 title 塞给它
        DomesticDetailViewModel(
            repository = get(), // 自动从 Koin 容器中寻找 DomesticRepository 实例
            mediaTitle = title  // 注入解耦后的纯 String 参数
        )
    }
}
