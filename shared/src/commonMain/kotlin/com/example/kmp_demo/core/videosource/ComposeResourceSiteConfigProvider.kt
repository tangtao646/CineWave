package com.example.kmp_demo.core.videosource

import cinewave.shared.generated.resources.Res
import org.jetbrains.compose.resources.InternalResourceApi

/**
 * 基于 Compose Resources 的站点配置提供者。
 *
 * 从 [composeResources/files/db.json] 读取站点配置。
 * 使用 Compose Multiplatform 生成的 [Res] 类访问资源文件。
 */
class ComposeResourceSiteConfigProvider : VideoSourceSiteConfigProvider {

    @OptIn(InternalResourceApi::class)
    override suspend fun getSitesJson(): String {
        val bytes = Res.readBytes("files/db.json")
        return bytes.decodeToString()
    }
}
