package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState

/**
 * Android 平台实现：使用新的 [AndroidVideoPlayerScreen]。
 *
 * 基于 ExoPlayer (Media3) 原生实现，完全替代 ComposeMediaPlayer 库。
 * 全屏切换、手势、控制栏等全部由 Compose 原生实现，不再受第三方库限制。
 */
@Composable
actual fun PlatformVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>?,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit,
    topBar: @Composable (BoxScope.() -> Unit)?,
    onFullScreenChange: ((Boolean) -> Unit)?,
) {
    AndroidVideoPlayerScreen(
        url = url,
        title = title,
        onBack = onBack,
        headers = headers,
        controls = controls,
        topBar = topBar,
        onFullScreenChange = onFullScreenChange,
    )
}
