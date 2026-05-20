package com.example.kmp_demo.features.radio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.kmp_demo.LocalScaffoldPadding
import com.example.kmp_demo.features.radio.ui.components.MiniPlayerBar
import com.example.kmp_demo.features.radio.ui.list.RadioListScreen
import com.example.kmp_demo.features.radio.ui.list.RadioListViewModel
import com.example.kmp_demo.features.radio.ui.player.PlayerDetailScreen
import com.example.kmp_demo.features.radio.ui.search.RadioSearchScreen
import org.koin.compose.viewmodel.koinViewModel

object RadioRoutes {
    const val graph = "radio_graph"
    const val list = "radio_list"
    const val search = "radio_search"
    const val player = "radio_player"
}

fun NavGraphBuilder.radioGraph(navController: NavHostController) {
    navigation(startDestination = RadioRoutes.list, route = RadioRoutes.graph) {
        composable(RadioRoutes.list) {
            val viewModel: RadioListViewModel = koinViewModel()
            // 使用 Box 叠加，让 MiniPlayerBar 真正悬浮在列表之上
            Box(
                modifier = Modifier.padding(LocalScaffoldPadding.current)
            ) {
                RadioListScreen(
                    viewModel = viewModel,
                    onNavigateToSearch = { navController.navigate(RadioRoutes.search) },
                    onNavigateToPlayer = { navController.navigate(RadioRoutes.player) }
                )

                // 将 MiniPlayerBar 放在 Box 的底部，实现悬浮效果
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    MiniPlayerBar(
                        playerManager = viewModel.playerManager,
                        onClick = { navController.navigate(RadioRoutes.player) }
                    )
                }
            }
        }

        composable(RadioRoutes.search) {
            RadioSearchScreen(
                viewModel = koinViewModel(),
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate(RadioRoutes.player) }
            )
        }

        composable(RadioRoutes.player) {
            val listViewModel: RadioListViewModel = koinViewModel()
            PlayerDetailScreen(
                playerManager = listViewModel.playerManager,
                onClose = { navController.popBackStack() }
            )
        }
    }
}
