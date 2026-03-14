---
title: '3-Pin Progressive Discovery'
slug: '3-pin-progressive-discovery'
created: '2026-03-13'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
adversarial_fixes_applied: 'F1,F2,F3,F4,F5,F9,F10,FA,FB,FC,FD,FE,FF,FG,FH,FI,FJ,FK,FL,FM'
adversarial_fixes_date: '2026-03-13'
tech_stack:
  - 'Kotlin Multiplatform / Compose Multiplatform (commonMain)'
  - 'Ktor (HTTP/SSE streaming)'
  - 'Koin (DI)'
  - 'SQLDelight (multiplatform persistence)'
  - 'kotlinx.coroutines + Flow / channelFlow'
  - 'ViewModel + StateFlow (Jetpack lifecycle-viewmodel)'
files_to_modify:
  - 'GeminiPromptBuilder.kt'
  - 'GeminiAreaIntelligenceProvider.kt'
  - 'BucketUpdate.kt'
  - 'MapUiState.kt'
  - 'MapViewModel.kt'
  - 'MapScreen.kt'
  - 'GeocodingSearchBar.kt'
  - 'ExpandablePoiCard.kt'
code_patterns:
  - 'channelFlow + launch for concurrent Stage 1 / Stage 2 — extend same pattern for background batches'
  - 'CompletableDeferred<Stage1Result> to coordinate stage timing'
  - 'BucketUpdate sealed class — add BackgroundBatchReady, BackgroundEnrichmentComplete, and data object BackgroundFetchComplete variants'
  - 'MutableStateFlow<MapUiState> + copy() pattern for state updates'
  - 'cancelAreaFetch() cleans up all area-related jobs — no backgroundFetchJob needed; channelFlow scope cancels background batches automatically when fetch job is cancelled'
  - 'PlatformBackHandler in priority order — last composed = highest priority'
  - 'MapFloatingUiDark color for all floating surfaces'
  - 'Color(0xFFFFD700) for gold saved-pin indicator (already used in SavesNearbyPill)'
test_patterns:
  - 'UnconfinedTestDispatcher + runTest for VM coroutine tests'
  - 'FakeAreaRepository / FakeSavedPoiRepository fakes wired via createViewModel()'
  - 'advanceTimeBy() for debounce / delay testing'
  - 'flow { emit(...) } lambdas on fake repos to simulate update sequences'
---

# Tech-Spec: 3-Pin Progressive Discovery

**Created:** 2026-03-13

## Overview

### Problem Statement

Stage 1 currently requests 8 POIs from Gemini, causing 15–30s+ load times (Bug #30 — worse since Dynamic Vibes v1). Users see a wall of pins with no way to progressively explore. First paint is slow, and there is no engagement loop once pins load.

### Solution

Reduce Stage 1 to exactly 3 curated hero POIs for fast first paint (<3s target). Immediately after initial pins drop, a silent background pipeline pre-fetches 2 more batches of 3 POIs. Refresh reveals the next 3 from the pre-fetched queue instantly (no network wait). Batch navigation (◀ 1/4 ▶) in the search bar allows forward and backward navigation through all batches. Stage 2 enrichment runs for all POIs as they arrive. Vibe filter applied client-side from the queue.

### Scope

**In Scope:**
- Stage 1 prompt change: 8 → 3 curated hero POIs
- Background fetch pipeline: 2 silent calls of 3 each after initial drop, dedup prompt ("Do NOT include: [name1, name2, name3]")
- Stage 2 enrichment for all batches as they arrive
- Refresh button → reveals next 3 from pre-fetched queue; if queue exhausted → "Search deeper..." triggers fresh Gemini call
- Batch navigation in search bar (◀ 1/4 ▶) — covers both forward and back through all batches (all batches held in memory)
- Floating per-pin cards on map (3 visible at a time — name, why special, vibe tag, rating, distance)
- Expand-in-place detail (80% viewport) — replaces bottom sheet as primary detail entry point; includes save, share, directions, ask AI
- Swipe between current batch of 3 in detail view
- "Show All" mini-pin mode on last nav page — all discovered pins as mini markers, tap any to jump to its batch
- Saved pins persist across refreshes with gold marker; refresh cycles only unsaved pins
- Vibe filter applied client-side from queue: shows next 3 matching active vibe; if queue exhausted for that vibe → "Search deeper..."

**Out of Scope:**
- Refresh counter badge "12 more places to discover" (#78 — deferred)
- Camera gentle reframe on refresh (#87 — deferred)
- Empty state confetti "You've explored it all" (#89 — deferred)

## Context for Development

### Codebase Patterns

- KMP (Kotlin Multiplatform) with Compose Multiplatform — all UI in `commonMain`
- Stage 1 uses `channelFlow { launch { ... } }` in `GeminiAreaIntelligenceProvider`. Two concurrent coroutines (Stage 1 + Stage 2) coordinated via `CompletableDeferred<Stage1Result>`. Background batches extend this same pattern — a third `launch {}` block fires immediately after `stage1Deferred.complete()`.
- `BucketUpdate` is a sealed class in `domain/model/`. Each new event type is a `data class` subtype. Handler in `MapViewModel` is a `when(update)` block — new variant must be added there.
- `MapUiState.Ready` is a large `data class` with all state as copy-updated fields. No sub-objects; all top-level fields. Pattern: `_uiState.value = current.copy(field = newValue)`.
- `GeocodingSearchBar` renders one of 4 `@Composable` private states (`IdleState`, `ActiveState`, `SelectedState`, `SpinningState`) based on flags. Add `BatchNavState` as a new private composable for the ◀ 1/4 ▶ view; add `showBatchNav: Boolean` and `batchIndex/batchTotal/onPrev/onNext/onSearchDeeper` params.
- `ExpandablePoiCard` is currently `fillMaxWidth(0.9f)` and aligned `Alignment.Center`. For expand-in-place mode it needs `fillMaxWidth(0.92f)` + `fillMaxHeight(0.80f)` + vertical scroll. Extend with a `fullscreen: Boolean` flag (default false) OR create new `ExpandInPlacePoiDetail` composable.
- All floating surfaces use `MapFloatingUiDark.copy(alpha = 0.90f)` or `0.97f`. Gold accent: `Color(0xFFFFD700)`.
- `PlatformBackHandler(enabled = <condition>) { <dismiss> }` — last-composed = highest priority. In `ReadyContent`, the final `PlatformBackHandler` is for chat. Expand-in-place detail handler must be composed BEFORE chat handler.
- Tests use `UnconfinedTestDispatcher`, `FakeAreaRepository`, `FakeSavedPoiRepository`. Pattern: `flow { emit(BucketUpdate.VibesReady(...)) }` in fake repo. Assert via `uiState.value as MapUiState.Ready`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | `buildPinOnlyPrompt()` line 18 — change "8" → "3"; add `buildBackgroundBatchPrompt(areaName, excludeNames, vibeLabels)` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt` | `streamAreaPortrait()` — add third `launch {}` for background batches after `stage1Deferred.complete()` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt` | Add `BackgroundBatchReady(pois: List<POI>, batchIndex: Int)` sealed subclass |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | Add `poiBatches`, `activeBatchIndex`, `isBackgroundFetching`, `showAllMode` fields to `Ready` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | Add `poiBatchesCache`, batch nav methods, vibe-filtered visible pois logic; handle `BackgroundBatchReady`, `BackgroundEnrichmentComplete`, `BackgroundFetchComplete` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Wire `FloatingPoiCard` overlay, expand-in-place detail, batch nav in search bar, show-all mode |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/GeocodingSearchBar.kt` | Add `BatchNavState` composable + params; `IdleState` placeholder text update |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt` | Add `fullscreen: Boolean = false` param for 80% viewport mode + swipe-between-3 pager |
| `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt` | Add batch nav, background fetch, vibe filter, saved pin persistence tests |
| `_bmad-output/brainstorming/brainstorming-session-2026-03-13-002.md` | Ideas #73–#93 — locked decisions and prototype references |

**New files to create:**
| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FloatingPoiCard.kt` | Compact per-pin floating card (name, vibe icon, rating, distance) rendered above each of the 3 pins |

### Technical Decisions

- **Background fetch trigger**: Immediately after `stage1Deferred.complete()` inside `streamAreaPortrait()` — launches 2 sequential batch fetches (batch 1 then batch 2); each emits `BucketUpdate.BackgroundBatchReady(pois, batchIndex)`
- **Background batch size**: 2 calls × 3 POIs = 6 background POIs; total pool up to 9 (3 initial + 6 background)
- **Dedup strategy**: `buildBackgroundBatchPrompt()` includes `"Do NOT include: ${excludeNames.joinToString(", ")}"` — prompt-level exclusion (#77); batch 2 excludes initial 3 + batch 1 names
- **Stage 2 enrichment**: `buildDynamicVibeEnrichmentPrompt()` already runs after Stage 1. For background batches, each `BackgroundBatchReady` event triggers an additional Stage 2 enrichment call using the same vibe labels from Stage 1.
- **Vibe filter**: `MapViewModel` computes `visiblePois` from `poiBatches[activeBatchIndex]` filtered by `activeDynamicVibe?.label`. No Gemini calls. `onNextBatch()` always increments one slot regardless of vibe — it never auto-skips empty-vibe batches. If the active vibe yields 0 matching POIs in a batch, the map renders 0 pins and the floating card strip is empty. When all batches are exhausted (user is on Show All page or `onNextBatch()` is blocked at max index) and no matches remain, the search bar replaces the ▶ arrow with "Search deeper…". This is the only trigger for "Search deeper…" from a vibe-filtered state. (Decision: simple 1-step nav is predictable; silent auto-advance creates confusion about which batch the user is on.)
- **Batch navigation**: `poiBatches: List<List<POI>>` in state. `activeBatchIndex` tracks current. `◀` decrements (min 0), `▶` increments (max = `MAX_BATCH_SLOTS - 1`, where that last slot = Show All mode). `GeocodingSearchBar` shows `BatchNavState` when `poiBatches.size > 1 && !isSearchingArea`. `batchTotal` passed to search bar is always `MAX_BATCH_SLOTS = 4` (fixed denominator — avoids "1/2 → 1/3 → 1/4" flicker as background batches arrive). ▶ is disabled when the next slot's batch has not yet arrived in `poiBatches`.
- **Show All mode**: `showAllMode = (activeBatchIndex == poiBatches.size)`. All accumulated POIs rendered as mini markers (no floating cards). `MapComposable` gets the full flattened list.
- **Saved pins**: Always rendered with gold border marker regardless of active batch. `MapComposable` already receives `savedPois` separately from `pois`. Refresh cycles only non-saved pins in current batch view.
- **Expand-in-place detail**: `ExpandablePoiCard` with `fullscreen = true` → `fillMaxWidth(0.92f)` + `fillMaxHeight(0.80f)` + `HorizontalPager` for swipe-between-3 (Compose Foundation Pager). `PlatformBackHandler` enabled when expanded. Dismiss: back button, tap scrim, or close button.
- **`confidenceBlock` in chat**: With 3 initial POIs, count < 6 → "LOW CONFIDENCE" chat block. Background batch enrichment will populate more POIs over time — chat confidence improves as batches arrive. No change needed to `buildChatSystemContext(poiCount)` — it already reads from live POI list.

## Implementation Plan

### Tasks

Tasks are ordered by dependency — lowest level first.

---

- [x] **Task 1: GeminiPromptBuilder — reduce Stage 1 count + add background batch prompt**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action 1: In `buildPinOnlyPrompt()` (line 18), change `"pois: 8 best POIs"` to `"pois: 3 best POIs"` — the 3 most curated, confident picks. Update vibe threshold rule: `"ONLY return vibes where at least 2 of the 3 POIs will be tagged"` (was 3-of-8).
  - Action 2: Add method `buildBackgroundBatchPrompt(areaName: String, excludeNames: List<String>, vibeLabels: List<String>): String`. Returns 3 more POIs using exact Stage 1 vibe labels. Inclusion of: `"Do NOT include any of these places: ${excludeNames.joinToString(", ")}"`. Same pois-only JSON schema as `buildPinOnlyPrompt` (no vibes field needed).

---

- [x] **Task 2: BucketUpdate — add BackgroundBatchReady, BackgroundEnrichmentComplete, BackgroundFetchComplete variants**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt`
  - Action: Add at the bottom of the sealed class:
    ```kotlin
    data class BackgroundBatchReady(
        val pois: List<POI>,
        val batchIndex: Int,  // 1-based: 1 = first bg batch, 2 = second
    ) : BucketUpdate()

    /** Enrichment for a background batch — distinct from PortraitComplete to avoid
     *  merging against the wrong POI base. batchIndex is 1-based. */
    data class BackgroundEnrichmentComplete(
        val pois: List<POI>,
        val batchIndex: Int,
    ) : BucketUpdate()

    /** Emitted (in a finally block) after the background batch pipeline finishes or
     *  fails. Guarantees isBackgroundFetching is cleared even on silent errors.
     *  Use `data object` (Kotlin 1.9+) to match sealed class convention and enable
     *  value-equality in tests. */
    data object BackgroundFetchComplete : BucketUpdate()
    ```

---

- [x] **Task 3: GeminiAreaIntelligenceProvider — add background fetch pipeline**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt`
  - Action: Inside `streamAreaPortrait()`, add a **third** `launch {}` block after the existing Stage 2 `launch {}`:
    ```
    launch {
        try {
            val stage1Result = stage1Deferred.await()   // throws on Stage 1 failure — bail silently
            val stage1Names = stage1Result.pois.map { it.name }
            val stage1VibeLabels = stage1Result.vibeLabels

            // Batch 1
            ensureActive()
            val batch1Pois = streamAndParse(buildBackgroundBatchPrompt(areaName, stage1Names, stage1VibeLabels))
            send(BucketUpdate.BackgroundBatchReady(batch1Pois, batchIndex = 1))
            ensureActive()
            val enriched1 = streamAndParse(buildDynamicVibeEnrichmentPrompt(areaName, stage1VibeLabels, batch1Pois.map { it.name }))
            send(BucketUpdate.BackgroundEnrichmentComplete(enriched1, batchIndex = 1))

            // Batch 2
            ensureActive()
            val batch2Pois = streamAndParse(buildBackgroundBatchPrompt(areaName, stage1Names + batch1Pois.map { it.name }, stage1VibeLabels))
            send(BucketUpdate.BackgroundBatchReady(batch2Pois, batchIndex = 2))
            ensureActive()
            val enriched2 = streamAndParse(buildDynamicVibeEnrichmentPrompt(areaName, stage1VibeLabels, batch2Pois.map { it.name }))
            send(BucketUpdate.BackgroundEnrichmentComplete(enriched2, batchIndex = 2))

        } catch (e: CancellationException) {
            throw e   // propagate cancellation — never swallow
        } catch (e: Exception) {
            log.w("Background batch failed silently", e)
        } finally {
            // MUST use trySend, not send. If the channelFlow scope was cancelled (area changed),
            // send() inside finally throws CancellationException, swallowing the event and leaving
            // isBackgroundFetching = true permanently. trySend is non-throwing.
            trySend(BucketUpdate.BackgroundFetchComplete)
        }
    }
    ```
  - `streamAndParse(prompt)` is **not** a pre-existing function. Create a private suspend helper inside `GeminiAreaIntelligenceProvider` that wraps the existing Ktor SSE call + `responseParser.parseStage1Response()` (same JSON schema used by Stage 1). Signature: `private suspend fun streamAndParse(prompt: String): List<POI>`. Do NOT invent a new HTTP client — reuse the existing Ktor `httpClient` and SSE streaming pattern from `streamAreaPortrait()`.
  - `ensureActive()` before every network call ensures prompt cooperative cancellation when the user navigates to a new area — prevents stale SSE responses emitting into a new search.
  - Use `BackgroundEnrichmentComplete` (not `PortraitComplete`) for background enrichment to avoid merging against the wrong POI base in the ViewModel.
  - Notes: Third `launch {}` is a child of the `channelFlow` scope. It is automatically cancelled when the ViewModel cancels the flow collection job — no separate `backgroundFetchJob` tracking required.

---

- [x] **Task 4: MapUiState — add batch queue fields**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Add to `MapUiState.Ready`:
    ```kotlin
    val poiBatches: List<List<POI>> = emptyList(),     // 0 = initial 3, 1 = bg batch 1, 2 = bg batch 2
    val allDiscoveredPois: List<POI> = emptyList(),     // flat list (for chat + vibe counts + Show All)
    val activeBatchIndex: Int = 0,
    val isBackgroundFetching: Boolean = false,
    val showAllMode: Boolean = false,
    ```
  - Notes: Existing `pois` field remains as "currently rendered pins" (current batch filtered by vibe). No existing fields removed.

---

- [x] **Task 5: MapViewModel — batch queue handling, navigation, vibe filter**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action 1 — new private vars: `poiBatchesCache: MutableList<List<POI>>`. *(No `backgroundFetchJob` — the channelFlow scope handles cancellation. No `stage1VibeLabelsCache` — vibe labels are consumed inside the provider and not needed in the ViewModel.)*
  - Action 2 — extend `cancelAreaFetch()`: `poiBatchesCache.clear()`. The main `fetchJob` (which collects `streamAreaPortrait()`) cancels the entire channelFlow scope including background batches — no extra job to cancel.
  - Action 3 — handle `BucketUpdate.BackgroundBatchReady`: **guard — if `update.pois.isEmpty()` skip the append entirely** (Gemini may return 0 POIs if all candidates were excluded by the dedup prompt; appending an empty batch creates a blank navigation slot). If non-empty: append to `poiBatchesCache`; compute `allPois = poiBatchesCache.flatten()`; update `poiBatches`, `allDiscoveredPois`, `isBackgroundFetching = (update.batchIndex < 2)` *(note: `BackgroundFetchComplete` is the reliable terminal — this is a progress hint only)*, recompute `dynamicVibePoiCounts` from `allPois`.
  - Action 3b — handle `BucketUpdate.BackgroundFetchComplete`: set `isBackgroundFetching = false`. This fires even on silent failures and is the guaranteed terminal event.
  - Action 3c — handle `BucketUpdate.BackgroundEnrichmentComplete(pois, batchIndex)`: merge enriched POIs into `poiBatchesCache[batchIndex]` by replacing POIs where `enriched.name == existing.name`; update `poiBatches`, `allDiscoveredPois`. Do NOT touch `poiBatchesCache[0]` (Stage 1) — those are enriched via the existing `PortraitComplete` path. **Risk**: Gemini may return slightly different name casing/punctuation between Stage 1 and enrichment (e.g. "Dukes Surf Shop" vs "Duke's Surf Shop"), causing the name match to miss and the enriched POI to be silently orphaned. Mitigation: design `buildDynamicVibeEnrichmentPrompt()` to echo back the exact Stage 1 names verbatim as the POI key (e.g. include "Use exactly this name: ${stage1Name}" in the prompt). If orphaning is still observed during smoke testing, fall back to lat/lng proximity match (within 0.0001°) as the merge key.
  - Action 4 — update `VibesReady`/`PinsReady` handler: after setting `pois`, also set `poiBatchesCache = mutableListOf(pois)`, `poiBatches = listOf(pois)`, `allDiscoveredPois = pois`, `activeBatchIndex = 0`, `isBackgroundFetching = true`, `showAllMode = false`.
  - Action 5 — add `fun onNextBatch()`: if `showAllMode` no-op; if `activeBatchIndex + 1 < poiBatchesCache.size` → `val newIndex = activeBatchIndex + 1; val visible = computeVisiblePois(poiBatchesCache[newIndex], current.activeDynamicVibe); _uiState.value = current.copy(activeBatchIndex = newIndex, pois = visible)`; else → `_uiState.value = current.copy(showAllMode = true, pois = current.allDiscoveredPois)`.
  - Action 6 — add `fun onPrevBatch()`: if `showAllMode` → `val lastIdx = poiBatchesCache.size - 1; _uiState.value = current.copy(showAllMode = false, activeBatchIndex = lastIdx, pois = computeVisiblePois(poiBatchesCache[lastIdx], current.activeDynamicVibe))`; else if `activeBatchIndex > 0` → `val newIndex = activeBatchIndex - 1; _uiState.value = current.copy(activeBatchIndex = newIndex, pois = computeVisiblePois(poiBatchesCache[newIndex], current.activeDynamicVibe))`.
  - Action 7 — add `fun onSearchDeeper()`: reset state fields first (`_uiState.value = current.copy(activeBatchIndex = 0, showAllMode = false, isBackgroundFetching = false, poiBatches = emptyList(), allDiscoveredPois = emptyList())`); clear `poiBatchesCache`; then call `retryAreaFetch()`.
  - Action 8 — add `private fun computeVisiblePois(batch: List<POI>, activeVibe: DynamicVibe?): List<POI>`: returns `batch` unfiltered if vibe null; else `batch.filter { poi -> activeVibe.label in poi.vibes }`. *(Use only `poi.vibes: List<String>` — do NOT use bare `== poi.vibe` which fails when `vibe` is a comma-separated string. Ensure enrichment always populates `poi.vibes` as a proper list.)*
  - Action 9 — update `switchDynamicVibe()`: recompute `pois` using `computeVisiblePois(poiBatchesCache[activeBatchIndex], newVibe)`.
  - **F9 — pois field semantics note**: `pois` in `MapUiState.Ready` now means *visible rendered pins* (current batch, vibe-filtered). Any existing code reading `state.pois` for purposes other than map rendering must migrate: `savedNearbyCount` → use `state.allDiscoveredPois`; `POIListView` → use `state.allDiscoveredPois`; chat context → already handled in Task 9 Action 5. Flag all usages in MapScreen and dependent ViewModels during implementation.

---

- [x] **Task 6: FloatingPoiCard — create new component**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FloatingPoiCard.kt` *(new)*
  - Action: `@Composable fun FloatingPoiCard(poi: POI, isSaved: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier)`.
    - Root: `Box(modifier.clickable { onTap() }.clip(RoundedCornerShape(12.dp)).background(MapFloatingUiDark.copy(alpha = 0.94f)).border(1.dp, if (isSaved) Color(0xFFFFD700).copy(0.6f) else Color.White.copy(0.08f), RoundedCornerShape(12.dp)).padding(12.dp, 10.dp))`.
    - `Column`: vibe label (labelSmall, `Color.White.copy(alpha = 0.6f)`, 1 line); name (titleSmall, white, max 2 lines); if `poi.rating != null`: `"★ ${poi.rating}"` (labelSmall, `Color(0xFFFFD700)`).
    - If saved: `Icon(Icons.Default.Bookmark, 10.dp, Color(0xFFFFD700))` in `Alignment.TopEnd` of the Box.
  - Notes: Rendered in a bottom-strip `Row` in `MapScreen` — not anchored to map pin screen coordinates.

---

- [x] **Task 7: ExpandablePoiCard — fullscreen mode + swipe pager**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt`
  - Action: Add params (all defaulted — zero impact on existing callers):
    ```kotlin
    fullscreen: Boolean = false,
    siblingPois: List<POI> = emptyList(),
    siblingIndex: Int = 0,
    onSiblingSelected: (Int) -> Unit = {},
    siblingIsSaved: (POI) -> Boolean = { false },
    ```
  - When `fullscreen = true`: root `Column` modifier uses `fillMaxWidth(0.92f).fillMaxHeight(0.80f)` instead of `fillMaxWidth(0.9f)`.
  - When `siblingPois.size > 1`: wrap card body content in `HorizontalPager(pageCount = siblingPois.size, state = pagerState)` (pagerState = `rememberPagerState(initialPage = siblingIndex)`). Each page renders full card content for `siblingPois[page]`. **Do NOT use `LaunchedEffect(pagerState.currentPage)` — it fires on initial composition, calling `onSiblingSelected(initialPage)` spuriously and triggering any side effects (camera, analytics) on every card open.** Instead use `LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.drop(1).collect { page -> onSiblingSelected(page) } }` — `drop(1)` skips the initial emission, firing only on actual user swipes. Add row of 5dp dot indicators below image header (filled white for current page, alpha 0.3f others); hide dots when `siblingPois.size == 1`.
  - Import: `androidx.compose.foundation.pager.HorizontalPager`, `rememberPagerState` — no new library.

---

- [x] **Task 8: GeocodingSearchBar — add BatchNavState**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/GeocodingSearchBar.kt`
  - Action 1 — add params to `GeocodingSearchBar` (all defaulted): `showBatchNav: Boolean = false`, `batchIndex: Int = 0`, `batchTotal: Int = 0`, `onPrevBatch: () -> Unit = {}`, `onNextBatch: () -> Unit = {}`, `onSearchDeeper: () -> Unit = {}`.
  - Action 2 — add branch before `else -> IdleState(...)`: `showBatchNav && !spinning && selectedPlace == null && !active -> BatchNavState(batchIndex, batchTotal, onPrevBatch, onNextBatch, onSearchDeeper)`.
  - Action 3 — add `@Composable private fun BatchNavState(...)`: `Surface(RoundedCornerShape(50), MapFloatingUiDark.copy(0.90f), fillMaxWidth().padding(horizontal = 16.dp))`. Column with: Row(`◀` IconButton disabled+dimmed at index 0; `Text("${batchIndex+1} / $batchTotal")` or `"All"` when on last slot; `▶` IconButton disabled+dimmed at last slot). Below row: `TextButton("Search deeper…", onSearchDeeper)` in `MaterialTheme.colorScheme.primary` only when `batchIndex == batchTotal - 1`.
  - Action 4 — update `IdleState` placeholder text to `"Search a place…"`.

---

- [x] **Task 9: MapScreen — wire all new components**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action 1 — **Floating card strip**: Add inside `Box` (visible when `!showListView && !showAllMode && pois.isNotEmpty() && selectedPoi == null`):
    ```kotlin
    Row(
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
  - Action 2 — **Fullscreen detail**: Update `ExpandablePoiCard` call: add `fullscreen = true`, `siblingPois = if (state.showAllMode) emptyList() else state.pois.take(3)`, `siblingIndex = state.pois.take(3).indexOfFirst { it.name == state.selectedPoi?.name }.coerceAtLeast(0)`, `onSiblingSelected = { idx -> state.pois.getOrNull(idx)?.let { viewModel.selectPoi(it) } }`, `siblingIsSaved = { p -> p.savedId in state.savedPoiIds }`.
  - Action 3 — **Batch nav**: Update `GeocodingSearchBar` call: add `showBatchNav = state.poiBatches.size > 1 && !state.isSearchingArea`, `batchIndex = if (state.showAllMode) state.poiBatches.size else state.activeBatchIndex`, `batchTotal = MAX_BATCH_SLOTS` (constant = 4: 3 POI batches + 1 Show All slot), `onPrevBatch = { viewModel.onPrevBatch() }`, `onNextBatch = { viewModel.onNextBatch() }`, `onSearchDeeper = { viewModel.onSearchDeeper() }`. **Why MAX_BATCH_SLOTS**: using `state.poiBatches.size + 1` causes the denominator to increment live ("1/2 → 1/3 → 1/4") while the user watches, which is disorienting. A fixed denominator of 4 is shown immediately once nav appears — slots beyond the loaded count simply have ▶ disabled until the batch arrives. Define `private const val MAX_BATCH_SLOTS = 4` in `MapViewModel` (or a companion object constant).
  - Action 4 — **Show All map**: In `MapComposable` call, change pois arg to: `if (state.savedVibeFilter) emptyList() else if (state.showAllMode) state.allDiscoveredPois else state.pois`.
  - Action 5 — **Chat context**: Change all 3 `chatViewModel.openChat(state.areaName, state.pois, ...)` → `state.allDiscoveredPois`.
  - Action 6 — **Back handler for Show All**: Add before `PlatformBackHandler(enabled = state.selectedPoi != null)`:
    ```kotlin
    PlatformBackHandler(enabled = state.showAllMode && state.selectedPoi == null) {
        viewModel.onPrevBatch()
    }
    ```

---

- [x] **Task 10: MapViewModelTest — add batch pipeline tests**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt`
  - Action: Add 10 test cases:
    1. **`backgroundFetchSetsIsBackgroundFetching`**: Emit `VibesReady(vibes, 3 pois)` → assert `isBackgroundFetching == true`. Emit `BackgroundBatchReady(pois, 1)` → assert `poiBatches.size == 2`, `isBackgroundFetching == true`. Emit `BackgroundBatchReady(pois, 2)` → assert `poiBatches.size == 3`, `isBackgroundFetching == true` (still true — batch arrival is NOT the terminal). Emit `BackgroundFetchComplete` → assert `isBackgroundFetching == false`.
    2. **`batchNavForwardEntersShowAllAtEnd`**: Set `poiBatches = [b0, b1, b2]`, `activeBatchIndex = 2`. Call `onNextBatch()`. Assert `showAllMode == true` and `pois == allDiscoveredPois`.
    3. **`vibeFilterComputedClientSideFromCurrentBatch`**: Batch 0 = [Nightlife POI, Nightlife POI, History POI]. Call `switchDynamicVibe(nightlifeVibe)`. Assert `pois.size == 2`.
    4. **`savedPinsPersistedAcrossBatchNavigation`**: Emit initial 3 pins. Save pin A. Navigate to batch 1 via `onNextBatch()`. Assert `savedPoiIds` still contains pin A's id.
    5. **`backgroundFetchCompleteAlwaysClearsFlag`**: Emit `VibesReady` → assert `isBackgroundFetching == true`. Emit `BackgroundFetchComplete` directly (no batch events — simulates silent failure). Assert `isBackgroundFetching == false`.
    6. **`onSearchDeeperResetsAllBatchState`**: Populate 3 batches, navigate to batch 2. Call `onSearchDeeper()`. Assert `activeBatchIndex == 0`, `showAllMode == false`, `poiBatches.isEmpty()`, `allDiscoveredPois.isEmpty()`, `isBackgroundFetching == false`.
    7. **`backgroundEnrichmentMergesIntoCorrectBatch`**: Emit `VibesReady(3 pois)`. Emit `BackgroundBatchReady([poiX, poiY, poiZ], batchIndex = 1)`. Emit `BackgroundEnrichmentComplete([enrichedX (same name, new rating)], batchIndex = 1)`. Assert `poiBatches[1].first { it.name == poiX.name }.rating == enrichedX.rating`. Assert `poiBatches[0]` (Stage 1 batch) is untouched.
    8. **`emptyBackgroundBatchIsSkipped`**: Emit `VibesReady(3 pois)`. Emit `BackgroundBatchReady(emptyList(), batchIndex = 1)`. Assert `poiBatches.size == 1` (only initial batch; empty batch was not appended).
    9. **`onNextBatchWritesPoisToState`**: Set `poiBatches = [b0, b1]`, `activeBatchIndex = 0`. Call `onNextBatch()`. Assert `state.activeBatchIndex == 1` and `state.pois == computeVisiblePois(b1, null)` (not an empty list, not b0).
    10. **`vibeFilterEmptyBatchDoesNotAutoAdvance`**: Batch 0 = [History POI × 3]. Batch 1 = [Nightlife POI × 3]. `activeBatchIndex = 0`. Call `switchDynamicVibe(nightlifeVibe)`. Assert `pois.isEmpty()` and `activeBatchIndex == 0` (no auto-advance; user must manually tap ▶).

### Acceptance Criteria

- [ ] **AC 1**: Given the user searches an area, when Stage 1 returns, then exactly 3 POI pins drop on the map and first paint completes in under 3 seconds (pass threshold) / under 5 seconds (acceptable); fail if over 5 seconds.
- [ ] **AC 2**: Given Stage 1 has returned 3 pins, when the user has not interacted, then `isBackgroundFetching = true` immediately and 2 background fetches complete silently, populating `poiBatches` to size 3.
- [ ] **AC 3**: Given `poiBatches.size > 1`, when the search bar is idle, then batch navigation controls (◀ 2/4 ▶) are visible in the search bar.
- [ ] **AC 4**: Given batch nav shows "1/4", when user taps ▶, then map instantly transitions to next 3 pins, nav updates to "2/4", no network call fires.
- [ ] **AC 5**: Given batch nav shows "2/4", when user taps ◀, then map returns to first batch and nav shows "1/4".
- [ ] **AC 6**: Given user is on last batch, when user taps ▶, then Show All mode activates: all discovered pins render as markers, floating card strip disappears, nav shows "All".
- [ ] **AC 7**: Given Show All mode is active, when user presses Android back button, then Show All exits and last batch's 3 cards are restored.
- [ ] **AC 8**: Given active vibe is "Nightlife" and current batch has 2 Nightlife + 1 History POI, when batch is displayed, then only 2 pins and 2 floating cards are visible.
- [ ] **AC 9**: Given vibe filter is active and no remaining batches contain matching POIs, when user taps ▶, then "Search deeper…" appears in search bar and no batch transition occurs.
- [ ] **AC 10**: Given user saved a POI from batch 1, when navigating to batch 2, then the saved POI remains on map with gold border marker alongside the 3 new pins.
- [ ] **AC 11**: Given 3 pins are visible in normal mode, then a bottom strip of 3 compact floating cards shows: name, vibe label, and rating (if available).
- [ ] **AC 12**: Given 3 floating cards are visible, when user taps a card, then it expands to ~80% viewport showing image header, insight text, and save/share/directions/ask AI chips.
- [ ] **AC 13**: Given expand-in-place detail is open, when user swipes horizontally, then detail transitions to next/prev POI in current batch, dot indicators update.
- [ ] **AC 14**: Given expand-in-place detail is open, when user presses Android back button or taps scrim, then detail collapses and 3-card strip returns.
- [ ] **AC 15**: Given Stage 2 enrichment completes for a background batch, when user navigates to that batch, then floating cards show enriched insight text and rating.
- [ ] **AC 16**: Given background batch 2 prompt is built with batch 1 names as exclusions, then none of batch 1's POI names appear in the batch 2 response.
- [ ] **AC 17**: Given a background batch fetch fails, when the error occurs, then no error is shown to the user and batch nav shows only successfully loaded batches.
- [ ] **AC 18**: Given "Search deeper…" is tapped, when fresh fetch completes, then batch state resets to "1/4" with a new set of initial 3 pins.

## Additional Context

### Dependencies

- **Compose Foundation Pager**: `HorizontalPager` + `rememberPagerState` — no new library needed; already in `compose-foundation` (transitive dep of `compose-material3`). Verify import: `androidx.compose.foundation.pager.*`.
- **No new network APIs or Koin module changes** required.
- **Bug closed**: Bug #30 (Stage 1 pin speed). After verifying 3-pin Stage 1 consistently completes under 10s, reduce the 60s socket timeout band-aid back to 30s.

### Testing Strategy

**Unit tests (MapViewModelTest.kt):** 4 new test cases — see Task 10.

**Unit tests (GeminiPromptBuilderTest.kt — create if not exists):**
- Assert `buildPinOnlyPrompt()` output contains `"3"` and not `"8"`.
- Assert `buildBackgroundBatchPrompt(excludeNames = listOf("Duke's"))` output contains `"Do NOT include"` and `"Duke's"`.

**Manual smoke test checklist:**
1. Search "Shoreditch, London" → verify 3 pins drop in <5s.
2. Wait ~5s → verify `◀ 2/4 ▶` appears in search bar.
3. Tap ▶ → instant transition to 3 new pins, no spinner, nav "2/4".
4. Tap ▶ twice more → Show All mode, all mini pins, no card strip.
5. Android back from Show All → returns to last batch with card strip.
6. Save a pin from batch 1, navigate to batch 2 → gold pin still on map.
7. Select vibe → only matching pins in current batch show cards.
8. Tap a floating card → 80% viewport detail with action chips.
9. Swipe in detail → transitions to sibling POI, dots update.
10. Tap "Search deeper…" → fresh fetch, resets to "1/4".

### Notes

- **Floating card positioning**: Cards render in a fixed Compose `Row` at the bottom — NOT anchored to map pin screen coordinates. Pin-anchored cards require `MapComposable.toScreenLocation(LatLng)` on every camera move (complex + fragile). Fixed strip is stable and matches the prototype's side-by-side layout.
- **Vibe count risk**: `dynamicVibePoiCounts` MUST be recomputed from `allDiscoveredPois` after each background batch (Task 5 Action 3). Computing from only the current batch causes incorrect VibeRail chip counts.
- **GPS cache regression (known, accepted)**: `gpsAreaPoisCache` currently stores the full 8-POI list. After this change it stores only the initial 3. On a cache hit, background fetch should re-fire to repopulate batches 1 and 2. If background calls fail (offline, poor connectivity), the user is capped at 3 POIs with batch nav hidden — a worse offline experience than the previous 8-POI cache. This is a genuine regression from the prior behaviour, accepted as a known trade-off for v1. Before v1.1, either: (a) cache all 3 batches as a unit once complete, or (b) remove GPS cache gating for background fetches. Track as Bug #32.
- **iOS pager**: `HorizontalPager` is fully multiplatform via Compose Multiplatform — no expect/actual needed.
- **`savedId` check**: `FloatingPoiCard` uses `poi.savedId in state.savedPoiIds` (same pattern as `MapScreen` line 331) — not `poi.savedId.isNotEmpty()`.

