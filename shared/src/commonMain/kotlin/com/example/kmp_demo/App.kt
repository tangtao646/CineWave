package com.example.kmp_demo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.example.kmp_demo.core.initializeCoil
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.core.security.SensitiveWordLoader
import com.example.kmp_demo.features.domestic.DomesticRoutes
import com.example.kmp_demo.features.domestic.domesticGraph
import com.example.kmp_demo.features.film.FilmRoutes
import com.example.kmp_demo.features.film.filmGraph
import com.example.kmp_demo.features.radio.RadioRoutes
import com.example.kmp_demo.features.radio.radioGraph
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

val LocalScaffoldPadding = staticCompositionLocalOf { PaddingValues(0.dp) }

@Composable
fun App() {
    // ✅ 核心改动：删掉外层的 KoinApplication，只保留 KoinContext。
    // 它会自动拾取你在 Android MainApplication 里已经初始化好的全局 Koin 上下文
    KoinContext {
        MaterialTheme {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()

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
                    val currentRoute = navBackStackEntry?.destination?.route
                    val shouldShowBottomBar = currentRoute?.contains("_player") == false
                    if (shouldShowBottomBar) {
                        AppBottomBar(navController = navController)
                    }
                }
            ) { innerPadding ->
                CompositionLocalProvider(LocalScaffoldPadding provides innerPadding) {
                    NavHost(
                        navController = navController,
                        startDestination = RadioRoutes.graph,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        radioGraph(navController)
                        filmGraph(navController)
                        domesticGraph(navController)
                    }
                }
            }
        }
    }
}

sealed class BottomBarScreen(val route: String, val title: String, val icon: ImageVector) {
    object Radio : BottomBarScreen(RadioRoutes.graph, "电台", Icons.Default.Radio)
    object Film : BottomBarScreen(FilmRoutes.graph, "电影", Icons.Default.Face)
    object Domestic : BottomBarScreen(DomesticRoutes.graph, "国产", Icons.Default.Tv)
}

@Composable
fun AppBottomBar(navController: NavHostController) {
    val screens = listOf(BottomBarScreen.Radio, BottomBarScreen.Film, BottomBarScreen.Domestic)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        screens.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                label = { Text(text = screen.title) },
                icon = { Icon(imageVector = screen.icon, contentDescription = null) },
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().route ?: return@navigate) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
        }
    }
}