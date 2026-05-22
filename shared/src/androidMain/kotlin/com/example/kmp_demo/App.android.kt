package com.example.kmp_demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.kmp_demo.features.domestic.domesticGraph
import com.example.kmp_demo.features.domestic.DomesticRoutes
import com.example.kmp_demo.features.film.FilmRoutes
import com.example.kmp_demo.features.radio.RadioRoutes
import com.example.kmp_demo.features.film.filmGraph
import com.example.kmp_demo.features.radio.radioGraph
import org.koin.compose.KoinContext

/**
 * Android 平台的 App 入口。
 *
 * 使用 NavHost 实现标准导航，与 commonMain 中的导航图无缝集成。
 * 底部导航栏的显隐由每个页面通过 [LocalShowBottomBar] 自行控制。
 */
@Composable
fun App() {
    KoinContext {
        MaterialTheme {
            val navController = rememberNavController()
            val showBottomBar =
                navController.currentBackStackEntryAsState().value?.destination?.route in listOf(
                    RadioRoutes.list,
                    FilmRoutes.home,
                    DomesticRoutes.home,
                )
            Scaffold(
                bottomBar = {
                    // 由每个页面通过 CompositionLocalProvider(LocalShowBottomBar provides false) 自行控制
                    if (showBottomBar) {
                        AppBottomBar(navController = navController)
                    }
                }
            ) { innerPadding ->
                CompositionLocalProvider(LocalScaffoldPadding provides innerPadding) {
                    NavHost(
                        navController = navController,
                        startDestination = RadioRoutes.graph,
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        radioGraph(navController = navController)
                        filmGraph(navController = navController)
                        domesticGraph(navController = navController)
                    }
                }
            }
        }
    }
}
