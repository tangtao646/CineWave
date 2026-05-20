package com.example.kmp_demo.core.videosource

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 资源站点配置加载器
 *
 * 从 Compose Multiplatform 的 [composeResources/files] 中加载 db.json，
 * 解析为 [VideoSourceSite] 列表。
 *
 * 职责单一：仅负责站点配置的加载与解析。
 */
class VideoSourceSiteLoader(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * 从原始 JSON 字符串解析站点列表。
     *
     * @param jsonString db.json 的原始内容
     * @return 解析成功的 [VideoSourceSite] 列表
     */
    fun loadFromJson(jsonString: String): List<VideoSourceSite> {
        val element = json.parseToJsonElement(jsonString)
        return when (element) {
            is JsonArray -> json.decodeFromJsonElement(element)
            else -> emptyList()
        }
    }

    /**
     * 获取所有活跃的站点。
     *
     * @param jsonString db.json 的原始内容
     * @return 仅包含 active = true 的站点列表
     */
    fun loadActiveSites(jsonString: String): List<VideoSourceSite> {
        return loadFromJson(jsonString).filter { it.active }
    }
}
