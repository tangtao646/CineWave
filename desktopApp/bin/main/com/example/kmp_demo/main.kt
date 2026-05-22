package com.example.kmp_demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.kmp_demo.core.player.domain.DesktopFullscreenController
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.videosource.di.coreVideosourceModule
import com.example.kmp_demo.di.commonModule
import com.example.kmp_demo.di.platformModule
import com.example.kmp_demo.features.domestic.di.domesticModule
import com.example.kmp_demo.features.film.di.filmModule
import com.example.kmp_demo.features.radio.di.radioModule
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

fun main() = application {
    // 初始化 Koin DI（必须在 Composable 之前）
    startKoin {
        printLogger(Level.INFO)
        modules(
            commonModule,
            platformModule,
            coreVideosourceModule,
            radioModule,
            filmModule,
            domesticModule,
        )
    }

    val windowState = WindowState(
        size = DpSize(1280.dp, 800.dp),
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "CineWave",
        state = windowState,
    ) {
        // 注入 Desktop 全屏控制器
        val fullscreenController = DesktopFullscreenController(windowState)
        CompositionLocalProvider(
            LocalFullscreenController provides fullscreenController
        ) {
            App()
        }
    }
}
