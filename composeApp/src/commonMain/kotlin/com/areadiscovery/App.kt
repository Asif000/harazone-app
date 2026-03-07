package com.areadiscovery

import androidx.compose.runtime.Composable
import com.areadiscovery.di.appModule
import com.areadiscovery.ui.map.MapScreen
import com.areadiscovery.ui.theme.AreaDiscoveryTheme
import org.koin.compose.KoinApplication

@Composable
fun App(
    platformConfig: org.koin.dsl.KoinAppDeclaration = {},
    onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Boolean = { _, _, _ -> false },
) {
    KoinApplication(application = { platformConfig(); modules(appModule()) }) {
        AreaDiscoveryTheme {
            MapScreen(onNavigateToMaps = onNavigateToMaps)
        }
    }
}
