package com.example.kmp_demo.core

import coil3.PlatformContext
import okio.Path

/**
 * 平台特定工具函数 - expect 声明
 * 用于替换 android.widget.Toast、android.content.Intent 等平台 API
 */

/**
 * 显示 Toast 消息
 */
expect fun showToast(message: String)

/**
 * 打开无障碍设置页面
 */
expect fun openAccessibilitySettings()


// 声明一个跨平台获取缓存根目录的函数
expect fun PlatformContext.getPlatformCachePath(): String