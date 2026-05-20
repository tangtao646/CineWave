package com.example.kmp_demo.core.player.platform

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains

/**
 * iOS 平台：使用 NSCachesDirectory 作为缓存根目录。
 *
 * @param context iOS 上忽略此参数，仅用于保持签名一致
 */
internal actual fun getDefaultCacheDir(context: Any?): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory, NSUserDomainMask, true
    )
    return paths.first() as? String ?: ""
}
