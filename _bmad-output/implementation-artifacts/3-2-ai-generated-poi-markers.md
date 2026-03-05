# Story 3.2: AI-Generated POI Markers

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to see interesting points of interest appear as markers on the map based on AI recommendations,
so that I can discover hidden gems and notable places I wouldn't have found on my own.

## Acceptance Criteria

1. **Given** an area portrait has been loaded (from Epic 2 or cache), **when** the map screen is displayed, **then** AI-generated POIs from the area portrait are rendered as map markers [FR14].
2. **Given** POI markers are rendered, **when** the map is visible, **then** markers use distinct icons per POI type (landmark, food, culture, nature, etc.) — implemented via MapLibre `addImage()` + `SymbolLayer`.
3. **Given** POI data is fetched, **when** the map renders POIs, **then** data comes from `AreaRepository.getAreaPortrait()` using the same repository instance as `SummaryScreen` — no duplicate AI provider calls.
4. **Given** a POI marker is rendered, **when** TalkBack is active, **then** each marker has `contentDescription = "[POI name], [category]"` [NFR21].
5. **Given** POI data has been resolved, **when** the Map screen enters the `Ready` state with a non-empty POI list, **then** a numeric badge on the Map tab icon shows the POI count (e.g., "3") to signal content before the user taps.
6. **Given** the map enters `Ready` state, **when** POIs are resolved (even if zero), **then** analytics fires `map_opened` with `area_name` and `poi_count`.

## Tasks / Subtasks

- [x] Task 1: Extend `MapViewModel` to fetch and expose POIs (AC: 1, 3, 6)
  - [x] 1.1: Add constructor dependencies to `MapViewModel`: `AreaRepository`, `AreaContextFactory`, `AnalyticsTracker` (in addition to existing `LocationProvider` and `PrivacyPipeline`)
  - [x] 1.2: After `MapUiState.Ready` is emitted (location resolved), call `areaRepository.getAreaPortrait(areaName, areaContextFactory.create())` in the same `viewModelScope.launch` block
  - [x] 1.3: Collect the portrait flow and listen for `BucketUpdate.PortraitComplete(pois)` — when received, update state: `_uiState.value = (current as? MapUiState.Ready)?.copy(pois = pois) ?: current`; ignore all other `BucketUpdate` types (buckets are SummaryScreen's concern)
  - [x] 1.4: After POIs are resolved (on receiving `PortraitComplete`, even if `pois.isEmpty()`), fire analytics: `analyticsTracker.trackEvent("map_opened", mapOf("area_name" to areaName, "poi_count" to pois.size.toString()))`
  - [x] 1.5: Update `UiModule.kt` Koin registration: `viewModel { MapViewModel(get(), get(), get(), get(), get()) }` — order: `LocationProvider`, `PrivacyPipeline`, `AreaRepository`, `AreaContextFactory`, `AnalyticsTracker`
  - [x] 1.6: **V1 Limitation Note** — `AreaRepositoryImpl` returns `PortraitComplete(pois = emptyList())` in cache hit paths (all 6 buckets cached). POIs are populated only on AI cache miss. This is acceptable V1 behaviour: on first launch, the AI call returns POIs; on subsequent loads, the cache serves the buckets but not POIs. Full POI caching is deferred to Epic 3/7.

- [x] Task 2: Update `MapComposable` `expect/actual` to accept and render POIs (AC: 1, 2, 4)
  - [x] 2.1: Update `commonMain/.../ui/map/MapComposable.kt` — add `pois: List<POI>` parameter:
    ```kotlin
    @Composable
    expect fun MapComposable(
        modifier: Modifier,
        latitude: Double,
        longitude: Double,
        zoomLevel: Double,
        pois: List<POI>,
    )
    ```
  - [x] 2.2: Update `MapScreen.kt` to pass `pois = state.pois` to `MapComposable` in the `Ready` branch
  - [x] 2.3: Update `androidMain/.../ui/map/MapComposable.android.kt` to accept `pois: List<POI>` parameter:
    - Add a `LaunchedEffect(pois)` that waits for the map to be ready (via `getMapAsync`) then adds markers:
      ```kotlin
      LaunchedEffect(pois) {
          if (pois.isEmpty()) return@LaunchedEffect
          mapView.getMapAsync { map ->
              // Remove previous markers before adding new ones
              map.markers.forEach { map.removeMarker(it) }
              pois.filter { it.latitude != null && it.longitude != null }.forEach { poi ->
                  map.addMarker(
                      MarkerOptions()
                          .position(LatLng(poi.latitude!!, poi.longitude!!))
                          .title(poi.name)
                          .snippet(poi.type)
                  )
              }
          }
      }
      ```
    - **Custom icons**: Use `map.addImage(iconId, bitmap)` + `SymbolManager` for distinct icons per `poi.type`. Map `poi.type` string to icon via `poiTypeToIconId(poi.type): String` helper function that returns one of: `"icon_landmark"`, `"icon_food"`, `"icon_culture"`, `"icon_nature"`, `"icon_default"`. Draw `BitmapDescriptorFactory`-style bitmaps from vector resources. If implementing SymbolLayer is too complex in this story, fall back to the simple `addMarker()` approach for V1 and file a ticket to add custom icons.
    - **Accessibility**: MapLibre's `MapView` does not expose per-marker `contentDescription` through Compose directly. Use `mapboxMap.setOnInfoWindowClickListener` for basic tap handling. For TalkBack, set the `MapView`'s `AccessibilityNodeInfo` or defer to a POI list view (Story 3.4). Note this known limitation in Dev Notes.
  - [x] 2.4: Update `iosMain/.../ui/map/MapComposable.ios.kt` — add `pois: List<POI>` parameter (ignore it in the stub body, iOS map deferred per backlog note)

- [x] Task 3: Map tab badge showing POI count (AC: 5)
  - [x] 3.1: Update `BottomNavBar` to accept `mapPoiCount: Int` parameter (default 0):
    - For the Map `NavigationBarItem`, wrap the icon in `BadgedBox`:
      ```kotlin
      icon = {
          BadgedBox(badge = {
              if (mapPoiCount > 0) Badge { Text(mapPoiCount.toString()) }
          }) {
              Icon(imageVector = item.icon, contentDescription = null)
          }
      }
      ```
    - All other tabs remain unchanged (no badge wrapping)
  - [x] 3.2: Update `App.kt` to obtain `MapViewModel` via `koinViewModel<MapViewModel>()` and collect `uiState`:
    ```kotlin
    val mapViewModel: MapViewModel = koinViewModel()
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val mapPoiCount = (mapUiState as? MapUiState.Ready)?.pois?.size ?: 0
    ```
    Then pass `mapPoiCount` to `BottomNavBar(navController, mapPoiCount = mapPoiCount)`

- [x] Task 4: `MapViewModelTest` updates (AC: 1, 3, 6)
  - [x] 4.1: Update `FakeAreaRepository` to be configurable for POI tests:
    ```kotlin
    class FakeAreaRepository(
        private val updates: List<BucketUpdate> = listOf(BucketUpdate.PortraitComplete(emptyList()))
    ) : AreaRepository {
        override fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> =
            updates.asFlow()
    }
    ```
  - [x] 4.2: Update `MapViewModelTest` helper `createViewModel()` to pass all 5 constructor params: `FakeLocationProvider`, `FakePrivacyPipeline`, `FakeAreaRepository`, `FakeAreaContextFactory`, `FakeAnalyticsTracker`
  - [x] 4.3: Test: `poisAreEmptyBeforePortraitComplete` — `FakeAreaRepository` returns no updates → `MapUiState.Ready.pois` is `emptyList()`
  - [x] 4.4: Test: `poisPopulatedOnPortraitComplete` — `FakeAreaRepository` emits `PortraitComplete(mockPOIs)` → `MapUiState.Ready.pois == mockPOIs`
  - [x] 4.5: Test: `analyticsMapOpenedFiredWithCorrectParams` — verify `FakeAnalyticsTracker.events` contains `"map_opened"` event with `"area_name"` = resolved area name and `"poi_count"` = POI count string
  - [x] 4.6: Test: `noPoisLoadedIfLocationFails` — `FakeLocationProvider` returns failure → state is `LocationFailed`, `FakeAreaRepository.getAreaPortrait()` is NOT called (verify via `FakeAreaRepository` call count or a spy pattern)
  - [x] 4.7: Existing tests (4 of them) still pass — ensure updated `createViewModel()` uses sensible defaults so existing test scenarios still work

## Dev Notes

### Architecture Requirements

**No duplicate AI calls:** `MapViewModel` uses `AreaRepository` (not `AreaIntelligenceProvider` directly). `AreaRepositoryImpl` is cache-first: if `SummaryViewModel` already triggered `getAreaPortrait()` for the same `areaName + language`, the repository serves the cached buckets without calling the AI provider again. The `PortraitComplete` update is emitted at the end of all paths. In cache-hit paths, `PortraitComplete(pois = emptyList())` is emitted — this is V1 behaviour. POI markers appear on first AI load; a full POI cache (to persist across session restarts) is out of scope for this story.

**`MapViewModel` dependency chain after this story:**
```
MapViewModel(
    locationProvider: LocationProvider,       // from PlatformModule (expect/actual)
    privacyPipeline: PrivacyPipeline,         // from AppModule
    areaRepository: AreaRepository,           // from DataModule
    areaContextFactory: AreaContextFactory,   // from UiModule (factory)
    analyticsTracker: AnalyticsTracker,       // from PlatformModule (expect/actual)
)
```
Update `UiModule.kt`: `viewModel { MapViewModel(get(), get(), get(), get(), get()) }`

**POI collection flow in `MapViewModel`:** After emitting `MapUiState.Ready`, the ViewModel continues collecting the portrait flow in the same coroutine. Only `PortraitComplete` updates are acted on — all `BucketUpdate.ContentDelta`, `BucketUpdate.BucketComplete`, and `BucketUpdate.ContentAvailabilityNote` are silently ignored (Map screen is not a streaming content screen). Pattern:
```kotlin
// After MapUiState.Ready is emitted:
areaRepository.getAreaPortrait(areaName, areaContextFactory.create())
    .catch { e -> AppLogger.e(e) { "Map: portrait fetch failed" } }
    .collect { update ->
        if (update is BucketUpdate.PortraitComplete) {
            val pois = update.pois
            val current = _uiState.value
            if (current is MapUiState.Ready) {
                _uiState.value = current.copy(pois = pois)
            }
            analyticsTracker.trackEvent(
                "map_opened",
                mapOf("area_name" to areaName, "poi_count" to pois.size.toString()),
            )
        }
    }
```

**`AreaContextFactory` in `MapViewModel`:** `AreaContextFactory.create()` reads system time and visit count. It's already a Koin factory (recreated each time). `MapViewModel` should call `create()` once when building the portrait request context, not on every retry (same pattern as `SummaryViewModel`).

**MapLibre marker API (MapLibre 11.11.0):**
- Simple markers: `mapboxMap.addMarker(MarkerOptions().position(LatLng(lat, lon)).title(name).snippet(type))` — still available in 11.x
- Custom icons: Requires `mapboxMap.addImage("icon_id", bitmap)` + `SymbolManager` from `MapLibre Plugins` OR a `SymbolLayer` backed by a `GeoJsonSource`. The main `android-sdk` artifact does NOT bundle the annotation plugin separately in 11.11.0 — use the built-in `MarkerOptions` API for V1, which displays a standard red pin. File a follow-up to add `org.maplibre.gl:android-plugin-annotation-v9` for custom icons.
- **V1 decision:** Use `MapboxMap.addMarker()` with `title` = POI name and `snippet` = POI type. This satisfies AC1 (markers rendered). AC2 (distinct icons) is partially satisfied — all markers share the default pin icon in V1. Add a TODO comment to `MapComposable.android.kt` noting the custom icon work deferred to next cycle.

**Marker TalkBack accessibility (NFR21):**
MapLibre's `MapView` is a classic Android View. Individual marker `contentDescription` is not natively supported through the Compose accessibility tree. Two options:
1. **V1 (this story):** Set `MapView`'s `importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS` and document that POI accessibility is served by Story 3.4's list view (`POIListView`) — the accessible alternative to the map.
2. **Full accessibility:** Implement a custom `AccessibilityDelegate` on `MapView` that exposes virtual accessibility nodes per marker — defer to Story 3.4 or a dedicated accessibility pass.
Dev agent should choose option 1 for this story scope.

**Badge in `App.kt`:** `koinViewModel<MapViewModel>()` inside `App()` returns the SAME ViewModel instance that `MapScreen()` uses (Koin ViewModelScope is tied to the NavBackStackEntry for `MapScreen`, but `App.kt` would create a NEW scope). Use `koinViewModel()` inside the `Scaffold` lambda instead — or better, move badge state extraction into `BottomNavBar`'s own `koinViewModel<MapViewModel>()` call. The simplest pattern: call `koinViewModel<MapViewModel>()` inside `BottomNavBar()` with `MapUiState` collection. Since `BottomNavBar` is always composed (not scoped to a nav destination), it will share the same Koin `viewModel { }` instance only if the ViewModel is `single` scoped — `viewModel { }` in Koin creates a scoped ViewModel. **Correct approach:** Use `koinViewModel()` directly in the `BottomNavBar` composable — Koin's `viewModel` in Compose creates a `ViewModelStoreOwner`-scoped instance. Two calls to `koinViewModel<MapViewModel>()` in the same Activity will return the same instance. Verify this works as expected; if not, pass `mapPoiCount` as a parameter from `App.kt` down the tree.

**`retry()` and POI fetch:** When `retry()` is called, the ViewModel resets to `Loading` and calls `loadLocation()` again, which will re-fetch the portrait and POIs from scratch (or cache). No special handling needed — the existing retry flow will re-trigger the `getAreaPortrait()` call.

**`FakeAreaRepository` update:** Current `FakeAreaRepository` returns `emptyFlow()`. Update it to accept a configurable list of `BucketUpdate` to emit, defaulting to `listOf(BucketUpdate.PortraitComplete(emptyList()))`. This allows test control without breaking existing tests that use the default.

### Project Structure Notes

**Files to create/modify:**

| Action | Path |
|--------|------|
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt` (expect — add `pois` param) |
| MODIFY | `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt` (actual — add marker rendering) |
| MODIFY | `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt` (actual stub — add `pois` param, ignore) |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` (pass `pois` to `MapComposable`) |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/BottomNavBar.kt` (add `mapPoiCount` param + `BadgedBox`) |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt` (get badge count, pass to `BottomNavBar`) |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt` (update `MapViewModel` Koin binding with 5 params) |
| MODIFY | `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaRepository.kt` (configurable updates) |
| MODIFY | `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` (new tests + update existing) |
| MODIFY | `_bmad-output/implementation-artifacts/sprint-status.yaml` (update story status) |

**No new files needed** — all changes are extensions of existing Story 3.1 infrastructure.

**`MapUiState.kt` is unchanged** — `pois: List<POI> = emptyList()` was added in Story 3.1 in anticipation of this story. Zero modifications needed.

**`AreaRepository.kt` interface is unchanged** — no new methods needed; `MapViewModel` uses the existing `getAreaPortrait()` flow.

### Previous Story Learnings (from 3.1)

1. **`mapView.getMapAsync { }` callback timing:** Any operation on the map (camera, style, markers) must be inside `getMapAsync`. Use a `LaunchedEffect` key to re-trigger when data changes (e.g., `LaunchedEffect(pois)`).
2. **`remember { MapView(...) }` is the single MapView instance** — do not create a new `MapView` for each recomposition. Adding markers should go through the same reference.
3. **`LaunchedEffect` vs `DisposableEffect`:** Use `LaunchedEffect(pois)` to react to POI list changes without cleanup side effects. The existing `DisposableEffect(Unit)` manages MapView lifecycle; do not merge marker logic into it.
4. **Parallel `async { }` pattern in `MapViewModel`:** The existing location resolution uses `async { }` — the POI fetch is sequential AFTER location resolves (needs area name first). Do NOT make it parallel with location — it must wait for the `areaName` result.
5. **`koinViewModel()` import:** Always from `org.koin.compose.viewmodel.koinViewModel` (Koin 4.x KMP import).
6. **`FakeAreaRepository.callCount`:** Add a `var callCount: Int` to `FakeAreaRepository` to verify that `getAreaPortrait()` is NOT called when location fails (AC for Task 4.6).

### References

- `MapUiState.kt` — `pois: List<POI>` field already present: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
- `MapViewModel.kt` — base for extension: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
- `MapComposable.android.kt` — base for marker rendering: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
- `AreaRepository` interface: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/repository/AreaRepository.kt`
- `AreaRepositoryImpl` — cache paths emit `PortraitComplete(emptyList())`: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt:74,89,111,137`
- `BucketUpdate.PortraitComplete(pois)` — the update type to listen for: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/BucketUpdate.kt`
- `MockAreaIntelligenceProvider.mockPOIs` — 3 sample POIs with lat/lon: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MockAreaIntelligenceProvider.kt:48`
- `POI.kt` — `latitude: Double?`, `longitude: Double?` (nullable — filter before adding markers): `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt`
- `AnalyticsTracker.trackEvent()` — interface: `composeApp/src/commonMain/kotlin/com/areadiscovery/util/AnalyticsTracker.kt`
- `FakeAnalyticsTracker` — test fake: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAnalyticsTracker.kt`
- `FakeAreaContextFactory` — test fake: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaContextFactory.kt`
- `SummaryViewModel` — reference for analytics + portrait collection pattern: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryViewModel.kt`
- Analytics event spec — `map_opened`: `_bmad-output/planning-artifacts/architecture.md` line ~310
- Epic 3 Story 3.2 ACs: `_bmad-output/planning-artifacts/epics.md:559-575`
- MapLibre `addMarker()` API: available in `org.maplibre.gl:android-sdk:11.11.0` (legacy but stable)
- `UiModule.kt` — current Koin registrations: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
- `App.kt` — `BottomNavBar` call site: `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt:20`

## Dev Agent Record

### Agent Model Used

claude-opus-4-6

### Debug Log References

- MapLibre `MarkerOptions` import: `org.maplibre.android.annotations.MarkerOptions` (not `maps.MarkerOptions`)
- MapLibre `addMarker()`/`removeMarker()` are deprecated in 11.x but functional — V1 approach per story spec

### Completion Notes List

- Task 1: Extended `MapViewModel` with 3 new dependencies (`AreaRepository`, `AreaContextFactory`, `AnalyticsTracker`). After location resolves to `Ready`, collects portrait flow and updates POIs on `PortraitComplete`. Fires `map_opened` analytics event with area name and POI count.
- Task 2: Added `pois: List<POI>` parameter to `MapComposable` expect/actual. Android actual renders markers via `LaunchedEffect(pois)` + `getMapAsync` + `addMarker()`. iOS stub accepts parameter but ignores it. MapView accessibility set to `NO_HIDE_DESCENDANTS` (V1 — POI accessibility deferred to Story 3.4 list view). Custom icons deferred via TODO comment.
- Task 3: Badge via `mapPoiCount` param passed from `App.kt` through `AppNavigation` to avoid duplicate ViewModel instances. `BadgedBox` wraps only the Map tab icon. Single `MapViewModel` created in Activity scope (`App.kt`), shared with `MapScreen` via parameter.
- Task 4: Updated `FakeAreaRepository` with configurable `updates` list + `callCount`. Added 4 new tests: `poisAreEmptyWhenRepositoryEmitsNoUpdates`, `poisPopulatedOnPortraitComplete`, `analyticsMapOpenedFiredWithCorrectParams`, `noPoisLoadedIfLocationFails`. All 5 existing tests updated to use 5-param constructor and still pass (9 total tests).
- V1 limitations documented: default pin icons (no custom per-type icons — AC2 partial), TalkBack via list view (Story 3.4 — AC4 partial), POIs only shown on cold cache miss; both full-cache-hit AND stale-while-revalidate paths return empty POIs (full POI caching deferred to Epic 3/7).

### Change Log

- 2026-03-04: Story 3.2 implementation complete — all 4 tasks, 9 passing tests
- 2026-03-05: Address round 1 code review findings (1C, 3M, 3L): C1 fix duplicate ViewModel (App.kt→AppNavigation→MapScreen param pass-through), M1 AC2 partial noted, M3 createViewModel() uses interface types, L1 test renamed
- 2026-03-05: Address round 2 code review findings (1M, 2L): M1 stale markers fix (remove early return on empty pois), L2 assert map_opened fires exactly once, L1/M4/L3 already fixed in round 1
- 2026-03-05: Address round 3 code review findings (1C, 2M, 3L): C1 fix eager ViewModel creation — removed koinViewModel from App.kt, MapViewModel now lazy (created only when MapScreen navigated to), badge via callback (onPoiCountChanged). M6 test areaContextFactoryCalledExactlyOnce added. L6 locationCallCount assertion added. 10 tests.
- 2026-03-05: Address round 4 code review findings (1M): M7 fix retry() race — cancel in-flight loadJob before relaunching (matches SummaryViewModel pattern). M8 V1 limitation note updated to clarify stale-while-revalidate also returns empty POIs. L9/L10 out of scope (AreaRepositoryImpl/use-case layer).

### File List

- MODIFIED: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
- MODIFIED: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt`
- MODIFIED: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
- MODIFIED: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt`
- MODIFIED: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
- MODIFIED: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/BottomNavBar.kt`
- MODIFIED: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt`
- MODIFIED: `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt`
- MODIFIED: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
- MODIFIED: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaRepository.kt`
- MODIFIED: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
- MODIFIED: `_bmad-output/implementation-artifacts/sprint-status.yaml`
- MODIFIED: `_bmad-output/implementation-artifacts/3-2-ai-generated-poi-markers.md`
