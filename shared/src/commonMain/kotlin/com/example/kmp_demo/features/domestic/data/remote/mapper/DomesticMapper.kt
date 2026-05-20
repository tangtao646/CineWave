package com.example.kmp_demo.features.domestic.data.remote.mapper

import com.example.kmp_demo.features.domestic.data.local.DomesticMediaEntity
import com.example.kmp_demo.features.domestic.data.remote.DomesticApiItem
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.model.DomesticMediaType

/**
 * 国内影视数据层映射器。
 *
 * 负责 DTO / Entity / Domain 三层之间的双向转换，
 * 保持 Repository 实现简洁。
 */

// ──────────────────────────────────────────────
// DomesticApiItem → DomesticMedia (API DTO → Domain)
// ──────────────────────────────────────────────

fun DomesticApiItem.toDomesticMedia(): DomesticMedia = DomesticMedia(
    id = id.toString(),
    title = name,
    coverUrl = posterUrl,
    year = year,
    area = null,
    type = inferDomesticMediaType(typeName),
    description = content,
    remarks = remarks,
    videoSources = emptyList(), // 播放源在详情页单独获取
)

// ──────────────────────────────────────────────
// DomesticApiItem → DomesticMediaEntity (API DTO → Room Entity)
// ──────────────────────────────────────────────

fun DomesticApiItem.toEntity(
    typeName: String,
    orderIndex: Long,
): DomesticMediaEntity = DomesticMediaEntity(
    id = id.toString(),
    title = name,
    coverUrl = posterUrl,
    year = year,
    area = null,
    type = inferEntityType(typeName),
    description = content,
    remarks = remarks,
    typeName = typeName,
    orderIndex = orderIndex,
)

// ──────────────────────────────────────────────
// DomesticMediaEntity → DomesticMedia (Room Entity → Domain)
// ──────────────────────────────────────────────

fun DomesticMediaEntity.toDomesticMedia(): DomesticMedia = DomesticMedia(
    id = id,
    title = title,
    coverUrl = coverUrl,
    year = year,
    area = area,
    type = DomesticMediaType.valueOf(type),
    description = description,
    remarks = remarks,
    videoSources = emptyList(),
)

// ──────────────────────────────────────────────
// Type inference helpers
// ──────────────────────────────────────────────

/**
 * 根据站点返回的 typeName 推断 [DomesticMediaType] 枚举值（用于 Domain 层）。
 */
fun inferDomesticMediaType(typeName: String?): DomesticMediaType {
    return when {
        typeName == null -> DomesticMediaType.DRAMA
        typeName.contains("动漫") || typeName.contains("动画") -> DomesticMediaType.ANIME
        typeName.contains("综艺") -> DomesticMediaType.VARIETY
        typeName.contains("电影") || typeName.contains("片") -> DomesticMediaType.MOVIE
        typeName.contains("剧") -> DomesticMediaType.DRAMA
        else -> DomesticMediaType.DRAMA
    }
}

/**
 * 根据站点返回的 typeName 推断 Entity 层 type 字符串（用于 Room 存储）。
 */
fun inferEntityType(typeName: String?): String {
    return inferDomesticMediaType(typeName).name
}
