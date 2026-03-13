# Quick Spec: Golden Path State Machine Regression Test

## Goal

One long-running unit test that simulates a full user session through every critical state transition. Guards against the class of bugs that only appear when moving between states (area change, search cancel, save, vibe switch, background resume).

## Why

8 of the last 11 bug fixes were state transition bugs. Existing tests cover individual functions but not the sequence of transitions a real user makes in a single session. This test catches regressions before they reach the device.

## File

`composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt`

## Test Name

`goldenPath_fullSessionNoRegressions`

## Fakes Required

All exist already:
- `SuspendingFakeAreaRepository` — controls when POIs arrive
- `FakeLocationProvider` / `ResettableFakeLocationProvider` — controls GPS
- `FakeWeatherProvider` — controls weather state
- `FakeSavedPoiRepository` — controls saves
- `FakeMapTilerGeocodingProvider` — controls search suggestions

## Test Flow

Each section below is a phase of the test. After each phase, assert the EXACT expected state shape. Use comments to name which historical bug each assertion guards.

### Phase 1: App Launch

```
1. Create VM with ResettableFakeLocationProvider (suspending)
2. Assert: state == Loading
3. Complete location with (40.0, -74.0), geocode = "Area A"
4. Assert: state == Ready, areaName == "Area A", pois == empty, isSearchingArea == false
5. Note cameraMoveId as baselineCameraMoveId
```

Guards: initial state correctness

### Phase 2: Stage 1 POIs Arrive

```
1. Complete area repository call with PinsReady(3 POIs with coords)
2. Assert: pois.size == 3, all have lat/lng != null
3. Assert: isEnrichingArea == true (stage 2 not yet arrived)
4. Assert: cameraMoveId unchanged (POI arrival should NOT move camera — map fits via LaunchedEffect)
```

Guards: progressive loading state

### Phase 3: Stage 2 Enrichment Arrives

```
1. Complete area repository call with PortraitComplete(3 POIs with vibe + description)
2. Assert: pois.size == 3, all have vibe set, description enriched
3. Assert: isEnrichingArea == false
4. Assert: imageUrl populated on POIs that had wikiSlug
```

Guards: H1 (Stage 2 POIs not dropped), H3 (enrichment applied)

### Phase 4: User Saves a POI

```
1. Call savePoi(pois[0], "Area A")
2. Advance scheduler
3. Assert: savedPoiIds contains pois[0].savedId
4. Assert: savedPoiCount == 1
5. Assert: cameraMoveId == baselineCameraMoveId (MUST NOT change)
6. Assert: selectedPoi == null (save doesn't select)
```

Guards: H2 (camera zoom on save), save state correctness

### Phase 5: User Switches Vibe

```
1. Call switchDynamicVibe(state.dynamicVibes[0]) (or switchVibe if still enum-based)
2. Assert: activeDynamicVibe == that vibe (or activeVibe)
3. Assert: savedVibeFilter == false
4. Assert: cameraMoveId == baselineCameraMoveId (MUST NOT change)
```

Guards: H2 (camera zoom on vibe switch)

### Phase 6: User Toggles Saved Vibe Filter

```
1. Call onSavedVibeSelected()
2. Assert: savedVibeFilter == true
3. Call onSavedVibeSelected() again (toggle off)
4. Assert: savedVibeFilter == false
5. Assert: activeVibe/activeDynamicVibe == null (cleared on toggle)
```

Guards: saved filter toggle correctness

### Phase 7: User Searches New Area

```
1. Note current areaName, pois, weather
2. Call onGeocodingSuggestionSelected(GeocodingSuggestion("Area B", ..., 50.0, -60.0))
3. Assert: isSearchingArea == true OR isGeocodingInitiatedSearch == true
4. Assert: areaName changes to "Area B" (after geocode completes)
5. Assert: old pois cleared or replaced
6. Assert: savedPoiIds updated for new area
7. Assert: weather refreshed (not stale from Area A)
```

Guards: query cancel on nav change, weather stale after search, vibeAreaSaveCounts update

### Phase 8: Cancel Mid-Search

```
1. Start another search (Area C) but DON'T complete the repository call
2. Call onGeocodingCancelLoad()
3. Assert: state restores to Area B data (preSearchSnapshot)
4. Assert: pois == Area B pois (not empty, not Area C)
5. Assert: isSearchingArea == false
```

Guards: cancel-restores-state regression

### Phase 9: Cache Hit on Return

```
1. Start a new area fetch for "Area B" again (simulate return)
2. Complete repository with cache hit (immediate PortraitComplete, no PinsReady)
3. Assert: pois restored from cache
4. Assert: AI provider call count == expected (no extra calls)
```

Guards: POI cache hit path, no wasted API calls

### Phase 10: Select POI from Chat Card

```
1. Call selectPoiWithImageResolve(fallbackPoi) where fallbackPoi has name matching pois[1]
2. Assert: selectedPoi != null
3. Assert: selectedPoi.name == fallbackPoi.name
4. Call clearPoiSelection()
5. Assert: selectedPoi == null
```

Guards: M1 (guard checks coordinates), POI selection flow

## Assertion Pattern

After each phase, assert using a helper:

```kotlin
fun assertReadyState(
    vm: MapViewModel,
    areaName: String? = null,
    poisSize: Int? = null,
    selectedPoi: String? = null, // null = no assertion, "" = assert null
    isSearching: Boolean? = null,
    savedVibeFilter: Boolean? = null,
    cameraMoveId: Int? = null,
    savedPoiCount: Int? = null,
) {
    val state = assertIs<MapUiState.Ready>(vm.uiState.value)
    areaName?.let { assertEquals(it, state.areaName) }
    poisSize?.let { assertEquals(it, state.pois.size) }
    if (selectedPoi == "") assertNull(state.selectedPoi)
    else selectedPoi?.let { assertEquals(it, state.selectedPoi?.name) }
    isSearching?.let { assertEquals(it, state.isSearchingArea) }
    savedVibeFilter?.let { assertEquals(it, state.savedVibeFilter) }
    cameraMoveId?.let { assertEquals(it, state.cameraMoveId) }
    savedPoiCount?.let { assertEquals(it, state.savedPoiCount) }
}
```

## Acceptance Criteria

1. Test passes on current codebase (green baseline)
2. Test is a single @Test function (not split into phases)
3. Each phase has a comment naming which bug(s) it guards
4. Test runs in < 3 seconds
5. Test uses only existing fakes (no new test infrastructure)
6. Test is added to MapViewModelTest.kt (not a separate file)

## Effort

~1 hour implementation. No production code changes.

## Notes

- If dynamic vibes have landed by the time this is implemented, use `switchDynamicVibe` instead of `switchVibe`. Adapt phase 5 accordingly.
- The test should be the LAST test in MapViewModelTest (before private helper classes) for readability.
- Do NOT mock internals. Only use public ViewModel API + fake dependencies.
