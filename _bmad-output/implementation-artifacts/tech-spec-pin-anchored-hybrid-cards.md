---
title: 'Pin-Anchored Hybrid Cards + Measured Onboarding Anchoring'
slug: 'pin-anchored-hybrid-cards'
created: '2026-03-14'
status: 'review'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'MapLibre Android', 'MLN iOS']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapComposable.kt'
  - 'composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt'  # projection + gesture via MLNMapViewDelegate
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/OnboardingBubble.kt'
code_patterns:
  - 'MapUiState.Ready is mutated via _uiState.value = state.copy(...); always cast to Ready before mutating'
  - 'MapComposable is expect/actual: commonMain declares expect fun, androidMain and iosMain provide actual'
  - 'suppressCameraIdle[0] = true before programmatic camera moves; cleared in onCameraIdle guard'
  - 'onGloballyPositioned { coords -> coords.boundsInRoot() } returns Rect in root coordinate space'
  - 'Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) } places composable at pixel offset within parent'
  - 'Canvas Bezier: drawPath with quadraticBezierTo, apply PathEffect.dashPathEffect for dashed line'
  - 'POI.savedId is the canonical ID: "$name|$latitude|$longitude"'
  - 'POI.liveStatus: String? contains free-text like "Open until 10pm" or "Closed" — parse with contains(ignoreCase=true)'
  - 'POI.confidence: Confidence enum — used for AI data quality, not for status display'
test_patterns:
  - 'MapViewModel tests use fake state; set _uiState via viewModel internal or test the public API then assert uiState.value'
  - 'Composable tests use ComposeTestRule; set state then assert node properties'
---

# Tech-Spec: Pin-Anchored Hybrid Cards + Measured Onboarding Anchoring

**Created:** 2026-03-14

---

## Overview

### Problem Statement

Two independent spatial anchoring failures:

**(1) Floating POI cards are detached from their pins.** The current `FloatingPoiCard` strip (`MapScreen.kt:204–219`) is a bottom `Row` that shows up to 3 cards with `Modifier.weight(1f)` — no relationship to the map pins they represent. Users cannot tell which card belongs to which pin. This is a known deferred backlog item (#98, #99, #103).

**(2) OnboardingBubble callout dots use hardcoded pixel offsets.** Three `CalloutDot` composables in `OnboardingBubble.kt:106–110` are positioned with `Modifier.align(...).padding(...)` using fixed dp values (`end = 48.dp, bottom = 80.dp`, etc.). These misalign on different screen sizes and aspect ratios. Marked `TODO(BACKLOG-MEDIUM)` at line 104.

Both are the same class of problem: a Compose overlay element needs to be positioned relative to another UI element, and currently uses hardcoded geometry instead of measured positions.

### Solution

**Sub-feature A — Pin-Anchored Hybrid Cards (Android):**
Replace the detached bottom `Row` with a full-screen `PinCardLayer` overlay that renders: (a) mini chips positioned above each pin, (b) a hero card with a curved dashed leader line on the selected or nearest pin. Cards hide during map gestures and project fresh positions on `onCameraIdle` (settle-and-show pattern). Tap a chip to promote it to hero. Tap the hero card to open the existing `ExpandablePoiCard`.

**Sub-feature B — Measured Onboarding Anchoring (both platforms):**
Add `onGloballyPositioned` on the VibeRail, FabMenu, and AISearchBar in `MapScreen.kt`. Pass the measured `Offset` positions as parameters to `OnboardingBubble`. Replace the three hardcoded `CalloutDot` placements with `Modifier.offset { IntOffset(x, y) }` using measured positions. Dots skip rendering until positions are measured (guard on `!= Offset.Zero`).

**Unified pattern:** Convert a position in screen/layout coordinates to a Compose pixel offset → use `Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }` to place an overlay element at that position.

### Scope

**In Scope:**
- `ScreenOffset` data class (new, commonMain)
- `MapComposable` expect + both actuals: add `onPinsProjected` and `onMapGestureStart` callbacks
- `MapUiState.Ready`: new fields `pinScreenPositions`, `cardsVisible`, `selectedPinId`
- `MapViewModel`: new functions `onPinsProjected()`, `onMapGestureStart()`, `onPinChipTapped()`
- New composables: `PinMiniChip.kt`, `PinHeroCard.kt`, `PinCardLayer.kt`
- `MapScreen.kt`: replace FloatingPoiCard Row with PinCardLayer; wire new callbacks; measure 3 target elements for onboarding
- `OnboardingBubble.kt`: add 3 measured offset parameters; replace hardcoded CalloutDot positions with measured offset-based placement
- Android pin projection via `mapLibreMap.projection.toScreenLocation(LatLng)` on `onCameraIdle`
- Android gesture detection via `MapLibreMap.OnCameraMoveStartedListener` (REASON_API_GESTURE only)
- iOS pin projection via `MLNMapView.convertCoordinate(_:toPointTo:)` in `MapDelegate.regionDidChangeAnimated`
- iOS gesture detection via `MapDelegate.mapView(_:regionWillChangeAnimated:)` when `animated == false`
- Status confidence stripe color derived from `poi.liveStatus`
- Dual-ring dot on mini chips (vibe color outer ring + status color inner fill)
- Closed-place dimming: `alpha = 0.5f` + name strikethrough if liveStatus contains "closed"

**Out of Scope:**
- Live open/closed API — `liveStatus` uses only the existing `poi.liveStatus: String?` field, no new API calls
- Zoom-adaptive card sizing (v1: fixed sizes regardless of zoom)
- Push-apart collision resolution for overlapping chips (brainstorm idea #4 — deferred v1.1)
- Cluster resolution (chips may overlap in tight areas — acceptable for v1)
- "Show on Map" chip on hero card (separate feature)

---

## Context for Development

### Codebase Patterns

- **`MapUiState.Ready`** (`MapUiState.kt:12`) — data class with 30+ fields; all state changes via `_uiState.value = state.copy(...)`. Always cast: `val state = _uiState.value as? MapUiState.Ready ?: return` before mutating.
- **`MapComposable`** (`MapComposable.kt`) is an `expect fun` in commonMain. Both `MapComposable.android.kt` and `MapComposable.ios.kt` provide `actual fun` implementations. Adding a parameter to `MapComposable.kt` requires matching changes in BOTH actuals.
- **`suppressCameraIdle[0]`** — `BooleanArray(1)` flag. Set to `true` before any programmatic camera move; the `onCameraIdle` listener reads and clears it. Do NOT call `onPinsProjected` when `suppressCameraIdle` is true (cards should not re-project during programmatic fly-tos).
- **`MapLibreMap.OnCameraMoveStartedListener`** — fires when camera starts moving. The `reason` int is `REASON_API_GESTURE = 0` for user touch, `REASON_API_ANIMATION = 1` for programmatic. Only call `onMapGestureStart` when `reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE`.
- **`mapLibreMap.projection.toScreenLocation(LatLng)`** — returns `android.graphics.PointF` (x=screen px left, y=screen px top). Available in `MapLibre 11.x`. Call only after `styleLoaded.value == true`.
- **`onGloballyPositioned`** — Compose modifier that fires after layout. `coordinates.boundsInRoot()` returns `androidx.compose.ui.geometry.Rect` in root (screen) coordinates. Import: `import androidx.compose.ui.layout.boundsInRoot`.
- **`FloatingPoiCard` strip** (`MapScreen.kt:204–219`) — the entire `Row { state.pois.take(3).forEach { ... } }` block inside the main `Box`. This block will be replaced entirely.
- **`CalloutDot`** (`CalloutDot.kt`) — pulsing teal circle, accepts `modifier: Modifier`. Currently placed 3 times in `OnboardingBubble.kt:106–110` with `Alignment + padding`.
- **Canvas Bezier in Compose** — `Canvas(Modifier.fillMaxSize()) { drawPath(...) }` in `commonMain`. Use `Path().apply { moveTo(...); quadraticBezierTo(...); lineTo(...) }`. Dashed: `Paint().apply { pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) }`. Note: in Compose Canvas DSL use `drawPath(path, color, style = Stroke(width=..., pathEffect=...))`.
- **`POI.savedId`** — `"$name|$latitude|$longitude"` — canonical key for all maps keyed by POI identity.
- **`POI.liveStatus: String?`** — free text e.g. `"Open"`, `"Closed"`, `"Open until 10pm"`. Parse with `.contains("open", ignoreCase = true)` / `.contains("closed", ignoreCase = true)`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapComposable.kt` | Expect signature — add `onPinsProjected` + `onMapGestureStart` here |
| `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt` | Android actual — implement pin projection + gesture listener |
| `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt` | iOS actual — add params only (no-op implementation) |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | Add `pinScreenPositions`, `cardsVisible`, `selectedPinId` to `Ready` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | Add `onPinsProjected`, `onMapGestureStart`, `onPinChipTapped` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Remove FloatingPoiCard strip; add PinCardLayer; add onGloballyPositioned; update OnboardingBubble call |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/OnboardingBubble.kt` | Replace hardcoded CalloutDot lines 106-110 with measured offset params |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/components/CalloutDot.kt` | Reference only — existing composable, no changes needed |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | Reference — fields used: `name`, `type`, `liveStatus`, `rating`, `vibes`, `savedId` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/theme/` | Reference — `MapFloatingUiDark` color used in FloatingPoiCard, reuse in new cards |

### Technical Decisions

**1. Projection timing (settle-and-show):** Cards are hidden (`cardsVisible = false`) the moment a user gesture starts. On `onCameraIdle`, the Android map projects all current POI LatLngs to screen pixels and calls `onPinsProjected(Map<String, ScreenOffset>)`. Only then are cards shown. Single-shot projection — not 60fps tracking.

**2. Hero selection:** If `selectedPinId != null` and it matches a POI in `pois`, that POI is the hero. Otherwise, `pois.firstOrNull()` is the hero (first POI = nearest, since `pois` is ordered by distance from camera center). Tapping a chip sets `selectedPinId` to that POI's `savedId`. Tapping the hero again deselects (clears `selectedPinId`), reverting to nearest-as-hero.

**3. Card placement (auto-above, flip below):** Hero card defaults to above-pin (`cardY = pinY - cardHeight - 16dp-in-px`). If `pinY < screenHeight * 0.25f`, flip below (`cardY = pinY + PIN_HEIGHT_PX + 8dp-in-px`). Mini chips always above pin (`chipY = pinY - chipHeight - 8dp-in-px`). Use a constant `PIN_ICON_HEIGHT_PX = 48` (matches the emoji symbol size in `MapComposable.android.kt:ensureIcon`).

**4. Leader line:** Canvas in `PinCardLayer`, full-screen size. Draw ONE quadratic bezier from `heroCardBottomCenter` to `heroPinTop`. Control point: midpoint horizontally, 40% of the way down vertically. Color: vibe color at 55% alpha. `PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)`. Stroke width: 2dp in pixels. Only drawn when `cardsVisible == true` and hero card has a valid pin position.

**5. Status stripe color:** Private `fun statusColor(liveStatus: String?): Color` in `PinHeroCard.kt`:
- `liveStatus?.contains("open", ignoreCase = true) == true` → `Color(0xFF4CAF50)` (green)
- `liveStatus?.contains("closed", ignoreCase = true) == true` → `Color(0xFFF44336)` (red)
- else → `Color(0xFFFFAB40)` (amber — unknown)

**6. Dual-ring dot:** In `PinMiniChip.kt`, a 12dp `Box` with:
- Outer: `Circle` border 2dp stroke, color = vibe accent color from `vibeAccentColor(poi.vibes.firstOrNull() ?: poi.vibe)` — private helper defined in `PinMiniChip.kt` (see Task 7 code)
- Inner: filled circle 8dp, color = `statusColor(poi.liveStatus)`

**7. Closed dimming:** `PinMiniChip` applies `Modifier.alpha(if (isClosed) 0.5f else 1f)` where `isClosed = poi.liveStatus?.contains("closed", ignoreCase = true) == true`. Also apply `TextDecoration.LineThrough` to the name text when `isClosed`.

**8. Measured onboarding offsets:** `onGloballyPositioned` callbacks are placed on the wrapping Box/modifier of each target element in `MapScreen.kt`'s `ReadyContent`. Use `coordinates.boundsInRoot().center` for FabMenu and AISearchBar (dot appears at center of element). Use `coordinates.boundsInRoot().centerLeft` for VibeRail (dot appears at left edge center, visually pointing into the rail). All three offsets default to `Offset.Zero`; dots are suppressed when offset is `Offset.Zero` (not yet measured).

**9. No changes to ExpandablePoiCard or selectPoi flow:** Tapping the hero card calls the existing `viewModel.selectPoi(poi)`. The `ExpandablePoiCard` opens exactly as it does today (via `state.selectedPoi != null`). `PinCardLayer` is hidden when `state.selectedPoi != null` to avoid visual conflict.

---

## Implementation Plan

### Tasks

Tasks are ordered dependency-first. Complete each task fully before starting the next.

---

**Task 1 — Create `ScreenOffset` data class**

Create new file: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ScreenOffset.kt`

```kotlin
package com.harazone.ui.map

/** Screen-space pixel coordinate for positioning Compose overlays over map pins. */
data class ScreenOffset(val x: Float, val y: Float)
```

---

**Task 2 — Add `onPinsProjected` and `onMapGestureStart` to `MapComposable` expect**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapComposable.kt`

Add two parameters to the `expect fun MapComposable(...)` signature. Place after `onCameraIdle`:

```kotlin
onPinsProjected: (Map<String, ScreenOffset>) -> Unit,
onMapGestureStart: () -> Unit,
```

Add import: `import com.harazone.ui.map.ScreenOffset`

---

**Task 3 — Add new fields to `MapUiState.Ready`**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`

Add to the `Ready` data class (after `showAllMode`):

```kotlin
val pinScreenPositions: Map<String, ScreenOffset> = emptyMap(),
val cardsVisible: Boolean = false,
val selectedPinId: String? = null,
```

Add import at top of file: `import com.harazone.ui.map.ScreenOffset`

---

**Task 4 — Add new functions to `MapViewModel`**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`

Add three public functions (can be placed near `onCameraIdle()`):

```kotlin
fun onPinsProjected(positions: Map<String, ScreenOffset>) {
    val state = _uiState.value as? MapUiState.Ready ?: return
    _uiState.value = state.copy(pinScreenPositions = positions, cardsVisible = true)
}

fun onMapGestureStart() {
    val state = _uiState.value as? MapUiState.Ready ?: return
    _uiState.value = state.copy(cardsVisible = false)
}

fun onPinChipTapped(poiId: String) {
    val state = _uiState.value as? MapUiState.Ready ?: return
    val newSelected = if (state.selectedPinId == poiId) null else poiId
    _uiState.value = state.copy(selectedPinId = newSelected)
}
```

Add import: `import com.harazone.ui.map.ScreenOffset`

No changes needed to the existing `onCameraIdle()` function. Cards are already hidden by `onMapGestureStart()` when the gesture begins, and are shown again by `onPinsProjected()` after the camera settles. Adding a redundant hide at `onCameraIdle` would race against `onPinsProjected` if projection is dispatched synchronously in the same callback chain — potentially blanking cards that were just shown.

---

**Task 5 — Implement pin projection in `MapComposable.android.kt`**

File: `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt`

**5a.** Add the two new parameters to the `actual fun MapComposable(...)` signature after `onCameraIdle`:

```kotlin
onPinsProjected: (Map<String, ScreenOffset>) -> Unit,
onMapGestureStart: () -> Unit,
```

Add at top: `import com.harazone.ui.map.ScreenOffset`

**5b.** Inside the `mapView.getMapAsync { map -> }` block, after the existing `map.addOnCameraIdleListener(cameraIdleListener)` call (line ~192), add:

```kotlin
val gestureMoveListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
        currentOnMapGestureStart.value()
    }
}
map.addOnCameraMoveStartedListener(gestureMoveListener)
```

You also need to declare `currentOnMapGestureStart` alongside the other `rememberUpdatedState` refs (near line 74):
```kotlin
val currentOnPinsProjected = rememberUpdatedState(onPinsProjected)
val currentOnMapGestureStart = rememberUpdatedState(onMapGestureStart)
```

**5c.** Modify the existing `cameraIdleListener` block (lines 185–192). After calling `currentOnCameraIdle.value(target.latitude, target.longitude)`, add the pin projection:

```kotlin
val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
    if (suppressCameraIdle[0]) {
        suppressCameraIdle[0] = false
        return@OnCameraIdleListener
    }
    val target = map.cameraPosition.target ?: return@OnCameraIdleListener
    currentOnCameraIdle.value(target.latitude, target.longitude)

    // Project POI LatLngs → screen pixels and report back to Compose
    if (styleLoaded.value && !isDestroyed[0]) {
        val projected = currentPois.value.mapNotNull { poi ->
            val lat = poi.latitude ?: return@mapNotNull null
            val lng = poi.longitude ?: return@mapNotNull null
            val point = map.projection.toScreenLocation(LatLng(lat, lng))
            poi.savedId to ScreenOffset(point.x, point.y)
        }.toMap()
        currentOnPinsProjected.value(projected)
    }
}
```

Note: `currentPois` must be a `rememberUpdatedState(pois)` ref so the listener always sees the latest POI list. Check if it already exists (look for `val currentPois` or similar near the other `rememberUpdatedState` calls). If not, add:
```kotlin
val currentPois = rememberUpdatedState(pois)
```

---

**Task 6 — Implement pin projection + gesture start in `MapComposable.ios.kt`**

File: `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt`

**6a.** Add the two new parameters to the `actual fun MapComposable(...)` signature after `onCameraIdle`:

```kotlin
onPinsProjected: (Map<String, ScreenOffset>) -> Unit,
onMapGestureStart: () -> Unit,
```

Add import: `import com.harazone.ui.map.ScreenOffset`

**6b.** Add `rememberUpdatedState` refs alongside the existing ones (lines 60–62):

```kotlin
val currentOnPinsProjected = rememberUpdatedState(onPinsProjected)
val currentOnMapGestureStart = rememberUpdatedState(onMapGestureStart)
```

**6c.** Update the `remember { MapDelegate(...) }` call (line 70–78) to pass the two new callbacks:

```kotlin
val delegate = remember {
    MapDelegate(
        annotationPoiMap = annotationPoiMap,
        suppressCameraIdle = suppressCameraIdle,
        onPoiSelected = { currentOnPoiSelected.value(it) },
        onCameraIdle = { lat, lng -> currentOnCameraIdle.value(lat, lng) },
        onPinsProjected = { positions -> currentOnPinsProjected.value(positions) },
        onMapGestureStart = { currentOnMapGestureStart.value() },
        onStyleLoaded = { styleLoaded.value = true },
        onRenderFailed = { currentOnMapRenderFailed.value() },
    )
}
```

**6d.** Add `onPinsProjected` and `onMapGestureStart` to the `MapDelegate` constructor (lines 230–237):

```kotlin
private class MapDelegate(
    val annotationPoiMap: MutableMap<MLNPointAnnotation, POI>,
    private val suppressCameraIdle: BooleanArray,
    private val onPoiSelected: (POI?) -> Unit,
    private val onCameraIdle: (Double, Double) -> Unit,
    private val onPinsProjected: (Map<String, ScreenOffset>) -> Unit,   // NEW
    private val onMapGestureStart: () -> Unit,                          // NEW
    private val onStyleLoaded: () -> Unit,
    private val onRenderFailed: () -> Unit,
) : NSObject(), MLNMapViewDelegateProtocol {
```

**6e.** In the existing `regionDidChangeAnimated` method (lines 247–254), add pin projection after `onCameraIdle(lat, lng)`:

```kotlin
override fun mapView(mapView: MLNMapView, regionDidChangeAnimated: Boolean) {
    if (suppressCameraIdle[0]) {
        suppressCameraIdle[0] = false
        return
    }
    val (lat, lng) = mapView.centerCoordinate.useContents { latitude to longitude }
    onCameraIdle(lat, lng)

    // Project annotation screen positions (settle-and-show — fires once after camera stops)
    val projected = annotationPoiMap.entries.mapNotNull { (annotation, poi) ->
        val point = annotation.coordinate.useContents {
            mapView.convertCoordinate(
                CLLocationCoordinate2DMake(latitude, longitude),
                toPointTo = mapView,
            )
        }
        val x = point.useContents { x.toFloat() }
        val y = point.useContents { y.toFloat() }
        if (x == 0f && y == 0f) return@mapNotNull null  // off-screen or unmeasured
        poi.savedId to ScreenOffset(x, y)
    }.toMap()
    onPinsProjected(projected)
}
```

**6f.** Add the gesture-start delegate method immediately after `regionDidChangeAnimated`:

```kotlin
/** Fires when the camera region begins changing. animated=false → user gesture. */
override fun mapView(mapView: MLNMapView, regionWillChangeAnimated: Boolean) {
    if (!regionWillChangeAnimated && !suppressCameraIdle[0]) {
        onMapGestureStart()
    }
}
```

The `!suppressCameraIdle[0]` guard prevents firing gesture-start for programmatic camera moves (which also trigger this delegate before the suppress flag is read).

Add required import at top of file: `import com.harazone.ui.map.ScreenOffset`

---

**Task 7 — Create `PinMiniChip.kt`**

Create new file: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PinMiniChip.kt`

```kotlin
package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.POI
import com.harazone.ui.theme.MapFloatingUiDark

/** Maps a vibe name to its accent color. Mirrors the lookup in VibeRail.kt. */
private fun vibeAccentColor(vibeName: String?): Color = when (vibeName?.lowercase()) {
    "food", "eat"       -> Color(0xFFFF7043)
    "coffee"            -> Color(0xFF8D6E63)
    "drinks", "bar"     -> Color(0xFF7E57C2)
    "outdoors", "park"  -> Color(0xFF66BB6A)
    "culture", "art"    -> Color(0xFFEC407A)
    "shopping"          -> Color(0xFF29B6F6)
    "nightlife"         -> Color(0xFFAB47BC)
    "wellness", "spa"   -> Color(0xFF26C6DA)
    else                -> Color(0xFF90CAF9)  // default — soft blue
}

@Composable
fun PinMiniChip(
    poi: POI,
    isSaved: Boolean,
    isHero: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClosed = poi.liveStatus?.contains("closed", ignoreCase = true) == true
    val isOpen = poi.liveStatus?.contains("open", ignoreCase = true) == true
    val statusColor = when {
        isOpen   -> Color(0xFF4CAF50)
        isClosed -> Color(0xFFF44336)
        else     -> Color(0xFFFFAB40)
    }
    val vibeColor = vibeAccentColor(poi.vibes.firstOrNull() ?: poi.vibe)
    val borderColor = when {
        isSaved  -> Color(0xFFFFD700).copy(alpha = 0.7f)
        isHero   -> Color.White.copy(alpha = 0.4f)
        else     -> Color.White.copy(alpha = 0.1f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 120.dp)  // caps chip width; chipHalfWidthPx in PinCardLayer assumes 60dp half
            .alpha(if (isClosed) 0.5f else 1f)
            .clickable { onClick() }
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.94f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Dual-ring status dot: outer ring = vibe color, inner fill = status color
        Box(
            modifier = Modifier
                .size(12.dp)
                .border(2.dp, vibeColor, CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = poi.name,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (isClosed) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (poi.rating != null) {
                Text(
                    text = "★ ${poi.rating}",
                    fontSize = 10.sp,
                    color = Color(0xFFFFD700),
                )
            }
        }
    }
}
```

---

**Task 8 — Create `PinHeroCard.kt`**

Create new file: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PinHeroCard.kt`

```kotlin
package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.POI
import com.harazone.ui.theme.MapFloatingUiDark

@Composable
fun PinHeroCard(
    poi: POI,
    isSaved: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClosed = poi.liveStatus?.contains("closed", ignoreCase = true) == true
    val isOpen = poi.liveStatus?.contains("open", ignoreCase = true) == true
    val stripeColor = when {
        isOpen   -> Color(0xFF4CAF50)
        isClosed -> Color(0xFFF44336)
        else     -> Color(0xFFFFAB40)
    }
    val borderColor = if (isSaved) Color(0xFFFFD700).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f)

    Row(
        modifier = modifier
            .width(180.dp)
            .height(110.dp)  // explicit height so fillMaxHeight() on the stripe resolves correctly; matches HERO_CARD_HEIGHT_DP in PinCardLayer
            .clickable { onTap() }
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.96f)),
    ) {
        // Status stripe — left border, full card height
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(stripeColor),
        )
        Column(modifier = Modifier.padding(10.dp)) {
            // Vibe label
            Text(
                text = poi.vibes.firstOrNull() ?: poi.vibe,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            // Name
            Text(
                text = poi.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // Status pill — non-interactive; use a styled Box+Text to avoid empty onClick on SuggestionChip
            if (poi.liveStatus != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(stripeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = poi.liveStatus,
                        fontSize = 10.sp,
                        color = stripeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Rating
            if (poi.rating != null) {
                Text(
                    text = "★ ${poi.rating}",
                    fontSize = 11.sp,
                    color = Color(0xFFFFD700),
                )
            }
        }
    }
}
```

---

**Task 9 — Create `PinCardLayer.kt`**

Create new file: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PinCardLayer.kt`

This composable renders the full-screen overlay: Canvas leader line + positioned chips + hero card.

```kotlin
package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.POI
import com.harazone.ui.map.ScreenOffset
import com.areadiscovery.ui.components.PlatformBackHandler
import kotlin.math.roundToInt

private const val PIN_ICON_HEIGHT_DP = 48   // emoji symbol height in dp
private const val CHIP_HEIGHT_DP = 44       // PinMiniChip height in dp (matches chip padding + text + dual-ring row)
private const val HERO_CARD_HEIGHT_DP = 110 // PinHeroCard height in dp (matches Row height modifier; see Task 8)
private const val HERO_CARD_WIDTH_DP = 180  // PinHeroCard fixed width in dp (matches width(180.dp) in Task 8)

@Composable
fun PinCardLayer(
    pois: List<POI>,
    pinScreenPositions: Map<String, ScreenOffset>,
    savedPoiIds: Set<String>,
    selectedPinId: String?,
    cardsVisible: Boolean,
    screenHeightPx: Float,
    onChipTapped: (POI) -> Unit,
    onHeroTapped: (POI) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pinScreenPositions.isEmpty() || pois.isEmpty()) return

    val density = LocalDensity.current
    val heroPx     = with(density) { HERO_CARD_WIDTH_DP.dp.toPx() }
    val heroHeightPx = with(density) { HERO_CARD_HEIGHT_DP.dp.toPx() }
    val chipHeightPx = with(density) { CHIP_HEIGHT_DP.dp.toPx() }
    val chipHalfWidthPx = with(density) { 60.dp.toPx() }  // half of max chip width (120.dp max)
    val pinHeightPx  = with(density) { PIN_ICON_HEIGHT_DP.dp.toPx() }
    val chipGapPx = with(density) { 8.dp.toPx() }

    // Determine hero POI
    val heroPoi = if (selectedPinId != null) {
        pois.find { it.savedId == selectedPinId }
    } else null
    val effectiveHero = heroPoi ?: pois.firstOrNull()

    val heroPinOffset = effectiveHero?.let { pinScreenPositions[it.savedId] }

    // Android back button: if a chip has been promoted to hero (selectedPinId != null),
    // back clears the selection and returns to nearest-as-hero. CLAUDE.md platform rule.
    PlatformBackHandler(enabled = selectedPinId != null) {
        onChipTapped(pois.first { it.savedId == selectedPinId!! })  // toggle-deselect via onChipTapped
    }

    AnimatedVisibility(
        visible = cardsVisible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Leader line — drawn on full-screen Canvas
            if (heroPinOffset != null && effectiveHero != null) {
                val heroCardX = (heroPinOffset.x - heroPx / 2f).coerceAtLeast(8f)
                val pinY = heroPinOffset.y
                val isAbove = pinY > screenHeightPx * 0.25f
                val heroCardY = if (isAbove) {
                    (pinY - heroHeightPx - chipGapPx).coerceAtLeast(0f)
                } else {
                    pinY + pinHeightPx + chipGapPx
                }

                // Leader line endpoints depend on whether card is above or below pin
                val cardAnchorX = heroCardX + heroPx / 2f
                val cardAnchorY: Float
                val pinAnchorX = heroPinOffset.x
                val pinAnchorY: Float
                val ctrlY: Float
                if (isAbove) {
                    // Card is above pin: line from card bottom-center down to pin top
                    cardAnchorY = heroCardY + heroHeightPx
                    pinAnchorY  = pinY
                    ctrlY = cardAnchorY + (pinAnchorY - cardAnchorY) * 0.4f
                } else {
                    // Card is below pin: line from card top-center up to pin bottom
                    cardAnchorY = heroCardY
                    pinAnchorY  = pinY + pinHeightPx
                    ctrlY = pinAnchorY + (cardAnchorY - pinAnchorY) * 0.4f
                }

                val leaderColor = Color.White.copy(alpha = 0.55f)

                Canvas(Modifier.fillMaxSize()) {
                    val path = Path().apply {
                        moveTo(cardAnchorX, cardAnchorY)
                        quadraticBezierTo(
                            cardAnchorX,
                            ctrlY,
                            pinAnchorX,
                            pinAnchorY,
                        )
                    }
                    drawPath(
                        path = path,
                        color = leaderColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f),
                        ),
                    )
                }

                // Hero card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(heroCardX.roundToInt(), heroCardY.roundToInt()) },
                ) {
                    PinHeroCard(
                        poi = effectiveHero,
                        isSaved = effectiveHero.savedId in savedPoiIds,
                        onTap = { onHeroTapped(effectiveHero) },
                    )
                }
            }

            // Mini chips — all non-hero POIs
            pois.forEach { poi ->
                if (poi.savedId == effectiveHero?.savedId) return@forEach
                val pinOffset = pinScreenPositions[poi.savedId] ?: return@forEach
                val chipX = (pinOffset.x - chipHalfWidthPx).coerceAtLeast(8f)
                val chipY = (pinOffset.y - chipHeightPx - chipGapPx).coerceAtLeast(0f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(chipX.roundToInt(), chipY.roundToInt()) },
                ) {
                    PinMiniChip(
                        poi = poi,
                        isSaved = poi.savedId in savedPoiIds,
                        isHero = false,
                        onClick = { onChipTapped(poi) },
                    )
                }
            }
        }
    }
}
```

**Important note on `Modifier.offset` in a `Box(fillMaxSize)`:** The `fillMaxSize` Box positions children relative to (0,0) of the screen region. `Modifier.offset { IntOffset(x, y) }` applies a pixel translation on top of the composable's normal (0,0) placement within the Box — effectively placing it at absolute screen coordinates (x, y) within the Box bounds. This is the correct pattern for screen-coordinate overlays.

---

**Task 10 — `MapScreen.kt`: Replace FloatingPoiCard strip with PinCardLayer + wire callbacks**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`

**10a.** Add new imports:
```kotlin
import androidx.compose.ui.platform.LocalConfiguration
import com.harazone.ui.map.components.PinCardLayer
```

**10b.** Replace the FloatingPoiCard strip block (lines 204–219) with a platform-adaptive conditional. Do NOT remove the `FloatingPoiCard` import — it is still used as the iOS fallback.

Replace:
```kotlin
Row(
    verticalAlignment = Alignment.Top,
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = navBarPadding + 140.dp, start = 8.dp, end = 72.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    state.pois.take(3).forEach { poi ->
        FloatingPoiCard(
            poi = poi,
            isSaved = poi.savedId in state.savedPoiIds,
            onTap = { viewModel.selectPoi(poi) },
            modifier = Modifier.weight(1f),
        )
    }
}
```

With:
```kotlin
if (state.pinScreenPositions.isNotEmpty()) {
    // Android: projected pin-anchored cards (PinCardLayer added in 10c below)
} else if (!state.showListView && state.selectedPoi == null && state.pois.isNotEmpty()) {
    // Fallback — shown before first onCameraIdle projection completes (both platforms, brief):
    // also acts as a safety net if projection fails or returns empty
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navBarPadding + 140.dp, start = 8.dp, end = 72.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.pois.take(3).forEach { poi ->
            FloatingPoiCard(
                poi = poi,
                isSaved = poi.savedId in state.savedPoiIds,
                onTap = { viewModel.selectPoi(poi) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
```

**10c.** Add `PinCardLayer` in the main `Box`, after `MapComposable` and before the other overlay elements, guarded by `state.pinScreenPositions.isNotEmpty() && !state.showListView && state.selectedPoi == null`:

```kotlin
// Pin-anchored card layer — shown on both platforms once pinScreenPositions is populated (iOS via Task 6, Android via Task 5)
if (state.pinScreenPositions.isNotEmpty() && !state.showListView && state.selectedPoi == null) {
    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    PinCardLayer(
        pois = state.pois,
        pinScreenPositions = state.pinScreenPositions,
        savedPoiIds = state.savedPoiIds,
        selectedPinId = state.selectedPinId,
        cardsVisible = state.cardsVisible,
        screenHeightPx = screenHeightPx,
        onChipTapped = { poi -> viewModel.onPinChipTapped(poi.savedId) },
        onHeroTapped = { poi -> viewModel.selectPoi(poi) },
        modifier = Modifier.fillMaxSize(),
    )
}
```

**10d.** Update the `MapComposable(...)` call to pass the new callbacks (find the existing call and add two parameters after `onCameraIdle`):

```kotlin
onCameraIdle = { lat, lng -> viewModel.onCameraIdle(lat, lng) },
onPinsProjected = { positions -> viewModel.onPinsProjected(positions) },
onMapGestureStart = { viewModel.onMapGestureStart() },
```

---

**Task 11 — `MapScreen.kt`: Measure UI targets for OnboardingBubble**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`

**11a.** Add new imports:
```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
```

(Several of these may already be imported — only add missing ones.)

**11b.** Inside `ReadyContent` composable, declare three `Offset` state variables near the top of the function body:

```kotlin
var vibeRailOffset by remember { mutableStateOf(Offset.Zero) }
var savedFabOffset by remember { mutableStateOf(Offset.Zero) }
var searchBarOffset by remember { mutableStateOf(Offset.Zero) }
```

**11c.** On the `VibeRail` call (lines 263–281), append `onGloballyPositioned` to its `modifier`:

Current modifier:
```kotlin
modifier = Modifier
    .align(Alignment.BottomEnd)
    .padding(end = 8.dp, bottom = navBarPadding + 88.dp),
```

New modifier:
```kotlin
modifier = Modifier
    .align(Alignment.BottomEnd)
    .padding(end = 8.dp, bottom = navBarPadding + 88.dp)
    .onGloballyPositioned { coords ->
        vibeRailOffset = coords.boundsInRoot().centerLeft
    },
```

**11d.** On the `FabMenu` call (line ~408), append `onGloballyPositioned` to its modifier. If `FabMenu` does not have a `modifier` parameter, wrap it in a `Box`:

```kotlin
Box(
    modifier = Modifier.onGloballyPositioned { coords ->
        savedFabOffset = coords.boundsInRoot().center
    }
) {
    FabMenu(
        isExpanded = state.isFabExpanded,
        onToggle = { viewModel.toggleFab() },
        // ... rest of existing params unchanged
    )
}
```

**11e.** On the `AISearchBar` call (line ~469), append `onGloballyPositioned` to its `modifier`:

Current modifier:
```kotlin
modifier = Modifier
    .align(Alignment.BottomStart)
    // ... existing padding
```

New modifier:
```kotlin
modifier = Modifier
    .align(Alignment.BottomStart)
    // ... existing padding (unchanged)
    .onGloballyPositioned { coords ->
        searchBarOffset = coords.boundsInRoot().center
    },
```

**11f.** Update the `OnboardingBubble` call (lines 566–569) to pass measured offsets:

```kotlin
OnboardingBubble(
    visible = state.showOnboardingBubble,
    onDismiss = { viewModel.onOnboardingBubbleDismissed() },
    vibeRailOffset = vibeRailOffset,
    savedFabOffset = savedFabOffset,
    searchBarOffset = searchBarOffset,
)
```

---

**Task 12 — `OnboardingBubble.kt`: Replace hardcoded CalloutDot positions with measured offsets**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/OnboardingBubble.kt`

**12a.** Add new imports:
```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
```

**12b.** Update the `OnboardingBubble` function signature (line 74) to add three new parameters after `onDismiss`:

```kotlin
@Composable
fun OnboardingBubble(
    visible: Boolean,
    onDismiss: () -> Unit,
    vibeRailOffset: Offset = Offset.Zero,
    savedFabOffset: Offset = Offset.Zero,
    searchBarOffset: Offset = Offset.Zero,
) {
```

Default values of `Offset.Zero` preserve backwards compatibility if called without them (e.g., from preview or tests).

**12c.** Replace lines 104–110 (the three hardcoded `CalloutDot` placements):

Remove:
```kotlin
// TODO(BACKLOG-MEDIUM): Callout dot positions are hardcoded pixel offsets — will misalign on different screen sizes/aspect ratios. Use layout measurement or onGloballyPositioned to anchor dots to actual UI element positions.
// Callout dot — vibes rail (right side, mid-screen)
CalloutDot(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp, bottom = 80.dp))
// Callout dot — saved orb (right side, above mid)
CalloutDot(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, bottom = 200.dp))
// Callout dot — AI bar (bottom left)
CalloutDot(modifier = Modifier.align(Alignment.BottomStart).padding(start = 60.dp, bottom = 32.dp))
```

Replace with:
```kotlin
// Callout dots — positioned at measured layout bounds of target UI elements.
// Offset.Zero means not yet measured (first frame); dots skip rendering until measured.
if (vibeRailOffset != Offset.Zero) {
    CalloutDot(
        modifier = Modifier.offset {
            IntOffset(vibeRailOffset.x.roundToInt(), vibeRailOffset.y.roundToInt())
        }
    )
}
if (savedFabOffset != Offset.Zero) {
    CalloutDot(
        modifier = Modifier.offset {
            IntOffset(savedFabOffset.x.roundToInt(), savedFabOffset.y.roundToInt())
        }
    )
}
if (searchBarOffset != Offset.Zero) {
    CalloutDot(
        modifier = Modifier.offset {
            IntOffset(searchBarOffset.x.roundToInt(), searchBarOffset.y.roundToInt())
        }
    )
}
```

The `Modifier.offset { IntOffset(...) }` lambda version uses pixel offsets. The root coordinates from `boundsInRoot()` are in pixels, matching this API. The `Offset.Zero` guard ensures dots don't flash at (0,0) on the first frame before measurement.

---

### Acceptance Criteria

**AC1 — Mini chips appear pinned to map pins (Android)**

Given: Map shows 3 POIs, all with valid lat/lng, map has settled
When: `onCameraIdle` fires and projection completes
Then: 3 UI elements (chips or hero card) appear at positions visually overlapping or just above their respective map pins
And: Each chip shows the POI name (truncated) + dual-ring status dot

**AC2 — Hero card appears on nearest/selected pin with leader line (Android)**

Given: No pin is selected (`selectedPinId == null`), map has settled with 3 POIs
When: Cards become visible
Then: `pois[0]` renders as `PinHeroCard` (180dp wide, left confidence stripe, status pill, rating)
And: A curved dashed white line connects the bottom-center of the hero card to the top of its pin
And: The remaining 2 POIs render as `PinMiniChip`

**AC3 — Chip tap promotes to hero**

Given: 3 chips/cards visible, first POI is current hero
When: User taps the chip for `pois[1]`
Then: `pois[1]` renders as hero card with leader line
And: `pois[0]` renders as mini chip
And: Leader line now points to `pois[1]`'s pin

**AC4 — Hero card tap opens ExpandablePoiCard**

Given: Hero card is visible for `pois[0]`
When: User taps the hero card
Then: `viewModel.selectPoi(pois[0])` is called
And: `ExpandablePoiCard` opens (existing full-screen detail behavior, unchanged)
And: `PinCardLayer` is hidden (`state.selectedPoi != null` guard)

**AC5 — Cards hide during map gesture, reappear after settle (Android)**

Given: Cards are visible on screen
When: User begins panning the map
Then: All chips, hero card, and leader line immediately disappear (`cardsVisible = false`)
When: User lifts finger and map settles
Then: Cards reappear at updated pin positions reflecting new camera position

**AC6 — Confidence stripe and status pill color matches liveStatus**

Given: A POI has `liveStatus = "Open until 10pm"`
When: That POI renders as hero card
Then: Left stripe is green (`0xFF4CAF50`), status pill label is "Open until 10pm" in green
Given: `liveStatus = "Closed"`
Then: Left stripe is red (`0xFFF44336`), chip dot fill is red, chip name has strikethrough
Given: `liveStatus = null`
Then: Left stripe is amber (`0xFFFFAB40`)

**AC7 — Callout dots appear at measured positions (both platforms)**

Given: `OnboardingBubble` becomes visible for the first time
When: Layout has been measured (second frame after first composition)
Then: The VibeRail callout dot appears visually touching/overlapping the left edge of the VibeRail
And: The saved-fab callout dot appears at the center of the FabMenu
And: The AI search bar callout dot appears at the center of the AISearchBar

**AC8 — No callout dots at position (0,0) on first frame**

Given: `OnboardingBubble` becomes visible
When: Immediately on first visible frame (before layout measurement)
Then: Zero `CalloutDot` composables are rendered (all three offsets are `Offset.Zero`, guarded)
And: After second frame (post-measurement), all 3 dots appear at correct positions

**AC9 — Screen-size robustness for callout dots**

Given: Running on a device with non-standard screen size (e.g. large phone, or different aspect ratio)
When: OnboardingBubble is shown
Then: All 3 callout dots correctly align with their target elements (they follow the measured layout positions, not fixed dp offsets)
And: Resolves `TODO(BACKLOG-MEDIUM)` at `OnboardingBubble.kt:104`

---

## Additional Context

### Dependencies

- No new external library dependencies — all APIs used are already in the dependency graph:
  - `androidx.compose.ui.layout.boundsInRoot` — part of Compose UI (already imported in `MapScreen.kt` for other uses; verify)
  - `androidx.compose.ui.layout.onGloballyPositioned` — part of Compose UI
  - `mapLibreMap.projection.toScreenLocation(LatLng)` — MapLibre 11.x (already dependency)
  - `android.graphics.PointF` — Android stdlib
  - `androidx.compose.ui.graphics.PathEffect` — part of Compose UI Graphics
  - `androidx.compose.material3.SuggestionChip` — already used in `ChatOverlay.kt`
- `LocalConfiguration.current.screenHeightDp` requires `import androidx.compose.ui.platform.LocalConfiguration` — add to `MapScreen.kt` if missing.

### Testing Strategy

| Test | File | What to Verify |
|------|------|----------------|
| `onPinsProjected_setsPositionsAndCardsVisible` | `MapViewModelTest` | Call `onPinsProjected(mapOf("k" to ScreenOffset(10f, 20f)))`; assert `state.pinScreenPositions["k"] == ScreenOffset(10f, 20f)` and `state.cardsVisible == true` |
| `onMapGestureStart_hidesCards` | `MapViewModelTest` | Set `cardsVisible = true` via `onPinsProjected`; call `onMapGestureStart()`; assert `state.cardsVisible == false` |
| `onPinChipTapped_togglesSelection` | `MapViewModelTest` | Call `onPinChipTapped("id1")`; assert `state.selectedPinId == "id1"`; call again; assert `state.selectedPinId == null` |
| `onboardingBubble_noDots_whenOffsetsZero` | Composable test | Render `OnboardingBubble(visible=true, onDismiss={}, vibeRailOffset=Offset.Zero, ...)` — assert zero `CalloutDot` nodes rendered |
| `onboardingBubble_dots_whenOffsetsMeasured` | Composable test | Render `OnboardingBubble(visible=true, onDismiss={}, vibeRailOffset=Offset(100f,200f), savedFabOffset=Offset(300f,400f), searchBarOffset=Offset(50f,600f))` — assert 3 `CalloutDot` composables rendered |
| `pinCardLayer_hidesWhenPositionsEmpty` | Composable test | Render `PinCardLayer(pinScreenPositions=emptyMap(), ...)` — assert no chip or card nodes rendered |
| `pinCardLayer_heroIsFirstPoi_whenNoSelection` | Composable test | Render with 2 POIs and no `selectedPinId`; assert first POI renders as `PinHeroCard` and second as `PinMiniChip` |
| `onPinChipTapped_backButtonClearsSelection` | `MapViewModelTest` | Call `onPinChipTapped("id1")`; assert `state.selectedPinId == "id1"`; call `onPinChipTapped("id1")` again (simulate back via toggle); assert `state.selectedPinId == null` |
| `pinCardLayer_heroCardBelowPin_whenPinInTop25Percent` | Composable test | Render with hero pin at `y = screenHeight * 0.10f`; assert hero card Y offset is greater than pin Y (card is below pin, not above) |
| `pinCardLayer_chipY_clampsToZero_whenPinNearTop` | Composable test | Render with a non-hero pin at `y = 10f`; assert chip composable is rendered (not clipped/absent) — `chipY.coerceAtLeast(0f)` prevents negative offset |

### Notes

- **`currentPois` in `MapComposable.android.kt`**: The `cameraIdleListener` is a lambda captured at style-load time. It references `currentPois` which must be a `rememberUpdatedState` ref to always have the latest `pois` list. Search for any existing `val currentPois = rememberUpdatedState(...)` in the file. If not present, add it alongside the other `rememberUpdatedState` declarations near line 74.
- **`Modifier.offset { IntOffset(...) }` vs `Modifier.absoluteOffset`**: Use `offset { }` (the lambda version that takes a density lambda) — it uses pixel values directly and is applied after the composable's own layout. `absoluteOffset` is similar but `offset` is the idiomatic choice for pixel-space placement within a `Box`.
- **`PinCardLayer` placement in MapScreen Box**: Must be placed AFTER the `MapComposable` but BEFORE `VibeRail`, `FabMenu`, and `AISearchBar`. This ensures the map is drawn first, then the pin cards overlay the map, then the UI chrome is drawn on top of everything.
- **FloatingPoiCard fallback**: Both platforms show the old bottom strip briefly on first load (before the first `onCameraIdle` projection fires). After `onCameraIdle` → `onPinsProjected`, `pinScreenPositions` becomes non-empty and the strip swaps to `PinCardLayer`. On both platforms this transition is instant and imperceptible. The fallback also acts as a safety net if projection returns an empty map (e.g. all POIs off-screen).
- **iOS `regionWillChangeAnimated` race condition**: `suppressCameraIdle[0]` is set inside a `LaunchedEffect` (coroutine — async). `regionWillChangeAnimated` fires synchronously on the iOS main thread when the programmatic move begins. In theory the suppress flag may not be set yet when the delegate fires, allowing a false `onMapGestureStart()` call. In practice, `setCenterCoordinate(animated: true)` produces `regionWillChangeAnimated = true`, so the `!regionWillChangeAnimated` check is the primary guard. The `!suppressCameraIdle[0]` is a secondary guard for programmatic moves that also pass `animated = false`. If false positives are observed during testing, the mitigation is to post `onMapGestureStart()` one frame later via `dispatch_async(dispatch_get_main_queue())` in the delegate — but do not pre-apply this unless needed.
- **iOS `convertCoordinate` returning (0,0)**: Off-screen or not-yet-rendered annotations may return `CGPoint(0,0)`. The `if (x == 0f && y == 0f) return@mapNotNull null` guard drops these from the projection map. Chips for those POIs simply won't render until the POI is scrolled on-screen and the camera settles again.
- **FabMenu modifier**: If `FabMenu` composable does not accept a `modifier` parameter, the wrapping `Box` approach in Task 11d is correct. Check `FabMenu.kt` — if it already has a `modifier` param, pass directly to avoid extra layout node.
- **Prototype reference**: `_bmad-output/brainstorming/prototype-pin-anchored-cards.html` — open in browser to see the final hybrid design with all scenarios (chips, hero, leader line, status pills, closed dimming). Also `prototype-game-ui-anchoring.html` for the cross-pollination inspiration.
- **`TODO(BACKLOG-MEDIUM)` resolution**: After implementing Task 12, the TODO comment at `OnboardingBubble.kt:104` is resolved. Remove it as part of Task 12c (already done — the replacement code does not include the TODO).
- **Leader line control point**: The control point is always on the card-anchor X axis, 40% of the way toward the pin anchor. For above-pin case: `ctrlY = cardAnchorY + (pinAnchorY - cardAnchorY) * 0.4f`. For below-pin (flip) case: `ctrlY = pinAnchorY + (cardAnchorY - pinAnchorY) * 0.4f`. Both cases are implemented in Task 9.
