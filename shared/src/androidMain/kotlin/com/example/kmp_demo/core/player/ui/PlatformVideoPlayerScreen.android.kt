package com.example.kmp_demo.core.player.ui

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.player.domain.ShareUrlResolver
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.ExoPlayerController
import com.example.kmp_demo.core.player.platform.getDefaultCacheDir
import com.example.kmp_demo.core.network.createHttpClient
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Android 平台统一的视频播放器屏幕实现。
 *
 *
 * 设计要点：
 * - 使用 [ExoPlayerController] 直接管理 ExoPlayer，不依赖任何第三方 Compose 播放库
 * - 使用 [VideoPlayerSurface]（AndroidView + PlayerView）渲染视频画面
 * - 全屏切换通过 [AndroidFullscreenController] 原生实现，不再受 ComposeMediaPlayer 限制
 * - 控制栏、手势、顶栏等 UI 完全由 Compose 实现
 * - 缓存系统（DiskLruCache + M3u8CacheInterceptor）保持独立
 *
 * @param url 视频播放地址
 * @param title 视频标题
 * @param onBack 返回回调
 * @param headers 自定义请求头
 * @param controls 自定义控制栏（为 null 时使用默认 VideoPlayerControls）
 * @param topBar 自定义顶栏（为 null 时使用默认返回+标题顶栏）
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
            VideoPlayerControls(state = s, onAction = action)
        }
    },
    topBar: @Composable (BoxScope.() -> Unit)? = null,
    onFullScreenChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fullscreenController = LocalFullscreenController.current

    // ========== 缓存系统初始化 ==========
    val resolvedCacheDir = "${getDefaultCacheDir(context)}/video_cache"
    val diskCache = remember { DiskLruCache(resolvedCacheDir) }
    val httpClient = remember { createHttpClient() }

    // ========== 分享链接解析器 ==========
    val shareUrlResolver = remember(httpClient) { ShareUrlResolver(httpClient) }

    val interceptor = remember {
        M3u8CacheInterceptor(
            httpClient = httpClient,
            diskCache = diskCache,
            cacheDir = resolvedCacheDir
        )
    }

    // ========== 播放器控制器（注入 HybridDataSource） ==========
    val controller = remember(diskCache) { ExoPlayerController(context, diskCache) }
    val manager = remember(controller) {
        VideoPlayerManager(controller)
    }
    val uiState by manager.uiState.collectAsState()

    // S11: 收集缓存进度（可选 UI 展示）
    val cacheProgress by interceptor.cacheProgress.collectAsState()

    // ========== 打开视频 ==========
    LaunchedEffect(url, headers) {
        val resolvedUrl = shareUrlResolver.resolve(url, headers)
        manager.open(resolvedUrl, headers)
    }

    // ========== 全屏处理 ==========
    LaunchedEffect(uiState.isFullScreen) {
        if (uiState.isFullScreen) {
            fullscreenController.enterFullscreen()
        } else {
            fullscreenController.exitFullscreen()
        }
        onFullScreenChange?.invoke(uiState.isFullScreen)
    }

    // ========== 返回键处理 ==========
    BackHandler {
        if (uiState.isFullScreen) {
            handlePlayerAction(
                manager,
                PlayerAction.ToggleFullScreen,
                coroutineScope = coroutineScope
            )
        } else {
            onBack()
        }
    }

    // ========== 屏幕常亮 ==========
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    // ========== 释放资源 ==========
    DisposableEffect(manager) {
        onDispose {
            fullscreenController.exitFullscreen()
            interceptor.stop()
            httpClient.close()
            manager.release()
        }
    }

    // ========== UI 布局 ==========
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
                            handlePlayerAction(
                                manager,
                                PlayerAction.ToggleControls,
                                coroutineScope = coroutineScope
                            )
                        },
                        onDoubleTap = {
                            handlePlayerAction(
                                manager,
                                PlayerAction.TogglePlayPause,
                                coroutineScope = coroutineScope
                            )
                        }
                    )
                }
        ) {
            // 中央大按钮（播放/暂停 + 缓冲指示），固定在覆盖层正中央，不受 controls 显隐影响
            CenterPlayButton(
                state = uiState,
                onClick = {
                    handlePlayerAction(
                        manager,
                        PlayerAction.TogglePlayPause,
                        coroutineScope = coroutineScope
                    )
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
                                    handlePlayerAction(
                                        manager,
                                        PlayerAction.ToggleControls,
                                        coroutineScope = coroutineScope
                                    )
                                },
                                onDoubleTap = {
                                    handlePlayerAction(
                                        manager,
                                        PlayerAction.TogglePlayPause,
                                        coroutineScope = coroutineScope
                                    )
                                }
                            )
                        }
                ) {
                    controls(uiState) { action ->
                        handlePlayerAction(
                            manager,
                            action,
                            coroutineScope = coroutineScope
                        )
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
                        .clickable(enabled = true) {
                            if (uiState.isFullScreen) {
                                handlePlayerAction(
                                    manager,
                                    PlayerAction.ToggleFullScreen,
                                    coroutineScope = coroutineScope
                                )
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
                                    handlePlayerAction(
                                        manager,
                                        PlayerAction.ToggleFullScreen,
                                        coroutineScope = coroutineScope
                                    )
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

internal fun handlePlayerAction(
    manager: VideoPlayerManager,
    action: PlayerAction,
    diskCache: DiskLruCache? = null,
    playerState: VideoPlayerState? = null,
    coroutineScope: CoroutineScope? = null,
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
        PlayerAction.TogglePip -> {
            playerState?.let { state ->
                if (state.isPipSupported) {
                    val scope = coroutineScope ?: GlobalScope
                    scope.launch {
                        state.enterPip()
                    }
                }
            }
        }
        PlayerAction.ToggleControls -> manager.toggleControls()
        PlayerAction.ClearCache -> {
            GlobalScope.launch(Dispatchers.IO) {
                diskCache?.clear()
            }
        }
    }
}
