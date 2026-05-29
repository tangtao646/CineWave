package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.kmp_demo.core.player.domain.VideoPlayerManager
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState

/**
 * 视频播放器控制栏组件
 *
 * 包含：播放/暂停、快进/快退、进度条、时间显示、音量控制、选集按钮、全屏切换
 * 纯 UI 组件，所有状态通过 [state] 传入，事件通过 [onAction] 回调上报。
 *
 * 控制栏元素的显隐策略：
 * - **桌面端**：所有控制元素始终展示（播放控制、音量控制、选集按钮、全屏切换）
 * - **移动端竖屏**：隐藏音量控制和选集按钮（空间紧凑），仅保留播放控制和全屏切换
 * - **移动端横屏**：展示所有控制元素
 *
 * 竖屏状态通过 [LocalIsPortrait] CompositionLocal 获取，由各平台层设置。
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
    // 记住上一次非静音的音量值，用于静音切换
    var previousVolume by remember { mutableStateOf(1.0f) }

    // 竖屏模式下隐藏音量控制和选集按钮（移动端空间紧凑）
    val isPortrait = LocalIsPortrait.current
    val effectiveShowVolumeControl = !isPortrait
    val effectiveShowEpisodeSelector = showEpisodeSelector && !isPortrait



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
            // 进度条（带缓存区域标记）
            VideoProgressSlider(
                progress = state.progressFraction,
                bufferedPercent = state.bufferedPercent,
                onSeek = { fraction -> onAction(PlayerAction.SeekToFraction(fraction)) },
                colors = sliderColor,
                state = state,  // 传入状态以获取缓存信息
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 底部按钮行
            // 使用 Box + Alignment.Center 确保播放控制按钮组始终水平居中，
            // 不受左右两侧内容（时间、音量、选集、全屏）宽度变化的影响。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                // 左侧：时间显示 + 音量控制
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.currentPositionText} / ${state.durationText}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    if (effectiveShowVolumeControl) {
                        Spacer(modifier = Modifier.width(8.dp))
                        VolumeControl(
                            volume = state.volume,
                            onVolumeChange = { volume ->
                                onAction(PlayerAction.SetVolume(volume))
                            },
                            onToggleMute = {
                                if (state.volume > 0f) {
                                    previousVolume = state.volume
                                    onAction(PlayerAction.SetVolume(0f))
                                } else {
                                    onAction(PlayerAction.SetVolume(previousVolume))
                                }
                            },
                        )
                    }
                }

                // 居中：快退 — 播放/暂停 — 快进（始终水平居中）
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showSeekButtons) {
                        PlayerIconButton(
                            icon = Icons.Default.Replay10,
                            contentDescription = "快退 10 秒",
                            onClick = { onAction(PlayerAction.SeekBackward(10)) }
                        )
                    }

                    PlayerIconButton(
                        icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        iconSize = 32.dp,
                        onClick = { onAction(PlayerAction.TogglePlayPause) }
                    )

                    if (showSeekButtons) {
                        PlayerIconButton(
                            icon = Icons.Default.Forward10,
                            contentDescription = "快进 10 秒",
                            onClick = { onAction(PlayerAction.SeekForward(10)) }
                        )
                    }
                }

                // 右侧：选集按钮 + 全屏切换
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (effectiveShowEpisodeSelector && onEpisodeSelectorClick != null) {
                        PlayerTextButton(
                            label = "选集",
                            onClick = onEpisodeSelectorClick,
                        )
                    }

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
 * 视频进度条 — 带缓存区域标记。
 *
 * 在进度条上绘制已缓存/未缓存的切片区域：
 * -  红色半透明区域 = 已缓存到本地磁盘，拖动到这里秒播
 * - ⬜ 灰色区域 = 未缓存，需要网络加载
 *
 * 参考市面上的加速播放器（如 PotPlayer、IINA、Infuse）的缓存可视化设计。
 */
@Composable
private fun VideoProgressSlider(
    progress: Float,
    bufferedPercent: Int,
    onSeek: (Float) -> Unit,
    colors: SliderColors,
    state: VideoPlayerUiState,  // 新增：用于获取缓存状态
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else progress

    var sliderWidth by remember { mutableStateOf(0f) }

    // 缓存区域颜色
    val cachedColor = Color(0x66FF4444)  // 半透明红色
    val uncachedColor = Color(0x33FFFFFF) // 半透明白色

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged {
                sliderWidth = it.width.toFloat()
            }
            .pointerInput(sliderWidth) {
                detectTapGestures { offset ->
                    if (sliderWidth > 0f) {
                        val fraction = (offset.x / sliderWidth).coerceIn(0f, 1f)
                        onSeek(fraction)
                    }
                }
            }
    ) {
        // 缓存状态标记层（在 Slider 下方绘制）
        if (state.cachedSegments.isNotEmpty() && state.duration > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val trackHeight = 4.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2f
                val totalDuration = state.duration.toFloat()

                // 绘制每个切片的缓存状态
                state.cachedSegments.forEach { segment ->
                    val startFraction = segment.startMs.toFloat() / totalDuration
                    val widthFraction = segment.durationMs.toFloat() / totalDuration

                    val left = startFraction * size.width
                    val segWidth = widthFraction * size.width

                    // 跳过宽度为 0 的切片
                    if (segWidth <= 0f) return@forEach

                    val color = if (segment.isCached) cachedColor else uncachedColor

                    // 绘制缓存标记条
                    drawRect(
                        color = color,
                        topLeft = Offset(left, trackTop),
                        size = Size(segWidth, trackHeight),
                    )
                }
            }
        }

        // Material3 Slider
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
 * 音量控制组件 — 横向滑块 + 音量图标 + 百分比显示。
 *
 * 布局：音量图标 | 百分比文本 | 横向滑块
 * - 点击图标切换静音/恢复
 * - 拖动滑块调节音量
 * - 滑块宽度固定，不占用过多空间
 *
 * @param volume 当前音量 0.0 ~ 1.0
 * @param onVolumeChange 音量变化回调
 * @param onToggleMute 静音切换回调
 */
@Composable
private fun VolumeControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(140.dp)
    ) {
        // 音量图标（点击切换静音）
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = getVolumeIcon(volume),
                contentDescription = if (volume > 0f) "静音" else "取消静音",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // 百分比文本
        Text(
            text = "${(volume * 100).toInt()}%",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
            maxLines = 1,
        )

        // 音量滑块
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .width(72.dp)
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFFF4444),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

/**
 * 根据音量值返回对应的图标。
 *
 * - volume == 0f → VolumeOff（静音）
 * - volume < 0.5f → VolumeDown（低音量）
 * - volume >= 0.5f → VolumeUp（高音量）
 */
private fun getVolumeIcon(volume: Float): ImageVector {
    return when {
        volume <= 0f -> Icons.Default.VolumeOff
        volume < 0.5f -> Icons.Default.VolumeDown
        else -> Icons.Default.VolumeUp
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
    /** 设置音量 0.0 ~ 1.0 */
    data class SetVolume(val volume: Float) : PlayerAction
    /** 切换静音（在 0 和上次音量之间切换） */
    data object ToggleMute : PlayerAction
    /** 清除磁盘缓存 */
    data object ClearCache : PlayerAction
}

/**
 * 统一的播放器动作处理函数，供各平台 Composable 调用。
 *
 * 消除 Android 端 [handlePlayerAction] 与 Desktop 端 [handleDesktopPlayerAction]
 * 之间的重复代码。各平台只需调用此函数即可。
 *
 * ## 平台差异说明
 * - [PlayerAction.TogglePip]：Android 使用 ExoPlayer 原生实现，Desktop 不支持，此处均为空操作
 * - [PlayerAction.ClearCache]：Android 使用 SimpleCache，Desktop 不使用磁盘缓存，此处均为空操作
 *
 * @param manager 播放器管理器，所有操作通过它委托给底层 [IVideoPlayerController]
 * @param action 要执行的动作
 */
internal fun handlePlayerAction(
    manager: VideoPlayerManager,
    action: PlayerAction,
) {
    when (action) {
        PlayerAction.TogglePlayPause -> manager.togglePlayPause()
        is PlayerAction.SeekForward -> manager.seekForward(action.seconds)
        is PlayerAction.SeekBackward -> manager.seekBackward(action.seconds)
        is PlayerAction.SeekToFraction -> manager.seekToFraction(action.fraction)
        is PlayerAction.SeekToMs -> manager.seekTo(action.positionMs)
        PlayerAction.ToggleFullScreen -> manager.toggleFullScreen()
        PlayerAction.TogglePip -> { /* 平台自行决定是否支持画中画 */ }
        PlayerAction.ToggleControls -> manager.toggleControls()
        is PlayerAction.SetVolume -> manager.setVolume(action.volume)
        PlayerAction.ToggleMute -> {
            val currentVolume = manager.uiState.value.volume
            if (currentVolume > 0f) {
                manager.setVolume(0f)
            } else {
                manager.setVolume(1.0f)
            }
        }
        PlayerAction.ClearCache -> { /* 平台自行决定缓存清理策略 */ }
    }
}
