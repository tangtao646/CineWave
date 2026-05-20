package com.example.kmp_demo.core.player.platform

import android.content.Context

/**
 * Android 平台：使用 context.cacheDir 作为缓存根目录。
 *
 * @param context 必须为 android.content.Context 实例
 */
internal actual fun getDefaultCacheDir(context: Any?): String {
    val ctx = requireNotNull(context as? Context) {
        "Android getDefaultCacheDir requires a non-null Context"
    }
    // ctx.cacheDir already points to /data/user/0/<package>/cache
    // Do NOT append "/cache" again, or it will create a double /cache/cache/ path
    return ctx.cacheDir.absolutePath
}
