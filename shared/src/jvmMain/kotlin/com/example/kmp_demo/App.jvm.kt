package com.example.kmp_demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.example.kmp_demo.core.initializeCoil
import com.example.kmp_demo.core.navigation.decodeNavParam
import com.example.kmp_demo.core.navigation.encodeNavParam
import com.example.kmp_demo.core.player.domain.EpisodeCache
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.core.security.SensitiveWordLoader
import com.example.kmp_demo.features.domestic.DomesticRoutes
import com.example.kmp_demo.features.domestic.ui.DomesticDetailScreen
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticHomeScreen
import com.example.kmp_demo.features.domestic.ui.DomesticSearchScreen
import com.example.kmp_demo.features.domestic.ui.player.DomesticPlayerScreen
import com.example.kmp_demo.features.film.FilmRoutes
import com.example.kmp_demo.features.film.ui.FilmDetailScreen
import com.example.kmp_demo.features.film.ui.FilmDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmHomeScreen
import com.example.kmp_demo.features.film.ui.player.FilmPlayerScreen
import com.example.kmp_demo.features.film.ui.search.FilmSearchScreen
import com.example.kmp_demo.features.radio.RadioRoutes
import com.example.kmp_demo.features.radio.ui.list.RadioListScreen
import com.example.kmp_demo.features.radio.player.RadioPlayerManager
import com.example.kmp_demo.features.radio.ui.list.RadioListViewModel
import com.example.kmp_demo.features.radio.ui.player.PlayerDetailScreen
import com.example.kmp_demo.features.radio.ui.search.RadioSearchScreen
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Desktop 平台的 App 入口。
 *
 * 使用左右布局：左侧 NavigationRail 导航栏 + 右侧内容区。
 * 底部导航栏已替换为侧边导航栏，更符合 Desktop 操作习惯。
 *
 * 由于 JetBrains Navigation Compose 在 Desktop 上存在
 * SavedStateRegistryController.performRestore(Bundle) 的兼容性问题，
 * 这里使用简单的状态管理替代 NavHost。
 *
 * ## 关键设计：页面缓存与生命周期管理
 * 使用 Compose 的 [key] 函数确保每次路由变化时，Composable 树完全重建，
 * 避免 ViewModel 和播放器 Controller 的缓存/复用问题。
 *
 * ## 全屏沉浸式播放
 * 当视频播放器进入全屏模式时，左侧导航栏自动隐藏，播放器占据整个窗口。
 * 按 ESC 键或点击播放器顶栏的退出全屏按钮可退出全屏模式。
 */
@Composable
fun App() {
    KoinContext {
        MaterialTheme {
            // 当前路由，初始为电台列表页
            var currentRoute by remember { mutableStateOf(RadioRoutes.list) }
            // 全屏沉浸式状态 — 播放器全屏时隐藏左侧导航栏
            var isFullScreen by remember { mutableStateOf(false) }

            val platformContext: PlatformContext = LocalPlatformContext.current
            LaunchedEffect(platformContext) {
                SingletonImageLoader.setSafe { context ->
                    initializeCoil(context)
                }
            }

            val sensitiveWordFilter = koinInject<SensitiveWordFilter>()
            LaunchedEffect(Unit) {
                val loader = SensitiveWordLoader(sensitiveWordFilter)
                loader.loadAsync()
            }

            // 始终获取 RadioListViewModel（Koin 单例），用于侧边栏 MiniPlayer
            val radioViewModel: RadioListViewModel = koinViewModel()
            val radioPlayerManager = radioViewModel.playerManager

            // 全屏时监听 ESC 键退出全屏
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Escape && isFullScreen) {
                            isFullScreen = false
                            true
                        } else {
                            false
                        }
                    }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // ===== 左侧导航栏（全屏时隐藏）=====
                    if (!isFullScreen) {
                        DesktopNavigationRail(
                            currentScreen = currentRoute,
                            onNavigate = { screen -> currentRoute = screen },
                            radioPlayerManager = radioPlayerManager,
                            onMiniPlayerClick = { currentRoute = RadioRoutes.player }
                        )
                    }

                    // ===== 右侧内容区 =====
                    Box(modifier = Modifier.weight(1f)) {
                        // 使用 key(currentRoute) 确保每次路由变化时 Composable 树完全重建
                        // 这解决了两个关键问题：
                        // 1. 页面缓存：回退后再次进入同一页面时，ViewModel 是全新的
                        // 2. 播放器崩溃：播放器 Controller 不会因为 Compose 缓存而处于已释放状态
                        key(currentRoute) {
                            // 根据当前路由显示对应的页面
                            when {
                                // === 电台板块 ===
                                currentRoute == RadioRoutes.list -> {
                                    RadioListScreen(
                                        viewModel = radioViewModel,
                                        onNavigateToSearch = { currentRoute = RadioRoutes.search },
                                        onNavigateToPlayer = { currentRoute = RadioRoutes.player }
                                    )
                                }

                                currentRoute == RadioRoutes.search -> {
                                    RadioSearchScreen(
                                        viewModel = koinViewModel(),
                                        onBack = { currentRoute = RadioRoutes.list },
                                        onNavigateToPlayer = { currentRoute = RadioRoutes.player }
                                    )
                                }

                                currentRoute == RadioRoutes.player -> {
                                    PlayerDetailScreen(
                                        playerManager = radioPlayerManager,
                                        onClose = { currentRoute = RadioRoutes.list }
                                    )
                                }

                                // === 电影板块 ===
                                currentRoute == FilmRoutes.home -> {
                                    FilmHomeScreen(
                                        onSearchClick = { currentRoute = FilmRoutes.search },
                                        onMovieClick = { movieId ->
                                            currentRoute = FilmRoutes.detail(movieId)
                                        }
                                    )
                                }

                                // 电影搜索页
                                currentRoute == FilmRoutes.search -> {
                                    FilmSearchScreen(
                                        onBackClick = { currentRoute = FilmRoutes.home },
                                        onMovieClick = { movieId ->
                                            currentRoute = FilmRoutes.detail(movieId)
                                        }
                                    )
                                }

                                // 电影详情页
                                currentRoute.startsWith("film_detail/") -> {
                                    val movieId = currentRoute.removePrefix("film_detail/").toIntOrNull() ?: return@Box
                                    // 使用 currentRoute 作为 key，确保切换不同电影时创建新的 ViewModel
                                    val viewModel: FilmDetailViewModel = koinViewModel(
                                        key = currentRoute,
                                        parameters = { parametersOf(movieId) }
                                    )
                                    FilmDetailScreen(
                                        viewModel = viewModel,
                                        onBackClick = { currentRoute = FilmRoutes.home },
                                        onNavigateToPlayer = { url, title ->
                                            // 从 ViewModel 的 episodesCache 中获取剧集列表并缓存
                                            val episodes = viewModel.episodesCache.value
                                            EpisodeCache.put(episodes)
                                            currentRoute = "film_player/${url.encodeNavParam()}/${title.encodeNavParam()}"
                                        }
                                    )
                                }

                                // 电影播放器页
                                currentRoute.startsWith("film_player/") -> {
                                    val encoded = currentRoute.removePrefix("film_player/")
                                    val slashIndex = encoded.indexOf("/")
                                    if (slashIndex == -1) return@Box
                                    val encodedUrl = encoded.take(slashIndex)
                                    val encodedTitle = encoded.substring(slashIndex + 1)
                                    val url = encodedUrl.decodeNavParam()
                                    val title = encodedTitle.decodeNavParam()
                                    val episodes = EpisodeCache.get()
                                    FilmPlayerScreen(
                                        initialUrl = url,
                                        seriesTitle = title,
                                        episodes = episodes,
                                        onBack = { currentRoute = FilmRoutes.home },
                                        onFullScreenChange = { full -> isFullScreen = full }
                                    )
                                }

                                // === 国产板块 ===
                                currentRoute == DomesticRoutes.home -> {
                                    DomesticHomeScreen(
                                        onSearchClick = { currentRoute = DomesticRoutes.search },
                                        onMediaClick = { media ->
                                            currentRoute = DomesticRoutes.detail(media.title)
                                        }
                                    )
                                }

                                // 国产搜索页
                                currentRoute == DomesticRoutes.search -> {
                                    DomesticSearchScreen(
                                        onBackClick = { currentRoute = DomesticRoutes.home },
                                        onMediaClick = { media ->
                                            currentRoute = DomesticRoutes.detail(media.title)
                                        }
                                    )
                                }

                                // 国产详情页
                                currentRoute.startsWith("domestic_detail/") -> {
                                    val encodedTitle = currentRoute.removePrefix("domestic_detail/")
                                    val title = encodedTitle.decodeNavParam()
                                    // 使用 currentRoute 作为 key，确保切换不同影视时创建新的 ViewModel
                                    val viewModel: DomesticDetailViewModel = koinViewModel(
                                        key = currentRoute,
                                        parameters = { parametersOf(title) }
                                    )
                                    DomesticDetailScreen(
                                        viewModel = viewModel,
                                        onBack = { currentRoute = DomesticRoutes.home },
                                        onPlay = { url, title, episodes ->
                                            // 缓存剧集列表，供播放器页读取
                                            EpisodeCache.put(episodes)
                                            currentRoute = "domestic_player/${url.encodeNavParam()}/${title.encodeNavParam()}"
                                        }
                                    )
                                }

                                // 国产播放器页
                                currentRoute.startsWith("domestic_player/") -> {
                                    val encoded = currentRoute.removePrefix("domestic_player/")
                                    val slashIndex = encoded.indexOf("/")
                                    if (slashIndex == -1) return@Box
                                    val encodedUrl = encoded.substring(0, slashIndex)
                                    val encodedTitle = encoded.substring(slashIndex + 1)
                                    val url = encodedUrl.decodeNavParam()
                                    val title = encodedTitle.decodeNavParam()
                                    val episodes = EpisodeCache.get()
                                    DomesticPlayerScreen(
                                        initialUrl = url,
                                        seriesTitle = title,
                                        episodes = episodes,
                                        onBack = { currentRoute = DomesticRoutes.home },
                                        onFullScreenChange = { full -> isFullScreen = full }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop 侧边导航栏。
 *
 * 使用 Material3 NavigationRail 组件。
 * 底部集成电台 MiniPlayer（紧凑模式），始终可见。
 */
@Composable
fun DesktopNavigationRail(
    currentScreen: String,
    onNavigate: (String) -> Unit,
    radioPlayerManager: RadioPlayerManager?,
    onMiniPlayerClick: () -> Unit
) {
    val screens = listOf(
        DesktopNavItem(RadioRoutes.list, "电台", Icons.Default.Radio),
        DesktopNavItem(FilmRoutes.home, "电影", Icons.Default.Face),
        DesktopNavItem(DomesticRoutes.home, "国产", Icons.Default.Tv)
    )

    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        header = {
            // 应用图标
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = "CineWave",
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        screens.forEach { item ->
            val selected = item.route == currentScreen
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                alwaysShowLabel = true
            )
        }

        // 将 MiniPlayer 推到导航栏底部
        Spacer(modifier = Modifier.weight(1f))

        // 底部 MiniPlayer（电台播放时显示）
        if (radioPlayerManager != null) {
            MiniPlayerRailItem(
                playerManager = radioPlayerManager,
                onClick = onMiniPlayerClick
            )
        }
    }
}

/**
 * 导航栏底部的紧凑型 MiniPlayer。
 * 只显示当前电台名称和播放/暂停按钮。
 */
@Composable
fun MiniPlayerRailItem(
    playerManager: RadioPlayerManager,
    onClick: () -> Unit
) {
    val uiState by playerManager.uiState.collectAsState()
    val currentStation = uiState.currentStation ?: return
    val isPlaying = uiState.isPlaying

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // 电台图标
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentStation.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 播放/暂停小图标
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class DesktopNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
