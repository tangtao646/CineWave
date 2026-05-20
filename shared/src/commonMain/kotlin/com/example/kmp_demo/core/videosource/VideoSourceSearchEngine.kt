package com.example.kmp_demo.core.videosource

import com.example.kmp_demo.core.videosource.domain.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 视频源搜索引擎。
 *
 * 协调多站点并行搜索，对结果去重后返回。
 * 职责单一：仅负责搜索编排与结果聚合。
 *
 * 高内聚：所有多站点搜索逻辑集中于此。
 * 低耦合：依赖 [VideoSourceSiteLoader]、[VideoSourceApiClient]、[VideoSourceSiteConfigProvider] 的抽象。
 */
class VideoSourceSearchEngine(
    private val siteLoader: VideoSourceSiteLoader,
    private val apiClient: VideoSourceApiClient,
    private val configProvider: VideoSourceSiteConfigProvider,
) {
    /**
     * 在所有活跃站点中并行搜索指定关键词。
     *
     * @param keyword 搜索关键词（影片标题）
     * @return 去重后的播放源列表
     */
    suspend fun search(keyword: String): List<VideoSource> =
        withContext(Dispatchers.Default) {
            val sitesJson = configProvider.getSitesJson()
            val activeSites = siteLoader.loadActiveSites(sitesJson)
            if (activeSites.isEmpty()) return@withContext emptyList()

            // 并行搜索所有站点
            val deferredResults = activeSites.map { site ->
                async {
                    searchSite(site, keyword)
                }
            }

            // 聚合结果并去重（两轮去重）
            val allResults = deferredResults.awaitAll().flatten()
            deduplicate(allResults)
        }

    /**
     * 在单个站点搜索。
     */
    private suspend fun searchSite(site: VideoSourceSite, keyword: String): List<VideoSource> {
        val response = apiClient.search(site, keyword) ?: return emptyList()
        return VideoSourceParser.parse(response, site.name)
    }

    /**
     * 两轮去重：
     * 1. 先以 URL 为唯一标识进行第一轮筛选（去除完全相同的链接）
     * 2. 再以归一化的剧集名称为标识进行第二轮筛选（同名剧集保留先出现的）
     *
     * 这样既能去除同一站点/跨站点的重复链接，
     * 又能解决不同站点提供不同链接但指向同一集的问题（如"第1集"重复出现）。
     */
    private fun deduplicate(sources: List<VideoSource>): List<VideoSource> {
        // 第一轮：按 URL 去重
        val urlDeduplicated = deduplicateBy(sources) { it.url }

        // 第二轮：按归一化的剧集名称去重（保留先出现的）
        return deduplicateBy(urlDeduplicated) { normalizeEpisodeName(it.quality) }
    }

    /**
     * 按指定 key 提取函数去重，保留最先出现的元素。
     */
    private fun deduplicateBy(
        sources: List<VideoSource>,
        keySelector: (VideoSource) -> String,
    ): List<VideoSource> {
        val seen = mutableSetOf<String>()
        return sources.filter { source ->
            seen.add(keySelector(source))
        }
    }

    /**
     * 归一化剧集名称，用于名称比对去重。
     *
     * 不同站点对同一集的命名可能不同，例如：
     * - "第01集" → "第1集"
     * - "第1集"  → "第1集"
     * - "01"     → "1"
     * - "第1集:觉醒" → "第1集"
     *
     * 归一化策略：
     * 1. 提取 "第X集" 中的数字 X，统一为 "第X集" 格式
     * 2. 如果无法匹配剧集模式，则保留原始值（不进行合并）
     */
    private fun normalizeEpisodeName(name: String): String {
        // 尝试匹配 "第X集" 模式，提取集数数字
        val episodePattern = Regex("第(\\d+)集")
        val match = episodePattern.find(name)
        if (match != null) {
            val episodeNumber = match.groupValues[1].toIntOrNull()
            if (episodeNumber != null) {
                return "第${episodeNumber}集"
            }
        }
        // 尝试匹配纯数字（如 "01"、"1"）
        val digitsOnly = name.trim().toIntOrNull()
        if (digitsOnly != null) {
            return "第${digitsOnly}集"
        }
        // 无法归一化，保留原始值
        return name.trim()
    }
}
