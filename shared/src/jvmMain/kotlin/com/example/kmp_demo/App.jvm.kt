package com.example.kmp_demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.example.kmp_demo.core.initializeCoil
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.core.security.SensitiveWordLoader
import com.example.kmp_demo.features.domestic.ui.DomesticDetailScreen
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticHomeScreen
import com.example.kmp_demo.features.domestic.ui.DomesticSearchScreen
import com.example.kmp_demo.features.domestic.ui.player.DomesticPlayerScreen
import com.example.kmp_demo.features.film.ui.FilmDetailScreen
import com.example.kmp_demo.features.film.ui.FilmDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmHomeScreen
import com.example.kmp_demo.features.film.ui.player.FilmPlayerScreen
import com.example.kmp_demo.features.film.ui.search.FilmSearchScreen
import com.example.kmp_demo.features.radio.ui.DesktopPlayerDetailScreen
import com.example.kmp_demo.features.radio.ui.components.MiniPlayerBar
import com.example.kmp_demo.features.radio.ui.list.RadioListScreen
import com.example.kmp_demo.features.radio.ui.list.RadioListViewModel
import com.example.kmp_demo.features.radio.ui.search.RadioSearchScreen
import com.example.kmp_demo.navigation.DesktopRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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
 * 这里引入了 JetBrains Navigation3 进行桌面端导航验证。
 * Nav3 是声明式的、类型安全的，且专门为跨平台设计。
 *
 * ## 全屏沉浸式播放
 * 当视频播放器进入全屏模式时，左侧导航栏自动隐藏，播放器占据整个窗口。
 * 按 ESC 键或点击播放器顶栏的退出全屏按钮可退出全屏模式。
 */
@Composable
fun App() {
    KoinContext {
        MaterialTheme {
            // 在 Desktop 上使用 Navigation3 需要配置序列化模块以支持状态保存
            val configuration = remember {
                SavedStateConfiguration {
                    serializersModule = SerializersModule {
                        polymorphic(NavKey::class) {
                            subclass(DesktopRoute.RadioList::class)
                            subclass(DesktopRoute.RadioSearch::class)
                            subclass(DesktopRoute.RadioPlayer::class)
                            subclass(DesktopRoute.FilmHome::class)
                            subclass(DesktopRoute.FilmSearch::class)
                            subclass(DesktopRoute.FilmDetail::class)
                            subclass(DesktopRoute.FilmPlayer::class)
                            subclass(DesktopRoute.DomesticHome::class)
                            subclass(DesktopRoute.DomesticSearch::class)
                            subclass(DesktopRoute.DomesticDetail::class)
                            subclass(DesktopRoute.DomesticPlayer::class)
                        }
                    }
                }
            }
            // 使用 Navigation3 的 NavBackStack 管理路由栈
            val backStack = rememberNavBackStack(configuration, DesktopRoute.RadioList)
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

            // 自动管理全屏状态：当进入电台播放器或视频播放页面时，隐藏左侧导航栏
            // 当离开这些页面时，强制退出全屏模式，确保导航栏能重新显示
            LaunchedEffect(backStack.last()) {
                val currentRoute = backStack.last()
                isFullScreen = currentRoute is DesktopRoute.FilmPlayer
                        || currentRoute is DesktopRoute.DomesticPlayer
                        || currentRoute is DesktopRoute.RadioPlayer
            }

            // 全屏时监听 ESC 键退出全屏 (或者执行返回操作)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Escape) {
                            if (isFullScreen) {
                                isFullScreen = false
                                true
                            } else if (backStack.size > 1) {
                                // 如果不是全屏且有回退栈，则执行返回
                                backStack.removeLast()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // ===== 左侧导航栏（全屏时隐藏）=====
                    if (!isFullScreen) {
                        DesktopNavigationRail(
                            currentScreen = backStack.last() as DesktopRoute,
                            onNavigate = { route -> 
                                // 桌面端导航通常点击主项时清空栈并跳转
                                backStack.clear()
                                backStack.add(route)
                            },
                        )
                    }

                    // ===== 右侧内容区 =====
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 桌面端 MiniPlayer 状态：提前获取，供列表 padding 和 MiniPlayer 悬浮使用
                            val playerUiState by radioPlayerManager.uiState.collectAsState()
                            val currentRoute = backStack.last()
                            val isRadioSection = currentRoute is DesktopRoute.RadioList || currentRoute is DesktopRoute.RadioSearch
                            val hasCurrentStation = playerUiState.currentStation != null

                            // 使用 Navigation3 的 NavDisplay 替代手动的 when(currentRoute)
                            NavDisplay(
                                backStack = backStack,
                            ) { route ->
                                val desktopRoute = route as DesktopRoute
                                NavEntry(key = desktopRoute) {
                                    // 根据当前路由显示对应的页面
                                    when (desktopRoute) {
                                        // === 电台板块 ===
                                        is DesktopRoute.RadioList -> {
                                            // 在 NavEntry 内部重新观察状态，确保状态变化时能触发重组
                                            val innerPlayerUiState by radioPlayerManager.uiState.collectAsState()
                                            val innerHasCurrentStation = innerPlayerUiState.currentStation != null

                                            Column(modifier = Modifier.fillMaxSize()) {
                                                RadioListScreen(
                                                    modifier = Modifier.weight(1f),
                                                    viewModel = radioViewModel,
                                                    onNavigateToSearch = { backStack.add(DesktopRoute.RadioSearch) },
                                                    onNavigateToPlayer = { backStack.add(DesktopRoute.RadioPlayer) }
                                                )

                                                if (innerHasCurrentStation) {
                                                    MiniPlayerBar(
                                                        playerManager = radioPlayerManager,
                                                        onClick = { backStack.add(DesktopRoute.RadioPlayer) }
                                                    )
                                                }
                                            }
                                        }

                                        is DesktopRoute.RadioSearch -> {
                                            RadioSearchScreen(
                                                viewModel = koinViewModel(),
                                                onBack = { backStack.removeLast() },
                                                onNavigateToPlayer = { backStack.add(DesktopRoute.RadioPlayer) }
                                            )
                                        }

                                        is DesktopRoute.RadioPlayer -> {
                                            // 桌面端使用适配的播放器页面，支持全屏沉浸式体验
                                            DesktopPlayerDetailScreen(
                                                playerManager = radioPlayerManager,
                                                onClose = {
                                                    isFullScreen = false
                                                    backStack.removeLast()
                                                }
                                            )
                                        }

                                        // === 电影板块 ===
                                        is DesktopRoute.FilmHome -> {
                                            FilmHomeScreen(
                                                onSearchClick = { backStack.add(DesktopRoute.FilmSearch) },
                                                onMovieClick = { movieId ->
                                                    backStack.add(DesktopRoute.FilmDetail(movieId))
                                                }
                                            )
                                        }

                                        is DesktopRoute.FilmSearch -> {
                                            FilmSearchScreen(
                                                onBackClick = { backStack.removeLast() },
                                                onMovieClick = { movieId ->
                                                    backStack.add(DesktopRoute.FilmDetail(movieId))
                                                }
                                            )
                                        }

                                        is DesktopRoute.FilmDetail -> {
                                            // 使用 route 本身作为 key，实现 ViewModel 实例隔离
                                            val viewModel: FilmDetailViewModel = koinViewModel(
                                                key = desktopRoute.toString(),
                                                parameters = { parametersOf(desktopRoute.movieId) }
                                            )
                                            FilmDetailScreen(
                                                viewModel = viewModel,
                                                onBackClick = { backStack.removeLast() },
                                                onNavigateToPlayer = { url, title ->
                                                    val episodes = viewModel.episodesCache.value
                                                    backStack.add(DesktopRoute.FilmPlayer(url, title, episodes))
                                                }
                                            )
                                        }

                                        is DesktopRoute.FilmPlayer -> {
                                            FilmPlayerScreen(
                                                initialUrl = desktopRoute.url,
                                                seriesTitle = desktopRoute.title,
                                                episodes = desktopRoute.episodes,
                                                onBack = { 
                                                    isFullScreen = false
                                                    backStack.removeLast() 
                                                },
                                                onFullScreenChange = { full -> isFullScreen = full }
                                            )
                                        }

                                        // === 国产板块 ===
                                        is DesktopRoute.DomesticHome -> {
                                            DomesticHomeScreen(
                                                onSearchClick = { backStack.add(DesktopRoute.DomesticSearch) },
                                                onMediaClick = { media ->
                                                    backStack.add(DesktopRoute.DomesticDetail(media.title))
                                                }
                                            )
                                        }

                                        is DesktopRoute.DomesticSearch -> {
                                            DomesticSearchScreen(
                                                onBackClick = { backStack.removeLast() },
                                                onMediaClick = { media ->
                                                    backStack.add(DesktopRoute.DomesticDetail(media.title))
                                                }
                                            )
                                        }

                                        is DesktopRoute.DomesticDetail -> {
                                            val viewModel: DomesticDetailViewModel = koinViewModel(
                                                key = desktopRoute.toString(),
                                                parameters = { parametersOf(desktopRoute.title) }
                                            )
                                            DomesticDetailScreen(
                                                viewModel = viewModel,
                                                onBack = { backStack.removeLast() },
                                                onPlay = { url, title, episodes ->
                                                    backStack.add(DesktopRoute.DomesticPlayer(url, title, episodes))
                                                }
                                            )
                                        }

                                        is DesktopRoute.DomesticPlayer -> {
                                            DomesticPlayerScreen(
                                                initialUrl = desktopRoute.url,
                                                seriesTitle = desktopRoute.title,
                                                episodes = desktopRoute.episodes,
                                                onBack = { 
                                                    isFullScreen = false
                                                    backStack.removeLast() 
                                                },
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
    currentScreen: DesktopRoute,
    onNavigate: (DesktopRoute) -> Unit,
) {
    val screens = listOf(
        DesktopNavItem(DesktopRoute.RadioList, "电台", Icons.Default.Radio),
        DesktopNavItem(DesktopRoute.FilmHome, "电影", Icons.Default.Face),
        DesktopNavItem(DesktopRoute.DomesticHome, "国产", Icons.Default.Tv)
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


    }
}


private data class DesktopNavItem(
    val route: DesktopRoute,
    val label: String,
    val icon: ImageVector
)
