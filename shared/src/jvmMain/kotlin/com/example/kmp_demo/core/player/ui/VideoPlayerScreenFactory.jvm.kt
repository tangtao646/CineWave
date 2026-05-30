package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import com.example.kmp_demo.core.player.cache.CacheProxyServer
import com.example.kmp_demo.core.player.cache.DebugLog
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.CacheOrchestrator
import com.example.kmp_demo.core.player.domain.IVideoPlayerController
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.player.domain.LocalPlayerKeyActionHandler
import com.example.kmp_demo.core.player.domain.PlayerKeyAction
import com.example.kmp_demo.core.player.domain.PlayerKeyActionBridge
import com.example.kmp_demo.core.player.domain.ShareUrlResolver
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Desktop 平台视频播放器屏幕 — 基于 VLCJ (libvlc)。
 *
 * 重构后职责：
 * 1. 创建 [VideoPlayerManager]（组合 [CacheOrchestrator]）
 * 2. 通过 DI 获取 [ShareUrlResolver]，在 LaunchedEffect 中解析 URL
 * 3. LaunchedEffect(url) 中调用 manager.open()（副作用集中在此层）
 * 4. 调用 [SharedVideoPlayerScreen]（纯 UI）
 *
 * ## 架构
 * - 视频渲染：VLCJ 的 [CallbackVideoSurface] 获取 RGBA 帧 → Compose Canvas 绘制
 * - 业务编排：[VideoPlayerManager] 聚合状态、管理控制栏显隐
 * - 缓存管理：[CacheOrchestrator] 管理代理服务器和切片追踪
 * - 播放控制：通过 [IVideoPlayerController] 接口与 commonMain 解耦
 * - 生命周期：DisposableEffect 管理播放器资源的创建与释放
 *
 * ## 全屏架构
 * 全屏切换由 [DesktopVideoPlayerController.setFullscreen] 内部调用
 * [LocalFullscreenController] 实现，UI 层不再手动管理全屏状态。
 *
 * @see DesktopVideoPlayerController
 * @see VlcjVideoSurface
 * @see VideoPlayerManager
 * @see CacheOrchestrator
 */
@Composable
actual fun PlatformVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>?,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit,
    onFullScreenChange: ((Boolean) -> Unit)?,
    onManagerCreated: ((VideoPlayerManager) -> Unit)?,
) {
    val fullscreenController = LocalFullscreenController.current

    // ========== 从 Koin 获取（由 DI 管理生命周期） ==========
    val controller: IVideoPlayerController =
        koinInject(parameters = { parametersOf(fullscreenController) })
    val proxyServer: CacheProxyServer = koinInject()
    val segmentCacheTracker: SegmentCacheTracker = koinInject()
    val shareUrlResolver: ShareUrlResolver = koinInject()
    val httpClient: HttpClient = koinInject()

    // ========== 创建 CacheOrchestrator ==========
    val cacheOrchestrator = remember(proxyServer, segmentCacheTracker, httpClient) {
        CacheOrchestrator(
            proxyServer = proxyServer,
            segmentCacheTracker = segmentCacheTracker,
            httpClient = httpClient,
        )
    }

    // ========== 创建 VideoPlayerManager ==========
    val manager = remember(controller, cacheOrchestrator) {
        VideoPlayerManager(
            controller = controller,
            cacheOrchestrator = cacheOrchestrator,
        )
    }

    // 通知上层 Manager 已创建
    LaunchedEffect(manager) {
        onManagerCreated?.invoke(manager)
    }

    // ========== 防重入：防止同一 URL 被重复打开 ==========
    var lastOpenedUrl by remember { mutableStateOf<String?>(null) }

    // ========== 副作用集中在此：解析 URL + 打开视频 ==========
    LaunchedEffect(url, headers) {
        val resolvedUrl = shareUrlResolver.resolve(url, headers)
        // 防重入：如果解析后的 URL 与上次相同，跳过
        if (resolvedUrl == lastOpenedUrl) return@LaunchedEffect
        lastOpenedUrl = resolvedUrl

        DebugLog.d("[PlatformVideoPlayerScreen]"," resolvedUrl = $resolvedUrl")
        manager.open(resolvedUrl, headers)
    }

    val uiState by manager.uiState.collectAsState()
    val keyActionHandler: (PlayerKeyAction) -> Unit = { action ->
        if (uiState.playbackState != VideoPlaybackState.IDLE &&
            uiState.playbackState != VideoPlaybackState.ERROR
        ) {
            when (action) {
                PlayerKeyAction.TogglePlayPause -> handlePlayerAction(manager, PlayerAction.TogglePlayPause)
                PlayerKeyAction.SeekForward -> {
                    manager.seekForward(10)
                }
                PlayerKeyAction.SeekBackward -> {
                    manager.seekBackward(10)
                }
                PlayerKeyAction.VolumeUp -> {
                    val newVolume = (uiState.volume + 0.1f).coerceAtMost(1.0f)
                    manager.setVolume(newVolume)
                }
                PlayerKeyAction.VolumeDown -> {
                    val newVolume = (uiState.volume - 0.1f).coerceAtLeast(0.0f)
                    manager.setVolume(newVolume)
                }
                PlayerKeyAction.ExitFullscreen -> {
                    if (uiState.isFullScreen) {
                        handlePlayerAction(manager, PlayerAction.ToggleFullScreen)
                        onFullScreenChange?.invoke(false)
                    }
                }
            }
        }
    }

    PlayerKeyActionBridge.handler.set(keyActionHandler)

    CompositionLocalProvider(LocalPlayerKeyActionHandler provides keyActionHandler) {
        SharedVideoPlayerScreen(
            url = url,
            title = title,
            onBack = onBack,
            manager = manager,
            controls = controls,
            onFullScreenChange = onFullScreenChange,
            // ========== 平台插槽：视频 Surface ==========
            videoSurface = { modifier ->
                val vlcjController = controller as DesktopVideoPlayerController
                VlcjVideoSurface(
                    controller = vlcjController,
                    modifier = modifier,
                )
            },
            // ========== 平台插槽：资源释放 ==========
            onPlatformDispose = {
                // Desktop 无需额外释放逻辑
            },
        )
    }
}
