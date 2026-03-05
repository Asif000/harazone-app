package com.areadiscovery.ui.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private const val MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json"
private const val DEFAULT_ZOOM = 14.0

@Composable
actual fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
) {
    val context = LocalContext.current
    val currentLatitude = rememberUpdatedState(latitude)
    val currentLongitude = rememberUpdatedState(longitude)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { map ->
                map.setStyle(MAP_STYLE_URL)
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(currentLatitude.value, currentLongitude.value),
                        zoomLevel,
                    ),
                )
            }
        },
    )

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}
