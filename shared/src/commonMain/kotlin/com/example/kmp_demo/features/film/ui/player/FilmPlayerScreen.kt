package com.example.kmp_demo.features.film.ui.player

import androidx.compose.runtime.Composable
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.player.ui.PlayerScreen

/**
 * 电影播放器屏幕。
 *
 * 委托给通用 [PlayerScreen] 组件。
 * 保留此文件以保持导航路由兼容性。
 */
@Composable
fun FilmPlayerScreen(
    initialUrl: String,
    seriesTitle: String,
    episodes: List<EpisodeInfo> = emptyList(),
    onBack: () -> Unit,
    onFullScreenChange: ((Boolean) -> Unit)? = null,
) {
    PlayerScreen(
        initialUrl = initialUrl,
        seriesTitle = seriesTitle,
        episodes = episodes,
        onBack = onBack,
        onFullScreenChange = onFullScreenChange,
    )
}
