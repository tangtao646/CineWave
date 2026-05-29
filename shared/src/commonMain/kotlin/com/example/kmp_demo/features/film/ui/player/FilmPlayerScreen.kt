package com.example.kmp_demo.features.film.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.player.ui.*
import org.koin.compose.viewmodel.koinViewModel

/**
 * 电影播放器屏幕 — 支持沉浸式选集与自动连播。
 *
 * 纯 UI 层，所有业务逻辑委托给 [PlayerViewModel]。
 *
 * 设计要点：
 * - 顶栏标题动态显示 `"影片名 · 第X集"`
 * - 控制栏包含选集按钮（仅当剧集数 > 1 时显示）
 * - 选集弹窗使用 Material3 ModalBottomSheet 沉浸式展示
 * - 切换剧集时利用 [PlatformVideoPlayerScreen] 的 LaunchedEffect(url) 自动重新加载
 * - 自动连播：播放完一集自动进入下一集（通过 [PlayerViewModel] 的剧集上下文实现）
 *
 * @param initialUrl    初始播放 URL
 * @param seriesTitle   影片系列名称
 * @param episodes      剧集列表（为空时退化为单集播放）
 * @param onBack        返回回调
 */
@Composable
fun FilmPlayerScreen(
    initialUrl: String,
    seriesTitle: String,
    episodes: List<EpisodeInfo> = emptyList(),
    onBack: () -> Unit,
    onFullScreenChange: ((Boolean) -> Unit)? = null,
) {
    val viewModel: PlayerViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()

    // 初始化剧集上下文
    LaunchedEffect(initialUrl, episodes, seriesTitle) {
        val initialIndex = episodes.indexOfFirst { it.url == initialUrl }.coerceAtLeast(0)
        viewModel.sendIntent(
            PlayerContract.Intent.Init(
                episodes = episodes,
                initialIndex = initialIndex,
                seriesTitle = seriesTitle,
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PlatformVideoPlayerScreen(
            url = state.currentUrl.ifEmpty { initialUrl },
            title = state.displayTitle.ifEmpty { seriesTitle },
            onBack = onBack,
            onFullScreenChange = onFullScreenChange,
            controls = { uiState, onAction ->
                VideoPlayerControls(
                    state = uiState,
                    onAction = onAction,
                    showEpisodeSelector = episodes.size > 1,
                    currentEpisodeLabel = state.currentEpisodeLabel,
                    onEpisodeSelectorClick = {
                        viewModel.sendIntent(PlayerContract.Intent.OpenEpisodeSheet)
                    },
                )
            },
            // 注入剧集上下文到 Manager，启用自动连播
            onManagerCreated = viewModel.onManagerCreated,
        )

        // 沉浸式选集弹窗
        if (state.showEpisodeSheet) {
            EpisodeSelectorSheet(
                episodes = episodes,
                currentIndex = state.currentIndex,
                onSelect = { index ->
                    viewModel.sendIntent(PlayerContract.Intent.SelectEpisode(index))
                },
                onDismiss = {
                    viewModel.sendIntent(PlayerContract.Intent.DismissEpisodeSheet)
                },
            )
        }
    }
}
