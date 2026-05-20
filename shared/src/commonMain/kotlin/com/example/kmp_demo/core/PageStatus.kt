package com.example.kmp_demo.core

/**
 * 统一页面状态模型
 */
sealed class PageStatus {
    data object Loading : PageStatus()
    data object Empty : PageStatus()
    data class Error(val message: String? = null) : PageStatus()
    data object Content : PageStatus()
}
