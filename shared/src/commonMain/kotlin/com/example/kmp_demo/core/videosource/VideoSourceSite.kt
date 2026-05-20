package com.example.kmp_demo.core.videosource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 资源站点配置模型
 * 对应 db.json 中的站点配置
 */
@Serializable
data class VideoSourceSite(
    val key: String,
    val name: String,
    val api: String,
    val active: Boolean = true,
)
