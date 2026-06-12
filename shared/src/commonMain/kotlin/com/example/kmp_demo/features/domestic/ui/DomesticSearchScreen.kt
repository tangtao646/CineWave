package com.example.kmp_demo.features.domestic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.gridColumns
import com.example.kmp_demo.core.components.rememberPageStatus
import com.example.kmp_demo.core.components.safeContent
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.ui.components.DomesticMediaCard
import org.koin.compose.viewmodel.koinViewModel

/**
 * 国内影视搜索页。
 *
 * 布局全面向 FilmSearchScreen 看齐：
 * - 引入 Paging 3 处理搜索结果
 * - 使用 PageContainer 统一处理加载/错误状态
 * - 移除 ViewModel 内部的手动 Loading/Error 维护
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomesticSearchScreen(
    onBackClick: () -> Unit,
    onMediaClick: (DomesticMedia) -> Unit,
    viewModel: DomesticSearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchResults = viewModel.searchResults.collectAsLazyPagingItems()
    val focusRequester = remember { FocusRequester() }
    val pageStatus = searchResults.rememberPageStatus(currentQuery = uiState.query)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = Modifier.safeContent(),
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = uiState.query,
                        onValueChange = {
                            viewModel.sendIntent(
                                DomesticSearchContract.Intent.UpdateQuery(
                                    it
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("搜索国产剧/动漫/综艺...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.sendIntent(DomesticSearchContract.Intent.ClearQuery) }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when {
                // 初始空状态
                uiState.query.isBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "搜索你喜欢的国产剧、动漫、综艺",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 命中敏感词拦截
                uiState.isBlocked -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "未找到相关影视资源，换个关键词试试吧",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 正常结果流
                else -> {
                    PageContainer(
                        pageStatus = pageStatus,
                        onRetry = { searchResults.retry() }
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns()),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = searchResults.itemCount,
                                key = { index ->
                                    val media = searchResults.peek(index)
                                    media?.let { "${it.id}_$index" } ?: "placeholder_$index"
                                }
                            ) { index ->
                                searchResults[index]?.let { media ->
                                    DomesticMediaCard(
                                        media = media,
                                        modifier = Modifier.padding(8.dp),
                                        onClick = { onMediaClick(media) }
                                    )
                                }
                            }

                            // 分页加载态 Footer
                            if (searchResults.loadState.append is LoadState.Loading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
