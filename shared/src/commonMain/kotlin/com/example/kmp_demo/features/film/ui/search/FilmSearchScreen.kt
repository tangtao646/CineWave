package com.example.kmp_demo.features.film.ui.search

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
import androidx.paging.compose.itemKey
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.gridColumns
import com.example.kmp_demo.core.components.rememberPageStatus
import com.example.kmp_demo.core.components.safeContent
import com.example.kmp_demo.features.film.ui.components.MovieCard
import org.koin.compose.viewmodel.koinViewModel
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmSearchScreen(
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    viewModel: FilmSearchViewModel = koinViewModel()
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
                            viewModel.sendIntent(FilmSearchContract.Intent.UpdateQuery(it))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("搜索电影、影人...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.sendIntent(FilmSearchContract.Intent.ClearQuery)
                                }) {
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
            if (uiState.query.isBlank()) {
                // 搜索页特有的空状态提示
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "开始搜索你喜欢的电影",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (uiState.isBlocked) {
                // 命中敏感词拦截
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "未找到相关影视资源，换个关键词试试吧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 使用 PageContainer 处理搜索结果的状态
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
                                val movie = searchResults.peek(index) // peek 不会触发新的分页加载，安全高效
                                movie?.let { "${it.id}_$index" } ?: "placeholder_$index"
                            }
                        ) { index ->
                            searchResults[index]?.let { movie ->
                                MovieCard(
                                    movie = movie,
                                    modifier = Modifier.padding(8.dp),
                                    onClick = { onMovieClick(movie.id) }
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
