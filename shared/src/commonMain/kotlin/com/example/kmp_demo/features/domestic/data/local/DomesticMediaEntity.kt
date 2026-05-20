package com.example.kmp_demo.features.domestic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 国内影视媒体 Room 实体。
 *
 * 用于首页瀑布流的 Room 缓存 + Paging 分页。
 * [type] 字段用于按分类筛选（如"国产剧"、"综艺"），对应 [DomesticMediaType]。
 * [orderIndex] 用于控制列表排序顺序。
 */
@Entity(tableName = "domestic_media")
data class DomesticMediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverUrl: String?,
    val year: String?,
    val area: String?,
    val type: String, // DomesticMediaType name: DRAMA/MOVIE/ANIME/VARIETY
    val description: String?,
    val remarks: String?,
    val typeName: String, // 分类筛选 key，如"国产剧"、"综艺"、"全部"
    val orderIndex: Long = System.currentTimeMillis(),
)
