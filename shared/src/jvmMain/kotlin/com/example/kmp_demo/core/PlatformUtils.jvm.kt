package com.example.kmp_demo.core

import coil3.PlatformContext

/**
 * Desktop 平台特定工具函数实现
 */

actual fun showToast(message: String) {
    // Desktop 上使用标准输出替代 Toast
    println("[Toast] $message")
}

actual fun openAccessibilitySettings() {
    // Desktop 上无需无障碍设置
}

actual fun PlatformContext.getPlatformCachePath(): String {
    return "${System.getProperty("user.home")}/.cinewave"
}
