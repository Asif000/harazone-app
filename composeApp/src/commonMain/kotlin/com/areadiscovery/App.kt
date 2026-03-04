package com.areadiscovery

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.areadiscovery.di.appModule
import com.areadiscovery.ui.navigation.AppNavigation
import com.areadiscovery.ui.navigation.BottomNavBar
import com.areadiscovery.ui.theme.AreaDiscoveryTheme
import org.koin.compose.KoinApplication

@Composable
fun App() {
    KoinApplication(application = { modules(appModule()) }) {
        AreaDiscoveryTheme {
            val navController = rememberNavController()
            Scaffold(
                bottomBar = { BottomNavBar(navController) },
            ) { innerPadding ->
                AppNavigation(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
