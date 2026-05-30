package com.example.kmp_demo.core.player.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_demo.core.player.domain.PlayerError
import com.example.kmp_demo.core.player.domain.PlayerErrorType

/**
 * 播放器错误展示组件
 *
 * 符合主流设计标准（参考 Netflix、YouTube、Bilibili 等主流播放器的错误页面）：
 * - 居中布局，视觉焦点在错误图标上
 * - 清晰的错误消息，用户能理解发生了什么
 * - 可重试的错误显示"重试"按钮，不可重试的错误仅展示信息
 * - 平滑的淡入动画
 * - 半透明黑色背景层，不遮挡视频画面
 *
 * 注意：错误弹框不包含"返回"按钮，返回操作由页面级别的顶栏处理。
 *
 * ## 错误类型与展示策略
 *
 * | 错误类型 | 图标 | 消息 | 操作 |
 * |---------|------|------|------|
 * | SOURCE_NOT_FOUND | 搜索/文件缺失 | "视频资源不存在" | 无（不可重试） |
 * | FORBIDDEN | 锁定 | "视频资源访问受限" | 无（不可重试） |
 * | NETWORK_ERROR | 信号断开 | "网络连接失败" | 重试 |
 * | TIMEOUT | 时钟 | "连接超时" | 重试 |
 * | FORMAT_ERROR | 文件损坏 | "视频格式不支持" | 无（不可重试） |
 * | DRM_ERROR | 安全 | "DRM 解密失败" | 无（不可重试） |
 * | UNKNOWN | 警告 | "播放出错" | 重试 |
 *
 * @param playerError 错误信息
 * @param onRetry 重试回调（仅可重试的错误显示）
 * @param modifier 修饰符
 */
@Composable
fun ErrorDisplay(
    playerError: PlayerError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 淡入动画
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(playerError) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            // 错误内容卡片
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 错误图标（带圆形背景）
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x33FF4444),
                                    Color(0x11FF4444),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = getErrorIcon(playerError.type),
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 错误标题
                Text(
                    text = playerError.message,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                // 错误详情（如果有）
                if (playerError.detail != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playerError.detail,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }

                // HTTP 状态码（如果有）
                if (playerError.httpStatusCode != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HTTP ${playerError.httpStatusCode}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 重试按钮（仅可重试的错误显示）
                if (playerError.retryable && onRetry != null) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4444),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重试", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * 根据错误类型返回对应的 Material Icons 图标。
 *
 * 设计原则：图标应直观传达错误性质，让用户无需阅读文字就能大致理解问题类型。
 */
private fun getErrorIcon(type: PlayerErrorType): ImageVector {
    return when (type) {
        PlayerErrorType.SOURCE_NOT_FOUND -> Icons.Default.SearchOff
        PlayerErrorType.FORBIDDEN -> Icons.Default.Lock
        PlayerErrorType.NETWORK_ERROR -> Icons.Default.SignalWifiOff
        PlayerErrorType.TIMEOUT -> Icons.Default.AccessTime
        PlayerErrorType.FORMAT_ERROR -> Icons.Default.BrokenImage
        PlayerErrorType.DRM_ERROR -> Icons.Default.Security
        PlayerErrorType.UNKNOWN -> Icons.Default.Warning
    }
}
