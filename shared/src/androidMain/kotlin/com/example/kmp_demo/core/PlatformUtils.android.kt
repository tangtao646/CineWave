package com.example.kmp_demo.core

import coil3.PlatformContext

/**
 * Android 平台特定工具函数实现
 */

actual fun showToast(message: String) {
    // Toast requires a Context, which is typically available in Compose via LocalContext
    // This function is a placeholder - actual Toast calls should use the composable context
    // For ViewModel effects, use the ShowToast effect pattern instead
}

actual fun openAccessibilitySettings() {
    // This function requires a Context to start an Activity
    // It's handled in the composable layer via LocalContext
}

actual fun PlatformContext.getPlatformCachePath(): String {
    return this.cacheDir.absolutePath
}