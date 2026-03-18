---
title: 'Streaming Discovery UX'
slug: 'streaming-discovery-ux'
created: '2026-03-17'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
reviewFindings: '2C+5H+5M+5L fixed 2026-03-17; re-review C3+H6+M6+M7 fixed 2026-03-17'
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Coroutines/Flow', 'MapLibre']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/GeocodingSearchBar.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PoiCarousel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt'
code_patterns: ['MVVM', 'StateFlow/UiState', 'Compose unidirectional data flow']
test_patterns: ['MapViewModelTest', 'MockK', 'Turbine', 'JUnit']
---

# Tech-Spec: Streaming Discovery UX

**Created:** 2026-03-17

## Overview

### Problem Statement

AreaDiscovery's map discovery UX was built for a 3-POI batch paradigm: pins arrive in groups, paginated via `< 1/4 >` arrows in the search bar. The product is moving to a streaming model where pins appear individually as they're discovered. The current UI has three compounding issues:

1. **Stale pagination widget** — `InlineBatchNav` (`< 1/4 >`) is a batch-world concept; it conveys page position but not discovery progress.
2. **Carousel dots show batch scope, not total** — Dots reflect the current batch (~3 POIs), not the full discovered set.
3. **Three slideshow bugs** — Slideshow stops permanently after user interaction (#7), doesn't restart after "Search this area" if pois arrive late (#8), and manual carousel swipe doesn't reset camera zoom from slideshow level 16.0 to default 14.0 (#9).
4. **No "Take Me Somewhere"** — No profile-driven random discovery entry point exists despite taste data being available via saved POI vibe history.

### Solution

1. Replace `InlineBatchNav` with a live `PoiCountChip` in the search bar: "Discovering..." during fetch → "N places" when pins land. Staggered count-up animation (150ms per pin) gives a streaming feel without backend changes.
2. Carousel dots reflect `pois` (the vibe-filtered set the carousel actually scrolls through), capped at 9 silently. The counter in the search bar communicates total discovered count.
3. Fix all three slideshow bugs with surgical ViewModel changes.
4. Replace the "Search this area" floating pill with a **segmented toggle pill** (Concept D): `↻ Search here | 🎲 Surprise me`. User toggles active mode; tapping executes it. Only shown when `showSearchAreaPill = true`. 🎲 segment is only active when user has saved POIs (taste data exists).

### Scope

**In Scope:**
- Remove `InlineBatchNav` from `GeocodingSearchBar` (Idle and Selected states)
- Add `PoiCountChip` composable to `GeocodingSearchBar`
- `MapUiState.Ready`: add `poiStreamingCount: Int` for animated counter drive
- Carousel dots: read from `pois.size` (carousel-actual, vibe-filtered) capped at 9 — NOT `allDiscoveredPois.size` (dots beyond `pois.size` can never become active)
- Bug #7: fix slideshow not restarting after user interaction
- Bug #8: fix slideshow not triggering after "Search this area" when pois arrive late
- Bug #9: fix camera zoom not resetting on manual carousel swipe
- `AreaContext`: add `tasteProfile: List<String>` field
- `MapViewModel.onSurpriseMe()`: derives taste profile from `latestSavedPois`, builds modified context, re-runs area portrait
- `MapUiState.Ready`: add `showSurpriseMe: Boolean` (true when user has saved POIs)
- Replace `FilledTonalButton` "Search this area" in `MapScreen` with new `SearchSurpriseTogglePill` composable — two segments, sliding highlight, same visibility condition as current pill
- Unit tests for all ViewModel changes

**Out of Scope:**
- True per-token Gemini streaming (POIs still arrive in batches; animation is client-side stagger only)
- Ghost pins for saved/visited POIs (separate spec)
- Simplified card action redesign (separate spec)
- Dot ring-timer for slideshow position (visual polish, post-spec)
- Removing `poiBatches`/`activeBatchIndex` from state (batch fetch still used by background enrichment)

---

## Context for Development

### Codebase Patterns

- **State**: `MapUiState.Ready` is a sealed class `data class` in `MapUiState.kt`. All state updates go through `_uiState.value = current.copy(...)`. Never mutate state directly.
- **ViewModel**: `MapViewModel` has no `@HiltViewModel` — uses Koin. Constructor-injected. All UI-facing functions are `fun` on the class. Companion object holds all `internal const val` constants.
- **Slideshow**: `startAutoSlideshow()` is private; `stopAutoSlideshowIfRunning()` is public. `slideshowJob: Job?` tracks the running coroutine.
- **Idle detection**: `MapScreen` bumps `idleTimerKey` on every pointer event; `LaunchedEffect(idleTimerKey)` waits `IDLE_THRESHOLD_MS` then calls `viewModel.onIdleDetected()`.
- **Search/Surprise toggle pill**: Replaces the existing `FilledTonalButton` "Search this area" in `MapScreen` (around line 411). New `SearchSurpriseTogglePill` composable with two segments and a sliding highlight. Same `showSearchAreaPill` visibility condition. The 🎲 segment is visually active only when `state.showSurpriseMe = true`.
- **AreaContext**: `data class` with no defaults needing migration. `AreaContextFactory.create()` builds it. Adding a nullable field with default is safe.
- **Dots**: Currently rendered inline in `PoiCarousel` using `pois.indices.forEach`. Change to `minOf(pois.size, MAX_DOTS)` — no new param; uses existing `pois` param. `MAX_DOTS = 9` is private to `PoiCarousel`.
- **Strings**: All user-visible strings go in `composeApp/src/commonMain/composeResources/values/strings.xml` — never hardcoded in composables.

### Files to Reference

| File | Purpose |
|------|---------|
| `MapUiState.kt` | Add `poiStreamingCount: Int = 0` field |
| `MapViewModel.kt` | Fix 3 bugs + add `onSurpriseMe()` |
| `MapScreen.kt` | Replace "Search this area" button with `SearchSurpriseTogglePill`; wire counter + carousel props |
| `GeocodingSearchBar.kt` | Replace `InlineBatchNav` with `PoiCountChip` (IdleState/SelectedState); add "Discovering…" to SpinningState; remove batch nav + onSearchDeeper params |
| `PoiCarousel.kt` | Cap dots at 9 using `pois.size` (no new param needed) |
| `AreaContext.kt` | Add `tasteProfile: List<String> = emptyList()` |
| `AreaContextFactory.kt` | No change needed — `collectPortraitWithRetry` copies the factory result with `.copy(tasteProfile = tasteProfile)` |
| `GeminiPromptBuilder.kt` | Read `tasteProfile` from context and inject into prompt |
| `strings.xml` | Add: `search_discovering`, `search_n_places`, `vibe_surprise_me` |

### Key Investigation Findings (Step 2)

- **`buildAreaPortraitPrompt`** takes `AreaContext` directly (line ~70 in `GeminiPromptBuilder.kt`). Taste profile injection goes after the language rule block, before the "Output EXACTLY 6 JSON objects" line. `buildPinOnlyPrompt` does NOT take `AreaContext` — taste profile only needed in `buildAreaPortraitPrompt`.
- **Chat's `TasteProfile`** is a rich domain object used for chat context — unrelated to our new `tasteProfile: List<String>` in `AreaContext`. No naming conflict.
- **`strings.xml` format**: `%1$d` for int args. Confirmed pattern: `<string name="poi_list_empty_vibe">No %1$s found in this area</string>` (already uses %1$s). Use `%1$d` for the places count.
- **Toggle pill placement**: `MapScreen` line ~411 has `FilledTonalButton` "Search this area". Replace with `SearchSurpriseTogglePill(onSearchHere = viewModel::onSearchThisArea, onSurpriseMe = viewModel::onSurpriseMe, showSurpriseMe = state.showSurpriseMe)`. Same `if (state.showSearchAreaPill && ...)` visibility condition as the current button.
- **Test infrastructure**: `FakeAreaContextFactory`, `FakeSavedPoiRepository` exist. Tests use `runTest(testDispatcher)`, `advanceTimeBy()` for time-based assertions. Pattern: `createViewModel(savedPoiRepository = myFake)`.
- **`selectedPinIndex` guard root cause confirmed**: After `onCarouselSwiped(index)`, `selectedPinIndex` is set and never auto-cleared. `startAutoSlideshow()` guard `if (current.selectedPinIndex != null) return` blocks restart. Removing this guard is safe because `selectedIndex = state.autoSlideshowIndex ?: state.selectedPinIndex` in MapScreen means slideshow index takes natural priority.

### Technical Decisions

- **Client-side stagger, not backend streaming**: POIs still arrive via `BucketUpdate.PinsReady` (full batch). The count-up animation is driven by `poiStreamingCount` incrementing in a loop after pois arrive, 150ms per step. No backend changes required.
- **Dots read `pois.size` (H5 fix)**: Carousel scrolls within `pois` (vibe-filtered). Using `allDiscoveredPois.size` would render dots that can never become active (when a vibe filter reduces the visible set). Dots must match what the carousel can actually reach: `minOf(pois.size, MAX_DOTS)`. The live counter in the search bar shows the total discovered count separately.
- **`selectedPinIndex` guard removed from `startAutoSlideshow()`**: The guard `if (current.selectedPinIndex != null) return` was preventing restart. Slideshow naturally overrides `selectedPinIndex` via `autoSlideshowIndex ?: selectedPinIndex` in MapScreen. Removing the guard is safe.
- **Bug #8 fix via `schedulePostSearchSlideshow()`**: After `onComplete` in the `onSearchThisArea` path, schedule `delay(IDLE_THRESHOLD_MS)` then `startAutoSlideshow()`. This handles the case where pois arrive after the idle timer has already fired.
- **Bug #9 fix in `onCarouselSwiped()`**: Always set `cameraZoomLevel = DEFAULT_ZOOM_LEVEL` in the state copy, regardless of `slideshowJob` state.
- **`onSurpriseMe()` taste derivation**: Count `vibe` field frequency in `latestSavedPois`. Take top 3 labels. Pass as `tasteProfile` in `AreaContext`. `GeminiPromptBuilder.buildAreaPortraitPrompt` injects after the language rule block: `"User taste: ${context.tasteProfile.joinToString(", ")}. Surface surprising, lesser-known places matching these vibes."` Only inject when `tasteProfile.isNotEmpty()`.
- **Surprise Me segment visibility**: The 🎲 segment is always visible in the toggle pill but only interactive when `state.showSurpriseMe = true` (i.e. user has saved POIs). When no saved POIs, the segment is dimmed/disabled — user can see the feature exists but can't use it until they've saved places. This is better UX than hiding it entirely.

---

## Review Findings Applied (2026-03-17)

All 17 findings from adversarial review resolved inline. Summary for dev agent awareness:

| ID | Severity | Fix Location | Summary |
|----|----------|-------------|---------|
| C1 | Critical | T8 | `collectPortraitWithRetry` now takes `tasteProfile: List<String> = emptyList()` param; injects at line ~1307 via `.copy(tasteProfile = tasteProfile)` |
| C2 | Critical | T10 | `isDiscovering` param removed; "Discovering…" rendered in `SpinningState` directly; `PoiCountChip` (IdleState/SelectedState) shows "N places" only |
| H1 | High | T6 | `pendingPostSearchSlideshow: Boolean` flag in ViewModel; set in `onSearchThisArea`, cleared in `onGeocodingSubmitEmpty` `onComplete` callback |
| H2 | High | T7 | `cancelAreaFetch()` now unconditionally cancels `counterAnimJob` and resets `poiStreamingCount = 0` before existing guard |
| H3 | High | T8 | `onSurpriseMe()` state copy preserves `visitedPois`, `visitedPoiIds`, `visitedPoiCount` |
| H4 | High | T14 | `showSurpriseMe` explicitly set in `observeAll()` observer (not `observeSavedIds()`) |
| H5 | High | T11 | Dots use `minOf(pois.size, MAX_DOTS)` — carousel-scrollable count, not `allDiscoveredPois.size` |
| M1 | Medium | T13 | `onSearchDeeper` also removed from `GeocodingSearchBar` call site in `MapScreen` |
| M2 | Medium | T7 | `counterAnimJob: Job?` tracks animation; cancelled before each new launch |
| M3 | Medium | T7 | `animatePoiCounter` called with `allDiscoveredPois.size` (total), not `update.pois.size` (batch delta) |
| M4 | Medium | T12 | `animateFloatAsState(targetValue = activeMode.toFloat(), tween(200))` drives sliding highlight offset |
| M5 | Medium | Tests | Added `onSurpriseMe_doesNothingWhenNoSaves` + `onSurpriseMe_doesNothingWhenSavesHaveNoVibe` |
| L1 | Low | T1 | `search_n_places` is `<plurals>` resource ("1 place" / "N places") |
| L2 | Low | T12 | PlatformBackHandler not needed — pill is not an overlay; documented explicitly |
| L3 | Low | T8/T14 | `showSurpriseMe` condition: `.any { it.vibe.isNotBlank() }` |
| L4 | Low | T11 | `MAX_DOTS = 9` stays `private` in `PoiCarousel.kt` |
| L5 | Low | T8/T1 | ~~Error string uses string resource~~ — **superseded by C3** |
| **C3** | **Critical** | T8 | Reverted L5 fix: `_errorEvents: MutableSharedFlow<String>` can't accept `Res.string.*`; use hardcoded literal `"Discovery failed — try again"`. `surprise_me_error` string resource removed from T1. |
| **H6** | **High** | AC2 | AC2 rewritten to reference `pois` (carousel-scrollable); added vibe-filter scenario showing `pois=3` → 3 dots, not 9 |
| **M6** | **Medium** | T7 | `cancelAreaFetch()` now resets `pendingPostSearchSlideshow = false`; prevents ghost slideshow after pan-cancels a Search This Area fetch |
| **M7** | **Medium** | T8 | `collectPortraitWithRetry` pseudocode `onComplete` signature corrected: `suspend (pois: List<Poi>, finalAreaName: String) -> Unit` (was wrong `List<Vibe>`) |

---

## Implementation Plan

### Tasks

**T1 — `strings.xml`: Add new strings** *(no dependencies)*
- File: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Add:
  ```xml
  <string name="search_discovering">Discovering…</string>
  <string name="vibe_surprise_me">Surprise Me</string>
  <!-- L1: plurals for "1 place" / "N places" -->
  <plurals name="search_n_places">
      <item quantity="one">%1$d place</item>
      <item quantity="other">%1$d places</item>
  </plurals>
  ```
- Note: `<plurals>` resource for "1 place" vs "N places" — use `pluralStringResource(Res.plurals.search_n_places, poiCount, poiCount)` at call site. `%1$d` for the integer arg.

**T2 — `AreaContext.kt`: Add taste profile field** *(no dependencies)*
- File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt`
- Add: `val tasteProfile: List<String> = emptyList()`
- No callers need updating — default is empty list; existing `AreaContextFactory.create()` continues to work.

**T3 — `MapUiState.kt`: Add `poiStreamingCount`** *(no dependencies)*
- File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
- Add to `MapUiState.Ready`: `val poiStreamingCount: Int = 0`
- This drives the animated counter in the search bar.

**T4 — `MapViewModel.kt`: Fix bug #9 — camera zoom on manual swipe** *(depends on T3)*
- File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
- In `onCarouselSwiped(index: Int)`:
  ```kotlin
  fun onCarouselSwiped(index: Int) {
      val state = _uiState.value as? MapUiState.Ready ?: return
      if (state.pois.isEmpty()) return
      _uiState.value = state.copy(
          selectedPinIndex = index.coerceIn(0, state.pois.size - 1),
          cameraZoomLevel = DEFAULT_ZOOM_LEVEL,  // always reset from slideshow zoom
      )
  }
  ```
- Root cause: `stopAutoSlideshowIfRunning()` has early return `if (slideshowJob == null)` so zoom isn't reset when slideshow has already ended naturally.

**T5 — `MapViewModel.kt`: Fix bug #7 — slideshow restarts after idle** *(depends on T3)*
- File: `MapViewModel.kt`
- In `startAutoSlideshow()`, remove the guard:
  ```kotlin
  // REMOVE THIS LINE:
  if (current.selectedPinIndex != null) return
  ```
- Rationale: `autoSlideshowIndex ?: selectedPinIndex` in MapScreen means slideshow index takes priority. Removing the guard lets idle re-trigger restart the slideshow even when user previously swiped to a card.
- Start index stays at `0` (slideshow always restarts from beginning on idle).

**T6 — `MapViewModel.kt`: Fix bug #8 — slideshow after Search This Area** *(depends on T5)*
- File: `MapViewModel.kt`
- **H1 fix — disambiguation flag**: `onSearchThisArea` and keyboard empty-submit both call `onGeocodingSubmitEmpty`. Without a flag, `schedulePostSearchSlideshow` would fire on keyboard-enter too. Use a private flag:
  ```kotlin
  private var pendingPostSearchSlideshow = false
  ```
- In `onSearchThisArea()`, set the flag BEFORE calling the shared path:
  ```kotlin
  fun onSearchThisArea() {
      pendingPostSearchSlideshow = true
      onGeocodingSubmitEmpty()
  }
  ```
- Add private helper:
  ```kotlin
  private fun schedulePostSearchSlideshow() {
      viewModelScope.launch {
          delay(IDLE_THRESHOLD_MS)
          val state = _uiState.value as? MapUiState.Ready ?: return@launch
          if (state.pois.isNotEmpty() && !state.isSearchingArea && !state.showListView && state.selectedPoi == null) {
              startAutoSlideshow()
          }
      }
  }
  ```
- In `onGeocodingSubmitEmpty()` → `areaFetchJob` `onComplete` callback, read and clear the flag:
  ```kotlin
  onComplete = { pois, _ ->
      // ... existing state update ...
      if (pendingPostSearchSlideshow) {
          pendingPostSearchSlideshow = false
          schedulePostSearchSlideshow()
      }
  }
  ```
- This ensures keyboard empty-submit never triggers `schedulePostSearchSlideshow`, only explicit "Search this area" taps do.

**T7 — `MapViewModel.kt`: Animated POI counter** *(depends on T3, T5)*
- File: `MapViewModel.kt`
- **M2 fix — cancel before launch**: Declare `private var counterAnimJob: Job? = null`. Cancel before each new animation to avoid overlapping increments from rapid batch arrivals.
- **M3 fix — use `allDiscoveredPois.size`**: Each `PinsReady`/`VibesReady` event delivers a delta batch. The counter target should be the total accumulated count (`allDiscoveredPois.size`), not just the current batch (`update.pois.size`), so the counter never goes backwards.
  ```kotlin
  private var counterAnimJob: Job? = null

  private fun animatePoiCounter(targetCount: Int) {
      counterAnimJob?.cancel()
      counterAnimJob = viewModelScope.launch {
          val current = _uiState.value as? MapUiState.Ready ?: return@launch
          val startCount = current.poiStreamingCount
          for (i in (startCount + 1)..targetCount) {
              delay(150L)
              val s = _uiState.value as? MapUiState.Ready ?: break
              _uiState.value = s.copy(poiStreamingCount = i)
          }
      }
  }
  ```
- Call `animatePoiCounter(state.allDiscoveredPois.size)` after each `PinsReady` / `VibesReady` state update (using the updated state's `allDiscoveredPois.size`, not the raw batch size).
- **H2 fix + M6 fix — unconditional reset in `cancelAreaFetch()`**: `cancelAreaFetch()` currently has a conditional guard (early return when no active job) that skips state cleanup. Add unconditional resets BEFORE the existing guard — including `pendingPostSearchSlideshow` (M6: avoids ghost slideshow on next unrelated submit after a cancelled "Search this area"):
  ```kotlin
  fun cancelAreaFetch() {
      counterAnimJob?.cancel()
      counterAnimJob = null
      pendingPostSearchSlideshow = false  // M6: reset flag so cancelled searches don't ghost-trigger slideshow
      val current = _uiState.value as? MapUiState.Ready
      if (current != null) {
          _uiState.value = current.copy(poiStreamingCount = 0)
      }
      areaFetchJob?.cancel()  // existing guard logic follows
      // ...
  }
  ```

**T8 — `MapViewModel.kt`: Add `onSurpriseMe()` + update `collectPortraitWithRetry`** *(depends on T2, T3)*
- File: `MapViewModel.kt`

**C1 fix — `collectPortraitWithRetry` needs `tasteProfile` param**: The function creates its own `AreaContext` internally at line ~1307: `val context = areaContextFactory.create().copy(isNewUser = pendingColdStart)`. Add a parameter so the caller can inject taste data:
  ```kotlin
  private suspend fun collectPortraitWithRetry(
      areaName: String,
      tasteProfile: List<String> = emptyList(),  // C1: new param
      onComplete: suspend (pois: List<Poi>, finalAreaName: String) -> Unit,  // M7: real signature — second param is String, not List<Vibe>
      onError: (Exception) -> Unit,
  ) {
      // At line ~1307, change to:
      val context = areaContextFactory.create().copy(
          isNewUser = pendingColdStart,
          tasteProfile = tasteProfile,  // C1: inject taste
      )
      // rest of function unchanged
  }
  ```
- All existing callers pass no `tasteProfile` (default = `emptyList()`) — no changes needed at those call sites.

**L3 fix — `showSurpriseMe` condition**: Use `.any { it.vibe.isNotBlank() }` (not `.isNotEmpty()`) so users with saves that have no vibe tag don't falsely enable Surprise Me:
  ```kotlin
  val hasTasteData = latestSavedPois.any { it.vibe.isNotBlank() }
  if (!hasTasteData) return
  ```

- Add `onSurpriseMe()`:
  ```kotlin
  fun onSurpriseMe() {
      val current = _uiState.value as? MapUiState.Ready ?: return
      // L3: check for vibed saves, not just any saves
      val hasTasteData = latestSavedPois.any { it.vibe.isNotBlank() }
      if (!hasTasteData) return
      // Derive taste profile: count vibe frequency, take top 3
      val tasteProfile = latestSavedPois
          .map { it.vibe }
          .filter { it.isNotBlank() }
          .groupingBy { it }
          .eachCount()
          .entries
          .sortedByDescending { it.value }
          .take(3)
          .map { it.key }
      cancelAreaFetch()
      val areaName = current.areaName
      // H3 fix: preserve visited state in state copy
      _uiState.value = current.copy(
          isSearchingArea = true,
          isLoadingVibes = true,
          pois = emptyList(),
          poiStreamingCount = 0,
          activeDynamicVibe = null,
          selectedPinIndex = null,
          visitedPois = current.visitedPois,        // H3: preserve
          visitedPoiIds = current.visitedPoiIds,    // H3: preserve
          visitedPoiCount = current.visitedPoiCount, // H3: preserve
      )
      areaFetchJob = viewModelScope.launch {
          collectPortraitWithRetry(
              areaName = areaName,
              tasteProfile = tasteProfile,  // C1: pass taste data
              onComplete = { _, _ -> schedulePostSearchSlideshow() },
              onError = { _ -> _errorEvents.tryEmit("Discovery failed — try again") }, // C3: hardcoded literal — Res.string.* can't resolve in ViewModel
          )
      }
  }
  ```
- **C3 note**: `_errorEvents: MutableSharedFlow<String>`. KMP string resources (`Res.string.*`) require a Composable context and cannot be resolved inside a ViewModel. All existing `onError` lambdas in `MapViewModel` use hardcoded string literals — match that pattern. The `surprise_me_error` string resource added in T1 is **not needed** — remove that entry from T1's strings.xml changes.

**T9 — `GeminiPromptBuilder.kt`: Inject taste profile** *(depends on T2)*
- File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
- When `context.tasteProfile.isNotEmpty()`, append to the system prompt:
  `"User taste profile: ${context.tasteProfile.joinToString(", ")}. Prioritise surprising, lesser-known places matching these vibes."`
- Locate the section where `AreaContext` fields are injected into the prompt string and add the taste block there.

**T10 — `GeocodingSearchBar.kt`: Replace batch nav with POI counter** *(depends on T1)*
- File: `GeocodingSearchBar.kt`
- Remove params: `showBatchNav`, `batchIndex`, `batchTotal`, `onPrevBatch`, `onNextBatch`, `onSearchDeeper`
- Add params: `poiCount: Int = 0`
- **C2 fix — "Discovering..." goes in SpinningState, not via `isDiscovering` param**: When `isSearchingArea = true`, `GeocodingSearchBar` renders `SpinningState`. `IdleState`/`SelectedState` only render when `isSearchingArea = false`. Therefore `isDiscovering = state.isSearchingArea` would always be `false` at those call sites — dead code. Do NOT add an `isDiscovering` param to `IdleState`/`SelectedState`. Instead:
  - In `SpinningState` content: add `Text(stringResource(Res.string.search_discovering), ...)` where batch nav used to be
  - In `IdleState` / `SelectedState`: add `PoiCountChip(poiCount)` showing "N places" only (never "Discovering…")
- Add private `PoiCountChip` composable (IdleState/SelectedState only):
  ```kotlin
  @Composable
  private fun PoiCountChip(poiCount: Int) {
      if (poiCount <= 0) return
      Text(
          text = pluralStringResource(Res.plurals.search_n_places, poiCount, poiCount),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.6f),
      )
  }
  ```
- In `SpinningState` content (where `InlineBatchNav` used to render during searches):
  ```kotlin
  Text(
      text = stringResource(Res.string.search_discovering),
      style = MaterialTheme.typography.labelSmall,
      color = Color.White.copy(alpha = 0.6f),
  )
  ```
- Replace `InlineBatchNav(...)` calls in `IdleState` and `SelectedState` with `PoiCountChip(poiCount)`.
- Remove `InlineBatchNav` composable entirely.

**T11 — `PoiCarousel.kt`: Dots reflect carousel-scrollable POIs** *(depends on T3)*
- File: `PoiCarousel.kt`
- **H5 fix — use `pois.size`, NOT `allDiscoveredPois.size`**: The carousel renders `pois` (vibe-filtered). If dots were driven by `allDiscoveredPois.size`, dots beyond `pois.size` could never become active, creating ghost dots. Drive dots from what the carousel can actually reach.
- **L4 fix — `MAX_DOTS` stays private**: This constant is UI-specific to `PoiCarousel`. Keep it private in the file, not shared to state or ViewModel.
- No new param needed — use existing `pois` param already available in `PoiCarousel`.
- Internal constant: `private const val MAX_DOTS = 9`
- Change dots render:
  ```kotlin
  val dotCount = minOf(pois.size, MAX_DOTS)  // H5: pois.size (carousel-scrollable), not allDiscoveredPois
  (0 until dotCount).forEach { i ->
      Box(
          Modifier
              .size(if (i == visibleIndex) 8.dp else 6.dp)
              .clip(CircleShape)
              .background(
                  if (i == visibleIndex) Color.White
                  else Color.White.copy(alpha = 0.4f)
              )
      )
  }
  ```
- `visibleIndex` remains `listState.firstVisibleItemIndex` (unchanged).
- Remove old: `pois.indices.forEach { i -> ... }`
- Remove `totalPoiCount: Int` param if it was added — no longer needed.

**T12 — `MapScreen.kt`: Add `SearchSurpriseTogglePill`** *(depends on T1, T8)*
- File: `MapScreen.kt`
- **L2 — PlatformBackHandler decision**: `SearchSurpriseTogglePill` is NOT an overlay or modal — it's a persistent pill on the map. It doesn't need `PlatformBackHandler`. Back button should dismiss the topmost modal (e.g. POI detail), not reset the pill mode.
- **M4 — Animation implementation**: Use `animateFloatAsState` for the sliding highlight. The active-segment highlight is a `Box` with `Modifier.offset { IntOffset(x = (activeMode * segmentWidth).roundToInt(), y = 0) }` where the offset is driven by `animateFloatAsState(targetValue = activeMode.toFloat(), animationSpec = tween(200))`.
  ```kotlin
  @Composable
  private fun SearchSurpriseTogglePill(
      onSearchHere: () -> Unit,
      onSurpriseMe: () -> Unit,
      surpriseMeEnabled: Boolean,  // false = 🎲 dimmed, not clickable
      modifier: Modifier = Modifier,
  ) {
      var activeMode by remember { mutableStateOf(0) } // 0 = Search, 1 = Surprise
      val highlightOffset by animateFloatAsState(
          targetValue = activeMode.toFloat(),
          animationSpec = tween(durationMillis = 200),
          label = "pillHighlight",
      )
      // Layout: Row inside RoundedCornerShape(20.dp) pill
      // Background highlight Box at fractional offset (highlightOffset * halfWidth)
      // Tap logic:
      //   segment i: if activeMode == i → execute action; else activeMode = i
      // 🎲 segment: ContentAlpha = if (surpriseMeEnabled) 1f else 0.35f; clickable = surpriseMeEnabled
      // Style: MapFloatingUiDark background, active highlight Color.White.copy(alpha = 0.15f)
  }
  ```
- Replace the existing `FilledTonalButton` "Search this area" block (MapScreen ~line 413) with:
  ```kotlin
  SearchSurpriseTogglePill(
      onSearchHere = viewModel::onSearchThisArea,
      onSurpriseMe = viewModel::onSurpriseMe,
      surpriseMeEnabled = state.showSurpriseMe,
  )
  ```
- Same outer visibility condition unchanged: `if (state.showSearchAreaPill && !state.isSearchingArea && !state.showListView && state.selectedPoi == null && !showProfile)`.

**T13 — `MapScreen.kt`: Wire counter + carousel props** *(depends on T3, T7, T10, T11)*
- File: `MapScreen.kt`
- `GeocodingSearchBar(...)` call site changes:
  - **Remove**: `showBatchNav`, `batchIndex`, `batchTotal`, `onPrevBatch`, `onNextBatch`
  - **M1 fix — also remove**: `onSearchDeeper` (no longer a param on `GeocodingSearchBar`)
  - **Add**: `poiCount = state.poiStreamingCount`
  - Do NOT add `isDiscovering` — "Discovering…" is now rendered inside `SpinningState` (C2 fix)
- `PoiCarousel(...)` call site: no `totalPoiCount` prop needed (H5 fix — carousel uses its existing `pois` param internally)

**T14 — `MapUiState.kt` + `MapViewModel.kt`: Add `showSurpriseMe`** *(no dependencies)*
- Add to `MapUiState.Ready`: `val showSurpriseMe: Boolean = false`
- **H4 fix — explicit observer**: Set in `MapViewModel.observeAll()` (the combined observer that also watches saved POIs), NOT in `observeSavedIds()` (which only tracks ID sets). The `observeAll()` observer has access to the full `SavedPoi` list with vibe fields.
- **L3 fix — condition**: Use `.any { it.vibe.isNotBlank() }` (not `.isNotEmpty()`) so users with blank-vibe saves don't falsely enable Surprise Me:
  ```kotlin
  // Inside observeAll() saved POI collector:
  val showSurpriseMe = savedPois.any { it.vibe.isNotBlank() }
  _uiState.value = current.copy(showSurpriseMe = showSurpriseMe)
  ```

### Acceptance Criteria

**AC1 — Live POI counter (T7, T10)**
- Given: user triggers a map search
- When: `isSearchingArea = true`
- Then: search bar shows "Discovering…" label (no arrows, no page numbers)
- When: first batch of POIs arrives (`PinsReady`)
- Then: counter animates from 0 → N, incrementing one step per 150ms
- When: streaming completes
- Then: label shows "N places" (static)
- When: user navigates to a new area
- Then: counter resets to 0 and "Discovering…" appears again

**AC2 — Carousel dots (T11)**
- Given: `pois` (vibe-filtered carousel list) has 5 items
- When: carousel renders
- Then: 5 dots shown; active dot is 8dp white, inactive are 6dp 40% white
- Given: `pois` has 12 items (no vibe filter active)
- When: carousel renders
- Then: exactly 9 dots shown (silent cap); no "+N" label
- Given: `pois` has 3 items (vibe filter active) and `allDiscoveredPois` has 12
- When: carousel renders
- Then: exactly 3 dots shown — dots must never exceed what the carousel can scroll to
- When: user swipes to card 3
- Then: dot at index 2 becomes active

**AC3 — Bug #7: Slideshow restarts after idle** *(T5)*
- Given: slideshow is running
- When: user swipes the carousel
- Then: slideshow stops immediately
- When: user is idle for 10s without touching the screen
- Then: slideshow restarts from card 0
- And: camera flies to first POI at zoom 16.0

**AC4 — Bug #8: Slideshow starts after Search This Area** *(T6)*
- Given: user taps "Search this area"
- When: POIs arrive (within or after 10s)
- Then: slideshow begins automatically after `IDLE_THRESHOLD_MS` from POI arrival
- And: no user interaction is needed to trigger it

**AC5 — Bug #9: Camera zoom resets on manual swipe** *(T4)*
- Given: slideshow is running (camera at zoom 16.0)
- When: slideshow ends naturally (all cards cycled)
- And: user swipes the carousel manually
- Then: `cameraZoomLevel` is set to 14.0 (DEFAULT_ZOOM_LEVEL)
- Given: slideshow is still running
- When: user swipes the carousel
- Then: slideshow stops AND `cameraZoomLevel` resets to 14.0

**AC6 — Surprise Me (T8, T9, T12, T14)**
- Given: user has panned the map (search pill visible)
- When: `SearchSurpriseTogglePill` renders
- Then: two segments shown — "↻ Search here" and "🎲 Surprise me"
- Given: user has 0 saved POIs
- When: pill renders
- Then: 🎲 segment is visible but dimmed (disabled, `alpha = 0.3f`)
- Given: user has ≥1 saved POI
- When: pill renders
- Then: 🎲 segment is fully active and tappable
- When: user taps 🎲 segment while "Search here" is active
- Then: 🎲 segment becomes active (sliding highlight moves), no action yet
- When: user taps 🎲 segment again (already active)
- Then: current pins clear, search bar shows "Discovering…", Gemini re-queries with top-3 vibe taste profile injected into prompt
- And: new pins reflect the user's saved vibe preferences

---

## Additional Context

### Dependencies

- No new libraries required
- `GeminiPromptBuilder` already reads from `AreaContext` — `tasteProfile` is an additive field

### Testing Strategy

- **`MapViewModelTest`** (extend existing):
  - `onCarouselSwiped_resetsZoomToDefault` — verify `cameraZoomLevel = DEFAULT_ZOOM_LEVEL` after swipe when zoom was at `SLIDESHOW_ZOOM_LEVEL`
  - `startAutoSlideshow_startsEvenWhenSelectedPinIndexSet` — verify slideshow starts when `selectedPinIndex != null`
  - `onSurpriseMe_derivesTopThreeVibes` — mock `latestSavedPois` with 5 entries (mixed vibes), verify context `tasteProfile` contains correct top 3 by frequency
  - `animatePoiCounter_incrementsToTargetCount` — verify `poiStreamingCount` reaches `allDiscoveredPois.size` after delay
  - `schedulePostSearchSlideshow_startsSlideshow` — verify `autoSlideshowIndex` becomes non-null after `IDLE_THRESHOLD_MS`
  - **M5 fix**: `onSurpriseMe_doesNothingWhenNoSaves` — call `onSurpriseMe()` with `latestSavedPois = emptyList()`, verify `isSearchingArea` stays `false` and no `areaFetchJob` is launched
  - **M5 also add**: `onSurpriseMe_doesNothingWhenSavesHaveNoVibe` — call `onSurpriseMe()` with saves that have blank vibe fields, verify same no-op behaviour (covers L3 condition)

### Notes

- **Batch system preserved**: `poiBatches`, `activeBatchIndex`, `showAllMode` remain in `MapUiState.Ready` — the background enrichment system still uses them. Only the UI surface (batch nav arrows) is removed.
- **Dots active index**: `visibleIndex = listState.firstVisibleItemIndex` — this is the carousel's local scroll state. It doesn't reset when `allDiscoveredPois` grows. If new POIs land while carousel is at card 3, dot 3 stays active. Correct behaviour.
- **"Search this area" vs initial load**: `schedulePostSearchSlideshow()` is only called from the `onSearchThisArea` path, not initial GPS load (which already has idle detection working correctly).
- **iOS parity**: All changes are in `commonMain`. No `iosMain` / `androidMain` changes needed. MapLibre camera zoom API is wrapped in `MapComposable.kt` which already handles both platforms.
