package com.example.kmp_demo.core.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.LoadStateNotLoading
import app.cash.paging.compose.LazyPagingItems
import com.example.kmp_demo.core.PageStatus

/**
 * 全局统一页面状态容器
 */
@Composable
fun PageContainer(
    pageStatus: PageStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { DefaultLoading() },
    emptyContent: @Composable () -> Unit = { DefaultEmpty(onRetry) },
    errorContent: @Composable (String?) -> Unit = { DefaultError(it, onRetry) },
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (pageStatus) {
            is PageStatus.Loading -> loadingContent()
            is PageStatus.Empty -> emptyContent()
            is PageStatus.Error -> errorContent(pageStatus.message)
            is PageStatus.Content -> content()
        }
    }
}

@Composable
fun DefaultLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun DefaultEmpty(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("暂无数据", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("刷新试试") }
    }
}

@Composable
fun DefaultError(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message ?: "网络似乎开小差了",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

/**
 * Paging 状态映射扩展
 */
@Composable
fun <T : Any> LazyPagingItems<T>.rememberPageStatus(): PageStatus {
    return when (val refresh = loadState.refresh) {
        is LoadStateLoading -> if (itemCount == 0) PageStatus.Loading else PageStatus.Content
        is LoadStateError -> if (itemCount == 0) PageStatus.Error(refresh.error.message) else PageStatus.Content
        is LoadStateNotLoading -> if (itemCount == 0) PageStatus.Empty else PageStatus.Content
    }
}
