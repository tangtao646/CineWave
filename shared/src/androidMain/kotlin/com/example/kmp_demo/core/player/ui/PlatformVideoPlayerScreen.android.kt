package com.example.kmp_demo.core.player.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import com.example.kmp_demo.core.player.ui.LocalIsPortrait
import com.example.kmp_demo.core.network.createHttpClient
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.player.domain.ShareUrlResolver
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.ExoPlayerController
import org.koin.compose.koinInject

/**
 * Android 平台统一的视频播放器屏幕实现。
 *
 * ## 缓存架构
 * 使用 ExoPlayer 原生 SimpleCache + CacheDataSource 方案，
 * 无需本地 HTTP 代理，无端口竞态，真正流式缓存。
 *
 * @param url 视频播放地址
 * @param title 视频标题
 * @param onBack 返回回调
 * @param headers 自定义请求头
 * @param controls 自定义控制栏
 * @param topBar 自定义顶栏
 * @param onFullScreenChange 全屏状态变化回调
 */
@Composable
fun AndroidVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>? = null,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit = { s, action ->
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            AndroidVideoPlayerControls(state = s, onAction = action)
        }
    },
    topBar: @Composable (BoxScope.() -> Unit)? = null,
    onFullScreenChange: ((Boolean) -> Unit)? = null,
) {
    val fullscreenController = LocalFullscreenController.current
    
    // ========== 从 Koin 获取（由 DI 管理生命周期） ==========
    val controller: ExoPlayerController = koinInject()
    val segmentCacheTracker: SegmentCacheTracker = koinInject()

    // ========== 分享链接解析器 ==========
    val httpClient = remember { createHttpClient() }
    val shareUrlResolver = remember(httpClient) { ShareUrlResolver(httpClient) }

    // ========== 播放器管理器（无代理服务器） ==========
    val manager = remember(controller, segmentCacheTracker) {
        VideoPlayerManager(
            controller = controller,
            proxyServer = null,          // Android 使用 SimpleCache，无需代理
            segmentCacheTracker = segmentCacheTracker,
        )
    }
    val uiState by manager.uiState.collectAsState()

    // ========== 打开视频 ==========
    LaunchedEffect(url, headers) {
        val resolvedUrl = shareUrlResolver.resolve(url, headers)
        manager.open(resolvedUrl, headers)
    }

    // ========== 全屏处理 ==========
    LaunchedEffect(uiState.isFullScreen) {
        if (uiState.isFullScreen) fullscreenController.enterFullscreen()
        else fullscreenController.exitFullscreen()
        onFullScreenChange?.invoke(uiState.isFullScreen)
    }

    // ========== 返回键处理 ==========
    BackHandler {
        if (uiState.isFullScreen) {
            handlePlayerAction(manager, PlayerAction.ToggleFullScreen)
        } else {
            onBack()
        }
    }

    // ========== 屏幕常亮 ==========
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose { currentView.keepScreenOn = false }
    }

    // ========== 释放资源 ==========
    DisposableEffect(manager) {
        onDispose {
            fullscreenController.exitFullscreen()
            httpClient.close()
            segmentCacheTracker.release()
            manager.release()
            // 注意：ExoPlayerCache(SimpleCache) 由 Koin single{} 管理，此处不释放
        }
    }

    // ========== 横竖屏检测 ==========
    // 使用 LocalConfiguration 获取当前屏幕方向
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // ========== UI 布局 ==========
    // 通过 CompositionLocalProvider 将横竖屏状态传递给 VideoPlayerControls
    CompositionLocalProvider(LocalIsPortrait provides isPortrait) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 视频渲染 Surface
            VideoPlayerSurface(
                player = controller.player,
                modifier = Modifier.fillMaxSize()
            )

            // 覆盖层（手势 + 控制栏）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                handlePlayerAction(manager, PlayerAction.ToggleControls)
                            },
                            onDoubleTap = {
                                handlePlayerAction(manager, PlayerAction.TogglePlayPause)
                            }
                        )
                    }
            ) {
                // 中央大按钮
                CenterPlayButton(
                    state = uiState,
                    onClick = {
                        handlePlayerAction(manager, PlayerAction.TogglePlayPause)
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                // 底部控制栏
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
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                                )
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        handlePlayerAction(manager, PlayerAction.ToggleControls)
                                    },
                                    onDoubleTap = {
                                        handlePlayerAction(manager, PlayerAction.TogglePlayPause)
                                    }
                                )
                            }
                    ) {
                        controls(uiState) { action ->
                            handlePlayerAction(manager, action)
                        }
                    }
                }

                // 顶部栏
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
                            .windowInsetsPadding(
                                WindowInsets.statusBars.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                )
                            )
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

}

/**
 * Android 平台专用的播放器控制栏包装。
 *
 * 竖屏/横屏的显隐控制由 [LocalIsPortrait] CompositionLocal 自动处理：
 * - **竖屏模式**：隐藏音量控制和选集按钮（空间紧凑）
 * - **横屏模式**：显示所有控制元素
 *
 * [LocalIsPortrait] 由 [AndroidVideoPlayerScreen] 在顶层通过
 * [CompositionLocalProvider] 提供，基于 [LocalConfiguration.current.orientation] 计算。
 */
@Composable
private fun AndroidVideoPlayerControls(
    state: VideoPlayerUiState,
    onAction: (PlayerAction) -> Unit,
) {
    VideoPlayerControls(
        state = state,
        onAction = onAction,
    )
}

internal fun handlePlayerAction(
    manager: VideoPlayerManager,
    action: PlayerAction,
) {
    when (action) {
        PlayerAction.TogglePlayPause -> manager.togglePlayPause()
        is PlayerAction.SeekForward -> manager.seekForward(action.seconds)
        is PlayerAction.SeekBackward -> manager.seekBackward(action.seconds)
        is PlayerAction.SeekToFraction -> {
            val targetMs = (action.fraction * (manager.uiState.value.duration)).toLong()
            manager.seekTo(targetMs)
        }

        is PlayerAction.SeekToMs -> manager.seekTo(action.positionMs)
        PlayerAction.ToggleFullScreen -> manager.toggleFullScreen()
        PlayerAction.TogglePip -> { /* Android 使用 ExoPlayer 原生实现，暂不支持画中画 */
        }

        PlayerAction.ToggleControls -> manager.toggleControls()
        is PlayerAction.SetVolume -> manager.setVolume(action.volume)
        PlayerAction.ToggleMute -> {
            val currentVolume = manager.uiState.value.volume
            if (currentVolume > 0f) {
                manager.setVolume(0f)
            } else {
                manager.setVolume(1.0f)
            }
        }

        PlayerAction.ClearCache -> {
            // SimpleCache 清理：由用户在设置页手动触发，此处保留接口
        }
    }
}
