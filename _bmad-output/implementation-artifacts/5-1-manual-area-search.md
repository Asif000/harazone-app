# Story 5.1: Manual Area Search

Status: review

## Story

As a user,
I want to search for any area by name and see its full area portrait,
so that I can explore places I'm planning to visit or curious about without being there.

## Acceptance Criteria

1. **Given** the user taps the search icon on the Summary screen or the "Search an area" CTA in the LocationFailed state, **then** the app navigates to the Search screen [FR17]
2. **Given** the Search screen is open with no query, **then** a zero-state is shown with category chips ("Popular cities", "Nearby areas", "Trending") and a list of recent searches (most recent first, up to 5) [UX: Google Maps pattern]
3. **Given** the user types an area name (e.g., "Shibuya, Tokyo") and submits (keyboard "Done" / "Search" action), **then** `SearchViewModel` calls `SearchAreaUseCase` and the screen transitions to streaming the area portrait using `BucketCard` composables — identical visual quality to GPS-triggered summary [FR17]
4. **Given** a search is submitted, **then** the query is persisted to the `search_history` SQLDelight table (upsert by query text, updated timestamp) and appears in recent searches on the next idle state
5. **Given** the streaming portrait completes, **then** analytics fires `summary_viewed` with `source = "search"` and `area_name` [Epic 5.1 AC]
6. **Given** the Search screen is open, **when** the user taps the back arrow, **then** navigation pops back to the previous screen (Summary)
7. **Given** search fails (network error, no AI response), **then** a user-friendly error message is shown with a "Try again" button (no crash, no empty screen)
8. **Given** recent searches are shown, **when** the user taps a recent search chip, **then** it auto-fills the search field and triggers the search immediately

## Tasks / Subtasks

- [x] Task 1: Add `search_history` SQLDelight table + migration (AC: 4)
  - [x] 1.1: Create `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/search_history.sq`:
    ```sql
    CREATE TABLE IF NOT EXISTS search_history (
        query TEXT NOT NULL,
        searched_at INTEGER NOT NULL,
        PRIMARY KEY (query)
    );

    getRecentSearches:
    SELECT query FROM search_history
    ORDER BY searched_at DESC
    LIMIT 5;

    upsertSearch:
    INSERT OR REPLACE INTO search_history(query, searched_at)
    VALUES (:query, :searched_at);

    deleteAll:
    DELETE FROM search_history;
    ```
  - [x] 1.2: Create migration `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/3.sqm`:
    ```sql
    CREATE TABLE IF NOT EXISTS search_history (
        query TEXT NOT NULL,
        searched_at INTEGER NOT NULL,
        PRIMARY KEY (query)
    );
    ```
    **CRITICAL**: This is migration 3. The existing migrations are: 1 (initial bucket cache) and 2 (poi cache). Adding `3.sqm` increments the schema version to 3. `AndroidSqliteDriver` auto-runs pending migrations on first open of an existing DB. No manual version bump needed — SQLDelight reads migration file numbers.
  - [x] 1.3: Verify `AreaDiscoveryDatabase` generated code includes `searchHistoryQueries` after Gradle sync (this is automatic from SQLDelight).

- [x] Task 2: Create `SearchAreaUseCase` (AC: 3)
  - [x] 2.1: Create `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/usecase/SearchAreaUseCase.kt`:
    ```kotlin
    package com.areadiscovery.domain.usecase

    import com.areadiscovery.domain.model.AreaContext
    import com.areadiscovery.domain.model.BucketUpdate
    import com.areadiscovery.domain.repository.AreaRepository
    import kotlinx.coroutines.flow.Flow

    open class SearchAreaUseCase(private val repository: AreaRepository) {
        open operator fun invoke(areaName: String, context: AreaContext): Flow<BucketUpdate> =
            repository.getAreaPortrait(areaName, context)
    }
    ```
    - Architecturally distinct from `GetAreaPortraitUseCase` (GPS-triggered) even though V1 delegates identically. Future: add geocoding validation, search-specific caching, or different prompt strategies here without touching GPS flow.
    - Pattern mirrors `GetAreaPortraitUseCase` — `open class` for testability.

- [x] Task 3: Create `SearchUiState` (AC: 2, 3, 7)
  - [x] 3.1: Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchUiState.kt`:
    ```kotlin
    package com.areadiscovery.ui.search

    import com.areadiscovery.domain.model.POI
    import com.areadiscovery.ui.summary.BucketDisplayState
    import com.areadiscovery.domain.model.BucketType

    val DEFAULT_CATEGORY_CHIPS = listOf("Popular cities", "Nearby areas", "Trending")

    sealed class SearchUiState {
        // Zero state — no active search
        data class Idle(
            val recentSearches: List<String> = emptyList(),
            val categoryChips: List<String> = DEFAULT_CATEGORY_CHIPS,
        ) : SearchUiState()

        // Portrait is streaming
        data class Streaming(
            val query: String,
            val areaName: String,
            val buckets: Map<BucketType, BucketDisplayState>,
        ) : SearchUiState()

        // Portrait complete
        data class Complete(
            val query: String,
            val areaName: String,
            val buckets: Map<BucketType, BucketDisplayState>,
            val pois: List<POI>,
        ) : SearchUiState()

        // Search error
        data class Error(
            val query: String,
            val message: String,
        ) : SearchUiState()
    }
    ```
    - `BucketDisplayState` is already in `com.areadiscovery.ui.summary` — import directly.
    - `DEFAULT_CATEGORY_CHIPS` are hardcoded for V1 (not dynamic/server-driven).
    - No `Loading` state — Idle → Streaming is immediate once search is submitted.

- [x] Task 4: Create `SearchViewModel` (AC: 2, 3, 4, 5, 7, 8)
  - [x] 4.1: Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchViewModel.kt`:
    ```kotlin
    package com.areadiscovery.ui.search

    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.viewModelScope
    import com.areadiscovery.data.local.AreaDiscoveryDatabase
    import com.areadiscovery.domain.service.AreaContextFactory
    import com.areadiscovery.domain.usecase.SearchAreaUseCase
    import com.areadiscovery.util.AnalyticsTracker
    import com.areadiscovery.util.AppLogger
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.launch
    import kotlin.coroutines.cancellation.CancellationException

    class SearchViewModel(
        private val searchAreaUseCase: SearchAreaUseCase,
        private val areaContextFactory: AreaContextFactory,
        private val stateMapper: com.areadiscovery.ui.summary.SummaryStateMapper,
        private val analyticsTracker: AnalyticsTracker,
        private val database: AreaDiscoveryDatabase,
        private val clock: com.areadiscovery.util.AppClock,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        private var searchJob: Job? = null

        init {
            loadRecentSearches()
        }

        fun search(query: String) {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isBlank()) return

            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                persistSearch(trimmedQuery)

                val context = areaContextFactory.create()
                var summaryState: com.areadiscovery.ui.summary.SummaryUiState =
                    com.areadiscovery.ui.summary.SummaryUiState.Loading

                try {
                    searchAreaUseCase(trimmedQuery, context).collect { update ->
                        summaryState = stateMapper.processUpdate(summaryState, update, trimmedQuery)
                        _uiState.value = summaryState.toSearchUiState(trimmedQuery)

                        if (summaryState is com.areadiscovery.ui.summary.SummaryUiState.Complete) {
                            analyticsTracker.trackEvent(
                                "summary_viewed",
                                mapOf("source" to "search", "area_name" to trimmedQuery),
                            )
                            AppLogger.d { "Tracked: summary_viewed, source=search, area=$trimmedQuery" }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(e) { "Search portrait streaming failed" }
                    _uiState.value = SearchUiState.Error(
                        query = trimmedQuery,
                        message = e.message ?: "Search failed. Please try again.",
                    )
                }
            }
        }

        fun clearSearch() {
            searchJob?.cancel()
            loadRecentSearches()
        }

        private fun loadRecentSearches() {
            viewModelScope.launch {
                val recent = database.searchHistoryQueries.getRecentSearches().executeAsList()
                _uiState.value = SearchUiState.Idle(recentSearches = recent)
            }
        }

        private suspend fun persistSearch(query: String) {
            database.searchHistoryQueries.upsertSearch(
                query = query,
                searched_at = clock.nowMs(),
            )
        }

        private fun com.areadiscovery.ui.summary.SummaryUiState.toSearchUiState(
            query: String,
        ): SearchUiState = when (this) {
            is com.areadiscovery.ui.summary.SummaryUiState.Streaming ->
                SearchUiState.Streaming(
                    query = query,
                    areaName = areaName,
                    buckets = buckets,
                )
            is com.areadiscovery.ui.summary.SummaryUiState.Complete ->
                SearchUiState.Complete(
                    query = query,
                    areaName = areaName,
                    buckets = buckets,
                    pois = pois,
                )
            is com.areadiscovery.ui.summary.SummaryUiState.Error ->
                SearchUiState.Error(query = query, message = message)
            else -> SearchUiState.Streaming(
                query = query,
                areaName = query,
                buckets = emptyMap(),
            )
        }
    }
    ```
    **Key decisions:**
    - Reuses `SummaryStateMapper` from `ui.summary` — no duplicate streaming-state logic.
    - Reuses `SummaryUiState` as an internal staging type before mapping to `SearchUiState`.
    - `persistSearch()` is called before streaming starts (optimistic persistence).
    - `clearSearch()` cancels in-flight search and reloads recent searches.
    - **Thread safety**: `database.searchHistoryQueries` is called from `viewModelScope.launch` (Main dispatcher). SQLDelight Android driver dispatches DB ops synchronously on calling thread; for Main-thread safety use `.executeAsListFlow()` with `flowOn(Dispatchers.IO)` if perf is an issue — but for a 5-row read, Main is acceptable in V1.

- [x] Task 5: Create `SearchScreen.kt` (AC: 1, 2, 3, 6, 7, 8)
  - [x] 5.1: Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchScreen.kt`:
    ```kotlin
    @Composable
    fun SearchScreen(
        onNavigateBack: () -> Unit,
        viewModel: SearchViewModel = koinViewModel(),
    )
    ```
    - `koinViewModel()` import: `org.koin.compose.viewmodel.koinViewModel` (Koin 4.x KMP pattern — same as all other screens)
  - [x] 5.2: Screen structure — `Scaffold` with `TopAppBar`:
    ```
    TopAppBar:
      NavigationIcon: BackArrow icon → onNavigateBack()
      Title: TextField (search input, autofocus on entry)
      TrailingIcon: X clear button (visible when query non-empty)
    Content:
      when (uiState) {
        is Idle → IdleContent(recentSearches, categoryChips, onSearch)
        is Streaming → StreamingContent(areaName, buckets)
        is Complete → CompleteContent(areaName, buckets, pois)
        is Error → ErrorContent(query, message, onRetry)
      }
    ```
  - [x] 5.3: Search input — use `TextField` (not `OutlinedTextField`) styled as a top bar search field:
    ```kotlin
    var query by rememberSaveable { mutableStateOf("") }
    // In TopAppBar title slot:
    TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search any area...") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    ```
    - `query` is local Compose state (not in ViewModel) — search is triggered on IME action or chip tap, not on every keystroke (no debounce needed in V1; field is just an input vehicle)
    - Request focus automatically on screen entry via `LaunchedEffect(Unit) { focusRequester.requestFocus() }`
  - [x] 5.4: Idle state content (`IdleContent`):
    ```
    Column {
      // Recent searches section (only if non-empty)
      if (recentSearches.isNotEmpty()) {
        Text("Recent", style = labelMedium, color = onSurfaceVariant)
        FlowRow {
          recentSearches.forEach { recent ->
            SuggestionChip(label = recent, onClick = { onSearch(recent) })
          }
        }
        Spacer(md)
      }
      // Category chips
      Text("Explore", style = labelMedium, color = onSurfaceVariant)
      FlowRow {
        categoryChips.forEach { chip ->
          SuggestionChip(label = chip, onClick = { onSearch(chip) })
        }
      }
    }
    ```
    - `SuggestionChip` is a stable M3 component — no `@OptIn` needed.
    - `FlowRow` is from `androidx.compose.foundation.layout.FlowRow` — check it's in Compose Multiplatform version. If not available, use `LazyRow` instead.
  - [x] 5.5: Streaming state content — reuse `BucketCard` composables:
    ```kotlin
    // StreamingContent:
    LazyColumn(contentPadding = PaddingValues(MaterialTheme.spacing.md)) {
        item {
            Text(
                text = areaName,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.sm),
            )
        }
        // Reuse same BucketCard pattern as SummaryScreen
        items(BucketType.entries.toList()) { bucketType ->
            val bucketState = buckets[bucketType]
            if (bucketState != null) {
                BucketCard(bucketType = bucketType, state = bucketState)
                HorizontalDivider()
            }
        }
    }
    ```
    - `BucketCard` is at `com.areadiscovery.ui.components.BucketCard` — import directly.
    - Same `BucketDisplayState` type — no adaptation needed.
  - [x] 5.6: Complete state content — same as Streaming (buckets + areaName), no additional changes needed for V1 (InlineChatPrompt deferred — chat screen wiring is Epic 4).
  - [x] 5.7: Error state content:
    ```kotlin
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(MaterialTheme.spacing.lg))
        Text("Couldn't load portrait for \"$query\"", style = bodyMedium)
        Text(message, style = bodySmall, color = onSurfaceVariant)
        Spacer(Modifier.height(MaterialTheme.spacing.md))
        Button(onClick = { viewModel.search(query) }) { Text("Try again") }
    }
    ```
  - [x] 5.8: Back navigation — `onNavigateBack()` is called from back arrow AND from `BackHandler`:
    ```kotlin
    BackHandler(enabled = uiState !is SearchUiState.Idle) {
        viewModel.clearSearch()
    }
    BackHandler(enabled = uiState is SearchUiState.Idle) {
        onNavigateBack()
    }
    ```
    Actually simpler: always call `onNavigateBack()` from the back arrow (popBackStack). `clearSearch()` should be called when the user explicitly navigates back from results to idle — but for V1 simplicity, back always exits the search screen.
    **Simplified**: Back arrow → `onNavigateBack()` only. No intermediate "back to idle" state.
  - [x] 5.9: When a chip is tapped (recent search or category chip):
    ```kotlin
    onSearch = { chipText ->
        query = chipText  // Update the text field
        viewModel.search(chipText)
    }
    ```
  - [x] 5.10: Accessibility:
    - Search `TextField` contentDescription: `"Search for an area"`
    - Back button contentDescription: `"Back"`
    - Each `SuggestionChip` label is already accessible via M3 defaults
    - Streaming buckets reuse `BucketCard`'s existing accessibility annotations (established in Epic 1/2)

- [x] Task 6: Wire navigation (AC: 1, 6)
  - [x] 6.1: Add `SearchRoute` to `Routes.kt`:
    ```kotlin
    @Serializable
    data object SearchRoute
    ```
  - [x] 6.2: Add `SearchScreen` composable to `AppNavigation.kt`:
    ```kotlin
    import com.areadiscovery.ui.search.SearchScreen

    // Inside NavHost block, add:
    composable<SearchRoute> {
        SearchScreen(onNavigateBack = { navController.popBackStack() })
    }
    ```
  - [x] 6.3: Update `SummaryScreen` parameter + `AppNavigation.kt`:
    - Add `onNavigateToSearch: () -> Unit` parameter to `SummaryScreen` composable function signature.
    - In `AppNavigation.kt`, update `SummaryScreen` call:
      ```kotlin
      composable<SummaryRoute> {
          SummaryScreen(
              onNavigateToChat = { ... },
              onNavigateToSearch = { navController.navigate(SearchRoute) },
          )
      }
      ```
  - [x] 6.4: Update `SummaryScreen.kt` to add search entry points:
    - **Top app bar search icon**: Add `actions` param to `MediumTopAppBar`:
      ```kotlin
      actions = {
          IconButton(onClick = onNavigateToSearch) {
              Icon(
                  imageVector = Icons.Filled.Search,
                  contentDescription = "Search areas",
              )
          }
      }
      ```
      - `Icons.Filled.Search` is in `androidx.compose.material.icons.filled.Search` — already imported via `compose-material-icons-extended` (confirmed present since Story 3.3).
    - **LocationFailed state search CTA**: In `SummaryScreen`'s `LocationFailed` branch, add a button below the existing message:
      ```kotlin
      is SummaryUiState.LocationFailed -> {
          // existing message text...
          Spacer(Modifier.height(MaterialTheme.spacing.md))
          Button(onClick = onNavigateToSearch) {
              Text("Search an area")
          }
      }
      ```

- [x] Task 7: Register in DI (AC: 3, 4)
  - [x] 7.1: Add to `DataModule.kt`:
    ```kotlin
    single { SearchAreaUseCase(get()) }
    ```
  - [x] 7.2: Add to `UiModule.kt`:
    ```kotlin
    viewModel { SearchViewModel(get(), get(), get(), get(), get(), get()) }
    ```
    - Constructor order: `searchAreaUseCase`, `areaContextFactory`, `stateMapper`, `analyticsTracker`, `database`, `clock`
    - `SummaryStateMapper` is already a `factory` in `UiModule` — Koin `get()` resolves it.
    - `AreaDiscoveryDatabase` is `single` in `DataModule` — `get()` resolves it.
    - `AppClock` is `single<AppClock>` in `DataModule` — `get()` resolves it.

- [x] Task 8: Tests for `SearchViewModel` (AC: 3, 4, 5, 7)
  - [x] 8.1: `searchEmitsStreamingThenComplete` — create ViewModel with fake `SearchAreaUseCase`, call `search("Shibuya, Tokyo")`, collect states, assert transition: Idle → Streaming → Complete.
  - [x] 8.2: `searchPersistsQueryToHistory` — call `search("Paris")`, assert `database.searchHistoryQueries.getRecentSearches()` contains "Paris".
  - [x] 8.3: `searchFiresAnalyticsOnComplete` — assert `analyticsTracker.trackEvent("summary_viewed", mapOf("source" to "search", "area_name" to "Paris"))` called after streaming completes.
  - [x] 8.4: `searchErrorTransitionsToErrorState` — configure fake use case to throw, assert `SearchUiState.Error`.
  - [x] 8.5: `clearSearchRestoresIdleWithRecentSearches` — search "Paris", then `clearSearch()`, assert `SearchUiState.Idle` with "Paris" in `recentSearches`.
  - [x] 8.6: `chipTapAutoFillsAndSearches` — this is UI behavior (tested in SearchScreen UI test if time permits, but ViewModel has no direct hook for chip; test via `search()` method).
  - **Test infrastructure**: Use `InMemorySqliteDriver` (already a dependency: `sqldelight.sqlite.driver` in `androidUnitTest`) + `Dispatchers.Unconfined` for coroutine tests. Use Turbine for flow collection (`turbine` is in test deps). Use existing `FakeAnalyticsTracker` pattern from prior story tests.

## Dev Notes

### Architecture Requirements

**Reuse, don't reinvent — the entire streaming portrait pipeline already exists:**
- `AreaRepository.getAreaPortrait(areaName, context)` — same API call path as GPS-triggered summary
- `SummaryStateMapper.processUpdate()` — handles streaming state transitions
- `BucketCard`, `StreamingTextContent`, `BucketSectionHeader` — UI composables unchanged
- `AreaContextFactory.create()` — same context creation (time-of-day, day-of-week)
- `GetAreaPortraitUseCase` pattern — `SearchAreaUseCase` mirrors it exactly

**SearchViewModel uses SummaryStateMapper internally:**
- `stateMapper.processUpdate(summaryState, bucketUpdate, areaName)` returns `SummaryUiState`
- That `SummaryUiState` is immediately mapped to `SearchUiState` via `toSearchUiState()`
- This avoids duplicating streaming state machine logic

**Database migration pattern (established in Story 2.3 + Story 3.2):**
- New `.sq` file → adds queries to `AreaDiscoveryDatabase` (SQLDelight auto-generates)
- New `.sqm` migration file → runs on existing DBs (migration 3 = adds `search_history`)
- `AndroidSqliteDriver(AreaDiscoveryDatabase.Schema, context, "area_discovery.db")` — Schema includes version, auto-runs migrations
- **Do NOT manually bump any version number** — SQLDelight derives it from migration file count
- Current DB schema version: 2 (after Story 3.2 added migration `2.sqm`)
- After adding `3.sqm`: schema version becomes 3

**`SearchRoute` is NOT in the bottom nav:**
- Bottom nav: Summary, Map, Chat, Saved — 4 tabs, unchanged
- Search is a full-screen modal pushed onto the nav stack (popBackStack() exits it)
- `BottomNavBar.kt` is UNCHANGED

**`SummaryScreen` changes are additive only:**
- Add `onNavigateToSearch: () -> Unit` parameter (with default `{}` to avoid breaking existing test instantiation)
- Add search `IconButton` in `MediumTopAppBar.actions` slot
- Add "Search an area" `Button` in `LocationFailed` branch
- All other SummaryScreen logic is UNCHANGED

**`FlowRow` availability:** In Compose Multiplatform (CMP), `FlowRow` is available since `compose.foundation` 1.5+. Check `libs.versions.toml` for the Compose version before using. If `FlowRow` is not available, use a `LazyRow` for chips. Do NOT add new library dependencies for chip layout.

**`BackHandler` in commonMain:** `BackHandler` is in `androidx.activity.compose` which is Android-only. For KMP commonMain, either:
1. Skip `BackHandler` and rely only on the top app bar back arrow (simplest for V1)
2. Use `expect/actual` — overkill for V1
**Recommended**: Skip `BackHandler` in V1. Top app bar back arrow is sufficient.

**`Icons.Filled.Search`**: Available via `compose-material-icons-extended`. Confirmed present since Story 3.3 (Icons.Outlined.* used there). Import: `import androidx.compose.material.icons.filled.Search`.

**`rememberSaveable` for query state**: Use `rememberSaveable { mutableStateOf("") }` for the search text field so it survives configuration changes (screen rotation). This is local UI state — not in ViewModel.

**Analytics already wired:** `AnalyticsTracker` is in `UiModule` via `get()` (same pattern as `SummaryViewModel`). No new analytics setup needed.

**`SummaryStateMapper` is a `factory` in `UiModule`:** Each `viewModel {}` block that requests `get<SummaryStateMapper>()` gets a fresh instance. For `SearchViewModel`, this is correct — mappers are stateful (track streaming state) and must not be shared between ViewModels.

### Project Structure Notes

**Files to create:**

| Action | Path |
|--------|------|
| CREATE | `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/search_history.sq` |
| CREATE | `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/3.sqm` |
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/usecase/SearchAreaUseCase.kt` |
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchUiState.kt` |
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchViewModel.kt` |
| CREATE | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchScreen.kt` |
| CREATE | `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/search/SearchViewModelTest.kt` |

**Files to modify:**

| Action | Path |
|--------|------|
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/Routes.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt` |
| MODIFY | `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt` |
| MODIFY | `_bmad-output/implementation-artifacts/sprint-status.yaml` |

**Files UNCHANGED (do not touch):**
- `SummaryViewModel.kt` — GPS-triggered portrait flow unchanged
- `SummaryUiState.kt` — reused as internal type in SearchViewModel, not modified
- `SummaryStateMapper.kt` — reused as-is
- `AreaRepository.kt` / `AreaRepositoryImpl.kt` — no new methods needed
- `GetAreaPortraitUseCase.kt` — SearchAreaUseCase is a separate class, not a modification
- `BottomNavBar.kt` — search is NOT a bottom tab
- `MapScreen.kt`, `MapViewModel.kt`, `POIListView.kt` — unrelated
- `AppModule.kt`, `PlatformModule.android.kt` — no new platform dependencies

### Previous Story Intelligence

From Story 3.4 (and prior stories):

1. **`koinViewModel()` import**: Always `org.koin.compose.viewmodel.koinViewModel` — never `org.koin.androidx.compose.koinViewModel` (wrong for KMP).
2. **ViewModel constructor in UiModule**: Positional `get()` — order must match constructor exactly. Double-check `SearchViewModel` constructor order vs `viewModel { SearchViewModel(get(), get(), get(), get(), get(), get()) }`.
3. **`SummaryStateMapper` is a factory, not singleton**: Each `get()` in a `viewModel {}` block gets a fresh instance. This is correct — `SummaryStateMapper` is stateful per-ViewModel.
4. **`LazyColumn` + `items(BucketType.entries.toList())`**: `BucketType.entries` is Kotlin 1.9+ syntax for enum values. Established in existing `SummaryScreen`.
5. **`MaterialTheme.spacing.md/sm/xs/lg`**: Custom extension property from `Spacing.kt` (`xs=4dp, sm=8dp, md=16dp, lg=24dp`). Always use these, never hardcode dp values.
6. **`HorizontalDivider()`**: M3 divider component. Used between bucket cards in `SummaryScreen` — reuse same pattern.
7. **`TextOverflow.Ellipsis` import**: `import androidx.compose.ui.text.style.TextOverflow` if needed.
8. **`collectAsStateWithLifecycle()`**: Import `import androidx.lifecycle.compose.collectAsStateWithLifecycle`. Use this (not `collectAsState()`) for lifecycle-aware collection.
9. **`@OptIn(ExperimentalMaterial3Api::class)`**: Needed for `TopAppBar` (it's experimental in some M3 versions). Add to `SearchScreen` file.
10. **DB queries on Main thread**: For short queries (≤5 rows), calling SQLDelight on Main thread is acceptable in V1. For production, add `.flowOn(Dispatchers.IO)`. Don't over-engineer in V1.

### Git Intelligence (last 8 commits)

1. **e4c8c28** — Fix slow app launch: lastLocation-first + cache-hit state mapping. Key insight: `LocationProvider.lastLocation()` returns cached GPS faster than `getCurrentLocation()`. Use this pattern for any future location operations.
2. **7bbb4c8** — Address Story 3.4 review findings (3M, 2L): modifier bug, @Preview, safe key, capitalizedType, clickable/semantics order.
3. **589d934** — Implement Story 3.4: POI accessible list view with Map/List toggle.
4. **b8c04c4** — Mark Story 3.3 as done.
5. **ab7eaca** — Round 2 review fixes: `resolveActivity` guard, `rememberUpdatedState`, `SnackbarHost` peek padding, `SHEET_PEEK_HEIGHT` constant, manifest `<queries>` declaration.

**Code patterns to follow:**
- `sealed class SummaryUiState` pattern → mirror for `SearchUiState`
- `open class GetAreaPortraitUseCase` → mirror for `SearchAreaUseCase` (open for testability)
- `MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)` → `MutableStateFlow<SearchUiState>(SearchUiState.Idle())`
- `viewModelScope.launch { }` for async ops — established in both `SummaryViewModel` and `MapViewModel`
- `CancellationException` re-throw in catch block — mandatory pattern for coroutine cancellation safety

### References

- `SearchAreaUseCase.kt` architecture slot: `_bmad-output/planning-artifacts/architecture.md` line ~586
- `SearchScreen.kt` + `SearchViewModel.kt` architecture slot: `_bmad-output/planning-artifacts/architecture.md` line ~647-649
- Epic 5.1 ACs: `_bmad-output/planning-artifacts/epics.md` lines 675-690
- UX search zero-state + chips: `_bmad-output/planning-artifacts/ux-design-specification.md` line ~694 (Jamie Flow diagram)
- UX search icon in top app bar hint: `_bmad-output/planning-artifacts/ux-design-specification.md` line ~356 ("Search available but secondary")
- UX navigation patterns: `_bmad-output/planning-artifacts/ux-design-specification.md` line ~1177-1184
- `SummaryViewModel.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryViewModel.kt`
- `SummaryStateMapper.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryStateMapper.kt`
- `BucketCard.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketCard.kt`
- `AppNavigation.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt`
- `Routes.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/Routes.kt`
- `SummaryScreen.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt`
- `DataModule.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
- `UiModule.kt`: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
- `area_bucket_cache.sq` migration pattern: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_bucket_cache.sq`
- Migration `2.sqm` pattern: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/2.sqm`
- `Spacing.kt` tokens (xs=4, sm=8, md=16, lg=24): `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Spacing.kt`
- `AnalyticsTracker` pattern: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryViewModel.kt` lines 86-94

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- SQLDelight generated property is `search_historyQueries` (with underscore from table name `search_history`), not `searchHistoryQueries`. Fixed during build validation.

### Completion Notes List

- Task 1: Created `search_history.sq` table definition + `3.sqm` migration. Schema version auto-increments to 3.
- Task 2: Created `SearchAreaUseCase` mirroring `GetAreaPortraitUseCase` pattern.
- Task 3: Created `SearchUiState` sealed class with Idle/Loading/Streaming/Complete/Error states + `DEFAULT_CATEGORY_CHIPS`. **Deviation from spec:** Added `Loading` state (spec said "No Loading state") — needed for immediate visual feedback when search is submitted before first AI streaming chunk arrives. Without it, UI appeared unresponsive for several seconds.
- Task 4: Created `SearchViewModel` — reuses `SummaryStateMapper` internally for streaming state transitions, maps to `SearchUiState` via extension. Persists search history, fires analytics on complete.
- Task 5: Created `SearchScreen` with TopAppBar search field (autofocus), FlowRow chips for recent searches and categories, portrait streaming via BucketCard reuse, error retry. Skipped `BackHandler` (Android-only) per Dev Notes — back arrow only.
- Task 6: Added `SearchRoute`, wired into `AppNavigation` with `popBackStack`. Added `onNavigateToSearch` param to `SummaryScreen`, search icon in MediumTopAppBar actions, "Search an area" button in LocationFailed state.
- Task 7: Registered `SearchAreaUseCase` in DataModule, `SearchViewModel` in UiModule.
- Task 8: 7 unit tests covering streaming lifecycle, history persistence, analytics tracking, error handling, clearSearch, blank query rejection, and recent search ordering. Tests use `JdbcSqliteDriver(IN_MEMORY)` in androidUnitTest source set.

### Change Log

- 2026-03-05: Implemented Story 5.1 — Manual Area Search (all 8 tasks)
- 2026-03-05: Address code review findings (3M, 4L): race condition fix (loadJob tracking), duplicate analytics guard, DB ops on IO dispatcher, unused import, internal visibility for DEFAULT_CATEGORY_CHIPS, Loading state deviation documented

### File List

**Created:**
- `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/search_history.sq`
- `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/3.sqm`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/usecase/SearchAreaUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchUiState.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/SearchScreen.kt`
- `composeApp/src/androidUnitTest/kotlin/com/areadiscovery/ui/search/SearchViewModelTest.kt`

**Modified:**
- `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/Routes.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
