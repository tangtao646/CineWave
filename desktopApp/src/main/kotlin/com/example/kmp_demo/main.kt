package com.example.kmp_demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.kmp_demo.core.player.domain.DesktopFullscreenController
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.videosource.di.coreVideosourceModule
import com.example.kmp_demo.di.commonModule
import com.example.kmp_demo.di.platformModule
import com.example.kmp_demo.features.domestic.di.domesticModuleJvm
import com.example.kmp_demo.features.film.di.filmModuleJvm
import com.example.kmp_demo.features.radio.di.radioModuleJvm
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

fun main() = application {
    // 初始化 Koin DI（仅在首次 composition 时执行，避免 recomposition 重复调用）
    remember {
        startKoin {
            printLogger(Level.INFO)
            modules(
                commonModule,
                platformModule,
                coreVideosourceModule,
                radioModuleJvm,
                filmModuleJvm,
                domesticModuleJvm,
            )
        }
    }

    val windowState = WindowState(
        size = DpSize(1280.dp, 800.dp),
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "CineWave",
        state = windowState,
        onPreviewKeyEvent = { keyEvent ->
            // 在 Compose 窗口级别拦截 ESC 键，阻止系统默认回退行为
            // 播放器内的 ESC 处理由 DesktopKeyboardHandler（AWT KeyboardFocusManager）负责
            if (keyEvent.key == Key.Escape) {
                true // 消费事件，阻止 Compose Desktop 默认的 ESC 行为
            } else {
                false // 其他按键正常传递
            }
        },
    ) {
        // 注入 Desktop 全屏控制器
        val fullscreenController = remember(windowState) {
            DesktopFullscreenController(
                windowState = windowState,
            )
        }
        CompositionLocalProvider(
            LocalFullscreenController provides fullscreenController
        ) {
            App()
        }
    }
}
