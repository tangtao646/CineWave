package com.example.kmp_demo.features.film.domain.model

/**
 * 影片播放源模型
 *
 * @deprecated 已迁移到 [com.example.kmp_demo.core.videosource.domain.VideoSource]，
 *             此文件保留 typealias 做向后兼容。
 */
@Deprecated(
    message = "Moved to core.videosource.domain.VideoSource",
    replaceWith = ReplaceWith(
        "com.example.kmp_demo.core.videosource.domain.VideoSource",
        "com.example.kmp_demo.core.videosource.domain.VideoSource"
    )
)
typealias VideoSource = com.example.kmp_demo.core.videosource.domain.VideoSource
