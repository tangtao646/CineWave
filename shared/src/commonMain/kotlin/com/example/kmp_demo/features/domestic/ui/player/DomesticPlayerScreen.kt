package com.example.kmp_demo.features.domestic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.player.ui.*
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 国内板块播放器屏幕 — 支持沉浸式选集。
 *
 * 与 [FilmPlayerScreen] 不同，本组件接收剧集列表 [episodes]，
 * 在播放器内提供选集功能，用户无需返回详情页即可切换剧集。
 *
 * 设计要点：
 * - 顶栏标题动态显示 `"剧集名 · 第X集"`
 * - 控制栏包含选集按钮（仅当剧集数 > 1 时显示）
 * - 选集弹窗使用 Material3 ModalBottomSheet 沉浸式展示
 * - 切换剧集时利用 [PlatformVideoPlayerScreen] 的 LaunchedEffect(url) 自动重新加载
 *
 * @param initialUrl    初始播放 URL
 * @param seriesTitle   剧集系列名称（如"狂飙"）
 * @param episodes      剧集列表
 * @param onBack        返回回调
 */
@Composable
fun DomesticPlayerScreen(
    initialUrl: String,
    seriesTitle: String,
    episodes: List<EpisodeInfo>,
    onBack: () -> Unit,
) {
    // 找到初始 URL 对应的索引，找不到则从第 0 集开始
    var currentIndex by remember(initialUrl) {
        mutableIntStateOf(
            episodes.indexOfFirst { it.url == initialUrl }.coerceAtLeast(0)
        )
    }
    var showEpisodeSheet by remember { mutableStateOf(false) }

    val currentEpisode = episodes.getOrNull(currentIndex) ?: return

    // 动态标题：如"狂飙 · 第3集"
    val displayTitle = "$seriesTitle · ${currentEpisode.label}"

    // 选集回调：切换剧集
    val onSelectEpisode: (Int) -> Unit = { index ->
        currentIndex = index
        showEpisodeSheet = false
        // URL 变化会触发 PlatformVideoPlayerScreen 内部的 LaunchedEffect(url) 自动切换
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PlatformVideoPlayerScreen(
            url = currentEpisode.url,
            title = displayTitle,
            onBack = onBack,
            controls = { state, onAction ->
                VideoPlayerControls(
                    state = state,
                    onAction = onAction,
                    showEpisodeSelector = episodes.size > 1,
                    currentEpisodeLabel = currentEpisode.label,
                    onEpisodeSelectorClick = { showEpisodeSheet = true },
                )
            },
            topBar = {
                VideoPlayerTopBar(
                    title = displayTitle,
                    onBack = onBack,
                    pipEnabled = false,
                    onPipToggle = {},
                )
            },
        )

        // 沉浸式选集弹窗
        if (showEpisodeSheet) {
            EpisodeSelectorSheet(
                episodes = episodes,
                currentIndex = currentIndex,
                onSelect = onSelectEpisode,
                onDismiss = { showEpisodeSheet = false },
            )
        }
    }
}
