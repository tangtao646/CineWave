package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState

/**
 * 视频播放器控制栏组件
 *
 * 包含：播放/暂停、快进/快退、进度条、时间显示、选集按钮、全屏切换
 * 纯 UI 组件，所有状态通过 [state] 传入，事件通过 [onAction] 回调上报。
 *
 * @param showEpisodeSelector 是否显示选集按钮（剧集模式为 true，电影模式为 false）
 * @param currentEpisodeLabel 当前剧集标签，如"第3集"，显示在选集按钮上
 * @param onEpisodeSelectorClick 选集按钮点击回调
 */
@Composable
fun VideoPlayerControls(
    state: VideoPlayerUiState,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    showFullScreenButton: Boolean = true,
    showSeekButtons: Boolean = true,
    showEpisodeSelector: Boolean = false,
    currentEpisodeLabel: String? = null,
    onEpisodeSelectorClick: (() -> Unit)? = null,
    sliderColor: SliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color(0xFFFF4444),
        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
    )
) {
    Box(modifier = modifier) {
        // 底部控制栏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 进度条
            VideoProgressSlider(
                progress = state.progressFraction,
                bufferedPercent = state.bufferedPercent,
                onSeek = { fraction -> onAction(PlayerAction.SeekToFraction(fraction)) },
                colors = sliderColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 底部按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时间显示
                Text(
                    text = "${state.currentPositionText} / ${state.durationText}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // 快退
                if (showSeekButtons) {
                    PlayerIconButton(
                        icon = Icons.Default.Replay10,
                        contentDescription = "快退 10 秒",
                        onClick = { onAction(PlayerAction.SeekBackward(10)) }
                    )
                }

                // 播放/暂停
                PlayerIconButton(
                    icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    iconSize = 32.dp,
                    onClick = { onAction(PlayerAction.TogglePlayPause) }
                )

                // 快进
                if (showSeekButtons) {
                    PlayerIconButton(
                        icon = Icons.Default.Forward10,
                        contentDescription = "快进 10 秒",
                        onClick = { onAction(PlayerAction.SeekForward(10)) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 选集按钮（位于全屏按钮左侧）
                if (showEpisodeSelector && onEpisodeSelectorClick != null) {
                    PlayerTextButton(
                        label = "选集",
                        onClick = onEpisodeSelectorClick,
                    )
                }

                // 全屏切换
                if (showFullScreenButton) {
                    PlayerIconButton(
                        icon = if (state.isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (state.isFullScreen) "退出全屏" else "全屏",
                        onClick = { onAction(PlayerAction.ToggleFullScreen) }
                    )
                }
            }
        }
    }
}

/**
 * 中央播放/暂停按钮（带缓冲动画）
 */
@Composable
internal fun CenterPlayButton(
    state: VideoPlayerUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isBuffering -> {
            // 缓冲中显示加载指示器
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = modifier
                    .size(48.dp)
                    .semantics { testTag = "BufferingIndicator" }
            )
        }

        !state.isPlaying -> {
            // 暂停/停止状态显示播放按钮
            FilledIconButton(
                onClick = onClick,
                modifier = modifier
                    .size(64.dp)
                    .clip(CircleShape),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/**
 * 视频进度条
 */
@Composable
private fun VideoProgressSlider(
    progress: Float,
    bufferedPercent: Int,
    onSeek: (Float) -> Unit,
    colors: SliderColors
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else progress

    var sliderWidth by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { sliderWidth = it.width.toFloat() }
            .pointerInput(sliderWidth) {
                detectTapGestures { offset ->
                    if (sliderWidth > 0f) {
                        val fraction = (offset.x / sliderWidth).coerceIn(0f, 1f)
                        onSeek(fraction)
                    }
                }
            }
    ) {
        Slider(
            value = displayProgress,
            onValueChange = { dragProgress = it; isDragging = true },
            onValueChangeFinished = {
                isDragging = false
                onSeek(dragProgress)
            },
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "VideoProgressSlider" }
        )
    }
}

/**
 * 播放器图标按钮
 */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 播放器文字按钮（用于选集等场景）。
 *
 * 显示为带圆角背景的标签文本，比图标按钮更直观地表达当前剧集信息。
 */
@Composable
private fun PlayerTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/**
 * 播放器 UI 动作事件
 */
sealed interface PlayerAction {
    data object TogglePlayPause : PlayerAction
    data class SeekForward(val seconds: Long) : PlayerAction
    data class SeekBackward(val seconds: Long) : PlayerAction
    data class SeekToFraction(val fraction: Float) : PlayerAction
    data class SeekToMs(val positionMs: Long) : PlayerAction
    data object ToggleFullScreen : PlayerAction
    data object TogglePip : PlayerAction
    data object ToggleControls : PlayerAction
    /** 清除磁盘缓存 */
    data object ClearCache : PlayerAction
}
