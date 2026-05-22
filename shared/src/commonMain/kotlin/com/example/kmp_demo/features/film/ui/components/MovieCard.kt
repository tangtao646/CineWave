package com.example.kmp_demo.features.film.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kmp_demo.features.film.domain.model.Movie

/**
 * 平台相关的电影卡片组件。
 *
 * - Android：长条形（aspectRatio 0.67），适合手机竖屏滚动
 * - Desktop：正方形布局，标题单行+省略号，hover 显示完整标题
 */
@Composable
expect fun MovieCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
)
