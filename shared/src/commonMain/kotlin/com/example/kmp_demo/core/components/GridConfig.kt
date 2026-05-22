package com.example.kmp_demo.core.components

/**
 * 平台相关的网格列数配置。
 *
 * Desktop 端屏幕较大，使用 5 列网格；
 * Android 端屏幕较小，使用 2 列网格。
 *
 * 这样首页列表页（FilmHomeScreen、DomesticHomeScreen 等）
 * 可以完全复用 commonMain 的 UI 代码，只需将
 * `GridCells.Fixed(2)` 改为 `GridCells.Fixed(gridColumns())`。
 */
expect fun gridColumns(): Int
