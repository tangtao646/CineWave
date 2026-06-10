package com.example.kmp_demo.features.film

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.example.kmp_demo.core.navigation.decodeNavParam
import com.example.kmp_demo.core.navigation.encodeNavParam
import com.example.kmp_demo.features.domestic.DomesticRoutes
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmDetailScreen
import com.example.kmp_demo.features.film.ui.FilmDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmHomeScreen
import com.example.kmp_demo.features.film.ui.player.FilmPlayerScreen
import com.example.kmp_demo.features.film.ui.search.FilmSearchScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Map.entry

/**
 * 电影板块路由协议
 */
object FilmRoutes {
    const val graph = "film_graph"
    const val home = "film_home"
    const val search = "film_search"
    const val detail = "film_detail/{movieId}"
    const val player = "film_player/{url}/{title}"

    fun detail(movieId: Int): String = "film_detail/$movieId"

    fun player(url: String, title: String): String {
        val encodedUrl = url.encodeNavParam()
        val encodedTitle = title.encodeNavParam()
        return "film_player/$encodedUrl/$encodedTitle"
    }
}

/**
 * 封装电影板块的导航图
 */
fun NavGraphBuilder.filmGraph(
    navController: NavHostController,
) {
    navigation(
        route = FilmRoutes.graph,
        startDestination = FilmRoutes.home
    ) {
        composable(FilmRoutes.home) {
            FilmHomeScreen(
                onSearchClick = { navController.navigate(FilmRoutes.search) },
                onMovieClick = { movieId ->
                    navController.navigate(FilmRoutes.detail(movieId))
                    //navController.navigate(FilmRoutes.player("https://vip.ffzy-play6.com/20221027/2369_b83d9749/index.m3u8","test"))
                },
            )
        }

        composable(FilmRoutes.search) {
            FilmSearchScreen(
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(FilmRoutes.detail(movieId)) }
            )
        }

        composable(
            route = FilmRoutes.detail,
            arguments = listOf(
                navArgument("movieId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            // 从 NavBackStackEntry 提取 movieId，通过 parametersOf 传入 ViewModel
            val args = backStackEntry.arguments ?: return@composable
            val movieId = NavType.IntType[args, "movieId"] ?: -1
//

            //  绑定到整个 graph 作用域
            val graphEntry =
                remember(backStackEntry) { navController.getBackStackEntry(FilmRoutes.graph) }
            // 首次创建，通过 parametersOf 把 title 喂给 Koin 闭包
            val viewModel: FilmDetailViewModel = koinViewModel(
                viewModelStoreOwner = graphEntry,
                parameters = { parametersOf(movieId) }
            )

            FilmDetailScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToPlayer = { url, title, _ ->
                    navController.navigate(FilmRoutes.player(url, title))
                }
            )
        }

        composable(
            route = FilmRoutes.player,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments ?: return@composable
            val url = NavType.StringType[args, "url"]?.decodeNavParam() ?: ""
            val title = NavType.StringType[args, "title"]?.decodeNavParam() ?: ""


            val graphEntry =
                remember(backStackEntry) { navController.getBackStackEntry(FilmRoutes.graph) }
            val detailViewModel: FilmDetailViewModel =
                koinViewModel(viewModelStoreOwner = graphEntry)
            val episodes by detailViewModel.episodesCache.collectAsState()


            FilmPlayerScreen(
                initialUrl = url,
                seriesTitle = title,
                episodes = episodes,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
