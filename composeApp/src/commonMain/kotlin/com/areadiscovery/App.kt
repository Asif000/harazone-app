package com.areadiscovery

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.areadiscovery.di.appModule
import com.areadiscovery.ui.navigation.AppNavigation
import com.areadiscovery.ui.navigation.BottomNavBar
import com.areadiscovery.ui.theme.AreaDiscoveryTheme
import org.koin.compose.KoinApplication

@Composable
fun App(platformConfig: org.koin.dsl.KoinAppDeclaration = {}) {
    KoinApplication(application = { platformConfig(); modules(appModule()) }) {
        AreaDiscoveryTheme {
            val navController = rememberNavController()
            var mapPoiCount by remember { mutableIntStateOf(0) }
            Scaffold(
                bottomBar = { BottomNavBar(navController, mapPoiCount = mapPoiCount) },
            ) { innerPadding ->
                AppNavigation(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                    onMapPoiCountChanged = { mapPoiCount = it },
                )
            }
        }
    }
}
