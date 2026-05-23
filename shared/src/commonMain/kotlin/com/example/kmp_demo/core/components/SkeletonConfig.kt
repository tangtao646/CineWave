package com.example.kmp_demo.core.components

/**
 * 平台相关的骨架屏封面图宽高比。
 *
 * Desktop 端使用正方形 (1f)，适配网格布局；
 * Android 端使用竖屏比例 (0.67f)，适配移动端瀑布流。
 */
expect fun skeletonAspectRatio(): Float

/**
 * 平台相关的骨架屏加载数量。
 *
 * Desktop 端屏幕较大，显示 20 个骨架项；
 * Android 端屏幕较小，显示 6 个骨架项。
 */
expect fun skeletonCount(): Int
