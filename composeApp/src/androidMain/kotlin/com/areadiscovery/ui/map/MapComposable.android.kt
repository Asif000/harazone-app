package com.areadiscovery.ui.map

import android.animation.ValueAnimator
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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
    cameraMoveId: Int,
    pois: List<POI>,
    activeVibe: Vibe?,
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
    val lastFittedPois = remember { mutableStateOf<List<POI>>(emptyList()) }
    val suppressCameraIdle = remember { booleanArrayOf(false) }

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
    LaunchedEffect(latitude, longitude, cameraMoveId) {
        if (latitude == 0.0 && longitude == 0.0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (isDestroyed[0]) return@getMapAsync
            mapRef[0] = map
            if (styleLoaded.value) {
                suppressCameraIdle[0] = true
                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)), 600, null)
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
                            if (suppressCameraIdle[0]) {
                                suppressCameraIdle[0] = false
                                return@OnCameraIdleListener
                            }
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

        // Filter POIs by active vibe; null = show all
        val filteredPois = if (activeVibe != null) {
            pois.filter { it.vibe.contains(activeVibe.name, ignoreCase = true) }
        } else {
            pois
        }.filter { it.latitude != null && it.longitude != null }

        // Ensure icon bitmap exists for a given vibe + POI type combo
        fun ensureIcon(vibe: Vibe, poiType: String): String {
            val typeKey = poiType.lowercase().trim()
            val iconKey = "poi_${vibe.name}_$typeKey"
            if (style.getImage(iconKey) == null) {
                val vibeColorArgb = vibe.toColor().toArgb()
                val emoji = poiTypeEmoji(typeKey)
                val size = 64
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                // Vibe-colored circle background
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = vibeColorArgb
                    this.style = Paint.Style.FILL
                }
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
                // Emoji icon centered
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size * 0.5f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT
                }
                val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(emoji, size / 2f, textY, textPaint)
                style.addImage(iconKey, bitmap)
            }
            return iconKey
        }

        // Add symbols with stagger (runs inside LaunchedEffect — auto-cancelled on restart)
        for ((i, poi) in filteredPois.withIndex()) {
            if (isDestroyed[0]) return@LaunchedEffect
            delay(50L * i)

            val poiVibe = Vibe.entries.firstOrNull { poi.vibe.contains(it.name, ignoreCase = true) }
                ?: Vibe.DEFAULT
            val vibe = activeVibe ?: poiVibe
            val iconKey = ensureIcon(vibe, poi.type)

            val symbol = sm.create(
                SymbolOptions()
                    .withLatLng(LatLng(poi.latitude!!, poi.longitude!!))
                    .withIconImage(iconKey)
                    .withIconSize(0.1f)
                    .withTextField(poi.name)
                    .withTextSize(10f)
                    .withTextColor("#FAFAFA")
                    .withTextOffset(arrayOf(0f, 1.8f))
                    .withTextHaloColor("rgba(0,0,0,0.7)")
                    .withTextHaloWidth(1.5f)
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
            pinAnimatorsRef.add(animator)
        }

        // Fit camera to show all pins — only when pois list changed (not on vibe switch)
        if (pois !== lastFittedPois.value && filteredPois.isNotEmpty()) {
            lastFittedPois.value = pois
            suppressCameraIdle[0] = true
            if (filteredPois.size >= 2) {
                try {
                    val boundsBuilder = LatLngBounds.Builder()
                    for (poi in filteredPois) {
                        boundsBuilder.include(LatLng(poi.latitude!!, poi.longitude!!))
                    }
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100),
                        600,
                    )
                } catch (_: Exception) {
                    // Identical coordinates — fall back to centering on first POI
                    val poi = filteredPois[0]
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(poi.latitude!!, poi.longitude!!), 15.0),
                        600,
                    )
                }
            } else {
                val poi = filteredPois[0]
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(poi.latitude!!, poi.longitude!!), 15.0),
                    600,
                )
            }
        }

        // Add glow zones (only when a specific vibe is selected)
        if (activeVibe != null && filteredPois.size >= 2) {
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

/** Maps POI type strings from Gemini to emoji for map pin icons.
 *  Uses single-codepoint emoji only (no U+FE0F variation selectors)
 *  for reliable canvas.drawText() rendering on API 26-28. */
private fun poiTypeEmoji(type: String): String = when {
    type.contains("food") || type.contains("restaurant") || type.contains("cafe") || type.contains("bakery") -> "\uD83C\uDF5C" // 🍜
    type.contains("bar") || type.contains("pub") || type.contains("nightlife") || type.contains("entertainment") -> "\uD83C\uDFAD" // 🎭
    type.contains("park") || type.contains("garden") || type.contains("nature") -> "\uD83C\uDF33" // 🌳
    type.contains("historic") || type.contains("heritage") || type.contains("monument") || type.contains("memorial") -> "\uD83C\uDFDB" // 🏛
    type.contains("shop") || type.contains("market") || type.contains("mall") || type.contains("store") -> "\uD83D\uDED2" // 🛒
    type.contains("art") || type.contains("gallery") || type.contains("museum") -> "\uD83C\uDFA8" // 🎨
    type.contains("transit") || type.contains("station") || type.contains("transport") -> "\uD83D\uDE87" // 🚇
    type.contains("beach") || type.contains("coast") || type.contains("waterfront") -> "\uD83C\uDF0A" // 🌊
    type.contains("temple") || type.contains("church") || type.contains("mosque") || type.contains("religious") -> "\uD83D\uDD4C" // 🕌
    type.contains("hotel") || type.contains("hostel") || type.contains("accommodation") -> "\uD83C\uDFE8" // 🏨
    type.contains("safety") || type.contains("police") || type.contains("security") -> "\uD83D\uDEE1" // 🛡
    type.contains("landmark") || type.contains("attraction") || type.contains("viewpoint") -> "\uD83D\uDDFC" // 🗼
    type.contains("district") || type.contains("neighborhood") || type.contains("area") -> "\uD83C\uDFD8" // 🏘
    type.contains("sport") || type.contains("stadium") || type.contains("gym") -> "\u26BD" // ⚽
    type.contains("library") || type.contains("education") || type.contains("university") -> "\uD83D\uDCDA" // 📚
    type.contains("hospital") || type.contains("clinic") || type.contains("health") -> "\uD83C\uDFE5" // 🏥
    else -> "\uD83D\uDCCD" // 📍
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
