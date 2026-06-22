package com.example.kmp_demo.core

import io.ktor.client.plugins.ResponseException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.serialization.JsonConvertException
import io.ktor.client.call.NoTransformationFoundException

/**
 * 统一页面状态模型
 */
sealed class PageStatus {
    data object Loading : PageStatus()
    data object Empty : PageStatus()
    data class Error(val message: String? = null) : PageStatus()
    data object Content : PageStatus()
}

/**
 * 将异常转换为用户友好的错误消息
 */
fun Throwable.toUserFriendlyMessage(): String {
    // 优先识别特定的异常类型
    return when (this) {
        is ResponseException -> {
            when (this.response.status.value) {
                503 -> "服务器太忙了 (503)，请稍后再试"
                500, 502, 504 -> "服务器出错了，技术人员正在抢修"
                404 -> "请求的内容找不到了"
                else -> "服务器响应异常 (${this.response.status.value})"
            }
        }
        is SocketTimeoutException -> "网络连接超时，请检查网络后再试"
        is JsonConvertException,
        is NoTransformationFoundException -> "数据解析失败，可能是服务器返回了错误页面"
        else -> {
            val msg = message ?: ""
            when {
                msg.contains("503") -> "服务暂时不可用，请稍后再试"
                msg.contains("NoTransformationFoundException") -> "服务器返回数据格式错误 (可能是 503)"
                else -> "网络似乎开小差了，请检查网络连接"
            }
        }
    }
}
