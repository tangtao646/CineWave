package com.example.kmp_demo.core.player.domain

import androidx.compose.runtime.staticCompositionLocalOf

interface FullscreenController {
    fun enterFullscreen()
    fun exitFullscreen()
}

// 提供一个全局的 CompositionLocal
val LocalFullscreenController = staticCompositionLocalOf<FullscreenController> {
    object : FullscreenController {
        override fun enterFullscreen() {}
        override fun exitFullscreen() {}
    }
}