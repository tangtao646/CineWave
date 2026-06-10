package com.example.kmp_demo.features.domestic

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
import com.example.kmp_demo.features.domestic.ui.DomesticDetailScreen
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticHomeScreen
import com.example.kmp_demo.features.domestic.ui.DomesticSearchScreen
import com.example.kmp_demo.features.domestic.ui.player.DomesticPlayerScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 国内影视板块路由协议。
 *
 * 播放器路由：
 * - [player]：旧路由，保留兼容（直接使用 [FilmPlayerScreen] 的简单播放）
 * - [playerScreen]：新路由，使用 [DomesticPlayerScreen] 支持沉浸式选集
 */
object DomesticRoutes {
    const val graph = "domestic_graph"
    const val home = "domestic_home"
    const val search = "domestic_search"
    const val detail = "domestic_detail/{title}"
    const val player = "domestic_player/{url}/{title}"
    const val playerScreen = "domestic_player_screen/{url}/{title}"

    fun detail(title: String): String = "domestic_detail/${title.encodeNavParam()}"

    fun player(url: String, title: String): String =
        "domestic_player/${url.encodeNavParam()}/${title.encodeNavParam()}"

    fun playerScreen(url: String, title: String): String =
        "domestic_player_screen/${url.encodeNavParam()}/${title.encodeNavParam()}"
}

/**
 * 封装国内影视板块的导航图。
 */
fun NavGraphBuilder.domesticGraph(navController: NavHostController) {
    navigation(route = DomesticRoutes.graph, startDestination = DomesticRoutes.home) {
        // 首页：搜索框 + 瀑布流
        composable(DomesticRoutes.home) {
            DomesticHomeScreen(
                onSearchClick = {
                    navController.navigate(DomesticRoutes.search)
                },
                onMediaClick = { media ->
                    navController.navigate(DomesticRoutes.detail(media.title))
                }
            )
        }

        // 搜索页
        composable(DomesticRoutes.search) {
            DomesticSearchScreen(
                onBackClick = { navController.popBackStack() },
                onMediaClick = { media ->
                    navController.navigate(DomesticRoutes.detail(media.title))
                }
            )
        }

        // 详情页
        composable(
            route = DomesticRoutes.detail,
            arguments = listOf(navArgument("title") { type = NavType.StringType })
        ) { entry ->
            val args = entry.arguments ?: return@composable
            val title = NavType.StringType[args, "title"]?.decodeNavParam() ?: return@composable

            //  绑定到整个 graph 作用域
            val graphEntry =
                remember(entry) { navController.getBackStackEntry(DomesticRoutes.graph) }
            // 首次创建，通过 parametersOf 把 title 喂给 Koin 闭包
            val viewModel: DomesticDetailViewModel = koinViewModel(
                viewModelStoreOwner = graphEntry,
                parameters = { parametersOf(title) }
            )
            DomesticDetailScreen(
                title = title,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPlay = { url, title, episodes ->
                    // 通过共享 ViewModel 缓存剧集列表，避免序列化到路由参数
                    navController.navigate(DomesticRoutes.playerScreen(url, title))
                }
            )
        }


        // 新播放器页（支持沉浸式选集）
        composable(
            route = DomesticRoutes.playerScreen,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val url = NavType.StringType[args, "url"]?.decodeNavParam() ?: ""
            val title = NavType.StringType[args, "title"]?.decodeNavParam() ?: ""

            // 同样绑定到整个 graph 作用域
            val graphEntry =
                remember(backStackEntry) { navController.getBackStackEntry(DomesticRoutes.graph) }
            val detailViewModel: DomesticDetailViewModel =
                koinViewModel(viewModelStoreOwner = graphEntry)
            val episodes by detailViewModel.episodesCache.collectAsState()

            DomesticPlayerScreen(
                initialUrl = url,
                seriesTitle = title,
                episodes = episodes,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
