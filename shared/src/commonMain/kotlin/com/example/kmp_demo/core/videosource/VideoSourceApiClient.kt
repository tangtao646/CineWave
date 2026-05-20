package com.example.kmp_demo.core.videosource

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * 资源站点 API 客户端。
 *
 * 负责向单个资源站点发送搜索请求并解析响应。
 * 职责单一：仅处理 HTTP 通信与 JSON 反序列化。
 *
 * 资源站 API 格式：
 *   GET {site.api}?ac=detail&wd={keyword}
 * 返回 JSON: { "code": 1, "list": [...] }
 */
class VideoSourceApiClient(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * 在指定站点搜索影片。
     *
     * @param site 目标资源站点
     * @param keyword 搜索关键词
     * @return API 响应，失败时返回 null
     */
    suspend fun search(site: VideoSourceSite, keyword: String): VideoSourceApiResponse? {
        return try {
            val response = httpClient.get(site.api) {
                parameter("ac", "detail")
                parameter("wd", keyword)
            }
            val body = response.bodyAsText()
            json.decodeFromString<VideoSourceApiResponse>(body)
        } catch (e: Exception) {
            // 单个站点失败不影响其他站点
            println("[VideoSourceApiClient] ${site.name} search failed: ${e.message}")
            null
        }
    }


}
