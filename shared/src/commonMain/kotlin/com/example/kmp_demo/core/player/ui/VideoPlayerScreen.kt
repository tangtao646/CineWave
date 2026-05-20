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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
    cacheDir: String? = null,  // 新增：缓存目录，由外部传入（Phase 5 平台集成）
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit = { s, action ->
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            VideoPlayerControls(state = s, onAction = action)
        }
    },
    /** PiP 画中画按钮点击回调。为 null 时使用默认行为（调用 playerState.enterPip()） */
    onPipClick: (() -> Unit)? = null,
    topBar: @Composable (BoxScope.() -> Unit)? = null,
    onFullScreenChange: ((Boolean) -> Unit)? = null
) {
    val fullscreenController = LocalFullscreenController.current
    // 用于 PiP 等需要 MonotonicFrameClock 的 Compose 协程操作
    val coroutineScope = rememberCoroutineScope()
    // 启用 ExoPlayer 内置缓存（200MB），用于缓存网络下载的切片。
    // 注意：我们不再进行 M3U8 改写（file:// 方案），播放器始终通过原始 URL 播放，
    // 因此 ExoPlayer 的 CacheDataSource 包装的是 HttpDataSource，不会出现 FileNotFoundException。
    // DiskLruCache 作为辅助缓存层，用于预加载和离线场景。
    val playerState = rememberVideoPlayerState(
        cacheConfig = CacheConfig(
            enabled = true,
            maxCacheSizeBytes = 200L * 1024L * 1024L
        )
    )

    playerState.isPipEnabled = true

    // PiP: 自动进入画中画模式（当应用进入后台且视频正在播放时）
    AutoPipEffect(playerState)

    // ========== 缓存系统初始化（Phase 4 集成） ==========
    // S1: 复用 NetworkClient.createHttpClient() 而非创建新实例

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

    // S11: 收集缓存进度（可选 UI 展示）
    val cacheProgress by interceptor.cacheProgress.collectAsState()

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
        // 进入播放页时：设置常亮标记
        currentView.keepScreenOn = true
        onDispose {
            // 退出播放页（组件销毁）时：自动释放常亮标记，恢复系统默认休眠行为
            currentView.keepScreenOn = false
        }
    }


    // S9: URL 变化时先 reset() 再重新 intercept()
    LaunchedEffect(url, headers) {
        interceptor.reset()  // 切换剧集时清理旧预加载状态
        val actualUrl = interceptor.intercept(url, headers)  // S8: 透传 headers
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


    // 最外层仅仅作为一个纯黑背景容器
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 🚀 核心修正：利用播放库推荐的 Lambda 架构，把手势和所有 UI 组件全部塞进 Surface 内部
        VideoPlayerSurface(
            playerState = playerState, modifier = Modifier.fillMaxSize()
        ) {
            // 🌟 此时这个 Box 天生就是挂在原生视频层之上的 Overlay，手势权重最高！
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

                // 2. 底部控制栏动画容器
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

                // 1. 顶栏动画容器
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

/**
 * 处理播放器动作事件
 *
 * @param coroutineScope 可选的 Compose [CoroutineScope]，用于需要 [MonotonicFrameClock] 的操作（如 PiP）。
 *   如果为 null，则 PiP 操作会使用 [GlobalScope] 作为后备。
 */
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
            // 进入画中画模式（仅 Android 8.0+ 和 iOS 有效）
            // enterPip() 内部调用 withFrameNanos()，需要 MonotonicFrameClock，
            // 该 Clock 仅在 Compose 协程作用域中可用。
            // 因此必须使用 coroutineScope（rememberCoroutineScope()）而非 GlobalScope。
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
            // 清除磁盘缓存 — 使用 GlobalScope 因为这是一个独立的一次性操作
            GlobalScope.launch(Dispatchers.IO) {
                diskCache?.clear()
            }
        }
    }
}

/**
 * 视频播放器顶栏（返回按钮 + 标题 + 更多菜单）
 *
 * @param title 视频标题
 * @param onBack 返回回调
 * @param pipEnabled 当前画中画开关状态
 * @param onPipToggle 画中画开关切换回调
 * @param onEnterPip 立即进入画中画回调
 */
@Composable
fun VideoPlayerTopBar(
    title: String,
    onBack: () -> Unit,
    pipEnabled: Boolean,
    onPipToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                )
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.4f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        // 更多按钮（⋮）
        Box {
            IconButton(
                onClick = { showMenu = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White
                )
            }

            // 下拉菜单
            DropdownMenu(
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f)),
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // 画中画开关
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "画中画",
                                modifier = Modifier.weight(1f),
                                color = Color.White
                            )
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = pipEnabled,
                                onCheckedChange = { checked ->
                                    onPipToggle(checked)
                                    showMenu = false
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFFF4444),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }
                    },
                    onClick = {
                        // 点击整行切换开关
//                        onPipToggle(!pipEnabled)
//                        showMenu = false
                    }
                )

            }
        }
    }
}
