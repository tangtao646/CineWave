package com.example.kmp_demo.core.videosource

/**
 * 资源站点配置提供者接口。
 *
 * 抽象站点配置的加载方式，使核心搜索逻辑与资源加载机制解耦。
 * 实现类可以从 Compose Resources、网络、本地文件等不同来源加载配置。
 */
fun interface VideoSourceSiteConfigProvider {
    /**
     * 获取站点配置的原始 JSON 字符串。
     * 对应 db.json 的内容。
     */
    suspend fun getSitesJson(): String
}
