package com.example.kmp_demo.features.radio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.player.RadioPlayerManager

@Composable
fun MiniPlayerBar(
    playerManager: RadioPlayerManager,
    onClick: () -> Unit
) {
    val uiState by playerManager.uiState.collectAsState()
    val currentStation = uiState.currentStation
    val isPlaying = uiState.isPlaying
    val isBuffering = uiState.playbackState == AppPlaybackState.BUFFERING

    if (currentStation == null) return

    // 按钮边缘旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "Buffering")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
           //.background(Color.White)
            .height(80.dp)
            //.padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        //shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationImage(
                url = currentStation.favicon,
                name = currentStation.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.trackTitle ?: currentStation.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = when {
                        isBuffering -> "正在连接..."
                        isPlaying -> "正在播放"
                        else -> "已暂停"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBuffering) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { playerManager.playPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val primaryColor = MaterialTheme.colorScheme.primary
                IconButton(
                    onClick = { playerManager.togglePlayPause() },
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            if (isBuffering) {
                                rotate(rotation) {
                                    drawCircle(
                                        brush = Brush.sweepGradient(
                                            0f to Color.Transparent,
                                            0.5f to primaryColor,
                                            1f to Color.Transparent
                                        ),
                                        radius = size.minDimension / 2 + 2.dp.toPx(),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                IconButton(onClick = { playerManager.playNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
