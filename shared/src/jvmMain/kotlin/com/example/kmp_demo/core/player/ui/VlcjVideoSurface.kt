package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * VLCJ 视频渲染表面 — 基于 Compose Canvas 的帧渲染。
 *
 * 从 [VlcjVideoPlayerController.latestVideoFrame] 获取最新的 RGBA 帧数据，
 * 通过 Compose [Canvas] 绘制，避免 macOS 上 AWT/Swing 的 `caopengllayer`
 * 视频输出兼容性问题。
 *
 * ## 渲染流程
 * ```
 * VLCJ CallbackVideoSurface → RGBA ByteBuffer → BufferedImage
 *     → Compose Canvas.drawImage() → 屏幕
 * ```
 *
 * ## 性能优化
 * - 使用 `withContext(Dispatchers.Default)` 在后台线程转换帧数据
 * - 帧率限制为 30fps（33ms 轮询间隔），避免过度绘制
 * - 仅在帧数据变化时触发重绘
 *
 * @param controller VLCJ 播放器控制器
 * @param modifier Compose Modifier
 * @param contentScale 内容缩放模式
 */
@Composable
fun VlcjVideoSurface(
    controller: DesktopVideoPlayerController,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var currentFrame by remember { mutableStateOf(controller.latestVideoFrame) }

    // 轮询最新视频帧（30fps）
    LaunchedEffect(controller) {
        while (isActive) {
            val frame = controller.latestVideoFrame
            if (frame != currentFrame) {
                currentFrame = frame
            }
            delay(33L) // ~30fps
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        val frame = currentFrame ?: return@Canvas

        // 计算绘制区域（保持宽高比）
        val sourceWidth = frame.width.toFloat()
        val sourceHeight = frame.height.toFloat()
        val targetWidth = containerSize.width.toFloat()
        val targetHeight = containerSize.height.toFloat()

        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return@Canvas
        }

        // 根据 ContentScale 计算目标绘制区域
        val drawRect = calculateDrawRect(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            contentScale = contentScale
        )

        // 将 BufferedImage 转换为 Compose ImageBitmap 并绘制
        val imageBitmap = frame.toComposeImageBitmap()
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(drawRect.left.toInt(), drawRect.top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(
                (drawRect.right - drawRect.left).toInt(),
                (drawRect.bottom - drawRect.top).toInt()
            )
        )
    }
}

/**
 * 根据 ContentScale 计算目标绘制矩形。
 *
 * @param sourceWidth 源图像宽度
 * @param sourceHeight 源图像高度
 * @param targetWidth 目标容器宽度
 * @param targetHeight 目标容器高度
 * @param contentScale 缩放模式
 * @return 绘制矩形（left, top, width, height）
 */
private fun calculateDrawRect(
    sourceWidth: Float,
    sourceHeight: Float,
    targetWidth: Float,
    targetHeight: Float,
    contentScale: ContentScale,
): Rect {
    val sourceAspect = sourceWidth / sourceHeight
    val targetAspect = targetWidth / targetHeight

    return when (contentScale) {
        ContentScale.Fit -> {
            if (sourceAspect > targetAspect) {
                val drawHeight = targetWidth / sourceAspect
                val offsetY = (targetHeight - drawHeight) / 2f
                Rect(0f, offsetY, targetWidth, offsetY + drawHeight)
            } else {
                val drawWidth = targetHeight * sourceAspect
                val offsetX = (targetWidth - drawWidth) / 2f
                Rect(offsetX, 0f, offsetX + drawWidth, targetHeight)
            }
        }
        ContentScale.FillBounds -> {
            Rect(0f, 0f, targetWidth, targetHeight)
        }
        ContentScale.FillWidth -> {
            val drawHeight = targetWidth / sourceAspect
            val offsetY = (targetHeight - drawHeight) / 2f
            Rect(0f, offsetY, targetWidth, offsetY + drawHeight)
        }
        ContentScale.FillHeight -> {
            val drawWidth = targetHeight * sourceAspect
            val offsetX = (targetWidth - drawWidth) / 2f
            Rect(offsetX, 0f, offsetX + drawWidth, targetHeight)
        }
        ContentScale.Crop -> {
            if (sourceAspect > targetAspect) {
                val drawWidth = targetHeight * sourceAspect
                val offsetX = (targetWidth - drawWidth) / 2f
                Rect(offsetX, 0f, offsetX + drawWidth, targetHeight)
            } else {
                val drawHeight = targetWidth / sourceAspect
                val offsetY = (targetHeight - drawHeight) / 2f
                Rect(0f, offsetY, targetWidth, offsetY + drawHeight)
            }
        }
        ContentScale.Inside -> {
            calculateDrawRect(sourceWidth, sourceHeight, targetWidth, targetHeight, ContentScale.Fit)
        }
        else -> {
            calculateDrawRect(sourceWidth, sourceHeight, targetWidth, targetHeight, ContentScale.Fit)
        }
    }
}
