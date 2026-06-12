package com.example.kmp_demo.core.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
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
        is LoadState.Loading -> if (itemCount == 0) PageStatus.Loading else PageStatus.Content
        is LoadState.Error -> if (itemCount == 0) PageStatus.Error(refresh.error.message) else PageStatus.Content
        is LoadState.NotLoading -> if (itemCount == 0) PageStatus.Empty else PageStatus.Content
    }
}

/**
 * 兼容搜索页联动状态机
 * * @param currentQuery 传入当前的实时 query
 */
@Composable
fun <T : Any> LazyPagingItems<T>.rememberPageStatus(currentQuery: String): PageStatus {
    var lastSettledQuery by remember { mutableStateOf("") }

    // 2. 核心状态校准器：只有当 Paging 判定刷新结束（NotLoading），且不是正在触发期间时，才落定词
    LaunchedEffect(loadState.refresh) {
        if (loadState.refresh is LoadState.NotLoading) {
            lastSettledQuery = currentQuery
        }
    }

    // 只要当前输入的词和上一次落定的词对不上，说明正处于【防抖期】或【网络请求发出去但新回包还没来】的临界空窗。
    // 此时，必须【无视 itemCount】，强行判定为全屏 Loading！
    if (currentQuery != lastSettledQuery) {
        return PageStatus.Loading
    }

    return this.rememberPageStatus()
}