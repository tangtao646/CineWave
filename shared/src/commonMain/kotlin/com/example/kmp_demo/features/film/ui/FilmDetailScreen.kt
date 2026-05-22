package com.example.kmp_demo.features.film.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.VideoSourceListContent
import com.example.kmp_demo.core.components.shimmer
import com.example.kmp_demo.features.film.domain.model.CastMember
import com.example.kmp_demo.features.film.domain.model.MovieDetail
import com.example.kmp_demo.features.film.domain.model.VideoSource
import com.example.kmp_demo.features.film.ui.components.MovieDetailSkeleton
import com.example.kmp_demo.LocalScaffoldPadding
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FilmDetailScreen(
    onBackClick: () -> Unit,
    onNavigateToPlayer: (url: String, title: String) -> Unit,
    viewModel: FilmDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is FilmDetailContract.Effect.NavigateToPlayer -> {
                    onNavigateToPlayer(effect.url, effect.title)
                }

                is FilmDetailContract.Effect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            PageContainer(
                pageStatus = uiState.pageStatus,
                onRetry = { viewModel.sendIntent(FilmDetailContract.Intent.Retry) },
                loadingContent = { MovieDetailSkeleton() }
            ) {
                uiState.movie?.let { movie ->
                    MovieDetailContent(
                        movie = movie,
                        videoSources = uiState.videoSources,
                        isSniffing = uiState.isSniffing,
                        onPlayClick = { viewModel.sendIntent(FilmDetailContract.Intent.PlayVideo(it)) },
                    )
                }
            }

            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailContent(
    movie: MovieDetail,
    videoSources: List<VideoSource>,
    isSniffing: Boolean,
    onPlayClick: (VideoSource) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = LocalScaffoldPadding.current.calculateBottomPadding())
            .verticalScroll(scrollState)
    ) {
        // 海报大图
        Box(modifier = Modifier.height(300.dp)) {
            AsyncImage(
                model = movie.backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().shimmer(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        startY = 400f
                    )
                )
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // 播放源部分
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

            Spacer(modifier = Modifier.height(24.dp))

            // 剧情简介
            Text(
                text = "剧情简介",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = movie.overview,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            // 演员列表
            if (movie.cast.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "主要演员",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(movie.cast) { member -> CastItem(member) }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun VideoSourceItem(
    source: VideoSource,
    onPlayClick: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
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

@Composable
fun CastItem(member: CastMember) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        AsyncImage(
            model = member.profilePath,
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(CircleShape).shimmer(),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = member.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = member.character,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
