package com.areadiscovery.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private const val MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json"

@Composable
actual fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
) {
    val context = LocalContext.current

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.setStyle(MAP_STYLE_URL)
            }
        }
    }

    LaunchedEffect(latitude, longitude) {
        mapView.getMapAsync { map ->
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(latitude, longitude),
                    zoomLevel,
                ),
            )
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )

    val lowMemoryCallback = remember {
        object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}
            override fun onLowMemory() { mapView.onLowMemory() }
            override fun onTrimMemory(level: Int) {}
        }
    }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        context.registerComponentCallbacks(lowMemoryCallback)
        onDispose {
            context.unregisterComponentCallbacks(lowMemoryCallback)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}
