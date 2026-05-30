package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import org.koin.compose.viewmodel.koinViewModel

/**
 * 通用播放器屏幕 — 支持沉浸式选集。
 *
 * 合并 [DomesticPlayerScreen] 和 [FilmPlayerScreen] 的重复代码，
 * 两个特征层退化为简单委托。
 *
 * 纯 UI 层，所有业务逻辑委托给 [PlayerViewModel]。
 *
 * 设计要点：
 * - 顶栏标题动态显示 `"剧集名 · 第X集"`
 * - 控制栏包含选集按钮（仅当剧集数 > 1 时显示）
 * - 选集弹窗使用 Material3 ModalBottomSheet 沉浸式展示
 * - 切换剧集时利用 [PlatformVideoPlayerScreen] 的 LaunchedEffect(url) 自动重新加载
 *
 * @param initialUrl    初始播放 URL
 * @param seriesTitle   剧集系列名称（如"狂飙"）
 * @param episodes      剧集列表（为空时退化为单集播放）
 * @param onBack        返回回调
 */
@Composable
fun PlayerScreen(
    initialUrl: String,
    seriesTitle: String,
    episodes: List<EpisodeInfo> = emptyList(),
    onBack: () -> Unit,
    onFullScreenChange: ((Boolean) -> Unit)? = null,
) {
    val viewModel: PlayerViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()

    // 初始化：传递 episodes 和 initialUrl，由 ViewModel 管理剧集索引
    LaunchedEffect(Unit) {
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
        )

        // 选集弹窗
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
