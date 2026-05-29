package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState

/**
 * 平台特定的视频播放器屏幕。
 *
 * - Android: 使用 （Foreground Service + MediaSession，支持后台播放）
 * - iOS: 使用 （compose-media-player 原生实现）
 *
 * @param onManagerCreated 当 [VideoPlayerManager] 创建完成后的回调。
 *   上层可在此回调中注入剧集上下文（[VideoPlayerManager.setEpisodeContext]）
 *   以实现自动连播功能。
 */
@Composable
expect fun PlatformVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>? = null,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit = { s, action ->
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            VideoPlayerControls(state = s, onAction = action)
        }
    },
    onFullScreenChange: ((Boolean) -> Unit)? = null,
    onManagerCreated: ((VideoPlayerManager) -> Unit)? = null,
)
