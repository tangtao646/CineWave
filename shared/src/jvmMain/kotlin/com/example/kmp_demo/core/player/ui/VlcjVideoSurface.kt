package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController

/**
 * VLCJ 视频渲染表面 — 基于 CallbackVideoSurface 的 Compose 原生渲染。
 *
 * 通过订阅 [DesktopVideoPlayerController.videoFrame] 获取 VLC 渲染的每一帧，
 * 并使用 Compose 的 [Image] 组件进行绘制。
 *
 * ## 架构优势
 * - **完美解决 macOS 兼容性**：不再依赖窗口叠加，视频完全属于 Compose 渲染树。
 * - **支持 UI 覆盖**：可以在视频上方显示任何 Compose 组件（如进度条、弹窗、字幕）。
 * - **一致的生命周期**：视频帧随 Composable 自动更新和释放。
 * - **跨平台表现一致**：在 Windows/Linux/macOS 上均表现稳定。
 *
 * @param controller VLCJ 播放器控制器
 * @param modifier Compose Modifier
 */
@Composable
fun VlcjVideoSurface(
    controller: DesktopVideoPlayerController,
    modifier: Modifier = Modifier,
) {
    val videoFrame by controller.videoFrame.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        videoFrame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = "Video Stream",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
