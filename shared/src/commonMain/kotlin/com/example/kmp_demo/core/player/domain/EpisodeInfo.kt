package com.example.kmp_demo.core.player.domain

import kotlinx.serialization.Serializable

/**
 * 剧集信息模型，用于播放页内沉浸式选集。
 *
 * 当详情页包含多个播放源（如电视剧的多集）时，将这些播放源转换为
 * [EpisodeInfo] 列表传递给播放器页，用户无需返回详情页即可切换剧集。
 *
 * @param index 剧集序号（从 0 开始），用于排序和标识
 * @param label 显示标签，如"第1集"、"第2集"、"第3集"
 * @param url   播放地址
 * @param title 可选的剧集副标题，如"第1集：觉醒"
 */
@Serializable
data class EpisodeInfo(
    val index: Int,
    val label: String,
    val url: String,
    val title: String? = null,
)
