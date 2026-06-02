package com.example.kmp_demo.core

/**
 * 跨平台日志工具。
 *
 * 各平台提供 [actual] 实现，通过 [LOG_ENABLED] 编译期常量统一控制日志是否输出。
 *
 * ## 用法
 * ```kotlin
 * PlatformLogger.d("MyTag", "message")
 * PlatformLogger.e("MyTag", "error message")
 * PlatformLogger.e("MyTag", "error message", exception)
 * ```
 */
expect object PlatformLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable?)
}
