package com.example.kmp_demo.core.player.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Android 平台视频渲染 Surface。
 *
 * 使用 [PlayerView] 渲染视频画面，绑定到 [player]（ExoPlayer 或 MediaController）。
 * 完全由我们控制，不依赖任何第三方 Compose 播放库。
 *
 * 关键设计：
 * - [useController] = false，使用我们自己的 Compose 控制栏
 * - [resizeMode] 可配置，默认 FIT
 * - 通过 [onSurfaceViewReady] 回调暴露底层 SurfaceView，用于全屏切换等场景
 *
 * @param player ExoPlayer 或 MediaController 实例
 * @param modifier Compose 布局修饰符
 * @param resizeMode 画面缩放模式，默认 FIT
 */
@Composable
fun VideoPlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val context = LocalContext.current

    // 使用 remember 持有 PlayerView 引用，避免重组时重建
    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            this.resizeMode = resizeMode
            this.player = player
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // 当 player 实例变化时更新
    DisposableEffect(player) {
        playerView.player = player
        onDispose {
            // 不要在这里释放 player，由 Controller 管理生命周期
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier,
        update = { view ->
            view.player = player
            view.resizeMode = resizeMode
        }
    )
}
