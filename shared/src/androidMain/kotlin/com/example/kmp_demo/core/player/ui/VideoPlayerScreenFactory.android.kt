package com.example.kmp_demo.core.player.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import com.example.kmp_demo.core.player.cache.SegmentCacheTracker
import com.example.kmp_demo.core.player.domain.CacheOrchestrator
import com.example.kmp_demo.core.player.domain.IVideoPlayerController
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.player.domain.ShareUrlResolver
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.ExoPlayerController
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Android 平台统一的视频播放器屏幕实现。
 *
 * 重构后职责：
 * 1. 创建 [VideoPlayerManager]（组合 [CacheOrchestrator]）
 * 2. 通过 DI 获取 [ShareUrlResolver]，在 LaunchedEffect 中解析 URL
 * 3. LaunchedEffect(url) 中调用 manager.open()（副作用集中在此层）
 * 4. 调用 [SharedVideoPlayerScreen]（纯 UI）
 *
 * ## 缓存架构
 * 使用 ExoPlayer 原生 SimpleCache + CacheDataSource 方案，
 * 无需本地 HTTP 代理，无端口竞态，真正流式缓存。
 * [CacheOrchestrator] 在此处仅用于切片追踪，不启动代理服务器。
 *
 * ## 全屏架构
 * 全屏切换由 [ExoPlayerController.setFullscreen] 内部调用
 * [LocalFullscreenController] 实现，UI 层不再手动管理全屏状态。
 *
 * @param url 视频播放地址
 * @param title 视频标题
 * @param onBack 返回回调
 * @param headers 自定义请求头
 * @param controls 自定义控制栏
 * @param onFullScreenChange 全屏状态变化回调
 * @param onManagerCreated 当 [VideoPlayerManager] 创建完成后的回调
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
    val controller: IVideoPlayerController = koinInject(parameters = { parametersOf(fullscreenController) })
    val segmentCacheTracker: SegmentCacheTracker = koinInject()
    val shareUrlResolver: ShareUrlResolver = koinInject()
    val httpClient: HttpClient = koinInject()

    // ========== 创建 CacheOrchestrator（Android 无代理，仅用于切片追踪） ==========
    val cacheOrchestrator = remember(segmentCacheTracker, httpClient) {
        CacheOrchestrator(
            proxyServer = null,          // Android 使用 SimpleCache，无需代理
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

    // ========== 重试触发器：观察 retryTrigger 变化 ==========
    val retryTrigger by manager.retryTrigger.collectAsState()

    // ========== 副作用集中在此：解析 URL + 打开视频 ==========
    LaunchedEffect(url, headers, retryTrigger) {
        val resolvedUrl = shareUrlResolver.resolve(url, headers)
        manager.open(resolvedUrl, headers)
    }

    // ========== 横竖屏检测 ==========
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
            manager = manager,
            controls = controls,
            onFullScreenChange = onFullScreenChange,
            // ========== 平台插槽：视频 Surface ==========
            videoSurface = { modifier ->
                VideoPlayerSurface(
                    player = (controller as ExoPlayerController).player,
                    modifier = modifier,
                )
            },
            // ========== 平台插槽：资源释放 ==========
            onPlatformDispose = {
                fullscreenController.exitFullscreen()
                segmentCacheTracker.release()
                // 注意：ExoPlayerCache(SimpleCache) 由 Koin single{} 管理，此处不释放
            },
        )
    }
}
