package com.example.kmp_demo.core.player.domain

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState

/**
 * Desktop 全屏控制器 — 使用 Compose Desktop 的 WindowState
 *
 * 在 Desktop 入口（main.kt）中通过 CompositionLocalProvider 注入。
 *
 * Compose Desktop 的 WindowState 没有直接的 isFullscreen 属性，
 * 全屏通过设置窗口大小和位置来实现。
 */
class DesktopFullscreenController(
    private val windowState: WindowState
) : FullscreenController {

    private var savedSize: DpSize? = null

    override fun enterFullscreen() {
        savedSize = windowState.size
        // Desktop 全屏通过最大化窗口实现
        windowState.size = DpSize(9999.dp, 9999.dp)
    }

    override fun exitFullscreen() {
        savedSize?.let { windowState.size = it }
        savedSize = null
    }
}
