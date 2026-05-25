package com.example.kmp_demo.features.domestic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.kmp_demo.LocalScaffoldPadding
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.rememberPageStatus
import com.example.kmp_demo.core.components.safeContent
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.core.components.gridColumns
import com.example.kmp_demo.core.components.skeletonCount
import com.example.kmp_demo.features.domestic.ui.components.DomesticMediaCard
import com.example.kmp_demo.features.domestic.ui.components.DomesticSkeletonItem
import org.koin.compose.viewmodel.koinViewModel

/**
 * 国内影视首页。
 *
 * 使用 Paging 3 + Room 缓存驱动瀑布流数据。
 * 布局向 [FilmHomeScreen] 看齐：
 * - 顶部搜索框（点击跳转搜索页）
 * - 分类筛选芯片（动态发现，多站点合并）
 * - 瀑布流网格展示最近更新的影视内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomesticHomeScreen(
    onSearchClick: () -> Unit,
    onMediaClick: (DomesticMedia) -> Unit,
    viewModel: DomesticViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val mediaItems = viewModel.mediaPaging.collectAsLazyPagingItems()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pageStatus = mediaItems.rememberPageStatus()

    Scaffold(
        modifier = Modifier
            .safeContent()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = {
                        // 搜索框 — 点击跳转搜索页
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable(onClick = onSearchClick),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "搜索国产剧/动漫/综艺...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                // 分类筛选芯片
                if (uiState.availableTypes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "全部" 芯片
                        FilterChip(
                            selected = uiState.selectedType == "全部",
                            onClick = {
                                viewModel.sendIntent(DomesticContract.Intent.SelectType("全部"))
                            },
                            label = { Text("全部") }
                        )
                        // 动态发现的分类芯片
                        uiState.availableTypes.forEach { typeName ->
                            FilterChip(
                                selected = uiState.selectedType == typeName,
                                onClick = {
                                    viewModel.sendIntent(DomesticContract.Intent.SelectType(typeName))
                                },
                                label = { Text(typeName) }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        PageContainer(
            pageStatus = pageStatus,
            onRetry = { mediaItems.retry() },
            modifier = Modifier.padding(paddingValues),
            loadingContent = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns()),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(skeletonCount()) { DomesticSkeletonItem() }
                }
            }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns()),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = mediaItems.itemCount,
                    key = mediaItems.itemKey { it.id }
                ) { index ->
                    mediaItems[index]?.let { media ->
                        DomesticMediaCard(
                            media = media,
                            modifier = Modifier.padding(8.dp),
                            onClick = { onMediaClick(media) }
                        )
                    }
                }

                // 加载更多指示器
                if (mediaItems.loadState.append is LoadState.Loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }

                // 加载更多失败 — 重试按钮
                if (mediaItems.loadState.append is LoadState.Error) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val error = (mediaItems.loadState.append as LoadState.Error).error
                        TextButton(
                            onClick = { mediaItems.retry() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text("加载失败，点击重试: ${error.message}")
                        }
                    }
                }
            }
        }
    }
}
