package com.example.kmp_demo.core.player.domain

/**
 * 播放器错误类型枚举
 *
 * 定义所有可能的播放错误类型，支持多级分类：
 * - [SOURCE_NOT_FOUND]：资源不存在（HTTP 404）
 * - [FORBIDDEN]：权限不足（HTTP 403）
 * - [NETWORK_ERROR]：网络连接失败
 * - [TIMEOUT]：连接超时
 * - [FORMAT_ERROR]：媒体格式不支持或损坏
 * - [DRM_ERROR]：DRM 解密失败
 * - [UNKNOWN]：未知错误
 */
enum class PlayerErrorType {
    SOURCE_NOT_FOUND,
    FORBIDDEN,
    NETWORK_ERROR,
    TIMEOUT,
    FORMAT_ERROR,
    DRM_ERROR,
    UNKNOWN,
}

/**
 * 播放器错误信息模型
 *
 * 包含错误类型、用户可读的消息、技术详情和 HTTP 状态码。
 * 设计为不可变数据类，通过 StateFlow 在架构中传播。
 *
 * @param type 错误类型，用于 UI 层决定展示样式和图标
 * @param message 用户可读的错误消息（已本地化）
 * @param detail 技术详情（可选），用于调试日志
 * @param httpStatusCode HTTP 状态码（可选），如 404、403
 * @param retryable 是否可重试（网络错误可重试，格式错误不可重试）
 */
data class PlayerError(
    val type: PlayerErrorType,
    val message: String,
    val detail: String? = null,
    val httpStatusCode: Int? = null,
    val retryable: Boolean = true,
) {
    companion object {
        /**
         * 从 HTTP 状态码创建错误
         */
        fun fromHttpStatusCode(code: Int, detail: String? = null): PlayerError {
            return when (code) {
                404 -> PlayerError(
                    type = PlayerErrorType.SOURCE_NOT_FOUND,
                    message = "视频资源不存在",
                    detail = detail ?: "服务器返回 404 Not Found",
                    httpStatusCode = code,
                    retryable = false,
                )
                403 -> PlayerError(
                    type = PlayerErrorType.FORBIDDEN,
                    message = "视频资源访问受限",
                    detail = detail ?: "服务器返回 403 Forbidden",
                    httpStatusCode = code,
                    retryable = false,
                )
                410 -> PlayerError(
                    type = PlayerErrorType.SOURCE_NOT_FOUND,
                    message = "视频资源已失效",
                    detail = detail ?: "服务器返回 410 Gone",
                    httpStatusCode = code,
                    retryable = false,
                )
                in 500..599 -> PlayerError(
                    type = PlayerErrorType.NETWORK_ERROR,
                    message = "服务器暂时不可用",
                    detail = detail ?: "服务器返回 $code",
                    httpStatusCode = code,
                    retryable = true,
                )
                else -> PlayerError(
                    type = PlayerErrorType.UNKNOWN,
                    message = "播放出错",
                    detail = detail ?: "HTTP $code",
                    httpStatusCode = code,
                    retryable = true,
                )
            }
        }

        /**
         * 从异常创建错误
         */
        fun fromException(e: Exception, detail: String? = null): PlayerError {
            val message = e.message ?: e.javaClass.simpleName
            return when (e) {
                is java.io.FileNotFoundException -> PlayerError(
                    type = PlayerErrorType.SOURCE_NOT_FOUND,
                    message = "视频文件未找到",
                    detail = detail ?: message,
                    retryable = false,
                )
                is java.net.UnknownHostException,
                is java.net.ConnectException -> PlayerError(
                    type = PlayerErrorType.NETWORK_ERROR,
                    message = "网络连接失败，请检查网络",
                    detail = detail ?: message,
                    retryable = true,
                )
                is java.net.SocketTimeoutException -> PlayerError(
                    type = PlayerErrorType.TIMEOUT,
                    message = "连接超时，请稍后重试",
                    detail = detail ?: message,
                    retryable = true,
                )
                else -> PlayerError(
                    type = PlayerErrorType.UNKNOWN,
                    message = "播放出错",
                    detail = detail ?: message,
                    retryable = true,
                )
            }
        }

        /**
         * 通用未知错误
         */
        val unknown: PlayerError
            get() = PlayerError(
                type = PlayerErrorType.UNKNOWN,
                message = "播放出错",
                retryable = true,
            )
    }
}
