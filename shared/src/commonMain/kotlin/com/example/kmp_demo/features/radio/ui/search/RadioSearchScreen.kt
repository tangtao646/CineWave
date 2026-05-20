package com.example.kmp_demo.features.radio.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kmp_demo.features.radio.ui.components.StationItem
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.safeContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSearchScreen(
    viewModel: RadioSearchViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // 统一观察 playerManager 的聚合状态
    val playerUiState by viewModel.playerManager.uiState.collectAsState()
    val currentPlayingStation = playerUiState.currentStation
    val isPlaying = playerUiState.isPlaying

    Scaffold(
        modifier = Modifier.safeContent(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        TextField(
                            value = uiState.query,
                            onValueChange = {
                                viewModel.sendIntent(
                                    RadioSearchContract.Intent.UpdateQuery(
                                        it
                                    )
                                )
                            },
                            placeholder = {
                                Text(
                                    "搜索电台名称...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.sendIntent(RadioSearchContract.Intent.ClearQuery) }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)) {
                if (uiState.query.isBlank()) {
                    // 空输入状态
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "输入关键词开始搜索电台",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    // 使用 PageContainer 处理加载、报错、空结果
                    PageContainer(
                        pageStatus = uiState.pageStatus,
                        onRetry = { /* 搜索页通常由 debounce 自动重试，也可在此手动触发搜索 */ },
                        emptyContent = {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "未找到 \"${uiState.query}\" 相关电台",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.results, key = { it.uuid }) { station ->
                                val isCurrent = currentPlayingStation?.uuid == station.uuid
                                StationItem(
                                    station = station,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && isPlaying,
                                    onFavoriteClick = {
                                        viewModel.sendIntent(
                                            RadioSearchContract.Intent.ToggleFavorite(
                                                station
                                            )
                                        )
                                    },
                                    onTogglePlayPause = {
                                        viewModel.sendIntent(
                                            RadioSearchContract.Intent.PlayStation(
                                                station
                                            )
                                        )
                                    },
                                    onClick = {
                                        if (!isCurrent) {
                                            viewModel.sendIntent(
                                                RadioSearchContract.Intent.PlayStation(
                                                    station
                                                )
                                            )
                                        }
                                        onNavigateToPlayer()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
