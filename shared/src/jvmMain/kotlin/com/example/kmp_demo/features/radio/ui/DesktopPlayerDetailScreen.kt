package com.example.kmp_demo.features.radio.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.player.RadioPlayerManager

/**
 * 桌面端适配的电台播放器详情页。
 *
 * - ✅ 支持 ESC 键关闭页面
 * - ✅ 适配桌面端窗口布局，全屏沉浸式体验
 * - ✅ 关闭按钮位置使用标准 padding，不依赖系统栏
 */
@Composable
fun DesktopPlayerDetailScreen(
    playerManager: RadioPlayerManager,
    onClose: () -> Unit
) {
    val uiState by playerManager.uiState.collectAsState()
    val currentStation = uiState.currentStation
    val isPlaying = uiState.isPlaying
    val isBuffering = uiState.playbackState == AppPlaybackState.BUFFERING
    val sleepTimerRemaining = uiState.sleepTimerRemaining

    var showTimerMenu by remember { mutableStateOf(false) }

    // 按钮边缘旋转动画（缓冲时）
    val infiniteTransition = rememberInfiniteTransition(label = "BufferingDetail")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationDetail"
    )

    // 动态背景色
    var backgroundColor by remember { mutableStateOf(Color(0xFF2B2B2B)) }
    val animatedBgColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(1000),
        label = "BgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedBgColor.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            // 支持 ESC 键关闭
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
    ) {
        if (currentStation == null) {
            // 没有电台时显示提示
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "请选择一个电台开始播放",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        } else {
            // 关闭按钮 — 使用标准 padding，不依赖 statusBarsPadding
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 旋转唱片
                Box(contentAlignment = Alignment.Center) {
                    DesktopRotatingDisk(
                        imageUrl = currentStation.favicon,
                        isRotating = isPlaying
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // 电台名称
                Text(
                    text = uiState.trackTitle ?: currentStation.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // 标签
                Text(
                    text = currentStation.tags.joinToString(" | "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 睡眠定时器 — 桌面端使用 DropdownMenu 替代 ModalBottomSheet
                Box {
                    if (sleepTimerRemaining != null) {
                        AssistChip(
                            onClick = { showTimerMenu = true },
                            label = { Text("定时关闭: ${sleepTimerRemaining}min") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    } else {
                        IconButton(onClick = { showTimerMenu = true }) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "设置定时关闭",
                                tint = Color.Gray
                            )
                        }
                    }

                    // 定时器选项下拉菜单
                    DropdownMenu(
                        expanded = showTimerMenu,
                        onDismissRequest = { showTimerMenu = false },
                        offset = DpOffset(0.dp, 4.dp)
                    ) {
                        val options = listOf(
                            0 to "不开启",
                            15 to "15 分钟后",
                            30 to "30 分钟后",
                            60 to "60 分钟后",
                            90 to "90 分钟后"
                        )
                        options.forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    playerManager.setSleepTimer(mins)
                                    showTimerMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // 播放控制
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 上一首
                    IconButton(
                        onClick = { playerManager.playPrevious() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "上一首",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // 播放/暂停按钮（带缓冲动画）
                    val primaryColor = MaterialTheme.colorScheme.primary
                    FilledIconButton(
                        onClick = { playerManager.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .drawBehind {
                                if (isBuffering) {
                                    rotate(rotation) {
                                        drawCircle(
                                            brush = Brush.sweepGradient(
                                                0f to Color.Transparent,
                                                0.5f to primaryColor,
                                                1f to Color.Transparent
                                            ),
                                            radius = size.minDimension / 2 + 4.dp.toPx(),
                                            style = Stroke(width = 4.dp.toPx())
                                        )
                                    }
                                }
                            },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isBuffering) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "播放/暂停",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // 下一首
                    IconButton(
                        onClick = { playerManager.playNext() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 桌面端旋转唱片组件。
 *
 * 与 [com.example.kmp_demo.features.radio.ui.player.RotatingDisk] 功能相同，
 * 但移除了对 Android 特定 API 的依赖，确保在 JVM 桌面端稳定运行。
 */
@Composable
fun DesktopRotatingDisk(
    imageUrl: String,
    isRotating: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DiskRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    val currentRotation = if (isRotating) rotation else 0f

    Box(
        modifier = Modifier
            .size(280.dp)
            .clip(CircleShape)
            .background(Color(0xFF1A1A1A)) // 深灰色背景，模拟唱片
            .padding(8.dp)
            .drawBehind {
                // 绘制唱片纹路
                val center = size.center
                val radius = size.minDimension / 2
                for (i in 1..8) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = radius * (i / 9f),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 使用 graphicsLayer 进行旋转，性能更优
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .graphicsLayer { rotationZ = currentRotation }
                .clip(CircleShape)
        ) {
            if (imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Default.Radio),
                    error = rememberVectorPainter(Icons.Default.Radio)
                )
            } else {
                // 如果没有图片，显示默认图标
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        }

        // 中心装饰圆（模拟唱片轴）
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )
            }
        }
    }
}
