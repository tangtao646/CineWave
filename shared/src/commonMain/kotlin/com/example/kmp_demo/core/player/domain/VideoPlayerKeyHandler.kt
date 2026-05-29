package com.example.kmp_demo.core.player.domain

/**
 * 桌面端视频播放器键盘事件处理器。
 *
 * 由于 Compose Desktop 的焦点管理限制，嵌套的 Composable 无法可靠地
 * 捕获键盘事件。因此使用全局可变的 [onSpaceKey] 引用，让播放器页面
 * 在挂载时注册回调，在 [App.jvm.kt] 顶层捕获空格键时调用。
 *
 * 使用方式：
 * 1. [SharedVideoPlayerScreen] 在挂载时设置 [onSpaceKey] 回调
 * 2. [App.jvm.kt] 的顶层 [Box] 在空格键按下时调用 [onSpaceKey]()
 *
 * 这是一个线程安全的单例，使用 @Volatile 确保跨线程可见性。
 */
object VideoPlayerKeyHandler {
    /**
     * 当前活跃播放器的空格键回调。
     * 由 [SharedVideoPlayerScreen] 在挂载时设置，卸载时置空。
     */
    @Volatile
    var onSpaceKey: (() -> Unit)? = null
}
