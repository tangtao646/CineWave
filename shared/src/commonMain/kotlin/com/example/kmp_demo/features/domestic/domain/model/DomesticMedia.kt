package com.example.kmp_demo.features.domestic.domain.model

import com.example.kmp_demo.core.videosource.domain.VideoSource

/**
 * 国内影视媒体模型 — 与 TMDB Movie 解耦
 */
data class DomesticMedia(
    val id: String,              // 唯一标识（可由 API 的 vod_id 生成）
    val title: String,           // 标题
    val coverUrl: String?,       // 封面图 URL
    val year: String?,           // 年份
    val area: String?,           // 地区（大陆/台湾/香港）
    val type: DomesticMediaType, // 类型：剧集/电影/动漫/综艺
    val description: String?,    // 简介
    val remarks: String?,        // 备注（如"更新至30集"）
    val videoSources: List<VideoSource> = emptyList(), // 复用 core/videosource 的 VideoSource
)

enum class DomesticMediaType(val label: String) {
    DRAMA("剧集"),
    MOVIE("电影"),
    ANIME("动漫"),
    VARIETY("综艺"),
}
