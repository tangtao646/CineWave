package com.example.kmp_demo.core.player.cache

/**
 * 调试日志工具，通过 [enabled] 开关统一控制所有代理相关日志的打印。
 *
 * ## 用法
 * ```kotlin
 * DebugLog.d("CacheProxyServerJvm", "start() called")
 * DebugLog.d("DesktopVideoPlayerController", "Playing URL: $url")
 * ```
 *
 * ## 开关控制
 * - 开发调试时：`DebugLog.enabled = true`
 * - 发布/正常使用时：`DebugLog.enabled = false`
 * - 也可以在运行时通过代码动态切换
 */
object DebugLog {
    /** 全局开关：true=打印日志，false=静默 */
    var enabled: Boolean = true

    /**
     * 打印调试日志。
     * @param tag 日志标签，通常为类名
     * @param message 日志消息
     */
    fun d(tag: String, message: String) {
        if (enabled) {
            println("[$tag] $message")
        }
    }
}
