package com.harazone.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cocoapods.MapLibre.MLNAnnotationImage
import cocoapods.MapLibre.MLNAnnotationProtocol
import kotlinx.cinterop.ObjCAction
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
import com.harazone.ui.theme.toColor
import com.harazone.util.poiTypeEmoji
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSError
import platform.Foundation.NSExpression
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UITextAlignmentCenter
import platform.darwin.NSObject

private val MAP_STYLE_URL get() =
    "https://api.maptiler.com/maps/streets-v2-dark/style.json?key=${BuildKonfig.MAPTILER_API_KEY}"

private const val POI_TEXT_SOURCE_ID = "poi_text_source"
private const val POI_TEXT_LAYER_ID = "poi_text_layer"

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
    visitedPoiIds: Set<String>,
    visitedPois: List<SavedPoi>,
    visitedFilter: Boolean,
    onPinTapped: (Int) -> Unit,
    selectedPinIndex: Int?,
) {
    val currentOnPoiSelected = rememberUpdatedState(onPoiSelected)
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
    val currentOnMapRenderFailed = rememberUpdatedState(onMapRenderFailed)
    val currentOnPinTapped = rememberUpdatedState(onPinTapped)
    val currentPois = rememberUpdatedState(pois)

    val suppressCameraIdle = remember { booleanArrayOf(false) }
    val annotationPoiMap = remember { mutableMapOf<MLNPointAnnotation, POI>() }
    val currentAnnotations = remember { mutableListOf<MLNPointAnnotation>() }
    val styleLoaded = remember { mutableStateOf(false) }
    val lastFittedPois = remember { mutableStateOf<List<POI>>(emptyList()) }
    val savedFilterFitted = remember { booleanArrayOf(false) }
    val wasSavedVibeFilter = remember { booleanArrayOf(false) }

    val delegate = remember {
        MapDelegate(
            annotationPoiMap = annotationPoiMap,
            suppressCameraIdle = suppressCameraIdle,
            onCameraIdle = { lat, lng -> currentOnCameraIdle.value(lat, lng) },
            onStyleLoaded = { styleLoaded.value = true },
            onRenderFailed = { currentOnMapRenderFailed.value() },
        )
    }

    // Tap handler for annotation selection (since imageForAnnotation conflicts with didSelectAnnotation)
    val tapHandler = remember {
        AnnotationTapHandler(
            annotationPoiMap = annotationPoiMap,
            onPoiSelected = { poi ->
                val index = currentPois.value.indexOfFirst { it.savedId == poi.savedId }
                if (index >= 0) currentOnPinTapped.value(index)
                else currentOnPoiSelected.value(poi)
            },
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
            // Add tap gesture for annotation selection
            val tapGesture = UITapGestureRecognizer(
                target = tapHandler,
                action = platform.objc.sel_registerName("handleTap:"),
            )
            addGestureRecognizer(tapGesture)
            tapHandler.mapView = this
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

    // Selection ring update — remove+re-add all annotations to trigger imageForAnnotation.
    // With MLNPointAnnotation there is no way to update a single pin's image without re-adding
    // (unlike MLNSymbolStyleLayer where style.addImage() suffices). With 3 pins this is negligible.
    // Targeted 2-pin update would require migrating to MLNShapeSource + MLNSymbolStyleLayer,
    // which is blocked by K/N interop issues (see tech-spec-ios-custom-pins-symbol-layer.md).
    LaunchedEffect(selectedPinIndex) {
        delegate.selectedPinIndex = selectedPinIndex
        delegate.currentPois = currentPois.value
        delegate.clearImageCache()
        if (currentAnnotations.isNotEmpty() && styleLoaded.value) {
            @Suppress("UNCHECKED_CAST")
            mapView.removeAnnotations(currentAnnotations.toList() as List<*>)
            @Suppress("UNCHECKED_CAST")
            mapView.addAnnotations(currentAnnotations.toList() as List<*>)
        }
    }

    // POI markers + text labels + glow zones
    LaunchedEffect(pois, activeVibe, visitedPoiIds, visitedPois, visitedFilter, styleLoaded.value) {
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
        delegate.visitedPoiIds = visitedPoiIds
        delegate.selectedPinIndex = selectedPinIndex
        delegate.currentPois = pois

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

        // Text labels via MLNSymbolStyleLayer
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
                UIColor(red = 0.98, green = 0.98, blue = 0.98, alpha = 1.0),
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

        // Reset saved filter zoom flag when regular POIs are back
        if (pois.isNotEmpty()) savedFilterFitted[0] = false

        // Force camera re-fit when visitedFilter transitions true -> false
        val forceRefit = wasSavedVibeFilter[0] && !visitedFilter && filteredPois.isNotEmpty()
        wasSavedVibeFilter[0] = visitedFilter
        if (forceRefit) lastFittedPois.value = emptyList()

        // Fit camera to show all pins (only when the POI list itself changes)
        if (pois !== lastFittedPois.value && annotations.isNotEmpty()) {
            lastFittedPois.value = pois
            suppressCameraIdle[0] = true
            @Suppress("UNCHECKED_CAST")
            mapView.showAnnotations(annotations as List<*>, animated = true)
        }

        // Fit camera to saved POIs when saved filter is first activated
        if (visitedFilter && pois.isEmpty() && visitedPois.isNotEmpty() && !savedFilterFitted[0]) {
            savedFilterFitted[0] = true
            val validSaved = visitedPois.filter { it.lat != 0.0 && it.lng != 0.0 }
            if (validSaved.isNotEmpty()) {
                suppressCameraIdle[0] = true
                val savedAnnotations = validSaved.map { sp ->
                    val a = MLNPointAnnotation()
                    a.setCoordinate(CLLocationCoordinate2DMake(sp.lat, sp.lng))
                    a
                }
                @Suppress("UNCHECKED_CAST")
                mapView.showAnnotations(savedAnnotations as List<*>, animated = true)
            }
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
                style.insertLayer(layer, atIndex = 0u)
            }
        }
    }

    // Cleanup on disposal
    DisposableEffect(Unit) {
        onDispose {
            mapView.delegate = null
            tapHandler.mapView = null
            mapView.gestureRecognizers?.filterIsInstance<UITapGestureRecognizer>()?.forEach {
                mapView.removeGestureRecognizer(it)
            }
            if (currentAnnotations.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                mapView.removeAnnotations(currentAnnotations as List<*>)
            }
            currentAnnotations.clear()
            annotationPoiMap.clear()
        }
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Pin image drawing — uses legacy UIGraphicsBeginImageContext (reliable in K/N)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private fun drawPinImage(
    emoji: String,
    vibeColor: UIColor,
    statusColor: PinStatusColor,
    isClosed: Boolean,
    isSelected: Boolean,
    isSaved: Boolean,
): UIImage? {
    val size = 44.0
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(size, size), false, 2.0)

    // Background circle
    val bgColor = if (isClosed) UIColor(white = 0.5, alpha = 0.35) else vibeColor
    bgColor.setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(0.0, 0.0, size, size)).fill()

    // Emoji centered — render via UILabel snapshot into current context
    val emojiLabel = UILabel(frame = CGRectMake(0.0, 0.0, size, size))
    emojiLabel.text = emoji
    emojiLabel.font = UIFont.systemFontOfSize(size * 0.45)
    @Suppress("DEPRECATION")
    emojiLabel.textAlignment = UITextAlignmentCenter
    emojiLabel.backgroundColor = UIColor.clearColor
    emojiLabel.layer.renderInContext(UIGraphicsGetCurrentContext()!!)

    // Status dot at bottom-right
    if (statusColor != PinStatusColor.GREY) {
        // Dark outline
        UIColor.blackColor.setFill()
        UIBezierPath.bezierPathWithOvalInRect(CGRectMake(size - 15.0, size - 15.0, 14.0, 14.0)).fill()
        // Colored dot
        statusColor.toUIColor().setFill()
        UIBezierPath.bezierPathWithOvalInRect(CGRectMake(size - 13.0, size - 13.0, 10.0, 10.0)).fill()
    }

    // Saved: gold ring
    if (isSaved) {
        val gold = UIColor(red = 1.0, green = 0.843, blue = 0.0, alpha = 1.0)
        gold.setStroke()
        val ringPath = UIBezierPath.bezierPathWithOvalInRect(CGRectMake(2.0, 2.0, size - 4.0, size - 4.0))
        ringPath.lineWidth = 3.0
        ringPath.stroke()
    }

    // Selected: white ring
    if (isSelected) {
        UIColor.whiteColor.setStroke()
        val ringPath = UIBezierPath.bezierPathWithOvalInRect(CGRectMake(2.0, 2.0, size - 4.0, size - 4.0))
        ringPath.lineWidth = 4.0
        ringPath.stroke()
    }

    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image
}

private fun PinStatusColor.toUIColor(): UIColor = when (this) {
    PinStatusColor.GREEN -> UIColor(red = 0.298, green = 0.686, blue = 0.314, alpha = 1.0)
    PinStatusColor.ORANGE -> UIColor(red = 1.0, green = 0.596, blue = 0.0, alpha = 1.0)
    PinStatusColor.RED -> UIColor(red = 0.957, green = 0.263, blue = 0.212, alpha = 1.0)
    PinStatusColor.GREY -> UIColor(red = 0.620, green = 0.620, blue = 0.620, alpha = 1.0)
}

// ---------------------------------------------------------------------------
// Tap handler — @ObjCAction bridge for annotation selection
// Uses coordinate hit-test (44pt radius) instead of MLNShapeSource's visibleFeatures(at:)
// because we use MLNPointAnnotation, not MLNSymbolStyleLayer. With 3 pins the fixed radius
// is sufficient. visibleFeatures would require the MLNShapeSource migration blocked by K/N
// interop issues (UIGraphicsImageRenderer, NSString.drawAtPoint, MLNShapeSource.setFeatures).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private class AnnotationTapHandler(
    private val annotationPoiMap: MutableMap<MLNPointAnnotation, POI>,
    private val onPoiSelected: (POI) -> Unit,
) : NSObject() {
    var mapView: MLNMapView? = null

    @Suppress("unused")
    @ObjCAction
    fun handleTap(gesture: UITapGestureRecognizer) {
        val mv = mapView ?: return
        val point = gesture.locationInView(mv)
        // Find closest annotation within 44pt hit area
        val px = point.useContents { x }
        val py = point.useContents { y }
        var closest: MLNPointAnnotation? = null
        var closestDist = 44.0 * 44.0 // max tap distance squared
        for (annotation in annotationPoiMap.keys) {
            val screenPt = mv.convertCoordinate(annotation.coordinate, toPointToView = mv)
            val dx = screenPt.useContents { x } - px
            val dy = screenPt.useContents { y } - py
            val dist = dx * dx + dy * dy
            if (dist < closestDist) {
                closestDist = dist
                closest = annotation
            }
        }
        closest?.let { annotationPoiMap[it]?.let(onPoiSelected) }
    }
}

// ---------------------------------------------------------------------------
// Delegate — provides custom pin images via imageForAnnotation
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private class MapDelegate(
    val annotationPoiMap: MutableMap<MLNPointAnnotation, POI>,
    private val suppressCameraIdle: BooleanArray,
    private val onCameraIdle: (Double, Double) -> Unit,
    private val onStyleLoaded: () -> Unit,
    private val onRenderFailed: () -> Unit,
) : NSObject(), MLNMapViewDelegateProtocol {

    var activeVibe: Vibe? = null
    var visitedPoiIds: Set<String> = emptySet()
    var selectedPinIndex: Int? = null
    var currentPois: List<POI> = emptyList()

    // Pin image cache to avoid redrawing identical pins
    private val imageCache = mutableMapOf<String, MLNAnnotationImage>()

    fun clearImageCache() { imageCache.clear() }

    override fun mapView(mapView: MLNMapView, didFinishLoadingStyle: MLNStyle) {
        onStyleLoaded()
    }

    override fun mapView(mapView: MLNMapView, regionDidChangeAnimated: Boolean) {
        if (suppressCameraIdle[0]) {
            suppressCameraIdle[0] = false
            return
        }
        val (lat, lng) = mapView.centerCoordinate.useContents { latitude to longitude }
        onCameraIdle(lat, lng)
    }

    // NOTE: didSelectAnnotation conflicts with imageForAnnotation in Kotlin/Native
    // (same JVM signature). Selection is handled via didSelectAnnotation; custom images
    // are provided via imageForAnnotation. We keep imageForAnnotation and use
    // didSelectAnnotation for tap handling since Kotlin sees them as the same overload.
    // Workaround: use only imageForAnnotation and handle selection via a tap gesture recognizer.

    override fun mapView(mapView: MLNMapView, imageForAnnotation: MLNAnnotationProtocol): MLNAnnotationImage? {
        val annotation = imageForAnnotation as? MLNPointAnnotation ?: return null
        val poi = annotationPoiMap[annotation] ?: return null

        val poiVibe = Vibe.entries.firstOrNull { poi.vibe.contains(it.name, ignoreCase = true) } ?: Vibe.DEFAULT
        val vibe = activeVibe ?: poiVibe
        val resolved = resolveStatus(poi.liveStatus, poi.hours)
        val status = liveStatusToColor(resolved)
        val closed = isClosed(resolved)
        val isSaved = poi.savedId in visitedPoiIds
        val poiIndex = currentPois.indexOfFirst { it.savedId == poi.savedId }
        val isSelected = poiIndex >= 0 && poiIndex == selectedPinIndex

        val cacheKey = "${vibe.name}_${poi.type}_${status.name}_${closed}_${isSaved}_$isSelected"

        imageCache[cacheKey]?.let { return it }

        val vibeComposeColor = vibe.toColor()
        val vibeUiColor = UIColor(
            red = vibeComposeColor.red.toDouble(),
            green = vibeComposeColor.green.toDouble(),
            blue = vibeComposeColor.blue.toDouble(),
            alpha = 1.0,
        )

        val image = drawPinImage(
            emoji = poiTypeEmoji(poi.type),
            vibeColor = vibeUiColor,
            statusColor = status,
            isClosed = closed,
            isSelected = isSelected,
            isSaved = isSaved,
        ) ?: return null

        val annotationImage = MLNAnnotationImage.annotationImageWithImage(image, reuseIdentifier = cacheKey)
        imageCache[cacheKey] = annotationImage
        return annotationImage
    }

    override fun mapViewDidFailLoadingMap(mapView: MLNMapView, withError: NSError) {
        onRenderFailed()
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun hexToUIColor(hex: String): UIColor {
    val h = hex.trimStart('#')
    if (h.length < 6) return UIColor.grayColor
    val r = h.substring(0, 2).toInt(16) / 255.0
    val g = h.substring(2, 4).toInt(16) / 255.0
    val b = h.substring(4, 6).toInt(16) / 255.0
    return UIColor(red = r, green = g, blue = b, alpha = 1.0)
}

@OptIn(ExperimentalForeignApi::class)
private fun removePoiTextLayer(style: MLNStyle) {
    style.layerWithIdentifier(POI_TEXT_LAYER_ID)?.let { style.removeLayer(it) }
    style.sourceWithIdentifier(POI_TEXT_SOURCE_ID)?.let { style.removeSource(it) }
}

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
