# Story 1.6: Summary Screen with Mock Data & Navigation Shell

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user**,
I want to open the app and immediately see a beautiful, streaming area portrait filling the screen,
So that I experience the "whoa" moment within seconds of launch.

## Acceptance Criteria

1. **Given** the app launches with mock data (no real API or GPS), **When** the summary screen appears, **Then** a full-screen scrollable card renders the six-bucket area portrait for Alfama, Lisbon
2. **And** content streams bucket-by-bucket using `StreamingTextContent` within each bucket section
3. **And** the area name ("Alfama, Lisbon") displays prominently at the top in a collapsing orange gradient header (`MediumTopAppBar` with `enterAlwaysScrollBehavior`)
4. **And** each bucket uses `BucketSectionHeader` with the correct icon and title
5. **And** at least one `HighlightFactCallout` is visible per bucket
6. **And** `ConfidenceTierBadge` appears inline on AI-generated content
7. **And** `SummaryViewModel` exposes `StateFlow<SummaryUiState>` with states: `Loading`, `Streaming`, `Complete`, `Error`
8. **And** `SummaryViewModel` collects the `Flow<BucketUpdate>` from `MockAreaIntelligenceProvider` and uses `SummaryStateMapper` to transform updates into `SummaryUiState`
9. **And** a 4-tab bottom `NavigationBar` is visible: Summary (active), Map (placeholder), Chat (placeholder), Saved (placeholder)
10. **And** tapping Map, Chat, or Saved tabs navigates to placeholder screens with centered tab name text
11. **And** `InlineChatPrompt` appears at the bottom of the summary content after the last bucket (Nearby) as a bridge to the Chat tab
12. **And** the screen is readable top-to-bottom with TalkBack enabled [NFR20]
13. **And** the screen functions correctly at 200% system font scale
14. **And** Koin DI modules are set up providing `MockAreaIntelligenceProvider`, `SummaryStateMapper`, and `SummaryViewModel`
15. **And** all six skeleton bucket headers appear immediately on load before any streaming begins (pending state)
16. **And** pull-to-refresh re-triggers the streaming flow from the mock provider

## Tasks / Subtasks

- [x] Task 1: Add navigation dependency to project (AC: #9, #10)
  - [x] 1.1 Add `androidx-navigation = "2.9.1"` version to `libs.versions.toml` under `[versions]`
  - [x] 1.2 Add `androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }` to `[libraries]`
  - [x] 1.3 Add `implementation(libs.androidx.navigation.compose)` to `commonMain` dependencies in `build.gradle.kts`
  - [x] 1.4 Add `kotlin("plugin.serialization")` to plugins block if not present (needed for type-safe routes via `@Serializable`) — already present

- [x] Task 2: Create navigation routes and shell (AC: #9, #10)
  - [x] 2.1 Create `ui/navigation/Routes.kt` with `@Serializable` route objects: `SummaryRoute`, `MapRoute`, `ChatRoute`, `SavedRoute`
  - [x] 2.2 Create `ui/navigation/AppNavigation.kt` with `NavHost` + `NavController` setup, defining composable destinations for all 4 routes
  - [x] 2.3 Create `ui/navigation/BottomNavBar.kt` composable with `NavigationBar` + 4 `NavigationBarItem`s (Summary: `Icons.Filled.Explore`, Map: `Icons.Filled.Map`, Chat: `Icons.AutoMirrored.Filled.Chat`, Saved: `Icons.Filled.Bookmark`)
  - [x] 2.4 Use `popUpTo(startDestination) { saveState = true }` + `launchSingleTop = true` + `restoreState = true` for tab switching to avoid back stack bloat
  - [x] 2.5 Active tab state derived from `navController.currentBackStackEntryAsState()` — highlighted with orange indicator
  - [x] 2.6 Delete `.gitkeep` from `ui/navigation/` once real files are added

- [x] Task 3: Create placeholder screens for Map, Chat, Saved tabs (AC: #10)
  - [x] 3.1 Create `ui/map/MapPlaceholderScreen.kt` — centered `Text("Map")` in a `Box(fillMaxSize)`
  - [x] 3.2 Create `ui/chat/ChatPlaceholderScreen.kt` — centered `Text("Chat")` in a `Box(fillMaxSize)`
  - [x] 3.3 Create `ui/saved/SavedPlaceholderScreen.kt` — centered `Text("Saved")` in a `Box(fillMaxSize)`
  - [x] 3.4 Delete `.gitkeep` from `ui/map/`, `ui/chat/`, `ui/saved/` once real files are added

- [x] Task 4: Create `SummaryViewModel` (AC: #7, #8, #15, #16)
  - [x] 4.1 Create `ui/summary/SummaryViewModel.kt` extending `androidx.lifecycle.ViewModel`
  - [x] 4.2 Constructor-inject `AreaIntelligenceProvider` and `SummaryStateMapper`
  - [x] 4.3 Expose `val uiState: StateFlow<SummaryUiState>` backed by `MutableStateFlow(SummaryUiState.Loading)` — NEVER expose the mutable version
  - [x] 4.4 On `init`, call `loadPortrait()` which launches a coroutine in `viewModelScope` to collect `provider.streamAreaPortrait("Alfama, Lisbon", mockContext)` and feed each `BucketUpdate` through `stateMapper.processUpdate(currentState, update, "Alfama, Lisbon")`
  - [x] 4.5 Create `private val mockContext = AreaContext(timeOfDay = "morning", dayOfWeek = "Wednesday", visitCount = 0, preferredLanguage = "en")` for the hardcoded mock scenario
  - [x] 4.6 Implement `fun refresh()` that resets state to `Loading` and re-launches `loadPortrait()`  (for pull-to-refresh)
  - [x] 4.7 Wrap the collection in try/catch — on exception, emit `SummaryUiState.Error(e.message ?: "Unknown error")`
  - [x] 4.8 Cancel any in-flight collection job before starting a new one in `refresh()`

- [x] Task 5: Create `SummaryScreen` composable (AC: #1, #2, #3, #4, #5, #6, #11, #12, #13, #15, #16)
  - [x] 5.1 Create `ui/summary/SummaryScreen.kt` accepting `viewModel: SummaryViewModel = koinViewModel()` and `onNavigateToChat: (String) -> Unit` lambda
  - [x] 5.2 Collect `viewModel.uiState.collectAsStateWithLifecycle()` (from `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`)
  - [x] 5.3 Render collapsing orange gradient header using `MediumTopAppBar` with `TopAppBarDefaults.enterAlwaysScrollBehavior()`:
    - Expanded: area name in `displayMedium` (28sp Bold) + visit context ("First visit") + temporal chip ("Morning")
    - Collapsed: slim bar with area name only
    - Background: `MaterialTheme.colorScheme.primary` (orange gradient)
  - [x] 5.4 Use `Scaffold` with `modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` to connect scroll behavior
  - [x] 5.5 **Loading state**: Render all 6 `BucketCard` composables in `LazyColumn` with pending/skeleton state (`isStreaming=false, isComplete=false`) — gives immediate visual structure
  - [x] 5.6 **Streaming state**: Render buckets from `state.buckets` map using `BucketCard` — each bucket shows its streaming/complete state independently
  - [x] 5.7 **Complete state**: Same as streaming but `InlineChatPrompt` fades in after the last bucket, POI count shown
  - [x] 5.8 **Error state**: Show error message with retry button that calls `viewModel.refresh()`
  - [x] 5.9 Add `pullToRefresh` modifier (M3 pull-to-refresh) calling `viewModel.refresh()`
  - [x] 5.10 Add `HorizontalDivider` (1px beige `MaterialTheme.colorScheme.surface`) with 24dp (`MaterialTheme.spacing.lg`) vertical spacing between bucket sections
  - [x] 5.11 Content padding: 16dp (`MaterialTheme.spacing.md`) horizontal
  - [x] 5.12 Add `Spacer` at bottom of `LazyColumn` for bottom nav clearance
  - [x] 5.13 All text uses `MaterialTheme` tokens — zero hardcoded values

- [x] Task 6: Set up Koin DI modules (AC: #14)
  - [x] 6.1 Create `di/AppModule.kt` with root module aggregation function `fun appModule() = listOf(dataModule, uiModule)`
  - [x] 6.2 Create `di/DataModule.kt`: `single<AreaIntelligenceProvider> { MockAreaIntelligenceProvider() }` and `factory { SummaryStateMapper() }`
  - [x] 6.3 Create `di/UiModule.kt`: `viewModel { SummaryViewModel(get(), get()) }`
  - [x] 6.4 Delete `.gitkeep` from `di/` once real files are added
  - [x] 6.5 Initialize Koin in platform entry points:
    - Used `KoinApplication` composable in `App.kt` for cross-platform uniformity

- [x] Task 7: Update `App.kt` entry point (AC: #1, #9)
  - [x] 7.1 Replace stub content in `App.kt` with `AreaDiscoveryTheme` wrapping `KoinApplication` wrapping the navigation `Scaffold` with `BottomNavBar` and `NavHost`
  - [x] 7.2 Wire `SummaryScreen` as the start destination
  - [x] 7.3 Wire placeholder screens for Map, Chat, Saved routes
  - [x] 7.4 Pass `onNavigateToChat` lambda from `SummaryScreen` to navigate to Chat tab

- [x] Task 8: Write unit tests for `SummaryViewModel` (AC: #7, #8)
  - [x] 8.1 Create `commonTest/ui/summary/SummaryViewModelTest.kt` — viewModelScope works in commonTest with Dispatchers.setMain(StandardTestDispatcher())
  - [x] 8.2 Create `commonTest/fakes/FakeAreaIntelligenceProvider.kt` implementing `AreaIntelligenceProvider` with configurable emissions
  - [x] 8.3 Test: Initial state is `Loading`
  - [x] 8.4 Test: After first `ContentDelta`, state transitions to `Streaming`
  - [x] 8.5 Test: After `PortraitComplete`, state transitions to `Complete` with POIs
  - [x] 8.6 Test: On provider error (exception in flow), state transitions to `Error`
  - [x] 8.7 Test: `refresh()` resets to `Loading` and re-collects
  - [x] 8.8 Use Turbine for `StateFlow` testing: `viewModel.uiState.test { ... }`

- [x] Task 9: Build verification (AC: all)
  - [x] 9.1 Run `./gradlew :composeApp:assembleDebug` — PASS
  - [x] 9.2 Run `./gradlew :composeApp:allTests` — PASS
  - [x] 9.3 Run `./gradlew :composeApp:lint` — PASS

## Dev Notes

### Scope: What This Story Delivers

This story assembles the building blocks from Story 1.5 into a **complete, runnable Summary screen** with a **4-tab navigation shell** — the first screen a user sees. It wires up DI, ViewModel, and navigation while using mock data (no real API or GPS).

**Screen-level deliverables:**
1. `SummaryScreen` — Full-screen continuous scroll rendering six streaming buckets
2. `SummaryViewModel` — Manages streaming state lifecycle, connects provider → mapper → UI
3. `AppNavigation` + `BottomNavBar` — 4-tab navigation shell with type-safe routes
4. Placeholder screens for Map, Chat, Saved tabs
5. Koin DI wiring — Provider, StateMapper, ViewModel injection

**What changes from Story 1.5 output:**
- `SummaryStateMapper` is now consumed by `SummaryViewModel` (was standalone pure Kotlin)
- `BucketCard` and all composables are rendered inside `SummaryScreen` (were standalone components)
- `InlineChatPrompt` now navigates to Chat tab via `NavController`

### Architecture: Summary Screen Data Flow

```
App.kt
└── KoinApplication(appModule())
    └── AreaDiscoveryTheme
        └── Scaffold(bottomBar = BottomNavBar)
            └── NavHost(startDestination = SummaryRoute)
                ├── SummaryScreen(viewModel = koinViewModel())
                │   └── SummaryViewModel
                │       ├── AreaIntelligenceProvider.streamAreaPortrait() → Flow<BucketUpdate>
                │       ├── SummaryStateMapper.processUpdate() → SummaryUiState
                │       └── _uiState: MutableStateFlow<SummaryUiState>
                │           └── SummaryScreen collects via collectAsStateWithLifecycle()
                │               ├── Loading → 6 skeleton BucketCards
                │               ├── Streaming → BucketCards with live content
                │               ├── Complete → All BucketCards + InlineChatPrompt
                │               └── Error → Error message + retry button
                ├── MapPlaceholderScreen
                ├── ChatPlaceholderScreen
                └── SavedPlaceholderScreen
```

### Collapsing Header Pattern

The summary screen uses `MediumTopAppBar` with `enterAlwaysScrollBehavior`:

```kotlin
val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        MediumTopAppBar(
            title = { Text(areaName, style = MaterialTheme.typography.displayMedium) },
            colors = TopAppBarDefaults.mediumTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                scrolledContainerColor = MaterialTheme.colorScheme.primary
            ),
            scrollBehavior = scrollBehavior
        )
    },
    bottomBar = { BottomNavBar(navController) }
) { innerPadding ->
    // LazyColumn with bucket content
}
```

**CRITICAL**: The `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the `Scaffold` is **required** — without it, the top bar won't respond to scroll events.

### Navigation: Type-Safe Routes with @Serializable

CMP 1.10.0 navigation supports type-safe routes using `@Serializable`:

```kotlin
// Routes.kt
@Serializable data object SummaryRoute
@Serializable data object MapRoute
@Serializable data object ChatRoute
@Serializable data object SavedRoute

// AppNavigation.kt
NavHost(navController, startDestination = SummaryRoute) {
    composable<SummaryRoute> { SummaryScreen(onNavigateToChat = { query ->
        navController.navigate(ChatRoute) { /* tab switch pattern */ }
    }) }
    composable<MapRoute> { MapPlaceholderScreen() }
    composable<ChatRoute> { ChatPlaceholderScreen() }
    composable<SavedRoute> { SavedPlaceholderScreen() }
}
```

**Tab switching pattern** (prevents back stack bloat):
```kotlin
navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}
```

**IMPORTANT**: Requires `kotlin("plugin.serialization")` plugin for `@Serializable` on route objects.

### Koin DI Setup Pattern

```kotlin
// di/DataModule.kt
val dataModule = module {
    single<AreaIntelligenceProvider> { MockAreaIntelligenceProvider() }
    factory { SummaryStateMapper() }
}

// di/UiModule.kt
val uiModule = module {
    viewModel { SummaryViewModel(get(), get()) }
}

// di/AppModule.kt
fun appModule() = listOf(dataModule, uiModule)
```

**Koin initialization in Android `MainActivity.kt`:**
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule())
        }
        setContent { App() }
    }
}
```

**Koin initialization for iOS `MainViewController.kt`:**
```kotlin
fun MainViewController() = ComposeUIViewController {
    KoinApplication(application = { modules(appModule()) }) {
        App()
    }
}
```

**Alternative**: Use `KoinApplication` composable wrapper in `App.kt` (works cross-platform):
```kotlin
@Composable
fun App() {
    KoinApplication(application = { modules(appModule()) }) {
        AreaDiscoveryTheme {
            // Navigation scaffold
        }
    }
}
```

Choose the composable wrapper approach if Koin initialization must be identical across platforms. Use platform-specific `startKoin` if Android needs `androidContext()` for future features.

### Summary Screen: LazyColumn Structure

```kotlin
LazyColumn(
    modifier = Modifier.padding(innerPadding),
    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md) // 16dp
) {
    // Visit context header (below collapsing top bar)
    item { VisitContextHeader("First visit", "Morning — cafés, markets, quiet streets") }

    // Six buckets with dividers
    items(BucketType.entries) { bucketType ->
        val bucketState = buckets[bucketType] ?: BucketDisplayState(bucketType = bucketType)
        BucketCard(state = bucketState)

        if (bucketType != BucketType.NEARBY) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.lg), // 24dp
                color = MaterialTheme.colorScheme.surface // beige
            )
        }
    }

    // Inline chat prompt (only when complete)
    if (uiState is SummaryUiState.Complete) {
        item {
            InlineChatPrompt(
                areaName = areaName,
                onNavigateToChat = onNavigateToChat
            )
        }
    }

    // Bottom spacer for nav bar clearance
    item { Spacer(Modifier.height(MaterialTheme.spacing.touchTarget)) } // 48dp
}
```

**Bucket ordering**: Use `BucketType.entries` which gives the enum declaration order: SAFETY → CHARACTER → WHATS_HAPPENING → COST → HISTORY → NEARBY.

### Loading State: Skeleton Buckets

On initial load (before any streaming begins), render all 6 buckets in pending/skeleton state:

```kotlin
when (uiState) {
    is SummaryUiState.Loading -> {
        // Show 6 skeleton bucket cards immediately
        BucketType.entries.forEach { type ->
            BucketCard(state = BucketDisplayState(bucketType = type))
            // isStreaming=false, isComplete=false → skeleton state in BucketSectionHeader
        }
    }
    // ... Streaming, Complete, Error
}
```

This gives users immediate visual structure — they see where content will appear before streaming begins.

### Pull-to-Refresh Pattern

Use M3 pull-to-refresh:

```kotlin
val pullToRefreshState = rememberPullToRefreshState()
val isRefreshing = uiState is SummaryUiState.Loading

PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = { viewModel.refresh() },
    state = pullToRefreshState
) {
    LazyColumn(...) { ... }
}
```

**Note**: Check if `PullToRefreshBox` is available in CMP Material3 `1.10.0-alpha05`. If not, use the lower-level `pullToRefresh` modifier pattern.

### ViewModel Testing Strategy

**SummaryViewModel tests use Turbine for Flow testing:**

```kotlin
class SummaryViewModelTest {
    private val fakeProvider = FakeAreaIntelligenceProvider()
    private val stateMapper = SummaryStateMapper()

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = SummaryViewModel(fakeProvider, stateMapper)
        viewModel.uiState.test {
            assertEquals(SummaryUiState.Loading, awaitItem())
            // ...
        }
    }
}
```

**FakeAreaIntelligenceProvider** — configurable emissions:
```kotlin
class FakeAreaIntelligenceProvider : AreaIntelligenceProvider {
    var emissions: List<BucketUpdate> = emptyList()
    var shouldThrow: Boolean = false

    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = flow {
        if (shouldThrow) throw RuntimeException("Test error")
        emissions.forEach { emit(it) }
    }

    override fun streamChatResponse(...) = flow { /* not used in this story */ }
}
```

**Test location**: Try `commonTest` first. If `viewModelScope` requires `Dispatchers.Main` (Android-only), move to `androidUnitTest` and use `@get:Rule val mainDispatcherRule = MainDispatcherRule()`.

### Dependencies Summary

**Already in project (no changes needed):**
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6`
- `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6`
- `io.insert-koin:koin-core:4.1.1`
- `io.insert-koin:koin-compose:4.1.1`
- `io.insert-koin:koin-compose-viewmodel:4.1.1`
- `io.insert-koin:koin-android:4.1.1` (androidMain)

**NEW dependency required:**
- `org.jetbrains.androidx.navigation:navigation-compose:2.9.1` — Add to `libs.versions.toml` and `commonMain` dependencies
- `kotlin("plugin.serialization")` — Add to plugins block (for `@Serializable` route objects)

### Project Structure Notes

All new files go under existing package structure:

```
composeApp/src/commonMain/kotlin/com/areadiscovery/
├── App.kt                                    ← MODIFIED (navigation shell replaces stub)
├── di/
│   ├── AppModule.kt                          ← NEW
│   ├── DataModule.kt                         ← NEW
│   └── UiModule.kt                           ← NEW
├── ui/
│   ├── navigation/
│   │   ├── Routes.kt                         ← NEW
│   │   ├── AppNavigation.kt                  ← NEW
│   │   └── BottomNavBar.kt                   ← NEW
│   ├── summary/
│   │   ├── SummaryScreen.kt                  ← NEW
│   │   ├── SummaryViewModel.kt               ← NEW
│   │   ├── SummaryStateMapper.kt             ← EXISTING (Story 1.5)
│   │   ├── SummaryUiState.kt                 ← EXISTING (Story 1.5)
│   │   └── BucketDisplayState.kt             ← EXISTING (Story 1.5)
│   ├── map/
│   │   └── MapPlaceholderScreen.kt           ← NEW
│   ├── chat/
│   │   └── ChatPlaceholderScreen.kt          ← NEW
│   └── saved/
│       └── SavedPlaceholderScreen.kt         ← NEW

composeApp/src/commonTest/kotlin/com/areadiscovery/
├── fakes/
│   └── FakeAreaIntelligenceProvider.kt       ← NEW
└── ui/summary/
    └── SummaryViewModelTest.kt               ← NEW

composeApp/src/androidMain/kotlin/com/areadiscovery/
└── MainActivity.kt                           ← MODIFIED (Koin init if using platform-specific startKoin)

composeApp/src/iosMain/kotlin/com/areadiscovery/
└── MainViewController.kt                    ← MODIFIED (Koin init if using platform-specific approach)
```

**Files to delete:**
- `di/.gitkeep`
- `ui/navigation/.gitkeep`
- `ui/map/.gitkeep`
- `ui/chat/.gitkeep`
- `ui/saved/.gitkeep`

### What NOT to Do

- Do NOT create a real `AreaRepository` — mock provider injected directly (repository is Story 2.x)
- Do NOT add GPS/location logic — hardcoded "Alfama, Lisbon" mock context
- Do NOT create `ShareCardRenderer` — that is a later story (share feature)
- Do NOT create `POIDetailCard` — that is Story 3.x (map POI interaction)
- Do NOT create `OfflineStatusIndicator` — that is Phase 1b (Story 7.x)
- Do NOT add SQLDelight database setup — that is Story 2.3
- Do NOT add Firebase Analytics/Crashlytics — that is Story 1.7
- Do NOT hardcode any colors, sizes, or spacing — use `MaterialTheme` tokens exclusively
- Do NOT expose `MutableStateFlow` from ViewModel — always `StateFlow` via `.asStateFlow()`
- Do NOT call provider directly from composables — go through ViewModel
- Do NOT create real chat functionality — Chat is a placeholder screen
- Do NOT add `WindowSizeClass` responsive breakpoints — compact phone only for Phase 1a (save responsive for later)
- Do NOT create use cases — direct provider injection is sufficient for mock phase

### Previous Story (1.5) Learnings

**Patterns established that MUST be followed:**
- `assert()` requires `@ExperimentalNativeApi` on Kotlin/Native — use `assertTrue()`/`assertEquals()` from `kotlin.test`
- Build verification requires all three gates: `assembleDebug`, `allTests`, `lint`
- Actual library versions: Kotlin 2.3.0, Compose MP 1.10.0, AGP 8.11.2, Gradle 8.14.3, Material 3 `1.10.0-alpha05`
- `.gitkeep` files must be deleted when adding real files to a directory
- `androidTarget()` deprecated-as-error in Kotlin 2.3.0 — uses `@Suppress("DEPRECATION")`
- Koin BOM `platform()` not supported in KMP — explicit versions used
- `material-icons-extended` is pinned at version 1.7.3 (CMP 1.10.0)

**Story 1.5 code patterns to reuse:**
- `BucketCard` accepts `BucketDisplayState` — render with the exact data class shape
- `SummaryStateMapper.processUpdate(currentState, update, areaName)` — takes 3 args (areaName was added in code review round 2)
- `SummaryUiState` sealed class: `Loading` (object), `Streaming(buckets, areaName)`, `Complete(buckets, pois, areaName)`, `Error(message)`
- `BucketDisplayState` — `isStreaming=false, isComplete=false` triggers skeleton state in `BucketSectionHeader`
- Theme tokens: `MaterialTheme.spacing.lg` (24dp), `MaterialTheme.spacing.md` (16dp), `MaterialTheme.spacing.bucketInternal` (12dp)
- `PortraitComplete` is `data class(pois: List<POI>)` not `data object` — it carries POI data

**Story 1.5 code review items resolved:**
- M3: Reactive reduce-motion via ContentObserver/NSNotification
- M6: Explicit `areaName` parameter on `processUpdate`

### KMP-Specific Gotchas

- `viewModelScope` in commonMain requires `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` (already in deps)
- `collectAsStateWithLifecycle` requires `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` (already in deps)
- Navigation `@Serializable` routes require `kotlin("plugin.serialization")` plugin
- `koinViewModel()` import: `org.koin.compose.viewmodel.koinViewModel`
- `KoinApplication` composable import: `org.koin.compose.KoinApplication`
- `MediumTopAppBar` and `TopAppBarDefaults.enterAlwaysScrollBehavior()` fully available in CMP Material3
- `PullToRefreshBox` may be experimental in M3 `1.10.0-alpha05` — check for `@OptIn(ExperimentalMaterial3Api::class)`
- `NavigationBar` + `NavigationBarItem` fully available in commonMain
- If ViewModel tests fail in `commonTest` due to `Dispatchers.Main`, move to `androidUnitTest` with `MainDispatcherRule`

### Git Intelligence

Recent commits:
```
a16db36 Add streaming composables, state mapper, and UI components (Story 1.5)
d0895fd Add domain models, mock AI provider, and tests (Story 1.4)
729cd7f Initial commit: KMP project setup, CI/CD, and design system (Stories 1.1–1.3)
```

**Patterns from commits:**
- Source: `composeApp/src/commonMain/kotlin/com/areadiscovery/{layer}/{sublayer}/`
- Tests: `composeApp/src/commonTest/kotlin/com/areadiscovery/{layer}/{sublayer}/`
- Theme tokens accessed via `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.spacing`
- Story 1.5 added `material-icons-extended:1.7.3` — all bucket icons already available

### Latest Technical Information

**Navigation Compose for KMP (2.9.1):**
- Type-safe routes via `@Serializable` data objects/classes
- `composable<RouteType> { }` pattern for destinations
- `navController.currentBackStackEntryAsState()` for active tab tracking
- `navController.graph.findStartDestination().id` for `popUpTo` in tab switching

**Koin 4.1.1 with CMP:**
- `koinViewModel<T>()` from `koin-compose-viewmodel` for lifecycle-aware VM injection
- `viewModel { }` DSL in module definitions
- `KoinApplication(application = { modules(...) }) { }` composable for cross-platform init
- No BOM support — explicit version on each artifact

**Material 3 (1.10.0-alpha05) APIs:**
- `MediumTopAppBar` with `scrollBehavior` parameter fully available
- `TopAppBarDefaults.enterAlwaysScrollBehavior()` available
- `NavigationBar` + `NavigationBarItem` available
- `PullToRefreshBox` likely experimental — use `@OptIn(ExperimentalMaterial3Api::class)`
- `HorizontalDivider` available for bucket section dividers

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.6] — Acceptance criteria, user story
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Architecture] — MVVM + StateFlow pattern, Navigation, UiState sealed class
- [Source: _bmad-output/planning-artifacts/architecture.md#Streaming Data Flow] — Flow<BucketUpdate> → StateMapper → StateFlow<UiState> pipeline
- [Source: _bmad-output/planning-artifacts/architecture.md#Koin Module Pattern] — DI module structure per layer
- [Source: _bmad-output/planning-artifacts/architecture.md#Testing Strategy] — commonTest vs androidUnitTest split, Turbine usage
- [Source: _bmad-output/planning-artifacts/architecture.md#Enforcement Guidelines] — Anti-patterns to reject
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Chosen Direction B: Continuous Scroll] — Full-screen summary card layout
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Experience Mechanics] — Streaming phases, skeleton headers, attention choreography
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Navigation Patterns] — Bottom nav, tab switching, Chat entry point
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Collapsing Orange Gradient Header] — MediumTopAppBar with enterAlwaysScrollBehavior
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Accessibility Strategy] — TalkBack, reduced motion, font scaling, touch targets
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Spacing & Layout Foundation] — 8dp base unit, spacing tokens
- [Source: _bmad-output/implementation-artifacts/1-5-streaming-composables-and-shared-ui-components.md] — Component APIs, state mapper interface, code review fixes

## Change Log

- 2026-03-04: Story 1.6 implementation complete — navigation shell, SummaryViewModel, SummaryScreen, Koin DI, placeholder screens, and ViewModel unit tests
- 2026-03-04: Addressed code review findings — 10 items resolved (3 High, 5 Medium, 2 Low)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Fixed deprecated `mediumTopAppBarColors` → `topAppBarColors` (M3 API change)
- Used `KoinApplication` composable in App.kt for cross-platform Koin init (instead of platform-specific `startKoin`)
- `kotlin("plugin.serialization")` already present from Story 1.3 setup — no change needed
- ViewModel tests work in `commonTest` using `Dispatchers.setMain(StandardTestDispatcher())` — no need for androidUnitTest

### Completion Notes List

- Implemented full 4-tab navigation shell with type-safe `@Serializable` routes
- SummaryViewModel manages streaming lifecycle: Loading → Streaming → Complete/Error with pull-to-refresh support
- SummaryScreen renders collapsing orange header, 6 bucket cards with skeleton/streaming/complete states, and InlineChatPrompt
- Koin DI wires MockAreaIntelligenceProvider, SummaryStateMapper, and SummaryViewModel
- 5 unit tests cover all ViewModel state transitions using Turbine
- All 3 build gates pass: assembleDebug, allTests, lint

### Code Review Follow-ups Resolved

- ✅ H1: Fixed error state layout — replaced Box with Column so Text and Button stack vertically
- ✅ H2: Added "First visit · Morning" visit context label to MediumTopAppBar expanded state
- ✅ H3: Added maxLines=1 + TextOverflow.Ellipsis to header text (displayMedium is 28sp in our theme, not M3 default 45sp)
- ✅ M1: Replaced qualifiedName string comparison with `hasRoute(routeClass)` for robust tab detection
- ✅ M2: Moved SummaryStateMapper from dataModule to uiModule (correct layer placement)
- ✅ M3: Set icon contentDescription to null — label text is visible, prevents TalkBack "Summary Summary" duplication
- ✅ M4: Restored bottom spacer to `spacing.touchTarget` (48dp) for proper bottom nav clearance
- ✅ M5: Added maxLines=3 + TextOverflow.Ellipsis to error message text
- ✅ L1: Wrapped BottomNavBar items list in remember{} to avoid recomposition allocations
- ✅ L2: Added TODO comment explaining query parameter is intended for Chat screen (Story 4.x)

### File List

New files:
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/Routes.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/AppNavigation.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/BottomNavBar.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapPlaceholderScreen.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/chat/ChatPlaceholderScreen.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/saved/SavedPlaceholderScreen.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryViewModel.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/AppModule.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/ui/summary/SummaryViewModelTest.kt

Modified files:
- gradle/libs.versions.toml (added androidx-navigation version + library)
- composeApp/build.gradle.kts (added navigation-compose dependency)
- composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt (replaced stub with navigation shell)

Deleted files:
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/.gitkeep
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/.gitkeep
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/.gitkeep
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/chat/.gitkeep
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/saved/.gitkeep
