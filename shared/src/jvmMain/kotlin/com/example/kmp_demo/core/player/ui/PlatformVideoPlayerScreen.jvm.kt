package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Desktop 平台视频播放器屏幕 — 基于 VLCJ (libvlc)。
 *
 * 使用 [VlcjVideoSurface] 渲染视频，底层通过 VLCJ 调用 libvlc 实现硬件加速解码。
 *
 * ## 架构
 * - 视频渲染：VLCJ 的 [EmbeddedMediaPlayer] 输出到 AWT Canvas，通过 SwingPanel 嵌入 Compose
 * - 播放控制：通过 [VlcjVideoPlayerController] 实现 [IPlayerController] 接口
 * - 生命周期：DisposableEffect 管理播放器资源的创建与释放
 *
 * ## 前置条件
 * - 需要用户安装 VLC 播放器
 *   - macOS: `brew install vlc`
 *   - Linux: `sudo apt install vlc`
 *   - Windows: 从 https://www.videolan.org/vlc/ 下载安装
 *
 * @see VlcjVideoPlayerController
 * @see VlcjVideoSurface
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
    val controller: IPlayerController = koinInject()
    val vlcjController = controller as DesktopVideoPlayerController

    val uiState by controller.playbackState.collectAsState()
    val currentPosition by controller.currentPosition.collectAsState()
    val duration by controller.duration.collectAsState()
    val bufferedPercent by controller.bufferedPercent.collectAsState()
    val volume by controller.volume.collectAsState()
    val isFullScreen by controller.isFullScreen.collectAsState()

    val aggregatedState =
        remember(uiState, currentPosition, duration, bufferedPercent, volume, isFullScreen) {
            VideoPlayerUiState(
                playbackState = uiState,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPercent = bufferedPercent,
                volume = volume,
                isFullScreen = isFullScreen,
            )
        }

    // 打开视频
    LaunchedEffect(url) {
        controller.open(url, headers)
    }

    // 全屏状态变化
    LaunchedEffect(isFullScreen) {
        onFullScreenChange?.invoke(isFullScreen)
    }

    // 释放资源
    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // VLCJ 视频渲染表面
        VlcjVideoSurface(
            controller = vlcjController,
            modifier = Modifier.fillMaxSize()
        )

        // 覆盖层（控制栏 + 顶栏）
        Box(modifier = Modifier.fillMaxSize()) {
            // 中央播放/暂停按钮
            val scope = rememberCoroutineScope()
            if (aggregatedState.playbackState.name in listOf("PAUSED", "IDLE", "READY", "ENDED")) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clickable { scope.launch { controller.togglePlayPause() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (aggregatedState.isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                }
            }

            // 底部控制栏
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                controls(aggregatedState) { action ->
                    scope.launch {
                        handlePlayerAction(controller, action)
                    }
                }
            }

            // 顶部栏
            topBar?.let {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    it()
                }
            }
        }
    }
}

internal suspend fun handlePlayerAction(
    controller: IPlayerController,
    action: PlayerAction,
) {
    when (action) {
        PlayerAction.TogglePlayPause -> controller.togglePlayPause()
        is PlayerAction.SeekForward -> controller.seekForward(action.seconds)
        is PlayerAction.SeekBackward -> controller.seekBackward(action.seconds)
        is PlayerAction.SeekToFraction -> {
            // 需要 duration 来计算目标位置
        }

        is PlayerAction.SeekToMs -> controller.seekTo(action.positionMs)
        PlayerAction.ToggleFullScreen -> controller.toggleFullScreen()
        PlayerAction.ToggleControls -> { /* 控制栏可见性由 UI 层管理 */
        }

        else -> {}
    }
}
