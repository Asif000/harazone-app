# Tech Spec: iOS Custom POI Pins via Symbol Style Layer

**Status**: SUPERSEDED — implemented via `MLNAnnotationImage` + `UIGraphicsBeginImageContextWithOptions` + `AnnotationTapHandler` in ambient-layer-bottom-carousel spec (2026-03-15). Symbol layer approach abandoned due to K/N interop issues with `UIGraphicsImageRenderer`, `NSString.drawAtPoint`, and `MLNShapeSource.setFeatures`.
**Platform**: iOS only (`iosMain`)
**File**: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt`
**Effort**: ~half day

---

## Background

The iOS map currently renders POI pins using `MLNPointAnnotation` with MapLibre's default red pin image. Custom vibe-coloured pin images cannot be set via the `imageForAnnotation:` delegate method because it shares the same Kotlin/Native signature as `didSelectAnnotation:` — both resolve to `(MLNMapView, MLNAnnotationProtocol)`, causing a conflicting overloads compile error.

A second issue also exists: tapping empty map space does not deselect the POI card (backlog item). This was caused by `UITapGestureRecognizer` crashing with `doesNotRecognizeSelector` because the Kotlin/Native `private class MapDelegate` had its `mapTapped:` method removed by dead code elimination before it could be registered in the ObjC method table.

Both problems are solved together in this spec.

---

## Solution

### Part 1 — Custom pins via Symbol Style Layer

Replace `MLNPointAnnotation` markers with a **MapLibre Symbol Style Layer** pipeline:

1. **Register pin images into the map style** — one `UIImage` per vibe type (Character, History, What's On, Safety, Nearby, Cost) plus a default. Draw each image programmatically as a filled circle with an icon using `UIGraphicsImageRenderer`. Register with `style.setImage(_:forName:)` using a stable key like `"pin_character"`, `"pin_history"`, etc.

2. **Replace `MLNPointAnnotation` with `MLNPointFeature`** — each POI becomes an `MLNPointFeature` with a `"poiId"` string property (use `poi.name` as a stable key since POI has no UUID). Keep the existing `annotationPoiMap` but key it by `poiId` string instead of annotation object.

3. **Wrap features in `MLNShapeCollectionFeature`** and add to a single `MLNShapeSource` with identifier `"poi_source"`. One source, rebuilt on every POI/vibe update.

4. **Add `MLNSymbolStyleLayer`** on top:
   - `iconImageName`: `NSExpression(format: "pin_%K", "vibe")` — maps the feature's `"vibe"` property to the registered image name
   - `iconAllowsOverlap`: `NSExpression.expressionForConstantValue(true)`
   - `iconScale`: `NSExpression.expressionForConstantValue(NSNumber(double = 1.2))`

5. **Cleanup** — on style reload or POI update, remove the old source and layer before re-adding (use `style.removeLayer` / `style.removeSource` by identifier, wrapped in try-catch since removal throws if not present).

### Part 2 — Fix deselect-on-empty-tap + tap detection via `MapTapHandler`

The root cause of the `doesNotRecognizeSelector` crash: `private class MapDelegate` had its tap method DCE'd. Fix: create a **separate `internal` class** for the gesture recognizer target. `internal` visibility prevents DCE and ensures the method is registered in the ObjC runtime.

```kotlin
internal class MapTapHandler(
    private val mapView: MLNMapView,
    private val onPoiTapped: (POI?) -> Unit,
    private val poiIndex: () -> Map<String, POI>,
) : NSObject() {
    fun handleTap(recognizer: UITapGestureRecognizer) {
        val point = recognizer.locationInView(mapView)
        val features = mapView.visibleFeatures(
            at = point,
            inStyleLayersWithIdentifiers = setOf("poi_symbol_layer"),
        )
        val poiId = features.firstOrNull()
            ?.let { (it as? MLNFeature)?.attribute(forKey = "poiId") as? String }
        onPoiTapped(poiId?.let { poiIndex()[it] })
    }
}
```

- Tap on a symbol feature → extracts `poiId` from feature attributes → looks up `POI` → calls `onPoiTapped(poi)`
- Tap on empty space → no features found → calls `onPoiTapped(null)` → deselects card

Wire the gesture recognizer in `MapComposable`:
```kotlin
val tapHandler = remember { MapTapHandler(mapView, ...) }
val tapGesture = remember {
    UITapGestureRecognizer(target = tapHandler, action = NSSelectorFromString("handleTap:"))
}
// add to mapView once
```

Remove `didSelectAnnotation` delegate method entirely — tap detection is now fully handled by `MapTapHandler`.

---

## Key Implementation Notes

- **No `MLNPointAnnotation` at all after this change** — remove `currentAnnotations`, `annotationPoiMap` (replace with a `poiById: Map<String, POI>`), all `removeAnnotations`/`addAnnotations` calls, and the `didSelectAnnotation` override in `MapDelegate`.
- **`@OptIn(ExperimentalForeignApi::class)`** required on `MapTapHandler.handleTap` and anywhere using `CValue<CGPoint>`.
- **`MLNFeature` cast** — `visibleFeatures(at:inStyleLayersWithIdentifiers:)` returns `[MLNFeature]`. Cast each to `MLNFeature` protocol to call `attribute(forKey:)`.
- **`requireGestureRecognizerToFail`** — optionally call `tapGesture.require(toFail: existingRecognizer)` for each of MapLibre's built-in recognizers to prevent double-fire. Not strictly required since `handleTap` checks for feature presence.
- **`suppressCameraIdle` guard** — keep as-is; no change needed.
- **Glow zones** — keep the existing `MLNCircleStyleLayer` glow zone logic unchanged. Just add the symbol layer on top of the glow layers (insert at the end, not `atIndex = 0`).
- **Android** — no changes needed. Android uses `MapLibre Android SDK` annotation plugin, not affected.

---

## Files to Change

| File | Change |
|------|--------|
| `iosMain/.../MapComposable.ios.kt` | Full rewrite of annotation section; add `MapTapHandler` class; add symbol layer pipeline |

No other files change.

---

## Acceptance Criteria

- [ ] POI pins render with a filled-circle image in the vibe's accent colour (or default colour when no vibe active)
- [ ] Tapping a pin selects the POI and shows the detail card — no crash
- [ ] Tapping empty map space dismisses the POI detail card
- [ ] Multiple POI updates (vibe switch, new area) correctly remove old pins and add new ones — no duplicate layers/sources
- [ ] `BACKLOG-LOW: custom pin images` TODO comment removed from source
- [ ] `BACKLOG-LOW: deselect on empty tap` TODO comment removed from source
