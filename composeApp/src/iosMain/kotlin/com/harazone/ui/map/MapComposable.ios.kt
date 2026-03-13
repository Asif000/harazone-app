package com.harazone.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cocoapods.MapLibre.MLNAnnotationProtocol
import cocoapods.MapLibre.MLNCircleStyleLayer
import cocoapods.MapLibre.MLNMapView
import cocoapods.MapLibre.MLNMapViewDelegateProtocol
import cocoapods.MapLibre.MLNPointAnnotation
import cocoapods.MapLibre.MLNPointFeature
import cocoapods.MapLibre.MLNShapeSource
import cocoapods.MapLibre.MLNStyle
import cocoapods.MapLibre.MLNSymbolStyleLayer
import com.harazone.BuildKonfig
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.Vibe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSError
import platform.Foundation.NSExpression
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.darwin.NSObject

private val MAP_STYLE_URL get() =
    "https://api.maptiler.com/maps/streets-v2-dark/style.json?key=${BuildKonfig.MAPTILER_API_KEY}"

private const val POI_TEXT_SOURCE_ID = "poi_text_source"
private const val POI_TEXT_LAYER_ID = "poi_text_layer"

// TODO(BACKLOG-LOW): iOS gold saved pins — implement MLNAnnotationView subclass with gold border when MLNAnnotationView ObjC bridging is resolved
@OptIn(ExperimentalForeignApi::class)
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
    savedPoiIds: Set<String>,
    savedPois: List<SavedPoi>,
    savedVibeFilter: Boolean,
) {
    // TODO(BACKLOG-HIGH): iOS dual-pin layer deferred — see tech-spec-save-as-snapshot-poi-architecture.md
    // Required: load savedPois from DB, render as gold pins, apply 50m suppression for Gemini results
    val currentOnPoiSelected = rememberUpdatedState(onPoiSelected)
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
    val currentOnMapRenderFailed = rememberUpdatedState(onMapRenderFailed)

    val suppressCameraIdle = remember { booleanArrayOf(false) }
    val annotationPoiMap = remember { mutableMapOf<MLNPointAnnotation, POI>() }
    val currentAnnotations = remember { mutableListOf<MLNPointAnnotation>() }
    val styleLoaded = remember { mutableStateOf(false) }
    val lastFittedPois = remember { mutableStateOf<List<POI>>(emptyList()) }

    val delegate = remember {
        MapDelegate(
            annotationPoiMap = annotationPoiMap,
            suppressCameraIdle = suppressCameraIdle,
            onPoiSelected = { currentOnPoiSelected.value(it) },
            onCameraIdle = { lat, lng -> currentOnCameraIdle.value(lat, lng) },
            onStyleLoaded = { styleLoaded.value = true },
            onRenderFailed = { currentOnMapRenderFailed.value() },
        )
    }

    val mapView = remember {
        MLNMapView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)).apply {
            styleURL = NSURL(string = MAP_STYLE_URL)
            showsUserLocation = true
            setCenterCoordinate(
                centerCoordinate = CLLocationCoordinate2DMake(latitude, longitude),
                zoomLevel = zoomLevel,
                animated = false,
            )
            this.delegate = delegate
            // TODO(BACKLOG-LOW): Tap empty map space to deselect POI card on iOS.
            //   UITapGestureRecognizer target-action requires @ObjCAction for Kotlin/Native
            //   to expose the method to the ObjC runtime — without it crashes with
            //   doesNotRecognizeSelector. Use MLNAnnotationView subclass or a bridging
            //   NSObject helper class with @ObjCAction once confirmed.
        }
    }

    // Camera fly-to when caller changes latitude/longitude/cameraMoveId
    LaunchedEffect(latitude, longitude, cameraMoveId) {
        if (latitude == 0.0 && longitude == 0.0) return@LaunchedEffect
        suppressCameraIdle[0] = true
        mapView.setCenterCoordinate(
            centerCoordinate = CLLocationCoordinate2DMake(latitude, longitude),
            zoomLevel = zoomLevel,
            animated = true,
        )
    }

    // POI markers + glow zones — react to pois / activeVibe / style ready
    LaunchedEffect(pois, activeVibe, styleLoaded.value) {
        if (!styleLoaded.value) return@LaunchedEffect
        val style = mapView.style ?: return@LaunchedEffect

        // Remove old annotations
        if (currentAnnotations.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            mapView.removeAnnotations(currentAnnotations as List<*>)
        }
        currentAnnotations.clear()
        annotationPoiMap.clear()

        // Remove previous text label layer + source and glow layers
        removePoiTextLayer(style)
        removeGlowLayers(style)

        val filteredPois = if (activeVibe != null) {
            pois.filter { it.vibe.contains(activeVibe.name, ignoreCase = true) }
        } else {
            pois
        }.filter { it.latitude != null && it.longitude != null }

        delegate.activeVibe = activeVibe

        // Build annotations (pins + tap handling)
        val annotations = filteredPois.map { poi ->
            val annotation = MLNPointAnnotation()
            annotation.setCoordinate(CLLocationCoordinate2DMake(poi.latitude!!, poi.longitude!!))
            annotation.setTitle(poi.name)
            annotationPoiMap[annotation] = poi
            currentAnnotations.add(annotation)
            annotation
        }

        if (annotations.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            mapView.addAnnotations(annotations as List<*>)
        }

        // Text labels via MLNSymbolStyleLayer — renders POI name below each pin
        if (filteredPois.isNotEmpty()) {
            val features = filteredPois.map { poi ->
                MLNPointFeature().apply {
                    setCoordinate(CLLocationCoordinate2DMake(poi.latitude!!, poi.longitude!!))
                    @Suppress("UNCHECKED_CAST")
                    setAttributes(mapOf("name" to poi.name) as Map<Any?, *>)
                }
            }

            @Suppress("UNCHECKED_CAST")
            val source = MLNShapeSource(
                identifier = POI_TEXT_SOURCE_ID,
                features = features as List<*>,
                options = null,
            )
            style.addSource(source)

            val textLayer = MLNSymbolStyleLayer(identifier = POI_TEXT_LAYER_ID, source = source)
            textLayer.text = NSExpression.expressionForKeyPath("name")
            textLayer.textFontSize = NSExpression.expressionForConstantValue(NSNumber(double = 10.0))
            textLayer.textColor = NSExpression.expressionForConstantValue(
                UIColor(red = 0.98, green = 0.98, blue = 0.98, alpha = 1.0), // #FAFAFA
            )
            textLayer.textHaloColor = NSExpression.expressionForConstantValue(
                UIColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 0.7),
            )
            textLayer.textHaloWidth = NSExpression.expressionForConstantValue(NSNumber(double = 1.5))
            textLayer.textOffset = NSExpression.expressionForConstantValue(
                listOf(NSNumber(double = 0.0), NSNumber(double = 1.8)),
            )
            textLayer.textAllowsOverlap = NSExpression.expressionForConstantValue(true)
            style.addLayer(textLayer)
        }

        // Fit camera to show all pins (only when the POI list itself changes)
        if (pois !== lastFittedPois.value && annotations.isNotEmpty()) {
            lastFittedPois.value = pois
            suppressCameraIdle[0] = true
            @Suppress("UNCHECKED_CAST")
            mapView.showAnnotations(annotations as List<*>, animated = true)
        }

        // Glow zones — only when a vibe is active and there are clusters
        if (activeVibe != null && filteredPois.size >= 2) {
            val clusters = clusterPois(filteredPois)
            val vibeColor = hexToUIColor(activeVibe.accentColorHex)
            for ((idx, cluster) in clusters.withIndex()) {
                val centroid = cluster.first
                val sourceId = "glow_source_$idx"
                val layerId = "glow_layer_$idx"

                val point = MLNPointFeature()
                point.setCoordinate(CLLocationCoordinate2DMake(centroid.first, centroid.second))

                val source = MLNShapeSource(identifier = sourceId, shape = point, options = null)
                style.addSource(source)

                val layer = MLNCircleStyleLayer(identifier = layerId, source = source)
                layer.circleRadius = NSExpression.expressionForConstantValue(NSNumber(double = 80.0))
                layer.circleColor = NSExpression.expressionForConstantValue(vibeColor)
                layer.circleOpacity = NSExpression.expressionForConstantValue(NSNumber(double = 0.25))
                layer.circleBlur = NSExpression.expressionForConstantValue(NSNumber(double = 1.2))
                // Insert below all other layers so markers appear on top
                style.insertLayer(layer, atIndex = 0u)
            }
        }
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Delegate
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private class MapDelegate(
    val annotationPoiMap: MutableMap<MLNPointAnnotation, POI>,
    private val suppressCameraIdle: BooleanArray,
    private val onPoiSelected: (POI?) -> Unit,
    private val onCameraIdle: (Double, Double) -> Unit,
    private val onStyleLoaded: () -> Unit,
    private val onRenderFailed: () -> Unit,
) : NSObject(), MLNMapViewDelegateProtocol {

    var activeVibe: Vibe? = null

    /** Called when the map finishes loading a style. */
    override fun mapView(mapView: MLNMapView, didFinishLoadingStyle: MLNStyle) {
        onStyleLoaded()
    }

    /** Called after camera animation completes or user stops panning/zooming. */
    override fun mapView(mapView: MLNMapView, regionDidChangeAnimated: Boolean) {
        if (suppressCameraIdle[0]) {
            suppressCameraIdle[0] = false
            return
        }
        val (lat, lng) = mapView.centerCoordinate.useContents { latitude to longitude }
        onCameraIdle(lat, lng)
    }

    /** POI pin tapped → select. */
    override fun mapView(mapView: MLNMapView, didSelectAnnotation: MLNAnnotationProtocol) {
        val annotation = didSelectAnnotation as? MLNPointAnnotation ?: return
        annotationPoiMap[annotation]?.let { onPoiSelected(it) }
    }

    // TODO(BACKLOG-LOW): Add vibe-coloured circle marker images via mapView:imageForAnnotation:
    //   once the Kotlin/Native conflicting-overloads issue with didSelectAnnotation is resolved.
    //   Both delegate methods have (MLNMapView, MLNAnnotationProtocol) params so Kotlin sees them
    //   as the same signature. For now, MLNMapView uses default red pins.

    override fun mapViewDidFailLoadingMap(mapView: MLNMapView, withError: NSError) {
        onRenderFailed()
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun hexToUIColor(hex: String): UIColor {
    val h = hex.trimStart('#')
    val r = h.substring(0, 2).toInt(16) / 255.0
    val g = h.substring(2, 4).toInt(16) / 255.0
    val b = h.substring(4, 6).toInt(16) / 255.0
    return UIColor(red = r, green = g, blue = b, alpha = 1.0)
}

/** Remove POI text label symbol layer + source from a previous render. */
@OptIn(ExperimentalForeignApi::class)
private fun removePoiTextLayer(style: MLNStyle) {
    style.layerWithIdentifier(POI_TEXT_LAYER_ID)?.let { style.removeLayer(it) }
    style.sourceWithIdentifier(POI_TEXT_SOURCE_ID)?.let { style.removeSource(it) }
}

/** Remove any glow circle layers/sources added by a previous POI render. */
@OptIn(ExperimentalForeignApi::class)
private fun removeGlowLayers(style: MLNStyle) {
    @Suppress("UNCHECKED_CAST")
    val layers = (style.layers as? List<*>) ?: return
    layers.filterIsInstance<MLNCircleStyleLayer>()
        .filter { it.identifier.startsWith("glow_layer_") }
        .forEach { style.removeLayer(it) }

    @Suppress("UNCHECKED_CAST")
    val sources = style.sources as? Set<*> ?: return
    sources.filterIsInstance<MLNShapeSource>()
        .filter { it.identifier.startsWith("glow_source_") }
        .forEach { style.removeSource(it) }
}

// TODO(BACKLOG-MEDIUM): clusterPois uses Manhattan distance in degrees — same limitation as Android. Replace with Haversine.
private fun clusterPois(pois: List<POI>): List<Pair<Pair<Double, Double>, List<POI>>> {
    val used = BooleanArray(pois.size)
    val clusters = mutableListOf<Pair<Pair<Double, Double>, List<POI>>>()
    for (i in pois.indices) {
        if (used[i]) continue
        val cluster = mutableListOf(pois[i])
        used[i] = true
        for (j in i + 1 until pois.size) {
            if (used[j]) continue
            val dist = kotlin.math.abs(pois[i].latitude!! - pois[j].latitude!!) +
                kotlin.math.abs(pois[i].longitude!! - pois[j].longitude!!)
            if (dist < 0.005) {
                cluster.add(pois[j])
                used[j] = true
            }
        }
        if (cluster.size >= 2) {
            val lat = cluster.sumOf { it.latitude!! } / cluster.size
            val lng = cluster.sumOf { it.longitude!! } / cluster.size
            clusters.add((lat to lng) to cluster)
        }
    }
    return clusters
}
