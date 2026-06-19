package com.example.kmp_demo.features.domestic.data.remote

import com.example.kmp_demo.core.network.commonHeaders
import com.example.kmp_demo.core.network.userAgent
import com.example.kmp_demo.core.videosource.VideoSourceSite
import com.example.kmp_demo.core.videosource.VideoSourceSiteConfigProvider
import com.example.kmp_demo.core.videosource.VideoSourceSiteLoader
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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
     * 站点分类映射缓存。
     * Key: Site Key (如 "ffzy")
     * Value: Map<分类名称, 分类ID> (如 {"国产剧": 13})
     */
    private val siteCategoryCache = mutableMapOf<String, Map<String, Int>>()

    private val cacheMutex = Mutex()

    /**
     * 限定使用的站点 key 列表（仅这几个站点，避免遍历全部 33 个）。
     */
    companion object {
        private val TARGET_SITE_KEYS =
            setOf("ffzy", "bfzy", "lzi", "dbzy", "dyttzy", "zy360", "aqyzy", "wujin", "maotaizy")
    }

    /**
     * 提取通用的 HTTP 配置。
     */
    private fun HttpRequestBuilder.applyCommonConfig() {
        commonHeaders()
        userAgent()
        parameter("out", "json")
    }

    /**
     * 发现所有活跃站点中可用的分类。
     *
     * 优化策略：不再等待所有站点返回。
     * 只要有一个站点成功返回分类列表（class 字段），立即返回该结果。
     */
    suspend fun discoverTypes(): List<String> {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return emptyList()

        // 遍历站点，取第一个成功的站点结果
        for (site in sites) {
            try {
                val body = httpClient.get(site.api) {
                    parameter("ac", "list")
                    applyCommonConfig()
                }.bodyAsText()

                val response = json.decodeFromString<DomesticListResponse>(body)
                val categoryNames = response.categories?.map { it.name } ?: emptyList()

                if (categoryNames.isNotEmpty()) {
                    return categoryNames.sorted()
                }
            } catch (e: Exception) {
                // 当前站点失败，继续尝试下一个，不中断流程
                println("[DomesticApi] discoverTypes: Skip failed site ${site.name}: ${e.message}")
            }
        }

        return emptyList()
    }

    /**
     * 获取最近更新的影视列表（含封面图），支持按分类筛选。
     *
     * 并行请求所有活跃站点，采用 SupervisorScope 隔离单个站点故障。
     */
    suspend fun getRecentMedia(
        page: Int = 1,
        hours: Int = 24,
        typeName: String? = null,
    ): List<DomesticApiItem> = supervisorScope {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return@supervisorScope emptyList()

        val deferredResults = sites.map { site ->
            async {
                fetchSiteRecentMedia(site, page, hours, typeName)
            }
        }

        // 使用 runCatching 收集所有结果，避免单个站点崩溃导致整体取消
        val results = deferredResults.map { runCatching { it.await() } }

        val successes = results.mapNotNull { it.getOrNull() }

        // 如果全部请求都失败了，才抛出第一个错误
        if (successes.isEmpty() && results.any { it.isFailure }) {
            throw results.first { it.isFailure }.exceptionOrNull()!!
        }

        deduplicate(successes.flatten())
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
            applyCommonConfig()
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
            applyCommonConfig()
        }.bodyAsText()
        val detailResponse = json.decodeFromString<DomesticDetailResponse>(detailBody)

        return detailResponse.list ?: emptyList()
    }

    /**
     * 解析某个站点上指定分类名称对应的 type_id。
     *
     * 采用了动态映射 + 内存缓存策略。
     */
    private suspend fun resolveTypeIdForSite(site: VideoSourceSite, typeName: String): Int? {
        // 使用锁保护内存缓存的读写
        val cachedMap = cacheMutex.withLock { siteCategoryCache[site.key] }
        if (cachedMap != null) {
            return cachedMap[typeName]
        }

        return try {
            val body = httpClient.get(site.api) {
                parameter("ac", "list")
                applyCommonConfig()
            }.bodyAsText()
            val response = json.decodeFromString<DomesticListResponse>(body)

            val categories = response.categories ?: emptyList()
            if (categories.isNotEmpty()) {
                val newMap = categories.associate { it.name to it.id }
                cacheMutex.withLock {
                    siteCategoryCache[site.key] = newMap
                }
                newMap[typeName]
            } else {
                null
            }
        } catch (e: Exception) {
            println("[DomesticApi] resolveTypeIdForSite failed for ${site.name}: ${e.message}")
            null
        }
    }

    /**
     * 搜索影视内容。
     * 
     * 采用两步策略以确保兼容所有资源站：
     * 1. ac=list&wd=... 获取列表
     * 2. ac=detail&ids=... 获取详情
     */
    suspend fun search(keyword: String, page: Int = 1): List<DomesticApiItem> = supervisorScope {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return@supervisorScope emptyList()

        val deferredResults = sites.map { site ->
            async {
                fetchSiteSearchMedia(site, keyword, page)
            }
        }

        val results = deferredResults.map { runCatching { it.await() } }
        val successes = results.mapNotNull { it.getOrNull() }

        // 只有当所有请求都失败（Result.failure）且没有任何成功返回时才抛错
        if (successes.isEmpty() && results.any { it.isFailure }) {
            throw results.first { it.isFailure }.exceptionOrNull()!!
        }

        deduplicate(successes.flatten())
    }

    /**
     * 从单个站点执行两步搜索。
     */
    private suspend fun fetchSiteSearchMedia(
        site: VideoSourceSite,
        keyword: String,
        page: Int
    ): List<DomesticApiItem> {
        // 1. 搜索列表获取 IDs
        val listBody = httpClient.get(site.api) {
            parameter("ac", "list")
            parameter("wd", keyword)
            parameter("pg", page)
            applyCommonConfig()
        }.bodyAsText()
        val listResponse = json.decodeFromString<DomesticListResponse>(listBody)
        val ids = listResponse.list?.map { it.vodId }?.joinToString(",") ?: return emptyList()
        if (ids.isBlank()) return emptyList()

        // 2. 获取详情
        val detailBody = httpClient.get(site.api) {
            parameter("ac", "detail")
            parameter("ids", ids)
            applyCommonConfig()
        }.bodyAsText()
        val detailResponse = json.decodeFromString<DomesticDetailResponse>(detailBody)
        return detailResponse.list ?: emptyList()
    }

    /**
     * 搜索影视内容，找到第一个标题匹配的条目即返回。
     */
    suspend fun searchFirstMatch(keyword: String): SearchMatchResult? = supervisorScope {
        val sites = loadActiveSites()
        if (sites.isEmpty()) return@supervisorScope null

        val deferredResults = sites.map { site ->
            async {
                // 搜索第一页即可
                val items = fetchSiteSearchMedia(site, keyword, 1)
                val match = items.firstOrNull { item ->
                    item.name.equals(keyword, ignoreCase = true) ||
                            item.name.contains(keyword, ignoreCase = true)
                }
                match?.let {
                    SearchMatchResult(item = it, siteBaseUrl = extractBaseUrl(site.api))
                }
            }
        }

        val results = deferredResults.map { runCatching { it.await() } }

        // 只要有任何一站返回了非空的 SearchMatchResult，就优先返回它
        val firstMatch = results.firstNotNullOfOrNull { it.getOrNull() }
        if (firstMatch != null) return@supervisorScope firstMatch

        // 如果全部站点都执行失败（抛出异常），则抛出第一个错误
        if (results.all { it.isFailure }) {
            throw results.first().exceptionOrNull()!!
        }

        null
    }

    /**
     * 搜索结果，包含匹配的条目和来源站点的 base URL。
     */
    data class SearchMatchResult(
        val item: DomesticApiItem,
        val siteBaseUrl: String,
    )

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

// ==================== Serializers = : ====================

/**
 * 处理 API 返回值中 Int 和 String 混用的情况（兼容部分资源站 ID 为字符串的问题）。
 */
@OptIn(ExperimentalSerializationApi::class)
object AnyToIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AnyToInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = input.decodeJsonElement()
        return if (element is JsonPrimitive) {
            element.intOrNull ?: element.content.toIntOrNull() ?: 0
        } else {
            0
        }
    }
}

/**
 * 处理 API 返回值中 Int? 和 String? 混用的情况。
 */
@OptIn(ExperimentalSerializationApi::class)
object AnyToNullableIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AnyToNullableInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int?) =
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int? {
        val input = decoder as? JsonDecoder ?: return null
        val element = input.decodeJsonElement()
        if (element is JsonNull) return null
        return if (element is JsonPrimitive) {
            element.intOrNull ?: element.content.toIntOrNull()
        } else {
            null
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
    @SerialName("class") val categories: List<DomesticCategory>? = null,
)

/**
 * 资源站返回的完整分类条目。
 */
@Serializable
data class DomesticCategory(
    @SerialName("type_id") @Serializable(with = AnyToIntSerializer::class) val id: Int,
    @SerialName("type_name") val name: String,
)

/**
 * 资源站 ac=list 返回的简要条目。
 * 现在包含 type_id 和 type_name，用于分类发现。
 */
@Serializable
data class DomesticListItem(
    @SerialName("vod_id") @Serializable(with = AnyToIntSerializer::class) val vodId: Int,
    @SerialName("type_id") @Serializable(with = AnyToNullableIntSerializer::class) val typeId: Int? = null,
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
    @SerialName("vod_id") @Serializable(with = AnyToIntSerializer::class) val id: Int = 0,
    @SerialName("vod_name") val name: String = "",
    @SerialName("vod_pic") val posterUrl: String? = null,
    @SerialName("vod_remarks") val remarks: String? = null,
    @SerialName("vod_year") val year: String? = null,
    @SerialName("type_name") val typeName: String? = null,
    @SerialName("vod_content") val content: String? = null,
    @SerialName("vod_play_from") val playFrom: String? = null,
    @SerialName("vod_play_url") val playUrl: String? = null,
)
