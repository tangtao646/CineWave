package com.example.kmp_demo.di

import org.koin.core.module.Module

/**
 * 平台特定的 Koin 模块列表。
 * Android 提供 Room 数据库和 Media3 播放器等实现，
 * iOS 提供对应的平台实现。
 */
expect val platformModule: Module
