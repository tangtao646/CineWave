package com.example.kmp_demo.features.film.domain.model

/**
 * 视频协议类型定义
 */
enum class VideoProtocol {
    HTTP,   // 包括 HLS, Dash, MP4
    FTP,
    MAGNET,
    UNKNOWN;

    companion object {
        fun fromUrl(url: String): VideoProtocol {
            val lower = url.lowercase()
            return when {
                lower.startsWith("magnet:") -> MAGNET
                lower.startsWith("ftp://") -> FTP
                lower.startsWith("http") -> HTTP
                else -> UNKNOWN
            }
        }
    }
}
