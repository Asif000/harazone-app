---
title: 'Saved Places List Redesign'
slug: 'saved-places-list-redesign'
created: '2026-03-10'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin', 'Compose Multiplatform', 'Koin', 'SQLDelight', 'Coil3', 'kotlinx.coroutines.test', 'kotlin.test']
files_to_modify:
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq'
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/7.sqm (NEW — verify number first)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesViewModel.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesUiState.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt (NEW)'
  - 'composeApp/src/commonTest/kotlin/com/harazone/ui/saved/SavedPlacesViewModelTest.kt (NEW)'
code_patterns:
  - 'StateFlow<UiState> + ViewModel.viewModelScope coroutines'
  - 'Koin viewModel { } registration in UiModule.kt'
  - 'SQLDelight ALTER TABLE migration in numbered .sqm files'
  - 'Optimistic UI update then Snackbar-driven DB commit'
  - 'AnimatedVisibility overlay pattern (see ChatOverlay in MapScreen)'
  - 'PlatformBackHandler on every dismissible overlay'
  - 'AsyncImage with ColorPainter placeholder (Coil3)'
  - 'DisposableEffect onDispose for cleanup on screen exit'
  - 'kotlin.test + UnconfinedTestDispatcher for ViewModel tests in commonTest'
test_patterns:
  - 'commonTest — ViewModel tests use kotlin.test + kotlinx.coroutines.test'
  - 'FakeSavedPoiRepository (already exists at commonTest/fakes/)'
  - 'runTest + UnconfinedTestDispatcher pattern (see MapViewModelTest.kt)'
---

# Tech-Spec: Saved Places List Redesign

**Created:** 2026-03-10

## Overview

### Problem Statement

The current saved places experience (`SavesBottomSheet` in `MapScreen.kt:390`) is a plain `ModalBottomSheet` with basic text-only `SavedPoiRow` composables — name, type, area, note, date only. Users cannot unsave, get directions, share, or ask AI from the list. There are no images, no distance context, no filtering, and no sense of discovery. It feels like a database dump, not a curated personal collection.

### Solution

Replace `SavesBottomSheet` with a full-screen `SavedPlacesScreen` composable. Compact uniform cards (100dp image area + info panel) with 4 action chips matching `ExpandablePoiCard`. Distance capsule filters via local haversine compute grouped by `areaName`. AI Discovery Story card at the top (static, derived from save data — no API call). Fixed bottom bar with "Ask AI about saves..." and Map dismiss button. `image_url` added to DB and stored at save time for richer cards.

### Scope

**In Scope:**
- New `SavedPlacesScreen` composable — full-screen overlay, replaces `SavesBottomSheet`
- New `SavedPlacesViewModel` — exposes saved list, distance capsule filters, search state
- New `SavedPlacesUiState` + `DistanceCapsule` + `DiscoveryStory` data classes
- New `SavedPoiCard` component — 100dp image area (real photo or type gradient + emoji), name, area, type badge, date, 4 actions
- Distance capsule filters — local haversine compute, grouped by `SavedPoi.areaName`, nearest cluster highlighted green
- Search within saves — local string filter on name + areaName
- 4 card actions: Unsave (immediate hide + Snackbar-driven undo), Directions, Share, Ask AI (opens ChatOverlay pre-seeded)
- AI Discovery Story card — static, derived from save data; shown when saves ≥ 2
- Fixed bottom bar: "Ask AI about saves...", Map toggle (dismisses), FAB (opens ChatOverlay)
- `PlatformBackHandler` to dismiss
- DB migration: add nullable `image_url TEXT` column to `saved_pois` table
- Write `poi.imageUrl` at save time in `MapViewModel.savePoi` and `ChatViewModel.savePoi`
- Koin registration for `SavedPlacesViewModel`
- Unit tests: capsule generation, search filter, unsave+undo, discovery story, compound filter

**Out of Scope:**
- AI Picks capsule filter (removed)
- AI nudge banners between cards (deferred)
- Real-time open/closed status (requires Google Places integration)
- Gemini-generated Discovery Story (static/derived only)
- "Closed Now" capsule filter (no hours data in `SavedPoi`)
- Currency conversion for international saves (deferred)
- Multi-view / Collections / Timeline (Profile Page #19)

---

## Context for Development

### Codebase Patterns

- Package root is `com.harazone` — all new files go in `com.harazone.*`
- DI: Koin — new ViewModel registered in `UiModule.kt`
- UI: Jetpack Compose Multiplatform in `commonMain`
- State: `StateFlow<UiState>` pattern — see `MapUiState.kt` and `ChatUiState.kt`
- DB: SQLDelight — schema in `.sq` files, migrations as `N.sqm` files. Verify actual latest file number before creating new migration.
- Overlay pattern: `AnimatedVisibility(visible = flag, enter = fadeIn(), exit = fadeOut())` — see `ChatOverlay` wiring in `MapScreen.kt`
- Save/unsave: optimistic local update in ViewModel — see `MapViewModel.savePoi/unsavePoi`
- `PlatformBackHandler`: always add to dismissible overlays — `com.harazone.ui.components.PlatformBackHandler`
- Images: Coil 3 `AsyncImage` with `ColorPainter` placeholder — see `ExpandablePoiCard.kt:107`
- Chat open pattern: `chatViewModel.openChat(areaName, pois, activeVibe, entryPoint)` then `chatViewModel.sendMessage(msg)` — see `MapScreen.kt:262`
- GPS: `MapUiState.Ready.gpsLatitude/gpsLongitude` + `showMyLocation: Boolean` — use `showMyLocation` to determine GPS validity
- Tests: `commonTest`, `kotlin.test`, `UnconfinedTestDispatcher` — see `MapViewModelTest.kt`

### Files to Reference

| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt:262,390` | Chat open pattern at :262; `SavesBottomSheet` to replace at :390 |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt` | `ChatEntryPoint.SavesSheet` already defined — use for onAskAi wiring |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt:71` | `openChat(areaName, pois, activeVibe, entryPoint)` + `sendMessage(msg)` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt:155` | `savePoi` — add `imageUrl` field |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt:260` | `savePoi` — add `imageUrl = null` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt` | 4 action chips pattern + image loading pattern to replicate |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt` | Domain model — add `imageUrl: String?` |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq` | DB schema — add `image_url TEXT` + update `insertOrReplace` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt` | Update insert + row mapping for `imageUrl` |
| `composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt` | Add `viewModel { SavedPlacesViewModel(get()) }` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | `gpsLatitude`, `gpsLongitude`, `showMyLocation` fields |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/components/PlatformBackHandler.kt` | Must use in `SavedPlacesScreen` |
| `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeSavedPoiRepository.kt` | Use in ViewModel tests |
| `/tmp/saves-final-v7.html` | Final visual design reference |

### Technical Decisions

1. **Full-screen via `AnimatedVisibility`**: `SavedPlacesScreen` rendered as a `Box` overlay in `MapScreen` using `AnimatedVisibility(visible = state.showSavesSheet)`. No nav graph change needed. Same pattern as `ChatOverlay`.

2. **DB migration**: Verify the highest-numbered `.sqm` file in `migrations/` directory before creating the new one (as of spec creation, latest is `6.sqm`, expected next is `7.sqm`). Add `ALTER TABLE saved_pois ADD COLUMN image_url TEXT;` — nullable, backfills as null for all existing rows. Update `insertOrReplace` in `.sq` file to add `:image_url` parameter.

3. **Distance capsule algorithm** (pure local, no API):
   - Receive `userLat: Double?, userLng: Double?` — nullable. `null` means GPS unavailable.
   - At call site in `MapScreen`: pass `state.gpsLatitude.takeIf { state.showMyLocation }` and `state.gpsLongitude.takeIf { state.showMyLocation }`.
   - For each `SavedPoi`, compute haversine distance using stored `lat`/`lng` (see T5 for haversine helper spec).
   - Group by `areaName` — track min distance and count per group.
   - Sort groups by min distance ascending → produces `List<DistanceCapsule>`.
   - Nearest group: `isNearest = true` (green styling in UI).
   - GPS null: sort groups by count descending, no nearest highlight.
   - "All" capsule always prepended with total count.
   - `DistanceCapsule` does NOT carry `isActive` — active state derived in UI as `capsule.label == uiState.activeCapsule || (uiState.activeCapsule == null && capsule.label == "All")`.

4. **AI Discovery Story** (static, no Gemini):
   - Computed in `SavedPlacesViewModel` from full `saves` list.
   - `summary`: `"${saves.size} places across ${uniqueAreaCount} areas"`.
   - `tags`: up to 3 — most-frequent type label, "X countries" if multi-country saves, "note keeper" if any `userNote != null`.
   - Exposed as `DiscoveryStory?` in UiState — null when `saves.size < 2`.

5. **Unsave with Snackbar-driven undo** (no separate Job timer — eliminates duration race condition):
   - `fun unsavePoi(poiId: String)`: add `id` to `pendingUnsaveIds` only. Card disappears from `filteredSaves` immediately. No Job started.
   - `fun commitUnsave(poiId: String, undo: Boolean)`: if `undo == true` → remove from `pendingUnsaveIds` (card reappears, no DB call). If `undo == false` → call `repository.unsave(id)` + remove from `pendingUnsaveIds`.
   - `fun commitAllPendingUnsaves()`: for each id in `pendingUnsaveIds`, call `repository.unsave(id)`. Clear `pendingUnsaveIds`. Used on screen dispose.
   - In `SavedPlacesScreen`, unsave flow:
     ```kotlin
     viewModel.unsavePoi(poi.id)
     scope.launch {
         val result = snackbarHostState.showSnackbar(
             message = "Removed",
             actionLabel = "Undo",
             duration = SnackbarDuration.Long   // ~10s — gives real undo window
         )
         viewModel.commitUnsave(poi.id, undo = result == SnackbarResult.ActionPerformed)
     }
     ```
   - `DisposableEffect(Unit) { onDispose { viewModel.commitAllPendingUnsaves() } }` in `SavedPlacesScreen` — commits all pending deletes silently when screen exits for any reason (dismiss, back button, etc.).
   - `SnackbarHostState` managed in `SavedPlacesScreen`, not ViewModel.

6. **GPS passthrough** (avoids double LocationProvider subscription):
   - `SavedPlacesViewModel` injects only `SavedPoiRepository`.
   - `MapScreen` passes `userLat` / `userLng` as parameters to `SavedPlacesScreen` at open time.
   - `SavedPlacesScreen` calls `viewModel.onLocationUpdated(userLat, userLng)` via `LaunchedEffect`.

7. **`onAskAi` wiring** (exact, no guesswork):
   - `SavedPlacesScreen` calls `onAskAi("Tell me more about ${poi.name}")` when card Ask AI tapped.
   - In `MapScreen`, `onAskAi` lambda: `{ msg -> viewModel.closeSavesSheet(); chatViewModel.openChat(state.areaName, state.pois, state.activeVibe, ChatEntryPoint.SavesSheet); chatViewModel.sendMessage(msg) }`.
   - `ChatEntryPoint.SavesSheet` already exists in `ChatEntryPoint.kt`.
   - Bottom bar "Ask AI about saves..." uses: `onAskAi("What should I do with my saved places in ${state.areaName}?")`.

8. **Type-to-gradient mapping**: private `fun gradientForType(type: String): Brush` in `SavedPoiCard.kt`. Call `type.lowercase()` before matching keywords. Mappings: music/jazz → purple, food/restaurant/cafe/eat → amber, art/gallery/mural/street → gold, park/nature/garden/green → green, museum/history/heritage → teal, default → `Color(0xFF1e2028)` solid. This is best-effort — most saves will show gradient until real images are stored.

9. **Uniform card height**: Both `poi.name` and `poi.areaName` text fields MUST have `maxLines = 1, overflow = TextOverflow.Ellipsis` to prevent variable card heights.

10. **Empty state header**: When `saves.isEmpty()`, show title row with count "0" but hide the capsule `LazyRow` and hide/disable the search `TextField`. Show centered empty state message in the list area. Do not show Discovery Story card.

---

## Implementation Plan

### Tasks

- [x] **T1 — DB migration + schema update**
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/` — first verify the highest existing `.sqm` number, then create `<N+1>.sqm`
  - Action: Single line: `ALTER TABLE saved_pois ADD COLUMN image_url TEXT;`
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq`
  - Action: Add `image_url TEXT` to `CREATE TABLE` statement. Add `:image_url` parameter to `insertOrReplace` query.

- [x] **T2 — Domain model: add `imageUrl`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt`
  - Action: Add `val imageUrl: String? = null` field. Default value prevents breaking existing call sites.

- [x] **T3 — Repository: thread `imageUrl` through**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt`
  - Action: In `save()`, add `image_url = poi.imageUrl` to `insertOrReplace`. In `observeAll()` row mapping, add `imageUrl = it.image_url`.

- [x] **T4 — Write `imageUrl` at save time**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt:155`
  - Action: In `savePoi(poi, areaName)`, add `imageUrl = poi.imageUrl` to `SavedPoi(...)` constructor.
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt:260`
  - Action: In `savePoi(card, areaName)`, add `imageUrl = null` to `SavedPoi(...)` constructor. (`ChatPoiCard` has no `imageUrl` — gradient fallback is intentional.)

- [x] **T5 — `SavedPlacesUiState` models + haversine helper**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesUiState.kt` (create new)
  - Action: Define the following. Note `DistanceCapsule` has NO `isActive` field — active state derived in UI:
    ```kotlin
    data class DistanceCapsule(
        val label: String,      // areaName or "All"
        val count: Int,
        val isNearest: Boolean, // true for the geographically closest group
    )

    data class DiscoveryStory(
        val summary: String,
        val tags: List<String>,
    )

    data class SavedPlacesUiState(
        val saves: List<SavedPoi> = emptyList(),
        val filteredSaves: List<SavedPoi> = emptyList(),
        val capsules: List<DistanceCapsule> = emptyList(),
        val activeCapsule: String? = null,   // null = "All" selected
        val searchQuery: String = "",
        val discoveryStory: DiscoveryStory? = null,
        val pendingUnsaveIds: Set<String> = emptySet(),
    )
    ```
  - Also create private haversine helper (can be a top-level internal fun in this file or a separate `GeoUtils.kt`):
    ```kotlin
    internal fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return R * 2 * asin(sqrt(a))
    }
    ```

- [x] **T6 — `SavedPlacesViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesViewModel.kt` (create new)
  - Action: Implement with constructor `SavedPlacesViewModel(private val savedPoiRepository: SavedPoiRepository)`:
    - Private vars: `private var userLat: Double? = null`, `private var userLng: Double? = null`
    - `init`: `viewModelScope.launch { savedPoiRepository.observeAll().collect { updateState(it) } }`
    - `fun onLocationUpdated(lat: Double?, lng: Double?)`: store coords, recompute capsules, update state
    - `fun selectCapsule(label: String?)`: update `activeCapsule`, recompute `filteredSaves`
    - `fun onSearchQueryChanged(query: String)`: update `searchQuery`, recompute `filteredSaves`
    - `fun unsavePoi(poiId: String)`: add to `pendingUnsaveIds`, update state. No Job, no timer.
    - `fun commitUnsave(poiId: String, undo: Boolean)`: if undo → remove from `pendingUnsaveIds`; else → `viewModelScope.launch { savedPoiRepository.unsave(poiId) }` + remove from `pendingUnsaveIds`
    - `fun commitAllPendingUnsaves()`: `viewModelScope.launch { pendingUnsaveIds.forEach { savedPoiRepository.unsave(it) }; clearPending() }`
    - `filteredSaves`: `saves.filter { it.id !in pendingUnsaveIds && matchesCapsule(it) && matchesSearch(it) }`
    - Capsule generation: if `userLat != null && userLng != null` → compute haversine per save, group by `areaName`, sort by min distance, mark nearest. Else → sort by count desc. Prepend "All" capsule.
    - Discovery story: null when `saves.size < 2`; else summary + up to 3 tags from save patterns.
  - Notes: Inject `SavedPoiRepository` only. No `LocationProvider`.

- [x] **T7 — Register `SavedPlacesViewModel` in Koin**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt`
  - Action: Add `viewModel { SavedPlacesViewModel(get()) }` to the `module { }` block.

- [x] **T8 — `SavedPoiCard` component**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt` (create new)
  - Action: Implement:
    ```kotlin
    @Composable
    fun SavedPoiCard(
        poi: SavedPoi,
        isPendingUnsave: Boolean,
        onUnsave: () -> Unit,
        onDirections: () -> Unit,
        onShare: () -> Unit,
        onAskAi: () -> Unit,
        modifier: Modifier = Modifier,
    )
    ```
    - Apply `modifier` to the root `Card(modifier = modifier, ...)` composable — do not swallow it.
    - Card alpha: `Modifier.alpha(if (isPendingUnsave) 0.4f else 1f)` applied to root Card.
    - Image area (height = 100.dp): if `poi.imageUrl != null` → `AsyncImage(model = poi.imageUrl, placeholder = ColorPainter(...), contentScale = ContentScale.Crop)`; else → `Box` with `background(gradientForType(poi.type))` + `Text` (emoji, 44.sp, alpha 0.15f) centered.
    - Card body below image: name `Text(poi.name, style = titleSmall, color = White, maxLines = 1, overflow = Ellipsis)`, area `Text(poi.areaName, style = bodySmall, color = White.copy(alpha=0.4f), maxLines = 1, overflow = Ellipsis)`.
    - Footer row: left side → type badge + saved date `"MMM d"` format; right side → 4 `IconButton` actions.
    - 4 actions (right-aligned): `Icons.Default.Bookmark` (gold tint, `onUnsave`), `Icons.Default.Directions` (`onDirections`), `Icons.Default.Share` (`onShare`), `Icons.Default.AutoAwesome` (`onAskAi`).
    - Private `fun gradientForType(type: String): Brush`: call `type.lowercase()` first. Keyword matches: contains "music" or "jazz" → purple gradient; contains "food", "restaurant", "cafe", "eat" → amber; contains "art", "gallery", "mural", "street art" → gold; contains "park", "nature", "garden", "green" → green; contains "museum", "history", "heritage" → teal; default → `Brush.linearGradient(listOf(Color(0xFF1a1d24), Color(0xFF1e2128)))`.
    - Note: DiscoveryStoryCard colors (`Color(0xFF7C4DFF)` purple, `Color(0xFFFFD700)` gold) should be defined as file-level `private val` constants in `SavedPlacesScreen.kt`, not as inline magic numbers.

- [x] **T9 — `DiscoveryStoryCard` composable**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt` (inline private composable)
  - Action: `@Composable private fun DiscoveryStoryCard(story: DiscoveryStory, modifier: Modifier = Modifier)`:
    - Define at top of file: `private val storyPurple = Color(0xFF7C4DFF)` and `private val storyGold = Color(0xFFFFD700)`.
    - Background: `Brush.linearGradient(listOf(storyPurple.copy(alpha=0.07f), storyGold.copy(alpha=0.04f)))`.
    - Border: `border(1.dp, storyPurple.copy(alpha=0.12f), RoundedCornerShape(20.dp))`.
    - Content: `Icon(AutoAwesome)` top-right, "YOUR DISCOVERY STORY" label (`labelSmall`, `storyPurple`), `story.summary` text (`bodyMedium`), `story.tags` as `SuggestionChip` row.

- [x] **T10 — `SavedPlacesScreen`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt` (create new)
  - Action: Implement:
    ```kotlin
    @Composable
    fun SavedPlacesScreen(
        userLat: Double?,
        userLng: Double?,
        onDismiss: () -> Unit,
        onAskAi: (String) -> Unit,
        onDirections: (Double, Double, String) -> Unit,
        onShare: (String) -> Unit,
        viewModel: SavedPlacesViewModel = koinViewModel(),
    )
    ```
    - `LaunchedEffect(userLat, userLng) { viewModel.onLocationUpdated(userLat, userLng) }`
    - `DisposableEffect(Unit) { onDispose { viewModel.commitAllPendingUnsaves() } }` — commits all pending deletes on any exit.
    - `PlatformBackHandler(enabled = true) { onDismiss() }`
    - `val snackbarHostState = remember { SnackbarHostState() }` + `val scope = rememberCoroutineScope()`
    - Structure: `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })` or manual `Box`:
      1. Sticky header (non-scrolling): title row "Saved Places" + count, search `TextField` (hidden/disabled when `saves.isEmpty()`), capsule `LazyRow` (hidden when `saves.isEmpty()`). Active capsule chip derived as `capsule.label == uiState.activeCapsule || (uiState.activeCapsule == null && capsule.label == "All")`.
      2. `LazyColumn(contentPadding = PaddingValues(bottom = 80.dp))`:
         - `DiscoveryStoryCard` if `uiState.discoveryStory != null`
         - Empty state (centered text "No saved places yet — start exploring!") if `uiState.filteredSaves.isEmpty()` AND `uiState.saves.isEmpty()`
         - `SavedPoiCard` per save, keyed by `poi.id`, `isPendingUnsave = poi.id in uiState.pendingUnsaveIds`
      3. Fixed bottom bar: "Ask AI about saves..." `TextButton` (`onAskAi("What should I do with my saved places?")`), "Map" `TextButton` (`onDismiss()`), FAB `FloatingActionButton`.
    - Unsave flow per card:
      ```kotlin
      onUnsave = {
          viewModel.unsavePoi(poi.id)
          scope.launch {
              val result = snackbarHostState.showSnackbar(
                  message = "Removed",
                  actionLabel = "Undo",
                  duration = SnackbarDuration.Long  // ~10s
              )
              viewModel.commitUnsave(poi.id, undo = result == SnackbarResult.ActionPerformed)
          }
      }
      ```

- [x] **T11 — Wire `SavedPlacesScreen` into `MapScreen`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action:
    1. Delete `SavesBottomSheet` private composable and `SavedPoiRow` private composable (lines ~419–495).
    2. Replace `if (state.showSavesSheet) { SavesBottomSheet(...) }` block with:
       ```kotlin
       AnimatedVisibility(
           visible = state.showSavesSheet,
           enter = fadeIn(),
           exit = fadeOut(),
       ) {
           SavedPlacesScreen(
               userLat = state.gpsLatitude.takeIf { state.showMyLocation },
               userLng = state.gpsLongitude.takeIf { state.showMyLocation },
               onDismiss = { viewModel.closeSavesSheet() },
               onAskAi = { msg ->
                   viewModel.closeSavesSheet()
                   chatViewModel.openChat(state.areaName, state.pois, state.activeVibe, ChatEntryPoint.SavesSheet)
                   chatViewModel.sendMessage(msg)
               },
               onDirections = { lat, lng, name -> onNavigateToMaps(lat, lng, name) },
               onShare = { /* TODO: platform share intent — stub for now */ },
           )
       }
       ```
    3. The existing `PlatformBackHandler(enabled = state.showSavesSheet)` at ~line 398 can be removed — `SavedPlacesScreen` handles its own back handler internally.
    4. Add import for `SavedPlacesScreen` and `ChatEntryPoint`.

- [x] **T12 — Unit tests**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/saved/SavedPlacesViewModelTest.kt` (create new)
  - Action: 10 tests using `kotlin.test`, `runTest`, `UnconfinedTestDispatcher`, `FakeSavedPoiRepository`:
    1. `capsule_nearestGroup_isHighlighted` — 3 areas at known coords, user GPS near area B → B capsule `isNearest = true`, ordered B first
    2. `capsule_noGps_sortedByCount` — `onLocationUpdated(null, null)` → capsules sorted by count desc, no capsule has `isNearest = true`
    3. `search_matchesName_caseInsensitive` — query "jazz" → save with name "Blue Note Jazz Club" appears in `filteredSaves`
    4. `search_matchesAreaName` — query "wynwood" → save with `areaName = "Wynwood, Miami"` appears
    5. `capsule_and_search_intersection` — select capsule "Wynwood", then query "jazz" → `filteredSaves` contains only Wynwood saves whose name/area contains "jazz"; saves from other areas excluded even if they match query
    6. `unsave_removesFromFilteredImmediately` — `unsavePoi(id)` → `filteredSaves` no longer contains that id
    7. `undo_restoresCard` — `unsavePoi(id)` then `commitUnsave(id, undo=true)` → card back in `filteredSaves`
    8. `undo_preventsDbDelete` — `commitUnsave(id, undo=true)` → `FakeSavedPoiRepository.unsaveCallCount == 0`
    9. `discoveryStory_nullWhenFewerThanTwoSaves` — 1 save → `uiState.discoveryStory == null`
    10. `discoveryStory_generatedWithCorrectCounts` — 5 saves across 3 areas → `story.summary` contains "5" and "3"

### Acceptance Criteria

- [x] **AC1 — Full-screen surface**: Given the user taps "Saved Places" in the FAB menu, when the screen opens, then a full-screen overlay appears (not a bottom sheet) covering the map entirely, with sticky header, scrollable card list, and fixed bottom bar.

- [x] **AC2 — Uniform card layout**: Given saves exist, when the list renders, then each card shows a 100dp image area (real photo when `imageUrl` non-null, else type-based gradient + emoji), name (1 line, ellipsis), area (1 line, ellipsis), type badge, saved date, and 4 action icons; all cards are visually the same height.

- [x] **AC3 — Image stored at save time**: Given a user saves a POI that has an `imageUrl`, when they open Saved Places, then the card shows the real image. Given a POI was saved without `imageUrl` (including all chat-side saves), then the card shows the type gradient fallback.

- [x] **AC4 — Distance capsule filters with GPS**: Given the user has GPS (`showMyLocation = true`) and saves across multiple `areaName` values, when the screen opens, then capsule chips show one chip per unique `areaName` sorted by proximity; the nearest chip is styled green; tapping it filters the list to saves with that `areaName`; tapping "All" restores the full list.

- [x] **AC5 — Distance capsules without GPS**: Given `showMyLocation = false`, when the screen opens, then capsule chips appear sorted by count descending, no chip is styled green as nearest.

- [x] **AC6 — Search within saves**: Given the user types in the search field, when typing, then the list filters in real time to saves where `name` or `areaName` contains the query (case-insensitive); clearing the field restores the full (capsule-filtered) list.

- [x] **AC7 — Unsave with timed undo**: Given a save is visible in the list, when the user taps Unsave, then the card disappears immediately; a Snackbar with "Undo" action appears (~10s); if Undo is tapped, the card reappears and no DB delete is committed; if the Snackbar times out, the delete is committed to DB.

- [x] **AC8 — Unsave on dismiss**: Given the user has tapped Unsave on a card and the Snackbar is still visible, when the user dismisses the screen (Map button, back button, or any other method), then the pending delete is committed to the DB (no data loss, no stuck pending state).

- [x] **AC9 — Directions**: Given a save card is visible, when the user taps Directions, then `onDirections(poi.lat, poi.lng, poi.name)` is called.

- [x] **AC10 — Ask AI on card**: Given a save card is visible, when the user taps Ask AI (✨), then the saves screen closes and `ChatOverlay` opens pre-seeded with `"Tell me more about {poi.name}"`.

- [x] **AC11 — AI Discovery Story card**: Given the user has 2 or more saves, when the screen opens, then a "Your Discovery Story" card appears at the top with a summary and up to 3 personality tags. Given fewer than 2 saves, the card is not shown.

- [x] **AC12 — Android back button**: Given the Saved Places screen is open, when the user presses the Android back button, then the screen dismisses and the map is visible (does NOT exit the app).

- [x] **AC13 — Empty state**: Given the user has no saves, when the screen opens, then an empty state message "No saved places yet — start exploring!" is shown; the capsule row and search bar are hidden; no Discovery Story card is rendered.

---

## Additional Context

### Dependencies

- `SavedPoiRepository.observeAll()` — exists, returns `Flow<List<SavedPoi>>`
- `MapUiState.Ready.gpsLatitude`, `gpsLongitude`, `showMyLocation` — user GPS available in existing state
- `MapViewModel.openSavesSheet/closeSavesSheet` — existing, no changes needed
- `ChatViewModel.openChat(areaName, pois, activeVibe, entryPoint)` + `sendMessage(msg)` — existing
- `ChatEntryPoint.SavesSheet` — already defined in `ChatEntryPoint.kt`
- `PlatformBackHandler` — exists in `com.harazone.ui.components`
- SQLDelight migration infrastructure — exists; verify latest number before creating new `.sqm`
- `FakeSavedPoiRepository` — exists in `commonTest/fakes/`

### Testing Strategy

**Unit tests** (`SavedPlacesViewModelTest.kt` in `commonTest`):
- 10 tests: capsule nearest, capsule no-GPS, search name, search area, capsule+search intersection, unsave immediate, undo restores, undo prevents DB write, discovery story null, discovery story counts

**Manual test checklist:**
- Open Saved Places → cards render with images (for saves with imageUrl) and gradients (for others); all cards same height
- Tap a capsule chip → list filters; tap "All" → list restores; type in search with capsule active → intersection filters correctly
- Unsave → card disappears + Snackbar; tap Undo → card reappears; no DB write
- Unsave → let Snackbar timeout (~10s) → item gone from DB (verify by reopening app)
- Unsave → tap "Map" before Snackbar times out → screen closes, item is deleted from DB (verify by reopening)
- Tap Directions → maps app opens
- Tap Ask AI → saves screen closes, ChatOverlay opens pre-seeded
- Press Android back → screen dismisses, map visible
- Open with 0 saves → empty state, no capsules, no search; open with 1 save → no Discovery Story; open with 2+ saves → Discovery Story visible

### Notes

- `ChatPoiCard` has no `imageUrl` field (not in Gemini JSON schema) — `ChatViewModel.savePoi` passes `imageUrl = null`. Chat-saved cards always show gradient fallback. No change to `ChatPoiCard` needed.
- `SavedPoi` has no `vibe` field — footer type badge uses `poi.type.replaceFirstChar { it.uppercaseChar() }` as proxy. Future story should store `vibe` at save time.
- `liveStatus` not in `SavedPoi` — open/closed dimming and "Closed Now" capsule deferred until Google Places integration.
- Migration number: verify actual latest `.sqm` file before creating. As of spec creation, `6.sqm` was latest → expected new is `7.sqm`. Do not assume.
- Delete `SavesBottomSheet` and `SavedPoiRow` from `MapScreen.kt` entirely in T11.
- Share intent (T11) — implement as no-op `TODO` stub if platform share not yet wired.
- `gradientForType` uses `.lowercase()` for matching — Gemini type strings are non-deterministic. Most saves show gradient; that is acceptable V1 behavior.
- `DiscoveryStoryCard` colors defined as `private val` constants at file top — not inline magic numbers.
- Visual reference: `/tmp/saves-final-v7.html` — 2 tabs (Home City, Traveling).
