package com.example.kmp_demo.features.radio.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.example.kmp_demo.features.radio.ui.components.SearchBarPlaceholder
import com.example.kmp_demo.features.radio.ui.components.StationItem
import com.example.kmp_demo.core.components.PageContainer
import com.example.kmp_demo.core.components.rememberPageStatus
import com.example.kmp_demo.core.components.shimmer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioListScreen(
    viewModel: RadioListViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val stations = viewModel.stations.collectAsLazyPagingItems()
    val uiState by viewModel.uiState.collectAsState()
    val refreshState = rememberPullToRefreshState()
    val pageStatus = stations.rememberPageStatus()

    val isRefreshing = stations.loadState.refresh is LoadStateLoading && stations.itemCount > 0

    // 统一观察 playerManager 的聚合状态
    val playerUiState by viewModel.playerManager.uiState.collectAsState()
    val currentPlayingStation = playerUiState.currentStation
    val isPlaying = playerUiState.isPlaying

    var showCountrySheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. 搜索与国家切换 Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBarPlaceholder(
                modifier = Modifier.weight(1f),
                onClick = onNavigateToSearch
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 国家切换按钮
            OutlinedIconButton(
                onClick = { showCountrySheet = true },
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = "Switch Country",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        uiState.selectCountryCode,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. 分类 Tab
        ScrollableTabRow(
            selectedTabIndex = uiState.categoryLabels.indexOf(uiState.selectCategoryLabel),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            uiState.categoryLabels.forEach { category ->
                Tab(
                    selected = uiState.selectCategoryLabel == category,
                    onClick = {
                        viewModel.sendIntent(
                            RadioListContract.Intent.SelectCateIntent(
                                category
                            )
                        )
                    },
                    text = { Text(category) }
                )
            }
        }

        // 3. 电台列表容器
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .weight(1f)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { stations.refresh() },
                state = refreshState
            ) {
                // 使用 PageContainer 处理加载、错误和空状态
                PageContainer(
                    pageStatus = pageStatus,
                    onRetry = { stations.retry() },
                    loadingContent = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(10) { NewsSkeletonItem() }
                        }
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(stations.itemCount, key = stations.itemKey { it.uuid }) { index ->
                            stations[index]?.let {
                                val isCurrent = currentPlayingStation?.uuid == it.uuid

                                StationItem(
                                    station = it,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && isPlaying,
                                    onFavoriteClick = {
                                        viewModel.sendIntent(
                                            RadioListContract.Intent.ToggleFavorite(
                                                it
                                            )
                                        )
                                    },
                                    onTogglePlayPause = {
                                        if (!isCurrent) {
                                            viewModel.sendIntent(
                                                RadioListContract.Intent.PlayFromList(
                                                    stations.itemSnapshotList.items, index
                                                )
                                            )
                                        } else {
                                            viewModel.sendIntent(
                                                RadioListContract.Intent.TogglePlayPause
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (!isCurrent) {
                                            viewModel.sendIntent(
                                                RadioListContract.Intent.PlayFromList(
                                                    stations.itemSnapshotList.items, index
                                                )
                                            )
                                        }
                                        onNavigateToPlayer()
                                    }
                                )
                            }
                        }

                        // 处理加载更多 (Footer)
                        if (stations.loadState.append is LoadStateLoading) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
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

    // 国家选择弹窗
    if (showCountrySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCountrySheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            var countrySearch by remember { mutableStateOf("") }
            val filteredCountries = uiState.countries.filter {
                it.name.contains(
                    countrySearch,
                    ignoreCase = true
                ) || it.code.contains(countrySearch, ignoreCase = true)
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "切换国家/地区",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = countrySearch,
                    onValueChange = { countrySearch = it },
                    placeholder = { Text("搜索国家...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredCountries) { country ->
                        ListItem(
                            headlineContent = { Text(country.name) },
                            supportingContent = { Text("${country.stationCount} 个电台") },
                            trailingContent = {
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(country.code)
                                }
                            },
                            modifier = Modifier.clickable {
                                showCountrySheet = false
                                viewModel.sendIntent(RadioListContract.Intent.ChangeCountry(country.code))
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun NewsSkeletonItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(100.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .shimmer())
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(20.dp)
                .shimmer())
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(15.dp)
                .shimmer())
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .shimmer())
    }
}


