package com.example.kmp_demo.core.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/**
 * 为 LazyVerticalGrid 提供的通用分页 Footer 逻辑。
 * 包含：加载中、加载失败（重试）、已加载全部三种状态。
 */
fun LazyGridScope.pagingFooter(
    items: LazyPagingItems<*>,
    noMoreMsg: String = "— 到底啦，没有更多内容了 —"
) {
    val appendState = items.loadState.append

    when (appendState) {
        is LoadState.Loading -> {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PagingLoadingFooter()
            }
        }
        is LoadState.Error -> {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PagingErrorFooter(
                    error = appendState.error,
                    onRetry = { items.retry() }
                )
            }
        }
        is LoadState.NotLoading -> {
            if (appendState.endOfPaginationReached && items.itemCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PagingNoMoreFooter(noMoreMsg)
                }
            }
        }
    }
}

/**
 * 为 LazyColumn 提供的通用分页 Footer 逻辑。
 */
fun LazyListScope.pagingFooter(
    items: LazyPagingItems<*>,
    noMoreMsg: String = "— 到底啦，没有更多内容了 —"
) {
    when (val appendState = items.loadState.append) {
        is LoadState.Loading -> {
            item {
                PagingLoadingFooter()
            }
        }
        is LoadState.Error -> {
            item {
                PagingErrorFooter(
                    error = appendState.error,
                    onRetry = { items.retry() }
                )
            }
        }
        is LoadState.NotLoading -> {
            if (appendState.endOfPaginationReached && items.itemCount > 0) {
                item {
                    PagingNoMoreFooter(noMoreMsg)
                }
            }
        }
    }
}

@Composable
private fun PagingLoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun PagingErrorFooter(
    error: Throwable,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val errorMessage = error.message ?: "未知错误"
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )

        TextButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("点击重试")
        }
    }
}

@Composable
private fun PagingNoMoreFooter(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
