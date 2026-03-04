package com.areadiscovery.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import kotlin.reflect.KClass

private data class BottomNavItem<T : Any>(
    val label: String,
    val icon: ImageVector,
    val route: T,
    val routeClass: KClass<T>,
)

@Composable
fun BottomNavBar(navController: NavController) {
    val items = remember {
        listOf(
            BottomNavItem("Summary", Icons.Filled.Explore, SummaryRoute, SummaryRoute::class),
            BottomNavItem("Map", Icons.Filled.Map, MapRoute, MapRoute::class),
            BottomNavItem("Chat", Icons.AutoMirrored.Filled.Chat, ChatRoute, ChatRoute::class),
            BottomNavItem("Saved", Icons.Filled.Bookmark, SavedRoute, SavedRoute::class),
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        items.forEach { item ->
            val selected = currentDestination?.hasRoute(item.routeClass) == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(
                        item.route,
                        navOptions {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        },
                    )
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
