package com.example.kmp_demo.core.player.platform

/**
 * JVM/Desktop 平台默认缓存目录
 *
 * 使用用户主目录下的 .cinewave/cache 作为缓存根目录。
 */
internal actual fun getDefaultCacheDir(context: Any?): String {
    return "${System.getProperty("user.home")}/.cinewave/cache"
}
