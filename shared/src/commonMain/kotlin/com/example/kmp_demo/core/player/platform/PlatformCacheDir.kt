package com.example.kmp_demo.core.player.platform

/**
 * 获取平台默认缓存根目录。
 *
 * 这是一个 expect/actual 声明，各平台提供自己的实现：
 * - Android：使用 context.cacheDir
 * - iOS：使用 NSCachesDirectory
 *
 * @param context 可选的平台上下文（Android 上为 android.content.Context，iOS 上忽略）
 */
internal expect fun getDefaultCacheDir(context: Any? = null): String
