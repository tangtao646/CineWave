package com.example.kmp_demo.core.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState

/**
 * 平台间共享的视频播放器 UI 骨架。
 *
 * 重构后：纯 UI 组件，不包含任何副作用逻辑。
 * - 移除 LaunchedEffect(url) { manager.open() }
 * - 移除 ShareUrlResolver 的创建和调用
 * - 移除 httpClient 的创建和关闭
 * - 只负责 UI 布局、手势、动画
 *
 *
 * ## 架构
 * ```
 * SharedVideoPlayerScreen          ← commonMain（纯 UI 骨架）
 *   ├─ videoSurface: 插槽          ← 平台提供（ExoPlayer Surface / VLCJ Surface）
 *   ├─ topBarModifier: 参数        ← 平台提供（Android 的 statusBars padding）
 *   ├─ bottomBarModifier: 参数     ← 平台提供（Android 的 systemBars padding）
 *   └─ onPlatformDispose: 回调     ← 平台提供（额外资源释放）
 * ```
 *
 * @param url 视频播放地址（仅用于 UI 显示，不触发 manager.open()）
 * @param title 视频标题
 * @param onBack 返回回调
 * @param manager 播放器管理器（由平台层创建并传入）
 * @param controls 自定义控制栏
 * @param onFullScreenChange 全屏状态变化回调
 * @param videoSurface 平台特定的视频渲染 Surface
 * @param topBarModifier 顶部栏的额外修饰符（如 Android 的 statusBars padding）
 * @param bottomBarModifier 底部控制栏的额外修饰符（如 Android 的 systemBars padding）
 * @param onPlatformDispose 平台特定的资源释放回调
 */
@Composable
fun SharedVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    manager: VideoPlayerManager,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit,
    onFullScreenChange: ((Boolean) -> Unit)? = null,
    // 平台插槽 ↓
    videoSurface: @Composable (Modifier) -> Unit,
    topBarModifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier,
    onPlatformDispose: () -> Unit = {},
) {
    val uiState by manager.uiState.collectAsState()

    // ========== 全屏状态变化回调 ==========
    LaunchedEffect(uiState.isFullScreen) {
        onFullScreenChange?.invoke(uiState.isFullScreen)
    }

    // ========== 释放资源 ==========
    DisposableEffect(manager) {
        onDispose {
            onPlatformDispose()
            manager.release()
        }
    }

    // ========== UI 布局 ==========
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 视频渲染 Surface（平台插槽）
        videoSurface(Modifier.fillMaxSize())

        // 覆盖层（手势 + 控制栏 + 顶栏）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { handlePlayerAction(manager, PlayerAction.ToggleControls) },
                        onDoubleTap = { handlePlayerAction(manager, PlayerAction.TogglePlayPause) }
                    )
                }
        ) {
            // 中央播放/暂停按钮（带缓冲指示）
            CenterPlayButton(
                state = uiState,
                onClick = { handlePlayerAction(manager, PlayerAction.TogglePlayPause) },
                modifier = Modifier.align(Alignment.Center)
            )

            // 底部控制栏（带动画）
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .then(bottomBarModifier)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { handlePlayerAction(manager, PlayerAction.ToggleControls) },
                                onDoubleTap = { handlePlayerAction(manager, PlayerAction.TogglePlayPause) }
                            )
                        }
                ) {
                    controls(uiState) { action ->
                        handlePlayerAction(manager, action)
                    }
                }
            }

            // 顶部栏（带动画）
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(topBarModifier)
                ) {
                    VideoPlayerTopBar(
                        title = title,
                        onBack = {
                            if (uiState.isFullScreen) {
                                handlePlayerAction(manager, PlayerAction.ToggleFullScreen)
                            } else {
                                onBack()
                            }
                        },
                        pipEnabled = false,
                        onPipToggle = {},
                    )
                }
            }
        }
    }
}
