package com.example.kmp_demo.core.videosource.domain

/**
 * 影片播放源模型
 */
data class VideoSource(
    val url: String,          // 播放地址
    val quality: String,      // 分辨率/质量 (如 1080p, 720p)
    val format: String,       // 格式 (如 mp4, m3u8)
    val size: Long = 0L,      // 文件大小 (字节)
    val sourceSite: String,   // 来源站点名称
)
