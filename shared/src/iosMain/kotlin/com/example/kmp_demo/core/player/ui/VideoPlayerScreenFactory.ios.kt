package com.example.kmp_demo.core.player.ui

import androidx.compose.runtime.Composable

/**
 * iOS 平台实现：使用 [VideoPlayerScreen]（compose-media-player 原生实现）。
 *
 * iOS 上后台播放由系统级 AVPlayer 控制，不需要 Foreground Service。
 * 如需 iOS 后台播放，可参考 compose-media-player 的 `pauseOnBackground` 特性。
 */
@Composable
actual fun PlatformVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>?,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit,
    onFullScreenChange: ((Boolean) -> Unit)?,
) {
    VideoPlayerScreen(
        url = url,
        title = title,
        onBack = onBack,
        headers = headers,
        controls = controls,
        onFullScreenChange = onFullScreenChange,
    )
}
