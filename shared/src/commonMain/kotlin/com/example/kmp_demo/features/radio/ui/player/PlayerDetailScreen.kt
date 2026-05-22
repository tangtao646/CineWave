package com.example.kmp_demo.features.radio.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.player.RadioPlayerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    playerManager: RadioPlayerManager,
    onClose: () -> Unit
) {
    val uiState by playerManager.uiState.collectAsState()
    val currentStation = uiState.currentStation
    val isPlaying = uiState.isPlaying
    val isBuffering = uiState.playbackState == AppPlaybackState.BUFFERING
    val sleepTimerRemaining = uiState.sleepTimerRemaining

    var showTimerSheet by remember { mutableStateOf(false) }

    // 按钮边缘旋转动画
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

    // 动态色彩状态（使用固定颜色，避免 Android Palette 依赖）
    var backgroundColor by remember { mutableStateOf(Color(0xFF2B2B2B)) }
    val animatedBgColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(1000),
        label = "BgColor"
    )

    if (currentStation == null) return

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

    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
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
                RotatingDisk(
                    imageUrl = currentStation.favicon,
                    isRotating = isPlaying
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = uiState.trackTitle ?: currentStation.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = currentStation.tags.joinToString(" | "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 睡眠定时器
            if (sleepTimerRemaining != null) {
                AssistChip(
                    onClick = { showTimerSheet = true },
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
                IconButton(onClick = { showTimerSheet = true }) {
                    Icon(Icons.Default.Timer, contentDescription = "Set Timer", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 播放控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playerManager.playPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp)
                    )
                }

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
                        containerColor = if (isBuffering) MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.1f
                        ) else MaterialTheme.colorScheme.primary
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
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { playerManager.playNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        if (showTimerSheet) {
            ModalBottomSheet(onDismissRequest = { showTimerSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "设置定时关闭",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                    val options = listOf(0, 15, 30, 60, 90)
                    options.forEach { mins ->
                        ListItem(
                            headlineContent = { Text(if (mins == 0) "不开启" else "${mins} 分钟后") },
                            modifier = Modifier.clickable {
                                playerManager.setSleepTimer(mins)
                                showTimerSheet = false
                            }
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun RotatingDisk(
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
            .background(Color.Black)
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color.DarkGray, style = Stroke(width = 1f))
        }

        // Coil 3 KMP: 直接传 URL，无需 ImageRequest.Builder(LocalContext.current)
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentRotation)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .align(Alignment.Center)
        )
    }
}
