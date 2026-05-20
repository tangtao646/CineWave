package com.example.kmp_demo.features.domestic.data.remote

import com.example.kmp_demo.core.videosource.VideoSourceSearchEngine
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.model.DomesticMediaType

/**
 * 国内影视搜索源包装器。
 *
 * 包装 [VideoSourceSearchEngine]，将搜索结果映射为 [DomesticMedia] 列表。
 * 按标题去重聚合：同一标题下的多个播放源合并到一个 DomesticMedia。
 */
class DomesticSearchEngine(
    private val searchEngine: VideoSourceSearchEngine,
) {
    /** 搜索并映射为 DomesticMedia 列表 */
    suspend fun search(keyword: String): List<DomesticMedia> {
        val sources = searchEngine.search(keyword)
        return aggregateToMedia(sources)
    }

    private fun aggregateToMedia(sources: List<VideoSource>): List<DomesticMedia> {
        // 按标题去重聚合：同一标题下的多个播放源合并到一个 DomesticMedia
        return sources.groupBy { extractTitleKey(it) }
            .map { (_, group) ->
                val first = group.first()
                DomesticMedia(
                    id = generateId(first),
                    title = extractDisplayTitle(first),
                    coverUrl = null, // 搜索源通常不返回封面，可后续扩展
                    year = null,
                    area = null,
                    type = inferType(first),
                    description = null,
                    remarks = null,
                    videoSources = group,
                )
            }
    }

    /**
     * 从 VideoSource 的 quality 字段提取标题 key。
     * quality 字段通常格式为 "第01集" 或 "庆余年 第01集"。
     */
    private fun extractTitleKey(source: VideoSource): String {
        // 尝试从 quality 中提取剧集名（去掉 "第X集" 部分）
        val quality = source.quality
        val episodePattern = Regex("第\\d+集")
        return if (episodePattern.containsMatchIn(quality)) {
            quality.replace(episodePattern, "").trim()
        } else {
            quality
        }
    }

    /**
     * 生成唯一 ID。
     */
    private fun generateId(source: VideoSource): String {
        val key = extractTitleKey(source)
        return key.hashCode().toUInt().toString(16)
    }

    /**
     * 提取显示标题。
     */
    private fun extractDisplayTitle(source: VideoSource): String {
        val key = extractTitleKey(source)
        return key.ifBlank { source.quality }
    }

    /**
     * 推断媒体类型。
     * 基于站点名称和剧集名进行简单推断。
     */
    private fun inferType(source: VideoSource): DomesticMediaType {
        val lowerName = source.sourceSite.lowercase()
        val lowerQuality = source.quality.lowercase()

        return when {
            lowerQuality.contains("动漫") || lowerName.contains("anime") -> DomesticMediaType.ANIME
            lowerQuality.contains("综艺") || lowerName.contains("variety") -> DomesticMediaType.VARIETY
            lowerQuality.contains("电影") || lowerName.contains("movie") -> DomesticMediaType.MOVIE
            lowerQuality.contains("剧") || lowerQuality.contains("第") -> DomesticMediaType.DRAMA
            else -> DomesticMediaType.DRAMA // 默认剧集
        }
    }
}
