package com.example.kmp_demo.features.film.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.example.kmp_demo.LocalScaffoldPadding
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.rememberPageStatus
import com.example.kmp_demo.core.components.safeContent
import com.example.kmp_demo.features.film.data.remote.dto.GenreDto
import com.example.kmp_demo.features.film.domain.model.MovieSortOrder
import com.example.kmp_demo.features.film.ui.components.MovieCard
import com.example.kmp_demo.features.film.ui.components.MovieSkeletonItem
import org.koin.compose.viewmodel.koinViewModel
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmHomeScreen(
    onSearchClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    viewModel: FilmViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val movies = viewModel.movies.collectAsLazyPagingItems()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pageStatus = movies.rememberPageStatus()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .safeContent()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = {
                        // 搜索框
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
                                    text = "搜索电影...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            MovieSortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        viewModel.sendIntent(
                                            FilmContract.Intent.SelectSortOrder(
                                                order
                                            )
                                        )
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (uiState.sortOrder == order) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                    },
                    scrollBehavior = scrollBehavior
                )

                // 分类 Tab 栏
                GenreTabs(
                    genres = uiState.genres,
                    selectedGenreId = uiState.selectedGenreId,
                    onGenreSelected = { viewModel.sendIntent(FilmContract.Intent.SelectGenre(it)) }
                )
            }
        }
    ) { paddingValues ->
        PageContainer(
            pageStatus = pageStatus,
            onRetry = { movies.retry() },
            modifier = Modifier.padding(paddingValues),
            loadingContent = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(6) { MovieSkeletonItem() }
                }
            }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = LocalScaffoldPadding.current.calculateBottomPadding())
            ) {
                items(
                    count = movies.itemCount,
                    key = movies.itemKey { it.id }
                ) { index ->
                    movies[index]?.let { movie ->
                        MovieCard(
                            movie = movie,
                            modifier = Modifier.padding(8.dp),
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }

                if (movies.loadState.append is LoadState.Loading) {
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

                if (movies.loadState.append is LoadState.Error) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val error = (movies.loadState.append as LoadState.Error).error
                        TextButton(
                            onClick = { movies.retry() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreTabs(
    genres: List<GenreDto>,
    selectedGenreId: String?,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "热门" 选项 (对应 genreId = null)
        item {
            FilterChip(
                selected = selectedGenreId == null,
                onClick = { onGenreSelected(null) },
                label = { Text("热门") }
            )
        }

        items(genres, key = { it.id }) { genre ->
            FilterChip(
                selected = selectedGenreId == genre.id.toString(),
                onClick = { onGenreSelected(genre.id.toString()) },
                label = { Text(genre.name) }
            )
        }
    }
}
