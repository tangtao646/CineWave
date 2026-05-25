package com.example.kmp_demo.features.radio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            val playerUiState by viewModel.playerManager.uiState.collectAsState()
            val hasCurrentStation = playerUiState.currentStation != null

            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(LocalScaffoldPadding.current)
            ) {
                RadioListScreen(
                    modifier = Modifier.weight(1f),
                    viewModel = viewModel,
                    onNavigateToSearch = { navController.navigate(RadioRoutes.search) },
                    onNavigateToPlayer = { navController.navigate(RadioRoutes.player) }
                )

                if (hasCurrentStation) {
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
