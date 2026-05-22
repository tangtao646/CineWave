package com.example.kmp_demo.core.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kmp_demo.core.videosource.domain.VideoSource

/**
 * Android 端播放源列表 — 纵向列表布局。
 * 手机屏幕窄，纵向列表更易点击和阅读。
 */
@Composable
actual fun VideoSourceListContent(
    videoSources: List<VideoSource>,
    isSniffing: Boolean,
    onPlayClick: (VideoSource) -> Unit,
) {
    if (isSniffing) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else if (videoSources.isEmpty()) {
        Text(
            text = "未找到可用资源",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    } else {
        videoSources.forEach { source ->
            VideoSourceListItem(
                source = source,
                onPlayClick = { onPlayClick(source) },
            )
        }
    }
}

@Composable
private fun VideoSourceListItem(
    source: VideoSource,
    onPlayClick: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "源: ${source.sourceSite}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${source.quality} • ${source.format.uppercase()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = source.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(source.url))
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onPlayClick) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
