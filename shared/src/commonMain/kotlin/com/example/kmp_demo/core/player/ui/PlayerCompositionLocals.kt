package com.example.kmp_demo.core.player.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * 当前设备是否为竖屏模式。
 *
 * 用于控制播放器控制栏中某些元素的显隐：
 * - 竖屏模式：隐藏音量控制和选集按钮（移动端空间紧凑）
 * - 横屏模式/桌面端：显示所有控制元素
 *
 * 由各平台在顶层设置：
 * - Android：根据 [android.content.res.Configuration.orientation] 设置
 * - Desktop：始终为 false（桌面端始终展示所有控制）
 */
val LocalIsPortrait = compositionLocalOf { false }
