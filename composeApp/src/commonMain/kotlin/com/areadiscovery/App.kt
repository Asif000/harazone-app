package com.areadiscovery

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.areadiscovery.di.appModule
import com.areadiscovery.ui.map.MapUiState
import com.areadiscovery.ui.map.MapViewModel
import com.areadiscovery.ui.navigation.AppNavigation
import com.areadiscovery.ui.navigation.BottomNavBar
import com.areadiscovery.ui.theme.AreaDiscoveryTheme
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    KoinApplication(application = { modules(appModule()) }) {
        AreaDiscoveryTheme {
            val navController = rememberNavController()
            val mapViewModel: MapViewModel = koinViewModel()
            val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()
            val mapPoiCount = (mapUiState as? MapUiState.Ready)?.pois?.size ?: 0
            Scaffold(
                bottomBar = { BottomNavBar(navController, mapPoiCount = mapPoiCount) },
            ) { innerPadding ->
                AppNavigation(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                    mapViewModel = mapViewModel,
                )
            }
        }
    }
}
