package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import com.example.kmp_demo.core.player.cache.CacheProxyServer
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
    val controller: IVideoPlayerController = koinInject(parameters = { parametersOf(fullscreenController) })
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

    // 通过 CompositionLocal 注册桌面端键盘动作处理器。
    // 由 DesktopKeyboardHandler 在捕获键盘事件时调用。
    // 使用 CompositionLocalProvider 确保离开播放器页面时自动恢复默认值。
    // 同时通过 PlayerKeyActionBridge 桥接到 AWT 事件线程。
    val uiState by manager.uiState.collectAsState()
    val keyActionHandler: (PlayerKeyAction) -> Unit = { action ->
        // 仅在播放器处于活跃状态时响应（非 IDLE、非 ERROR）
        if (uiState.playbackState != VideoPlaybackState.IDLE &&
            uiState.playbackState != VideoPlaybackState.ERROR
        ) {
            when (action) {
                PlayerKeyAction.TogglePlayPause -> handlePlayerAction(manager, PlayerAction.TogglePlayPause)
                PlayerKeyAction.SeekForward -> { /* 预留：快进 */ }
                PlayerKeyAction.SeekBackward -> { /* 预留：快退 */ }
                PlayerKeyAction.VolumeUp -> { /* 预留：增加音量 */ }
                PlayerKeyAction.VolumeDown -> { /* 预留：减小音量 */ }
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
