---
title: 'Recent Places in Geocoding Search Bar'
slug: 'recent-places-search'
created: '2026-03-08'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Jetpack Compose Multiplatform', 'SQLDelight', 'Koin', 'Kotlin Coroutines/Flow']
files_to_modify:
  - 'composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/search_history.sq (DELETE)'
  - 'composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/recent_places.sq (CREATE)'
  - 'composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/4.sqm (CREATE)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/RecentPlace.kt (CREATE)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/repository/RecentPlacesRepository.kt (CREATE)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/RecentPlacesRepositoryImpl.kt (CREATE)'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeRecentPlacesRepository.kt (CREATE)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt (MODIFY)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt (MODIFY)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/GeocodingSearchBar.kt (MODIFY)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ExpandablePoiCard.kt (MODIFY)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt (MODIFY)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt (MODIFY)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt (MODIFY)'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt (MODIFY)'
code_patterns:
  - 'SQLDelight: INSERT OR REPLACE for upserts; named queries in .sq files; .sqm for migrations'
  - 'Koin DI: single{} for repos, viewModel{} with positional get() in UiModule'
  - 'ViewModel: collects Flows via viewModelScope.launch; all state in _uiState MutableStateFlow'
  - 'MapUiState.Ready is a data class — add fields with default values'
  - 'GeocodingSearchBar is pure Composable — state-driven entirely by props'
  - 'Fakes extend or implement the real type; UnconfinedTestDispatcher + runTest pattern'
test_patterns:
  - 'kotlin.test (BeforeTest/AfterTest/Test)'
  - 'UnconfinedTestDispatcher + Dispatchers.setMain/resetMain'
  - 'createViewModel() helper with named fake params'
  - 'assertIs<MapUiState.Ready> then access .recentPlaces'
---

# Tech-Spec: Recent Places in Geocoding Search Bar

**Created:** 2026-03-08

## Overview

### Problem Statement

When a user taps the geocoding search bar and hasn't typed anything yet, they see an empty state with just a placeholder. There's no quick way to re-navigate to places they've previously searched — they must retype every time.

### Solution

When the search bar enters active state with an empty query, show up to 10 recently selected places (stored in SQLDelight) in the existing dropdown panel. Recents are replaced instantly by live geocoding suggestions as soon as the user starts typing. Each geocoding suggestion selection upserts into the recents table.

### Scope

**In Scope:**
- New `recent_places` SQLDelight table (`place_name`, `lat`, `lng`, `searched_at`) via migration 4
- Drop dead `search_history` table in same migration
- `RecentPlacesRepository` interface (domain) + `RecentPlacesRepositoryImpl` (data) for upsert, observe top-10, clear-all
- `MapViewModel`: inject repo, collect `observeRecent()` Flow into `MapUiState.Ready.recentPlaces`, upsert on geocoding suggestion selected, navigate on recent selected, clear on demand
- `GeocodingSearchBar`: new `recentPlaces`, `onRecentSelected`, `onClearRecents` params; recents dropdown when `query.isBlank() && recentPlaces.isNotEmpty()`; hidden when user starts typing
- Recents rows: pin icon + `place_name` only (no subtitle), styled identically to `SuggestionRow`
- "Clear history" button at the bottom of the recents dropdown
- `FakeRecentPlacesRepository` for ViewModel tests
- ViewModel unit tests covering recents upsert, selection, and clear

**Out of Scope:**
- Recents in `AISearchBar.kt` or `SearchOverlay.kt`
- Deleting individual recent entries
- Syncing recents to any backend
- Showing `fullAddress` or distance in recents rows

## Context for Development

### Codebase Patterns

- **SQLDelight**: Tables in `*.sq` files; migrations in `migrations/*.sqm`. Schema version currently at 3. `.sq` filename determines generated Queries class (e.g., `recent_places.sq` → `RecentPlacesQueries`). Upsert pattern: `INSERT OR REPLACE`. `AreaDiscoveryDatabase` singleton registered in `DataModule.kt`.
- **Koin DI**: `DataModule.kt` registers singletons with `single {}`. `UiModule.kt` registers ViewModel with positional `get()` calls — currently `viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get()) }` (7 params). Adding `RecentPlacesRepository` makes it 8.
- **ViewModel**: `MapViewModel` takes both interfaces (e.g., `AreaRepository`) and concrete classes (e.g., `MapTilerGeocodingProvider`). For recents, use an interface — required for testability. Flows are collected via `viewModelScope.launch { flow.collect { ... } }`. All UI state lives in `_uiState: MutableStateFlow<MapUiState>`, updated via `.copy()`.
- **GeocodingSearchBar**: Pure Composable, props-driven. `active = !spinning && selectedPlace == null && (isFieldFocused || query.isNotBlank() || suggestions.isNotEmpty())`. The `ActiveState` composable already renders a dropdown `Surface` when `hasDropdown = suggestions.isNotEmpty()`. Recents will use a parallel `hasRecents = query.isBlank() && recentPlaces.isNotEmpty()` branch — one or the other, never both.
- **Fakes**: Interface fakes implement directly. Pattern: `class FakeRecentPlacesRepository : RecentPlacesRepository { ... }` with a mutable `MutableStateFlow` to simulate the observable.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/search_history.sq` | Dead table — DELETE |
| `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_bucket_cache.sq` | `INSERT OR REPLACE` upsert pattern to follow |
| `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/3.sqm` | Most recent migration — schema is at version 3 |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/GeocodingSearchBar.kt` | `ActiveState` inner composable — add recents branch here |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` | Add 8th constructor param, Flow collection, upsert, recent selection |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt` | Add `recentPlaces: List<RecentPlace> = emptyList()` to `Ready` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` | Pass 3 new params to `GeocodingSearchBar` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt` | Register `RecentPlacesRepositoryImpl` singleton |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt` | Add 8th `get()` to ViewModel binding |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeMapTilerGeocodingProvider.kt` | Pattern for fake implementation |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` | Test patterns to follow; extend with recents tests |

### Technical Decisions

- **Replace `search_history`** — dead code (no data, no wiring). Delete `search_history.sq`. Migration 4: `DROP TABLE IF EXISTS search_history; CREATE TABLE recent_places (place_name TEXT PRIMARY KEY, lat REAL NOT NULL, lng REAL NOT NULL, searched_at INTEGER NOT NULL);`.
- **10-item cap** — enforced by the `observeRecent` query (`ORDER BY searched_at DESC LIMIT 10`). PK on `place_name` handles deduplication automatically via `INSERT OR REPLACE`. No additional deletion needed.
- **Pin icon, no subtitle** — identical visual to `SuggestionRow` but with only the name line; the subtitle `Text` composable is omitted entirely.
- **Separate `recentPlaces` param** — `GeocodingSearchBar` receives `recentPlaces: List<RecentPlace>` alongside `suggestions`. Dropdown shows either recents (empty query) or live suggestions (non-empty query), never both.
- **`RecentPlace` domain model** — `data class RecentPlace(val name: String, val latitude: Double, val longitude: Double)`. Does not reuse `GeocodingSuggestion`.
- **No re-upsert on recent tap** — `onRecentSelected` mirrors `onGeocodingSuggestionSelected` (sets `pendingLat/Lng/AreaName`, triggers area search) but does NOT call `upsert` again. The record's timestamp stays at the time of the last geocoding selection.
- **Flow collection in `init`** — collect `recentPlacesRepository.observeRecent()` once in `init {}` alongside `loadLocation()`. When state is `Ready`, update `recentPlaces`; otherwise emission is skipped via `as? MapUiState.Ready ?: return@collect`. No race condition: `loadLocation()` transitions to `Ready` before portrait Flow collection begins, so the recents Flow will find a `Ready` state.
- **Dispatchers.IO for DB ops** — `RecentPlacesRepositoryImpl` uses injected `ioDispatcher` (default `Dispatchers.IO`) for both `mapToList()` and `withContext()`, matching `AreaRepositoryImpl` pattern. Never use `Dispatchers.Default` for blocking I/O.
- **AppClock for timestamps** — `kotlinx-datetime` is NOT in project deps. Use the existing `AppClock` interface (`com.areadiscovery.util.AppClock`) injected into `RecentPlacesRepositoryImpl` for `searched_at` timestamps, matching `AreaRepositoryImpl` and `AreaContextFactory` patterns.
- **No `searchCurrentLocation()` helper** — The area search in `onGeocodingSuggestionSelected` is done inline via `collectPortraitWithRetry()`. `onRecentSelected` must duplicate this pattern (not call a nonexistent helper). Both methods follow the same structure: cancel jobs → set pending coords → update state → launch `collectPortraitWithRetry`.
- **`cameraMoveId + 1` required** — `onRecentSelected` must increment `cameraMoveId` to trigger map camera pan to the selected location, same as `onGeocodingSuggestionSelected`.
- **Optimistic clear** — `onClearRecents` clears `recentPlaces` from state immediately (before the DB coroutine), so the dropdown disappears instantly without waiting for the Flow to re-emit.

## Implementation Plan

### Tasks

- [x] **Task 1: Delete `search_history.sq` and create `recent_places.sq`**
  - File: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/search_history.sq` → **DELETE this file**
  - **Before deleting**: grep for `SearchHistoryQueries`, `search_history`, `upsertSearch`, `getRecentSearches` across `src/` to confirm no Kotlin code references the generated class. If any references exist, remove them first.
  - File: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/recent_places.sq` → **CREATE**
  - Action: Write the new `.sq` file with the following content exactly:
    ```sql
    CREATE TABLE IF NOT EXISTS recent_places (
        place_name TEXT NOT NULL,
        lat        REAL NOT NULL,
        lng        REAL NOT NULL,
        searched_at INTEGER NOT NULL,
        PRIMARY KEY (place_name)
    );

    observeRecent:
    SELECT place_name, lat, lng
    FROM recent_places
    ORDER BY searched_at DESC
    LIMIT 10;

    upsertPlace:
    INSERT OR REPLACE INTO recent_places(place_name, lat, lng, searched_at)
    VALUES (:place_name, :lat, :lng, :searched_at);

    clearAll:
    DELETE FROM recent_places;
    ```
  - Notes: The `.sq` filename `recent_places` determines the generated class name `RecentPlacesQueries`. SQLDelight code-gen will produce `AreaDiscoveryDatabase.recentPlacesQueries` automatically.

- [x] **Task 2: Create migration `4.sqm`**
  - File: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/4.sqm` → **CREATE**
  - Action: Write:
    ```sql
    DROP TABLE IF EXISTS search_history;

    CREATE TABLE IF NOT EXISTS recent_places (
        place_name TEXT NOT NULL,
        lat        REAL NOT NULL,
        lng        REAL NOT NULL,
        searched_at INTEGER NOT NULL,
        PRIMARY KEY (place_name)
    );
    ```
  - Notes: **Bump `schemaVersion` in `build.gradle.kts`** — SQLDelight does not auto-derive the version. Find the SQLDelight block and change `schemaVersion = 3` to `schemaVersion = 4`. Without this, the migration file is silently ignored and `recentPlacesQueries` crashes at runtime. `DROP TABLE IF EXISTS` is safe even for users who may have never had migration 3 applied.

- [x] **Task 3: Create `RecentPlace.kt` domain model**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/RecentPlace.kt` → **CREATE**
  - Action: Write:
    ```kotlin
    package com.areadiscovery.domain.model

    data class RecentPlace(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )
    ```

- [x] **Task 4: Create `RecentPlacesRepository.kt` interface**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/repository/RecentPlacesRepository.kt` → **CREATE**
  - Action: Write:
    ```kotlin
    package com.areadiscovery.domain.repository

    import com.areadiscovery.domain.model.RecentPlace
    import kotlinx.coroutines.flow.Flow

    interface RecentPlacesRepository {
        fun observeRecent(): Flow<List<RecentPlace>>
        suspend fun upsert(place: RecentPlace)
        suspend fun clearAll()
    }
    ```

- [x] **Task 5: Create `RecentPlacesRepositoryImpl.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/RecentPlacesRepositoryImpl.kt` → **CREATE**
  - Action: Write:
    ```kotlin
    package com.areadiscovery.data.repository

    import app.cash.sqldelight.coroutines.asFlow
    import app.cash.sqldelight.coroutines.mapToList
    import com.areadiscovery.data.local.AreaDiscoveryDatabase
    import com.areadiscovery.domain.model.RecentPlace
    import com.areadiscovery.domain.repository.RecentPlacesRepository
    import com.areadiscovery.util.AppClock
    import kotlinx.coroutines.CoroutineDispatcher
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.map
    import kotlinx.coroutines.withContext

    class RecentPlacesRepositoryImpl(
        private val database: AreaDiscoveryDatabase,
        private val clock: AppClock,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : RecentPlacesRepository {

        override fun observeRecent(): Flow<List<RecentPlace>> =
            database.recentPlacesQueries
                .observeRecent()
                .asFlow()
                .mapToList(ioDispatcher)
                .map { rows -> rows.map { RecentPlace(it.place_name, it.lat, it.lng) } }

        override suspend fun upsert(place: RecentPlace) = withContext(ioDispatcher) {
            database.recentPlacesQueries.upsertPlace(
                place_name = place.name,
                lat = place.latitude,
                lng = place.longitude,
                searched_at = clock.nowMs(),
            )
        }

        override suspend fun clearAll() = withContext(ioDispatcher) {
            database.recentPlacesQueries.clearAll()
        }
    }
    ```
  - Notes: Uses `AppClock` (from `com.areadiscovery.util.AppClock`) — the project's existing timestamp abstraction used in `AreaRepositoryImpl` and `AreaContextFactory`. `kotlinx-datetime` is NOT in project dependencies. Uses injected `ioDispatcher` (default `Dispatchers.IO`) matching the `AreaRepositoryImpl` pattern for blocking DB I/O.

- [x] **Task 6: Update `MapUiState.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
  - Action: Add import for `RecentPlace` and add a new field to `MapUiState.Ready`:
    - Add import: `import com.areadiscovery.domain.model.RecentPlace`
    - Add field inside `data class Ready(...)` after `isGeocodingInitiatedSearch`:
      ```kotlin
      val recentPlaces: List<RecentPlace> = emptyList(),
      ```

- [x] **Task 7: Update `MapViewModel.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action — four changes:

  **7a. Add constructor param** — add as the 8th parameter (after `geocodingProvider`):
  ```kotlin
  private val recentPlacesRepository: RecentPlacesRepository,
  ```
  Add import: `import com.areadiscovery.domain.repository.RecentPlacesRepository`
  Add import: `import com.areadiscovery.domain.model.RecentPlace`

  **7b. Collect recents Flow in `init {}`** — add after `loadLocation()`:
  ```kotlin
  viewModelScope.launch {
      recentPlacesRepository.observeRecent().collect { recents ->
          val current = _uiState.value as? MapUiState.Ready ?: return@collect
          _uiState.value = current.copy(recentPlaces = recents)
      }
  }
  ```

  **7c. Upsert in `onGeocodingSuggestionSelected`** — at the end of the function body, after setting `_uiState.value`, add a fire-and-forget upsert:
  ```kotlin
  viewModelScope.launch {
      recentPlacesRepository.upsert(
          RecentPlace(
              name = suggestion.name,
              latitude = suggestion.latitude,
              longitude = suggestion.longitude,
          )
      )
  }
  ```

  **7d. Add `onRecentSelected` function** — new public function mirroring `onGeocodingSuggestionSelected` but taking a `RecentPlace`. Does NOT upsert (already in history). Uses the same inline `collectPortraitWithRetry` pattern — there is no `searchCurrentLocation()` helper:
  ```kotlin
  fun onRecentSelected(recent: RecentPlace) {
      val current = _uiState.value as? MapUiState.Ready ?: return
      cameraIdleJob?.cancel()
      geocodingJob?.cancel()
      searchJob?.cancel()
      returnToLocationJob?.cancel()
      pendingLat = recent.latitude
      pendingLng = recent.longitude
      pendingAreaName = recent.name
      _uiState.value = current.copy(
          geocodingQuery = "",
          geocodingSuggestions = emptyList(),
          isGeocodingLoading = false,
          geocodingSelectedPlace = recent.name,
          isGeocodingInitiatedSearch = true,
          latitude = recent.latitude,
          longitude = recent.longitude,
          cameraMoveId = current.cameraMoveId + 1,
          isSearchingArea = true,
          showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, current),
          vibePoiCounts = emptyMap(),
          pois = emptyList(),
          activeVibe = null,
      )
      searchJob = viewModelScope.launch {
          try {
              collectPortraitWithRetry(
                  areaName = recent.name,
                  onComplete = { pois, _ ->
                      val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                      val counts = computeVibePoiCounts(pois)
                      _uiState.value = state.copy(
                          areaName = recent.name,
                          pois = pois,
                          vibePoiCounts = counts,
                          activeVibe = null,
                          isSearchingArea = false,
                          showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, state),
                      )
                  },
                  onError = { e ->
                      AppLogger.e(e) { "Recent selection: portrait fetch failed" }
                      val s = _uiState.value as? MapUiState.Ready
                      if (s != null) _uiState.value = s.copy(
                          isSearchingArea = false,
                          showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, s),
                      )
                      _errorEvents.tryEmit("Couldn't load area info. Try again.")
                  },
              )
          } catch (e: CancellationException) {
              val s = _uiState.value as? MapUiState.Ready
              if (s != null) _uiState.value = s.copy(isSearchingArea = false)
              throw e
          } catch (e: Exception) {
              AppLogger.e(e) { "Recent selection: unexpected error" }
              val s = _uiState.value as? MapUiState.Ready
              if (s != null) _uiState.value = s.copy(
                  isSearchingArea = false,
                  showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, s),
              )
              _errorEvents.tryEmit("Couldn't load area info. Try again.")
          }
      }
  }
  ```

  **7e. Add `onClearRecents` function** — new public function with optimistic state update (clears list instantly, then persists):
  ```kotlin
  fun onClearRecents() {
      val current = _uiState.value as? MapUiState.Ready ?: return
      _uiState.value = current.copy(recentPlaces = emptyList())
      viewModelScope.launch {
          recentPlacesRepository.clearAll()
      }
  }
  ```

  Notes: There is **no** `searchCurrentLocation()` helper in the ViewModel. The area search is launched inline via `searchJob = viewModelScope.launch { collectPortraitWithRetry(...) }`. Copy the full launch block from `onGeocodingSuggestionSelected`, substituting `recent.name/latitude/longitude` for `suggestion.*`.

- [x] **Task 8: Update `GeocodingSearchBar.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/GeocodingSearchBar.kt`
  - Action — three changes:

  **8a. Add params to `GeocodingSearchBar` composable** — add after `onCancelLoad`:
  ```kotlin
  recentPlaces: List<RecentPlace> = emptyList(),
  onRecentSelected: (RecentPlace) -> Unit = {},
  onClearRecents: () -> Unit = {},
  ```
  Add import: `import com.areadiscovery.domain.model.RecentPlace`

  **8b. Pass new params into `ActiveState`** — in the `active ->` branch of the `when` block, add the three new params to the `ActiveState(...)` call:
  ```kotlin
  recentPlaces = recentPlaces,
  onRecentSelected = onRecentSelected,
  onClearRecents = onClearRecents,
  ```

  **8c. Update `ActiveState` private composable** — add three new params:
  ```kotlin
  recentPlaces: List<RecentPlace>,
  onRecentSelected: (RecentPlace) -> Unit,
  onClearRecents: () -> Unit,
  ```
  Replace the `hasDropdown` / dropdown rendering block. Current code:
  ```kotlin
  val hasDropdown = suggestions.isNotEmpty()
  ```
  New logic: mutually exclusive — show recents when query is blank, live suggestions when query is not blank:
  ```kotlin
  val hasRecents = query.isBlank() && recentPlaces.isNotEmpty()
  val hasDropdown = query.isNotBlank() && suggestions.isNotEmpty()
  val showPanel = hasRecents || hasDropdown
  ```

  Update the top `Surface` shape to use `showPanel` instead of `hasDropdown`:
  ```kotlin
  shape = if (showPanel) {
      RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
  } else {
      RoundedCornerShape(50)
  },
  ```

  Replace the `if (hasDropdown) { ... }` block at the bottom of `ActiveState` with:
  ```kotlin
  if (showPanel) {
      Surface(
          shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
          color = MapFloatingUiDark.copy(alpha = 0.95f),
      ) {
          Column {
              if (hasRecents) {
                  recentPlaces.forEachIndexed { i, recent ->
                      if (i > 0) {
                          HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                      }
                      RecentPlaceRow(recent, onRecentSelected)
                  }
                  HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
                  Box(
                      contentAlignment = Alignment.Center,
                      modifier = Modifier
                          .fillMaxWidth()
                          .clickable { onClearRecents() }
                          .padding(horizontal = 14.dp, vertical = 9.dp),
                  ) {
                      Text(
                          text = "Clear history",
                          style = MaterialTheme.typography.labelSmall,
                          color = Color.White.copy(alpha = 0.4f),
                      )
                  }
              } else {
                  suggestions.forEachIndexed { i, s ->
                      if (i > 0) {
                          HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                      }
                      SuggestionRow(s, query, onSuggestionSelected)
                  }
              }
          }
      }
  }
  ```

  **8d. Add `RecentPlaceRow` private composable** — add below `SuggestionRow`:
  ```kotlin
  @Composable
  private fun RecentPlaceRow(
      recent: RecentPlace,
      onSelected: (RecentPlace) -> Unit,
  ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelected(recent) }
              .padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
          Icon(
              imageVector = Icons.Default.Place,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(10.dp))
          Text(
              text = recent.name,
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
      }
  }
  ```

- [x] **Task 9: Update `MapScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: In `ReadyContent`, find the `GeocodingSearchBar(...)` call and add three new named params after `onCancelLoad`:
    ```kotlin
    recentPlaces = state.recentPlaces,
    onRecentSelected = { viewModel.onRecentSelected(it) },
    onClearRecents = { viewModel.onClearRecents() },
    ```

- [x] **Task 10: Wire DI**
  - File A: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
  - Action: Add a new singleton registration anywhere in `val dataModule = module { ... }`:
    ```kotlin
    single<RecentPlacesRepository> { RecentPlacesRepositoryImpl(get(), get()) }
    ```
    Add imports: `import com.areadiscovery.data.repository.RecentPlacesRepositoryImpl` and `import com.areadiscovery.domain.repository.RecentPlacesRepository`. The two `get()` calls resolve `AreaDiscoveryDatabase` and `AppClock` (both already registered as singletons in this module).

  - File B: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
  - Action: Change the `viewModel` line from 7 to 8 `get()` calls:
    ```kotlin
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    ```

- [x] **Task 11: Create `FakeRecentPlacesRepository.kt`**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeRecentPlacesRepository.kt` → **CREATE**
  - Action: Write:
    ```kotlin
    package com.areadiscovery.fakes

    import com.areadiscovery.domain.model.RecentPlace
    import com.areadiscovery.domain.repository.RecentPlacesRepository
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.asStateFlow

    class FakeRecentPlacesRepository : RecentPlacesRepository {

        private val _recents = MutableStateFlow<List<RecentPlace>>(emptyList())

        var upsertCalls: List<RecentPlace> = emptyList()
            private set
        var clearAllCount: Int = 0
            private set

        fun setRecents(places: List<RecentPlace>) {
            _recents.value = places
        }

        override fun observeRecent(): Flow<List<RecentPlace>> = _recents.asStateFlow()

        override suspend fun upsert(place: RecentPlace) {
            upsertCalls = upsertCalls + place
            val updated = (_recents.value.filterNot { it.name == place.name } + place)
                .takeLast(10)
            _recents.value = updated
        }

        override suspend fun clearAll() {
            clearAllCount++
            _recents.value = emptyList()
        }
    }
    ```

- [x] **Task 12: Extend `MapViewModelTest.kt` with recents tests**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
  - Action: Update `createViewModel()` helper to add `recentPlacesRepository` param (with `FakeRecentPlacesRepository()` default). Add the following four tests:

  **Test 1 — Recents appear in state when repo emits:**
  ```kotlin
  @Test
  fun recentsFromRepositoryAppearInReadyState() = runTest(testDispatcher) {
      val fakeRecents = FakeRecentPlacesRepository()
      val place = RecentPlace("Shibuya", 35.659, 139.700)
      fakeRecents.setRecents(listOf(place))
      val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
      testScheduler.advanceUntilIdle()
      val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
      assertEquals(listOf(place), state.recentPlaces)
  }
  ```

  **Test 2 — Selecting a geocoding suggestion upserts to recents:**
  ```kotlin
  @Test
  fun selectingGeocodingSuggestionUpsertsToRecents() = runTest(testDispatcher) {
      val fakeRecents = FakeRecentPlacesRepository()
      val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
      assertIs<MapUiState.Ready>(viewModel.uiState.value)
      val suggestion = GeocodingSuggestion("Shibuya", "Shibuya, Tokyo", 35.659, 139.700, null)
      viewModel.onGeocodingSuggestionSelected(suggestion)
      assertEquals(1, fakeRecents.upsertCalls.size)
      assertEquals("Shibuya", fakeRecents.upsertCalls.first().name)
  }
  ```

  **Test 3 — Selecting a recent navigates without upsert:**
  ```kotlin
  @Test
  fun selectingRecentNavigatesWithoutUpsert() = runTest(testDispatcher) {
      val fakeRecents = FakeRecentPlacesRepository()
      val place = RecentPlace("Asakusa", 35.714, 139.796)
      fakeRecents.setRecents(listOf(place))
      val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
      assertIs<MapUiState.Ready>(viewModel.uiState.value)
      viewModel.onRecentSelected(place)
      val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
      assertEquals("Asakusa", state.geocodingSelectedPlace)
      assertEquals(0, fakeRecents.upsertCalls.size)
  }
  ```

  **Test 4 — Clear recents empties the list:**
  ```kotlin
  @Test
  fun clearRecentsEmptiesRecentPlacesInState() = runTest(testDispatcher) {
      val fakeRecents = FakeRecentPlacesRepository()
      fakeRecents.setRecents(listOf(RecentPlace("Shinjuku", 35.689, 139.700)))
      val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
      assertIs<MapUiState.Ready>(viewModel.uiState.value)
      viewModel.onClearRecents()
      val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
      assertEquals(emptyList(), state.recentPlaces)
      assertEquals(1, fakeRecents.clearAllCount)
  }
  ```

  Notes: Add `import com.areadiscovery.fakes.FakeRecentPlacesRepository` and `import com.areadiscovery.domain.model.RecentPlace` to the test file.

### Acceptance Criteria

- [x] **AC 1:** Given the search bar is in idle state, when the user taps it (transitioning to active), then if there are saved recent places the recents dropdown appears immediately with a pin icon and place name per row, and no subtitle.

- [x] **AC 2:** Given the recents dropdown is visible, when the user starts typing any character, then the recents list disappears and is replaced by live geocoding suggestions (or empty if no results yet).

- [x] **AC 3:** Given the user types a query and selects a geocoding suggestion, when the selection is handled, then the place is upserted into `recent_places` with the current timestamp, and the area search for that place begins.

- [x] **AC 4:** Given a place already exists in recents, when it is selected again from the live geocoding suggestions, then the record is updated in-place (same `place_name` PK) with a fresh timestamp, moving it to the top of the list on next open.

- [x] **AC 5:** Given the user taps a place in the recents dropdown, when the selection is handled, then the area search for that place begins (same UX as geocoding suggestion selection) and no new upsert is performed.

- [x] **AC 6:** Given the recents dropdown is showing, when the user taps "Clear history", then all recents are deleted, the dropdown closes (no items = no panel), and tapping the bar again shows nothing.

- [x] **AC 7:** Given there are more than 10 saved recent places in the database, when the recents dropdown opens, then at most 10 places are shown, ordered by most recently selected first.

- [x] **AC 8:** Given there are no saved recents, when the user taps the idle search bar, then the active state shows the text field with placeholder only — no dropdown panel appears.

- [x] **AC 9:** Given the app is killed and restarted, when the user taps the search bar, then previously selected places appear in the recents list (persisted via SQLDelight).

- [x] **AC 10:** Given the app is running on a device with schema version 3 (has `search_history`), when the database migrates to version 4, then `search_history` is dropped and `recent_places` is created with no crash.

## Additional Context

### Dependencies

- SQLDelight `asFlow()` / `mapToList()` coroutine extensions — already used in `AreaRepositoryImpl`. Confirm `app.cash.sqldelight:coroutines-extensions` is in `libs.versions.toml`.
- `AppClock` (`com.areadiscovery.util.AppClock`) — already in project, already registered as Koin singleton. Use this for timestamps. **`kotlinx-datetime` is NOT a project dependency** — do not import it.
- No new external dependencies required.

### Testing Strategy

- **Unit tests (Task 12):** 4 ViewModel tests using `FakeRecentPlacesRepository`. Cover: state population from Flow, upsert on geocoding selection, navigation from recent tap, clear recents. Run with `./gradlew :composeApp:test`.
- **Smoke test:** `AppLaunchSmokeTest` covers app launch with no crash — migration 4 will be exercised on a fresh install. Run with `./gradlew :composeApp:connectedDebugAndroidTest`.
- **Manual testing:**
  1. Fresh install (or `adb uninstall com.areadiscovery.debug` first — required to exercise the migration path cleanly).
  2. Search and select 3+ places, then tap the bar again — confirm recents appear.
  3. Start typing — confirm recents disappear, live suggestions appear.
  4. Tap a recent — confirm area navigates correctly.
  5. Tap "Clear history" — confirm panel disappears and recents are gone.
  6. Kill and relaunch app — confirm recents persist.

### Notes

- **Migration verification:** SQLDelight requires an explicit `schemaVersion` in `build.gradle.kts`. The current value is 3 (matching `2.sqm` and `3.sqm`). You **must** change it to 4 alongside creating `4.sqm`, or the migration will be silently skipped.
- **`active` state expansion:** The `GeocodingSearchBar`'s `active` condition already includes `isFieldFocused` — recents will show correctly when `isFieldFocused && query.isBlank() && recentPlaces.isNotEmpty()` without any change to the `active` predicate itself.
- **iOS:** This is a KMP common module change — `recent_places.sq`, domain model, and repository interface are in `commonMain`. `RecentPlacesRepositoryImpl` uses the same `DatabaseDriverFactory` abstraction already set up for iOS. No iOS-specific code needed.
