---
title: 'V3 Full-Screen Map Experience'
slug: 'v3-fullscreen-map'
created: '2026-03-06'
status: 'Completed'
stepsCompleted: [1, 2, 3, 4, 5, 6]
tech_stack:
  - 'Kotlin Multiplatform (commonMain + androidMain + iosMain)'
  - 'Compose Multiplatform (UI in commonMain)'
  - 'Koin 4.x (DI, viewModelOf pattern)'
  - 'MapLibre Android (expect/actual via MapComposable)'
  - 'MapLibre Annotation Plugin v9 (SymbolManager for custom POI pins)'
  - 'Ktor SSE (Gemini streaming)'
  - 'kotlinx.serialization (JSON parsing)'
  - 'Coroutines + Flow (StateFlow, ViewModel scopes)'
  - 'Open-Meteo HTTP API (no auth, free)'
  - 'kotlin.test + UnconfinedTestDispatcher (unit tests)'
files_to_modify:
  - 'App.kt'
  - 'ui/map/MapScreen.kt'
  - 'ui/map/MapUiState.kt'
  - 'ui/map/MapViewModel.kt'
  - 'ui/map/MapComposable.kt'
  - 'iosMain/ui/map/MapComposable.ios.kt'
  - 'ui/map/POIListView.kt'
  - 'androidMain/ui/map/MapComposable.android.kt'
  - 'domain/model/POI.kt'
  - 'data/remote/GeminiResponseParser.kt'
  - 'data/remote/GeminiPromptBuilder.kt'
  - 'data/remote/GeminiAreaIntelligenceProvider.kt'
  - 'data/remote/MockAreaIntelligenceProvider.kt'
  - 'ui/theme/Color.kt'
  - 'di/UiModule.kt'
  - 'di/DataModule.kt'
code_patterns:
  - 'sealed class UiState with Loading/Ready/LocationFailed variants'
  - 'StateFlow<UiState> exposed from ViewModel, collected via collectAsStateWithLifecycle()'
  - 'expect/actual for platform composables'
  - 'SSE streaming via Ktor httpClient.sse{} with StreamingParser'
  - 'Delimiter-based response parsing: ---BUCKET--- and ---POIS---'
  - 'Koin single{}/viewModel{} registrations in dataModule/uiModule'
  - 'AnalyticsTracker.trackEvent(name, mapOf()) for all key events'
  - 'AppLogger.e(throwable) { message } for errors, .d{} for debug'
  - 'MaterialTheme.spacing.md/sm/xs/lg/xl for all spacing'
  - 'Standalone Color vals in Color.kt (not M3 color scheme) for custom colors'
test_patterns:
  - 'kotlin.test package (not JUnit): @Test, @BeforeTest, @AfterTest'
  - 'UnconfinedTestDispatcher + Dispatchers.setMain/resetMain'
  - 'Fake collaborators in commonTest/fakes/: FakeLocationProvider, FakeAreaRepository, FakeAnalyticsTracker, FakeAreaContextFactory'
  - 'ViewModel factory helper: createViewModel(...) private fun in test class'
  - 'ResettableFakeLocationProvider pattern for suspend/deferred location flows'
  - 'companion object with const val fixtures for parser tests'
  - 'assertIs<T>() for sealed class state assertions'
---

# Tech-Spec: V3 Full-Screen Map Experience

**Created:** 2026-03-06

## Overview

### Problem Statement

The current app uses a summary-first architecture: `SummaryScreen` is the start destination, `MapScreen` is a secondary tab behind a bottom nav, and POI details appear in a `BottomSheetScaffold`. The map is buried behind a text summary and the POI discovery flow is heavy with chrome (`BottomNavBar`, segmented map/list buttons, sheet peek height). This architecture does not deliver the core "whoa, there's so much going on here" moment — immediate, spatial, visual area discovery on app open.

### Solution

Retire the summary-first architecture entirely. Replace with a single full-screen `MapScreen` (v3) as the app's sole primary surface. Remove `Scaffold` padding and `BottomNavBar`. Eight interconnected UI systems — floating top context bar, vibe rail, POI pins + glow zones, expandable POI card, AI search overlay, list mode, FAB menu — all layer as overlays above the map, driven by a single extended `MapViewModel`. POIs come from a new Gemini structured output format with vibe-enriched data. Weather from Open-Meteo (free, no API key required).

### Scope

**In Scope:**
1. Full-screen MapLibre map — dark tile style, fills entire viewport with no Scaffold padding, pan/zoom
2. Top context bar — floating frosted glass overlay, city + visit tag + Open-Meteo weather + local time
3. Vibe rail — 6 vertical orbs (right side), per-vibe colors, count badges, Character auto-selects on load, breathing animation on active orb
4. POI pins — 10 Material Symbols types, always-visible labels, spring pop-in animation, MapLibre circle-layer glow zones per cluster
5. Expandable POI card — centered pop-out overlay, placeholder photo gradient, rating, liveStatus, buzz meter, AI insight, Save/Share/Directions/Ask AI chips; expands to full detail + all-vibe insights
6. AI search bar + overlay — bottom floating bar always visible, full-screen dark overlay on tap, smart question vs location detection, streaming Gemini response card, follow-up chips
7. List mode — toggled via top-right icon, vibe chip strip + scrollable POI cards, same expandable card on tap, auto-activates on MapLibre render failure with AlertBanner
8. FAB menu — bottom-right, spring animation, scrim, Saved Places + Settings items (both tap to snackbar "Coming soon")
9. POI domain model enrichment: add `vibe`, `insight`, `hours`, `liveStatus`, `rating`, `vibeInsights`
10. New Gemini structured output format + updated `GeminiResponseParser` + `GeminiPromptBuilder`
11. Implement `streamChatResponse` in `GeminiAreaIntelligenceProvider` (currently `emptyFlow()`)
12. New `WeatherProvider` interface + `OpenMeteoWeatherProvider` implementation
13. Single extended `MapViewModel` for all shared state (vibes, search, POI card, FAB, weather)
14. v3 dark color palette + vibe accent colors in `Color.kt`
15. Delete summary/search/chat/navigation architecture (full list in Tasks below)

**Out of Scope:**
- Saved Places screen implementation (FAB item visible, tap → snackbar "Coming soon")
- Settings screen (FAB item visible, tap → snackbar "Coming soon")
- Offline cache / Phase 1b features
- Permission-denied onboarding (Jamie's flow) — Phase 1b
- "First visit" tag is hardcoded — no real visit detection logic
- Real photo carousel (placeholder gradient only)
- Viewport-aware pin refresh on map pan (Phase 1b)
- Voice STT/TTS (Phase 1a+)
- Share card renderer (Phase 1b)
- iOS MapComposable changes (Android only — update iOS stub signature only)

## Context for Development

### Codebase Patterns

**Architecture:**
- KMP structure: `commonMain` for all business logic + Compose UI. `androidMain`/`iosMain` for platform actuals.
- Expect/actual: `MapComposable` is `expect fun` in `commonMain/ui/map/MapComposable.kt`. Android actual in `androidMain/ui/map/MapComposable.android.kt` using MapLibre (`org.maplibre.android`).
- DI: Koin 4.x. ViewModels via `viewModel { MyViewModel(get(), get(), ...) }` in `di/UiModule.kt`. Data deps in `di/DataModule.kt`. Platform deps in `di/PlatformModule.android.kt`.
- App entry: `App.kt` composable receives `platformConfig: KoinAppDeclaration` lambda + `onNavigateToMaps` callback. Called from `MainActivity.kt`.

**ViewModel pattern:**
- `MutableStateFlow<UiState>` private, exposed as `val uiState: StateFlow<UiState> = _uiState.asStateFlow()`.
- `collectAsStateWithLifecycle()` in Compose screens.
- `viewModelScope.launch {}` for async work. `loadJob?.cancel()` before re-launching.
- Companion object for constants: `LOCATION_TIMEOUT_MS`, `LOCATION_FAILURE_MESSAGE`.

**Streaming / AI:**
- `AreaIntelligenceProvider` interface has `streamAreaPortrait()` and `streamChatResponse()`.
- `GeminiAreaIntelligenceProvider`: SSE via `httpClient.sse {}`. `streamChatResponse` is currently `emptyFlow()` — needs implementation.
- `GeminiResponseParser.StreamingParser`: delimiter-based (`---BUCKET---`, `---POIS---`). `PoiJson` needs v3 field extensions.
- `GeminiPromptBuilder`: update POI output format; add `buildAiSearchPrompt(query, areaName)`.

**Current MapComposable.android.kt key facts:**
- Uses MapTiler streets-v2 (light). Change to `streets-v2-dark`.
- Currently uses `MarkerOptions()` default pins — replace with `SymbolManager` (annotation plugin v9).
- Has `styleLoaded[]`, `pendingPois[]`, `isDestroyed[]` lifecycle guards — preserve these patterns.
- `onPoiSelected` callback exists — add `activeVibe: Vibe` and `onMapRenderFailed: () -> Unit` params.
- Map render failure: `mapView.addOnDidFailLoadingMapListener { onMapRenderFailed() }`.
- Glow zones implemented as MapLibre `CircleLayer` (circle-radius + circle-blur + circle-opacity) — NOT Compose canvas. This keeps glow zones geo-coordinate-native and avoids screen coordinate conversion complexity.

**POI model v3 fields (all have defaults for DB backward compat):**
```kotlin
@Serializable
data class POI(
    // existing — unchanged
    val name: String,
    val type: String,
    val description: String,
    val confidence: Confidence,
    val latitude: Double?,
    val longitude: Double?,
    // new — all have defaults
    val vibe: String = "",
    val insight: String = "",
    val hours: String? = null,
    val liveStatus: String? = null,   // "open" | "busy" | "closed"
    val rating: Float? = null,
    val vibeInsights: Map<String, String> = emptyMap()
)
```

**MapUiState.Ready v3 extension:**
```kotlin
data class Ready(
    // existing — unchanged
    val areaName: String,
    val latitude: Double,
    val longitude: Double,
    val pois: List<POI> = emptyList(),
    val selectedPoi: POI? = null,
    val showListView: Boolean = false,
    // new v3
    val activeVibe: Vibe = Vibe.DEFAULT,
    val vibePoiCounts: Map<Vibe, Int> = emptyMap(),
    val weather: WeatherState? = null,
    val visitTag: String = "First visit",
    val isSearchOverlayOpen: Boolean = false,
    val searchQuery: String = "",
    val aiResponse: String = "",
    val isAiResponding: Boolean = false,
    val followUpChips: List<String> = emptyList(),
    val isFabExpanded: Boolean = false,
    val mapRenderFailed: Boolean = false,
) : MapUiState()
```

**MapComposable v3 expect signature:**
```kotlin
@Composable
expect fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
    pois: List<POI>,
    activeVibe: Vibe,
    onPoiSelected: (POI?) -> Unit,
    onMapRenderFailed: () -> Unit,
)
```

**v3 composable Box hierarchy (MapScreen):**
```
Box(Modifier.fillMaxSize()) {
    if (state.showListView) {
        ListModeView(...)          // fills box, z-index: base
    } else {
        MapComposable(...)         // fills box, z-index: base
    }
    if (state.mapRenderFailed && state.showListView) {
        AlertBanner(...)           // top, conditional
    }
    TopContextBar(...)             // align Top+Center, always visible
    ZoomControls(...)              // align CenterStart (Android only — pass as slot or hide on iOS)
    VibeRail(...)                  // align CenterEnd, always visible
    if (state.selectedPoi != null) {
        ExpandablePoiCard(...)     // align Center, conditional
    }
    FabScrim(...)                  // conditional on isFabExpanded
    FabMenu(...)                   // align BottomEnd
    AISearchBar(...)               // align BottomStart, always visible, leaves room for FAB
    if (state.isSearchOverlayOpen) {
        SearchOverlay(...)         // fillMaxSize, z-index: top
    }
    SnackbarHost(...)              // align BottomCenter
}
```

**AI question detection (`isQuestion()`):**
```kotlin
private fun isQuestion(query: String): Boolean {
    val q = query.trim().lowercase()
    val questionStarters = listOf("what", "where", "when", "who", "how", "is ", "are ", "can ", "does ", "why")
    return q.endsWith("?") || questionStarters.any { q.startsWith(it) }
}
```

**Area search behavior in `submitSearch()`:** Calls `getAreaPortrait(query, context)` → on `PortraitComplete`, updates `pois`, `areaName`, `vibePoiCounts`, closes overlay. Camera does NOT move (Phase 1a — no geocoding for searched area). Analytics: `search_area_submitted`. **ActiveVibe fallback:** After computing `vibePoiCounts` for the new area, if `vibePoiCounts[activeVibe] == 0` (current vibe has no POIs in the new area), auto-switch `activeVibe` to the vibe with the highest count: `Vibe.values().maxByOrNull { vibePoiCounts[it] ?: 0 } ?: Vibe.DEFAULT`. This prevents a silent empty map state.

**AI question behavior:** Calls `aiProvider.streamChatResponse(query, areaName, emptyList())` → accumulate `ChatToken.text` into `aiResponse`. On final token (`isComplete = true`), set `isAiResponding = false`, compute `followUpChips` via `computeFollowUpChips(query)`. Analytics: `search_question_submitted`.

**Follow-up chips (hardcoded keyword-based):**
```kotlin
private fun computeFollowUpChips(query: String): List<String> {
    val q = query.lowercase()
    return when {
        q.containsAny("safe", "crime", "danger") -> listOf("Is it safe at night?", "What areas to avoid?")
        q.containsAny("food", "eat", "restaurant", "drink") -> listOf("Best time to visit?", "Vegetarian options?")
        q.containsAny("history", "historic", "old", "founded") -> listOf("When was it built?", "Any famous events here?")
        q.containsAny("cost", "price", "expensive", "cheap") -> listOf("Budget tips?", "Free things to do?")
        else -> listOf("Tell me more", "What's nearby?")
    }
}
private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
```

**Buzz meter:** 3-segment bar representing *activity level* (not quality). `busy` → 3 filled (peak activity — venue is packed), `open` → 2 filled (normal activity — open and operating), `closed` → 1 filled (venue exists but is currently inactive), `null` → 0 filled (status unknown). Segments colored with active vibe color. Accessibility content description: "Activity: [Busy/Open/Closed/Unknown]".

**Glow zones (MapLibre CircleLayer in androidMain):** After pins are added via `SymbolManager`, cluster pins within 0.005 degree proximity, compute cluster centroid, add a `CircleLayer` per cluster with:
- `circleRadius` — use a zoom-based interpolation expression so the glow scales with geography, not pixels:
  ```kotlin
  circleRadius(Expression.interpolate(Expression.exponential(2f), Expression.zoom(),
      Expression.stop(10, 30f),
      Expression.stop(14, 80f),
      Expression.stop(17, 160f)
  ))
  ```
- `circleColor(vibeColorHex)`
- `circleOpacity(0.25f)`
- `circleBlur(1.2f)` (soft glow)
- `circleTranslate()` not needed — centroid is the circle center
Use `ValueAnimator` repeating REVERSE from opacity 0.20 to 0.35 over 2000ms for breathing effect on active clusters.

**POI spring pop-in (androidMain):** After `SymbolManager.create()` for each symbol, use a `ValueAnimator(0f, 1f)` over 300ms (FastOutSlowIn) that updates `SymbolManager.update(symbol.withIconSize(it))` for all symbols in the active batch. Stagger per-symbol: 50ms delay between each pin's animator start.

**v3 Color additions:**
```kotlin
val MapBackground = Color(0xFF0A0A0A)
val MapSurfaceDark = Color(0xFF161016)
val MapFloatingUiDark = Color(0xFF0A0A0A)
val VibeCharacter = Color(0xFF2BBCB3)
val VibeHistory = Color(0xFFC4935A)
val VibeWhatsOn = Color(0xFF9B6ED8)
val VibeSafety = Color(0xFFE8A735)
val VibeNearby = Color(0xFF5B9BD5)
val VibeCost = Color(0xFF5CAD6F)
// Remove: GalaxyHistory, GalaxySafety, GalaxyCharacter, GalaxyHappening, GalaxyCost, GalaxyNearby
```

**`ContentNoteBanner` migration:** Move `ui/summary/ContentNoteBanner.kt` → `ui/components/ContentNoteBanner.kt`. Update package declaration. Update import in v3 `MapScreen.kt`.

**Vibe color lookup in Compose:** Add `fun Vibe.toColor(): Color` extension in a new `ui/theme/VibeColors.kt` file or at the bottom of `Color.kt`:
```kotlin
fun Vibe.toColor(): Color = when(this) {
    Vibe.CHARACTER -> VibeCharacter
    Vibe.HISTORY -> VibeHistory
    Vibe.WHATS_ON -> VibeWhatsOn
    Vibe.SAFETY -> VibeSafety
    Vibe.NEARBY -> VibeNearby
    Vibe.COST -> VibeCost
}
```

**GeminiPromptBuilder v3 POI format:**
```
After the last bucket, output this delimiter:
---POIS---

Then output a JSON array of points of interest:
[{"poi":"Time Out Market","type":"food","vibe":"character","insight":"Curated food hall with 24 restaurants","hours":"Sun-Wed 10am-12am, Thu-Sat 10am-2am","liveStatus":"open","confidence":"HIGH","rating":4.5,"latitude":38.71,"longitude":-9.13,"vibeInsights":{"character":"A gathering hub for locals","history":"Converted 1892 iron market hall","cost":"Mid-range, $10-25"}}]

Valid vibe values: character, history, whats_on, safety, nearby, cost
Valid liveStatus values: open, busy, closed
Valid type values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district
```

**`buildAiSearchPrompt(query, areaName)`:**
```
You are a knowledgeable local guide for {areaName}. Answer the following question concisely as if you are a local expert: "{query}". Keep your response under 120 words. Be specific and practical. Do not use bullet points.
```

**`streamChatResponse` implementation in `GeminiAreaIntelligenceProvider`:** Same SSE pattern as `streamAreaPortrait` but uses `buildAiSearchPrompt()`. Response is plain text — no delimiter parsing. For each SSE event, call `responseParser.extractTextFromSseEvent(data)` and emit `ChatToken(text = chunk, isComplete = false)`. After `incoming.collect {}` loop, emit `ChatToken(text = "", isComplete = true)`. Retry logic: same as `streamAreaPortrait` (up to 3 attempts, retryable on network errors).

**Deletion order:** Follow Group D tasks (Tasks 15–20) in sequence — they are the single authoritative deletion order. Complete Group C (ViewModel) before starting Group D to ensure a stable compile baseline.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/.../App.kt` | Strip Scaffold + BottomNavBar + NavController; render MapScreen directly |
| `composeApp/src/commonMain/.../ui/map/MapScreen.kt` | Complete replacement — v3 Box layout |
| `composeApp/src/commonMain/.../ui/map/MapUiState.kt` | Extend `Ready` with 10 new fields |
| `composeApp/src/commonMain/.../ui/map/MapViewModel.kt` | Add 7 new methods + weather fetch + vibePoiCounts computation |
| `composeApp/src/commonMain/.../ui/map/MapComposable.kt` | Add `activeVibe: Vibe`, `onMapRenderFailed: () -> Unit` params |
| `composeApp/src/commonMain/.../ui/map/POIListView.kt` | Add `VibeChipStrip` (top) + refactor to `PoiListCard` items |
| `composeApp/src/androidMain/.../ui/map/MapComposable.android.kt` | Dark tile URL; SymbolManager custom icons; vibe filter; MapLibre glow zones; spring pop-in; render failure listener |
| `composeApp/src/commonMain/.../domain/model/POI.kt` | Add 6 fields with defaults |
| `composeApp/src/commonMain/.../domain/model/Vibe.kt` | NEW: enum with 6 values + displayName, accentColorHex, orbIconName |
| `composeApp/src/commonMain/.../domain/model/WeatherState.kt` | NEW: data class temperatureF, weatherCode, conditionLabel, emoji |
| `composeApp/src/commonMain/.../domain/provider/WeatherProvider.kt` | NEW: interface |
| `composeApp/src/commonMain/.../data/remote/OpenMeteoWeatherProvider.kt` | NEW: Ktor GET, parse temperature + weathercode |
| `composeApp/src/commonMain/.../data/remote/GeminiResponseParser.kt` | Extend PoiJson + update parsePoisJson mapping |
| `composeApp/src/commonMain/.../data/remote/GeminiPromptBuilder.kt` | Update POI section; add `buildAiSearchPrompt()` |
| `composeApp/src/commonMain/.../data/remote/GeminiAreaIntelligenceProvider.kt` | Implement `streamChatResponse()` |
| `composeApp/src/commonMain/.../data/remote/MockAreaIntelligenceProvider.kt` | Update mockPOIs to v3; implement mock `streamChatResponse` |
| `composeApp/src/commonMain/.../ui/theme/Color.kt` | Add v3 dark palette + vibe colors; remove Galaxy* |
| `composeApp/src/commonMain/.../di/UiModule.kt` | Remove old VMs; update MapViewModel registration (+WeatherProvider dep) |
| `composeApp/src/commonMain/.../di/DataModule.kt` | Add OpenMeteoWeatherProvider; remove SearchAreaUseCase |
| `composeApp/src/commonMain/.../ui/components/StreamingTextContent.kt` | Reuse for AI search response streaming text |
| `composeApp/src/commonMain/.../ui/components/AlertBanner.kt` | Reuse for map render failure banner |
| `composeApp/src/commonMain/.../ui/components/TimeOfDay.kt` | Reuse for time display in TopContextBar |
| `composeApp/src/commonTest/.../ui/map/MapViewModelTest.kt` | Extend with new test sections |
| `composeApp/src/commonTest/.../data/remote/GeminiResponseParserTest.kt` | Add v3 POI field assertions |
| `_bmad-output/planning-artifacts/ux-design-specification.md` | Full v3 layout spec — colors, composable hierarchy, interaction flows |
| `_bmad-output/brainstorming/prototype-orbital-map-v3.html` | Working HTML reference implementation — treat as ground truth for visual behavior |

### Technical Decisions

See "Context for Development" above — all technical decisions are inlined with the relevant patterns for complete context.

## Implementation Plan

### Tasks

**Group A — Domain Model & Infrastructure (no UI dependencies)**

- [ ] Task 1: Create `Vibe` enum
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Vibe.kt` (NEW)
  - Action: Create enum with values CHARACTER, HISTORY, WHATS_ON, SAFETY, NEARBY, COST. Each entry has `displayName: String`, `accentColorHex: String`, `orbIconName: String` constructor params. Add `companion object { val DEFAULT = CHARACTER }`.
  - Notes: Hex values — CHARACTER `#2BBCB3`, HISTORY `#C4935A`, WHATS_ON `#9B6ED8`, SAFETY `#E8A735`, NEARBY `#5B9BD5`, COST `#5CAD6F`. Icon names (Material Symbols): CHARACTER `palette`, HISTORY `history`, WHATS_ON `event`, SAFETY `shield`, NEARBY `explore`, COST `payments`.

- [ ] Task 2: Create `WeatherState` data class
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/WeatherState.kt` (NEW)
  - Action: `data class WeatherState(val temperatureF: Int, val weatherCode: Int, val conditionLabel: String, val emoji: String)`. Add companion object with `fun fromCode(code: Int, tempF: Int): WeatherState` mapping: code 0 → "Clear"/☀️, 1–3 → "Partly Cloudy"/⛅, 45–48 → "Foggy"/🌫️, 51–67 → "Rainy"/🌧️, 71–77 → "Snowy"/❄️, 80–82 → "Showers"/🌦️, 95–99 → "Thunderstorm"/⛈️, else → "Cloudy"/🌥️. **Note:** WMO codes 4–44, 68–70, 78, 83–94 (drizzle variants, freezing rain, ice pellets, slight hail) are intentionally collapsed into the `else → "Cloudy"` fallback. This is acceptable for Phase 1a — the app does not need meteorological precision. Do not add extra branches for these edge cases.

- [ ] Task 3: Enrich `POI` domain model
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt`
  - Action: Add to the existing `data class POI(...)`: `val vibe: String = ""`, `val insight: String = ""`, `val hours: String? = null`, `val liveStatus: String? = null`, `val rating: Float? = null`, `val vibeInsights: Map<String, String> = emptyMap()`. All new fields at end with defaults so existing instantiation sites compile without changes.
  - Notes: `@Serializable` already on the class — new fields are serializable. **No DB migration needed** — confirmed via `AreaRepositoryImpl` inspection: POIs are stored as a single `pois_json` TEXT column via `json.encodeToString(pois)` / `json.decodeFromString(cached.pois_json)`. The `Json` instance uses `ignoreUnknownKeys = true`, so old cached blobs missing the new fields will deserialize safely using the declared defaults. Stale cache entries expire naturally via the existing TTL system.

- [ ] Task 4: Create `WeatherProvider` interface
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/WeatherProvider.kt` (NEW)
  - Action: `interface WeatherProvider { suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherState> }`

- [ ] Task 5: Add v3 colors to `Color.kt`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Color.kt`
  - Action: Add 9 new color vals below existing confidence colors: `MapBackground`, `MapSurfaceDark`, `MapFloatingUiDark`, `VibeCharacter`, `VibeHistory`, `VibeWhatsOn`, `VibeSafety`, `VibeNearby`, `VibeCost` (hex values in Technical Decisions above). Remove the 6 `Galaxy*` vals (only used in RadialGalaxy.kt which is being deleted). Add `fun Vibe.toColor(): Color` extension at the bottom of this file.

**Group B — Data Layer (depends on Group A)**

- [ ] Task 6: Update `GeminiPromptBuilder`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
  - Action: (1) In `buildAreaPortraitPrompt()`, replace the POI output format comment and example JSON with the v3 format (see "GeminiPromptBuilder v3 POI format" in Technical Decisions above). Update the instruction text to include vibe, insight, hours, liveStatus, rating, vibeInsights fields and their valid values. (2) Add new method `fun buildAiSearchPrompt(query: String, areaName: String): String` (see "buildAiSearchPrompt" in Technical Decisions above).

- [ ] Task 7: Update `GeminiResponseParser` for v3 POI format
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt`
  - Action: Replace `PoiJson` data class with v3 version: add `@SerialName("poi") val poi: String = ""` (Gemini uses "poi" key, not "name"), `val vibe: String = ""`, `val insight: String = ""`, `val hours: String? = null`, `val liveStatus: String? = null`, `val rating: Float? = null`, `@Serializable val vibeInsights: Map<String, String> = emptyMap()`. In `parsePoisJson()`, update the `POI(...)` constructor call to map `poiJson.poi.ifBlank { poiJson.name }` → `name` (backward compat fallback), and pass all new fields.

- [ ] Task 8: Implement `streamChatResponse` in `GeminiAreaIntelligenceProvider`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt`
  - Action: Replace `override fun streamChatResponse(...): Flow<ChatToken> = emptyFlow()` with a real implementation. Use same SSE pattern as `streamAreaPortrait` but: (1) build prompt via `promptBuilder.buildAiSearchPrompt(query, areaName)`, (2) for each SSE event extract text via `responseParser.extractTextFromSseEvent(data)` and emit `ChatToken(text = text, isComplete = false)`, (3) after `incoming.collect {}` finishes, emit `ChatToken(text = "", isComplete = true)`. No delimiter parsing needed — raw text accumulation. Same retry logic (up to 3 attempts).

- [ ] Task 9: Create `OpenMeteoWeatherProvider`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/OpenMeteoWeatherProvider.kt` (NEW)
  - Action: `class OpenMeteoWeatherProvider(private val httpClient: HttpClient) : WeatherProvider`. Implement `getWeather()`: Ktor GET to `https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,weathercode&temperature_unit=fahrenheit`. Parse response JSON: `current.temperature_2m` (round to Int) + `current.weathercode` (Int). Return `Result.success(WeatherState.fromCode(code, tempF))`. Catch exceptions → `Result.failure(e)`. Add `@Serializable` internal classes `OpenMeteoResponse(val current: OpenMeteoCurrent)` and `OpenMeteoCurrent(@SerialName("temperature_2m") val temperatureF: Float, val weathercode: Int)`.
  - Notes: No API key required. Uses existing `httpClient` singleton from DI. Use `httpClient.get(url)` (not SSE — standard JSON response).

- [ ] Task 10: Update `MockAreaIntelligenceProvider`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MockAreaIntelligenceProvider.kt`
  - Action: (1) Update `mockPOIs` list — add v3 fields to each POI. Assign vibes to the 3 existing Alfama POIs as follows: POI[0] (Time Out Market or similar) → `vibe = "character"`, POI[1] (castle/monument) → `vibe = "history"`, POI[2] (viewpoint/miradouro) → `vibe = "character"`. Since `character` appears twice and `history` once, add exactly 4 new POIs to cover the remaining vibes: one `whats_on` (e.g. "Fado Show at Clube de Fado"), one `safety` (e.g. "Police Station Alfama"), one `nearby` (e.g. "Santa Apolónia Station"), one `cost` (e.g. "Pastéis de Belém"). All 7 POIs must have `vibe`, `insight`, `hours`, `liveStatus`, `rating`, `vibeInsights` populated with Alfama-appropriate values. (2) Implement `streamChatResponse()`: replace the existing stub with a flow that emits a mock response word-by-word with 80ms delay, ending with `ChatToken("", isComplete = true)`. Use a hardcoded response referencing the areaName: `"$areaName is a vibrant historic neighbourhood. Locals recommend visiting in the late afternoon when the light hits the castle walls. Tram 28 is the easiest way to reach the upper streets."`

- [ ] Task 11: Update `DataModule`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
  - Action: (1) Add `single<WeatherProvider> { OpenMeteoWeatherProvider(get()) }`. (2) Remove `single { SearchAreaUseCase(get()) }`.

**Group C — State & ViewModel (depends on Groups A + B)**

- [ ] Task 12: Extend `MapUiState`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
  - Action: Add 10 new fields to `Ready` (all with defaults, as specified in Technical Decisions above): `activeVibe`, `vibePoiCounts`, `weather`, `visitTag`, `isSearchOverlayOpen`, `searchQuery`, `aiResponse`, `isAiResponding`, `followUpChips`, `isFabExpanded`, `mapRenderFailed`. Import `Vibe`, `WeatherState`.

- [ ] Task 13: Extend `MapViewModel`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: (1) Add `weatherProvider: WeatherProvider` as constructor parameter. (2) In `loadLocation()`, after `_uiState.value = MapUiState.Ready(areaName, lat, lon)`, launch a parallel coroutine to call `weatherProvider.getWeather(lat, lon)` and update `weather` in Ready state on success (log on failure, weather stays null). (3) After `PortraitComplete`, compute `vibePoiCounts`: `Vibe.values().associateWith { v -> pois.count { it.vibe.equals(v.name, ignoreCase = true) } }`, update Ready state. (4) Add 7 new public methods: `switchVibe(vibe: Vibe)` (update `activeVibe`, analytics `vibe_switched`), `openSearchOverlay()` (set `isSearchOverlayOpen = true`, clear searchQuery/aiResponse/followUpChips), `closeSearchOverlay()`, `submitSearch(query: String)` (detect question vs area per `isQuestion()`, call appropriate provider, stream result), `toggleFab()` (flip `isFabExpanded`), `onMapRenderFailed()` (set `mapRenderFailed = true`, `showListView = true`), `clearPoiSelection()` (set `selectedPoi = null`). (5) Add private `isQuestion(query)` and `computeFollowUpChips(query)` helpers (see Technical Decisions above). (6) In `submitSearch()` AI path: each `ChatToken` appends to `aiResponse` via `current.copy(aiResponse = current.aiResponse + token.text)`. On `isComplete = true`: set `isAiResponding = false`, set `followUpChips = computeFollowUpChips(query)`. (7) In `submitSearch()` area path: same `getAreaPortrait()` collect loop, on `PortraitComplete` also close overlay and update `areaName`.

- [ ] Task 14: Update `UiModule`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
  - Action: Remove `factory { SummaryStateMapper() }`, `viewModel { SummaryViewModel(...) }`, `viewModel { SearchViewModel(...) }`. Update MapViewModel registration to pass `get()` for the new `weatherProvider` param: `viewModel { MapViewModel(get(), get(), get(), get(), get()) }`.

**Group D — Delete Old Architecture (depends on Group C being stable)**

- [ ] Task 15: Delete feature test files
  - Files to delete: `commonTest/.../ui/summary/SummaryViewModelTest.kt`, `SummaryStateMapperTest.kt`, `TimelineEraParserTest.kt`, `ChipExtractorTest.kt`, `ui/components/TimelineCardTest.kt`, `ui/components/RadialGalaxyTest.kt`
  - Action: Delete all 6 files. The `MapViewModelTest.kt` is kept and extended in Task 34.

- [ ] Task 16: Delete summary package
  - Files to delete: `ui/summary/SummaryScreen.kt`, `SummaryViewModel.kt`, `SummaryUiState.kt`, `SummaryStateMapper.kt`, `BucketDisplayState.kt`, `ChipExtractor.kt`, `DiscoveryChip.kt`, `TimelineEraParser.kt`
  - Notes: `ContentNoteBanner.kt` is in `ui/summary/` but is MOVED, not deleted (Task 17 below).

- [ ] Task 17: Move `ContentNoteBanner` to `ui/components/`
  - Action: Copy `ui/summary/ContentNoteBanner.kt` to `ui/components/ContentNoteBanner.kt`. Update package declaration from `com.areadiscovery.ui.summary` to `com.areadiscovery.ui.components`. Delete original. No logic changes.

- [ ] Task 18: Delete search + chat packages
  - Files to delete: `ui/search/SearchScreen.kt`, `SearchViewModel.kt`, `SearchUiState.kt`, `ui/chat/ChatPlaceholderScreen.kt`
  - Also delete: `domain/usecase/SearchAreaUseCase.kt`

- [ ] Task 19: Delete navigation package
  - Files to delete: `ui/navigation/AppNavigation.kt`, `BottomNavBar.kt`, `Routes.kt`

- [ ] Task 20: Delete old UI components
  - Files to delete: `ui/components/POIDetailCard.kt`, `RadialGalaxy.kt`, `ChipCloud.kt`, `MiniTimelineStrip.kt`, `TimelineCard.kt`, `RightNowCard.kt`, `HighlightFactCallout.kt`, `BucketCard.kt`, `BucketSectionHeader.kt`, `BucketTypeMapper.kt`, `InlineChatPrompt.kt`
  - Notes: `StreamingTextContent.kt`, `ConfidenceTierBadge.kt`, `AlertBanner.kt`, `TimeOfDay.kt`, `ReduceMotion.kt` are KEPT.

**Group E — New UI Components (depends on Groups A–D)**

- [ ] Task 21: Create `TopContextBar`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/TopContextBar.kt` (NEW)
  - Action: Composable `TopContextBar(areaName: String, visitTag: String, weather: WeatherState?, modifier: Modifier)`. Layout: `Row` centered, frosted glass background (`MapFloatingUiDark` at 70% alpha + `BlurMaskFilter` or Material3 surface with low alpha). Content: area name text (white, labelMedium), bullet separator, visitTag text (dim white), bullet, weather emoji + temperature (if weather != null, else shimmer placeholder), bullet, current time via `TimeOfDay`. Padding: `horizontal = 16.dp, vertical = 8.dp`. Corner radius: 20dp pill shape. Max width: `wrapContentWidth()` centered.

- [ ] Task 22: Create `VibeOrb` + `VibeRail`
  - Files: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/VibeOrb.kt` and `VibeRail.kt` (NEW)
  - Action: `VibeOrb(vibe: Vibe, isActive: Boolean, poiCount: Int, onClick: () -> Unit)`: Circle 48dp, background = active vibe color at 90% if active / 40% if inactive, scale 1.1 if active / 0.9 if inactive via `animateFloatAsState`. Count badge (small circle top-right, white text, vibe color bg). Center: `Icon(imageVector = vibe.toImageVector(), contentDescription = vibe.displayName, tint = Color.White, modifier = Modifier.size(20.dp))`. Add the following extension in `VibeOrb.kt` (uses `Icons.Default.*` from `material-icons-extended` — available in KMP):
    ```kotlin
    fun Vibe.toImageVector(): ImageVector = when (this) {
        Vibe.CHARACTER -> Icons.Default.Palette
        Vibe.HISTORY   -> Icons.Default.History
        Vibe.WHATS_ON  -> Icons.Default.Event
        Vibe.SAFETY    -> Icons.Default.Shield
        Vibe.NEARBY    -> Icons.Default.Explore
        Vibe.COST      -> Icons.Default.Payments
    }
    ```
    Verify `material-icons-extended` is in `commonMain` dependencies (check `build.gradle.kts`). If absent, add `implementation(compose.materialIconsExtended)`. Check `ReduceMotion` — if false, breathing animation via `InfiniteTransition` on alpha (0.85↔1.0 over 1800ms) for active orb only. If reduced motion, no animation. `VibeRail(vibes, activeVibe, vibePoiCounts, onVibeSelected)`: `Column(verticalArrangement = Arrangement.spacedBy(8.dp))` of 6 `VibeOrb`s. Padding bottom to clear FAB.

- [ ] Task 23: Create `ExpandablePoiCard`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ExpandablePoiCard.kt` (NEW)
  - Action: Composable `ExpandablePoiCard(poi: POI, activeVibe: Vibe, onDismiss: () -> Unit, onDirectionsClick: (Double, Double, String) -> Unit, onAskAiClick: (String) -> Unit, onSaveClick: () -> Unit, onShareClick: () -> Unit)`. State: `var expanded by remember { mutableStateOf(false) }`. Background: `MapSurfaceDark` at 97% alpha, 16dp corner radius, subtle vibe-color border at 12% alpha. Compact section (always shown): placeholder photo gradient (vibe color gradient, 120dp height, rounded top), POI name (titleMedium, white), type chip (labelMedium), rating row (stars, `poi.rating`), liveStatus badge (colored pill: green/orange/grey for open/busy/closed), buzz meter (3 segments, vibe color), insight text (bodyMedium, dim white), row of 4 chips: Save / Share / Directions / Ask AI (each AssistChip with icon). Expandable section (via `AnimatedVisibility(expanded)`): full description, hours (if non-null), all-vibe insights list (colored dot per vibe + insight text), local tip. "More details" / "Less" button at bottom. Dismiss: scrim click only (scrim handled by caller — see Task 31). **Do NOT use `BackHandler` in this composable** — it is `commonMain` and `BackHandler` is Android-only (`androidx.activity.compose`). Back-press dismissal is handled in `MapScreen.kt` (Task 31) via a platform-aware `BackHandler` placed inside an `if (LocalPlatform.current == Platform.Android)` block or an `expect/actual` wrapper.
  - Notes: "Ask AI" chip calls `onAskAiClick("Tell me more about ${poi.name}")`. "Directions" chip: `poi.latitude` and `poi.longitude` are `Double?` — **hide the Directions chip entirely** (do not render it) when either value is null, i.e. `if (poi.latitude != null && poi.longitude != null) { DirectionsChip(...) }`. When shown, calls `onDirectionsClick(poi.latitude!!, poi.longitude!!, poi.name)`. Save shows `✓ Saved` feedback via local state toggle.

- [ ] Task 24: Create `SearchOverlay` + `AISearchBar`
  - Files: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/SearchOverlay.kt` and `AISearchBar.kt` (NEW)
  - Action: `AISearchBar(onTap: () -> Unit, modifier: Modifier)`: `Row`, frosted glass pill, placeholder "Search or ask anything...", left spark icon, right mic icon stub. Tapping anywhere → `onTap()`. `SearchOverlay(query, aiResponse, isAiResponding, followUpChips, onQueryChange, onSubmit, onDismiss)`: `Box(Modifier.fillMaxSize())` with `MapBackground` 90% alpha bg. Top: close button + search `TextField` with auto-focus using the complete pattern:
    ```kotlin
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.focusRequester(focusRequester),
        ...
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    ```
    If `isAiResponding || aiResponse.isNotEmpty()`: show AI response card with `StreamingTextContent` for response text (reuse existing composable). If `!aiResponse.isNotEmpty() && !isAiResponding`: show zero-state with suggested area chips ("Alfama, Lisbon", "Shibuya, Tokyo", "Brooklyn, NY"). After AI response: follow-up chips row using `FlowRow`. Dismiss: tap outside text field or back.

- [ ] Task 25: Create `FabMenu`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/FabMenu.kt` (NEW)
  - Action: `FabMenu(isExpanded: Boolean, onToggle: () -> Unit, onSavedPlaces: () -> Unit, onSettings: () -> Unit)`. When `isExpanded`: animate in 2 label+icon rows above FAB with `AnimatedVisibility(spring())`: "Saved Places" (bookmarks icon) and "Settings" (settings icon). Each label row: white text on frosted dark pill. Scrim: `Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.3f)))` behind the menu, clicking it calls `onToggle()`. FAB itself: 56dp circle, `MapSurfaceDark` bg, "+" / "×" icon transition via `AnimatedContent`.

- [ ] Task 26: Refactor `POIListView`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt`
  - Action: Add `activeVibe: Vibe` parameter. Top: add a horizontal `VibeChipStrip` — `LazyRow` of 6 `FilterChip`s, one per vibe, selected state = `vibe == activeVibe`, vibe color when selected. Each chip tap calls `onVibeSelected(vibe)` (new callback param). Filter `pois` to `pois.filter { it.vibe.equals(activeVibe.name, ignoreCase = true) }` before rendering the list. Replace existing POI row items with a `PoiListCard` composable (inline or separate file): icon (Material Symbol per `poi.type`), name + type, rating, liveStatus badge, insight snippet, "Directions" + "Save" chips. Tap → `onPoiClick(poi)`.

- [ ] Task 27: Add MapLibre Annotation Plugin dependency
  - File: `composeApp/build.gradle.kts`
  - Action: The existing MapLibre SDK is pinned at `11.11.0` (confirmed in `gradle/libs.versions.toml`). The compatible annotation plugin v9 for MapLibre 11.x is `3.0.0`. Add to `androidMain` dependencies block: `implementation("org.maplibre.gl:android-plugin-annotation-v9:3.0.0")`. Also add to `gradle/libs.versions.toml`: `maplibre-annotation = "3.0.0"` and `maplibre-plugin-annotation = { module = "org.maplibre.gl:android-plugin-annotation-v9", version.ref = "maplibre-annotation" }`. Reference via `libs.maplibre.plugin.annotation` in the kts file. Sync and confirm build succeeds before proceeding to Task 30.

**Group F — MapComposable Updates (depends on Group E)**

- [ ] Task 28: Update `MapComposable.kt` expect signature
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapComposable.kt`
  - Action: Add `activeVibe: Vibe` and `onMapRenderFailed: () -> Unit` parameters to the `expect fun MapComposable(...)` signature.

- [ ] Task 29: Update iOS MapComposable stub
  - File: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt` (confirmed path)
  - Action: Update the `actual fun MapComposable(...)` signature to add `activeVibe: Vibe` and `onMapRenderFailed: () -> Unit` params. The iOS actual is a placeholder — just add the params with no behavior change. **This task must be done before Task 28**, since changing the `expect` signature without updating the iOS actual will break the iOS build immediately.

- [ ] Task 30: Rewrite `MapComposable.android.kt`
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Action: (1) Change `MAP_STYLE_URL` to `streets-v2-dark`. (2) Add `activeVibe: Vibe` and `onMapRenderFailed: () -> Unit` params to `actual fun`. (3) Register `mapView.addOnDidFailLoadingMapListener { onMapRenderFailed() }` immediately in the `MapView(context).apply {}` block. (4) Replace `MarkerOptions` approach with `SymbolManager` (from annotation plugin). In the `setStyle` callback: initialize `SymbolManager(mapView, map, style)`. For each active-vibe filtered POI (where `poi.vibe.equals(activeVibe.name, ignoreCase = true)`): programmatically generate a 48dp bitmap (dark circle + Material Symbol icon in vibe accent color) via `Canvas(Bitmap.createBitmap(48, 48, ARGB_8888))`, register with `style.addImage(poi.type, bitmap)`, create symbol via `SymbolOptions().withLatLng(...).withIconImage(poi.type).withTextField(poi.name).withTextSize(10f).withTextColor("#FAFAFA").withTextOffset(arrayOf(0f, 1.5f))`. (5) Stagger symbol adds: `coroutineScope.launch { pois.forEachIndexed { i, poi -> delay(50L * i); createSymbol(poi) } }`. (6) Animate icon size 0.1 → 1.0 using `ValueAnimator` (300ms, FastOutSlowIn) after all symbols created. (7) Add glow zones as CircleLayer: cluster nearby-POI centroids (within 0.005 degree), add `CircleLayer` per cluster with `circleRadius(80f)`, `circleColor(activeVibe.accentColorHex)`, `circleOpacity(0.25f)`, `circleBlur(1.2f)`. Animate opacity 0.20↔0.35 via `ValueAnimator(REVERSE, 2000ms)`. (8) On `activeVibe` change (new `LaunchedEffect(activeVibe)`): remove previous symbols + glow layers, re-add for new vibe.

**Group G — Main Screen + App Shell (depends on everything)**

- [ ] Task 31: Rewrite `MapScreen.kt` (v3 Box layout)
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action: Complete replacement. Remove all `BottomSheetScaffold`, `SegmentedButton`, and POIDetailCard usage. New structure: `MapScreen(viewModel: MapViewModel = koinViewModel(), onNavigateToMaps: (Double, Double, String) -> Boolean)`. Collect `uiState`. Handle Loading state: `Box(fillMaxSize) { CircularProgressIndicator(Modifier.align(Center)) }`. Handle LocationFailed: `Box(fillMaxSize) { ContentNoteBanner(message) + Button("Retry") { viewModel.retry() } }`. Handle Ready state: full v3 `Box(fillMaxSize)` layout per composable hierarchy in Technical Decisions above. Wire all composable callbacks to ViewModel methods. Wire "Directions" chip to `onNavigateToMaps` (show snackbar if returns false). Show `SnackbarHost` at bottom.

- [ ] Task 32: Update `App.kt`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt`
  - Action: Remove `NavController`, `AppNavigation`, `BottomNavBar`, `mapPoiCount` state, and the entire `Scaffold` wrapper. New body: `KoinApplication(application = { platformConfig(); modules(appModule()) }) { AreaDiscoveryTheme { MapScreen(onNavigateToMaps = onNavigateToMaps) } }`. Remove unused imports.

**Group H — Tests**

- [ ] Task 33: Extend `MapViewModelTest`
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt`
  - Action: Add `FakeWeatherProvider` to `commonTest/fakes/` (implements `WeatherProvider`, returns `Result.success(WeatherState(72, 0, "Clear", "☀️"))` by default). Update `createViewModel()` helper to accept and pass `weatherProvider`. Add test sections:
    - `switchVibe_updatesActiveVibe()`: call `switchVibe(Vibe.HISTORY)`, assert `Ready.activeVibe == Vibe.HISTORY`.
    - `switchVibe_firesAnalytics()`: assert `vibe_switched` event tracked with `vibe = "HISTORY"`.
    - `onMapRenderFailed_setsListViewAndMapFailed()`: assert `showListView = true` and `mapRenderFailed = true`.
    - `openSearchOverlay_setsFlag()`: assert `isSearchOverlayOpen = true`.
    - `closeSearchOverlay_clearsFlag()`: open then close, assert false.
    - `toggleFab_flipsExpanded()`: call twice, verify false → true → false.
    - `portraitComplete_computesVibePoiCounts()`: give POIs with mixed vibes, assert `vibePoiCounts[Vibe.CHARACTER] == expectedCount`.
    - `weatherFetchedAfterLocationResolves()`: use success location provider + success FakeWeatherProvider, assert `Ready.weather != null`.
    - `weatherFailureDoesNotBreakReadyState()`: use FakeWeatherProvider returning `Result.failure()`, assert state is still `Ready` with `weather = null`.

- [ ] Task 34: Extend `GeminiResponseParserTest`
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiResponseParserTest.kt`
  - Action: Add a companion object constant `V3_POIS_RESPONSE` with a sample `---POIS---` section using the v3 JSON format (poi, vibe, insight, hours, liveStatus, rating, vibeInsights fields). Add tests:
    - `parseFullResponse_v3Pois_parsesVibe()`: assert `portrait.pois[0].vibe == "character"`.
    - `parseFullResponse_v3Pois_parsesInsight()`: assert insight non-empty.
    - `parseFullResponse_v3Pois_parsesVibeInsights()`: assert `vibeInsights` map is populated.
    - `parseFullResponse_v3Pois_parsesLiveStatus()`: assert `liveStatus` is `"open"` etc.
    - `parseFullResponse_v3Pois_fallsBackToNameFieldWhenPoiMissing()`: test with a POI using `"name"` key (not `"poi"`) to verify backward compat fallback.

### Acceptance Criteria

- [ ] AC 1: Given the app opens with GPS granted, when location resolves within 10 seconds, then the full-screen map fills the viewport with no bottom nav, no Scaffold padding, and the dark tile style is visible within 5 seconds.

- [ ] AC 2: Given the map is loaded and location is resolved, when the Character vibe auto-selects, then Character-colored glow zones appear behind POI clusters and a result count badge appears on the Character orb in the vibe rail — all without any user interaction.

- [ ] AC 3: Given the map is in Ready state, when the user taps a vibe orb (e.g. History), then: (a) only History-vibe POI pins are shown on the map, (b) glow zones recolor to History amber, (c) the active orb scales to 1.1x with full opacity, (d) inactive orbs scale to 0.9x at 40% opacity, (e) the count badge on the tapped orb shows the correct POI count.

- [ ] AC 4: Given POIs are loaded, when they appear on the map, then each pin shows a vibe-colored custom icon (not default MapLibre pin) with an always-visible label below it, and pins animate in with a spring pop-in (scale 0 → 1 with staggered 50ms per pin).

- [ ] AC 5: Given the map is showing POIs, when the user taps a POI pin, then a centered `ExpandablePoiCard` appears over the map without navigating away from the map screen.

- [ ] AC 6: Given an `ExpandablePoiCard` is open, when the user taps "More details", then the card expands in-place to show full description, hours (if available), all-vibe insights with colored dots, and a local tip — without opening a new screen.

- [ ] AC 7: Given an `ExpandablePoiCard` is open, when the user taps "Ask AI", then the search overlay opens with the field pre-populated with "Tell me more about [POI name]".

- [ ] AC 8: Given the search overlay is open and the user types a question (e.g. "Is it safe here?"), when they submit, then: (a) an AI response card appears with a streaming `StreamingTextContent` animation, (b) after streaming completes, 2 follow-up chips appear below the response.

- [ ] AC 9: Given the search overlay is open and the user types an area name (e.g. "Shibuya, Tokyo"), when they submit, then: (a) the overlay closes, (b) the top context bar updates with "Shibuya, Tokyo", (c) new POIs load and appear on the map for the searched area.

- [ ] AC 10: Given location has resolved, when the top context bar is visible, then it shows: city name + "First visit" tag + weather emoji + temperature in °F + local time. If weather is loading, show a shimmer placeholder for the weather section.

- [ ] AC 11: Given the FAB is tapped, when it expands, then: (a) a semi-transparent scrim appears, (b) "Saved Places" and "Settings" items animate in above the FAB, (c) tapping either item shows a snackbar "Coming soon", (d) tapping the scrim closes the menu.

- [ ] AC 12: Given the user taps the list mode icon, when list mode activates, then: (a) the map is hidden and a scrollable list of POI cards appears, (b) a vibe chip strip at the top shows the 6 vibes with the active vibe highlighted, (c) the same `ExpandablePoiCard` opens when a list item is tapped.

- [ ] AC 13: Given MapLibre fails to load the map style (simulated via `onMapRenderFailed()`), when the failure triggers, then: (a) list mode auto-activates, (b) an `AlertBanner` appears at the top of the list view, (c) the user can interact with the list normally.

- [ ] AC 14: Given a POI in the expandable card has `liveStatus = "busy"`, when the card is displayed, then the buzz meter shows 3 filled segments in the active vibe color.

- [ ] AC 15: Given the `GeminiResponseParser` receives a v3 POI JSON response, when parsed, then the resulting `POI` objects have non-empty `vibe`, `insight`, and `vibeInsights` map populated correctly.

- [ ] AC 16: Given the app is built with all old architecture deleted, when the project compiles and the smoke test runs (`connectedDebugAndroidTest`), then there are zero compilation errors and the app launches without crashing.

## Additional Context

### Dependencies

**External:**
- `org.maplibre.gl:android-plugin-annotation-v9` — custom POI pin icons via SymbolManager. Must be added to `composeApp/build.gradle.kts` `androidMain` dependencies if not already present. Verify version compatibility with existing MapLibre Android version.
- Open-Meteo API (`api.open-meteo.com`) — no API key, no registration. Uses existing Ktor `HttpClient`.
- Gemini API (`generativelanguage.googleapis.com`) — already configured. `streamChatResponse` uses same key + endpoint as `streamAreaPortrait`.

**Internal:**
- All Group A tasks must complete before Groups B, C, E.
- Group D (deletions) must complete before Group G (main screen rewrite) — App.kt cannot compile with old navigation imports.
- Group F (MapComposable) must complete before Group G (MapScreen calls MapComposable with new signature).
- Groups E + F must complete before Group G.

**Data Risk:**
- ~~`POI.kt` fields are `@Serializable`. If `AreaRepositoryImpl` stores POIs as individual DB columns (not JSON blobs), a Room migration is required for the 6 new fields.~~ **Resolved:** `AreaRepositoryImpl` confirmed to store POIs as a single `pois_json` TEXT blob (`json.encodeToString(pois)` / `json.decodeFromString()`). The `Json` instance uses `ignoreUnknownKeys = true`. No migration needed — old blobs decode safely with the new fields falling back to their declared defaults.

### Testing Strategy

**Unit tests (kotlin.test, commonMain, no device needed):**
- `MapViewModelTest` — extend with all new ViewModel methods (Task 33). Use `FakeWeatherProvider`. Follow existing `UnconfinedTestDispatcher` + `Dispatchers.setMain` pattern.
- `GeminiResponseParserTest` — extend with v3 POI field assertions (Task 34). Use companion object `const val` fixtures. No mocking needed.
- `MockAreaIntelligenceProviderTest` — update existing test to assert v3 POI fields are present in `mockPOIs`.

**Not unit-tested (visual/platform-specific):**
- Composables (`TopContextBar`, `VibeRail`, `ExpandablePoiCard`, `SearchOverlay`, `FabMenu`) — defer UI tests to post-design-stabilization per project testing strategy.
- `MapComposable.android.kt` — platform actual, tested via smoke test on device.
- `OpenMeteoWeatherProvider` — live HTTP call, not unit tested. Tested via manual device verification.

**Device tests:**
- Smoke test: `./gradlew :composeApp:connectedDebugAndroidTest` must pass after all tasks complete. The existing `AppLaunchSmokeTest` launches MainActivity and verifies no crash — this covers the app shell.
- Manual verification checklist: (1) map loads with dark tiles; (2) Character vibe auto-selects with glow zones; (3) pin tap opens POI card; (4) search overlay opens on bar tap; (5) AI question streams response; (6) area search updates top bar; (7) FAB menu opens/closes; (8) list mode shows filtered POI cards; (9) weather displays in top bar.

### Notes

**High-risk items:**
1. **MapLibre Annotation Plugin version conflict**: The annotation plugin v9 must match the MapLibre Android SDK major version. If there's a version mismatch, it will crash at runtime when `SymbolManager` is initialized. Verify version compatibility first (Task 27).
2. ~~**POI DB storage format**: If POIs are stored as columns in the Room database, adding 6 new fields without a migration will cause a `DatabaseCorruptException` on next app launch.~~ **Resolved** — JSON blob storage confirmed, no migration needed (see Task 3 notes).
3. **`streamChatResponse` SSE response format**: Gemini may stream the chat response differently from the portrait (e.g., shorter chunks, different line endings). Test with a real Gemini API call before assuming the `extractTextFromSseEvent` approach produces good streaming UX. If chunks are very large, the streaming effect may not be visible — tune the response length in the prompt.
4. **`GlowZone CircleLayer radius in pixels vs meters**: MapLibre `circleRadius` is in screen pixels, not geo meters. This means glow zones scale with zoom level in pixel-space, which may look too large on zoom-out or too small on zoom-in. Consider using `circle-radius` with a zoom-based data expression (`interpolate`, `["zoom"]`) to maintain geographic size across zoom levels.
5. **Material Symbols font in Compose Multiplatform**: `Icon` composable with `Icons.Default.*` won't have the Material Symbols icons (restaurant, nightlife, etc.) unless the Material Symbols font/icon set is included. Verify the icon names resolve in the current dependencies. If not, fallback to emoji or vector drawables for pin type icons.

**Known limitations (Phase 1a):**
- Camera does not move on area search — user must pan to see new pins.
- Follow-up chips are hardcoded keyword-based, not AI-generated.
- Photo placeholder is a gradient, not a real image.
- "First visit" tag is always shown — no real visit history tracking.
- Viewport-aware pin refresh on map pan is not implemented.
- iOS MapComposable is a stub only.

**Future considerations (Phase 1b+):**
- Real photo URLs: add `photoUrl: String?` to `POI` model, load with Coil in the photo area of `ExpandablePoiCard`.
- Viewport-aware pin refresh: add `onViewportChanged(bounds: LatLngBounds)` callback to `MapComposable`, fetch POIs for new viewport via a new use case.
- Permission-denied onboarding: open search overlay by default when location is null, show suggested areas.
- Visit detection: persist `areaName` + timestamp to DB, compute "First visit" vs "Been here N times".
- Offline cached tile indicator: warm glow on known cached areas.
