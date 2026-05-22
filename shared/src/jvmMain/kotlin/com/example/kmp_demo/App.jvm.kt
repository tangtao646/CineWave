package com.example.kmp_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.DoneSegment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.example.kmp_demo.core.initializeCoil
import com.example.kmp_demo.core.navigation.decodeNavParam
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.core.security.SensitiveWordLoader
import com.example.kmp_demo.features.domestic.DomesticRoutes
import com.example.kmp_demo.features.domestic.ui.DomesticDetailScreen
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticHomeScreen
import com.example.kmp_demo.features.domestic.ui.DomesticSearchScreen
import com.example.kmp_demo.features.film.FilmRoutes
import com.example.kmp_demo.features.film.ui.FilmDetailScreen
import com.example.kmp_demo.features.film.ui.FilmDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmHomeScreen
import com.example.kmp_demo.features.radio.RadioRoutes
import com.example.kmp_demo.features.radio.ui.components.MiniPlayerBar
import com.example.kmp_demo.features.radio.ui.list.RadioListScreen
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
 * 由于 JetBrains Navigation Compose 在 Desktop 上存在
 * SavedStateRegistryController.performRestore(Bundle) 的兼容性问题，
 * 这里使用简单的状态管理替代 NavHost。
 *
 * 注意：Desktop 上 Material3 的 NavigationBarItem 内部使用 animateColorAsState，
 * 而 Oklab 色彩空间在 JetBrains Compose 中未实现，会导致运行时崩溃。
 * 因此这里使用自定义的底部栏实现，避免颜色动画。
 */
@Composable
fun App() {
    KoinContext {
        MaterialTheme {
            // 当前路由，初始为电台列表页
            var currentRoute by remember { mutableStateOf(RadioRoutes.list) }

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

            // 判断当前是否在底部导航的主 tab 页（用于控制底部栏显示/隐藏）
            val isMainTab = currentRoute in listOf(
                RadioRoutes.list,
                FilmRoutes.home,
                DomesticRoutes.home
            )

            Scaffold(
                bottomBar = {
                    // 只在主 tab 页显示底部导航栏
                    if (isMainTab) {
                        DesktopBottomBar(
                            currentScreen = currentRoute,
                            onNavigate = { screen -> currentRoute = screen }
                        )
                    }
                }
            ) { innerPadding ->
                CompositionLocalProvider(LocalScaffoldPadding provides innerPadding) {
                    // 根据当前路由显示对应的页面
                    when {
                        // === 电台板块 ===
                        currentRoute == RadioRoutes.list -> {
                            val viewModel: RadioListViewModel = koinViewModel()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                RadioListScreen(
                                    viewModel = viewModel,
                                    onNavigateToSearch = { currentRoute = RadioRoutes.search },
                                    onNavigateToPlayer = { currentRoute = RadioRoutes.player }
                                )

                                Box(
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    MiniPlayerBar(
                                        playerManager = viewModel.playerManager,
                                        onClick = { currentRoute = RadioRoutes.player }
                                    )
                                }
                            }
                        }

                        currentRoute == RadioRoutes.search -> {
                            RadioSearchScreen(
                                viewModel = koinViewModel(),
                                onBack = { currentRoute = RadioRoutes.list },
                                onNavigateToPlayer = { currentRoute = RadioRoutes.player }
                            )
                        }

                        currentRoute == RadioRoutes.player -> {
                            val listViewModel: RadioListViewModel = koinViewModel()
                            PlayerDetailScreen(
                                playerManager = listViewModel.playerManager,
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

                        // 电影详情页
                        currentRoute.startsWith("film_detail/") -> {
                            val movieId = currentRoute.removePrefix("film_detail/").toIntOrNull() ?: return@CompositionLocalProvider
                            // 使用 currentRoute 作为 key，确保切换不同电影时创建新的 ViewModel
                            val viewModel: FilmDetailViewModel = koinViewModel(
                                key = currentRoute,
                                parameters = { parametersOf(movieId) }
                            )
                            FilmDetailScreen(
                                viewModel = viewModel,
                                onBackClick = { currentRoute = FilmRoutes.home },
                                onNavigateToPlayer = { url, title ->
                                    // TODO: 实现 Desktop 播放器导航
                                }
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

                        currentRoute == DomesticRoutes.search -> {
                            DomesticSearchScreen(onBackClick = {

                            }, onMediaClick = {

                            })
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
                                    // TODO: 实现 Desktop 播放器导航
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop 底部导航栏。
 *
 * 使用自定义实现替代 Material3 的 NavigationBarItem，因为 Desktop 上
 * NavigationBarItem 内部的 animateColorAsState 使用了 Oklab 色彩空间，
 * 而 Oklab 在 JetBrains Compose 中未实现，会导致运行时崩溃。
 */
@Composable
fun DesktopBottomBar(
    currentScreen: String,
    onNavigate: (String) -> Unit
) {
    val screens = listOf(
        DesktopScreen(RadioRoutes.list, "电台", Icons.Default.Radio),
        DesktopScreen(FilmRoutes.home, "电影", Icons.Default.Face),
        DesktopScreen(DomesticRoutes.home, "国产", Icons.Default.Tv)
    )

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            screens.forEach { screen ->
                val selected = screen.route == currentScreen
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onNavigate(screen.route) }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

private data class DesktopScreen(
    val route: String,
    val title: String,
    val icon: ImageVector
)
