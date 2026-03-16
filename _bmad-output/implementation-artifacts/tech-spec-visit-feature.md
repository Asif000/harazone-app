---
title: 'Visit Feature — Replace Save with AI-Routed Visit Action'
slug: 'visit-feature'
created: '2026-03-15'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'SQLDelight', 'Gemini API (Ktor SSE)']
files_to_modify:
  - composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt
  - composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq
  - composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/11.sqm
  - composeApp/src/commonMain/kotlin/com/harazone/domain/repository/SavedPoiRepository.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt
  - composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeSavedPoiRepository.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapComposable.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PoiCarousel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FabMenu.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt
code_patterns:
  - 'SQLDelight .sq schema + numbered .sqm migrations (current version 10 → 11 next)'
  - 'MVVM + Koin DI — VMs injected via viewModel<>()'
  - 'Sealed class ChatEntryPoint drives pendingFramingHint in ChatViewModel'
  - 'resolveStatus(liveStatus, hours) in PinStatusUtils.kt returns open/closed/closing soon/null'
  - 'Optimistic UI updates in MapViewModel — update state first, rollback on error'
  - 'PlatformBackHandler on every dismissible overlay'
  - 'Compose Multiplatform commonMain only (no platform splits for UI logic)'
  - 'Visit framing hint built inline in ChatViewModel — NOT in GeminiPromptBuilder (layer violation avoided)'
test_patterns:
  - 'commonTest: JUnit5 + kotlin-test + Turbine (Flow assertions) + FakeX pattern'
  - 'androidUnitTest: JdbcSqliteDriver in-memory DB for repository tests'
  - 'FakeSavedPoiRepository in commonTest/fakes — must update with visit()'
  - 'MapViewModelTest + ChatViewModelTest in commonTest'
  - 'Given/When/Then test naming: `fun \`visitPoi routes GO_NOW when status is open\`()`'
---

# Tech-Spec: Visit Feature — Replace Save with AI-Routed Visit Action

**Created:** 2026-03-15
**Covers brainstorm tickets:** #52 (Visit Feature), #53 (Go Now only — client-side)
**Target release:** Public launch (post-tester)
**Review:** Adversarial review completed 2026-03-15 — 12 findings, all resolved below.

---

## Overview

### Problem Statement

The app has no action loop closure — users can discover and engage with POIs but have no meaningful next step. The current "Save" action (★ on carousel, bookmark on detail page) is passive and creates a dead-end list rather than driving actual visits.

### Solution

Replace "Save" with "Visit" as the single primary POI action. When tapped, `MapViewModel.visitPoi()` calls `resolveStatus()` to determine routing: **Go Now** (status = open / closing soon) or **Plan Soon** (status = closed / null). The `VisitState` is stored on `SavedPoi` via a new SQLDelight column. When the Visit action opens `AiDetailPage`, the chat is seeded with a Visit-aware framing hint built in `ChatViewModel`. The Saved Places screen becomes "Visited Places." Client-side MVP only — no backend, no proximity auto-detect.

### Scope

**In Scope:**
- `VisitState` enum (`WANT_TO_GO`, `GO_NOW`, `PLAN_SOON`) + `visitedAt: Long?` on `SavedPoi`
- SQLDelight migration `11.sqm` — two new nullable columns on `saved_pois`
- `SavedPoiRepository.visit()` — saves POI with `VisitState`
- `MapViewModel.visitPoi()` — routes via `resolveStatus()`, calls `repository.visit()`
- `ChatEntryPoint.VisitAction(poi, visitState)` — new entry point for visit-seeded AI
- `ChatViewModel.openChatForPoiVisit()` — builds visit framing hint inline, seeds AI intro
- `PoiCarousel` — ★/☆ → Visit button (visit-only; unvisit not available from carousel)
- `AiDetailPage` / `PoiDetailHeader` — bookmark icon → Visit button
- `POIListView` — save toggle → Visit toggle
- `MapUiState.Ready` — rename `savedPoiIds`→`visitedPoiIds`, `savedPois`→`visitedPois`, `savedPoiCount`→`visitedPoiCount`, `showSavesSheet`→`showVisitsSheet`, `savedVibeFilter`→`visitedFilter`
- All `MapScreen.kt`, `MapComposable.kt`, `FabMenu.kt` call sites updated to match renames
- `SavedPlacesScreen` — title "Visited Places"
- `SavedPoiCard` — `VisitState` badge: green "Go Now", amber "Plan Soon", grey "Want to Visit" (WANT_TO_GO)

**Out of Scope:**
- Proximity auto-detect (50m + dwell) — v1.1
- Plan Trip (far/international) — needs backend
- Visit → Visited → Return state evolution — v1.1
- AI Mirror Profile (#19) — separate spec
- Removing `save()` from `SavedPoiRepository` — keep for backward compat

---

## Context for Development

### Codebase Patterns

- **DB is SQLDelight** (not Room). Schema in `.sq` files; migrations are numbered `.sqm` files. Current version = 10. **Before creating `11.sqm`, verify no existing file at `migrations/11.sqm` and check `AreaDiscoveryDatabase.Schema.version`.** New migration = `11.sqm`. Only ALTER TABLE statements go in `.sqm`; the `CREATE TABLE` in `.sq` is updated to include new columns for fresh installs.
- **Optimistic UI in MapViewModel** — `visitPoi()` mirrors `savePoi()`: update `_uiState.value` immediately with new `visitedPoiIds`, then call repository, rollback the entire state object (both `visitedPoiIds` and remove the `SavedPoi` from `visitedPois`) on exception.
- **ChatEntryPoint sealed class** drives `pendingFramingHint` in `ChatViewModel.openChat()`. The `when (entryPoint)` block maps to a framing string. `VisitAction` is handled directly in `ChatViewModel` — the framing hint string is built inline in `ChatViewModel`, **not in `GeminiPromptBuilder`**, to avoid a layer violation (`data/remote` must never import `ui/map`).
- **`openChatForPoi()`** seeds via `tapIntentPill()` with `"Tell me about ${poi.name}."` New `openChatForPoiVisit()` uses visit-specific message: `"I want to visit ${poi.name}."` and passes a visit-state-aware framing hint string directly to `openChat()` via `pendingFramingHint`.
- **`resolveStatus(liveStatus, hours)`** in `PinStatusUtils.kt` returns `"open"`, `"closing soon"`, `"closed"`, or `null`. Routing: `"open"` or `"closing soon"` → `GO_NOW`; `"closed"` → `PLAN_SOON`; `null` → `WANT_TO_GO`.
- **VisitState serialization**: Use `VisitState.entries.find { it.name == rawValue }` (not `valueOf()`) when reading from DB — avoids `IllegalArgumentException` on unknown/future values. Unknown values fall back to `null`.
- **MapUiState rename** ripples into `MapScreen.kt` (~20 call sites), `MapComposable.kt` (~5), `POIListView.kt` (~6), `FabMenu.kt` (`onSavedPlaces` → `onVisitedPlaces`). All must be updated in the same task as the MapUiState change to keep the build green.
- **`latestSavedPois`** (private var in `MapViewModel`) is intentionally NOT renamed. It feeds `TasteProfileBuilder.build()` which requires ALL historical saved/visited POIs. Renaming it is safe but risks a dev agent second-guessing the taste profile logic. Leave as-is.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `domain/model/SavedPoi.kt` | Add `VisitState` enum + nullable fields |
| `data/local/saved_pois.sq` | Schema + new queries |
| `data/local/migrations/11.sqm` | ALTER TABLE migration |
| `domain/repository/SavedPoiRepository.kt` | Add `visit()` to interface |
| `data/repository/SavedPoiRepositoryImpl.kt` | Implement `visit()` with safe enum deserialization |
| `commonTest/fakes/FakeSavedPoiRepository.kt` | Add `visit()` stub |
| `ui/map/ChatEntryPoint.kt` | Add `VisitAction` |
| `ui/map/ChatViewModel.kt` | Add `openChatForPoiVisit()` with inline framing hint |
| `ui/map/MapUiState.kt` | Rename saved* → visited* |
| `ui/map/MapViewModel.kt` | Add `visitPoi()`, rename `unsavePoi()`, keep `latestSavedPois` |
| `ui/map/MapScreen.kt` | Update all call sites (~20) |
| `ui/map/MapComposable.kt` | Update params (~5) |
| `ui/map/POIListView.kt` | Replace save toggle with visit |
| `ui/map/components/PoiCarousel.kt` | Replace ★ with Visit button (visit-only, no unvisit) |
| `ui/map/components/AiDetailPage.kt` | Replace bookmark with Visit button |
| `ui/map/components/FabMenu.kt` | Rename `onSavedPlaces` → `onVisitedPlaces` param |
| `ui/saved/SavedPlacesViewModel.kt` | Rename `unsavePoi()` → `unvisitPoi()` |
| `ui/saved/SavedPlacesScreen.kt` | Rename title |
| `ui/saved/components/SavedPoiCard.kt` | Add `VisitState` badge (3 states) |

### Technical Decisions

1. **`VisitState` enum in domain model** — `WANT_TO_GO` (null status = no hours info), `GO_NOW` (open/closing soon), `PLAN_SOON` (closed). Stored as nullable TEXT in SQLDelight; `null` = legacy saved row. Unknown future values also deserialize to `null`.

2. **Routing logic in `MapViewModel.visitPoi()`:**
   ```kotlin
   val status = resolveStatus(poi.liveStatus, poi.hours)
   val visitState = when {
       status == "open" || status == "closing soon" -> VisitState.GO_NOW
       status == "closed" -> VisitState.PLAN_SOON
       else -> VisitState.WANT_TO_GO  // null hours or unknown status
   }
   ```

3. **Visit framing hint strings built inline in `ChatViewModel`** (NOT in `GeminiPromptBuilder` — that file is in `data/remote` and must not import `ui/map`):
   ```kotlin
   private fun buildVisitFramingHint(poi: POI, visitState: VisitState): String {
       val status = resolveStatus(poi.liveStatus, poi.hours)
       return when (visitState) {
           VisitState.GO_NOW -> "The user just tapped Visit on ${poi.name} — it is currently $status. Lead with a Go Now response: best approach right now, crowd level, one thing to do/order/see first. Conversational, not a checklist. End with a question."
           VisitState.PLAN_SOON -> "The user just tapped Visit on ${poi.name} — it is currently closed. Lead with a Plan Soon response: best time to visit (day/time), what to anticipate, why worth the wait. Conversational. End with a question."
           VisitState.WANT_TO_GO -> "The user just tapped Visit on ${poi.name}. Lead with an engaging overview — highlight what makes it special and the best time to visit."
       }
   }
   ```

4. **`openChatForPoiVisit()` opens a fresh chat** (forceReset = true) — this is only called when AiDetailPage is opened directly from a Visit tap (carousel → detail). When Visit is tapped *inside* an already-open AiDetailPage, only `visitPoi()` is called on the VM; the existing conversation is preserved and no chat reset occurs. Two call paths: (a) carousel Visit tap → ViewModel calls both `visitPoi()` and opens detail page which calls `openChatForPoiVisit()` fresh; (b) detail page header Visit tap → only `visitPoi()` fires, chat unchanged.

5. **`fetchPoiContext()` guard in `openChatForPoiVisit()`** — check `_uiState.value.contextBlurb == null && !_uiState.value.isContextLoading` before launching `fetchPoiContext()`. If context already loaded (detail page was pre-opened), skip to avoid double Gemini call and race condition on context UI state.

6. **VisitState safe deserialization** in `SavedPoiRepositoryImpl.observeAll()` mapper:
   ```kotlin
   visitState = it.visit_state?.let { raw ->
       VisitState.entries.find { e -> e.name == raw }
   }
   ```
   Unknown values (future states, corrupted data) silently become `null`. Never use `VisitState.valueOf()` — throws on unknown input.

7. **Carousel is visit-only** — `PoiCarousel` has `onVisitTapped` but no `onUnvisitTapped`. Tapping "✓ Visited" on an already-visited carousel card does nothing (no action). Unvisit is only available from the `SavedPoiCard` in the Visited Places sheet. This is intentional — the carousel is a discovery surface, not a management surface.

8. **`unvisitPoi()` in `SavedPlacesViewModel` calls `savedPoiRepository.unsave()`** — this is correct and intentional. `visit()` uses `INSERT OR REPLACE` so the row exists; `unsave()` deletes the row. There is no separate `unvisit()` method on the repository because delete is delete regardless of visit state.

9. **`VisitState` badge on `SavedPoiCard`** — three visual states:
   - `GO_NOW` → green pill "Go Now"
   - `PLAN_SOON` → amber pill "Plan Soon"
   - `WANT_TO_GO` → grey pill "Want to Visit" (NOT invisible — must differentiate from unvisited/legacy rows)
   - `null` (legacy) → no badge

10. **`savedAt` field** stays on `SavedPoi` — display fallback for legacy rows. `visitedAt` is new, populated by `clock.nowMs()` in `repository.visit()`. Rollback on `visitPoi()` exception must reset both `visitedPoiIds` AND `visitedPois` list in `MapUiState` — not just `visitedPoiIds`.

---

## Implementation Plan

### Tasks

- [ ] **T1: Add `VisitState` enum + fields to domain model**
  - File: `domain/model/SavedPoi.kt`
  - Action: Add `enum class VisitState { WANT_TO_GO, GO_NOW, PLAN_SOON }` before the `SavedPoi` data class. Add two new fields with defaults: `val visitState: VisitState? = null` and `val visitedAt: Long? = null`.
  - Notes: Enum in same file as `SavedPoi` — tightly coupled. Nullable defaults ensure backward compat with all existing `SavedPoi(...)` constructor calls in tests.

- [ ] **T2: Verify migration version + update SQLDelight schema**
  - Pre-step: Check `migrations/` directory — confirm no existing `11.sqm`. Check `AreaDiscoveryDatabase.Schema.version` to confirm current version is 10.
  - File A: `data/local/saved_pois.sq`
  - Action A: Add two columns to the `CREATE TABLE` statement: `visit_state TEXT` (nullable, no DEFAULT) and `visited_at INTEGER` (nullable, no DEFAULT). Add new query:
    ```sql
    insertOrReplaceWithState:
    INSERT OR REPLACE INTO saved_pois(poi_id, name, type, area_name, lat, lng, why_special, saved_at, user_note, image_url, description, rating, vibe, visit_state, visited_at)
    VALUES (:poi_id, :name, :type, :area_name, :lat, :lng, :why_special, :saved_at, :user_note, :image_url, :description, :rating, :vibe, :visit_state, :visited_at);
    ```
    Add: `updateVisitState: UPDATE saved_pois SET visit_state = :visit_state, visited_at = :visited_at WHERE poi_id = :poi_id;`
  - File B: `data/local/migrations/11.sqm` (create new)
  - Action B:
    ```sql
    ALTER TABLE saved_pois ADD COLUMN visit_state TEXT;
    ALTER TABLE saved_pois ADD COLUMN visited_at INTEGER;
    ```
  - Notes: The existing `insertOrReplace` query stays unchanged — used by `save()` for backward compat.

- [ ] **T3: Add `visit()` to `SavedPoiRepository` interface**
  - File: `domain/repository/SavedPoiRepository.kt`
  - Action: Add `suspend fun visit(poi: SavedPoi)`. Keep `save()`, `unsave()`, `updateUserNote()` unchanged.

- [ ] **T4: Implement `visit()` in `SavedPoiRepositoryImpl` + safe enum deserialization**
  - File: `data/repository/SavedPoiRepositoryImpl.kt`
  - Action:
    1. Add `override suspend fun visit(poi: SavedPoi)` that calls `database.saved_poisQueries.insertOrReplaceWithState(...)` with all fields including `visit_state = poi.visitState?.name` and `visited_at = clock.nowMs()`.
    2. Update `observeAll()` mapper to read new fields using safe deserialization:
       ```kotlin
       visitState = it.visit_state?.let { raw ->
           VisitState.entries.find { e -> e.name == raw }
       },
       visitedAt = it.visited_at,
       ```
    3. **Do NOT use `VisitState.valueOf()`** — it throws `IllegalArgumentException` on unknown input. The `entries.find` pattern silently returns `null` for unknown/future values.

- [ ] **T5: Update `FakeSavedPoiRepository`**
  - File: `commonTest/fakes/FakeSavedPoiRepository.kt`
  - Action: Add `override suspend fun visit(poi: SavedPoi) { _pois.value = _pois.value + poi }` — functionally identical to `save()`. Keeps all VM tests compiling after T3.

- [ ] **T6: Add `VisitAction` to `ChatEntryPoint`**
  - File: `ui/map/ChatEntryPoint.kt`
  - Action: Add `data class VisitAction(val poi: POI, val visitState: VisitState) : ChatEntryPoint()`
  - Notes: Import `VisitState` from `domain.model`.

- [ ] **T7: Add `openChatForPoiVisit()` to `ChatViewModel` with inline framing hint**
  - File: `ui/map/ChatViewModel.kt`
  - Action:
    1. Add private helper:
       ```kotlin
       private fun buildVisitFramingHint(poi: POI, visitState: VisitState): String {
           val status = resolveStatus(poi.liveStatus, poi.hours)
           return when (visitState) {
               VisitState.GO_NOW -> "The user just tapped Visit on ${poi.name} — it is currently $status. Lead with a Go Now response: best approach right now, crowd level, one thing to do/order/see first. Conversational, not a checklist. End with a question."
               VisitState.PLAN_SOON -> "The user just tapped Visit on ${poi.name} — it is currently closed. Lead with a Plan Soon response: best time to visit (day/time), what to anticipate, why worth the wait. Conversational. End with a question."
               VisitState.WANT_TO_GO -> "The user just tapped Visit on ${poi.name}. Lead with an engaging overview — highlight what makes it special and the best time to visit."
           }
       }
       ```
    2. Add `fun openChatForPoiVisit(poi: POI, visitState: VisitState, areaName: String, pois: List<POI>, activeDynamicVibe: DynamicVibe?)`:
       - Calls `openChat(areaName, pois, activeDynamicVibe, ChatEntryPoint.VisitAction(poi, visitState), forceReset = true)`
       - Then `tapIntentPill(ContextualPill(label = poi.name, message = "I want to visit ${poi.name}.", intent = ChatIntent.DISCOVER, emoji = "📍"))`
       - Then, **only if context not already loaded**: `if (_uiState.value.contextBlurb == null && !_uiState.value.isContextLoading) { poiContextJob?.cancel(); poiContextJob = viewModelScope.launch { fetchPoiContext(poi, areaName) } }`
    3. In the `when (entryPoint)` block inside `openChat()`, add case: `is ChatEntryPoint.VisitAction -> buildVisitFramingHint(entryPoint.poi, entryPoint.visitState)`
  - Notes: framing hint is built in ChatViewModel, not GeminiPromptBuilder. `data/remote` never imports `ui/map`.

- [ ] **T8: Rename `saved*` → `visited*` in `MapUiState`**
  - File: `ui/map/MapUiState.kt`
  - Action: Rename fields in `MapUiState.Ready`:
    - `savedPoiIds: Set<String>` → `visitedPoiIds: Set<String>`
    - `savedPois: List<SavedPoi>` → `visitedPois: List<SavedPoi>`
    - `savedPoiCount: Int` → `visitedPoiCount: Int`
    - `showSavesSheet: Boolean` → `showVisitsSheet: Boolean`
    - `savedVibeFilter: Boolean` → `visitedFilter: Boolean`
  - Notes: This immediately breaks compilation in ~4 files. Do NOT commit until T9–T11 are complete.

- [ ] **T9: Update `MapViewModel` — add `visitPoi()`, rename methods + state refs**
  - File: `ui/map/MapViewModel.kt`
  - Action:
    1. Add `fun visitPoi(poi: POI, areaName: String)`:
       ```kotlin
       val poiId = poi.savedId
       val current = _uiState.value as? MapUiState.Ready ?: return
       val savedPoiObj = SavedPoi(id = poiId, ..., visitState = visitState, visitedAt = null)
       _uiState.value = current.copy(visitedPoiIds = current.visitedPoiIds + poiId, visitedPois = current.visitedPois + savedPoiObj)
       viewModelScope.launch {
           try { savedPoiRepository.visit(savedPoiObj) }
           catch (e: Exception) {
               // Rollback full state — both visitedPoiIds AND visitedPois
               val s = _uiState.value as? MapUiState.Ready ?: return@launch
               _uiState.value = s.copy(visitedPoiIds = s.visitedPoiIds - poiId, visitedPois = s.visitedPois.filter { it.id != poiId })
           }
       }
       ```
    2. Rename `unsavePoi()` → `unvisitPoi()`. Body calls `savedPoiRepository.unsave(poiId)` — this is correct; `unsave()` deletes the row regardless of visit state.
    3. Rename `openSavesSheet()` → `openVisitsSheet()`, `closeSavesSheet()` → `closeVisitsSheet()`.
    4. Rename `onSavedVibeSelected()` → `onVisitedFilterSelected()`.
    5. Update all internal state field references: `savedPoiIds`→`visitedPoiIds`, `savedPois`→`visitedPois`, `savedPoiCount`→`visitedPoiCount`, `showSavesSheet`→`showVisitsSheet`, `savedVibeFilter`→`visitedFilter`.
    6. Update `latestSavedPoiIds` private var → `latestVisitedPoiIds`.
    7. **Keep `latestSavedPois` unchanged** — this private var feeds `TasteProfileBuilder.build()` which requires the full visit history. It is intentionally not renamed to `latestVisitedPois` to avoid confusion with the public state field. Update it in the `observeAll()` collector to use the new `visit()` semantics but keep its name.

- [ ] **T10: Update `MapScreen.kt` call sites**
  - File: `ui/map/MapScreen.kt`
  - Action: Update all ~20 references:
    - `state.showSavesSheet` → `state.showVisitsSheet`
    - `state.savedPoiIds` → `state.visitedPoiIds`
    - `state.savedPois` → `state.visitedPois`
    - `state.savedVibeFilter` → `state.visitedFilter`
    - `state.savedPoiCount` → `state.visitedPoiCount`
    - `viewModel.savePoi(...)` → `viewModel.visitPoi(...)`
    - `viewModel.unsavePoi(...)` → `viewModel.unvisitPoi(...)`
    - `viewModel.closeSavesSheet()` → `viewModel.closeVisitsSheet()`
    - `viewModel.onSavedVibeSelected()` → `viewModel.onVisitedFilterSelected()`
    - `onSaveTapped` lambda → `onVisitTapped`
    - `onUnsaveTapped` lambda → `onUnvisitTapped`
    - `isSaved = ...` → `isVisited = ...`
    - `onSave = { viewModel.visitPoi(...) }` / `onUnsave = { viewModel.unvisitPoi(...) }` → `onVisit` / `onUnvisit`
    - AiDetailPage call: add `visitState = state.selectedPoi?.savedId?.let { if (it in state.visitedPoiIds) state.visitedPois.find { p -> p.id == it }?.visitState else null }`
    - `savedNearbyCount` local val → `visitedNearbyCount`
    - `onSavedPlaces` in `FabMenu` call → `onVisitedPlaces`

- [ ] **T11: Update `MapComposable.kt`, `POIListView.kt`, `FabMenu.kt` call sites**
  - File A: `ui/map/MapComposable.kt`
  - Action A: Rename params `savedPoiIds` → `visitedPoiIds`, `savedVibeFilter` → `visitedFilter`.
  - File B: `ui/map/POIListView.kt`
  - Action B: Rename params `savedPoiIds` → `visitedPoiIds`, `onSaveTapped` → `onVisitTapped`, `onUnsaveTapped` → `onUnvisitTapped`. In `PoiListItem`: `isSaved` → `isVisited`, `onSaveToggled` → `onVisitToggled`. Update icon: `Icons.Filled.Bookmark`/`BookmarkBorder` → `Icons.Filled.CheckCircle`/`Icons.Outlined.CheckCircleOutline`. Content description: `"Saved"`/`"Save"` → `"Visited"`/`"Mark as Visit"`.
  - File C: `ui/map/components/FabMenu.kt`
  - Action C: Rename param `onSavedPlaces: () -> Unit` → `onVisitedPlaces: () -> Unit`. Update usages inside the composable.

- [ ] **T12: Replace save button with Visit button in `PoiCarousel`**
  - File: `ui/map/components/PoiCarousel.kt`
  - Action: Replace `savedPoiIds: Set<String>` → `visitedPoiIds: Set<String>`. Replace `onSaveTapped: (POI) -> Unit` → `onVisitTapped: (POI) -> Unit`. **No `onUnvisitTapped`** — carousel is visit-only (see Technical Decision #7). Replace ★/☆ `Text` button with:
    - Unvisited: `TextButton("Visit")` with white outlined border
    - Visited: `TextButton("✓ Visited")` with green tint, disabled (`enabled = false` — tapping has no effect)
  - Update `val isSaved = poi.savedId in savedPoiIds` → `val isVisited = poi.savedId in visitedPoiIds`.

- [ ] **T13: Replace bookmark with Visit button in `AiDetailPage` / `PoiDetailHeader`**
  - File: `ui/map/components/AiDetailPage.kt`
  - Action:
    1. Replace params on `AiDetailPage`: `isSaved: Boolean` → `isVisited: Boolean`, add `visitState: VisitState?`, `onSave: () -> Unit` → `onVisit: () -> Unit`, `onUnsave: () -> Unit` → `onUnvisit: () -> Unit`.
    2. In `PoiDetailHeader`: replace `Icons.Filled.Bookmark` / `Icons.Filled.BookmarkBorder` `IconButton` with `TextButton` showing `"✓ Visited"` (green) when `isVisited`, else `"Visit"` (white outlined). On click: if `isVisited` → `onUnvisit()`, else → `onVisit()`.
    3. Pass params down to `PoiDetailHeader`.
    4. **When Visit is tapped from within an already-open detail page** (`onVisit` fires): only call `onVisit()` (which triggers `MapViewModel.visitPoi()`). **Do NOT call `openChatForPoiVisit()`** from inside the detail page — that resets the conversation. The existing conversation is preserved. `openChatForPoiVisit()` is only called when the detail page is opened fresh from a carousel Visit tap.

- [ ] **T14: Update `SavedPlacesViewModel`**
  - File: `ui/saved/SavedPlacesViewModel.kt`
  - Action: Rename `unsavePoi(poiId: String)` → `unvisitPoi(poiId: String)`. Body calls `savedPoiRepository.unsave(poiId)` — this is correct and intentional. `visit()` uses `INSERT OR REPLACE`, so the row exists; `unsave()` deletes it. There is no separate `unvisit()` on the repository because delete is delete regardless of visit state.

- [ ] **T15: Update `SavedPlacesScreen` title + call sites**
  - File: `ui/saved/SavedPlacesScreen.kt`
  - Action: Change screen title `"Saved Places"` → `"Visited Places"`. If using a string resource: update `strings.xml` and all locale files (pt-BR minimum). Update `viewModel.unsavePoi(...)` → `viewModel.unvisitPoi(...)`.

- [ ] **T16: Add `VisitState` badge to `SavedPoiCard`**
  - File: `ui/saved/components/SavedPoiCard.kt`
  - Action: Add `visitState: VisitState? = null` param. In the card action row, replace `Icons.Filled.Bookmark` save indicator with a badge pill:
    - `GO_NOW` → green pill, label "Go Now"
    - `PLAN_SOON` → amber pill, label "Plan Soon"
    - `WANT_TO_GO` → grey pill, label "Want to Visit" (**must show** — differentiates intentional visits from legacy null rows)
    - `null` (legacy) → no badge
    Pill style: `Surface(shape = RoundedCornerShape(12.dp), color = badgeColor) { Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }`
    Update `SavedPlacesScreen` call sites to pass `poi.visitState`.

- [ ] **T17: Write tests**
  - File A: `androidUnitTest/data/repository/SavedPoiRepositoryTest.kt`
  - Action A: Add 3 tests using in-memory SQLDelight DB:
    - `visit() stores visitState in DB` — call `repository.visit(poi.copy(visitState = VisitState.GO_NOW))`, observe, assert `first().visitState == VisitState.GO_NOW`
    - `visit() stores visitedAt from clock` — assert `first().visitedAt == fakeClock.nowMs`
    - `legacy save() rows have null visitState` — call `repository.save(poi)`, observe, assert `first().visitState == null`
  - File B: `commonTest/ui/map/MapViewModelTest.kt`
  - Action B: Add 4 tests (use `FakeSavedPoiRepository`):
    - `visitPoi routes GO_NOW when resolveStatus returns open` — POI with `hours = "9am-10pm"` + current time mid-day → assert `visitedPoiIds` contains poi.savedId and `FakeSavedPoiRepository` received `GO_NOW`
    - `visitPoi routes PLAN_SOON when resolveStatus returns closed`
    - `visitPoi routes WANT_TO_GO when liveStatus and hours are null`
    - `unvisitPoi removes poi from visitedPoiIds and visitedPois`
  - File C: `commonTest/ui/map/ChatViewModelTest.kt`
  - Action C: Add 2 tests using `chatViewModel.systemContextForTest` accessor:
    - `openChatForPoiVisit with GO_NOW inserts Go Now framing hint into system context` — assert `systemContextForTest.contains("Go Now response")`
    - `openChatForPoiVisit with PLAN_SOON inserts Plan Soon framing hint into system context` — assert `systemContextForTest.contains("Plan Soon response")`

---

### Acceptance Criteria

- [ ] **AC1:** Given a POI with `liveStatus = "open"` or `hours` that `resolveStatus()` evaluates as "open", when the user taps "Visit", then `MapViewModel` saves the POI with `VisitState.GO_NOW` and the button shows "✓ Visited" (green).
- [ ] **AC2:** Given a POI with `liveStatus = "closed"` or `hours` that `resolveStatus()` evaluates as "closed", when the user taps "Visit", then `MapViewModel` saves the POI with `VisitState.PLAN_SOON`.
- [ ] **AC3:** Given a POI with null `liveStatus` and null `hours`, when the user taps "Visit", then `MapViewModel` saves the POI with `VisitState.WANT_TO_GO`.
- [ ] **AC4 (manual):** Given the user taps "Visit" from the carousel (opening a fresh AiDetailPage), when the detail page loads, then the AI intro message reflects the correct visit state — Go Now context (crowd, approach, what to do) for open POIs, Plan Soon timing suggestion for closed POIs. Verified manually; automated proxy: ChatViewModelTest T17-C asserts the framing hint is present in the system context.
- [ ] **AC5:** Given a POI is already visited (`isVisited = true`), when the user taps "✓ Visited" in `AiDetailPage` header (unvisit), then the POI is removed from `visitedPoiIds` + `visitedPois` and the button returns to "Visit." The existing chat conversation is NOT reset.
- [ ] **AC6:** Given the Visited Places sheet is open, when each `SavedPoiCard` renders, then: `GO_NOW` shows green "Go Now" pill, `PLAN_SOON` shows amber "Plan Soon" pill, `WANT_TO_GO` shows grey "Want to Visit" pill, and legacy `null` rows show no badge.
- [ ] **AC7:** Given a device upgrading from schema version 10, when the app launches, then `11.sqm` migration runs without crash and all existing saved POIs appear with `visitState = null` and the app is functional.
- [ ] **AC8:** Given the user is on the Visited Places screen, when it renders, then the screen title shows "Visited Places."
- [ ] **AC9:** Given a `POIListView` row for a visited POI, when it renders, then it shows `CheckCircle` icon (not Bookmark).
- [ ] **AC10:** Given a `POIListView` row for an unvisited POI, when the user taps the visit icon, then `onVisitTapped` fires and the icon updates to `CheckCircle`.

---

## Additional Context

### Dependencies

- `PinStatusUtils.resolveStatus()` — already exists; no changes needed
- SQLDelight migration v10→11; `AreaDiscoveryDatabase.Schema` auto-picks up `11.sqm` — verify no conflict before creating
- `FakeSavedPoiRepository` must be updated (T5) before any ViewModel tests compile post-rename
- T8–T11 must land in one atomic commit (MapUiState rename + all call site fixes)

### Testing Strategy

**Automated (9 new tests):**
- `SavedPoiRepositoryTest` (+3): DB visit storage, visitedAt clock, legacy null state
- `MapViewModelTest` (+4): GO_NOW routing, PLAN_SOON routing, WANT_TO_GO routing, unvisit removes from both fields
- `ChatViewModelTest` (+2): GO_NOW framing hint in system context, PLAN_SOON framing hint in system context

**Manual smoke tests:**
- After T13: Tap Visit on open POI from carousel → fresh AiDetailPage → AI leads with Go Now context
- After T13: Tap Visit on closed POI from carousel → AI leads with Plan Soon timing
- After T13: Open detail page, chat 3 turns, tap Visit → conversation preserved, visit recorded
- After T16: Open Visited Places → Go Now/Plan Soon/Want to Visit badges visible, legacy rows have no badge

### Notes

- **No circular import:** `GeminiPromptBuilder` (`data/remote`) is NOT touched by this feature. Framing hint logic is entirely in `ChatViewModel` (`ui/map`).
- **Brainstorm source:** `_bmad-output/brainstorming/brainstorming-session-2026-03-15-002.md`
- **Prototype:** `_bmad-output/brainstorming/prototype-visit-journey-v4.html`
- **Key principle:** User does NOT choose Go Now vs Plan Soon — routing is automatic based on `resolveStatus()`.
- **Post-ship tech debt:** Visit count tracking (v1.1). Proximity auto-detect (v1.1). Plan Trip with backend (v2).
- **`latestSavedPois`** private var in MapViewModel is intentionally not renamed — it feeds TasteProfileBuilder and its name is irrelevant to users. Only the public MapUiState fields are renamed.
