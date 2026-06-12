package com.example.kmp_demo.features.domestic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.kmp_demo.LocalScaffoldPadding
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.VideoSourceListContent
import com.example.kmp_demo.core.components.shimmer
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.ui.components.DomesticDetailSkeleton
import org.koin.compose.viewmodel.koinViewModel

/**
 * 国内影视详情页。
 *
 * 分阶段渲染：
 * 1. 元数据（封面、简介）→ 查询到就立即展示
 * 2. 播放源列表 → 最耗时，最后展示
 *
 * 参考 [FilmDetailScreen] 的 MVI + PageContainer 模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomesticDetailScreen(
    title: String,
    onBack: () -> Unit,
    onPlay: (url: String, title: String, episodes: List<EpisodeInfo>) -> Unit,
    viewModel: DomesticDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(title) {
        viewModel.sendIntent(DomesticDetailContract.Intent.Init(title))
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is DomesticDetailContract.Effect.NavigateToPlayer -> {
                    onPlay(effect.url, effect.title, effect.episodes)
                }
                is DomesticDetailContract.Effect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalScaffoldPadding.current.calculateBottomPadding())
        ) {
            PageContainer(
                pageStatus = uiState.pageStatus,
                onRetry = { viewModel.sendIntent(DomesticDetailContract.Intent.Retry) },
                loadingContent = { DomesticDetailSkeleton() }
            ) {
                uiState.media?.let { media ->
                    DomesticDetailContent(
                        media = media,
                        videoSources = uiState.videoSources,
                        isSniffing = uiState.isSniffing,
                        onPlayClick = { viewModel.sendIntent(DomesticDetailContract.Intent.Play(it)) },
                    )
                }
            }

            // 返回按钮 — 悬浮在封面图左上角
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
        }
    }
}

@Composable
private fun DomesticDetailContent(
    media: DomesticMedia,
    videoSources: List<VideoSource>,
    isSniffing: Boolean,
    onPlayClick: (VideoSource) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ========== 1. 封面大图 ==========
        Box(modifier = Modifier
            .height(320.dp)
            .fillMaxWidth()) {
            if (media.coverUrl != null) {
                AsyncImage(
                    model = media.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmer(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 无封面时显示标题占位
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = media.title.take(2),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 底部渐变遮罩，让文字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                            startY = 400f
                        )
                    )
            )
        }

        // ========== 2. 内容区域（简介 + 播放源） ==========
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // 元信息行
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "类型: ${media.type.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                media.year?.let {
                    Text(
                        text = "年份: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                media.area?.let {
                    Text(
                        text = "地区: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 备注（如"更新至30集"）
            media.remarks?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // ========== 3. 简介 ==========
            media.description?.let {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "剧情简介",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )
            }

            // ========== 4. 播放资源 ==========
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "播放资源",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            VideoSourceListContent(
                videoSources = videoSources,
                isSniffing = isSniffing,
                onPlayClick = onPlayClick,
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
