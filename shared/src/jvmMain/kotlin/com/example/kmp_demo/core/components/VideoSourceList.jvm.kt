package com.example.kmp_demo.core.components

import androidx.compose.foundation.clickable
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
 * Desktop 端播放源列表 — 网格布局（FlowRow）。
 * 桌面屏幕宽，网格布局更充分利用空间，视觉更整齐。
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
        // FlowRow 自动换行排列，每项占约 1/3 宽度
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 8,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            videoSources.forEach { source ->
                VideoSourceGridItem(
                    source = source,
                    onPlayClick = { onPlayClick(source) },
                    modifier = Modifier.width(280.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoSourceGridItem(
    source: VideoSource,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Card(
        modifier = modifier.padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlayClick),
        ) {

            // 质量和格式
            Text(
                modifier = Modifier.padding(12.dp),
                text = "${source.quality} • ${source.format.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

        }
    }
}
