package com.example.kmp_demo.di

import com.example.kmp_demo.core.network.createHttpClient
import com.example.kmp_demo.core.player.domain.ShareUrlResolver
import com.example.kmp_demo.core.security.SensitiveWordFilter
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * 通用 Koin 模块（跨平台）
 *
 * 提供所有跨平台共享的依赖。
 * 平台特定的依赖（IVideoPlayerController, AppDatabase）由 platformModule 提供。
 */
val commonModule = module {
    // === Network ===
    single<HttpClient> { createHttpClient() }

    // === Domain ===
    single { ShareUrlResolver(get()) }

    // === Security ===
    single { SensitiveWordFilter() }


}
