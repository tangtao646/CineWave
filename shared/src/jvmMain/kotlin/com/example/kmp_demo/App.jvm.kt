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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.example.kmp_demo.core.initializeCoil
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.core.security.SensitiveWordLoader
import com.example.kmp_demo.features.domestic.ui.DomesticHomeScreen
import com.example.kmp_demo.features.film.ui.FilmHomeScreen
import com.example.kmp_demo.features.radio.ui.components.MiniPlayerBar
import com.example.kmp_demo.features.radio.ui.list.RadioListScreen
import com.example.kmp_demo.features.radio.ui.list.RadioListViewModel
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
            var currentScreen by remember { mutableStateOf("radio") }

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

            Scaffold(
                bottomBar = {
                    DesktopBottomBar(
                        currentScreen = currentScreen,
                        onNavigate = { screen -> currentScreen = screen }
                    )
                }
            ) { innerPadding ->
                CompositionLocalProvider(LocalScaffoldPadding provides innerPadding) {
                    when (currentScreen) {
                        "radio" -> {
                            val viewModel: RadioListViewModel = koinViewModel()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                RadioListScreen(
                                    viewModel = viewModel,
                                    onNavigateToSearch = { /* TODO */ },
                                    onNavigateToPlayer = { /* TODO */ }
                                )

                                Box(
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    MiniPlayerBar(
                                        playerManager = viewModel.playerManager,
                                        onClick = { /* TODO */ }
                                    )
                                }
                            }
                        }
                        "film" -> {
                            FilmHomeScreen(
                                onSearchClick = { /* TODO */ },
                                onMovieClick = { /* TODO */ }
                            )
                        }
                        "domestic" -> {
                            DomesticHomeScreen(
                                onSearchClick = { /* TODO */ },
                                onMediaClick = { /* TODO */ }
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
        DesktopScreen("radio", "电台", Icons.Default.Radio),
        DesktopScreen("film", "电影", Icons.Default.Face),
        DesktopScreen("domestic", "国产", Icons.Default.Tv)
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
