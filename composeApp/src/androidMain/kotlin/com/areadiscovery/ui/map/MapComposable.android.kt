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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.areadiscovery.BuildKonfig
import com.areadiscovery.domain.model.POI
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private val MAP_STYLE_URL = "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildKonfig.MAPTILER_API_KEY}"

@Composable
actual fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
    pois: List<POI>,
    onPoiSelected: (POI?) -> Unit,
) {
    val context = LocalContext.current

    val poiMarkers = remember { mutableListOf<Marker>() }
    val poiMarkerMap = remember { mutableMapOf<Marker, POI>() }
    val poiVersion = remember { intArrayOf(0) }
    val isDestroyed = remember { booleanArrayOf(false) }

    val styleLoaded = remember { booleanArrayOf(false) }
    val styleLoading = remember { booleanArrayOf(false) }
    val pendingPois = remember { mutableListOf<POI>() }

    val mapView = remember {
        /* Initialize MapLibre singleton — return value unused (side-effect only) */
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
            // POI accessibility is served by Story 3.4's list view (POIListView)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    LaunchedEffect(latitude, longitude) {
        // Guard: skip camera move until GPS resolves (avoid null island)
        if (latitude == 0.0 && longitude == 0.0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (isDestroyed[0]) return@getMapAsync
            if (styleLoaded[0]) {
                // Fast path: style already loaded (e.g. location update after initial render)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoomLevel))
            } else if (!styleLoading[0]) {
                // Authoritative path: set style then move camera inside loaded callback.
                // Guard: only call setStyle once — skip if already loading.
                styleLoading[0] = true
                map.setStyle(MAP_STYLE_URL) { _ ->
                    if (!isDestroyed[0]) {
                        styleLoaded[0] = true
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoomLevel))
                        // Set click listeners once — map is fully ready here
                        map.setOnMarkerClickListener { marker ->
                            onPoiSelected(poiMarkerMap[marker])
                            true // consume click, prevent default info window
                        }
                        map.addOnMapClickListener {
                            onPoiSelected(null) // deselect on blank tap
                            true
                        }
                        // Flush any POI markers that arrived before style loaded
                        if (pendingPois.isNotEmpty()) {
                            val poisToAdd = pendingPois.toList()
                            pendingPois.clear()
                            poisToAdd.filter { it.latitude != null && it.longitude != null }.forEach { poi ->
                                val marker = map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(poi.latitude!!, poi.longitude!!))
                                        .title(poi.name)
                                        .snippet(poi.type)
                                )
                                poiMarkers.add(marker)
                                poiMarkerMap[marker] = poi
                            }
                        }
                    }
                }
            }
            // If style is loading (map.style != null but !styleLoaded), the setStyle callback
            // will move the camera to the latest lat/lng when it fires.
        }
    }

    // TODO: Replace default pin markers with custom icons per POI type using
    //  org.maplibre.gl:android-plugin-annotation-v9 SymbolManager (deferred to next cycle)
    LaunchedEffect(pois) {
        val version = ++poiVersion[0]
        if (!styleLoaded[0]) {
            // Style not ready — queue POIs for the setStyle callback to flush
            pendingPois.clear()
            pendingPois.addAll(pois)
            return@LaunchedEffect
        }
        mapView.getMapAsync { map ->
            // Discard callback if map was destroyed or pois changed while getMapAsync was queued
            if (isDestroyed[0] || poiVersion[0] != version) return@getMapAsync
            // Remove only POI markers (preserves any non-POI markers added by future stories)
            poiMarkers.forEach { map.removeMarker(it) }
            poiMarkers.clear()
            poiMarkerMap.clear()
            pois.filter { it.latitude != null && it.longitude != null }.forEach { poi ->
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(poi.latitude!!, poi.longitude!!))
                        .title(poi.name)
                        .snippet(poi.type)
                )
                poiMarkers.add(marker)
                poiMarkerMap[marker] = poi
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        context.registerComponentCallbacks(lowMemoryCallback)
        onDispose {
            isDestroyed[0] = true
            poiMarkers.clear()
            poiMarkerMap.clear()
            context.unregisterComponentCallbacks(lowMemoryCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}
