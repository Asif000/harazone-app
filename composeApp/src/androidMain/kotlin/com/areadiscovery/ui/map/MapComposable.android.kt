package com.areadiscovery.ui.map

import android.animation.ValueAnimator
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.areadiscovery.BuildKonfig
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.ui.theme.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private val MAP_STYLE_URL = "https://api.maptiler.com/maps/streets-v2-dark/style.json?key=${BuildKonfig.MAPTILER_API_KEY}"

@Composable
actual fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
    pois: List<POI>,
    activeVibe: Vibe,
    onPoiSelected: (POI?) -> Unit,
    onMapRenderFailed: () -> Unit,
    onCameraIdle: (lat: Double, lng: Double) -> Unit,
) {
    val context = LocalContext.current
    val currentOnPoiSelected = rememberUpdatedState(onPoiSelected)
    val currentOnMapRenderFailed = rememberUpdatedState(onMapRenderFailed)
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)

    val isDestroyed = remember { booleanArrayOf(false) }
    val styleLoaded = remember { mutableStateOf(false) }
    val styleLoading = remember { booleanArrayOf(false) }

    val symbolManagerRef = remember { arrayOfNulls<SymbolManager>(1) }
    val symbolsRef = remember { mutableListOf<Symbol>() }
    val symbolPoiMap = remember { mutableMapOf<Long, POI>() }
    val glowLayerIds = remember { mutableListOf<String>() }
    val glowSourceIds = remember { mutableListOf<String>() }
    val glowAnimators = remember { mutableListOf<ValueAnimator>() }
    val pinAnimatorsRef = remember { mutableListOf<ValueAnimator>() }
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val styleRef = remember { arrayOfNulls<Style>(1) }
    val cameraIdleListenerRef = remember { arrayOfNulls<MapLibreMap.OnCameraIdleListener>(1) }

    val mapView = remember {
        MapLibre.getInstance(context)
        val options = org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
        MapView(context, options).apply {
            onCreate(Bundle())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addOnDidFailLoadingMapListener { currentOnMapRenderFailed.value() }
        }
    }

    // Camera + style setup
    LaunchedEffect(latitude, longitude) {
        if (latitude == 0.0 && longitude == 0.0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (isDestroyed[0]) return@getMapAsync
            mapRef[0] = map
            if (styleLoaded.value) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoomLevel))
            } else if (!styleLoading[0]) {
                styleLoading[0] = true

                map.setStyle(MAP_STYLE_URL) { style ->
                    if (!isDestroyed[0]) {
                        styleLoaded.value = true
                        styleRef[0] = style
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoomLevel))

                        // Initialize SymbolManager
                        val sm = SymbolManager(mapView, map, style).apply {
                            iconAllowOverlap = true
                            textAllowOverlap = true
                        }
                        symbolManagerRef[0] = sm

                        sm.addClickListener { symbol ->
                            symbolPoiMap[symbol.id]?.let { poi ->
                                currentOnPoiSelected.value(poi)
                            }
                            true
                        }

                        map.addOnMapClickListener {
                            currentOnPoiSelected.value(null)
                            true
                        }

                        val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
                            val target = map.cameraPosition.target ?: return@OnCameraIdleListener
                            currentOnCameraIdle.value(target.latitude, target.longitude)
                        }
                        map.addOnCameraIdleListener(cameraIdleListener)
                        cameraIdleListenerRef[0] = cameraIdleListener
                    }
                }
            }
        }
    }

    // POI pins + glow zones — react to pois + activeVibe + style loaded changes
    LaunchedEffect(pois, activeVibe, styleLoaded.value) {
        if (!styleLoaded.value) return@LaunchedEffect
        val map = mapRef[0] ?: return@LaunchedEffect
        val style = styleRef[0] ?: return@LaunchedEffect
        val sm = symbolManagerRef[0] ?: return@LaunchedEffect

        // Clear previous symbols
        sm.delete(symbolsRef.toList())
        symbolsRef.clear()
        symbolPoiMap.clear()

        // Clear previous pin animators
        pinAnimatorsRef.forEach { it.cancel() }
        pinAnimatorsRef.clear()

        // Clear previous glow layers
        glowAnimators.forEach { it.cancel() }
        glowAnimators.clear()
        for (layerId in glowLayerIds) {
            style.removeLayer(layerId)
        }
        glowLayerIds.clear()
        for (sourceId in glowSourceIds) {
            style.removeSource(sourceId)
        }
        glowSourceIds.clear()

        val vibeColor = activeVibe.toColor()
        val vibeColorArgb = vibeColor.toArgb()

        // Filter POIs by active vibe; if no POIs have vibes assigned, show all
        val hasAnyVibes = pois.any { it.vibe.isNotBlank() }
        val filteredPois = if (hasAnyVibes) {
            pois.filter { it.vibe.equals(activeVibe.name, ignoreCase = true) }
        } else {
            pois
        }
            .filter { it.latitude != null && it.longitude != null }

        // Generate icon bitmap
        val iconKey = "vibe_icon_${activeVibe.name}"
        if (style.getImage(iconKey) == null) {
            val size = 48
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = vibeColorArgb
                this.style = Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

            // White inner circle for contrast
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                this.style = Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 4f, innerPaint)
            style.addImage(iconKey, bitmap)
        }

        // Add symbols with stagger (runs inside LaunchedEffect — auto-cancelled on restart)
        val pinAnimators = mutableListOf<ValueAnimator>()
        for ((i, poi) in filteredPois.withIndex()) {
            if (isDestroyed[0]) return@LaunchedEffect
            delay(50L * i)

            val symbol = sm.create(
                SymbolOptions()
                    .withLatLng(LatLng(poi.latitude!!, poi.longitude!!))
                    .withIconImage(iconKey)
                    .withIconSize(0.1f)
                    .withTextField(poi.name)
                    .withTextSize(10f)
                    .withTextColor("#FAFAFA")
                    .withTextOffset(arrayOf(0f, 1.5f))
            )
            symbolsRef.add(symbol)
            symbolPoiMap[symbol.id] = poi

            // Animate icon size 0.1 -> 1.0
            val animator = ValueAnimator.ofFloat(0.1f, 1.0f).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    if (!isDestroyed[0]) {
                        symbol.iconSize = anim.animatedValue as Float
                        sm.update(symbol)
                    }
                }
            }
            animator.start()
            pinAnimators.add(animator)
        }

        pinAnimatorsRef.addAll(pinAnimators)

        // Add glow zones
        if (filteredPois.size >= 2) {
            val clusters = clusterPois(filteredPois)
            val vibeHex = activeVibe.accentColorHex

            for ((idx, cluster) in clusters.withIndex()) {
                val centroid = cluster.first
                val sourceId = "glow_source_$idx"
                val layerId = "glow_layer_$idx"

                val feature = Feature.fromGeometry(
                    Point.fromLngLat(centroid.second, centroid.first)
                )
                val source = GeoJsonSource(sourceId, FeatureCollection.fromFeature(feature))
                style.addSource(source)
                glowSourceIds.add(sourceId)

                val layer = CircleLayer(layerId, sourceId).withProperties(
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.exponential(2f),
                            Expression.zoom(),
                            Expression.stop(10, 30f),
                            Expression.stop(14, 80f),
                            Expression.stop(17, 160f),
                        )
                    ),
                    PropertyFactory.circleColor(vibeHex),
                    PropertyFactory.circleOpacity(0.25f),
                    PropertyFactory.circleBlur(1.2f),
                )
                // Add layer below symbols
                style.addLayerBelow(layer, sm.layerId)
                glowLayerIds.add(layerId)

                // Breathing animation
                val glowAnimator = ValueAnimator.ofFloat(0.20f, 0.35f).apply {
                    duration = 2000
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { anim ->
                        if (!isDestroyed[0]) {
                            style.getLayer(layerId)?.let { l ->
                                (l as? CircleLayer)?.setProperties(
                                    PropertyFactory.circleOpacity(anim.animatedValue as Float)
                                )
                            }
                        }
                    }
                }
                glowAnimator.start()
                glowAnimators.add(glowAnimator)
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
            pinAnimatorsRef.forEach { it.cancel() }
            glowAnimators.forEach { it.cancel() }
            symbolsRef.clear()
            symbolPoiMap.clear()
            context.unregisterComponentCallbacks(lowMemoryCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraIdleListenerRef[0]?.let { mapRef[0]?.removeOnCameraIdleListener(it) }
            symbolManagerRef[0]?.onDestroy()
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}

/** Clusters POIs within 0.005 degree proximity. Returns list of (centroid, pois). */
private fun clusterPois(pois: List<POI>): List<Pair<Pair<Double, Double>, List<POI>>> {
    val valid = pois.filter { it.latitude != null && it.longitude != null }
    val used = BooleanArray(valid.size)
    val clusters = mutableListOf<Pair<Pair<Double, Double>, List<POI>>>()
    for (i in valid.indices) {
        if (used[i]) continue
        val cluster = mutableListOf(valid[i])
        used[i] = true
        for (j in i + 1 until valid.size) {
            if (used[j]) continue
            val dist = kotlin.math.abs(valid[i].latitude!! - valid[j].latitude!!) +
                kotlin.math.abs(valid[i].longitude!! - valid[j].longitude!!)
            if (dist < 0.005) {
                cluster.add(valid[j])
                used[j] = true
            }
        }
        if (cluster.size >= 2) {
            val centroidLat = cluster.sumOf { it.latitude!! } / cluster.size
            val centroidLng = cluster.sumOf { it.longitude!! } / cluster.size
            clusters.add((centroidLat to centroidLng) to cluster)
        }
    }
    return clusters
}
