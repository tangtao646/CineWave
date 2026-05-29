package com.example.kmp_demo.core.player.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicReference

/**
 * 当前活跃播放器的键盘动作处理器。

 */
val LocalPlayerKeyActionHandler = staticCompositionLocalOf<((PlayerKeyAction) -> Unit)?> { null }

/**
 * 桥接 CompositionLocal 与 AWT 事件线程。
 *
 * [LocalPlayerKeyActionHandler] 只能在 Composable 上下文中读取，
 * 而 AWT 的 [KeyEventDispatcher] 在 AWT 事件线程中运行。
 * 此桥接器在 Composable 上下文中将处理器写入 [AtomicReference]，
 * 在 AWT 事件线程中读取。
 *
 * 使用 [AtomicReference] 确保线程安全。
 */
internal object PlayerKeyActionBridge {
    val handler: AtomicReference<((PlayerKeyAction) -> Unit)?> = AtomicReference(null)
}

/**
 * 桌面端全局键盘事件处理器。
 *
 * 使用 AWT 的 [KeyboardFocusManager] 注册全局按键监听器，
 * 将按键事件映射为 [PlayerKeyAction] 并通过 [LocalPlayerKeyActionHandler]
 * 转发给当前活跃的播放器。
 *
 * 这是 JVM 平台特定的实现，因为 Compose Desktop 的焦点管理
 * 限制导致嵌套 Composable 无法可靠捕获键盘事件。

 *
 * ## 按键映射
 *
 * | 按键 | 动作 | 状态 |
 * |------|------|------|
 * | 空格键 | [TogglePlayPause] | ✅ 已实现 |
 * | 右方向键 | [SeekForward] | 📋 预留 |
 * | 左方向键 | [SeekBackward] | 📋 预留 |
 * | 上方向键 | [VolumeUp] | 📋 预留 |
 * | 下方向键 | [VolumeDown] | 📋 预留 |
 */
@Composable
fun DesktopKeyboardHandler() {
    DisposableEffect(Unit) {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

        val keyEventDispatcher = KeyEventDispatcher { event ->
            if (event.id != KeyEvent.KEY_RELEASED) return@KeyEventDispatcher false

            val action = mapKeyEventToAction(event) ?: return@KeyEventDispatcher false

            // 通过桥接器读取当前处理器（线程安全）
            val handler = PlayerKeyActionBridge.handler.get()
            if (handler != null) {
                handler(action)
                true
            } else {
                false
            }

        }

        focusManager.addKeyEventDispatcher(keyEventDispatcher)

        onDispose {
            focusManager.removeKeyEventDispatcher(keyEventDispatcher)
        }
    }
}

/**
 * 将 AWT 按键事件映射为 [PlayerKeyAction]。
 *
 * 后续扩展方向键时只需在此函数中添加映射逻辑。
 */
private fun mapKeyEventToAction(event: KeyEvent): PlayerKeyAction? {
    return when (event.keyCode) {
        KeyEvent.VK_SPACE -> PlayerKeyAction.TogglePlayPause
        KeyEvent.VK_RIGHT -> PlayerKeyAction.SeekForward
        KeyEvent.VK_LEFT -> PlayerKeyAction.SeekBackward
        KeyEvent.VK_UP -> PlayerKeyAction.VolumeUp
        KeyEvent.VK_DOWN -> PlayerKeyAction.VolumeDown
        KeyEvent.VK_ESCAPE -> PlayerKeyAction.ExitFullscreen
        else -> null
    }
}
