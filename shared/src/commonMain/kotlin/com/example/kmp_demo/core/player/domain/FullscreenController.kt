package com.example.kmp_demo.core.player.domain

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全屏控制器 — 平台端侧负责真正的全屏切换。
 *
 * ## 设计原则（依赖倒置）
 * - commonMain 定义接口，端侧（Android/Desktop）提供实现。
 * - 端侧上层只管通过此接口下发指令，具体全屏切换下放到实现类。
 * - [IVideoPlayerController.setFullscreen] 持有此引用，在切换全屏时调用。
 *
 * ## 使用方式
 * 在端侧入口（Activity / main.kt）通过 [CompositionLocalProvider] 注入。
 */
interface FullscreenController {
    /** 当前是否处于全屏模式 */
    val isFullScreen: StateFlow<Boolean>

    /** 进入全屏 */
    fun enterFullscreen()

    /** 退出全屏 */
    fun exitFullscreen()
}

/** 默认空实现（无操作），避免 CompositionLocal 崩溃 */
private val defaultFullscreenController = object : FullscreenController {
    override val isFullScreen: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override fun enterFullscreen() {}
    override fun exitFullscreen() {}
}

/** 提供一个全局的 CompositionLocal */
val LocalFullscreenController = staticCompositionLocalOf<FullscreenController> {
    defaultFullscreenController
}