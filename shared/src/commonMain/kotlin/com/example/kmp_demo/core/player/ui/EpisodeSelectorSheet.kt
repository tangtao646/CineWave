package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_demo.core.player.domain.EpisodeInfo

/**
 * 沉浸式选集底部弹窗。
 *
 * 使用 Material3 [ModalBottomSheet] 实现半屏弹窗，以网格布局展示所有剧集。
 * 当前播放的剧集使用主题色高亮，点击非当前剧集触发切换并自动关闭弹窗。
 *
 * @param episodes      剧集列表
 * @param currentIndex  当前播放的剧集索引
 * @param onSelect      选集回调，返回选中剧集的 index
 * @param onDismiss     关闭弹窗回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSelectorSheet(
    episodes: List<EpisodeInfo>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // 标题
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // 剧集网格
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp), // 限制最大高度，避免全屏
            ) {
                items(episodes, key = { it.index }) { episode ->
                    val isSelected = episode.index == currentIndex
                    EpisodeGridItem(
                        label = episode.label,
                        isSelected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                onSelect(episode.index)
                            } else {
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 剧集网格单项。
 *
 * @param label      显示标签，如"第3集"
 * @param isSelected 是否为当前播放剧集
 * @param onClick    点击回调
 */
@Composable
private fun EpisodeGridItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1.6f) // 宽高比，使卡片呈矩形
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
