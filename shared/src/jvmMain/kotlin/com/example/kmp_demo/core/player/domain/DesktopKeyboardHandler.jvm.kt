package com.example.kmp_demo.core.player.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * 桌面端全局键盘事件处理器。
 *
 * 使用 AWT 的 [KeyboardFocusManager] 注册全局按键监听器，
 * 捕获空格键事件并转发给当前活跃的播放器。
 *
 * 这是 JVM 平台特定的实现，因为 Compose Desktop 的焦点管理
 * 限制导致嵌套 Composable 无法可靠捕获键盘事件。
 *
 * 使用方式：
 * ```kotlin
 * DesktopKeyboardHandler()
 * ```
 *
 * 在 [App.jvm.kt] 的顶层 Composable 中调用即可。
 */
@Composable
fun DesktopKeyboardHandler() {
    DisposableEffect(Unit) {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

        val keyEventDispatcher = KeyEventDispatcher { event ->
            if (event.id == KeyEvent.KEY_RELEASED && event.keyCode == KeyEvent.VK_SPACE) {
                val handler = VideoPlayerKeyHandler.onSpaceKey
                if (handler != null) {
                    handler()
                    true // 消费事件
                } else {
                    false // 没有播放器处理，不消费
                }
            } else {
                false // 不处理其他按键
            }
        }

        focusManager.addKeyEventDispatcher(keyEventDispatcher)

        onDispose {
            focusManager.removeKeyEventDispatcher(keyEventDispatcher)
        }
    }
}
