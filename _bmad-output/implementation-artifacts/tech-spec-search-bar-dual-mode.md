---
title: 'Search Bar Dual Mode'
slug: 'search-bar-dual-mode'
created: '2026-03-08'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Ktor', 'StateFlow', 'kotlinx.serialization']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/GeocodingSuggestion.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MapTilerGeocodingProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/GeocodingSearchBar.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
code_patterns:
  - 'StateFlow + MutableStateFlow — all state in MapUiState.Ready, VM mutates via copy()'
  - 'Ktor httpClient.get(url).bodyAsText() + Json.decodeFromString<T> — matches OpenMeteoWeatherProvider pattern'
  - 'Job-based cancellation (geocodingJob?.cancel()) — same as cameraIdleJob, searchJob patterns'
  - 'Koin single { } in DataModule, viewModel { } in UiModule with positional get()'
  - 'Result<T> return type for provider methods — matches LocationProvider, WeatherProvider'
  - 'MapFloatingUiDark.copy(alpha) for floating UI dark glass background'
  - 'BasicTextField with custom decoration for text input in commonMain'
test_patterns:
  - 'MapViewModelTest uses UnconfinedTestDispatcher + FakeLocationProvider/FakeAreaRepository'
  - 'New ViewModel methods tested with fake GeocodingProvider returning controlled Result<List>'
  - 'advanceTimeBy(300) to trigger debounce in query change tests'
---

# Tech-Spec: Search Bar Dual Mode

**Created:** 2026-03-08

## Overview

### Problem Statement

The "Refresh area" button (visible only after panning the map) is the only way to search a new location. Users cannot proactively navigate to a place by name. The button is hidden by default and discoverable only by panning, creating a passive UX.

### Solution

Replace the "Refresh area" floating button with a permanently visible dual-purpose search bar below the TopContextBar. When idle it offers a one-tap area refresh. When typed into it delivers MapTiler geocoding autocomplete (3–5 suggestions) — selecting a suggestion flies the map to that location and loads POIs. Empty submit refreshes the current area.

### Scope

**In Scope:**
- New `GeocodingSearchBar` composable with 4 visual states: idle, active+dropdown, place-selected, spinning
- MapTiler forward geocoding API call (`api.maptiler.com/geocoding/{query}.json`) via existing Ktor HttpClient
- Camera fly-to on suggestion selection (reuses `cameraMoveId++` pattern)
- Area portrait load on suggestion selection and on empty submit
- Top bar `areaName` update after suggestion selection
- Suggestion rows: pin icon, bolded matched text, detail address line, distance from GPS
- Both Android and iOS platforms

**Out of Scope:**
- Bottom bar, vibe rail, FAB, AI search bar — no changes
- Voice input / mic button
- Search history or recent places
- Debounce tuning beyond the 300ms default
- iOS-specific map camera (handled automatically via `cameraMoveId` state)

---

## Context for Development

### Codebase Patterns

1. **State mutations**: All state lives in `MapUiState.Ready` (data class). ViewModel mutates via `_uiState.value = current.copy(...)`. Never mutate state outside the VM.

2. **Ktor HTTP calls**: Match `OpenMeteoWeatherProvider` exactly — `httpClient.get(url).bodyAsText()` then `json.decodeFromString<T>()` with a local `Json { ignoreUnknownKeys = true }` instance. Wrap in `try/catch`, return `Result<T>`.

3. **Camera fly-to**: Increment `cameraMoveId` in state — the `LaunchedEffect(latitude, longitude, cameraMoveId)` in `MapComposable.android.kt` calls `map.animateCamera(...)`. Update `latitude` + `longitude` in the same `copy()` call.

4. **Job lifecycle**: `geocodingJob?.cancel()` before launching — same pattern as `cameraIdleJob`, `searchJob`, `returnToLocationJob`. Rethrow `CancellationException`.

5. **Pending coordinates**: `pendingLat`, `pendingLng`, `pendingAreaName` are VM private vars. Set these before calling `collectPortraitWithRetry` so existing `onCameraIdle` handling isn't disrupted.

6. **Koin DI**: `single { MapTilerGeocodingProvider(get()) }` in `DataModule`. Add one more `get()` in `UiModule` `viewModel {}` — current has 6 args, becomes 7.

7. **API key**: `BuildKonfig.MAPTILER_API_KEY` — already used in `MapComposable.android.kt` and `AreaRepositoryImpl`.

8. **Composable style**: Floating UI uses `MapFloatingUiDark.copy(alpha = 0.90f)` background. `labelMedium` typography. `RoundedCornerShape(50)` for pills, sharp corners (0.dp) for dropdown card edges.

9. **BasicTextField**: Use `androidx.compose.foundation.text.BasicTextField` in commonMain for editable input with custom decoration. Not `TextField` from Material3 (harder to control shape when focused).

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `ui/map/MapScreen.kt` | Remove lines 163–227 (two AnimatedVisibility blocks); add GeocodingSearchBar |
| `ui/map/MapViewModel.kt` | Add 4 new public methods + geocodingJob + geocodingProvider constructor param |
| `ui/map/MapUiState.kt` | Add 4 new fields to Ready |
| `ui/map/components/AISearchBar.kt` | Style reference — `MapFloatingUiDark`, `RoundedCornerShape(24.dp)` |
| `ui/map/components/TopContextBar.kt` | Positioning reference — sits at `padding(top = 8.dp)` above search bar |
| `data/remote/OpenMeteoWeatherProvider.kt` | Ktor HTTP pattern to replicate |
| `data/remote/HttpClientFactory.kt` | Shared `HttpClient` — inject via Koin, do NOT create a new one |
| `di/DataModule.kt` | Add `single { MapTilerGeocodingProvider(get()) }` |
| `di/UiModule.kt` | Add 7th `get()` to `viewModel { MapViewModel(...) }` |
| `androidMain/ui/map/MapComposable.android.kt` | Line 49: `MAPTILER_API_KEY` usage; Lines 98–148: cameraMoveId LaunchedEffect |

### Technical Decisions

- **No domain interface for geocoding**: `MapTilerGeocodingProvider` is used directly in the ViewModel (not behind an interface). Geocoding is MapTiler-specific and not expected to swap. Keeps scope minimal.
- **Distance computed in VM**: Haversine from `state.gpsLatitude/gpsLongitude` to suggestion coordinates. Stored as `distanceKm: Double?` in `GeocodingSuggestion` (null if GPS is 0,0).
- **Area name for portrait fetch**: Use `suggestion.name` (MapTiler `text` field = short place name). Gemini handles both area names and POI names gracefully.
- **300ms debounce**: Cancel previous `geocodingJob` on each keystroke; launch new coroutine with `delay(300)` before HTTP call.
- **Limit=5**: MapTiler query param `&limit=5` matches the "3–5 suggestions" spec.
- **`showSearchThisArea` state field**: Left in place (still set by `onCameraIdle`), but no longer rendered in MapScreen. Clean-up deferred.

---

## Implementation Plan

### Tasks

Tasks are ordered by dependency — lowest level first.

---

- [x] **T1 — Create `GeocodingSuggestion` domain model**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/GeocodingSuggestion.kt` *(new file)*
  - Action: Create new data class:
```kotlin
package com.areadiscovery.domain.model

data class GeocodingSuggestion(
    val name: String,           // MapTiler "text" — short place name, e.g. "Belém Tower"
    val fullAddress: String,    // MapTiler "place_name" — full address line for detail row
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double?,    // Haversine from GPS; null if GPS unavailable
)
```

---

- [x] **T2 — Create `MapTilerGeocodingProvider`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MapTilerGeocodingProvider.kt` *(new file)*
  - Action: Create provider with internal serializable response DTOs and `search()` method:
  - Internal `@Serializable` data classes for the MapTiler geocoding response:
  ```kotlin
  @Serializable
  private data class GeocodingResponse(val features: List<GeocodingFeature>)

  @Serializable
  private data class GeocodingFeature(
      val text: String,
      @SerialName("place_name") val placeName: String,
      val center: List<Double>,   // [longitude, latitude]
  )
  ```
- Class:
  ```kotlin
  class MapTilerGeocodingProvider(private val httpClient: HttpClient) {
      private val json = Json { ignoreUnknownKeys = true }

      suspend fun search(query: String, limit: Int = 5): Result<List<GeocodingSuggestion>> {
          return try {
              val encoded = query.encodeURLPathPart()  // import io.ktor.http.encodeURLPathPart
              val url = "https://api.maptiler.com/geocoding/$encoded.json" +
                        "?key=${BuildKonfig.MAPTILER_API_KEY}&limit=$limit"
              val body = httpClient.get(url).bodyAsText()
              val response = json.decodeFromString<GeocodingResponse>(body)
              Result.success(response.features.map { f ->
                  GeocodingSuggestion(
                      name = f.text,
                      fullAddress = f.placeName,
                      latitude = f.center[1],
                      longitude = f.center[0],
                      distanceKm = null,  // caller injects distance
                  )
              })
          } catch (e: CancellationException) {
              throw e   // F3 fix: must rethrow for structured concurrency — debounce cancel must propagate
          } catch (e: Exception) {
              Result.failure(e)
          }
      }
  }
  ```
- Note: distance is `null` here; ViewModel enriches it with haversine after the call.
- Imports needed: `com.areadiscovery.BuildKonfig`, `com.areadiscovery.domain.model.GeocodingSuggestion`, `io.ktor.client.HttpClient`, `io.ktor.client.request.get`, `io.ktor.client.statement.bodyAsText`, `io.ktor.http.encodeURLPathPart`, `kotlinx.serialization.SerialName`, `kotlinx.serialization.Serializable`, `kotlinx.serialization.json.Json`, `kotlin.coroutines.cancellation.CancellationException`

---

- [x] **T3 — Add geocoding fields to `MapUiState.Ready`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt`
  - Action: Add 5 fields to `Ready` data class after `cameraMoveId`:
```kotlin
val geocodingQuery: String = "",
val geocodingSuggestions: List<GeocodingSuggestion> = emptyList(),
val isGeocodingLoading: Boolean = false,
val geocodingSelectedPlace: String? = null,
val isGeocodingInitiatedSearch: Boolean = false,  // F5 fix: true only when geocoding/bar triggered the search, not initial app load
```
  - Notes: Add import `com.areadiscovery.domain.model.GeocodingSuggestion`. `isGeocodingInitiatedSearch` guards the cancel ✕ in the spinner — it is `false` on initial load so cancel is hidden, `true` only after `onGeocodingSuggestionSelected` or `onGeocodingSubmitEmpty`.

---

- [x] **T4 — Register `MapTilerGeocodingProvider` in `DataModule`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
  - Action: Add after the `single<WeatherProvider>` line:
```kotlin
single { MapTilerGeocodingProvider(get()) }
```
  - Notes: Add import `com.areadiscovery.data.remote.MapTilerGeocodingProvider`

---

- [x] **T5 — Add geocoding to `MapViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: Four sub-changes — 5a through 5d:

**5a — Constructor param** (add as the 7th and last param, after `aiProvider: AreaIntelligenceProvider? = null`):
```kotlin
// Full constructor after change:
class MapViewModel(
    private val locationProvider: LocationProvider,
    private val getAreaPortrait: GetAreaPortraitUseCase,
    private val areaContextFactory: AreaContextFactory,
    private val analyticsTracker: AnalyticsTracker,
    private val weatherProvider: WeatherProvider,
    private val aiProvider: AreaIntelligenceProvider? = null,
    private val geocodingProvider: MapTilerGeocodingProvider,   // ← NEW
) : ViewModel()
```
- Notes: `aiProvider` stays at position 6 (Koin resolves it via `get()` — `AreaIntelligenceProvider` is registered as `single<AreaIntelligenceProvider>` in DataModule). `geocodingProvider` becomes position 7. The 7th `get()` in UiModule resolves `MapTilerGeocodingProvider` by type.

**5b — New job field** (after `returnToLocationJob`):
```kotlin
private var geocodingJob: Job? = null
```

**5c — New methods** (add after `toggleFab()`):

```kotlin
fun onGeocodingQueryChanged(query: String) {
    val current = _uiState.value as? MapUiState.Ready ?: return
    geocodingJob?.cancel()
    if (query.isBlank()) {
        _uiState.value = current.copy(
            geocodingQuery = "",
            geocodingSuggestions = emptyList(),
            isGeocodingLoading = false,
        )
        return
    }
    _uiState.value = current.copy(
        geocodingQuery = query,
        isGeocodingLoading = true,
    )
    geocodingJob = viewModelScope.launch {
        delay(300)
        val state = _uiState.value as? MapUiState.Ready ?: return@launch
        val result = geocodingProvider.search(query)
        val updated = _uiState.value as? MapUiState.Ready ?: return@launch
        if (result.isFailure) {
            AppLogger.e(result.exceptionOrNull()) { "Geocoding search failed" }
            _uiState.value = updated.copy(isGeocodingLoading = false)
            return@launch
        }
        val raw = result.getOrThrow()
        val withDistance = raw.map { s ->
            if (updated.gpsLatitude == 0.0 && updated.gpsLongitude == 0.0) s
            else s.copy(distanceKm = haversineKm(updated.gpsLatitude, updated.gpsLongitude, s.latitude, s.longitude))
        }
        _uiState.value = updated.copy(
            geocodingSuggestions = withDistance,
            isGeocodingLoading = false,
        )
    }
}

fun onGeocodingSuggestionSelected(suggestion: GeocodingSuggestion) {
    val current = _uiState.value as? MapUiState.Ready ?: return
    cameraIdleJob?.cancel()
    geocodingJob?.cancel()
    searchJob?.cancel()
    returnToLocationJob?.cancel()   // F1 fix: prevent GPS flight from overwriting geocoding result
    pendingLat = suggestion.latitude
    pendingLng = suggestion.longitude
    pendingAreaName = suggestion.name
    _uiState.value = current.copy(
        geocodingQuery = "",
        geocodingSuggestions = emptyList(),
        isGeocodingLoading = false,
        geocodingSelectedPlace = suggestion.name,
        isGeocodingInitiatedSearch = true,   // F5 fix: enables cancel ✕ in spinner
        latitude = suggestion.latitude,
        longitude = suggestion.longitude,
        cameraMoveId = current.cameraMoveId + 1,
        isSearchingArea = true,
        showSearchThisArea = false,
        showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, current),
        vibePoiCounts = emptyMap(),
        pois = emptyList(),
        activeVibe = null,
    )
    searchJob = viewModelScope.launch {
        try {
            collectPortraitWithRetry(
                areaName = suggestion.name,
                onComplete = { pois, _ ->
                    val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                    val counts = computeVibePoiCounts(pois)
                    _uiState.value = state.copy(
                        areaName = suggestion.name,
                        pois = pois,
                        vibePoiCounts = counts,
                        activeVibe = null,
                        isSearchingArea = false,
                        showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, state),
                    )
                },
                onError = { e ->
                    AppLogger.e(e) { "Geocoding selection: portrait fetch failed" }
                    val s = _uiState.value as? MapUiState.Ready
                    if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                    _errorEvents.tryEmit("Couldn't load area info. Try again.")
                },
            )
        } catch (e: CancellationException) {
            val s = _uiState.value as? MapUiState.Ready
            if (s != null) _uiState.value = s.copy(isSearchingArea = false)
            throw e
        } catch (e: Exception) {
            AppLogger.e(e) { "Geocoding selection: unexpected error" }
            val s = _uiState.value as? MapUiState.Ready
            if (s != null) _uiState.value = s.copy(isSearchingArea = false)
            _errorEvents.tryEmit("Couldn't load area info. Try again.")
        }
    }
}

fun onGeocodingSubmitEmpty() {
    val current = _uiState.value as? MapUiState.Ready ?: return
    cameraIdleJob?.cancel()
    geocodingJob?.cancel()
    val areaName = pendingAreaName.ifBlank { current.areaName }
    val lat = if (pendingLat != 0.0) pendingLat else current.latitude
    val lng = if (pendingLng != 0.0) pendingLng else current.longitude
    pendingAreaName = areaName
    pendingLat = lat
    pendingLng = lng
    _uiState.value = current.copy(
        isSearchingArea = true,
        isGeocodingInitiatedSearch = true,   // F5 fix: enables cancel ✕ in spinner
        showSearchThisArea = false,
        showMyLocation = false,
        vibePoiCounts = emptyMap(),
        pois = emptyList(),
        activeVibe = null,
    )
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
        try {
            collectPortraitWithRetry(
                areaName = areaName,
                onComplete = { pois, _ ->
                    val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                    val counts = computeVibePoiCounts(pois)
                    _uiState.value = state.copy(
                        areaName = areaName,
                        latitude = lat,
                        longitude = lng,
                        pois = pois,
                        vibePoiCounts = counts,
                        activeVibe = null,
                        isSearchingArea = false,
                        showMyLocation = isAwayFromGps(lat, lng, state),
                    )
                },
                onError = { e ->
                    AppLogger.e(e) { "Empty submit: portrait fetch failed" }
                    val s = _uiState.value as? MapUiState.Ready
                    if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                    _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
                },
            )
        } catch (e: CancellationException) {
            val s = _uiState.value as? MapUiState.Ready
            if (s != null) _uiState.value = s.copy(isSearchingArea = false)
            throw e
        } catch (e: Exception) {
            AppLogger.e(e) { "Empty submit: unexpected error" }
            val s = _uiState.value as? MapUiState.Ready
            if (s != null) _uiState.value = s.copy(isSearchingArea = false)
            _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
        }
    }
}

fun onGeocodingCleared() {
    val current = _uiState.value as? MapUiState.Ready ?: return
    geocodingJob?.cancel()
    _uiState.value = current.copy(
        geocodingQuery = "",
        geocodingSuggestions = emptyList(),
        isGeocodingLoading = false,
        geocodingSelectedPlace = null,
    )
}

fun onGeocodingCancelLoad() {
    val current = _uiState.value as? MapUiState.Ready ?: return
    searchJob?.cancel()
    _uiState.value = current.copy(
        isSearchingArea = false,
        isGeocodingInitiatedSearch = false,
        geocodingQuery = "",
        geocodingSuggestions = emptyList(),
        geocodingSelectedPlace = null,
    )
}
```

**5d — Add private haversine helper** (after `isAwayFromGps`):
```kotlin
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
    return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
```

  - Notes: Add imports `com.areadiscovery.data.remote.MapTilerGeocodingProvider`, `com.areadiscovery.domain.model.GeocodingSuggestion`

---

- [x] **T6 — Update `UiModule` to inject `geocodingProvider`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
  - Action: Change 6-arg `viewModel {}` to 7-arg:
```kotlin
// Before
viewModel { MapViewModel(get(), get(), get(), get(), get(), get()) }
// After
viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get()) }
```
  - Notes: The 7th `get()` resolves `MapTilerGeocodingProvider` (registered in T4). Koin resolves by type, order matches constructor.

---

- [x] **T7 — Create `GeocodingSearchBar` composable**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/GeocodingSearchBar.kt` *(new file)*
  - Action: Implement 4-state composable (idle / active+dropdown / selected / spinning). Full detail below.

Signature:
```kotlin
@Composable
fun GeocodingSearchBar(
    query: String,
    suggestions: List<GeocodingSuggestion>,
    isGeocodingLoading: Boolean,
    selectedPlace: String?,
    isSearchingArea: Boolean,
    isGeocodingInitiatedSearch: Boolean,   // F5 fix: hides cancel ✕ during initial app load
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (GeocodingSuggestion) -> Unit,
    onSubmitEmpty: () -> Unit,
    onClear: () -> Unit,
    onCancelLoad: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**State derivation inside composable:**
```
spinning  = isSearchingArea
showCancel = isSearchingArea && isGeocodingInitiatedSearch   // F5 fix
selected  = !spinning && selectedPlace != null
active    = !spinning && selectedPlace == null && (query.isNotBlank() || suggestions.isNotEmpty())
idle      = !spinning && selectedPlace == null && query.isBlank() && suggestions.isEmpty()
```

**Visual structure (4 states):**

*Idle state:*
```
Box (fillMaxWidth, padding horizontal 16.dp)
  Surface (shape=RoundedCornerShape(50), color=MapFloatingUiDark.copy(0.90f))
    clickable { onSubmitEmpty() }
    Row (padding h=16, v=10, verticalAlignment=Center)
      Icon(Icons.Default.Refresh, size=16.dp, tint=White.copy(0.7f))
      Spacer(8.dp)
      Text("Search a place or refresh area", labelMedium, White.copy(0.5f))
```

*Active state (query.isNotBlank() or suggestions non-empty):*
```
Column (fillMaxWidth, padding horizontal 16.dp)
  Surface (shape=RoundedCornerShape(topStart=20, topEnd=20, bottomStart=0, bottomEnd=0), MapFloatingUiDark.copy(0.95f))
    Row (padding h=14, v=10)
      Icon(Icons.Default.Search, 16.dp, White.copy(0.6f))
      Spacer(8.dp)
      BasicTextField(
        value=query, onValueChange=onQueryChanged,
        textStyle=labelMedium + color=White,
        modifier=Modifier.weight(1f),
        singleLine=true,
        decorationBox = { innerTextField ->
          Box {
            if (query.isEmpty()) Text(placeholder, White.copy(0.4f))
            innerTextField()
          }
        }
      )
      if (query.isNotBlank() || isGeocodingLoading) {
        Spacer(8.dp)
        Icon(Icons.Default.Close, 18.dp, White.copy(0.6f), clickable { onClear() })
      }
  // Dropdown — only when suggestions non-empty
  if (suggestions.isNotEmpty()) {
    Surface (shape=RoundedCornerShape(bottomStart=20, bottomEnd=20), MapFloatingUiDark.copy(0.95f))
      Column {
        suggestions.forEachIndexed { i, s ->
          if (i > 0) HorizontalDivider(color=White.copy(0.06f), thickness=0.5.dp)  // F9: Divider deprecated in M3
          SuggestionRow(s, query, onSuggestionSelected)
        }
      }
  }
```

*SuggestionRow (private composable):*
```kotlin
// F10 fix: query param added so bold-match annotation can be built
@Composable
private fun SuggestionRow(
    suggestion: GeocodingSuggestion,
    query: String,
    onSelected: (GeocodingSuggestion) -> Unit,
)
```
```
Row (padding h=14, v=10, clickable { onSelected(suggestion) })
  Icon(Icons.Default.Place, 14.dp, MaterialTheme.colorScheme.error)
  Spacer(10.dp)
  Column(modifier=Modifier.weight(1f))
    // Bold the query-matching substring using buildAnnotatedString + SpanStyle(fontWeight=Bold)
    Text(
      text = buildAnnotatedString {
        val lower = suggestion.name.lowercase()
        val q = query.trim().lowercase()
        val idx = lower.indexOf(q)
        if (idx >= 0) {
          append(suggestion.name.substring(0, idx))
          withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = White)) {
            append(suggestion.name.substring(idx, idx + q.length))
          }
          append(suggestion.name.substring(idx + q.length))
        } else {
          append(suggestion.name)
        }
      },
      style = labelMedium, color = White, maxLines = 1, overflow = Ellipsis
    )
    Text(suggestion.fullAddress, labelSmall, White.copy(0.5f), maxLines=1, ellipsis)
  if (suggestion.distanceKm != null)
    Text(formatDistance(suggestion.distanceKm), labelSmall, White.copy(0.4f))
```

Distance formatting helper (private, KMP-safe):
```kotlin
// F8 fix: String.format() is JVM-only — use manual rounding for KMP commonMain
private fun formatDistance(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).toInt()} m"
    else -> {
        val rounded = kotlin.math.round(km * 10) / 10.0
        "${rounded} km"
    }
}
```

*Selected state (`selectedPlace != null && !isSearchingArea`):*
```
Surface (RoundedCornerShape(50), MapFloatingUiDark.copy(0.90f), border=BorderStroke(1.dp, Purple.copy(0.3f)))
  Row (padding h=16, v=10)
    Icon(Icons.Default.Place, 14.dp, tint=MaterialTheme.colorScheme.primary)
    Spacer(8.dp)
    Text(selectedPlace, labelMedium, White, weight(1f), maxLines=1, ellipsis)
    Spacer(8.dp)
    Icon(Icons.Default.Close, 18.dp, White.copy(0.6f), clickable { onClear() })
```

*Spinning state (`isSearchingArea`):*
```
Surface (RoundedCornerShape(50), MapFloatingUiDark.copy(0.90f))
  Row (padding h=16, v=10)
    CircularProgressIndicator(size=14.dp, color=White, strokeWidth=2.dp)
    Spacer(8.dp)
    Text("Refreshing area…", labelMedium, White.copy(0.7f), weight(1f))
    // F5 fix: only show cancel if geocoding/bar initiated the search (not initial app load)
    if (showCancel) {
      Spacer(8.dp)
      Icon(Icons.Default.Close, 18.dp, White.copy(0.6f), clickable { onCancelLoad() })
    }
```

Imports needed: `BasicTextField`, `Icons.Default.{Refresh, Search, Close, Place}`, `CircularProgressIndicator`, `Surface`, `Row`, `Column`, `Box`, `Spacer`, `Icon`, `Text`, `Divider`, `RoundedCornerShape`, `BorderStroke`, `MapFloatingUiDark`, `MaterialTheme`, `GeocodingSuggestion`, `Modifier`, `Alignment`, `fillMaxWidth`, `padding`, `weight`, `size`, `clickable`

---

- [x] **T8 — Update `MapScreen` to use `GeocodingSearchBar`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action — four sub-steps:

  **8a — Remove** the two `AnimatedVisibility` blocks at lines 163–227 (the "Search this area" button and the "Loading area…" indicator).

  **8b — Add** the `GeocodingSearchBar` in `ReadyContent`'s `Box`, positioned after `TopContextBar`:
```kotlin
// Geocoding search bar (always visible, replaces Refresh Area button)
GeocodingSearchBar(
    query = state.geocodingQuery,
    suggestions = state.geocodingSuggestions,
    isGeocodingLoading = state.isGeocodingLoading,
    selectedPlace = state.geocodingSelectedPlace,
    isSearchingArea = state.isSearchingArea,
    isGeocodingInitiatedSearch = state.isGeocodingInitiatedSearch,
    onQueryChanged = { viewModel.onGeocodingQueryChanged(it) },
    onSuggestionSelected = { viewModel.onGeocodingSuggestionSelected(it) },
    onSubmitEmpty = { viewModel.onGeocodingSubmitEmpty() },
    onClear = { viewModel.onGeocodingCleared() },
    onCancelLoad = { viewModel.onGeocodingCancelLoad() },
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 56.dp)
        .fillMaxWidth(),
)
```

  **8c — Add** import: `com.areadiscovery.ui.map.components.GeocodingSearchBar`

  **8d — Remove** imports that become unused: `Icons.Default.Search` (verify no other usage). `Surface` stays (used by MyLocation button). `Button` — check if used elsewhere in the file; remove if not.

---

### Acceptance Criteria

- [x] **AC-1 — Idle state visible on load**
  - Given the app is open and the map is in Ready state with no query and no selected place
  - When the screen renders
  - Then a pill-shaped search bar is visible below the TopContextBar with placeholder "Search a place or refresh area", ↻ icon on the left, and no ✕

- [x] **AC-2 — Idle tap triggers area refresh**
  - Given the search bar is in idle state
  - When the user taps it without typing any text
  - Then `isSearchingArea` becomes true, the bar shows `CircularProgressIndicator` + "Refreshing area…" + ✕, and POIs reload for the current area

- [x] **AC-3 — Typing transitions to active state with dropdown**
  - Given the search bar is idle
  - When the user taps and types at least one character
  - Then the top corners become flat (square), ↻ is hidden, ✕ appears, and after 300ms debounce a MapTiler geocoding call fires and suggestions appear below

- [x] **AC-4 — Suggestions appear with correct layout**
  - Given the user has typed a query and MapTiler returns results
  - When the dropdown renders
  - Then 3–5 rows appear, each showing: pin icon (red), place name (short `text` field), full address detail line (`place_name`), and distance from GPS in "X.X km" or "XXX m" format

- [x] **AC-5 — Selecting a suggestion flies map and loads POIs**
  - Given suggestions are visible
  - When the user taps a suggestion row
  - Then the dropdown closes, the bar shows selected state with the place name + ✕, the map camera animates to the suggestion's coordinates, `areaName` in the TopContextBar updates to the suggestion's name, and POIs begin loading (`isSearchingArea = true`)

- [x] **AC-6 — Clear (✕) returns bar to idle**
  - Given the bar is in selected state or active typing state
  - When the user taps ✕
  - Then `geocodingQuery`, `geocodingSuggestions`, and `geocodingSelectedPlace` are cleared, bar returns to idle; map camera position is NOT changed

- [x] **AC-7 — Empty submit uses panned camera position**
  - Given the user has panned the map (triggering `onCameraIdle` + pending coords set) and has not typed anything
  - When the user taps the idle bar
  - Then the area at the panned camera position loads (uses `pendingAreaName`/`pendingLat`/`pendingLng`), same behaviour as the old "Refresh area" button

- [x] **AC-8 — Spinner shown during area load regardless of prior state**
  - Given a suggestion was selected OR empty submit was triggered
  - While `isSearchingArea = true`
  - Then the bar shows `CircularProgressIndicator` (14dp, `strokeWidth=2.dp`) + "Refreshing area…" + a ✕ cancel button on the right

- [x] **AC-9 — Cancel ✕ in spinner aborts load and returns to idle**
  - Given the bar is in spinning state (`isSearchingArea = true`)
  - When the user taps the ✕ in the spinner bar
  - Then `searchJob` is cancelled, `isSearchingArea` becomes false, bar returns to idle with no query or selected place

- [x] **AC-10 — Geocoding API failure is silent**
  - Given the MapTiler geocoding call fails (network error, 401, timeout, etc.)
  - When the error is caught in `onGeocodingQueryChanged`
  - Then `isGeocodingLoading` resets to false, suggestions remain empty, no crash, no snackbar shown to user

- [x] **AC-11 — Old Refresh button is gone**
  - Given the app is open and the user pans the map
  - When `onCameraIdle` fires
  - Then NO "Search this area" / "Refresh area" floating button appears (the two `AnimatedVisibility` blocks are removed from `MapScreen`)

- [x] **AC-12 — Bottom bar, vibe rail, FAB unchanged**
  - Given any state of the geocoding search bar
  - When the screen renders
  - Then the vibe rail, FAB menu, AI search bar, MyLocation button, and Map/List toggle are visually and functionally unchanged

---

---

## Additional Context

### Dependencies

- **MapTiler Geocoding API**: `https://api.maptiler.com/geocoding/{query}.json?key={MAPTILER_API_KEY}&limit=5`
  - Returns `{ "features": [{ "text": "...", "place_name": "...", "center": [lng, lat] }] }`
  - Free tier: 100k requests/month. No additional SDK needed — plain Ktor GET.
- **Ktor `encodeURLPathPart`**: from `io.ktor.http` — already a transitive dependency. Encodes spaces/special chars in the query path segment.
- **`BuildKonfig.MAPTILER_API_KEY`**: Already in project (`MapComposable.android.kt` line 49). No new secrets config needed.
- **No new Gradle dependencies** required.

### Testing Strategy

New unit tests in `MapViewModelTest.kt` using a fake `MapTilerGeocodingProvider`:

1. **`onGeocodingQueryChanged_blank_clearsSuggestions`**: Query set to blank → state has empty suggestions, isGeocodingLoading=false.
2. **`onGeocodingQueryChanged_withQuery_setsLoadingThenSuggestions`**: Advance time past 300ms → isGeocodingLoading=true then false, suggestions populated.
3. **`onGeocodingQueryChanged_debounce_cancelsQuickKeystrokes`**: Two calls within 300ms → only one HTTP call fired (second overwrites first job).
4. **`onGeocodingSuggestionSelected_updatesCameraAndLoadsPortrait`**: Selecting a suggestion → cameraMoveId incremented, latitude/longitude updated, isSearchingArea=true, then pois populated and isSearchingArea=false.
5. **`onGeocodingSubmitEmpty_withNoPending_usesCurrentAreaName`**: pendingAreaName blank → uses `current.areaName`, isSearchingArea=true then resolves.
6. **`onGeocodingCleared_resetsAllGeocodingState`**: After selecting a place → cleared returns to idle.
7. **`onGeocodingQueryChanged_apiFailure_isGeocodingLoadingResetsFalse`**: Provider returns failure → isGeocodingLoading=false, suggestions empty, no crash.

Pattern: create `FakeMapTilerGeocodingProvider` in `commonTest/fakes/` returning controlled `Result<List<GeocodingSuggestion>>`.

### Notes

- **`showSearchThisArea` state field**: Still set by `onCameraIdle` but no longer rendered. Not a bug — just dead state for now. Can be cleaned up in a future spec.
- **iOS camera**: The `cameraMoveId` increment works for iOS too — `MapComposable.ios.kt` (if present) also listens to `cameraMoveId`. No platform-specific camera work needed.
- **Keyboard dismissal**: When a suggestion is selected or the ✕ is tapped, focus should clear naturally since `BasicTextField` is no longer the composition target. If keyboard persists, call `LocalFocusManager.current.clearFocus()` inside the callback.
- **`Icons.Default.Refresh`**: Available from `androidx.compose.material.icons.filled` (same icon set used elsewhere). If not available, use a simple Unicode ↻ in a `Text` composable instead.
- **Gemini portrait fetch latency (15+ seconds)**: The area portrait load (Gemini AI call) consistently takes 15+ seconds. This is the root cause of the long spinner duration users experience after selecting a geocoded suggestion or tapping the idle bar. The cancel ✕ button added in this spec mitigates the UX impact. **Root fix is deferred to Epic 4a (AI Chat)** — options to explore there: streaming partial POI results as they arrive (already partially implemented via `BucketUpdate` flow), prompt caching, or a dedicated fast-path "area summary" call separate from full POI enrichment. Tag: `latency-gemini-portrait-fetch`.
- **F6/`showMyLocation` not restored on error (pre-existing bug)**: `onGeocodingSubmitEmpty` sets `showMyLocation = false` on start. The `onError` handler resets `isSearchingArea` but does not restore `showMyLocation`. This mirrors the same gap in the existing `onSearchThisAreaTapped`. Dev should add `showMyLocation = isAwayFromGps(lat, lng, s)` to the `onError` copy in both `onGeocodingSubmitEmpty` and `onGeocodingSuggestionSelected`. Low-risk (user can recover by panning), but worth fixing.
- **F11/`geocodingSelectedPlace` not cleared on `returnToCurrentLocation`**: If user selects a geocoded place then taps "My Location", the bar stays in selected state showing the old place name. Fix: in `returnToCurrentLocation()`, add `geocodingSelectedPlace = null, isGeocodingInitiatedSearch = false` to the two `copy()` calls that update state (isSameArea and !isSameArea branches). This is a one-line fix per branch that the dev agent should apply.
- **F12/URL slash encoding**: Queries containing `/` (e.g. "Mission District, San Francisco") will encode the slash as `%2F`. Strip or replace `/` with a space before calling `encodeURLPathPart()`: `val sanitized = query.replace("/", " ").trim()`. Use `sanitized` for encoding.
- **F13/`padding(top = 56.dp)` magic number**: This value = TopContextBar height (~40dp) + 8dp top padding + ~8dp gap. Add a comment: `// 56dp = TopContextBar height + top inset padding`. Update if TopContextBar layout changes.
- **F14/`FakeMapTilerGeocodingProvider` needs call counter**: For the debounce test, add `var callCount: Int = 0` to the fake and increment in `search()`. Assert `callCount == 1` after two rapid calls to verify debounce cancels the first job.
