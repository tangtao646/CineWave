package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import com.example.kmp_demo.core.player.cache.CacheProxyServer
import com.example.kmp_demo.core.player.cache.DebugLog
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.IVideoPlayerController
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.player.domain.LocalPlayerKeyActionHandler
import com.example.kmp_demo.core.player.domain.PlayerKeyAction
import com.example.kmp_demo.core.player.domain.PlayerKeyActionBridge
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Desktop 平台视频播放器屏幕 — 基于 VLCJ (libvlc)。
 *
 * 使用 [VideoPlayerManager] 进行业务编排，与 Android 端架构对齐。
 *
 * ## 架构
 * - 视频渲染：VLCJ 的 [CallbackVideoSurface] 获取 RGBA 帧 → Compose Canvas 绘制
 * - 业务编排：[VideoPlayerManager] 聚合状态、管理控制栏显隐、自动隐藏
 * - 播放控制：通过 [IVideoPlayerController] 接口与 commonMain 解耦
 * - 生命周期：DisposableEffect 管理播放器资源的创建与释放
 *
 * ## 全屏架构
 * 全屏切换由 [DesktopVideoPlayerController.setFullscreen] 内部调用
 * [LocalFullscreenController] 实现，UI 层不再手动管理全屏状态。
 * 符合依赖倒置原则：端侧上层只管下发指令，具体实现下放到控制器。
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
    onFullScreenChange: ((Boolean) -> Unit)?,
    onManagerCreated: ((VideoPlayerManager) -> Unit)?,
) {
    val fullscreenController = LocalFullscreenController.current

    // ========== 从 Koin 获取（由 DI 管理生命周期） ==========
    // 通过 parametersOf 将 CompositionLocal 中的 FullscreenController 传入构造函数
    val controller: IVideoPlayerController =
        koinInject(parameters = { parametersOf(fullscreenController) })
    val proxyServer: CacheProxyServer = koinInject()
    val segmentCacheTracker: SegmentCacheTracker = koinInject()

    val manager = remember(controller, proxyServer, segmentCacheTracker) {
        VideoPlayerManager(
            controller = controller,
            proxyServer = proxyServer,
            segmentCacheTracker = segmentCacheTracker,
        )
    }

    // 通知上层 Manager 已创建，用于注入剧集上下文等
    LaunchedEffect(manager) {
        onManagerCreated?.invoke(manager)
    }

    val uiState by manager.uiState.collectAsState()
    val keyActionHandler: (PlayerKeyAction) -> Unit = { action ->
        // 仅在播放器处于活跃状态时响应（非 IDLE、非 ERROR）
        if (uiState.playbackState != VideoPlaybackState.IDLE &&
            uiState.playbackState != VideoPlaybackState.ERROR
        ) {
            when (action) {
                PlayerKeyAction.TogglePlayPause -> handlePlayerAction(manager, PlayerAction.TogglePlayPause)
                PlayerKeyAction.SeekForward -> {
                    // 每次快进 10 秒
                    manager.seekForward(10)
                }
                PlayerKeyAction.SeekBackward -> {
                    // 每次快退 10 秒
                    manager.seekBackward(10)
                }
                PlayerKeyAction.VolumeUp -> {
                    // 每次增大 10%，最大 100%
                    val newVolume = (uiState.volume + 0.1f).coerceAtMost(1.0f)
                    manager.setVolume(newVolume)
                }
                PlayerKeyAction.VolumeDown -> {
                    // 每次减小 10%，最小 0%
                    val newVolume = (uiState.volume - 0.1f).coerceAtLeast(0.0f)
                    manager.setVolume(newVolume)
                }
                PlayerKeyAction.ExitFullscreen -> {
                    // 全屏时退出全屏，非全屏时不处理（避免误触退出播放页面）
                    if (uiState.isFullScreen) {
                        handlePlayerAction(manager, PlayerAction.ToggleFullScreen)
                        // 同步通知 App.jvm.kt 更新 isFullScreen 状态，使左侧导航栏重新显示
                        onFullScreenChange?.invoke(false)
                    }
                }
            }
        }
    }

    // 同步写入桥接器，供 AWT 事件线程读取
    PlayerKeyActionBridge.handler.set(keyActionHandler)

    CompositionLocalProvider(LocalPlayerKeyActionHandler provides keyActionHandler) {
        SharedVideoPlayerScreen(
            url = url,
            title = title,
            onBack = onBack,
            headers = headers,
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
