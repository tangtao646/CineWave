package com.example.kmp_demo.di

import com.example.kmp_demo.features.radio.domain.player.IRadioPlayerController
import com.example.kmp_demo.features.radio.player.Media3PlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
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
}
