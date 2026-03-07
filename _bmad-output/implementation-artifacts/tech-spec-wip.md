---
title: 'Search This Area Button'
slug: 'search-this-area-button'
created: '2026-03-06'
status: 'complete'
stepsCompleted: [1, 2, 3, 4, 5, 6]
tech_stack: ['Kotlin Multiplatform', 'Jetpack Compose', 'MapLibre Android', 'Koin', 'Coroutines']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
code_patterns: ['AnimatedVisibility', 'StateFlow + copy()', 'Job cancellation debounce', 'expect/actual']
test_patterns: ['MapViewModelTest — coroutine + fake providers']
---

# Tech-Spec: Search This Area Button

**Created:** 2026-03-06

## Overview

### Problem Statement

The current auto-refresh-on-pan/zoom behavior (from the previous spec) automatically fires portrait fetches whenever the camera settles on a new area. This is buggy, makes unnecessary API calls, and prevents the user from freely exploring the map. Users have no control over when a refresh happens.

### Solution

Replace the auto-fetch in `onCameraIdle` with a manual "Search this area" pill button. When the camera settles on a different area, a pill button appears top-center below the TopContextBar. The user taps it to trigger a portrait refresh. The button disappears after tapping or when the camera returns to the current area. No fetch ever happens automatically.

### Scope

**In Scope:**
- Remove auto-fetch logic from `onCameraIdle` in `MapViewModel`
- Remove `lastFetchedZoom`, `ZOOM_CHANGE_THRESHOLD`, `INITIAL_ZOOM` — no longer needed
- Remove `zoom: Double` parameter from the `onCameraIdle` callback signature (all 3 MapComposable files)
- Add `showSearchThisArea: Boolean = false` to `MapUiState.Ready`
- Add private ViewModel vars: `pendingLat`, `pendingLng`, `pendingAreaName`
- `onCameraIdle` becomes: debounce → geocode → compare first-token → show or hide button (no fetch)
- Add `fun onSearchThisAreaTapped()` to ViewModel: hide button + trigger portrait fetch using cached `pendingAreaName`
- New `SearchThisAreaButton` composable in `MapScreen.kt` — pill-shaped, top-center, below TopContextBar
- `AnimatedVisibility` with slide-down + fade-in / slide-up + fade-out animation

**Out of Scope:**
- "All pins" feature (separate spec)
- Any changes to VibeRail or vibe filtering
- iOS camera idle events (iOS stub remains no-op)
- Analytics for button taps
- Loading indicator while fetch is in progress after button tap

---

## Context for Development

### Codebase Patterns

- **Debounce**: Cancel previous `Job` + `delay(500)` inside new coroutine. `cameraIdleJob` already exists in `MapViewModel` — keep it, just change what happens after the debounce.
- **Portrait fetch**: `getAreaPortrait(areaName, context).catch { }.collect { update -> if (update is BucketUpdate.PortraitComplete) { ... } }` — exact same pattern as `submitSearch`. `onSearchThisAreaTapped` reuses it identically.
- **State update**: `_uiState.value = current.copy(...)` — standard pattern throughout ViewModel.
- **AnimatedVisibility**: Already used for other overlay elements in the project. Use `slideInVertically { -it } + fadeIn()` / `slideOutVertically { -it } + fadeOut()` for the pill sliding down from TopContextBar.
- **MapFloatingUiDark**: The color used by `TopContextBar` for the dark pill style. Reuse for the button background to maintain visual consistency.
- **expect/actual**: Removing `zoom` from the `onCameraIdle` signature requires updating all three files — `MapComposable.kt` (expect), `.android.kt` (actual), `.ios.kt` (actual).

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt` | Add `showSearchThisArea` field |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` | Gut auto-fetch from `onCameraIdle`; add `onSearchThisAreaTapped()`; remove zoom tracking |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt` | Remove `zoom` from `onCameraIdle` expect signature |
| `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt` | Remove `zoom` from actual + callback invocation |
| `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt` | Remove `zoom` from actual |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` | Add `SearchThisAreaButton` composable; update `onCameraIdle` lambda |

### Technical Decisions

- **No re-geocode on tap**: `pendingAreaName` is cached from the `onCameraIdle` debounce. `onSearchThisAreaTapped()` uses it directly — no second geocode call, snappy tap response.
- **Private ViewModel vars, not state**: `pendingLat`, `pendingLng`, `pendingAreaName` are not display data — they're action data. Keep them as private fields, not in `MapUiState`.
- **Button never auto-hides except on area match**: Button visibility only changes when (a) camera idles back to the same area first-token, or (b) user taps it. POI card open, search overlay open, FAB expanded — none of these hide the button. User is exploring; don't interrupt.
- **Remove zoom from signature**: `zoom: Double` was only used for `ZOOM_CHANGE_THRESHOLD` which is being deleted. Removing it from the signature is a clean break. Touch all 3 MapComposable files.
- **`cameraIdleJob` cancel on tap**: `onSearchThisAreaTapped()` should cancel `cameraIdleJob` first (user tapped; any in-flight debounce is irrelevant). Then fetch.
- **`searchJob` guard remains**: If a manual search is active, `onCameraIdle` still returns early. Same as before.
- **First-token comparison**: `areaName.substringBefore(",").trim()` — unchanged from the existing implementation. Stable against geocoder casing/suffix noise.

---

## Implementation Plan

### Tasks

Tasks ordered lowest-dependency first.

- [x] **Task 1 — Add `showSearchThisArea` to `MapUiState.Ready`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
  - Action: Add `val showSearchThisArea: Boolean = false` as the last field in the `Ready` data class.

- [x] **Task 2 — Refactor `MapViewModel.onCameraIdle` + add `onSearchThisAreaTapped`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action 1: Remove `private var lastFetchedZoom: Double = Double.NaN` field.
  - Action 2: Remove `ZOOM_CHANGE_THRESHOLD` and `INITIAL_ZOOM` constants from the `companion object`.
  - Action 3: Remove the `lastFetchedZoom = INITIAL_ZOOM` line from `loadLocation()`.
  - Action 4: Add three private fields below `cameraIdleJob`:
    ```kotlin
    private var pendingLat: Double = 0.0
    private var pendingLng: Double = 0.0
    private var pendingAreaName: String = ""
    ```
  - Action 5: Replace the entire body of `onCameraIdle` with the following. Change its signature from `(lat: Double, lng: Double, zoom: Double)` to `(lat: Double, lng: Double)`:
    ```kotlin
    fun onCameraIdle(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        if (searchJob?.isActive == true) return
        if (_uiState.value !is MapUiState.Ready) return
        cameraIdleJob?.cancel()
        cameraIdleJob = viewModelScope.launch {
            delay(500)
            if (searchJob?.isActive == true) return@launch
            val geocodeResult = locationProvider.reverseGeocode(lat, lng)
            if (geocodeResult.isFailure) return@launch
            val newAreaName = geocodeResult.getOrThrow()
            val current = _uiState.value as? MapUiState.Ready ?: return@launch
            val newToken = newAreaName.substringBefore(",").trim()
            val currentToken = current.areaName.substringBefore(",").trim()
            if (newToken.equals(currentToken, ignoreCase = true)) {
                // Panned back to current area — hide the button
                _uiState.value = current.copy(showSearchThisArea = false)
                return@launch
            }
            // Different area — cache and show button
            pendingLat = lat
            pendingLng = lng
            pendingAreaName = newAreaName
            _uiState.value = current.copy(showSearchThisArea = true)
        }
    }
    ```
  - Action 6: Add `onSearchThisAreaTapped` function below `onCameraIdle`:
    ```kotlin
    fun onSearchThisAreaTapped() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        _uiState.value = current.copy(showSearchThisArea = false)
        val areaName = pendingAreaName.ifBlank { return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val context = areaContextFactory.create()
                getAreaPortrait(areaName, context)
                    .catch { e ->
                        AppLogger.e(e) { "Search this area: portrait fetch failed" }
                        _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
                    }
                    .collect { update ->
                        if (update is BucketUpdate.PortraitComplete) {
                            val pois = update.pois
                            val state = _uiState.value as? MapUiState.Ready ?: return@collect
                            val counts = computeVibePoiCounts(pois)
                            val newActiveVibe = if ((counts[state.activeVibe] ?: 0) == 0) {
                                Vibe.entries.maxByOrNull { counts[it] ?: 0 } ?: Vibe.DEFAULT
                            } else {
                                state.activeVibe
                            }
                            _uiState.value = state.copy(
                                areaName = areaName,
                                pois = pois,
                                vibePoiCounts = counts,
                                activeVibe = newActiveVibe,
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Search this area: unexpected error" }
                _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
            }
        }
    }
    ```

- [x] **Task 3 — Remove `zoom` from expect signature**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt`
  - Action: Change `onCameraIdle: (lat: Double, lng: Double, zoom: Double) -> Unit` to `onCameraIdle: (lat: Double, lng: Double) -> Unit`.

- [x] **Task 4 — Update Android actual**
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Action 1: Change `onCameraIdle: (lat: Double, lng: Double, zoom: Double) -> Unit` parameter to `onCameraIdle: (lat: Double, lng: Double) -> Unit`.
  - Action 2: In the `OnCameraIdleListener` lambda, change the callback invocation from `currentOnCameraIdle.value(target.latitude, target.longitude, map.cameraPosition.zoom)` to `currentOnCameraIdle.value(target.latitude, target.longitude)`.

- [x] **Task 5 — Update iOS stub**
  - File: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt`
  - Action: Change `onCameraIdle: (lat: Double, lng: Double, zoom: Double) -> Unit` to `onCameraIdle: (lat: Double, lng: Double) -> Unit`. No body change needed.

- [x] **Task 6 — Add `SearchThisAreaButton` and wire in `MapScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action 1: Add the following imports if not present:
    ```kotlin
    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
    import androidx.compose.animation.slideInVertically
    import androidx.compose.animation.slideOutVertically
    import androidx.compose.foundation.layout.Row
    import androidx.compose.foundation.layout.size
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.Search
    import androidx.compose.material3.Icon
    import androidx.compose.material3.Surface
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.shape.RoundedCornerShape
    import com.areadiscovery.ui.theme.MapFloatingUiDark
    ```
  - Action 2: Update `onCameraIdle` lambda in the `MapComposable(...)` call — remove the `zoom` parameter:
    ```kotlin
    onCameraIdle = { lat, lng -> viewModel.onCameraIdle(lat, lng) },
    ```
  - Action 3: Add `SearchThisAreaButton` as an overlay in the `Box`, positioned top-center below the TopContextBar. Insert it immediately after the `TopContextBar(...)` block:
    ```kotlin
    // "Search this area" button
    AnimatedVisibility(
        visible = state.showSearchThisArea,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 56.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MapFloatingUiDark.copy(alpha = 0.90f),
            modifier = Modifier.clickable { viewModel.onSearchThisAreaTapped() },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Search this area",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
    ```
  - Notes: `top = 56.dp` = 8dp TopContextBar offset + ~40dp bar height + 8dp gap. Adjust if TopContextBar height differs on device — test on real device. `MapFloatingUiDark` is already imported in TopContextBar; confirm it is accessible from MapScreen (same package visibility). Add `import androidx.compose.foundation.layout.width` and `import androidx.compose.ui.graphics.Color` if not already present.

### Acceptance Criteria

- **AC1 — Button appears after pan to new area**
  - Given: map is loaded with portrait for "Alfama"
  - When: user pans to "Bairro Alto" and camera settles
  - Then: within ~500ms debounce, "Search this area" pill appears below TopContextBar with slide-down animation

- **AC2 — Button disappears when panning back**
  - Given: "Search this area" button is visible (panned to new area)
  - When: user pans back to "Alfama" and camera settles
  - Then: button slides up and disappears; no fetch occurs

- **AC3 — Tapping button triggers refresh and hides button**
  - Given: "Search this area" button is visible for "Bairro Alto"
  - When: user taps the button
  - Then: button disappears immediately; portrait fetch fires for "Bairro Alto"; TopContextBar and pins update when complete

- **AC4 — No auto-fetch on pan/zoom**
  - Given: user pans or zooms the map freely
  - When: camera settles on any area
  - Then: no portrait fetch fires automatically; only the button appears if area differs

- **AC5 — Button stays visible while POI card is open**
  - Given: "Search this area" button is visible
  - When: user taps a POI pin (POI card opens)
  - Then: button remains visible; user can still tap it after dismissing the card

- **AC6 — Button stays visible while search overlay is open**
  - Given: "Search this area" button is visible
  - When: user opens the AI search overlay
  - Then: button state is unchanged (overlay covers it visually; no state reset)

- **AC7 — Manual search cancels pending camera idle**
  - Given: "Search this area" button is visible (cameraIdleJob may be in debounce)
  - When: user submits a manual search
  - Then: `cameraIdleJob` is cancelled; button disappears (state resets on new portrait load)

- **AC8 — Spurious (0,0) idle ignored**
  - Given: MapLibre fires `onCameraIdle(0.0, 0.0)` during init
  - Then: no geocode, no state change, no button shown

---

## Additional Context

### Dependencies

No new dependencies. `AnimatedVisibility`, `slideInVertically`, `fadeIn/Out` are in `androidx.compose.animation` (already a dependency). `Icons.Default.Search` is in `material-icons-core` (check if extended icons are needed — prefer core). `MapFloatingUiDark` already exists in the theme.

### Testing Strategy

No new ViewModel tests required for this spec — the debounce/geocode logic already has full test coverage from the previous spec. The key behavioral change (show button instead of fetch) can be verified by asserting `showSearchThisArea == true` in the existing debounce tests if desired, but this is deferred until design stabilizes.

Manual device test checklist:
1. Pan to a new neighborhood → button appears
2. Pan back → button disappears
3. Tap button → button gone, pins/name update
4. Zoom in/out within same area → no button
5. Open POI card while button visible → button still there after dismissing card

### Notes

- `top = 56.dp` is an estimate based on TopContextBar's `padding(top = 8.dp)` + ~40dp bar height + 8dp gap. Verify and adjust on device.
- `onSearchThisAreaTapped` uses `searchJob` (not a dedicated job) so it participates in the existing search-wins guard correctly.
- If `pendingAreaName` is blank when user taps (edge case: button shown but pending state somehow cleared), `onSearchThisAreaTapped` returns early safely via `ifBlank { return }`.
- The `areaName` written to state on fetch completion uses `pendingAreaName` (the geocoded string), not `current.areaName`. This is intentional — same as `submitSearch` which writes `query` to `areaName`.
