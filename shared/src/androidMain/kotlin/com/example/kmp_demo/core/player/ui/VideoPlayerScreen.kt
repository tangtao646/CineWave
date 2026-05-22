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
import com.example.kmp_demo.core.network.createHttpClient
import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.ComposeMediaPlayerController
import com.example.kmp_demo.core.player.platform.getDefaultCacheDir
import io.github.kdroidfilter.composemediaplayer.AutoPipEffect
import io.github.kdroidfilter.composemediaplayer.CacheConfig
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 顶层视频播放器屏幕组件
 *
 * 如需自定义控制栏，可通过 [controls] 参数传入自定义 Composable。
 *
 * @param url 视频播放地址
 * @param title 视频标题
 * @param headers 自定义请求头
 * @param onBack 返回回调
 * @param controls 自定义控制栏（为 null 时使用默认 VideoPlayerControls）
 * @param topBar 自定义顶栏（为 null 时使用默认返回+标题顶栏）
 * @param onFullScreenChange 全屏状态变化回调
 */
@Composable
fun VideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>? = null,
    cacheDir: String? = null,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit = { s, action ->
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            VideoPlayerControls(state = s, onAction = action)
        }
    },
    onPipClick: (() -> Unit)? = null,
    topBar: @Composable (BoxScope.() -> Unit)? = null,
    onFullScreenChange: ((Boolean) -> Unit)? = null
) {
    val fullscreenController = LocalFullscreenController.current
    val coroutineScope = rememberCoroutineScope()
    val playerState = rememberVideoPlayerState(
        cacheConfig = CacheConfig(
            enabled = true,
            maxCacheSizeBytes = 200L * 1024L * 1024L
        )
    )

    playerState.isPipEnabled = true

    AutoPipEffect(playerState)

    val resolvedCacheDir = cacheDir ?: "${getDefaultCacheDir(LocalContext.current)}/video_cache"
    val diskCache = remember { DiskLruCache(resolvedCacheDir) }
    val httpClient = remember { createHttpClient() }
    val interceptor = remember {
        M3u8CacheInterceptor(httpClient, diskCache, resolvedCacheDir)
    }

    val controller = remember { ComposeMediaPlayerController(playerState) }
    val manager = remember(controller, interceptor) {
        VideoPlayerManager(controller, cacheInterceptor = interceptor)
    }
    val uiState by manager.uiState.collectAsState()

    val onBackWithFullScreen: () -> Unit = {
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

    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    LaunchedEffect(url, headers) {
        interceptor.reset()
        val actualUrl = interceptor.intercept(url, headers)
        manager.open(actualUrl, headers)
    }

    LaunchedEffect(uiState.isFullScreen) {
        if (uiState.isFullScreen) {
            fullscreenController.enterFullscreen()
        } else {
            fullscreenController.exitFullscreen()
        }
        onFullScreenChange?.invoke(uiState.isFullScreen)
    }

    DisposableEffect(manager) {
        onDispose {
            fullscreenController.exitFullscreen()
            interceptor.stop()
            httpClient.close()
            manager.release()
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoPlayerSurface(
            playerState = playerState, modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            handlePlayerAction(
                                manager,
                                PlayerAction.ToggleControls,
                                coroutineScope = coroutineScope
                            )
                        }, onDoubleTap = {
                            handlePlayerAction(
                                manager,
                                PlayerAction.TogglePlayPause,
                                coroutineScope = coroutineScope
                            )
                        })
                    }) {

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
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    handlePlayerAction(
                                        manager,
                                        PlayerAction.ToggleControls,
                                        coroutineScope = coroutineScope
                                    )
                                }, onDoubleTap = {
                                    handlePlayerAction(
                                        manager,
                                        PlayerAction.TogglePlayPause,
                                        coroutineScope = coroutineScope
                                    )
                                })
                            }) {
                        controls(uiState) { action ->
                            handlePlayerAction(
                                manager,
                                action,
                                playerState = playerState,
                                coroutineScope = coroutineScope
                            )
                        }
                    }
                }

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
                            .clickable(
                                enabled = true, onClick = onBackWithFullScreen
                            )

                    ) {
                        if (topBar != null) {
                            topBar()
                        } else {
                            VideoPlayerTopBar(
                                title = title,
                                onBack = onBackWithFullScreen,
                                pipEnabled = playerState.isPipEnabled,
                                onPipToggle = { enabled ->
                                    playerState.isPipEnabled = enabled
                                },
                            )
                        }
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
