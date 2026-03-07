---
title: 'Return to Current Location Button'
slug: 'return-to-current-location'
created: '2026-03-07'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Jetpack Compose', 'MapLibre Android', 'Koin', 'Coroutines', 'kotlinx.coroutines.test']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt'
code_patterns: ['StateFlow + copy()', 'AnimatedVisibility', 'LocationProvider.getCurrentLocation()', 'onCameraIdle debounce', 'suppressCameraIdle flag', 'MapFloatingUiDark pill style']
test_patterns: ['UnconfinedTestDispatcher + runTest', 'FakeLocationProvider with configurable results', 'ResettableFakeLocationProvider for async flows', 'assertIs<MapUiState.Ready> pattern']
---

# Tech-Spec: Return to Current Location Button

**Created:** 2026-03-07

## Overview

### Problem Statement

When users pan or zoom away from their GPS location on the map, there is no way to snap back to where they are. They must close and reopen the app or manually drag the map back. This is a basic map UX expectation that is currently missing.

### Solution

Add a "return to current location" button (circular dark icon button with MyLocation crosshair icon) positioned on the left side above the AI search bar. The button appears when the map camera is 100m+ away from the user's GPS coordinates. Tapping it re-fetches fresh GPS, animates the camera back, and re-fetches POIs if the area name has changed.

### Scope

**In Scope:**
- Store GPS coordinates in Ready state for distance comparison
- Show/hide MyLocation button based on 100m+ threshold from GPS
- Re-fetch fresh GPS on tap via LocationProvider.getCurrentLocation()
- Animate camera to new GPS coords
- Re-fetch area portrait if area name differs from GPS area
- Position: left side, above AI search bar (Position C)
- Dark circle style matching existing floating UI

**Out of Scope:**
- iOS MapComposable changes (camera already animates via LaunchedEffect)
- Continuous GPS tracking / blue dot on map
- Compass rotation button
- Zoom +/- controls

## Context for Development

### Codebase Patterns

- UI state managed via `MapUiState.Ready` data class with `copy()` updates
- `LocationProvider.getCurrentLocation()` returns `Result<GpsCoordinates>` (data class with `latitude`/`longitude`)
- Camera position in `MapComposable.android.kt` updates via `LaunchedEffect(latitude, longitude)` — currently uses `moveCamera()` (instant snap). **Must change to `animateCamera()` for smooth animation** (see Task 2b).
- `onCameraIdle(lat, lng)` callback already fires on every camera settle — reuse for distance check
- Existing floating UI uses `MapFloatingUiDark.copy(alpha = 0.90f)` with `RoundedCornerShape(50)`
- `suppressCameraIdle` flag in `MapComposable.android.kt` prevents camera-idle callbacks from programmatic camera moves. **Important**: `returnToCurrentLocation` updates `latitude`/`longitude` in state, which triggers the `LaunchedEffect` → `animateCamera` → camera idle event. Must set `suppressCameraIdle[0] = true` before the animate call to prevent a spurious `onCameraIdle` → reverse geocode → "Search this area" flicker.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `MapUiState.kt` | Add `gpsLatitude`, `gpsLongitude`, `showMyLocation` fields to `Ready` |
| `MapViewModel.kt` | Add `returnToCurrentLocation()` function, distance check in `onCameraIdle`, update `loadLocation()` to store GPS coords |
| `MapScreen.kt` | Add MyLocation button composable with `AnimatedVisibility` at Position C |
| `MapComposable.android.kt` | Change `moveCamera` to `animateCamera` in `LaunchedEffect(latitude, longitude)` + set `suppressCameraIdle` before animate |
| `MapComposable.kt` (expect) | No changes needed |
| `MapViewModelTest.kt` | Add tests for `returnToCurrentLocation` (same area, different area, GPS failure) |
| `FakeLocationProvider.kt` | Already supports configurable results — may need mutable results for multi-call scenarios |
| `GpsCoordinates.kt` | Simple data class (`latitude`, `longitude`) — no changes needed |

### Technical Decisions

- **100m threshold**: Use simple degree-based approximation. At most latitudes relevant to users, `0.001` degrees latitude ~= 111m and `0.001` degrees longitude ~= 80-111m. Calculate: `abs(cameraLat - gpsLat) > 0.0009 || abs(cameraLng - gpsLng) > 0.0009`. This is simple, fast, and accurate enough for show/hide UX — not navigation-grade.
- **Fresh GPS every tap**: Always call `getCurrentLocation()` rather than using cached launch coords. Handles user physically moving between pans.
- **Conditional POI refetch**: After GPS fetch + reverse geocode, compare `areaName` token (substring before first comma) with the geocoded GPS area name token. Same token = camera-only move. Different token = full portrait refetch using the same pattern as `onSearchThisAreaTapped`.
- **Button hidden during search/loading**: Hide the MyLocation button when `isSearchingArea` is true or `isSearchOverlayOpen` is true — avoid visual clutter during active operations.

## Implementation Plan

### Tasks

- [x] Task 1: Add GPS tracking fields to `MapUiState.Ready`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
  - Action: Add three fields to the `Ready` data class:
    - `gpsLatitude: Double = 0.0` — last known GPS latitude
    - `gpsLongitude: Double = 0.0` — last known GPS longitude
    - `showMyLocation: Boolean = false` — controls button visibility

- [x] Task 2a: Store GPS coords on initial load
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: In `loadLocation()` at the point where `MapUiState.Ready` is first created (line ~321), also set `gpsLatitude = coords.latitude` and `gpsLongitude = coords.longitude`.

- [x] Task 2b: Change `moveCamera` to `animateCamera` + suppress camera idle
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Action: In the `LaunchedEffect(latitude, longitude)` block (line ~97-103), when style is already loaded, change:
    ```kotlin
    // Before (instant snap):
    map.moveCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)))
    // After (smooth animation + suppress idle):
    suppressCameraIdle[0] = true
    map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)), 600, null)
    ```
  - Notes: The `600` ms duration matches the existing POI-fit animation duration. Setting `suppressCameraIdle` prevents `onCameraIdle` from firing a spurious reverse geocode + "Search this area" pill flicker after returning to location.

- [x] Task 3: Add distance helper function
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: Add a private helper in `MapViewModel`:
    ```kotlin
    private fun isAwayFromGps(cameraLat: Double, cameraLng: Double, state: MapUiState.Ready): Boolean {
        return kotlin.math.abs(cameraLat - state.gpsLatitude) > 0.0009 ||
               kotlin.math.abs(cameraLng - state.gpsLongitude) > 0.0009
    }
    ```

- [x] Task 4: Update `onCameraIdle` to set `showMyLocation`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: Inside the `onCameraIdle` method, after the existing debounce logic completes (inside the `cameraIdleJob` coroutine), add a `showMyLocation` update. At the point where `_uiState.value = current.copy(showSearchThisArea = true, ...)` is set (line ~224), also include `showMyLocation = isAwayFromGps(lat, lng, current)`. Additionally, when camera idle fires but the position is back near GPS, ensure `showMyLocation = false`. The simplest approach: always set `showMyLocation` in the same `copy()` call that sets `showSearchThisArea`:
    ```kotlin
    _uiState.value = current.copy(
        showSearchThisArea = true,
        isNewArea = isNew,
        showMyLocation = isAwayFromGps(lat, lng, current),
    )
    ```
    Also: at the top of `onCameraIdle`, before the early returns, add a quick non-debounced check — if camera is back near GPS, immediately hide the button AND return early to skip the debounced reverse geocode:
    ```kotlin
    val readyState = _uiState.value as? MapUiState.Ready ?: return
    if (!isAwayFromGps(lat, lng, readyState)) {
        if (readyState.showMyLocation || readyState.showSearchThisArea) {
            _uiState.value = readyState.copy(showMyLocation = false, showSearchThisArea = false)
        }
        return  // IMPORTANT: skip debounced path — no need to geocode when near GPS
    }
    ```
    Place this check right after the `if (_uiState.value !is MapUiState.Ready) return` guard (line ~209), before `cameraIdleJob?.cancel()`. The `return` prevents the debounced path from running and re-showing `showSearchThisArea` — this was identified as a flicker bug in adversarial review.

- [x] Task 5: Add `returnToCurrentLocation()` function
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: Add a new public function:
    ```kotlin
    fun returnToCurrentLocation() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        searchJob?.cancel()

        viewModelScope.launch {
            try {
                val locResult = locationProvider.getCurrentLocation()
                if (locResult.isFailure) {
                    _errorEvents.tryEmit("Can't find your location. Please try again.")
                    return@launch
                }
                val coords = locResult.getOrThrow()
                val geocodeResult = locationProvider.reverseGeocode(coords.latitude, coords.longitude)
                val gpsAreaName = geocodeResult.getOrNull() ?: current.areaName

                val state = _uiState.value as? MapUiState.Ready ?: return@launch
                val gpsToken = gpsAreaName.substringBefore(",").trim()
                val currentToken = state.areaName.substringBefore(",").trim()
                val isSameArea = gpsToken.equals(currentToken, ignoreCase = true)

                // Fire analytics immediately — before portrait fetch which may be long/failing
                analyticsTracker.trackEvent(
                    "return_to_location",
                    mapOf("same_area" to isSameArea.toString()),
                )

                if (isSameArea) {
                    // Same area — just move camera, update GPS coords
                    _uiState.value = state.copy(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        gpsLatitude = coords.latitude,
                        gpsLongitude = coords.longitude,
                        showMyLocation = false,
                        showSearchThisArea = false,
                    )
                } else {
                    // Different area — move camera + refetch portrait
                    _uiState.value = state.copy(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        gpsLatitude = coords.latitude,
                        gpsLongitude = coords.longitude,
                        showMyLocation = false,
                        showSearchThisArea = false,
                        isSearchingArea = true,
                        pois = emptyList(),
                        vibePoiCounts = emptyMap(),
                        activeVibe = null,
                    )
                    val context = areaContextFactory.create()
                    getAreaPortrait(gpsAreaName, context)
                        .catch { e ->
                            AppLogger.e(e) { "Return to location: portrait fetch failed" }
                            val s = _uiState.value as? MapUiState.Ready ?: return@catch
                            _uiState.value = s.copy(isSearchingArea = false)
                            _errorEvents.tryEmit("Couldn't load area info. Try again.")
                        }
                        .collect { update ->
                            if (update is BucketUpdate.PortraitComplete) {
                                val pois = update.pois
                                val s = _uiState.value as? MapUiState.Ready ?: return@collect
                                val counts = computeVibePoiCounts(pois)
                                _uiState.value = s.copy(
                                    areaName = gpsAreaName,
                                    pois = pois,
                                    vibePoiCounts = counts,
                                    activeVibe = null,
                                    isSearchingArea = false,
                                )
                            }
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Return to location: unexpected error" }
                _errorEvents.tryEmit("Can't find your location. Please try again.")
            }
        }
    }
    ```

- [x] Task 6: Hide MyLocation button during active operations
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: In `onSearchThisAreaTapped()`, add `showMyLocation = false` to the `copy()` call at line ~234. In `openSearchOverlay()`, add `showMyLocation = false` to the `copy()` call at line ~89.

- [x] Task 7: Add MyLocation button composable to MapScreen
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: Add the button inside the `Box(Modifier.fillMaxSize())` in `ReadyContent`, positioned at bottom-left above the AI search bar. Place it between the VibeRail and the ExpandablePoiCard blocks. Add necessary imports: `Icons.Default.MyLocation`, `IconButton`, `RoundedCornerShape`.
    ```kotlin
    // MyLocation button (Position C — left side, above AI bar)
    AnimatedVisibility(
        visible = state.showMyLocation && !state.isSearchingArea && !state.isSearchOverlayOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 12.dp, bottom = navBarPadding + 84.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MapFloatingUiDark.copy(alpha = 0.92f),
            modifier = Modifier
                .size(40.dp)
                .clickable { viewModel.returnToCurrentLocation() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Return to my location",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    ```
  - Notes: Import `androidx.compose.material.icons.filled.MyLocation`. Uses `navBarPadding + 84.dp` to sit above the AI search bar (which is at `navBarPadding + 16.dp` and ~44dp tall, plus ~24dp gap). The `navBarPadding` variable is already computed in `ReadyContent` at line ~274.

- [x] Task 8: Add unit tests for `returnToCurrentLocation`
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
  - Action: Add the following tests using existing patterns (FakeLocationProvider, assertIs, runTest):
    1. `returnToCurrentLocation_sameArea_movesCameraWithoutRefetch` — GPS returns same area token. Assert: `latitude`/`longitude` updated, `gpsLatitude`/`gpsLongitude` updated, `showMyLocation = false`, `pois` unchanged, `isSearchingArea = false`.
    2. `returnToCurrentLocation_differentArea_refetchesPortrait` — GPS returns different area. Assert: `areaName` updated, new POIs loaded, `isSearchingArea` transitions true then false.
    3. `returnToCurrentLocation_gpsFailure_emitsError` — `getCurrentLocation()` returns failure. Assert: error event emitted, state unchanged.
    4. `returnToCurrentLocation_firesAnalytics` — Assert: `return_to_location` event tracked with `same_area` param.
    5. `onCameraIdle_showsMyLocationWhenFarFromGps` — Pan 100m+ away. Assert: `showMyLocation = true`.
    6. `onCameraIdle_hidesMyLocationWhenNearGps` — Pan back near GPS. Assert: `showMyLocation = false`.
  - Notes: For tests needing mutable location results, use inline `object : LocationProvider` overrides (same pattern as existing `onSearchThisAreaTapped` tests). FakeLocationProvider may need a second `locationResult` for the return-to-location call — use the inline override pattern.

### Acceptance Criteria

- [ ] AC 1: Given the user is viewing the map at their GPS location, when they have not panned, then the MyLocation button is NOT visible.
- [ ] AC 2: Given the user has panned 100m+ from their GPS location, when the camera settles, then the MyLocation button fades in at Position C (left side, above AI bar).
- [ ] AC 3: Given the MyLocation button is visible, when the user taps it, then the app re-fetches fresh GPS coordinates, animates the camera to the new position, and hides the button.
- [ ] AC 4: Given the user panned to a different area name (e.g., Marrickville -> Newtown), when they tap MyLocation, then the app also refetches the area portrait and updates POIs for the GPS area.
- [ ] AC 5: Given the user panned within the same area (e.g., 200m within Marrickville), when they tap MyLocation, then the camera moves back but POIs are NOT refetched.
- [ ] AC 6: Given GPS fails when the user taps MyLocation, when the error occurs, then a snackbar shows "Can't find your location. Please try again." and state is unchanged.
- [ ] AC 7: Given the user pans back to within 100m of GPS without tapping the button, when the camera settles, then the MyLocation button hides automatically.
- [ ] AC 8: Given a search or area loading is in progress, when `isSearchingArea` or `isSearchOverlayOpen` is true, then the MyLocation button is hidden.
- [ ] AC 9: Given the MyLocation button styling, then it matches existing dark floating UI (dark circle, white icon, 40dp size, 0.92 alpha).

## Additional Context

### Dependencies

- No new libraries needed. All functionality uses existing `LocationProvider`, `MapUiState`, and Compose primitives.
- `Icons.Default.MyLocation` is available in `androidx.compose.material:material-icons-extended` — verify this dependency exists in `build.gradle.kts`. If only `material-icons-core` is present, `MyLocation` may not be available. Fallback: use `Icons.Default.GpsFixed` or a custom icon.

### Testing Strategy

- **Unit tests** (Task 8): 6 new tests in `MapViewModelTest.kt` covering happy path, error, and edge cases. Run with `./gradlew :composeApp:test`.
- **Manual testing**: Deploy to device, pan away from GPS, verify button appears/hides/works. Test: same area return, different area return, GPS failure (airplane mode).
- **Smoke test**: Existing `AppLaunchSmokeTest` covers app launch — no changes needed.

### Notes

- The `Icons.Default.MyLocation` icon may require `material-icons-extended` dependency. Check before implementing. If not available, use `Icons.Default.GpsFixed` from `material-icons-core`.
- The `navBarPadding + 84.dp` positioning may need visual tuning on device. Adjust after device testing.
- Future enhancement: animate the icon (pulse/spin) while GPS is being fetched to indicate loading state. Out of scope for V1.
- The `showMyLocation` flag is independent of `showSearchThisArea` — both can be visible simultaneously. "Search this area" appears at top-center, MyLocation at bottom-left. No visual conflict.
