package com.example.kmp_demo.core.player.domain

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop 全屏控制器 — 使用 Compose Desktop 的 [WindowPlacement.Fullscreen]。
 *
 * 在 Desktop 入口（[main.kt]）中通过 [CompositionLocalProvider] 注入。
 *
 * ## 真正的全屏实现
 * 使用 [WindowPlacement.Fullscreen] 让窗口真正全屏。
 * 注意：不能在窗口显示后动态修改 undecorated 属性（会抛出
 * [java.awt.IllegalComponentStateException]），因此全屏时依赖
 * [WindowPlacement.Fullscreen] 自身的行为来隐藏标题栏。
 *
 * @param windowState Compose Desktop 的窗口状态
 */
class DesktopFullscreenController(
    private val windowState: WindowState,
) : FullscreenController {

    private val _isFullScreen = MutableStateFlow(false)
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private var savedSize: DpSize? = null
    private var savedPlacement: WindowPlacement = WindowPlacement.Floating

    override fun enterFullscreen() {
        savedSize = windowState.size
        savedPlacement = windowState.placement
        windowState.placement = WindowPlacement.Fullscreen
        _isFullScreen.value = true
    }

    override fun exitFullscreen() {
        windowState.placement = savedPlacement
        savedSize?.let { windowState.size = it }
        savedSize = null
        _isFullScreen.value = false
    }
}
