package com.example.kmp_demo.features.domestic.data.remote

import com.example.kmp_demo.core.videosource.VideoSourceSite
import com.example.kmp_demo.core.videosource.VideoSourceSiteConfigProvider
import com.example.kmp_demo.core.videosource.VideoSourceSiteLoader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 国内影视 API 客户端。
 *
 * 基于资源站 API (ffzyapi 等) 的两步数据拉取策略：
 * 1. 调用 ac=list 获取最近更新的影片 ID 列表（此时无封面）
 * 2. 将 IDs 聚合后调用 ac=detail&ids=... 批量获取全量数据（含封面图、播放源）
 *
 * 支持多站点并行查询、去重合并，以及按 type_name 分类筛选。
 * baseUrl 从 db.json 中的活跃站点配置动态获取。
 */
class DomesticApi(
    private val httpClient: HttpClient,
    private val siteLoader: VideoSourceSiteLoader,
    private val configProvider: VideoSourceSiteConfigProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * 限定使用的站点 key 列表（仅这 5 个站点，避免遍历全部 33 个）。
     */
    companion object {
        private val TARGET_SITE_KEYS = setOf("ffzy", "bfzy", "lzi", "dbzy", "dyttzy")
    }
    /**
     * 发现所有活跃站点中可用的分类（type_name → 各站点的 type_id 映射）。
     *
     * 对每个活跃站点调用 ac=list（不带 t 参数），收集返回的 type_id/type_name 对，
     * 按 type_name 去重合并，返回所有可用的分类名称列表。
     */
    suspend fun discoverTypes(): List<String> {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return emptyList()

        val allTypeNames = mutableSetOf<String>()
        for (site in sites) {
            try {
                val body = httpClient.get(site.api) {
                    parameter("ac", "list")
                    parameter("pg", 1)
                    parameter("h", 24)
                    parameter("out", "json")
                }.bodyAsText()
                val response = json.decodeFromString<DomesticListResponse>(body)
                response.list?.forEach { item ->
                    item.typeName?.let { allTypeNames.add(it) }
                }
            } catch (_: Exception) {
                // 单个站点失败不影响其他站点
            }
        }
        return allTypeNames.toList().sorted()
    }

    /**
     * 获取最近更新的影视列表（含封面图），支持按分类筛选。
     *
     * 遍历所有活跃站点，对每个站点：
     * 1. 调用 ac=list（带 t 参数筛选分类）获取 ID 列表
     * 2. 调用 ac=detail&ids=... 批量获取详情（含封面）
     * 最后按标题去重合并。
     *
     * @param page 页码
     * @param hours 最近多少小时内的更新
     * @param typeName 可选分类名称（如"国产剧"、"综艺"），null 表示全部
     * @return 去重合并后的影片详情列表
     */
    suspend fun getRecentMedia(
        page: Int = 1,
        hours: Int = 24,
        typeName: String? = null,
    ): List<DomesticApiItem> {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return emptyList()

        val allItems = mutableListOf<DomesticApiItem>()
        for (site in sites) {
            try {
                val items = fetchSiteRecentMedia(site, page, hours, typeName)
                allItems.addAll(items)
            } catch (_: Exception) {
                // 单个站点失败不影响其他站点
            }
        }
        return deduplicate(allItems)
    }

    /**
     * 从单个站点获取最近更新的影视列表。
     */
    private suspend fun fetchSiteRecentMedia(
        site: VideoSourceSite,
        page: Int,
        hours: Int,
        typeName: String?,
    ): List<DomesticApiItem> {
        val baseUrl = site.api

        // 步骤 1：获取最近更新的简要列表（无封面）
        val listBody = httpClient.get(baseUrl) {
            parameter("ac", "list")
            parameter("pg", page)
            parameter("h", hours)
            parameter("out", "json")
            // 如果指定了分类，先发现该站点对应的 type_id
            if (typeName != null) {
                val typeId = resolveTypeIdForSite(site, typeName)
                if (typeId != null) {
                    parameter("t", typeId)
                }
            }
        }.bodyAsText()
        val listResponse = json.decodeFromString<DomesticListResponse>(listBody)

        val rawList = listResponse.list ?: return emptyList()
        if (rawList.isEmpty()) return emptyList()

        // 步骤 2：将 IDs 聚合后批量获取详情（含封面）
        val batchIds = rawList.map { it.vodId }.joinToString(separator = ",")
        val detailBody = httpClient.get(baseUrl) {
            parameter("ac", "detail")
            parameter("ids", batchIds)
            parameter("out", "json")
        }.bodyAsText()
        val detailResponse = json.decodeFromString<DomesticDetailResponse>(detailBody)

        return detailResponse.list ?: emptyList()
    }

    /**
     * 解析某个站点上指定分类名称对应的 type_id。
     * 通过调用 ac=list（不带 t）来发现该站点的 type_id/type_name 映射。
     */
    private suspend fun resolveTypeIdForSite(site: VideoSourceSite, typeName: String): Int? {
        return try {
            val body = httpClient.get(site.api) {
                parameter("ac", "list")
                parameter("pg", 1)
                parameter("h", 24)
                parameter("out", "json")
            }.bodyAsText()
            val response = json.decodeFromString<DomesticListResponse>(body)
            response.list
                ?.firstOrNull { it.typeName == typeName }
                ?.typeId
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 搜索影视内容。
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @return 搜索结果列表
     */
    suspend fun search(keyword: String, page: Int = 1): List<DomesticApiItem> {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return emptyList()

        val allItems = mutableListOf<DomesticApiItem>()
        for (site in sites) {
            try {
                val body = httpClient.get(site.api) {
                    parameter("ac", "detail")
                    parameter("wd", keyword)
                    parameter("pg", page)
                    parameter("out", "json")
                }.bodyAsText()
                val response = json.decodeFromString<DomesticDetailResponse>(body)
                response.list?.let { allItems.addAll(it) }
            } catch (_: Exception) {
                // 单个站点失败不影响其他站点
            }
        }
        return deduplicate(allItems)
    }

    /**
     * 搜索结果，包含匹配的条目和来源站点的 base URL。
     *
     * 用于在 [searchFirstMatch] 中同时返回匹配的站点信息，
     * 以便调用方拼接相对路径的封面图 URL。
     */
    data class SearchMatchResult(
        val item: DomesticApiItem,
        val siteBaseUrl: String,
    )

    /**
     * 搜索影视内容，找到第一个标题匹配的条目即返回。
     *
     * 与 [search] 不同，此方法在遍历站点时一旦找到名称匹配的条目就立即返回，
     * 避免遍历所有站点，从而加快元数据加载速度。
     * 用于详情页分阶段加载中的 Phase 1（快速展示基本信息）。
     *
     * @param keyword 搜索关键词
     * @return 第一个标题匹配的条目及其来源站点 base URL，未找到则返回 null
     */
    suspend fun searchFirstMatch(keyword: String): SearchMatchResult? {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return null

        for (site in sites) {
            try {
                val body = httpClient.get(site.api) {
                    parameter("ac", "detail")
                    parameter("wd", keyword)
                    parameter("pg", 1)
                    parameter("out", "json")
                }.bodyAsText()
                val response = json.decodeFromString<DomesticDetailResponse>(body)
                val match = response.list?.firstOrNull { item ->
                    item.name.contains(keyword, ignoreCase = true) ||
                        keyword.contains(item.name, ignoreCase = true)
                }
                if (match != null) {
                    // 从 site.api 提取 base URL（去掉路径部分）
                    val baseUrl = extractBaseUrl(site.api)
                    return SearchMatchResult(item = match, siteBaseUrl = baseUrl)
                }
            } catch (_: Exception) {
                // 单个站点失败不影响其他站点
            }
        }
        return null
    }

    /**
     * 从 API URL 中提取 base URL。
     *
     * 例如：https://example.com/api.php → https://example.com
     */
    private fun extractBaseUrl(apiUrl: String): String {
        return try {
            val uri = io.ktor.http.Url(apiUrl)
            "${uri.protocol.name}://${uri.host}"
        } catch (e: Exception) {
            apiUrl
        }
    }

    /**
     * 按标题去重合并（保留第一个出现的条目）。
     */
    private fun deduplicate(items: List<DomesticApiItem>): List<DomesticApiItem> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<DomesticApiItem>()
        for (item in items) {
            if (item.name.isNotBlank() && seen.add(item.name)) {
                result.add(item)
            }
        }
        return result
    }

    private suspend fun loadActiveSites(): List<VideoSourceSite> {
        return try {
            val sitesJson = configProvider.getSitesJson()
            val allActive = siteLoader.loadActiveSites(sitesJson)
            // 仅筛选出目标站点
            allActive.filter { it.key in TARGET_SITE_KEYS }
        } catch (e: Exception) {
            println("[DomesticApi] loadActiveSites failed: ${e.message}")
            emptyList()
        }
    }
}

// ==================== DTO ====================

/**
 * 资源站 ac=list 响应。
 */
@Serializable
data class DomesticListResponse(
    val list: List<DomesticListItem>? = null,
)

/**
 * 资源站 ac=list 返回的简要条目。
 * 现在包含 type_id 和 type_name，用于分类发现。
 */
@Serializable
data class DomesticListItem(
    @SerialName("vod_id") val vodId: Int,
    @SerialName("type_id") val typeId: Int? = null,
    @SerialName("type_name") val typeName: String? = null,
)

/**
 * 资源站 ac=detail 响应（含全量数据）。
 */
@Serializable
data class DomesticDetailResponse(
    val list: List<DomesticApiItem>? = null,
)

/**
 * 资源站返回的完整影片条目。
 */
@Serializable
data class DomesticApiItem(
    @SerialName("vod_id") val id: Int = 0,
    @SerialName("vod_name") val name: String = "",
    @SerialName("vod_pic") val posterUrl: String? = null,
    @SerialName("vod_remarks") val remarks: String? = null,
    @SerialName("vod_year") val year: String? = null,
    @SerialName("type_name") val typeName: String? = null,
    @SerialName("vod_content") val content: String? = null,
    @SerialName("vod_play_from") val playFrom: String? = null,
    @SerialName("vod_play_url") val playUrl: String? = null,
)
