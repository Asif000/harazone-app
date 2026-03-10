package com.harazone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.debug.DevSeeder
import com.harazone.di.appModule
import com.harazone.ui.map.MapScreen
import com.harazone.ui.theme.AreaDiscoveryTheme
import io.ktor.client.HttpClient
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun App(
    platformConfig: org.koin.dsl.KoinAppDeclaration = {},
    onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Boolean = { _, _, _ -> false },
    seedPersona: DevSeeder.Persona? = null,
    forceSeed: Boolean = false,
) {
    KoinApplication(application = { platformConfig(); modules(appModule()) }) {
        val httpClient: HttpClient = koinInject()
        // TODO(BACKLOG-HIGH): Coil disk cache not configured — iOS has no default disk cache, thumbnails re-fetched every session
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient)) }
                .build()
        }
        // Debug-only: seed test data using the SAME Koin-managed database
        // so ViewModels see the data on their first Flow emission.
        if (seedPersona != null) {
            val db: AreaDiscoveryDatabase = koinInject()
            remember(seedPersona, forceSeed) {
                DevSeeder.seedDirect(db, seedPersona, forceSeed)
                true
            }
        }
        AreaDiscoveryTheme {
            MapScreen(onNavigateToMaps = onNavigateToMaps)
        }
    }
}
