package com.areadiscovery

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.areadiscovery.di.appModule
import com.areadiscovery.ui.map.MapScreen
import com.areadiscovery.ui.theme.AreaDiscoveryTheme
import io.ktor.client.HttpClient
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun App(
    platformConfig: org.koin.dsl.KoinAppDeclaration = {},
    onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Boolean = { _, _, _ -> false },
) {
    KoinApplication(application = { platformConfig(); modules(appModule()) }) {
        val httpClient: HttpClient = koinInject()
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient)) }
                .build()
        }
        AreaDiscoveryTheme {
            MapScreen(onNavigateToMaps = onNavigateToMaps)
        }
    }
}
