package com.example.kmp_demo.features.domestic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.kmp_demo.core.components.shimmer
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia

/**
 * 国内影视媒体卡片。
 *
 * 在搜索结果网格中展示单个媒体条目。
 * 封面图使用 Coil 的 AsyncImage 加载。
 */
@Composable
fun DomesticMediaCard(
    media: DomesticMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // 封面图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (media.coverUrl != null) {
                    AsyncImage(
                        model = media.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().shimmer(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 无封面时显示占位
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = media.title.take(2),
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 文字信息区域
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 类型标签
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = media.type.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // 来源数量
                    if (media.videoSources.isNotEmpty()) {
                        Text(
                            text = "${media.videoSources.size}个源",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/**
 * 首页瀑布流骨架屏卡片 — 模仿 DomesticMediaCard 布局。
 */
@Composable
fun DomesticSkeletonItem() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // 封面图骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .shimmer()
            )

            // 文字信息骨架
            Column(modifier = Modifier.padding(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(18.dp)
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .shimmer()
                )
            }
        }
    }
}

/**
 * 详情页骨架屏 — 模仿 DomesticDetailContent 布局。
 */
@Composable
fun DomesticDetailSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // 封面大图骨架
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .shimmer()
        )

        Column(modifier = Modifier.padding(16.dp)) {
            // 标题骨架
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(32.dp).shimmer())
            Spacer(modifier = Modifier.height(8.dp))

            // 元信息行骨架
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(80.dp, 20.dp).shimmer())
                Box(modifier = Modifier.size(80.dp, 20.dp).shimmer())
                Box(modifier = Modifier.size(80.dp, 20.dp).shimmer())
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 简介标题骨架
            Box(modifier = Modifier.fillMaxWidth(0.3f).height(24.dp).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            // 简介内容骨架
            repeat(4) {
                Box(modifier = Modifier.fillMaxWidth().height(16.dp).shimmer())
                Spacer(modifier = Modifier.height(8.dp))
            }
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).shimmer())

            Spacer(modifier = Modifier.height(32.dp))

            // 播放资源标题骨架
            Box(modifier = Modifier.fillMaxWidth(0.3f).height(24.dp).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            // 播放源列表骨架
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
