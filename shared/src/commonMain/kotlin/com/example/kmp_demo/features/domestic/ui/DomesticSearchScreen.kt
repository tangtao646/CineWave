package com.example.kmp_demo.features.domestic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
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
import com.example.kmp_demo.core.components.gridColumns
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.ui.components.DomesticMediaCard
import org.koin.compose.viewmodel.koinViewModel

/**
 * 国内影视搜索页。
 *
 * 布局向 FilmSearchScreen 看齐：
 * - 顶部搜索栏（自动聚焦，支持清除）
 * - 搜索结果瀑布流网格
 * - 空状态提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomesticSearchScreen(
    onBackClick: () -> Unit,
    onMediaClick: (DomesticMedia) -> Unit,
    viewModel: DomesticSearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = uiState.query,
                        onValueChange = { viewModel.sendIntent(DomesticSearchContract.Intent.UpdateQuery(it)) },
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
                uiState.query.isBlank() && uiState.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "搜索你喜欢的国产剧、动漫、综艺",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 加载中
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // 错误状态
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "搜索失败",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
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

                // 搜索结果为空
                uiState.results.isEmpty() && uiState.query.isNotBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "未找到相关资源",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 搜索结果
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns()),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = uiState.results.size,
                            key = { index -> uiState.results[index].id }
                        ) { index ->
                            val media = uiState.results[index]
                            DomesticMediaCard(
                                media = media,
                                modifier = Modifier.padding(8.dp),
                                onClick = { onMediaClick(media) }
                            )
                        }
                    }
                }
            }
        }
    }
}
