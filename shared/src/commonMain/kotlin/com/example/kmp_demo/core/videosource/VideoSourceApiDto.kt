package com.example.kmp_demo.core.videosource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 资源站点 API 的通用响应 DTO。
 *
 * 资源站 API (如 ffzy, bfzy 等) 遵循统一格式：
 * ```json
 * {
 *   "code": 1,
 *   "list": [ { "vod_id": 160999, "vod_name": "...", "vod_play_url": "...", ... } ]
 * }
 * ```
 */
@Serializable
data class VideoSourceApiResponse(
    val code: Int = 0,
    val list: List<VideoSourceApiItem>? = null,
)

/**
 * 资源站点 API 返回的单个影片条目。
 *
 * vod_id 在 API 响应中为数字类型（如 160999），与 API 保持一致。
 */
@Serializable
data class VideoSourceApiItem(
    @SerialName("vod_id") val vodId: Int = 0,
    @SerialName("vod_name") val vodName: String = "",
    @SerialName("vod_pic") val vodPic: String? = null,
    @SerialName("vod_play_url") val vodPlayUrl: String? = null,
    @SerialName("vod_play_from") val vodPlayFrom: String? = null,
    @SerialName("vod_remarks") val vodRemarks: String? = null,
    @SerialName("vod_year") val vodYear: String? = null,
    @SerialName("type_name") val typeName: String? = null,
    @SerialName("vod_content") val vodContent: String? = null,
)
