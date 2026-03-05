package com.areadiscovery.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.areadiscovery.ui.chat.ChatPlaceholderScreen
import com.areadiscovery.ui.map.MapScreen
import com.areadiscovery.ui.map.MapViewModel
import com.areadiscovery.ui.saved.SavedPlaceholderScreen
import com.areadiscovery.ui.summary.SummaryScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = SummaryRoute,
        modifier = modifier,
    ) {
        composable<SummaryRoute> {
            SummaryScreen(
                // TODO: pass query to ChatScreen when implemented (Story 4.x)
                onNavigateToChat = { _ ->
                    navController.navigate(ChatRoute) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable<MapRoute> {
            MapScreen(viewModel = mapViewModel)
        }
        composable<ChatRoute> {
            ChatPlaceholderScreen()
        }
        composable<SavedRoute> {
            SavedPlaceholderScreen()
        }
    }
}
