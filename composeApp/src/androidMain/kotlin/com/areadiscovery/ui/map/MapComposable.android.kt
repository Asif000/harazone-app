package com.areadiscovery.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.areadiscovery.domain.model.POI
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.annotations.MarkerOptions

private const val MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json"

@Composable
actual fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
    pois: List<POI>,
) {
    val context = LocalContext.current

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.setStyle(MAP_STYLE_URL)
            }
            // POI accessibility is served by Story 3.4's list view (POIListView)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
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

    // TODO: Replace default pin markers with custom icons per POI type using
    //  org.maplibre.gl:android-plugin-annotation-v9 SymbolManager (deferred to next cycle)
    LaunchedEffect(pois) {
        mapView.getMapAsync { map ->
            map.markers.forEach { map.removeMarker(it) }
            pois.filter { it.latitude != null && it.longitude != null }.forEach { poi ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(poi.latitude!!, poi.longitude!!))
                        .title(poi.name)
                        .snippet(poi.type)
                )
            }
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
