---
title: 'Auto-Refresh Area Portrait on Map Pan/Zoom'
slug: 'auto-refresh-portrait-on-pan-zoom'
created: '2026-03-06'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Jetpack Compose', 'MapLibre Android', 'Koin', 'Coroutines']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
code_patterns: ['expect/actual', 'Job cancellation debounce', 'StateFlow + copy()', 'Snackbar error notification']
test_patterns: ['MapViewModelTest — coroutine + fake providers']
---

# Tech-Spec: Auto-Refresh Area Portrait on Map Pan/Zoom

**Created:** 2026-03-06

## Overview

### Problem Statement

When the user pans or zooms the map to a new area, the area portrait (TopContextBar name + POI pins) stays stale — it still reflects the original location. The app has no awareness of where the map camera is pointing after the initial load.

### Solution

Add an `onCameraIdle(lat, lng)` callback through the `expect/actual MapComposable`. On Android, wire MapLibre's `addOnCameraIdleListener` to emit the camera center. `MapViewModel` debounces 500ms, reverse-geocodes the new center, and if the area name differs from the current one, fetches a fresh portrait. Old pins remain visible during the fetch. On failure, a snackbar notifies the user.

### Scope

**In Scope:**
- `onCameraIdle: (lat: Double, lng: Double) -> Unit` parameter on `MapComposable` expect/actual (all three files)
- Android: `map.addOnCameraIdleListener` fires callback with camera center lat/lng
- iOS: no-op stub (map not implemented on iOS yet)
- `MapViewModel.onCameraIdle(lat, lng)` — debounce 500ms via Job cancel + delay, then reverse-geocode + portrait fetch if area name changed
- Manual search (`searchJob`) wins: `onCameraIdle` does nothing while a `searchJob` is active
- Cancel previous camera-idle job if a new idle fires before previous completes
- Keep existing `pois` visible during fetch (no wipe on start)
- Snackbar on portrait fetch failure (reuse existing `snackbarHostState` in `ReadyContent`)
- Error snackbar message: "Couldn't load area info. Try panning again."

**Out of Scope:**
- Loading spinner / indicator during fetch
- iOS camera idle events
- Analytics for pan events
- Camera position written back to `MapUiState`

## Context for Development

### Codebase Patterns

- **Debounce pattern**: No `debounce()` operator used — the codebase cancels the previous `Job` then `delay()`s inside a new coroutine. See `searchJob` in `MapViewModel`. Use identical pattern for the new `cameraIdleJob`.
- **Portrait fetch**: `getAreaPortrait(areaName, context).collect { update -> if (update is BucketUpdate.PortraitComplete) { ... } }` — exact pattern used in both `loadLocation()` and `submitSearch()`. Reuse as-is.
- **Reverse geocode**: `locationProvider.reverseGeocode(lat, lng): Result<String>` — already called in `loadLocation()`. Same call, same null/failure handling.
- **Snackbar**: `ReadyContent` already owns a `snackbarHostState` + `coroutineScope`. The callback needs to surface errors up to that scope. Use a `SharedFlow<String>` or a simple `onError: () -> Unit` lambda passed down. Simplest: add `snackbarMessage: String?` to `MapUiState.Ready` (already the pattern for future use) — OR expose a `SharedFlow<String> errorEvents` from the ViewModel (cleaner, avoids state reset). Use `SharedFlow` to match KMP best practice.
- **expect/actual**: `MapComposable.kt` defines the `expect` signature; `.android.kt` and `.ios.kt` are the `actual` implementations. Adding a parameter requires updating all three files simultaneously.
- **MapLibre camera idle**: `map.addOnCameraIdleListener { ... }` — listener fires after camera animation settles. `map.cameraPosition.target` gives the `LatLng` center at that moment.
- **`rememberUpdatedState`**: Already used for `onPoiSelected` and `onMapRenderFailed` in the Android actual. Use the same pattern for `onCameraIdle` to avoid stale closure captures.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt` | expect signature — add `onCameraIdle` param |
| `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt` | actual Android — wire `addOnCameraIdleListener` |
| `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt` | actual iOS — no-op stub |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` | add `onCameraIdle()` fun + `cameraIdleJob` + `errorEvents` SharedFlow |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` | pass `onCameraIdle` to MapComposable; collect `errorEvents` for snackbar |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` | add tests for new behaviour |

### Technical Decisions

- **Debounce via Job cancel + delay(500)** — matches existing `searchJob` pattern, no new operators needed.
- **`SharedFlow<String>` for errors** — `errorEvents` on ViewModel, collected in `MapScreen` via `LaunchedEffect`. Avoids polluting `MapUiState` with transient snackbar strings.
- **Area name comparison is case-insensitive trim** — `reverseGeocode` may return slightly different casing on retries; compare with `.trim().equals(other, ignoreCase = true)`.
- **Double manual search guard**: Check `searchJob?.isActive` twice — at entry AND after `delay(500)` — because a manual search can start during the debounce window after the initial guard passes.
- **Fresh state snapshot after geocode**: Read `_uiState.value` after delay+geocode, not before. Avoids stale `areaName` comparison when state changed during the 500ms window.
- **`(0.0, 0.0)` guard**: Return immediately if coordinates are default-zero — MapLibre can fire `onCameraIdle` during initialization before the camera has moved to the real location.
- **First-token comparison**: Compare `areaName.substringBefore(",").trim()` rather than the full geocoded string. Geocoders inconsistently append/strip city suffixes on zoom — first token ("Alfama" from "Alfama, Lisbon") is stable and avoids false-positive refetches.
- **`areaName` update timing**: Update `areaName` in state only after portrait completes (together with new `pois`) — consistent UX, no mismatch between name and pins.

## Implementation Plan

### Tasks

Tasks are ordered lowest-dependency first.

- [x] **Task 1 — Add `onCameraIdle` to ViewModel + `errorEvents` SharedFlow**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: Add `private var cameraIdleJob: Job? = null` field alongside existing `loadJob`/`searchJob`. Add `private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)` and `val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()`. Add the following imports (full qualified paths):
    ```kotlin
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.SharedFlow
    import kotlinx.coroutines.flow.asSharedFlow
    ```
  - Add the `onCameraIdle` function below. Note: the `searchJob` guard blocks camera-idle refresh during **both** AI Q&A streaming and area portrait searches — this is intentional; panning while AI is responding does not trigger a portrait refresh.
  - Notes: The complete function body to add:
    ```kotlin
    fun onCameraIdle(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return              // guard: spurious idle at init
        if (searchJob?.isActive == true) return            // guard: manual search wins
        if (_uiState.value !is MapUiState.Ready) return
        cameraIdleJob?.cancel()
        cameraIdleJob = viewModelScope.launch {
            delay(500)
            if (searchJob?.isActive == true) return@launch // guard: search started during debounce
            val geocodeResult = locationProvider.reverseGeocode(lat, lng)
            if (geocodeResult.isFailure) return@launch
            val newAreaName = geocodeResult.getOrThrow()
            // Re-read state fresh after debounce+geocode; compare first token to reduce geocoder noise
            val current = _uiState.value as? MapUiState.Ready ?: return@launch
            val newToken = newAreaName.substringBefore(",").trim()
            val currentToken = current.areaName.substringBefore(",").trim()
            if (newToken.equals(currentToken, ignoreCase = true)) return@launch
            try {
                val context = areaContextFactory.create()
                getAreaPortrait(newAreaName, context)
                    .catch { e ->
                        AppLogger.e(e) { "Camera idle: portrait fetch failed" }
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
                                areaName = newAreaName,
                                pois = pois,
                                vibePoiCounts = counts,
                                activeVibe = newActiveVibe,
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Camera idle: unexpected error" }
                _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
            }
        }
    }
    ```

- [x] **Task 2 — Update expect signature in `MapComposable.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt`
  - Action: Add `onCameraIdle: (lat: Double, lng: Double) -> Unit` as the last parameter in the `expect fun MapComposable(...)` signature, after `onMapRenderFailed`.
  - Notes: Full updated signature:
    ```kotlin
    @Composable
    expect fun MapComposable(
        modifier: Modifier,
        latitude: Double,
        longitude: Double,
        zoomLevel: Double,
        pois: List<POI>,
        activeVibe: Vibe,
        onPoiSelected: (POI?) -> Unit,
        onMapRenderFailed: () -> Unit,
        onCameraIdle: (lat: Double, lng: Double) -> Unit,
    )
    ```

- [x] **Task 3 — Wire camera idle in Android actual**
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Action 1: Add `onCameraIdle: (lat: Double, lng: Double) -> Unit` parameter to `actual fun MapComposable()`.
  - Action 2: Add `val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)` alongside the existing `rememberUpdatedState` calls at lines 64–65 (after `currentOnMapRenderFailed`).
  - Action 3: Store the listener in a variable so it can be deregistered, then register it. Insert the following block **after** `map.addOnMapClickListener { ... }` (around line 123) and **before** the closing `}` of the `setStyle` callback (around line 127). This is the last thing inside the `if (!isDestroyed[0])` block inside `setStyle`:
    ```kotlin
    val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
        val target = map.cameraPosition.target ?: return@OnCameraIdleListener
        currentOnCameraIdle.value(target.latitude, target.longitude)
    }
    map.addOnCameraIdleListener(cameraIdleListener)
    ```
  - Action 4: Store the listener reference so `onDispose` can remove it. Add `val cameraIdleListenerRef = remember { arrayOfNulls<MapLibreMap.OnCameraIdleListener>(1) }` alongside the other `remember` refs at the top of the composable. Assign it after registration: `cameraIdleListenerRef[0] = cameraIdleListener`.
  - Action 5: In the `DisposableEffect` `onDispose` block (around line 313), add deregistration **before** `mapView.onPause()`:
    ```kotlin
    cameraIdleListenerRef[0]?.let { mapRef[0]?.removeOnCameraIdleListener(it) }
    ```
  - Notes: `map.cameraPosition.target` is nullable — the null guard is required. Listener is registered inside `setStyle` callback, so it only activates after style loads — this naturally prevents spurious idles during initial camera setup. The listener reference **must** be stored and deregistered in `onDispose`; omitting this causes a second listener to accumulate on every recomposition, resulting in double geocode calls.

- [x] **Task 4 — Update iOS stub**
  - File: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt`
  - Action: Add `onCameraIdle: (lat: Double, lng: Double) -> Unit` parameter to `actual fun MapComposable()`. No body change needed — parameter is unused.

- [x] **Task 5 — Wire in `MapScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action 1: In `ReadyContent`, add error event collector after the existing `remember` calls. Key on `viewModel` so the collector restarts if the ViewModel instance changes, but note it will be cancelled during any brief `Loading` state from `retry()` — this is an accepted trade-off since retry errors are handled separately:
    ```kotlin
    LaunchedEffect(viewModel) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    ```
  - Action 2: Add `onCameraIdle` to the `MapComposable(...)` call. Be careful: `state.latitude` and `state.longitude` are the **original GPS coordinates** — do NOT pass these to `onCameraIdle`. The lambda receives the camera center from MapLibre directly:
    ```kotlin
    onCameraIdle = { lat, lng -> viewModel.onCameraIdle(lat, lng) },
    ```

- [x] **Task 6 — Add ViewModel tests**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
  - Action: Add 6 new test cases (see Testing Strategy section for full descriptions of each).
  - Notes: For debounce and time-sensitive tests, use `StandardTestDispatcher` — but you MUST set it as the `Main` dispatcher exactly like the existing `gpsTimeoutTransitionsToLocationFailed` test does (swap `resetMain`/`setMain` inside a try/finally block). Simply creating a `StandardTestDispatcher` without making it `Main` will not affect `viewModelScope.launch`, which always uses the `Main` dispatcher. Create local inline fakes for variable geocode results — do not modify the shared `FakeLocationProvider` class.

### Acceptance Criteria

- [ ] **AC1 — Camera idle triggers refresh when area changes**
  - Given: map is loaded with area "Alfama, Lisbon" and POIs visible
  - When: user pans to a new location, camera settles, and reverse geocode returns "Bairro Alto, Lisbon"
  - Then: within ~500ms debounce + fetch time, TopContextBar updates to "Bairro Alto, Lisbon" and new POI pins appear

- [ ] **AC2 — No refresh when area name unchanged**
  - Given: camera idles at a position that reverse-geocodes to the same area name (or same first-token, case-insensitive)
  - When: `onCameraIdle` fires
  - Then: no new portrait fetch occurs; `pois` and `areaName` unchanged

- [ ] **AC3 — Debounce: rapid panning triggers only one fetch**
  - Given: user pans quickly across three areas in under 500ms
  - When: camera finally idles on the third area
  - Then: only one portrait fetch is made (for the final area); previous in-flight geocode/fetch is cancelled

- [ ] **AC4 — Old pins stay visible during fetch**
  - Given: a camera-idle portrait fetch is in progress
  - When: portrait response has not yet arrived
  - Then: the previous area's POI pins remain on the map (no empty state flash)

- [ ] **AC5 — Error snackbar on fetch failure**
  - Given: reverse geocode succeeds and area name changed, but `getAreaPortrait` throws or emits an error
  - When: the coroutine catches the error
  - Then: snackbar displays "Couldn't load area info. Try panning again."

- [ ] **AC6 — Manual search takes priority**
  - Given: a manual area search `searchJob` is active
  - When: the camera idles and `onCameraIdle` is called
  - Then: no camera-idle geocode or fetch is started; manual search completes normally

- [ ] **AC7 — Spurious (0,0) idle is ignored**
  - Given: MapLibre fires `onCameraIdle` with coordinates (0.0, 0.0) during initialization
  - When: `onCameraIdle(0.0, 0.0)` is called on the ViewModel
  - Then: no geocode call is made and state is unchanged

- [ ] **AC8 — iOS: no crash**
  - Given: iOS actual is updated with the new parameter
  - When: MapComposable is rendered on iOS
  - Then: app runs without crash; `onCameraIdle` lambda is never invoked

## Additional Context

### Dependencies

- No new dependencies. Uses existing `locationProvider`, `getAreaPortrait`, `areaContextFactory`, `AppLogger` already injected into `MapViewModel`.
- `MutableSharedFlow` is in `kotlinx-coroutines-core` (already a dependency).

### Testing Strategy

Add to `MapViewModelTest.kt`:

- **`onCameraIdle debounces correctly`**: Must use `StandardTestDispatcher` set as `Main` (see `gpsTimeoutTransitionsToLocationFailed` for the exact try/finally dispatcher-swap boilerplate). Call `onCameraIdle` 3x with `advanceTimeBy(200)` between each. After the third call, `advanceTimeBy(600)` to let the debounce settle. Assert `geocodeCallCount == 1` on the location provider fake (only the final call's coroutine survived long enough to geocode).
- **`onCameraIdle fetches portrait when area changes`**: Use `UnconfinedTestDispatcher` (default). Create a local inline fake `LocationProvider` with `var geocodeResult = Result.success("New Area Name")` where "New Area Name" differs from the initial "Alfama, Lisbon". Create the ViewModel (initial area = "Alfama, Lisbon"), call `onCameraIdle(lat, lng)` with any non-zero coordinates. Assert state has `areaName == "New Area Name"` and new `pois`.
- **`onCameraIdle no-ops when area name unchanged`**: `FakeLocationProvider(geocodeResult = Result.success("Alfama, Lisbon"))` — initial load and camera-idle geocode both return "Alfama, Lisbon" (same first token). Call `onCameraIdle`, assert `FakeAreaRepository.callCount` is unchanged from after initial load.
- **`onCameraIdle no-ops when searchJob active`**: Create a `FakeAreaRepository` that never completes (use `CompletableDeferred` pattern from `ResettableFakeLocationProvider`). Call `viewModel.submitSearch("some area")` to start an in-flight `searchJob`. Create a separate fake `LocationProvider` with a geocode call counter. Call `onCameraIdle`. Assert that separate fake's `geocodeCallCount == 0`.
- **`onCameraIdle emits error event on fetch failure`**: Create a `FakeAreaRepository` that throws on `getPortrait`. Collect `viewModel.errorEvents` in a launched coroutine. Call `onCameraIdle` with coords that geocode to a different area. Assert collected message equals `"Couldn't load area info. Try panning again."`.
- **`onCameraIdle keeps existing pois during fetch`** *(AC4 regression guard)*: Create ViewModel with initial POIs loaded. Make `FakeAreaRepository` never complete on the second call (simulate slow fetch). Call `onCameraIdle` targeting a new area. Assert `(uiState.value as MapUiState.Ready).pois` still contains the original POIs (not empty) before the fetch resolves.

**Fake note**: `FakeLocationProvider.geocodeResult` is `private val` — for "area changed" tests, create an inline local fake with a `var geocodeResult` or a call-index list (similar to `ResettableFakeLocationProvider` defined locally in `MapViewModelTest.kt`). Do not modify the shared `FakeLocationProvider` class.

Existing fakes: `FakeAreaRepository`, `FakeLocationProvider`, `FakeAreaContextFactory`, `FakeAnalyticsTracker`, `FakeWeatherProvider` — all in `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/`.

### Notes

- `map.cameraPosition.target` is nullable in MapLibre Android — guard with `?: return@addOnCameraIdleListener`.
- `_errorEvents` uses `extraBufferCapacity = 2` so `tryEmit` never drops if collector is slightly behind.
- The `onCameraIdle` listener is registered inside `setStyle` callback, so it is only active after the map style has fully loaded — this avoids spurious idle events during initial camera setup.

## Review Notes
- Adversarial review completed
- Findings: 6 total, 2 fixed, 4 skipped
- Resolution approach: walk-through
- F1 (Medium/Real): Fixed — cancel cameraIdleJob on search start + searchJob guard inside collect
- F2 (Medium/Real): Fixed — bumped extraBufferCapacity to 2
- F3-F5 (Low/Real): Skipped — accepted design trade-offs per tech spec
- F6 (Low/Noise): Skipped — expected iOS stub behavior
