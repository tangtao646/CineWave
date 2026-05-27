package com.example.kmp_demo.core.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.kmp_demo.core.player.cache.CacheProxyServer
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.ShareUrlResolver
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import com.example.kmp_demo.core.network.createHttpClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Desktop 平台视频播放器屏幕 — 基于 VLCJ (libvlc)。
 *
 * 使用 [VideoPlayerManager] 进行业务编排，与 Android 端架构对齐。
 *
 * ## 架构
 * - 视频渲染：VLCJ 的 [CallbackVideoSurface] 获取 RGBA 帧 → Compose Canvas 绘制
 * - 业务编排：[VideoPlayerManager] 聚合状态、管理控制栏显隐、自动隐藏
 * - 播放控制：通过 [IPlayerController] 接口与 commonMain 解耦
 * - 生命周期：DisposableEffect 管理播放器资源的创建与释放
 *
 *
 * @see DesktopVideoPlayerController
 * @see VlcjVideoSurface
 * @see VideoPlayerManager
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
    val proxyServer: CacheProxyServer = koinInject()
    val segmentCacheTracker: SegmentCacheTracker = koinInject()
    val manager = remember(controller, proxyServer, segmentCacheTracker) {
        VideoPlayerManager(
            controller = controller,
            proxyServer = proxyServer,
            segmentCacheTracker = segmentCacheTracker,
        )
    }
    val uiState by manager.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 分享链接解析器
    val httpClient = remember { createHttpClient() }
    val shareUrlResolver = remember(httpClient) { ShareUrlResolver(httpClient) }

    // 打开视频（先解析分享链接）
    LaunchedEffect(url, headers) {
        val resolvedUrl = shareUrlResolver.resolve(url, headers)
        manager.open(resolvedUrl, headers)
    }

    // 全屏状态变化
    LaunchedEffect(uiState.isFullScreen) {
        onFullScreenChange?.invoke(uiState.isFullScreen)
    }

    // 释放资源
    DisposableEffect(manager) {
        onDispose {
            httpClient.close()
            manager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // VLCJ 视频渲染表面
        val vlcjController = controller as DesktopVideoPlayerController
        VlcjVideoSurface(
            controller = vlcjController,
            modifier = Modifier.fillMaxSize()
        )

        // 覆盖层（手势 + 控制栏 + 顶栏）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { manager.toggleControls() },
                        onDoubleTap = { manager.togglePlayPause() }
                    )
                }
        ) {
            // 中央播放/暂停按钮（带缓冲指示）
            CenterPlayButton(
                state = uiState,
                onClick = { manager.togglePlayPause() },
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
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { manager.toggleControls() },
                                onDoubleTap = { manager.togglePlayPause() }
                            )
                        }
                ) {
                    controls(uiState) { action ->
                        coroutineScope.launch {
                            handleDesktopPlayerAction(manager, action)
                        }
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
                        .clickable {
                            if (uiState.isFullScreen) {
                                manager.toggleFullScreen()
                            } else {
                                onBack()
                            }
                        }
                ) {
                    if (topBar != null) {
                        topBar()
                    } else {
                        VideoPlayerTopBar(
                            title = title,
                            onBack = {
                                if (uiState.isFullScreen) {
                                    manager.toggleFullScreen()
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
}

/**
 * Desktop 端播放器动作处理。
 *
 * 通过 [VideoPlayerManager] 间接操作 [IPlayerController]，
 * 与 Android 端的 [handlePlayerAction] 功能对等。
 */
private fun handleDesktopPlayerAction(
    manager: VideoPlayerManager,
    action: PlayerAction,
) {
    when (action) {
        PlayerAction.TogglePlayPause -> manager.togglePlayPause()
        is PlayerAction.SeekForward -> manager.seekForward(action.seconds)
        is PlayerAction.SeekBackward -> manager.seekBackward(action.seconds)
        is PlayerAction.SeekToFraction -> manager.seekToFraction(action.fraction)
        is PlayerAction.SeekToMs -> manager.seekTo(action.positionMs)
        PlayerAction.ToggleFullScreen -> manager.toggleFullScreen()
        PlayerAction.ToggleControls -> manager.toggleControls()
        PlayerAction.TogglePip -> { /* Desktop 不支持画中画 */ }
        PlayerAction.ClearCache -> { /* Desktop 不使用磁盘缓存 */ }
    }
}
