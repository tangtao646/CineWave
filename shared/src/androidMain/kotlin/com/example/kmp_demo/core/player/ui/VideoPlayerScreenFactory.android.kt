package com.example.kmp_demo.core.player.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.ExoPlayerController
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState

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
actual fun PlatformVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>?,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit,
    onFullScreenChange: ((Boolean) -> Unit)?,
) {
    val fullscreenController = LocalFullscreenController.current

    // ========== 从 Koin 获取（由 DI 管理生命周期） ==========
    val controller: ExoPlayerController = koinInject()
    val segmentCacheTracker: SegmentCacheTracker = koinInject()

    // ========== 播放器管理器（无代理服务器） ==========
    val manager = remember(controller, segmentCacheTracker) {
        VideoPlayerManager(
            controller = controller,
            proxyServer = null,          // Android 使用 SimpleCache，无需代理
            segmentCacheTracker = segmentCacheTracker,
        )
    }

    // ========== 横竖屏检测 ==========
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // ========== 全屏处理 ==========
    LaunchedEffect(manager.uiState.collectAsState().value.isFullScreen) {
        if (manager.uiState.value.isFullScreen) {
            fullscreenController.enterFullscreen()
        } else {
            fullscreenController.exitFullscreen()
        }
    }

    // ========== 返回键处理 ==========
    BackHandler {
        if (manager.uiState.value.isFullScreen) {
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

    // ========== 通过 CompositionLocalProvider 传递横竖屏状态 ==========
    CompositionLocalProvider(LocalIsPortrait provides isPortrait) {
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
                VideoPlayerSurface(
                    player = controller.player,
                    modifier = modifier,
                )
            },
            // ========== 平台插槽：修饰符 ==========
            topBarModifier = Modifier.windowInsetsPadding(
                WindowInsets.statusBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            ),
            bottomBarModifier = Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            ),
            // ========== 平台插槽：资源释放 ==========
            onPlatformDispose = {
                fullscreenController.exitFullscreen()
                segmentCacheTracker.release()
                // 注意：ExoPlayerCache(SimpleCache) 由 Koin single{} 管理，此处不释放
            },
        )
    }
}
