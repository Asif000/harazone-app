---
title: 'Saves Awareness'
slug: 'saves-awareness'
created: '2026-03-08'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin DI', 'MapLibre Android (SymbolManager)', 'MapLibre iOS (MLNPointAnnotation)', 'Android Canvas/Paint for bitmap icons', 'Kotlin Coroutines + Flow', 'kotlin.test + Turbine']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatEntryPoint.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/ChatViewModelTest.kt'
code_patterns:
  - 'ChatEntryPoint sealed class in commonMain alongside ChatViewModel'
  - 'latestSavedPois pattern (same as MapViewModel.latestRecents) for sync access in openChat()'
  - 'ensureIcon(vibe, poiType, isSaved) — add isSaved param, draw gold stroke + badge conditionally'
  - 'savedPoiIds threaded from MapUiState.Ready down through POIListView to PoiListCard'
  - 'Saves pill as AnimatedVisibility Box overlay in ReadyContent, Alignment.BottomCenter'
test_patterns:
  - 'commonTest with kotlin.test + Turbine (app.cash.turbine)'
  - 'FakeSavedPoiRepository, FakeAreaIntelligenceProvider, FakeClock fakes already exist'
  - 'GeminiPromptBuilderTest: assertTrue/assertFalse on prompt string content'
  - 'ChatViewModelTest: runTest + UnconfinedTestDispatcher'
---

# Tech-Spec: Saves Awareness

**Created:** 2026-03-08

## Overview

### Problem Statement

Saves are a dead end — users save places but nothing in the app changes. The map looks identical whether you have 0 or 30 saves. The AI has no idea what you've saved. There is no visual reward for saving. The app discovers FOR you but doesn't learn FROM you.

### Solution

Three interconnected features make saves functional and visible:
1. Inject saved places + screen context into every AI chat session so the AI feels personal
2. Render saved POI pins with a gold ring + checkmark badge on the map, and gold border + gold icon on list rows
3. Show a subtle "N saved places nearby" pill overlay (bottom-center, map and list views) when the current area contains saves

### Scope

**In Scope:**
- Feature 1: AI prompt injection — saves (capped at 10 most-recent, current area only by areaName match) + `ChatEntryPoint` sealed class for screen context framing
- Feature 1: `ChatEntryPoint` variants: `Default` (map/list), `SavesSheet`, `PoiCard(poi: POI)`
- Feature 1: Always inject both area POIs + saves; vary framing line by entrypoint
- Feature 2: Gold ring + ✓ badge on map pins for saved POIs — Android only (iOS deferred)
- Feature 2: Gold left border + gold star icon on list view rows for saved POIs
- Feature 3: Saves nearby pill — bottom-center overlay, areaName string match, always visible when count > 0, shown in both map and list views

**Out of Scope:**
- iOS gold pin styling (deferred — requires MLNAnnotationView custom view with ObjC bridging)
- Global saves injection across all areas (deferred — current-area only for v1)
- "Returning user" detection for pill (deferred — always-on when count > 0 is sufficient for v1)
- Saves-aware list view save CTAs (existing BACKLOG-MEDIUM — separate ticket)
- Radius or viewport-bounds "nearby" calculation (deferred — areaName match sufficient for v1)

## Context for Development

### Codebase Patterns

- `ChatViewModel.openChat(areaName, pois, activeVibe)` builds system context via `promptBuilder.buildChatSystemContext()` at line 72. Extend to accept `entryPoint: ChatEntryPoint = ChatEntryPoint.Default`. Inside `openChat()`, map the sealed class to a plain `framingHint: String?` before passing to the builder.
- `GeminiPromptBuilder.buildChatSystemContext(areaName, poiNames, vibeName)` returns a String. Add `savedPoiNames: List<String>` and `framingHint: String? = null` params — append lines at the end of the existing string. Do NOT add a `ChatEntryPoint` param here — that would create a `data.remote → ui.map` layering violation. The entrypoint-to-string mapping lives in `ChatViewModel`.
- `savedPoiRepository` already injected in `ChatViewModel`. In `init`, add `observeAll().collect { latestSavedPois = it }` — same pattern as `MapViewModel.latestRecents`. Use in `openChat()` synchronously.
- `MapUiState.Ready` already has `savedPois: List<SavedPoi>` and `savedPoiIds: Set<String>` — no new state fields needed.
- Android pins: `ensureIcon(vibe, poiType)` in `MapComposable.android.kt` creates 64×64 bitmaps on Canvas. Add `isSaved: Boolean` param, draw gold `Paint.Style.STROKE` ring + ✓ badge when true. Cache key: `"poi_${vibe.name}_${typeKey}_saved"`.
- `MapComposable` is `expect fun` — signature change must be applied to commonMain + both actuals.
- `POIListView.PoiListCard` uses Material3 `Card(colors = CardDefaults.cardColors(containerColor = MapSurfaceDark))`. Add gold border via `Card(border = BorderStroke(3.dp, Color(0xFFFFD700)))` when `isSaved`.
- `POI.savedId` = `"$name|$latitude|$longitude"` — use this to check membership in `savedPoiIds`. Never recompute inline.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/.../ui/map/ChatViewModel.kt` | openChat() at line 58 — extend with ChatEntryPoint + latestSavedPois |
| `composeApp/src/commonMain/.../data/remote/GeminiPromptBuilder.kt` | buildChatSystemContext() at line 60 — extend with saves + framing |
| `composeApp/src/commonMain/.../ui/map/MapUiState.kt` | savedPois + savedPoiIds already present — read-only reference |
| `composeApp/src/commonMain/.../ui/map/MapScreen.kt` | ReadyContent — add pill overlay + pass savedPoiIds to children |
| `composeApp/src/commonMain/.../ui/map/MapComposable.kt` | expect fun — add savedPoiIds param |
| `composeApp/src/androidMain/.../ui/map/MapComposable.android.kt` | ensureIcon() — add isSaved bitmap variant |
| `composeApp/src/iosMain/.../ui/map/MapComposable.ios.kt` | actual fun — add savedPoiIds param as no-op |
| `composeApp/src/commonMain/.../domain/repository/SavedPoiRepository.kt` | observeAll() used for latestSavedPois collector |
| `composeApp/src/commonMain/.../domain/model/SavedPoi.kt` | areaName + savedAt fields used for filter + sort |
| `composeApp/src/commonMain/.../ui/map/POIListView.kt` | PoiListCard — add isSaved param, gold Card border + star icon |
| `composeApp/src/commonTest/.../data/remote/GeminiPromptBuilderTest.kt` | Add 5 new tests for saves injection |
| `composeApp/src/commonTest/.../ui/map/ChatViewModelTest.kt` | Add 3 new tests for openChat saves behaviour |

### Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Saves scope | Current area only (areaName match) | Shorter prompt, more relevant. Known limitation: areaName string drift. Upgrade to radius later. |
| Save cap | 10 most-recent (sort by savedAt desc) | Prevents prompt bloat. Sufficient signal without overwhelming context. |
| ChatEntryPoint | Sealed class: Default, SavesSheet, PoiCard(poi) | Extensible — future screens add new variant without changing openChat signature |
| Both area POIs + saves always injected | Yes | AI can answer any question regardless of entrypoint. Framing line steers conversation start. |
| Gold pin badge style | ✓ circle at bottom-right of 64×64 bitmap | Doesn't obscure emoji/type signal. Readable at normal zoom. |
| iOS gold pins | Deferred | MLNAnnotationView subclass requires ObjC bridging — not worth complexity for v1 |
| List gold style | Gold left border (3dp) + gold star icon (18dp) | Most scannable. Border visible at distance; icon reinforces at close range. |
| Saves pill position | Bottom-center, padding(bottom = navBarPadding + 72dp) | Top is busy. Bottom-center is visible and unobtrusive above AI bar row. |
| Nearby definition | areaName string match | Zero new params, purely in-state. Known limitation documented as BACKLOG-LOW TODO. |
| Pill visibility | Always when count > 0 (hidden when searching or chat open) | Return detection adds complexity for minimal gain in v1. |

## Implementation Plan

### Tasks

**Feature 1 — AI Prompt Injection + ChatEntryPoint**

- [x] Task 1: Create `ChatEntryPoint` sealed class
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatEntryPoint.kt` (NEW)
  - Action: Create file with:
    ```kotlin
    package com.areadiscovery.ui.map
    import com.areadiscovery.domain.model.POI
    sealed class ChatEntryPoint {
        data object Default : ChatEntryPoint()
        data object SavesSheet : ChatEntryPoint()
        data class PoiCard(val poi: POI) : ChatEntryPoint()
    }
    ```
  - Notes: Place alongside ChatViewModel.kt in the same package

- [x] Task 2: Add `latestSavedPois` collector to `ChatViewModel`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt`
  - Action: Add `private var latestSavedPois: List<SavedPoi> = emptyList()` field. In `init`, add:
    ```kotlin
    viewModelScope.launch {
        savedPoiRepository.observeAll().collect { latestSavedPois = it }
    }
    ```
  - Notes: Same pattern as `MapViewModel.latestRecents`. Already has `savedPoiRepository` injected.
  - COLD-START RACE FIX: The collector starts asynchronously. If `openChat()` is called before the first emission arrives, `latestSavedPois` will still be `emptyList()`. Guard against this in `openChat()` (see Task 4) by checking `latestSavedPois.isEmpty()` and falling back to `savedPoiRepository.observeAll().first()`. This ensures saves are never silently dropped on first-open.

- [x] Task 3: Extend `GeminiPromptBuilder.buildChatSystemContext()` with saves + framing hint
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
  - Action: Add `savedPoiNames: List<String> = emptyList()` and `framingHint: String? = null` params. DO NOT import `ChatEntryPoint` here — `data.remote` must not depend on `ui.map`. The mapping from `ChatEntryPoint` to a String lives in `ChatViewModel.openChat()` (see Task 4). Provide the full updated function body:
    ```kotlin
    fun buildChatSystemContext(
        areaName: String,
        poiNames: List<String>,
        vibeName: String,
        savedPoiNames: List<String> = emptyList(),
        framingHint: String? = null,
    ): String {
        val poisLine = "You are an expert local guide for $areaName. " +
            "The current area contains these points of interest: ${poiNames.joinToString(", ")}. " +
            "The active vibe filter is: $vibeName."
        val vibeLine = "" // existing vibe/context lines — preserve whatever is currently here
        val savesLine = if (savedPoiNames.isNotEmpty())
            " The user has saved: ${savedPoiNames.joinToString(", ")} in this area." else ""
        val framingLine = if (!framingHint.isNullOrBlank()) " $framingHint" else ""
        return "$poisLine$vibeLine$savesLine$framingLine"
    }
    ```
  - Notes: Read the existing function body first and preserve the `poisLine`/`vibeLine` content exactly — only append `savesLine` and `framingLine` at the end. The framing line is injected into the system context only (invisible to the user — not shown in the chat message list).

- [x] Task 4: Update `ChatViewModel.openChat()` to filter saves, map entrypoint to framing hint, and guard cold-start race
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt`
  - Action: Add `entryPoint: ChatEntryPoint = ChatEntryPoint.Default` param. Before building systemContext:
    ```kotlin
    // Cold-start guard: collector may not have emitted yet on first open
    val effectiveSaves = if (latestSavedPois.isEmpty()) {
        savedPoiRepository.observeAll().first()
    } else {
        latestSavedPois
    }
    val areaSaves = effectiveSaves
        .filter { it.areaName == areaName }
        .sortedByDescending { it.savedAt }
        .take(10)
        .map { it.name }
    // Map ChatEntryPoint → plain String here; keeps data.remote free of ui.map imports
    val framingHint: String? = when (entryPoint) {
        is ChatEntryPoint.SavesSheet ->
            "The user is currently reviewing their saved places — lead with suggestions based on those first."
        is ChatEntryPoint.PoiCard ->
            "The user is currently looking at ${entryPoint.poi.name} — lead with context about that place."
        is ChatEntryPoint.Default -> null
    }
    val systemContext = promptBuilder.buildChatSystemContext(areaName, poiNames, vibeName, areaSaves, framingHint)
    ```
  - Notes: `savedAt` sort is descending (most recent first). areaName match is case-sensitive string equality. `observeAll().first()` is a suspend call — `openChat()` must be a suspend fun or called from a coroutine scope (verify existing signature). The framing hint string is appended to system context only — it is never shown in the chat message list.

- [x] Task 5: Update all `openChat()` call sites in `MapScreen.kt`
  - DEPENDENCY: Must complete Task 4 first (openChat signature change lives there)
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: Three call sites:
    1. AI bar tap (`onTap = { chatViewModel.openChat(...) }`) → add `entryPoint = ChatEntryPoint.Default` (default, can omit)
    2. `onAskAiClick` in `ExpandablePoiCard` block → `chatViewModel.openChat(state.areaName, state.pois, state.activeVibe, entryPoint = ChatEntryPoint.PoiCard(state.selectedPoi!!))`
    3. If saves sheet has an AI entry point → `ChatEntryPoint.SavesSheet`
  - Notes: `state.selectedPoi` is non-null inside the `if (state.selectedPoi != null)` block — `!!` is safe there. The `ChatEntryPoint` variants affect the system context string only — the user never sees the framing line in the chat UI.

**Feature 2a — Gold map pins (Android)**

- [x] Task 6: Add `savedPoiIds` param to `MapComposable` expect fun
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt`
  - Action: Add `savedPoiIds: Set<String> = emptySet()` param to the `expect fun MapComposable(...)` signature
  - Notes: Default value allows callers that don't need it to omit it

- [x] Task 7: Thread `savedPoiIds` into Android `MapComposable` and update `LaunchedEffect`
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Action: Add `savedPoiIds: Set<String>` param to `actual fun MapComposable(...)`. Change `LaunchedEffect(pois, activeVibe, styleLoaded.value)` to `LaunchedEffect(pois, activeVibe, savedPoiIds, styleLoaded.value)` so pins re-render when a POI is saved/unsaved.

- [x] Task 8: Add saved pin bitmap variant to `ensureIcon()`
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Action: Add `isSaved: Boolean` param to `ensureIcon()`. Update cache key to `"poi_${vibe.name}_${typeKey}${if (isSaved) "_saved" else ""}"`. When `isSaved`, after drawing the circle background and emoji, additionally:
    ```kotlin
    // Gold ring — radius must be inset by half strokeWidth + 1px so the stroke does not clip at bitmap edge
    val strokeWidth = 5f
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - strokeWidth / 2f - 1f, ringPaint) // ≈28.5f for size=64
    // Gold badge circle at bottom-right
    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFD700")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size - 10f, size - 10f, 10f, badgePaint)
    // Checkmark text in badge
    val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1A1A")
        textSize = 12f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText("✓", size - 10f, size - 6f, checkPaint)
    ```
  - Notes: Badge circle radius 10px, centered at (size-10, size-10). Checkmark y offset -4px for vertical centering.
  - FULL-REBUILD NOTE: The existing `LaunchedEffect` path deletes all symbols and re-creates them from scratch on each key change. This means the `_saved` cache key split works correctly — a saved pin gets its own bitmap, and the unsaved variant remains cached separately. Do NOT optimize to partial pin updates; the full-rebuild path is intentional here.

- [x] Task 9: Pass `isSaved` to `ensureIcon()` in pin rendering loop
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Action: In the `for ((i, poi) in filteredPois.withIndex())` loop, compute `val isSaved = poi.savedId in savedPoiIds` and pass to `ensureIcon(vibe, poi.type, isSaved)`.

- [x] Task 10: Pass `savedPoiIds` from `MapScreen` to `MapComposable`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: In the `MapComposable(...)` call in `ReadyContent`, add `savedPoiIds = state.savedPoiIds`.

**Feature 2b — Gold map pins (iOS no-op)**

- [x] Task 11: Add `savedPoiIds` param to iOS `MapComposable` actual (no-op)
  - File: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt`
  - Action: Add `savedPoiIds: Set<String>` param to `actual fun MapComposable(...)`. No other changes.
  - Notes: Add `// TODO(BACKLOG-LOW): iOS gold saved pins — implement MLNAnnotationView subclass with gold border when MLNAnnotationView ObjC bridging is resolved`

**Feature 2c — Gold list rows**

- [x] Task 12: Add `savedPoiIds` param to `POIListView` and thread to `PoiListCard`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt`
  - Action: Add `savedPoiIds: Set<String> = emptySet()` to `POIListView()`. In the `items` block, pass `isSaved = poi.savedId in savedPoiIds` to `PoiListCard`.

- [x] Task 13: Update `PoiListCard` with gold styling when saved
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt`
  - Action: Add `isSaved: Boolean` param to `PoiListCard`. Update `Card(...)` call:
    ```kotlin
    Card(
        border = if (isSaved) BorderStroke(3.dp, Color(0xFFFFD700)) else null,
        // existing params unchanged
    )
    ```
    Add gold bookmark icon at end of the name row (after liveStatus check):
    ```kotlin
    if (isSaved) {
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.Bookmark,
            contentDescription = "Saved",
            tint = Color(0xFFFFD700),
            modifier = Modifier.size(18.dp),
        )
    }
    ```
  - Notes: Import `androidx.compose.foundation.BorderStroke`. Use `Icons.Filled.Bookmark` (NOT `Icons.Default.Star` — that conflicts with the existing star used for ratings on the same card).

- [x] Task 14: Pass `savedPoiIds` from `MapScreen` to `POIListView`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: In the `POIListView(...)` call in `ReadyContent`, add `savedPoiIds = state.savedPoiIds`.

**Feature 3 — Saves nearby pill**

- [x] Task 15: Add `SavesNearbyPill` composable
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: Add private composable at bottom of file:
    ```kotlin
    @Composable
    private fun SavesNearbyPill(count: Int, modifier: Modifier = Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFFD700).copy(alpha = 0.12f))
                .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFFFFD700), CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$count saved place${if (count == 1) "" else "s"} nearby",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFD700),
            )
        }
    }
    ```
  - Notes: Uses `clip` + `background` + `border` pattern consistent with other overlays in the file. `CircleShape` already imported via Shape.kt.

- [x] Task 16: Add pill overlay to `ReadyContent` Box
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: Inside `ReadyContent`'s root `Box`, compute count and add pill:
    ```kotlin
    // Cross-reference current-area POIs against savedPoiIds for accurate nearby count.
    // NOTE: state.savedPois contains ALL saves across all areas; the .count filter IS the area-scoping
    // — do not simplify to savedPoiIds.size (that would count saves from every area ever visited).
    val savedNearbyCount = state.pois.count { it.savedId in state.savedPoiIds }
    // ... inside Box, BEFORE FabScrim so FABs render on top of the pill:
    AnimatedVisibility(
        visible = savedNearbyCount > 0 && !state.isSearchingArea && !chatState.isOpen && !state.isFabExpanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navBarPadding + 72.dp),
    ) {
        // TODO(BACKLOG-LOW): upgrade saves nearby to haversine radius — areaName match misses saves when geocoding drifts
        SavesNearbyPill(count = savedNearbyCount)
    }
    ```
  - Notes: Place this BEFORE the FabScrim and FAB menu blocks in the Box — earlier position = lower z-order = FABs render on top of the pill. `navBarPadding + 72.dp` positions pill above the bottom row (AI bar height ~56dp + gap). Hide pill when FABs are expanded so it doesn't compete visually with the FAB menu.

**Tests**

- [x] Task 17: Add saves injection tests to `GeminiPromptBuilderTest`
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt`
  - Action: Add 5 tests:
    - `buildChatSystemContext_includesSavedPlaces` — call with `savedPoiNames = listOf("Blue Note", "Wynwood Walls")`, assertTrue result contains "Blue Note"
    - `buildChatSystemContext_noSavesLine_whenSavesEmpty` — call with `savedPoiNames = emptyList()`, assertFalse result contains "The user has saved"
    - `buildChatSystemContext_savesSheetFramingLine` — call with `framingHint = "The user is currently reviewing their saved places..."`, assertTrue result contains "reviewing their saved places"
    - `buildChatSystemContext_poiCardFramingLine` — call with `framingHint = "The user is currently looking at Blue Note Jazz..."`, assertTrue result contains "Blue Note Jazz"
    - `buildChatSystemContext_nullFramingHint_noFramingLine` — call with `framingHint = null`, assertFalse result contains "currently"

- [x] Task 18: Add saves filtering tests to `ChatViewModelTest`
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/ChatViewModelTest.kt`
  - Action: Add 3 tests using `FakeSavedPoiRepository` to pre-populate saves:
    - `openChat_withAreaSaves_injectsThemIntoSystemContext` — populate FakeSavedPoiRepository with 2 saves for "Test Area", call `openChat("Test Area", ...)`, assert `conversationHistory[0].content` contains save names
    - `openChat_withSavesInOtherArea_doesNotInjectThem` — populate with saves for "Other Area", call `openChat("Test Area", ...)`, assert system context does NOT contain those names
    - `openChat_moreThanTenSaves_capsAtTen` — populate with 15 saves for "Test Area", call `openChat("Test Area", ...)`, assert system context contains exactly 10 names (count comma separations)
  - Notes: `conversationHistory` is `private var` in `ChatViewModel`. To allow tests to inspect the injected system context without making the full list public, add this to `ChatViewModel`:
    ```kotlin
    // Test-only accessor — allows ChatViewModelTest to verify system context injection
    internal val systemContextForTest: String
        get() = conversationHistory.firstOrNull()?.content.orEmpty()
    ```
    Tests should assert on `viewModel.systemContextForTest` (the first `conversationHistory` entry is always the system prompt). Check existing test patterns in `ChatViewModelTest` for coroutine setup.

### Acceptance Criteria

- [x] AC 1: Given user has 3 saved places in the current area, when they open chat from the map and ask "what should I do tonight?", then the AI response references at least one of the saved place names

- [x] AC 2: Given user opens chat from the saves sheet, when chat initialises, then the system context includes "currently reviewing their saved places"

- [x] AC 3: Given user taps "Ask AI" on a POI card for a specific place, when chat initialises, then the system context includes that place's name and "currently looking at"

- [x] AC 4: Given user has 0 saved places in the current area, when chat opens, then the system context does NOT contain "The user has saved:"

- [x] AC 5: Given user has 15 saves in the current area, when chat opens, then only 10 names appear in the system context (most recent first)

- [x] AC 6: Given user has saves in a different area only, when chat opens for the current area, then no saves appear in the system context

- [x] AC 7: Given a POI's `savedId` is in `savedPoiIds`, when the Android map renders, then that pin has a visible gold ring border and a ✓ badge at bottom-right

- [x] AC 8: Given a POI is not saved, when the map renders, then that pin renders without gold ring or badge

- [x] AC 9: Given user saves a POI while the map is visible, when `savedPoiIds` updates, then the pin re-renders with gold styling (no app restart needed)

- [x] AC 10: Given list view is active and a POI is saved, when the list renders, then that row has a gold left border and gold star icon

- [x] AC 11: Given list view is active and a POI is not saved, when the list renders, then the row has no gold treatment

- [x] AC 12: Given the current area has 3 saved places, when map or list view is shown, then the pill reads "3 saved places nearby"

- [x] AC 13: Given the current area has 1 saved place, when map or list view is shown, then the pill reads "1 saved place nearby" (singular)

- [x] AC 14: Given the current area has 0 saved places, when map or list view is shown, then no pill is visible

- [x] AC 15: Given the pill is visible and user opens chat, when the chat overlay appears, then the pill is hidden

- [x] AC 16: Given the area is loading (`isSearchingArea = true`), when map renders, then the pill is hidden

## Additional Context

### Dependencies

- No new dependencies required
- No DB schema changes
- No new Koin modules
- `FakeSavedPoiRepository` already exists for test use

### Testing Strategy

- Unit tests only (no instrumented tests needed for this spec)
- Run with `./gradlew :composeApp:test`
- 8 new tests total: 5 in `GeminiPromptBuilderTest`, 3 in `ChatViewModelTest`
- Manual device testing: save 2-3 POIs, open chat — verify AI references them by name; open from POI card — verify framing; check map pin gold styling; check list view gold rows; check pill count

### Notes

- Known limitation: areaName match for "nearby" will miss saves if geocoding returns a slightly different string for the same area. `TODO(BACKLOG-LOW)` added inline in Task 16.
- Known limitation: saves injected at `openChat()` time only — mid-conversation saves won't update AI context until chat is reopened. Acceptable for v1.
- iOS gold pins deferred — `TODO(BACKLOG-LOW)` added in Task 11.
- Brainstorm reference: `_bmad-output/brainstorming/brainstorming-session-2026-03-08-007.md` Tier 1, items 1–3
- Mockups: `/tmp/saves-awareness-map-accurate.html`, `/tmp/saves-awareness-list-v2.html`
