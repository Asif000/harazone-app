# Story 3.3: POI Detail Card & Bottom Sheet

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to tap a POI marker and see its history, significance, and tips in a detail card,
so that I can learn about interesting places without leaving the map.

## Acceptance Criteria

1. **Given** POI markers are displayed on the map, **when** the user taps a marker, **then** the bottom sheet slides up showing the POI name and category in the peek, and the full `POIDetailCard` in the expanded view [FR15].
2. **Given** a POI is selected, **when** the bottom sheet is visible, **then** `POIDetailCard` displays: POI name (titleMedium), POI type/category (bodyMedium warm gray), AI-generated description (bodyLarge), a `ConfidenceTierBadge`, and an action row with Save, Share, and Navigate `IconButton`s.
3. **Given** a POI is selected, **when** the detail card is shown, **then** Save and Share buttons are visible but show a "Coming soon" Snackbar on tap — wired actions deferred to Epic 6 (bookmark) and Epic 9 (share). Navigate button opens the system maps intent with the POI's lat/lon.
4. **Given** the bottom sheet is expanded with a POI, **when** the user taps elsewhere on the map (blank area, not a marker), **then** the selected POI is cleared and the sheet collapses to the area teaser peek.
5. **Given** no POI is selected, **when** the sheet is at peek height, **then** the collapsed content shows the area name (headlineSmall) and "Explore this area" teaser (bodyMedium).
6. **Given** a POI is selected, **when** the sheet is at peek height (dragged down from expanded), **then** the collapsed content shows the POI name + category as a summary.
7. **Given** the detail card is visible, **when** the user drags the sheet down to dismiss, **then** `selectedPoi` is cleared in the ViewModel.
8. **Given** TalkBack is active, **when** the user navigates the detail card, **then** content reads top-to-bottom: POI name → type → description → confidence → actions. All `IconButton`s have `contentDescription`. The card uses `semantics(mergeDescendants = false)` to allow individual element focus.
9. **Given** a POI marker is tapped, **when** the user is authenticated (all cases), **then** analytics fires `poi_tapped` with `area_name`, `poi_name`, `poi_type`.

## Tasks / Subtasks

- [x] Task 1: Add `selectedPoi` to `MapUiState` and `selectPoi()` to `MapViewModel` (AC: 1, 4, 7, 9)
  - [x]1.1: Add `selectedPoi: POI? = null` field to `MapUiState.Ready` data class (alongside existing `pois`, `areaName`, `latitude`, `longitude`).
  - [x]1.2: Add `fun selectPoi(poi: POI?)` to `MapViewModel`:
    ```kotlin
    fun selectPoi(poi: POI?) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(selectedPoi = poi)
        if (poi != null) {
            val areaName = current.areaName
            analyticsTracker.trackEvent(
                "poi_tapped",
                mapOf(
                    "area_name" to areaName,
                    "poi_name" to poi.name,
                    "poi_type" to poi.type,
                )
            )
        }
    }
    ```
  - [x]1.3: No change to `UiModule.kt` or Koin — `MapViewModel` constructor is unchanged.

- [x] Task 2: Create `POIDetailCard` composable (AC: 2, 3, 8)
  - [x]2.1: Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/POIDetailCard.kt`:
    ```kotlin
    @Composable
    fun POIDetailCard(
        poi: POI,
        onSaveClick: () -> Unit,
        onShareClick: () -> Unit,
        onNavigateClick: () -> Unit,
        modifier: Modifier = Modifier,
    )
    ```
  - [x]2.2: Card anatomy (per UX spec):
    ```
    ┌───────────────────────────────────────────┐
    │  📍 [poi.name]                            │  ← titleMedium
    │  [poi.type] (capitalised, warm gray)      │  ← bodyMedium, onSurfaceVariant
    │                                           │
    │  [poi.description]                        │  ← bodyLarge
    │                                           │
    │  [🔖 Save]  [📤 Share]  [🧭 Navigate]    │  ← Row of IconButtons, 48dp targets
    │  ✓ [ConfidenceTierBadge]                  │
    └───────────────────────────────────────────┘
    ```
    - Use M3 `Card` with `MaterialTheme.colorScheme.surfaceVariant` surface and `1.dp` elevation (beige card per theme)
    - 16dp horizontal + 16dp vertical padding inside the card (use `MaterialTheme.spacing.md`)
    - `Spacer(Modifier.height(spacing.sm))` between elements
  - [x]2.3: Action row — three `IconButton`s with 48dp touch targets:
    - **Save**: `Icons.Outlined.BookmarkBorder`, `contentDescription = "Save ${poi.name}"`, `onClick = onSaveClick`
    - **Share**: `Icons.Outlined.Share`, `contentDescription = "Share ${poi.name}"`, `onClick = onShareClick`
    - **Navigate**: `Icons.Outlined.Navigation`, `contentDescription = "Navigate to ${poi.name}"`, `onClick = onNavigateClick`
    - Use `Icons.Default.*` if Outlined variants not available in the existing icon import set — check what's already used in other composables before adding new imports
  - [x]2.4: Navigate action — use `expect/actual` for platform maps intent OR a simple URL intent. For Android:
    ```kotlin
    // In MapScreen's onNavigateClick lambda (platform-specific, not in the composable):
    val uri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(poi.name)}")
    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(mapIntent)
    ```
    For KMP portability: define `openMapsNavigation(lat: Double, lon: Double, name: String)` as an `expect fun` in `commonMain/kotlin/com/areadiscovery/ui/map/MapNavigation.kt`, with `actual` in androidMain. iOS stub is no-op. **Alternative (simpler V1):** Pass `onNavigateClick` as a lambda from `MapScreen.kt` (Android-only logic) so `POIDetailCard` stays pure Compose without platform code. Prefer the lambda approach to avoid new expect/actual files.
  - [x]2.5: Accessibility:
    - The outer `Card` should NOT set `semantics(mergeDescendants = true)` — individual elements (name, description, actions, badge) should each be focusable separately for TalkBack
    - POI name: no extra annotation needed (default Text semantics)
    - Description text: no extra annotation needed
    - `ConfidenceTierBadge` already has `contentDescription = "Confidence level: $label"` — reuse as-is
  - [x]2.6: Add `@Preview` for loaded state with sample data

- [x] Task 3: Update `MapComposable` (expect + actuals) to add `onPoiSelected` (AC: 1, 4)
  - [x]3.1: Update `commonMain/.../ui/map/MapComposable.kt` — add `onPoiSelected: (POI?) -> Unit` parameter:
    ```kotlin
    @Composable
    expect fun MapComposable(
        modifier: Modifier,
        latitude: Double,
        longitude: Double,
        zoomLevel: Double,
        pois: List<POI>,
        onPoiSelected: (POI?) -> Unit,
    )
    ```
  - [x]3.2: Update `androidMain/.../ui/map/MapComposable.android.kt`:
    - Add `onPoiSelected: (POI?) -> Unit` parameter
    - Add `val poiMarkerMap = remember { mutableMapOf<Marker, POI>() }` alongside existing `poiMarkers`
    - In both marker-rendering paths (setStyle callback flush + `LaunchedEffect(pois)` path), populate `poiMarkerMap[marker] = poi` for each added marker, and call `poiMarkerMap.clear()` when clearing markers
    - Set marker click listener once, in the `getMapAsync` block after style loads. Pattern:
      ```kotlin
      // Inside setStyle { _ -> } callback, after moving camera:
      map.setOnMarkerClickListener { marker ->
          onPoiSelected(poiMarkerMap[marker])
          true // consume the click (prevents default info window)
      }
      map.setOnMapClickListener {
          onPoiSelected(null) // deselect on blank tap
      }
      ```
    - **Important**: Set these listeners only once — inside the `setStyle` callback is the right place since the map is fully ready
    - **Important**: `poiMarkerMap` must be captured in the `remember` block, same as `poiMarkers` — both are stable references across recompositions
  - [x]3.3: Update `iosMain/.../ui/map/MapComposable.ios.kt` — add `onPoiSelected: (POI?) -> Unit` parameter (ignore it in body, same as `pois`).
  - [x]3.4: Update `MapScreen.kt` call site to pass `onPoiSelected = { poi -> viewModel.selectPoi(poi) }`.

- [x] Task 4: Update `MapScreen.kt` bottom sheet and integrate `POIDetailCard` (AC: 1, 2, 3, 4, 5, 6, 7)
  - [x]4.1: In the `MapUiState.Ready` branch, use `state.selectedPoi` to determine bottom sheet content.
  - [x]4.2: Add `val snackbarHostState = remember { SnackbarHostState() }` and `val coroutineScope = rememberCoroutineScope()` for stub action feedback. Wrap `BottomSheetScaffold` with `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })` — or hoist snackbar to parent; whichever is simpler. **Simplest approach**: add `SnackbarHost` directly inside the `BottomSheetScaffold` — actually `BottomSheetScaffold` doesn't have a `snackbarHost` slot. Use `Box` wrapper with `SnackbarHost` overlaid at the bottom.
  - [x]4.3: Bottom sheet `sheetContent` logic:
    ```kotlin
    sheetContent = {
        if (state.selectedPoi != null) {
            POIDetailCard(
                poi = state.selectedPoi,
                onSaveClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Bookmarks coming soon")
                    }
                },
                onShareClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Sharing coming soon")
                    }
                },
                onNavigateClick = {
                    // Android-specific navigation launch
                    state.selectedPoi.latitude?.let { lat ->
                        state.selectedPoi.longitude?.let { lon ->
                            val uri = android.net.Uri.parse(
                                "geo:$lat,$lon?q=${android.net.Uri.encode(state.selectedPoi.name)}"
                            )
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            )
        } else {
            // Default area teaser (existing content)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            ) {
                Text(text = state.areaName, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                Text(
                    text = if (state.pois.isNotEmpty()) "${state.pois.size} places to explore" else "Explore this area",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    ```
    **Note on platform-specific intent**: Since `MapScreen.kt` lives in `commonMain` and `android.net.Uri`/`android.content.Intent` are Android-only, the navigate lambda should be declared as a parameter or use `expect/actual`. **Preferred approach**: Add `onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Unit` as a parameter to `MapScreen` (defaulting to `{}`). Wire it from `AppNavigation.kt` on Android. This keeps `MapScreen.kt` fully in `commonMain` without Android imports.
  - [x]4.4: Auto-expand sheet on POI selection:
    ```kotlin
    LaunchedEffect(state.selectedPoi) {
        if (state.selectedPoi != null) {
            scaffoldState.bottomSheetState.expand()
        } else {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }
    ```
  - [x]4.5: Wire sheet collapse → deselect POI (so dragging sheet down clears the selection):
    ```kotlin
    val sheetValue = scaffoldState.bottomSheetState.currentValue
    LaunchedEffect(sheetValue) {
        if (sheetValue == SheetValue.PartiallyExpanded && state.selectedPoi != null) {
            viewModel.selectPoi(null)
        }
    }
    ```
    Import: `androidx.compose.material3.SheetValue`
  - [x]4.6: Update `MapScreen` signature to accept `onNavigateToMaps`:
    ```kotlin
    @Composable
    fun MapScreen(
        viewModel: MapViewModel = koinViewModel(),
        onPoiCountChanged: (Int) -> Unit = {},
        onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Unit = {},
    )
    ```
  - [x]4.7: Update `AppNavigation.kt` to pass `onNavigateToMaps`. Since AppNavigation is in commonMain, the Maps intent must be wired via a lambda passed from `MainActivity` (Android-only). Option: add `onNavigateToMaps` parameter to `AppNavigation` and pass it down to `MapScreen`. The actual `Intent` construction lives in `MainActivity.kt` (androidMain):
    ```kotlin
    // In MainActivity.kt or wherever App() is called:
    // Pass lambda: { lat, lon, name -> startActivity(...) }
    ```
    **Simplest V1**: Accept that Navigate is Android-only. Pass `onNavigateToMaps` all the way from `App()` → `AppNavigation` → `MapScreen`. Update `App.kt`, `AppNavigation.kt`, and `MapScreen.kt` signatures accordingly. If this creates too many signature changes, fall back to `LocalContext.current` + `context.startActivity()` in the `onNavigateClick` lambda inside `MapScreen` (works fine for androidMain compose context). **Recommended V1**: Use `LocalContext.current` in the `onNavigateClick` lambda in `MapScreen.kt`, guarded by `expect/actual` or a try-catch. But `LocalContext` is Android-specific in compose multiplatform. Check if it's available in commonMain. Actually in KMP Compose, `LocalContext` is Android-specific and not available in `commonMain`. **Final decision**: Declare `onNavigateToMaps` parameter on `MapScreen` with a no-op default. Wire from `AppNavigation`. The actual `Intent` lives in `AppNavigation.kt` (but that's also commonMain). Alternative: create `expect fun openMapsIntent(lat: Double, lon: Double, name: String)` in `MapScreen.kt` or a separate platform util. **Cleanest approach for this story**: Wire as a parameter passed from the platform entry point, update `App.kt` → `AppNavigation.kt` → `MapScreen.kt` chain. This is already the established pattern (e.g. `onPoiCountChanged`).

- [x] Task 5: Tests for `MapViewModel` `selectPoi()` (AC: 9)
  - [x]5.1: `selectPoiUpdatesSelectedPoiInReadyState` — create ViewModel in Ready state (via `FakeLocationProvider` success + `FakeAreaRepository` with `PortraitComplete`), call `viewModel.selectPoi(samplePoi)`, assert `(viewModel.uiState.value as MapUiState.Ready).selectedPoi == samplePoi`
  - [x]5.2: `selectPoiFiresPoiTappedAnalytics` — verify `analyticsTracker.recordedEvents` contains `"poi_tapped"` with correct `area_name`, `poi_name`, `poi_type`
  - [x]5.3: `selectPoiNullClearsSelectionWithoutAnalytics` — call `selectPoi(poi)`, then `selectPoi(null)`, assert `selectedPoi == null` and `poi_tapped` fired exactly once (not again for the null clear)
  - [x]5.4: `selectPoiNoOpBeforeReadyState` — call `selectPoi(poi)` when ViewModel is in `Loading` state (use `ResettableFakeLocationProvider`), assert no crash and state is still `Loading`
  - [x]5.5: All 9 existing tests remain passing with the new `selectedPoi` field (default `null` so no existing assertions break)

## Dev Notes

### Architecture Requirements

**`MapUiState.Ready` extension**: Adding `selectedPoi: POI? = null` with a default means all existing `createViewModel()` / `copy(...)` calls in tests compile without changes. The field is in `commonMain` — no platform code in the model.

**`MapViewModel.selectPoi()` is idempotent for null**: Calling `selectPoi(null)` multiple times is safe and fires no analytics. Calling `selectPoi(poi)` fires `poi_tapped` once per call — callers should not call it in tight loops.

**BottomSheetScaffold — two stops (V1 decision)**:
M3 `BottomSheetScaffold` supports two states:
- `SheetValue.PartiallyExpanded` — shows up to `sheetPeekHeight` (88dp)
- `SheetValue.Expanded` — full content

The UX spec describes a "three-stop" (collapsed / half / full). V1 maps this to two stops:
- **Collapsed (88dp peek)**: POI name + category summary, or area teaser
- **Expanded**: Full `POIDetailCard`

True three-stop (half-sheet at ~40% screen height) requires `anchoredDraggable` with custom snap points — defer to a future UX polish story. Note in Dev Agent Record.

**`setOnMarkerClickListener` must return `true`**: This prevents MapLibre from showing the default info window popup over the marker. `true` = event consumed, `false` = MapLibre shows default behaviour.

**`setOnMapClickListener` dismissal**: MapLibre's `MapboxMap.setOnMapClickListener` fires on tap of any empty map area. If the user taps a marker, `setOnMarkerClickListener` fires first and the map click does NOT fire (marker click is consumed with `true` return). So there's no double-fire to worry about.

**poiMarkerMap memory management**: `poiMarkerMap` is a `remember`ed `mutableMapOf<Marker, POI>()`. It must be cleared in `onDispose` alongside `poiMarkers.clear()`. Pattern:
```kotlin
// In onDispose:
isDestroyed[0] = true
poiMarkers.clear()
poiMarkerMap.clear()  // ADD THIS
```

**Sheet collapse detection**: `scaffoldState.bottomSheetState.currentValue` updates after animation completes. `targetValue` updates immediately when user starts dragging. Using `currentValue` (not `targetValue`) for clearing selection avoids clearing too early during the drag. This is intentional.

**Android maps intent guard**: Navigate only works if `poi.latitude != null && poi.longitude != null`. The `POI` model has nullable lat/lon. Always check before constructing the geo URI. If null, show a Snackbar "Location not available for this place".

**`SnackbarHost` placement**: `BottomSheetScaffold` doesn't have a built-in snackbar slot. Wrap the entire `BottomSheetScaffold` content in a `Box` with `SnackbarHost` pinned to the bottom:
```kotlin
Box(Modifier.fillMaxSize()) {
    BottomSheetScaffold(...) { ... }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}
```

**`coroutineScope` in `MapScreen`**: Use `rememberCoroutineScope()` (not `viewModelScope`) for snackbar launches — snackbars are UI concerns tied to the composable lifecycle.

### Project Structure Notes

**Files to create:**

| Action | Path |
|--------|------|
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/POIDetailCard.kt` |

**Files to modify:**

| Action | Path |
|--------|------|
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt` (expect — add `onPoiSelected`) |
| MODIFY | `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt` (actual — add tap wiring) |
| MODIFY | `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt` (actual stub — add param) |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` (POIDetailCard + sheet logic) |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt` (pass `onNavigateToMaps`) |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt` (pass `onNavigateToMaps` lambda) |
| MODIFY | `composeApp/src/androidMain/kotlin/com/areadiscovery/MainActivity.kt` (construct maps Intent, pass to App) |
| MODIFY | `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` (5 new tests) |
| MODIFY | `_bmad-output/implementation-artifacts/sprint-status.yaml` |

**`UiModule.kt` is UNCHANGED** — `MapViewModel` constructor has no new parameters.

**`AreaRepository` is UNCHANGED** — no new data fetching; POIs are already in `MapUiState.Ready.pois`.

**`POI.kt` is UNCHANGED** — model already has all needed fields.

**`ConfidenceTierBadge.kt` is UNCHANGED** — reuse as-is.

### Previous Story Learnings (from 3.1 + 3.2)

1. **`getMapAsync` timing**: All map operations must be inside `getMapAsync`. The click listeners should be set in the same `setStyle` callback where the camera is moved and the style is confirmed loaded.
2. **`remember { mutableListOf<Marker>() }` pattern**: The `poiMarkers` list is stable across recompositions. Add `poiMarkerMap` the same way — `val poiMarkerMap = remember { mutableMapOf<Marker, POI>() }`.
3. **`LaunchedEffect` vs `DisposableEffect`**: `LaunchedEffect(state.selectedPoi)` for sheet expand/collapse logic. `DisposableEffect(lifecycleOwner)` for MapView lifecycle. Do not mix them.
4. **`koinViewModel()` import**: Always `org.koin.compose.viewmodel.koinViewModel` (Koin 4.x KMP).
5. **Koin ViewModel lazy init**: `MapViewModel` is created lazily when `MapScreen` is first composed (established in Story 3.2). No changes to App.kt's ViewModel scoping.
6. **`poiMarkers.clear()` in `onDispose`**: Already set. Add `poiMarkerMap.clear()` in the same `onDispose` block.
7. **`isDestroyed[0]` guard**: Check `isDestroyed[0]` at the start of every `getMapAsync` callback. The story 3.2 camera and poi `getMapAsync` callbacks already have this. Extend the pattern to the click listener setup callback — if `isDestroyed[0]` is true before the style loads, skip setting up click listeners.
8. **`SheetValue` import**: `androidx.compose.material3.SheetValue` — needed for collapse detection in `LaunchedEffect(sheetValue)`.
9. **`@OptIn(ExperimentalMaterial3Api::class)`**: Already on `MapScreen` — keep it, `BottomSheetScaffold` and `SheetValue` are still experimental in M3.
10. **Device bugs now fixed (commit 437ed06)**: Camera zoom correctly centres on user. POIs now persist in `area_poi_cache` SQLDelight table and are restored on cache hit. Tile provider is Maptiler `streets-v2`. These fixes mean Story 3.3 can rely on POIs being present in `MapUiState.Ready.pois` from the cache on repeat visits.

### Backlog Items to Absorb (from Story 3.2 deferred list)

- **Custom POI icons** (LOW, Story 3.2 backlog, deferred to "Story 3.3 or dedicated icon pass"): Still deferred. This story does not add `org.maplibre.gl:android-plugin-annotation-v9`. All markers remain default red pins. Document in Dev Agent Record.
- **Deprecated `addMarker`/`removeMarker` API** (INFO, Story 3.2 backlog): Accepted for V1 — migration happens when custom icons are added. Note in Dev Agent Record.

### Git Intelligence (last 5 commits)

1. **437ed06** — Fixed 3 device bugs: camera zoom (moveCamera inside setStyle), POI cache-hit (added `area_poi_cache` SQLDelight table + migration 2, persist/restore POIs), tile provider (Maptiler streets-v2 via `MAPTILER_API_KEY`). Also added `@Serializable` to `POI` and `Confidence`.
2. **13de0ea** — Added device-testing bugs to `memory/backlog.md` (before the fixes).
3. **fd10066** — Marked Story 3.2 done after 11-round code review.
4. **aed6c9f** — Round 11 review fixes: permission check guard, camera `isDestroyed` guard, Crashlytics debug flag, `GrantPermissionRule` in smoke test, rename `GPS_TIMEOUT_MS`.
5. **374f0f3** — Round 10 fixes.

**Key insights from 437ed06**:
- `area_poi_cache` table now exists in the SQLDelight schema (migration 2). POIs are persisted as JSON.
- `Confidence` enum is now `@Serializable` — needed for `json.encodeToString(pois)`.
- The `styleLoaded` + `styleLoading` + `pendingPois` pattern in `MapComposable.android.kt` handles the race between POI arrival and style load. Story 3.3 click listener setup must use the same `styleLoaded` guard.
- `MAPTILER_API_KEY` is in `BuildKonfig` — no change needed here.

### References

- `MapUiState.kt` — base for `selectedPoi` addition: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
- `MapViewModel.kt` — add `selectPoi()` here: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
- `MapScreen.kt` — current BottomSheetScaffold structure: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
- `MapComposable.android.kt` — add click listener wiring (setOnMarkerClickListener, setOnMapClickListener): `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
- `POI.kt` — data model (name, type, description, confidence, latitude?, longitude?): `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt`
- `Confidence.kt` — enum for ConfidenceTierBadge: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Confidence.kt`
- `ConfidenceTierBadge.kt` — existing composable to reuse: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ConfidenceTierBadge.kt`
- `BucketSectionHeader.kt` / `BucketCard.kt` — reference for M3 Card + spacing patterns: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/`
- `App.kt` — ViewModel scope and callback wiring pattern (e.g. `onMapPoiCountChanged`): `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt`
- `AppNavigation.kt` — composable wiring, parameter pass-through: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt`
- `FakeAreaRepository.kt` — configurable test fake: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaRepository.kt`
- `FakeAnalyticsTracker.kt` — for analytics assertions: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAnalyticsTracker.kt`
- `MapViewModelTest.kt` — existing tests to keep green: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
- UX spec — POIDetailCard anatomy (section 7): `_bmad-output/planning-artifacts/ux-design-specification.md:989-1020`
- UX spec — bottom sheet pattern: `_bmad-output/planning-artifacts/ux-design-specification.md:798,1184`
- Architecture — `poi_tapped` analytics event: `_bmad-output/planning-artifacts/architecture.md:311`
- Architecture — Map screen component locations: `_bmad-output/planning-artifacts/architecture.md:634-636`
- Epic 3 Story 3.3 ACs: `_bmad-output/planning-artifacts/epics.md:576-592`
- `area_poi_cache.sq` — new table from device-bug fix: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_poi_cache.sq`

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- MapLibre uses `addOnMapClickListener` (returns boolean), not `setOnMapClickListener`
- Default lambda `= {}` doesn't match 3-param function types — must use `= { _, _, _ -> }`
- `Icons.Outlined.BookmarkBorder/Share/Navigation` available via `compose-material-icons-extended` (already in deps)

### Completion Notes List

- Task 1: Added `selectedPoi: POI? = null` to `MapUiState.Ready`, `selectPoi(poi)` to `MapViewModel` with `poi_tapped` analytics
- Task 2: Created `POIDetailCard.kt` — M3 Card with name, type, description, action row (Save/Share/Navigate IconButtons), ConfidenceTierBadge. Accessibility: individual element focus (no mergeDescendants on card)
- Task 3: Added `onPoiSelected: (POI?) -> Unit` to expect/actual `MapComposable`. Android: `setOnMarkerClickListener` + `addOnMapClickListener`, `poiMarkerMap` for Marker→POI lookup. iOS: param added, ignored
- Task 4: Rewired `MapScreen` bottom sheet: POI selected → `POIDetailCard` in sheet content; no POI → area teaser with poi count. Sheet auto-expands on select, collapse clears selection. SnackbarHost for stub actions. `onNavigateToMaps` lambda threaded from `MainActivity` → `App` → `AppNavigation` → `MapScreen` for Android maps intent
- Task 5: 4 new tests (selectPoi updates state, fires analytics, null clears without analytics, no-op before Ready). All 16 MapViewModelTest tests pass
- V1 decisions: Two-stop bottom sheet (peek/expanded), custom POI icons still deferred, deprecated addMarker API accepted
- Deferred: Custom POI icons (backlog), three-stop sheet (future UX polish)

### Change Log

- 2026-03-05: Story 3.3 implementation — POI detail card, bottom sheet integration, marker tap handling, navigate intent, 4 new tests
- 2026-03-05: Code review (3 rounds) — fixed: ActivityNotFoundException guard (resolveActivity + Boolean return chain), @Preview for POIDetailCard, rememberUpdatedState for onPoiSelected lambda, SnackbarHost peek padding, SHEET_PEEK_HEIGHT constant, addOnMapClickListener comment, <queries> manifest declaration for geo: intent, @Preview import order. Deferred: resolveActivity API 33 deprecation (lint only), scaffold state reset on retry (V1 accepted).

### File List

- CREATE: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/POIDetailCard.kt`
- MODIFY: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
- MODIFY: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
- MODIFY: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt`
- MODIFY: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
- MODIFY: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt`
- MODIFY: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
- MODIFY: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt`
- MODIFY: `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt`
- MODIFY: `composeApp/src/androidMain/kotlin/com/areadiscovery/MainActivity.kt`
- MODIFY: `composeApp/src/androidMain/AndroidManifest.xml`
- MODIFY: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
- MODIFY: `_bmad-output/implementation-artifacts/sprint-status.yaml`
