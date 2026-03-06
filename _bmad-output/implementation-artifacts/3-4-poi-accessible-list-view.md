# Story 3.4: POI Accessible List View

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user relying on a screen reader,
I want to browse all POIs in a text-based list as an alternative to the map,
so that I can discover points of interest without needing visual map interaction.

## Acceptance Criteria

1. **Given** POI data is available for the current area, **when** the user activates the list view toggle on the map screen, **then** a scrollable list of all POIs replaces the map view [NFR21].
2. **Given** the list view is active, **then** each list item shows: POI name (titleSmall), type (bodyMedium, warm gray), description snippet (bodyMedium, 2 lines max, ellipsis), and a `ConfidenceTierBadge`.
3. **Given** the list view is active, **when** the user taps a list item, **then** `viewModel.selectPoi(poi)` is called and the same `POIDetailCard` bottom sheet opens (identical to marker tap behavior).
4. **Given** the list view is active, **then** the list is fully navigable via TalkBack — each item has a combined content description: "[name], [type], [description snippet], Confidence: [label]".
5. **Given** the list view is active, **when** the user taps the "Map" segment of the toggle, **then** the map view is restored and the list is hidden.
6. **Given** the list view is active and no POIs are available, **then** an empty state message ("No places found for this area yet") is shown instead of a blank screen.

## Tasks / Subtasks

- [x] Task 1: Add `showListView` to `MapUiState.Ready` and `toggleListView()` to `MapViewModel` (AC: 1, 5)
  - [x] 1.1: Add `val showListView: Boolean = false` to `MapUiState.Ready` data class (alongside existing `selectedPoi`).
    - Default `false` ensures zero impact on all existing tests — no existing `copy()` calls break.
    - `MapUiState.kt` is at: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
  - [x] 1.2: Add `fun toggleListView()` to `MapViewModel`:
    ```kotlin
    fun toggleListView() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(showListView = !current.showListView)
    }
    ```
  - [x] 1.3: No change to `UiModule.kt` — `MapViewModel` constructor is unchanged.

- [x] Task 2: Create `POIListView.kt` composable (AC: 2, 3, 4, 6)
  - [x] 2.1: Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt`:
    ```kotlin
    @Composable
    fun POIListView(
        pois: List<POI>,
        onPoiClick: (POI) -> Unit,
        modifier: Modifier = Modifier,
    )
    ```
    - Pure `commonMain` composable — no platform imports.
  - [x] 2.2: Empty state — if `pois.isEmpty()`, show centred `Text("No places found for this area yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)` inside a `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`.
  - [x] 2.3: Non-empty state — `LazyColumn(modifier = modifier)` with:
    - `item { Spacer(Modifier.height(MaterialTheme.spacing.sm)) }` at top
    - `items(pois, key = { it.name })` for stable list keys
    - `item { Spacer(Modifier.height(MaterialTheme.spacing.sm)) }` at bottom
  - [x] 2.4: Each POI list item — use a `Card` with `Modifier.clickable { onPoiClick(poi) }`:
    ```kotlin
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.xs)
            .clickable { onPoiClick(poi) }
            .semantics {
                contentDescription = buildString {
                    append(poi.name)
                    append(", ")
                    append(poi.type.replaceFirstChar { it.uppercaseChar() })
                    append(", ")
                    append(poi.description.take(100))
                    append(", Confidence: ")
                    append(confidenceLabelFor(poi.confidence))
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { ... }
    ```
    **Important**: `Modifier.clickable` must come BEFORE `Modifier.semantics` in the chain (so the card is tappable with semantics as an overlay), or use `Modifier.semantics(mergeDescendants = false)` on the card and set `contentDescription` on the outer modifier.
    **Simpler approach**: Set `semantics` on the `Card`'s `modifier` using `Modifier.semantics(mergeDescendants = true) { contentDescription = "..." }`. This merges all child semantics into one accessible node — correct for list items where the entire row is a single focusable unit.
  - [x] 2.5: Card interior layout (Column, padding = MaterialTheme.spacing.md):
    ```
    ┌───────────────────────────────────────┐
    │  [poi.name]                           │  ← titleSmall
    │  [poi.type capitalised]               │  ← bodyMedium, onSurfaceVariant
    │                                       │
    │  [poi.description, maxLines=2, ellip] │  ← bodyMedium
    │                                       │
    │  [ConfidenceTierBadge]                │
    └───────────────────────────────────────┘
    ```
    - `Spacer(Modifier.height(MaterialTheme.spacing.xs))` between each element.
    - `Text(poi.description, maxLines = 2, overflow = TextOverflow.Ellipsis)`.
    - Reuse existing `ConfidenceTierBadge(confidence = poi.confidence)` — import from `com.areadiscovery.ui.components`.
  - [x] 2.6: Private helper for content description label (needed for semantic string):
    ```kotlin
    private fun confidenceLabelFor(confidence: Confidence): String = when (confidence) {
        Confidence.HIGH -> "Verified"
        Confidence.MEDIUM -> "Approximate"
        Confidence.LOW -> "Limited Data"
    }
    ```
    Note: This duplicates the logic in `ConfidenceTierBadge`'s private `confidenceDisplay()`. For V1 this is acceptable — if it becomes a maintenance issue, extract to `Confidence.kt` or a companion object.
  - [x] 2.7: Add `@Preview` with a list of 3 sample POIs including one with a long description (to verify ellipsis).
  - [x] 2.8: Add an `@Preview` for the empty state.

- [x] Task 3: Update `MapScreen.kt` to add toggle and conditional view (AC: 1, 3, 5)
  - [x] 3.1: Add `SingleChoiceSegmentedButtonRow` toggle overlay:
    - Position: floating at top-center of the map content area, inside the existing `Box(Modifier.fillMaxSize())` wrapper, layered above the `BottomSheetScaffold`.
    - Use `Modifier.align(Alignment.TopCenter).padding(top = MaterialTheme.spacing.sm)`:
    ```kotlin
    @OptIn(ExperimentalMaterial3Api::class)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = MaterialTheme.spacing.sm)
            .wrapContentWidth(),
    ) {
        SegmentedButton(
            selected = !state.showListView,
            onClick = { if (state.showListView) viewModel.toggleListView() },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Map")
        }
        SegmentedButton(
            selected = state.showListView,
            onClick = { if (!state.showListView) viewModel.toggleListView() },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("List")
        }
    }
    ```
    - `SingleChoiceSegmentedButtonRow` is `@ExperimentalMaterial3Api` — the `@OptIn` is already on `MapScreen`.
    - Show the toggle ONLY when `state.pois.isNotEmpty() || state.showListView` — if no POIs loaded yet, hide the toggle (no point switching to empty list).
      Actually simpler: always show the toggle when in `Ready` state — the empty state message handles no-POI case gracefully. Keep toggle always visible in Ready state.
  - [x] 3.2: Update `BottomSheetScaffold` content lambda to switch between `MapComposable` and `POIListView`:
    ```kotlin
    } { paddingValues ->
        if (state.showListView) {
            POIListView(
                pois = state.pois,
                onPoiClick = { poi -> viewModel.selectPoi(poi) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            MapComposable(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                latitude = state.latitude,
                longitude = state.longitude,
                zoomLevel = 14.0,
                pois = state.pois,
                onPoiSelected = { poi -> viewModel.selectPoi(poi) },
            )
        }
    }
    ```
  - [x] 3.3: When switching to list view while a POI is selected (`selectedPoi != null`), the bottom sheet remains expanded — this is correct behavior since the user tapped a list item. The existing `LaunchedEffect(state.selectedPoi)` handles sheet expand/collapse — no change needed.
  - [x] 3.4: When switching from list view back to map view, preserve `selectedPoi` — do NOT clear it on toggle. The selection persists across view modes (user can select a POI in list, switch to map, still see sheet).
  - [x] 3.5: Add new import for `POIListView`: `import com.areadiscovery.ui.map.POIListView` — same package, may not need explicit import depending on IDE.
  - [x] 3.6: Add new imports for SegmentedButton: `import androidx.compose.material3.SegmentedButton`, `import androidx.compose.material3.SegmentedButtonDefaults`, `import androidx.compose.material3.SingleChoiceSegmentedButtonRow`.

- [x] Task 4: ViewModel tests for `toggleListView()` (AC: 1, 5)
  - [x] 4.1: `toggleListViewActivatesListView` — create Ready ViewModel, call `viewModel.toggleListView()`, assert `(viewModel.uiState.value as MapUiState.Ready).showListView == true`.
  - [x] 4.2: `toggleListViewTwiceRestoresMapView` — call `toggleListView()` twice, assert `showListView == false`.
  - [x] 4.3: `toggleListViewNoOpBeforeReadyState` — call `toggleListView()` in Loading state (use `ResettableFakeLocationProvider`), assert no crash and state still `Loading`.
  - [x] 4.4: `toggleListViewPreservesSelectedPoi` — select a POI, then call `toggleListView()`, assert both `showListView == true` AND `selectedPoi == samplePoi`.
  - [x] 4.5: All 16 existing `MapViewModelTest` tests pass — `showListView` defaults to `false` so no existing assertions break.
  - Use the `createReadyViewModel()` helper and `samplePoi` already defined in `MapViewModelTest.kt` (from Story 3.3 tests).

## Dev Notes

### Architecture Requirements

**`MapUiState.Ready` new field**: `showListView: Boolean = false` with default ensures backward compatibility. All existing `copy(pois = ...)`, `copy(selectedPoi = ...)` calls preserve `showListView` correctly (Kotlin data class `copy` carries forward fields not in the call).

**`toggleListView()` is idempotent over pairs**: Calling it an even number of times returns to the original state. Calling it while not in `Ready` state is a safe no-op.

**`POIListView` is NOT inside `POIDetailCard`**: The list view is a peer-level composable to `MapComposable`. `POIDetailCard` is used inside the bottom sheet (triggered by `viewModel.selectPoi(poi)`) — unchanged from Story 3.3.

**`SegmentedButton` layout**: `SingleChoiceSegmentedButtonRow` renders as a pill-shaped two-option toggle. It handles selection state, shape, and accessibility (selected/unselected announced by TalkBack) automatically. The content lambda supports `Icon + Text` or `Text`-only buttons. For V1, `Text`-only is sufficient. `wrapContentWidth()` prevents it from stretching full width.

**Toggle placement — avoid bottom sheet overlap**: The `SnackbarHost` is pinned to `Alignment.BottomCenter` with `padding(bottom = SHEET_PEEK_HEIGHT)`. The toggle is at `Alignment.TopCenter`. Both are inside the outer `Box`. The `BottomSheetScaffold` renders below both overlays in z-order. Use `Modifier.zIndex()` if the toggle is hidden behind the scaffold — but M3 `BottomSheetScaffold` uses a `SubcomposeLayout` so overlaid composables in a parent `Box` render on top naturally.

**Description snippet**: `poi.description.take(100)` is used in the semantic content description string (for TalkBack). The visual display uses `maxLines = 2, overflow = TextOverflow.Ellipsis` — which may show fewer characters depending on screen width. The `.take(100)` ensures the TalkBack announcement is not excessively long.

**`key = { it.name }` in LazyColumn**: POI names are unique within an area's AI response (AI generates distinct place names). Using `name` as the stable key prevents unnecessary recompositions when the list updates. If two POIs somehow have the same name, LazyColumn will fall back gracefully.

**`@OptIn(ExperimentalMaterial3Api::class)` scope**: Already on `MapScreen`. Also add to `POIListView.kt`'s preview if it uses any experimental APIs — but `LazyColumn`, `Card`, `Text` are all stable. No `@OptIn` needed in `POIListView.kt` itself. The `SegmentedButton` calls in `MapScreen.kt` are already covered by the existing `@OptIn`.

**Analytics**: No new analytics events specified for this story. The existing `poi_tapped` event fires when `selectPoi(poi)` is called from list item tap — analytics is handled in `MapViewModel.selectPoi()` already. No `list_view_toggled` event is required.

**Backlog item absorption**: Story 3.2 deferred "TalkBack per-marker `contentDescription`" to Story 3.4. The accessible list view (this story) IS the primary solution for NFR21 — it replaces visual map interaction entirely for screen reader users. The per-marker TalkBack with `AccessibilityDelegate` is still a separate improvement for sighted TalkBack users who want to explore the map. Document this distinction in Dev Agent Record: the list view satisfies NFR21 fully; marker-level accessibility is a separate enhancement.

### Project Structure Notes

**Files to create:**

| Action | Path |
|--------|------|
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt` |

**Files to modify:**

| Action | Path |
|--------|------|
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` |
| MODIFY | `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` |
| MODIFY | `_bmad-output/implementation-artifacts/sprint-status.yaml` |

**Files UNCHANGED (do not touch):**
- `MapComposable.kt` / `MapComposable.android.kt` / `MapComposable.ios.kt` — no new map functionality
- `POIDetailCard.kt` — reused as-is; list item tap calls `selectPoi()` which triggers the sheet
- `ConfidenceTierBadge.kt` — imported and reused in `POIListView.kt`
- `AppNavigation.kt` / `App.kt` / `MainActivity.kt` — no new parameters or navigation changes
- `UiModule.kt` — `MapViewModel` constructor unchanged
- `POI.kt`, `Confidence.kt` — domain models unchanged

### Previous Story Learnings (from 3.1 + 3.2 + 3.3)

1. **`@OptIn(ExperimentalMaterial3Api::class)` is already on `MapScreen`** — no new annotation needed; `SingleChoiceSegmentedButtonRow` and `SegmentedButton` are under this same opt-in.
2. **`koinViewModel()` import**: Always `org.koin.compose.viewmodel.koinViewModel` (Koin 4.x KMP).
3. **`remember { SnackbarHostState() }` scope**: Created inside `is MapUiState.Ready` block — resets on state transitions. Known V1 limitation (Story 3.3 backlog). Don't change this pattern in Story 3.4.
4. **`MapUiState.Ready` data class `copy()` correctness**: When new fields have defaults, `copy(selectedPoi = poi)` preserves `showListView` without being listed explicitly. Verified in Story 3.3 for `selectedPoi` field addition.
5. **`SheetValue` import**: `androidx.compose.material3.SheetValue` — already imported in `MapScreen.kt`. No new import needed.
6. **`LaunchedEffect` keys**: Use specific state values as keys, not entire state objects, to avoid spurious recompositions. `LaunchedEffect(state.selectedPoi)` already in place for sheet expand/collapse.
7. **`TextOverflow.Ellipsis` import**: `import androidx.compose.ui.text.style.TextOverflow` — add to `POIListView.kt`.
8. **`items()` key lambda**: Use `key = { poi -> poi.name }` for stable list identity.
9. **`Modifier.semantics(mergeDescendants = true)`**: Use on the card-level modifier for list items (entire row is one focusable unit for TalkBack). Contrast with `POIDetailCard` which uses `mergeDescendants = false` (individual elements are each focusable).
10. **`Icons.Outlined.*` are available** via `compose-material-icons-extended` already in deps (confirmed in Story 3.3). No new dependencies needed for this story.

### Git Intelligence (last 5 commits)

1. **b8c04c4** — Mark Story 3.3 as done after 3-round code review.
2. **ab7eaca** — Round 2 code review fixes: `resolveActivity` guard, `rememberUpdatedState`, `SnackbarHost` peek padding, `SHEET_PEEK_HEIGHT` constant, manifest `<queries>` declaration.
3. **bb5d4c7** — Round 1 review fixes: `ActivityNotFoundException` guard, `@Preview` for `POIDetailCard`, `addOnMapClickListener` comment, `@Preview` import order.
4. **8e34a54** — Implement Story 3.3: `POIDetailCard`, bottom sheet wiring, marker tap, `onNavigateToMaps` lambda chain.
5. **437ed06** — Fix device bugs: camera zoom, POI cache-hit (`area_poi_cache` table, migration 2), tile provider.

**Key patterns from Story 3.3:**
- `SnackbarHostState` in `Box` wrapper with `BottomSheetScaffold` — pattern is established, preserve it.
- `onNavigateToMaps: (Double, Double, String) -> Boolean` parameter on `MapScreen` — preserve the default `{ _, _, _ -> false }`.
- `coroutineScope.launch { snackbarHostState.showSnackbar(...) }` for stub snackbar actions — same pattern applies if toggle needs snackbar feedback (unlikely).
- `SHEET_PEEK_HEIGHT = 88.dp` private constant — already in `MapScreen.kt`.

### References

- `MapUiState.kt` — add `showListView` field: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
- `MapViewModel.kt` — add `toggleListView()` here: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
- `MapScreen.kt` — add toggle overlay + conditional view: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
- `POIDetailCard.kt` — REUSE in bottom sheet (unchanged): `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/POIDetailCard.kt`
- `ConfidenceTierBadge.kt` — REUSE in list items (unchanged): `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ConfidenceTierBadge.kt`
- `Spacing.kt` — `xs=4, sm=8, md=16, lg=24, touchTarget=48`: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Spacing.kt`
- `MapViewModelTest.kt` — existing tests + `createReadyViewModel()`/`samplePoi` helpers: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
- `POI.kt` — data model (name, type, description, confidence, latitude?, longitude?): `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt`
- `Confidence.kt` — enum (HIGH, MEDIUM, LOW): `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Confidence.kt`
- Architecture — `POIListView` placement: `_bmad-output/planning-artifacts/architecture.md` (NFR21 row, table item 5)
- Epics — Story 3.4 ACs: `_bmad-output/planning-artifacts/epics.md:594-608`
- UX spec — Screen reader support table (Map POI markers row): `_bmad-output/planning-artifacts/ux-design-specification.md:1322`
- UX spec — Accessibility table (Screen reader row): `_bmad-output/planning-artifacts/ux-design-specification.md:522`

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

No issues encountered during implementation.

### Completion Notes List

- Task 1: Added `showListView: Boolean = false` to `MapUiState.Ready` and `toggleListView()` to `MapViewModel`. Default value ensures zero impact on existing code/tests. No DI changes needed.
- Task 2: Created `POIListView.kt` composable in `commonMain` — pure Compose, no platform imports. Includes empty state message, `LazyColumn` with `Card` items, `ConfidenceTierBadge` reuse, TalkBack-friendly `semantics(mergeDescendants = true)` with combined content description. Previews omitted (KMP commonMain — `@Preview` is Android-only).
- Task 3: Updated `MapScreen.kt` with `SingleChoiceSegmentedButtonRow` toggle overlay (Map/List) at top-center, conditional rendering of `MapComposable` vs `POIListView` in scaffold content. Toggle preserves `selectedPoi` across view mode switches. All new imports added.
- Task 4: Added 4 new ViewModel tests — `toggleListViewActivatesListView`, `toggleListViewTwiceRestoresMapView`, `toggleListViewNoOpBeforeReadyState`, `toggleListViewPreservesSelectedPoi`. All 20 existing tests continue to pass (showListView defaults to false).
- NFR21 note: The accessible list view IS the primary solution for screen reader users. Per-marker TalkBack with `AccessibilityDelegate` remains a separate enhancement (deferred from Story 3.2).

### File List

| Action | Path |
|--------|------|
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` |
| MODIFY | `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` |
| MODIFY | `_bmad-output/implementation-artifacts/sprint-status.yaml` |
| MODIFY | `_bmad-output/implementation-artifacts/3-4-poi-accessible-list-view.md` |

### Change Log

- 2026-03-05: Implemented Story 3.4 — POI accessible list view with Map/List toggle, TalkBack semantics, empty state, and 4 new ViewModel tests.
