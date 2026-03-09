---
title: 'AI Chat v1.1 — Progressive POI, Save Foundation, Contextual Education'
slug: 'ai-chat-v1-1'
created: '2026-03-08'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'SQLDelight', 'Koin 4.x', 'kotlinx.coroutines + Turbine', 'Gemini API (streaming chat)']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatOverlay.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/AISearchBar.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/ChatViewModelTest.kt'
files_to_create:
  - 'composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/saved_pois.sq'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/SavedPoi.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/repository/SavedPoiRepository.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/SavedPoiRepositoryImpl.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeSavedPoiRepository.kt'
code_patterns:
  - 'SQLDelight .sq files — CREATE TABLE + named queries, accessed via database.tableNameQueries'
  - 'Repository: interface in domain/repository/, impl in data/repository/'
  - 'StateFlow + copy() for all state mutations, id-based bubble updates in ChatViewModel'
  - 'Turbine .test{} for Flow assertions in unit tests'
  - 'FakeXxx classes in commonTest/fakes/ with configurable return values'
  - 'Koin: single<Interface> { Impl(get(), get()) } in dataModule'
  - 'animateFloat + infiniteRepeatable for shimmer (see BlinkingCursor in ChatOverlay.kt)'
  - 'onNavigateToMaps lambda passed through composable tree — no expect/actual needed for directions'
test_patterns:
  - 'kotlin.test (@Test, @BeforeTest, @AfterTest)'
  - 'Dispatchers.setMain/resetMain with UnconfinedTestDispatcher'
  - 'app.cash.turbine for Flow<T> testing'
  - 'FakeXxx pattern: MutableStateFlow + var shouldThrow: Boolean'
---

# Tech-Spec: AI Chat v1.1 — Progressive POI, Save Foundation, Contextual Education

**Created:** 2026-03-08

## Overview

### Problem Statement

Chat responses feel slow because the full Gemini response must complete before any POI cards render. Users have no way to save interesting places discovered through chat. New users don't know what to ask, leading to low engagement with the AI feature.

### Solution

Stream-parse the Gemini chat response progressively — showing skeleton shimmer cards immediately and fading in real POI cards as each JSON object completes mid-stream. Introduce a SavedPoi data layer (SQLDelight table + Repository) wired to inline save chips on each POI card. Add a flat saves list bottom sheet (FAB entry point) as the Itinerary foundation. Educate new users via cycling ghost text in the search bar and a one-line hint in the chat empty state.

### Scope

**In Scope:**
- Streaming JSON parser in `ChatViewModel` — detect complete `{...}` POI objects mid-stream, emit progressively as `ChatPoiCard` items
- Skeleton shimmer — 3 fixed slots shown from stream start, staggered fade-in as each POI card arrives
- SavedPoi SQLDelight table + `SavedPoiRepository` + DI wiring (data layer foundation for Itinerary)
- Save chip on each POI mini-card — optimistic UI update, persists to `saved_pois` table
- Directions chip on each POI mini-card — reuses existing `onNavigateToMaps` lambda (no new expect/actual needed)
- Ghost text cycling in `AISearchBar` — 4 phrases, animates only when `chatIsOpen == false`
- One-line hint text in `ChatOverlay` empty state (below area name, above starter chips)
- Flat saves list bottom sheet — `FabMenu` "Saved Places" entry point, badge count in menu item label
- `GeminiPromptBuilder.buildChatSystemContext` updated to instruct Gemini to embed POI JSON inline

**Out of Scope:**
- Itinerary grouping, planning CTA, area-grouped saves view
- Full "My Saves" / Itinerary list screen
- Directions deep-link analytics
- ~~POI image thumbnails in mini-cards~~ (DONE — satellite tile background on cards)
- Save annotations / AI-generated notes (Phase 3)
- Onboarding integration
- New expect/actual URL launcher (directions reuse existing `onNavigateToMaps` lambda)

## Context for Development

### Codebase Patterns

**SQLDelight (NOT Room):** DB is SQLDelight via `app.cash.sqldelight`. New table = new `.sq` file at `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/`. Follow `recent_places.sq` pattern exactly. `AreaDiscoveryDatabase` is auto-generated — adding a `.sq` file automatically adds `database.saved_poisQueries`. Repository accesses via `database.saved_poisQueries.method().asFlow().mapToList(ioDispatcher)`. See `RecentPlacesRepositoryImpl.kt` as the exact reference implementation.

**POI streaming parser:** As each `ChatToken` arrives in `ChatViewModel.sendMessage`, append text to `accumulated`. Run a brace-counting scanner over accumulated to find complete `{...}` blocks. On finding one, attempt `Json.decodeFromString<ChatPoiCard>(block)`. On success, append to `_uiState.value.poiCards` and set `showSkeletons = (poiCards.size < 3)`. On parse failure, ignore silently. Scanner: walk char-by-char; when `{` found, start counting depth; when depth returns to 0, extract substring and attempt parse.

**ChatPoiCard schema:** Matches the slim JSON keys Gemini will embed: `{"n":"Name","t":"type","lat":0.0,"lng":0.0,"w":"why special"}`. Kotlin model:
```kotlin
@Serializable
data class ChatPoiCard(
    @SerialName("n") val name: String,
    @SerialName("t") val type: String,
    val lat: Double,
    val lng: Double,
    @SerialName("w") val whySpecial: String,
) {
    val id: String get() = "$name|$lat|$lng"
}
```
Place this class in `ChatUiState.kt` (file already exists, add at top before `ChatBubble`).

**Updated chat system prompt:** In `GeminiPromptBuilder.buildChatSystemContext`, append: `" When the user asks about specific places, embed each as a compact JSON object on its own line: {\"n\":\"Name\",\"t\":\"type\",\"lat\":0.0,\"lng\":0.0,\"w\":\"why special in one sentence\"}. Otherwise respond conversationally."` Valid `t` values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district.

**Directions — no expect/actual:** `MapScreen.kt` already has `onNavigateToMaps: (Double, Double, String) -> Boolean`. Pass it through `ReadyContent` → `ChatOverlay(onNavigateToMaps = onNavigateToMaps)`. Add `onNavigateToMaps: (Double, Double, String) -> Boolean` param to `ChatOverlay`. POI mini-card directions chip calls this lambda. If it returns `false`, show a snackbar (pass `snackbarHostState` or use a callback).

**Ghost text cycling:** `AISearchBar` currently has static `Text("Ask anything...")`. Replace with `AnimatedContent(targetState = phraseIndex)` that cycles through 4 phrases every 3 seconds using `LaunchedEffect(Unit) { while(true) { delay(3_000); phraseIndex = (phraseIndex + 1) % 4 } }`. Cycling stops (effect cancelled) when `chatIsOpen == true` — pass `chatIsOpen: Boolean` as new param to `AISearchBar`. Phrases: `listOf("What's the vibe here?", "Hidden gems nearby?", "Safe to walk at night?", "Best time to visit?")`.

**ChatViewModel 4th param:** Add `private val savedPoiRepository: SavedPoiRepository`. In `init {}`, launch a coroutine to observe `savedPoiRepository.observeSavedIds()` and update `_uiState.value.savedPoiIds`. `savePoi(card: ChatPoiCard)` does optimistic update to `savedPoiIds` + `savedPoiRepository.save(...)`. `unsavePoi(id: String)` reverses it.

**MapViewModel 8th param:** Add `private val savedPoiRepository: SavedPoiRepository`. In `init {}`, observe `savedPoiRepository.observeAll()` → update `MapUiState.Ready.savedPois` and `savedPoiCount`. Add `openSavesSheet()` and `closeSavesSheet()` functions.

**SavesBottomSheet:** `ModalBottomSheet` composable added inline in `MapScreen.kt`'s `ReadyContent` (or extracted to separate file if it grows large). Triggered by `state.showSavesSheet`. Shows flat list of `state.savedPois`. `PlatformBackHandler(enabled = state.showSavesSheet) { viewModel.closeSavesSheet() }`. Add to BackHandler priority chain — higher than FAB, lower than ChatOverlay.

**Skeleton shimmer:** 3 `Box` placeholders with `alpha` animated via `rememberInfiniteTransition` + `animateFloat` (0.3f → 0.7f, `tween(800)`, `RepeatMode.Reverse`). Shown as a section in `ChatOverlay` when `chatState.showSkeletons == true`, above the real POI cards section. Each skeleton slot: `RoundedCornerShape(12.dp)`, height 72.dp, full width, background `MaterialTheme.colorScheme.surfaceVariant`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/sqldelight/.../recent_places.sq` | Schema pattern for saved_pois.sq |
| `composeApp/src/commonMain/kotlin/.../RecentPlacesRepositoryImpl.kt` | Repository impl pattern |
| `composeApp/src/commonMain/kotlin/.../ChatOverlay.kt` | Insertion points for mini-cards, skeleton, hint |
| `composeApp/src/commonMain/kotlin/.../ChatViewModel.kt` | Streaming token handling — add parser here |
| `composeApp/src/commonMain/kotlin/.../ChatUiState.kt` | Add ChatPoiCard, new state fields |
| `composeApp/src/commonMain/kotlin/.../AISearchBar.kt` | Replace static text with AnimatedContent |
| `composeApp/src/commonMain/kotlin/.../MapScreen.kt` | Wire saves sheet, badge counter, pass onNavigateToMaps to ChatOverlay |
| `composeApp/src/commonMain/kotlin/.../FabMenu.kt` | Add savedCount param for badge label |
| `composeApp/src/commonMain/kotlin/.../PlatformBackHandler.kt` | expect/actual reference |
| `composeApp/src/commonTest/kotlin/.../ChatViewModelTest.kt` | Test pattern reference |
| `composeApp/src/commonTest/kotlin/fakes/FakeAreaIntelligenceProvider.kt` | Fake pattern reference |

### Technical Decisions

- SQLDelight not Room: spec initially said "Room entity" — corrected. New `.sq` file is the right approach.
- Directions via lambda: `onNavigateToMaps` already exists and is platform-abstracted. No new expect/actual needed — simpler and consistent with the existing ExpandablePoiCard pattern.
- `ChatPoiCard` is a separate type from `POI`: lighter schema, only fields needed for chat cards. Avoids coupling chat flow to the portrait model.
- POI cards rendered as a separate section in `ChatOverlay` below bubbles, not embedded inside bubble items — keeps `ChatBubbleItem` rendering clean.
- Skeleton count: always 3 fixed slots regardless of how many POIs actually arrive.
- Badge: modify `FabMenuItem` label text to include count — "Saved Places (3)". Pass `savedCount: Int` to `FabMenu`.
- `showSkeletons`: set `true` when streaming starts AND the query appears to ask about places (simple heuristic: `computeFollowUpChips` already categorises queries — if category is not "chat" show skeletons). Simpler: always show skeletons on stream start, hide when stream ends or first POI card arrives.

## Implementation Plan

### Tasks

- [x] Task 1: Create `SavedPoi` domain model
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/SavedPoi.kt`
  - Action: Create data class `SavedPoi(val id: String, val name: String, val type: String, val areaName: String, val lat: Double, val lng: Double, val whySpecial: String, val savedAt: Long)`
  - Notes: Pure Kotlin, no Android imports. `id` is `"$name|$lat|$lng"` — deterministic, deduplication-safe.

- [x] Task 2: Create `saved_pois.sq` SQLDelight schema
  - File: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/saved_pois.sq`
  - Action: Create table and queries following `recent_places.sq` pattern exactly:
    ```sql
    CREATE TABLE IF NOT EXISTS saved_pois (
        poi_id      TEXT NOT NULL PRIMARY KEY,
        name        TEXT NOT NULL,
        type        TEXT NOT NULL,
        area_name   TEXT NOT NULL,
        lat         REAL NOT NULL,
        lng         REAL NOT NULL,
        why_special TEXT NOT NULL,
        saved_at    INTEGER NOT NULL
    );
    observeAll:
    SELECT * FROM saved_pois ORDER BY saved_at DESC;
    observeSavedIds:
    SELECT poi_id FROM saved_pois;
    insertOrReplace:
    INSERT OR REPLACE INTO saved_pois(poi_id, name, type, area_name, lat, lng, why_special, saved_at)
    VALUES (:poi_id, :name, :type, :area_name, :lat, :lng, :why_special, :saved_at);
    deleteById:
    DELETE FROM saved_pois WHERE poi_id = :poi_id;
    countAll:
    SELECT COUNT(*) FROM saved_pois;
    ```
  - Notes: SQLDelight auto-generates `database.saved_poisQueries` after sync. Build the project after this task.

- [x] Task 3: Create `SavedPoiRepository` interface
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/repository/SavedPoiRepository.kt`
  - Action: Define interface:
    ```kotlin
    interface SavedPoiRepository {
        fun observeAll(): Flow<List<SavedPoi>>
        fun observeSavedIds(): Flow<Set<String>>
        fun observeCount(): Flow<Int>
        suspend fun save(poi: SavedPoi)
        suspend fun unsave(poiId: String)
    }
    ```
  - Notes: Pure Kotlin, import `kotlinx.coroutines.flow.Flow` only.

- [x] Task 4: Create `SavedPoiRepositoryImpl`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/SavedPoiRepositoryImpl.kt`
  - Action: Implement `SavedPoiRepository` using `AreaDiscoveryDatabase`. Follow `RecentPlacesRepositoryImpl` exactly:
    - `observeAll()`: `database.saved_poisQueries.observeAll().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { SavedPoi(it.poi_id, it.name, it.type, it.area_name, it.lat, it.lng, it.why_special, it.saved_at) } }`
    - `observeSavedIds()`: `database.saved_poisQueries.observeSavedIds().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.poi_id }.toSet() }`
    - `observeCount()`: `database.saved_poisQueries.countAll().asFlow().mapToOneOrDefault(0, ioDispatcher).map { it.toInt() }`
    - `save(poi)`: `withContext(ioDispatcher) { database.saved_poisQueries.insertOrReplace(...) }`
    - `unsave(poiId)`: `withContext(ioDispatcher) { database.saved_poisQueries.deleteById(poiId) }`
    - Constructor: `(private val database: AreaDiscoveryDatabase, private val clock: AppClock, private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default)`
  - Notes: `saved_at` = `clock.nowMs()` in `save()`.

- [x] Task 5: Register `SavedPoiRepository` in DI
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
  - Action: Add `single<SavedPoiRepository> { SavedPoiRepositoryImpl(get(), get()) }` after the `RecentPlacesRepository` registration line.

- [x] Task 6: Create `FakeSavedPoiRepository` for tests
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeSavedPoiRepository.kt`
  - Action: Implement `SavedPoiRepository` with:
    - `private val _pois = MutableStateFlow<List<SavedPoi>>(emptyList())`
    - `var shouldThrow: Boolean = false`
    - `observeAll()` returns `_pois.asStateFlow()`
    - `observeSavedIds()` returns `_pois.map { it.map { p -> p.id }.toSet() }`
    - `observeCount()` returns `_pois.map { it.size }`
    - `save(poi)`: if shouldThrow throw RuntimeException("Test error"); else `_pois.value = _pois.value + poi`
    - `unsave(poiId)`: `_pois.value = _pois.value.filter { it.id != poiId }`

- [x] Task 7: Add `ChatPoiCard` model and new fields to `ChatUiState`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatUiState.kt`
  - Action:
    1. Add `@Serializable data class ChatPoiCard(@SerialName("n") val name: String, @SerialName("t") val type: String, val lat: Double, val lng: Double, @SerialName("w") val whySpecial: String) { val id: String get() = "$name|$lat|$lng" }` at top of file (add `kotlinx.serialization` imports).
    2. Add to `ChatUiState`: `val poiCards: List<ChatPoiCard> = emptyList()`, `val showSkeletons: Boolean = false`, `val savedPoiIds: Set<String> = emptySet()`.
  - Notes: `ChatPoiCard` must be `@Serializable` for `Json.decodeFromString` in the parser.

- [x] Task 8: Update `GeminiPromptBuilder.buildChatSystemContext`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
  - Action: Append to the returned string: `" When asked about specific places, embed each as a compact JSON object on its own line using these exact keys: {\"n\":\"Name\",\"t\":\"type\",\"lat\":0.0,\"lng\":0.0,\"w\":\"one sentence on why it is special\"}. Valid t values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district. Otherwise respond conversationally."`
  - Notes: The closing `"` of the existing return string gets this appended before it closes. Keep total prompt under 500 chars.

- [x] Task 9: Update `ChatViewModel` — add parser, save/unsave, 4th constructor param
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt`
  - Action:
    1. Add 4th constructor param: `private val savedPoiRepository: SavedPoiRepository`. Add import.
    2. In `init {}`: `viewModelScope.launch { savedPoiRepository.observeSavedIds().collect { ids -> _uiState.value = _uiState.value.copy(savedPoiIds = ids) } }`
    3. In `sendMessage()`, when `isStreaming = true` is set: also set `showSkeletons = true, poiCards = emptyList()`.
    4. In the `collect { token -> }` block, after `accumulated += token.text`: call private `parsePoiCards(accumulated)` and update state with any new cards found. Set `showSkeletons = (parsedCards.size < 3 && isStreaming)`.
    5. When `token.isComplete`: set `showSkeletons = false`.
    6. In the `.catch { }` error handler: set `showSkeletons = false`.
    7. Add `private fun parsePoiCards(text: String): List<ChatPoiCard>`: brace-counting scanner — walk chars, track depth, extract substrings at depth=0 boundaries, attempt `Json { ignoreUnknownKeys = true }.decodeFromString<ChatPoiCard>(substring)` for each, collect successes.
    8. Add `fun savePoi(card: ChatPoiCard, areaName: String)`: optimistic: `_uiState.value = _uiState.value.copy(savedPoiIds = savedPoiIds + card.id)`, then `viewModelScope.launch { try { savedPoiRepository.save(SavedPoi(card.id, card.name, card.type, areaName, card.lat, card.lng, card.whySpecial, clock.nowMs())) } catch(e) { /* revert */ _uiState.value = _uiState.value.copy(savedPoiIds = savedPoiIds - card.id) } }`.
    9. Add `fun unsavePoi(id: String)`: same optimistic pattern reversed.
    10. In `openChat()` reset: add `poiCards = emptyList(), showSkeletons = false, savedPoiIds = emptySet()` to the `ChatUiState(...)` constructor call (savedPoiIds re-populated by the init observer).
  - Notes: `Json { ignoreUnknownKeys = true }` is already used in `GeminiAreaIntelligenceProvider` — create a local val inside `ChatViewModel` or reuse a shared instance. Import `kotlinx.serialization.json.Json`.

- [x] Task 10: Update `UiModule` — ChatViewModel 4th param
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
  - Action: Change `viewModel { ChatViewModel(get(), get(), get()) }` to `viewModel { ChatViewModel(get(), get(), get(), get()) }`.

- [x] Task 11: Update `MapUiState.Ready` — saves state fields
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
  - Action: Add to `Ready` data class: `val savedPois: List<SavedPoi> = emptyList()`, `val savedPoiCount: Int = 0`, `val showSavesSheet: Boolean = false`. Add `SavedPoi` import.

- [x] Task 12: Update `MapViewModel` — observe saves, open/close sheet
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action:
    1. Add 8th constructor param: `private val savedPoiRepository: SavedPoiRepository`. Add import.
    2. In `init {}` block (after the existing `recentPlacesRepository.observeRecent()` observer): add `viewModelScope.launch { savedPoiRepository.observeAll().collect { pois -> val current = _uiState.value as? MapUiState.Ready ?: return@collect; _uiState.value = current.copy(savedPois = pois, savedPoiCount = pois.size) } }`.
    3. Add `fun openSavesSheet() { val current = _uiState.value as? MapUiState.Ready ?: return; _uiState.value = current.copy(showSavesSheet = true) }`.
    4. Add `fun closeSavesSheet() { val current = _uiState.value as? MapUiState.Ready ?: return; _uiState.value = current.copy(showSavesSheet = false) }`.

- [x] Task 13: Update `UiModule` — MapViewModel 8th param
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
  - Action: Change `viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get()) }` to `viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }`.

- [x] Task 14: Update `FabMenu` — add `savedCount` param for badge label
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/FabMenu.kt`
  - Action: Add `savedCount: Int = 0` param to `FabMenu`. Pass it to the `FabMenuItem` for "Saved Places": `label = if (savedCount > 0) "Saved Places ($savedCount)" else "Saved Places"`.

- [x] Task 15: Update `MapScreen.kt` — wire saves sheet, pass savedCount, fix "Coming soon", pass `onNavigateToMaps` to ChatOverlay
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action:
    1. In `ReadyContent`, pass `savedCount = state.savedPoiCount` to `FabMenu`.
    2. Change `onSavedPlaces` lambda from showing snackbar to `viewModel.openSavesSheet(); viewModel.toggleFab()`.
    3. Add `SavesBottomSheet` composable call: `if (state.showSavesSheet) { SavesBottomSheet(savedPois = state.savedPois, onDismiss = { viewModel.closeSavesSheet() }) }`.
    4. Add `PlatformBackHandler(enabled = state.showSavesSheet) { viewModel.closeSavesSheet() }` — insert BEFORE the `PlatformBackHandler(enabled = chatState.isOpen)` block so ChatOverlay remains highest priority.
    5. Pass `onNavigateToMaps` to `ChatOverlay`: `ChatOverlay(..., onNavigateToMaps = onNavigateToMaps)`.
    6. Add inline `SavesBottomSheet` composable at bottom of file (private):
       ```kotlin
       @Composable
       private fun SavesBottomSheet(savedPois: List<SavedPoi>, onDismiss: () -> Unit) {
           val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
           ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
               if (savedPois.isEmpty()) {
                   Text("No saved places yet", modifier = Modifier.padding(24.dp), ...)
               } else {
                   LazyColumn { items(savedPois) { poi -> SavedPoiRow(poi) } }
               }
           }
       }
       ```
    7. Add `SavedPoiRow` private composable: shows `poi.name` + `poi.areaName` + `poi.type` in a `ListItem`-style row. Keep simple for v1.
  - Notes: `onNavigateToMaps` is already available in `ReadyContent` signature — just pass it through.

- [x] Task 16: Update `ChatOverlay` — add POI mini-cards section, skeleton shimmer, empty state hint, `onNavigateToMaps` param
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatOverlay.kt`
  - Action:
    1. Add `onNavigateToMaps: (Double, Double, String) -> Boolean` param to `ChatOverlay`. Add `snackbarHostState: SnackbarHostState` param (or a `onDirectionsFailed: () -> Unit` callback — simpler).
    2. In the `LazyColumn` items block, after the existing `bubbles` items: add a new item for skeleton section (when `chatState.showSkeletons`) and a new item for POI cards section (when `chatState.poiCards.isNotEmpty()`).
    3. Skeleton section: `item(key = "skeletons") { SkeletonSection(chatState.poiCards.size) }` — shows `(3 - chatState.poiCards.size).coerceAtLeast(0)` remaining skeleton slots.
    4. POI cards section: `items(chatState.poiCards, key = { it.id }) { card -> ChatPoiMiniCard(card, isSaved = card.id in chatState.savedPoiIds, onSave = { viewModel.savePoi(card, chatState.areaName) }, onUnsave = { viewModel.unsavePoi(card.id) }, onDirections = { onNavigateToMaps(card.lat, card.lng, card.name) }) }`.
    5. Empty state hint: in `EmptyState` composable, add `Text("Try asking what's nearby, what's safe, or what's on tonight", style = bodySmall, color = onSurfaceVariant, textAlign = Center)` after the area name text and before `Spacer(Modifier.height(20.dp))`.
    6. Add private `SkeletonSection(filledCount: Int)` composable: shows `(3 - filledCount).coerceAtLeast(0)` shimmer placeholder boxes using `rememberInfiniteTransition` + `animateFloat(0.3f, 0.7f, tween(800), RepeatMode.Reverse)`. Each: `Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(shimmerColor.copy(alpha = shimmerAlpha)))`.
    7. Add private `ChatPoiMiniCard(card, isSaved, onSave, onUnsave, onDirections)` composable: `Surface(shape = RoundedCornerShape(12.dp), color = AiBubbleColor)` with a `Column` containing: card name (`titleSmall`), why special (`bodySmall`, max 2 lines), and a `Row` with two `SuggestionChip`s — "Save" / "Saved ✓" (toggling on `isSaved`) and "Directions".
  - Notes: The `AiBubbleColor` and `IndigoGradient` vals are already defined at top of `ChatOverlay.kt` — reuse them. Keep `ChatPoiMiniCard` simple — no images for v1.

- [x] Task 17: Update `AISearchBar` — cycling ghost text
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/AISearchBar.kt`
  - Action:
    1. Add `chatIsOpen: Boolean = false` param.
    2. Add `var phraseIndex by remember { mutableStateOf(0) }`.
    3. Add `LaunchedEffect(chatIsOpen) { if (!chatIsOpen) { while(true) { delay(3_000); phraseIndex = (phraseIndex + 1) % phrases.size } } }`.
    4. Define `val phrases = listOf("What's the vibe here?", "Hidden gems nearby?", "Safe to walk at night?", "Best time to visit?")`.
    5. Replace static `Text("Ask anything...")` with `AnimatedContent(targetState = phraseIndex, transitionSpec = { slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut() }) { index -> Text(phrases[index], ...) }`.
  - Notes: `AnimatedContent` requires `@OptIn(ExperimentalAnimationApi::class)` if not already imported. Pass `chatIsOpen = chatState.isOpen` from `MapScreen.kt` where `AISearchBar` is already called.

- [x] Task 18: Update `MapScreen.kt` — pass `chatIsOpen` to `AISearchBar`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: In the `AISearchBar(...)` call, add `chatIsOpen = chatState.isOpen`.
  - Notes: `chatState` is already available in `ReadyContent` scope. This is a one-line change.

- [x] Task 19: Write unit tests
  - Files: `ChatViewModelTest.kt`, `ChatViewModelTest.kt` additions
  - Action:
    1. `FakeAreaIntelligenceProvider`: no changes needed (existing fake works).
    2. `FakeSavedPoiRepository`: already created in Task 6.
    3. In `ChatViewModelTest.kt`, add `fakeSavedPoiRepository = FakeSavedPoiRepository()` to `createViewModel()`. Update `createViewModel()` to pass it as 4th param.
    4. Add test: `parsePoiCards extracts valid POI from streaming token` — send tokens that together form `{"n":"Cafe","t":"food","lat":1.0,"lng":2.0,"w":"Best espresso"}`, assert `chatState.poiCards` contains 1 card with name "Cafe".
    5. Add test: `showSkeletons true on stream start, false on complete` — assert `showSkeletons = true` after `sendMessage`, `false` after `token.isComplete`.
    6. Add test: `savePoi optimistically updates savedPoiIds` — call `viewModel.savePoi(card, "Test Area")`, assert `uiState.savedPoiIds.contains(card.id)`.
    7. Add test: `savePoi reverts on repository failure` — set `fakeSavedPoiRepository.shouldThrow = true`, call `savePoi`, assert `savedPoiIds` does NOT contain the id after the error.

### Acceptance Criteria

- [x] AC 1: Given the chat is open and the user sends a message, when streaming begins, then 3 skeleton shimmer slots are visible immediately (before any response arrives).

- [x] AC 2: Given Gemini response contains a valid inline POI JSON object `{n, t, lat, lng, w}`, when the complete `{...}` block arrives mid-stream, then a real POI mini-card replaces one skeleton slot with the POI name, type, and why-special text.

- [x] AC 3: Given POI mini-cards are visible, when the user taps "Save" on a card, then the chip label immediately changes to "Saved ✓" (optimistic) and the POI is persisted to `saved_pois` DB.

- [x] AC 4: Given a POI has been saved, when the user taps "Saved ✓" on the chip, then the chip returns to "Save" and the POI is removed from the DB.

- [x] AC 5: Given a POI mini-card is visible, when the user taps "Directions", then the device maps app opens with the correct location (or a snackbar appears if no maps app is available).

- [x] AC 6: Given the chat overlay is closed and the AI search bar is visible, when 3 seconds pass, then the ghost text cycles to the next phrase with a vertical slide animation.

- [x] AC 7: Given the chat overlay is open, when the ghost text would normally cycle, then it does NOT animate (cycling paused while chat is open).

- [x] AC 8: Given the chat overlay has no messages (empty state), when it first opens, then a one-line hint is visible below the area name and above the starter chips.

- [x] AC 9: Given at least one POI has been saved, when the user opens the FAB menu, then the "Saved Places" label shows the count (e.g., "Saved Places (3)").

- [x] AC 10: Given the user taps "Saved Places" in the FAB menu, when the sheet opens, then a flat list of saved POIs is shown with name and area for each entry.

- [x] AC 11: Given the saves sheet is open, when the user presses the Android back button, then the sheet dismisses (not the app).

- [x] AC 12: Given the Gemini response contains malformed or partial JSON, when the parser encounters it, then no POI card is shown and no crash occurs (silent failure).

- [x] AC 13: Given saving a POI fails (network/DB error), when the error occurs, then the save chip reverts to "Save" (optimistic update rolled back).

## Additional Context

### Dependencies

- SQLDelight (already in use — `app.cash.sqldelight`, `app.cash.sqldelight:coroutines-extensions`)
- Koin DI (already in use)
- `app.cash.turbine` for tests (already in use — see `ChatViewModelTest.kt`)
- `kotlinx.serialization` (already in use — `GeminiAreaIntelligenceProvider` uses it)
- No new third-party dependencies

### Testing Strategy

Unit tests (run with `./gradlew :composeApp:test`):
- `ChatViewModelTest` additions: parser extraction, skeleton lifecycle, save/unsave optimistic + rollback
- No `MapViewModelTest` additions for v1 (save count observation is trivial Flow plumbing)

Manual testing steps:
1. Open chat, send "show me cafes nearby" — verify skeletons appear, then POI cards fade in as response streams
2. Tap "Save" on a POI card — verify immediate "Saved ✓", reopen app and verify POI still saved
3. Open FAB, verify badge count matches saved POIs
4. Open "Saved Places" — verify flat list shows correctly
5. Tap Android back from Saved Places sheet — verify sheet dismisses, not app exit
6. Verify AISearchBar cycles phrases when chat closed, stops when chat open

### Notes

- High risk: Gemini may not reliably embed POI JSON in conversational responses — the parser must be completely silent on failure. Recommend testing prompt with several query types before release.
- High risk: `UrlLauncher` Context access on Android — decided to use existing `onNavigateToMaps` lambda instead of new expect/actual, avoiding this risk entirely.
- Low risk: `AnimatedContent` API may require `@OptIn(ExperimentalAnimationApi::class)` depending on Compose version — check at implementation time.
- Future: `SavesBottomSheet` grows into the full Itinerary builder (Epic 3). Keep the composable extractable — avoid inlining too much logic.
- Brainstorm context: `_bmad-output/brainstorming/brainstorming-session-2026-03-06-002.md` Session 6
- v1 spec reference: `_bmad-output/implementation-artifacts/tech-spec-ai-chat-v1-android.md`
- Contextual AI Tip deferred — reconnect with Epic 5 Story 5.2 (onboarding) when that spec is written

## Review Notes
- Adversarial review completed (17 findings)
- Findings: 17 total, 9 fixed, 8 skipped (noise/by-design)
- Resolution approach: auto-fix
- Fixed: F1 (parser string-aware braces), F3 (deduplicated savedAt), F4 (O(n) incremental parsing), F5 (optimistic save race), F7 (DB migration), F10 (fake unsave shouldThrow), F12 (LazyColumn height), F16 (braces test), F17 (dead observeCount removed)

## Post-Implementation Additions (beyond original spec)

1. POI JSON stripping from chat bubbles: `stripPoiJson()` in ChatViewModel removes parsed JSON blocks from the displayed message text so users see clean prose + separate POI cards (not raw JSON).
2. Satellite tile card backgrounds: POI mini-cards use MapTiler satellite tile as full-bleed background image (zoom 15, lat/lng to tile coords) with a vertical gradient scrim (black 35%->85%) for white text readability. Reuses existing MapTiler API key — no new API integration needed.
3. Stronger Gemini prompt: `buildChatSystemContext` updated with CRITICAL RULE instructing Gemini to ALWAYS emit POI JSON when mentioning any place by name, with an example format. Original prompt was too weak ("when asked about specific places") causing Gemini to respond with plain text.
4. DB migration safety net: `DatabaseDriverFactory` (both Android + iOS) runs `CREATE TABLE IF NOT EXISTS saved_pois` after driver creation as a fallback, not just in `AfterVersion` callbacks. Fixes crash on devices with stale DB state.
5. Card design: type emoji + type label, full-width cards with satellite background, translucent white chips with border.
6. Tests: `stripPoiJson` unit tests added (prose preservation, no-op on plain text).
