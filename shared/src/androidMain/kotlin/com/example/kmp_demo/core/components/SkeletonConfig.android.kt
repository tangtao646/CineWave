package com.example.kmp_demo.core.components

/**
 * Android 端使用竖屏比例，适配移动端瀑布流。
 */
actual fun skeletonAspectRatio(): Float = 0.67f

/**
 * Android 端屏幕较小，显示 6 个骨架项。
 */
actual fun skeletonCount(): Int = 6
