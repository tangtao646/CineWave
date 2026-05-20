package com.example.kmp_demo.core

import coil3.PlatformContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun showToast(message: String) {
}

actual fun openAccessibilitySettings() {
}


actual fun PlatformContext.getPlatformCachePath(): String {
    // iOS 下通过 Foundation API 拿到沙盒 Cache 目录
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return paths.first() as String
}