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
 *
 * 由播放器页面通过 [CompositionLocalProvider] 设置，
 * 由 [DesktopKeyboardHandler] 在捕获键盘事件时调用。
 *
 * 使用 CompositionLocal 而非全局单例，确保：
 * 1. 类型安全 — 编译期检查
 * 2. 作用域隔离 — 只有播放器子树能设置
 * 3. 自动生命周期 — 离开播放器页面自动恢复默认值
 *
 * 由于 AWT 的 [KeyEventDispatcher] 在非 Composable 线程中运行，
 * 无法直接读取 CompositionLocal，因此通过 [PlayerKeyActionBridge] 桥接。
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
 * ## 使用方式
 *
 * 在 [App.jvm.kt] 顶层调用一次：
 * ```kotlin
 * DesktopKeyboardHandler()
 * ```
 *
 * 播放器页面通过 [CompositionLocalProvider] 设置动作处理器：
 * ```kotlin
 * CompositionLocalProvider(LocalPlayerKeyActionHandler provides { action ->
 *     when (action) {
 *         PlayerKeyAction.TogglePlayPause -> manager.togglePlayPause()
 *         else -> {}
 *     }
 * }) {
 *     SharedVideoPlayerScreen(...)
 * }
 * ```
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
                true // 消费事件
            } else {
                false // 没有播放器处理，不消费
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
        // 预留方向键映射
        // KeyEvent.VK_RIGHT -> PlayerKeyAction.SeekForward
        // KeyEvent.VK_LEFT -> PlayerKeyAction.SeekBackward
        // KeyEvent.VK_UP -> PlayerKeyAction.VolumeUp
        // KeyEvent.VK_DOWN -> PlayerKeyAction.VolumeDown
        else -> null
    }
}
