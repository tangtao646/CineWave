package com.example.kmp_demo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.kmp_demo.features.domestic.DomesticRoutes
import com.example.kmp_demo.features.film.FilmRoutes
import com.example.kmp_demo.features.radio.RadioRoutes

val LocalScaffoldPadding = staticCompositionLocalOf { PaddingValues(0.dp) }


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
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                })
        }
    }
}
