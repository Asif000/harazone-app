# Story 3.1: MapLibre Integration & Base Map

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to see an interactive map centered on my current area that I can pan and zoom,
so that I can visually explore my surroundings.

## Acceptance Criteria

1. **Given** the user taps the Map tab in bottom navigation, **when** the map screen loads, **then** a MapLibre map renders centered on the current area within 3 seconds [NFR4].
2. **Given** MapLibre is the map engine, **when** the map composable is built, **then** it uses `expect/actual` wrappers so `commonMain` has no direct MapLibre SDK imports (KMP portability).
3. **Given** the map is displayed, **when** the user interacts with it, **then** standard pan and zoom gestures work freely [FR16].
4. **Given** the device is offline or tiles fail to load, **when** the map renders, **then** cached tiles are served or a minimal base map is shown — no crash, no blank error screen [NFR24].
5. **Given** the map screen is active, **when** the ViewModel initialises, **then** `MapViewModel` exposes `StateFlow<MapUiState>` starting at `MapUiState.Loading`, transitioning to `MapUiState.Ready(areaName, latitude, longitude)` on success or `MapUiState.LocationFailed(message)` on GPS failure/timeout.
6. **Given** the map is ready, **when** it renders, **then** the map fills the full screen width with a collapsible `BottomSheetScaffold` overlay at peek height showing the resolved area name.
7. **Given** the user is on the Map screen, **when** the bottom navigation renders, **then** the Map tab icon/label shows in the selected/active state.

## Tasks / Subtasks

- [x] Task 1: `MapUiState` + `MapViewModel` (AC: 5, 7)
  - [x] 1.1: Create `commonMain/.../ui/map/MapUiState.kt` — sealed class with three variants:
    - `data object Loading : MapUiState()`
    - `data class Ready(val areaName: String, val latitude: Double, val longitude: Double, val pois: List<POI> = emptyList()) : MapUiState()`
    - `data class LocationFailed(val message: String) : MapUiState()`
    - `pois` is empty for Story 3.1 — Story 3.2 will populate it; include the field now to avoid a breaking state change later.
  - [x] 1.2: Create `commonMain/.../ui/map/MapViewModel.kt` — inject `LocationProvider` and `PrivacyPipeline`; on init launch both calls in parallel via `async { }` in `viewModelScope`:
    - `locationProvider.getCurrentLocation()` → provides `GpsCoordinates` for centering the map
    - `privacyPipeline.resolveAreaName()` → provides the human-readable area name for the bottom sheet header
    - On both success → emit `MapUiState.Ready(areaName, lat, lon)`
    - On either failure → emit `MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)`
    - Use `LOCATION_FAILURE_MESSAGE = "Can't find your location. Please try again."` as a companion constant (mirrors `SummaryViewModel` convention)
  - [x] 1.3: Add `retry()` method to `MapViewModel` that resets state to `Loading` and re-invokes location resolution — mirrors `SummaryViewModel.refresh()` pattern
  - [x] 1.4: Wire `MapViewModel` into Koin `UiModule.kt` — `viewModel { MapViewModel(get(), get()) }` (where `get()` resolves `LocationProvider` and `PrivacyPipeline`)

- [x] Task 2: `expect/actual` map composable wrapper (AC: 2, 3, 4)
  - [x] 2.1: Create `commonMain/.../ui/map/MapComposable.kt` — declare the expect function:
    ```kotlin
    @Composable
    expect fun MapComposable(
        modifier: Modifier,
        latitude: Double,
        longitude: Double,
        zoomLevel: Double,
    )
    ```
    No MapLibre imports in `commonMain` — this is the KMP boundary.
  - [x] 2.2: Create `androidMain/.../ui/map/MapComposable.android.kt` — implement via `AndroidView`:
    - Wrap MapLibre's `MapView` in `AndroidView { context -> MapView(context).apply { onCreate(Bundle()) } }`
    - Call `mapView.getMapAsync { mapboxMap -> mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoomLevel)) }` to center the map
    - Forward `DisposableEffect` lifecycle callbacks: `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`, `onLowMemory` to the `MapView` instance — MapLibre requires this for correct tile lifecycle management
    - MapLibre's built-in tile cache (offline manager) handles NFR24 automatically — no additional code needed
    - Default `zoomLevel`: 14.0 (neighbourhood-level, suitable for area exploration)
    - Default map style: `Style.MAPBOX_STREETS` or a free MapLibre tile provider (document the tile endpoint used)
  - [x] 2.3: Create `iosMain/.../ui/map/MapComposable.ios.kt` — stub actual:
    ```kotlin
    @Composable
    actual fun MapComposable(modifier: Modifier, latitude: Double, longitude: Double, zoomLevel: Double) {
        // MapLibre iOS deferred — no iOS CocoaPods setup yet (tracked in backlog notes)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Map not yet available on iOS", style = MaterialTheme.typography.bodyMedium)
        }
    }
    ```

- [x] Task 3: `MapScreen` composable (AC: 1, 3, 4, 6, 7)
  - [x] 3.1: Create `commonMain/.../ui/map/MapScreen.kt` — full `@Composable fun MapScreen()`:
    - Obtain ViewModel via `val viewModel: MapViewModel = koinViewModel()`
    - Collect `uiState` via `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`
    - Render based on `uiState`:
      - `Loading` → `Box(fillMaxSize) { CircularProgressIndicator(Alignment.Center) }`
      - `LocationFailed` → warm fallback `Column` with `ContentNoteBanner`-style message + `Button("Retry") { viewModel.retry() }`
      - `Ready` → `BottomSheetScaffold` + `MapComposable`
  - [x] 3.2: For `Ready` state, use `BottomSheetScaffold` (M3 component):
    - `sheetPeekHeight = 88.dp` (collapsed state — shows area name + drag handle)
    - Sheet content: drag handle + `Text(state.areaName, style = headlineSmall)` + `Text("Explore this area", style = bodyMedium, color = onSurfaceVariant)`
    - `content = { paddingValues -> MapComposable(Modifier.fillMaxSize().padding(paddingValues), state.latitude, state.longitude, 14.0) }`
    - The three-stop full sheet (collapsed/half/full with POI list) is implemented in Story 3.3
  - [x] 3.3: Replace `MapPlaceholderScreen` in `AppNavigation.kt` — update `composable<MapRoute>` block to instantiate `MapScreen()` instead of `MapPlaceholderScreen()`
  - [x] 3.4: Delete `MapPlaceholderScreen.kt` — no longer needed after this story

- [x] Task 4: MapLibre lifecycle management (AC: 1, 4)
  - [x] 4.1: In `MapComposable.android.kt`, use `rememberUpdatedState` to safely reference `latitude`/`longitude` in callbacks (prevents stale closure captures after recomposition)
  - [x] 4.2: Store the `MapView` reference in a `remember { }` block so it survives recompositions without reinitializing MapLibre
  - [x] 4.3: Attach a `DisposableEffect(Unit)` to manage full MapView lifecycle — call `mapView.onStart()`, `onResume()` in effect body; `mapView.onPause()`, `onStop()`, `onDestroy()` in `onDispose { }` — MapLibre requires all six lifecycle calls or tiles will not cache/release correctly

- [x] Task 5: `MapViewModelTest` (AC: 5)
  - [x] 5.1: Create `commonTest/.../ui/map/MapViewModelTest.kt` (consistent with `SummaryViewModelTest` being in `commonTest`, not `androidUnitTest` as the architecture draft states — established project convention for pure-Kotlin ViewModel tests)
  - [x] 5.2: Test: `initialStateIsLoading` — verify `viewModel.uiState.value` is `MapUiState.Loading` before coroutines run (use `UnconfinedTestDispatcher` pattern from `SummaryViewModelTest`)
  - [x] 5.3: Test: `locationSuccessTransitionsToReady` — `FakeLocationProvider` returns success + `FakePrivacyPipeline` returns success → state is `MapUiState.Ready` with correct area name, lat, lon
  - [x] 5.4: Test: `locationFailureTransitionsToLocationFailed` — `FakeLocationProvider` returns failure → state is `MapUiState.LocationFailed` with `LOCATION_FAILURE_MESSAGE`
  - [x] 5.5: Test: `privacyPipelineFailureTransitionsToLocationFailed` — location succeeds but `FakePrivacyPipeline` returns failure → state is `MapUiState.LocationFailed`
  - [x] 5.6: Test: `retryResetsToLoadingAndReloads` — verify retry resets to `Loading` then resolves again

## Dev Notes

### Architecture Requirements

**KMP map boundary:** MapLibre 11.11.0 is in `androidMain` only (see `build.gradle.kts:58`). All `commonMain` code must interact with the map through the `expect/actual` `MapComposable` function. Zero MapLibre imports allowed in `commonMain` or test source sets.

**Lifecycle requirement:** MapLibre `MapView` is a classic Android View, not a Compose component. It requires all six lifecycle callbacks (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`). Omitting any will cause tile cache corruption or memory leaks. The `DisposableEffect` in `MapComposable.android.kt` is the canonical KMP-safe way to hook these.

**Parallel location calls:** `MapViewModel` makes two location-related calls on init:
1. `locationProvider.getCurrentLocation()` — for GPS coordinates (to center the map)
2. `privacyPipeline.resolveAreaName()` — for human-readable area name (for bottom sheet)

Both calls must run concurrently via `async { }.await()` within the same `viewModelScope.launch { }` block. If either fails, transition to `LocationFailed`. The `PrivacyPipeline` has a built-in 10s GPS timeout (established in Story 2.5). The `locationProvider.getCurrentLocation()` call does not — the ViewModel scope cancellation is the implicit timeout.

**`pois: List<POI>` in `MapUiState.Ready`:** Include this field with default `emptyList()` now. Story 3.2 will set it without breaking this state class. This avoids a forced refactor in the next story.

**Bottom sheet:** Use `androidx.compose.material3.BottomSheetScaffold` (already in M3 dependency). Do NOT import `accompanist` bottom sheet — the M3 built-in is what the UX spec references and it's already available.

**Tile provider / map style:**
MapLibre is provider-agnostic. For development, use a free tile provider:
- Option A: `https://demotiles.maplibre.org/style.json` (MapLibre demo tiles — always available, no API key)
- Option B: OpenStreetMap-backed style via Protomaps or a self-hosted PMTiles endpoint
Document the chosen endpoint in `MapComposable.android.kt` as a named constant:
```kotlin
private const val MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json"
```
This makes swapping to a production tile provider in Phase 1b a one-line change.

**NFR24 — tile fallback:** MapLibre 11.11.0's built-in offline tile cache handles this natively. When tiles fail to load, MapLibre serves whatever is already in the device tile cache. If the cache is empty and the device is offline, MapLibre renders a minimal/empty base map — it does not crash. No additional code is needed to satisfy NFR24 for Story 3.1.

**NFR4 — 3-second render:** MapLibre initialises asynchronously via `getMapAsync`. The `Loading` state covers the period between screen entry and map ready. The map itself is displayed immediately when `MapUiState.Ready` is emitted (location resolution is the bottleneck, not MapLibre init).

### Project Structure Notes

**Source tree changes this story:**

| Action | Path |
|--------|------|
| CREATE | `commonMain/.../ui/map/MapUiState.kt` |
| CREATE | `commonMain/.../ui/map/MapViewModel.kt` |
| CREATE | `commonMain/.../ui/map/MapScreen.kt` |
| CREATE | `commonMain/.../ui/map/MapComposable.kt` (expect) |
| CREATE | `androidMain/.../ui/map/MapComposable.android.kt` (actual) |
| CREATE | `iosMain/.../ui/map/MapComposable.ios.kt` (actual stub) |
| CREATE | `commonTest/.../ui/map/MapViewModelTest.kt` |
| MODIFY | `commonMain/.../di/UiModule.kt` (add `MapViewModel` binding) |
| MODIFY | `commonMain/.../ui/navigation/AppNavigation.kt` (swap placeholder) |
| DELETE | `commonMain/.../ui/map/MapPlaceholderScreen.kt` |

**Existing infrastructure reused:**
- `FakeLocationProvider` (`commonTest/fakes/`) — already exists from Story 2.1
- `FakePrivacyPipeline` (`commonTest/fakes/`) — already exists from Story 2.5
- `MapRoute` navigation route — already defined in `Routes.kt`
- `MapLibre` dependency — already declared in `build.gradle.kts:58` and `libs.versions.toml:21`
- `NavigationBar` / Map tab — already wired and showing active state in `BottomNavBar.kt:37`

**Architecture spec note:** The architecture draft lists `MapViewModelTest.kt` under `androidUnitTest`. This story places it in `commonTest` to match the established project convention from Story 2.5 (`SummaryViewModelTest` is in `commonTest`). Pure-Kotlin ViewModel tests run on the JVM in `commonTest` without issues.

### Previous Story Learnings (from 2.5)

1. **`expect/actual` boundary discipline:** Zero platform imports in `commonMain`. The `MapComposable` `expect` function must have no MapLibre references — the actual implementations hold all SDK code.
2. **State machine consistency:** All `when(state)` branches must cover every `MapUiState` variant — no `is ...` patterns that silently ignore new variants. Use exhaustive `when` expressions.
3. **`koinViewModel()` in composables:** Import from `org.koin.compose.viewmodel.koinViewModel` — this is the correct Koin 4.x KMP-compatible import (not the older `koinViewModel` from `koin-androidx-compose`).
4. **`collectAsStateWithLifecycle()`:** Import from `androidx.lifecycle.compose` — used in `SummaryScreen.kt` and should be used in `MapScreen.kt` for consistent lifecycle-aware collection.
5. **Fakes in `commonTest/fakes/`:** Create test doubles there, not inline in test files. `FakeLocationProvider` and `FakePrivacyPipeline` already exist — reuse them directly.
6. **`DisposableEffect` for Android View lifecycle:** When wrapping Android Views via `AndroidView` that need lifecycle callbacks, use `DisposableEffect(Unit)` in the composable scope (not inside the `AndroidView` factory lambda) to call `onPause`/`onStop`/`onDestroy`.
7. **`UnconfinedTestDispatcher(testScheduler)` in ViewModel tests:** Always set `Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))` before creating the ViewModel in tests. Reset with `Dispatchers.resetMain()` in `@AfterTest`.

### References

- MapLibre 11.11.0 dependency: `build.gradle.kts:58`, `libs.versions.toml:21,86`
- Navigation routes (MapRoute): `commonMain/.../ui/navigation/Routes.kt`
- AppNavigation (MapPlaceholderScreen to replace): `commonMain/.../ui/navigation/AppNavigation.kt:37-39`
- BottomNavBar Map tab (already wired): `commonMain/.../ui/navigation/BottomNavBar.kt:37`
- FakeLocationProvider: `commonTest/.../fakes/FakeLocationProvider.kt` [Source: Story 2.1]
- FakePrivacyPipeline: `commonTest/.../fakes/FakePrivacyPipeline.kt` [Source: Story 2.5]
- UiModule Koin pattern: `commonMain/.../di/UiModule.kt` [Source: Story 2.5]
- expect/actual pattern: `util/ConnectivityMonitor.kt` + platform implementations [Source: Story 2.4]
- MapScreen architecture spec: `_bmad-output/planning-artifacts/architecture.md#Project Structure`
- UX map spec: `_bmad-output/planning-artifacts/ux-design-specification.md` — "Map | Google Maps | Interactive map with POI markers. Three-stop bottom sheet..."
- Epic 3 Story 3.1 ACs: `_bmad-output/planning-artifacts/epics.md:541-557`

## Dev Agent Record

### Agent Model Used

claude-opus-4-6

### Debug Log References

None — clean implementation with no debug issues.

### Completion Notes List

- Implemented `MapUiState` sealed class with `Loading`, `Ready` (includes `pois: List<POI>` for Story 3.2 forward-compatibility), and `LocationFailed` variants.
- Implemented `MapViewModel` with parallel `async` calls to `LocationProvider.getCurrentLocation()` and `PrivacyPipeline.resolveAreaName()`, with `retry()` method.
- Created `expect/actual` `MapComposable` — Android actual wraps MapLibre `MapView` via `AndroidView` with full lifecycle management (`remember`, `rememberUpdatedState`, `DisposableEffect`); iOS actual is a stub placeholder.
- Used MapLibre demo tiles (`https://demotiles.maplibre.org/style.json`) as documented tile provider constant.
- Created `MapScreen` composable with exhaustive `when` over all states: `Loading` (centered spinner), `LocationFailed` (ContentNoteBanner + Retry button), `Ready` (BottomSheetScaffold at 88dp peek height with area name + MapComposable).
- Wired `MapViewModel` into Koin `UiModule.kt`.
- Replaced `MapPlaceholderScreen` with `MapScreen` in `AppNavigation.kt` and deleted the placeholder file.
- All 5 `MapViewModelTest` tests pass: initial loading state, location success → Ready, location failure → LocationFailed, pipeline failure → LocationFailed, retry resets and reloads.
- Full regression suite passes with 0 failures.

## Change Log

- 2026-03-04: Implemented Story 3.1 — MapLibre integration with expect/actual composable wrapper, MapViewModel with parallel location resolution, MapScreen with BottomSheetScaffold, and 5 unit tests.
- 2026-03-04: Address code review R1 findings (0H, 2M, 2L): M1 setStyle moved to factory one-shot, M2 sprint-status.yaml added to File List, L1 removed unused DEFAULT_ZOOM, L2 added onLowMemory lifecycle callback, L4 theme spacing in MapScreen.
- 2026-03-04: Address code review R2 findings (0H, 2M, 1L): M1 cancel sibling deferred on early location failure, M2 retry test now asserts Loading state via suspending fakes, L1 removed stale closure in remember block, L2 theme spacing in Ready sheet content.

### File List

| Action | Path |
|--------|------|
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt` |
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` |
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` |
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt` |
| CREATE | `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt` |
| CREATE | `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt` |
| CREATE | `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt` |
| MODIFY | `_bmad-output/implementation-artifacts/sprint-status.yaml` |
| DELETE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapPlaceholderScreen.kt` |
