package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import java.awt.Dimension
import javax.swing.JPanel

/**
 * VLCJ 视频渲染表面 — 基于 EmbeddedMediaPlayerComponent 的原生视频输出。
 *
 * 使用 [DesktopVideoPlayerController.videoSurfaceComponent]（[JPanel]）作为视频渲染容器，
 * 通过 Compose 的 [SwingPanel] 嵌入到 Compose Desktop 布局中。
 *
 * ## 架构优势
 * - **不依赖 `sun.misc.Unsafe`**：使用 VLC 原生 AWT 视频输出管道
 * - **硬件加速**：VLC 内部使用 OpenGL/VideoToolbox/DirectX
 * - **跨平台兼容**：EmbeddedMediaPlayerComponent 内部自动处理平台差异
 * - **性能更好**：无需帧数据拷贝和转换
 *
 * ## 渲染流程
 * ```
 * VLCJ EmbeddedMediaPlayerComponent (JPanel)
 *     → 内部 ComponentVideoSurface
 *     → VLC 原生渲染管道（硬件加速）
 *     → SwingPanel → Compose Desktop 布局
 * ```
 *
 * @param controller VLCJ 播放器控制器
 * @param modifier Compose Modifier
 */
@Composable
fun VlcjVideoSurface(
    controller: DesktopVideoPlayerController,
    modifier: Modifier = Modifier,
) {
    val videoSurfaceComponent = remember { controller.videoSurfaceComponent }

    // 组件卸载时释放视频表面
    DisposableEffect(controller) {
        onDispose {
            // SwingPanel 移除时会自动处理子组件
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                videoSurfaceComponent.apply {
                    // 设置最小尺寸
                    minimumSize = Dimension(1, 1)
                    preferredSize = Dimension(1, 1)
                }
            },
            update = {
                // SwingPanel 会自动调整大小
            }
        )
    }
}
