---
title: 'POI Coordinate Validation — Bounding Box + Re-geocode'
slug: 'poi-coordinate-validation'
created: '2026-03-08'
status: 'reverted — approach failed'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (KMP)'
  - 'Koin DI'
  - 'Ktor HttpClient'
  - 'SQLDelight (area_poi_cache)'
  - 'kotlinx.serialization'
  - 'kotlin.test + runTest{} for suspend functions'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/GeocodingProvider.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MapTilerGeocodingProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/AreaContext.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/service/PoiCoordinateValidator.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakePoiCoordinateValidator.kt (NEW)'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/domain/service/PoiCoordinateValidatorTest.kt (NEW)'
code_patterns:
  - 'domain interface in domain/provider; data impl in data/remote'
  - 'open class domain service, constructor takes domain interface'
  - 'POI.copy() for immutable coord replacement'
  - 'context.copy() to thread centroid (Double?) into AreaContext'
  - 'validate → enrichPoisWithImages → writePoisToCache ordering'
  - 'FakeX pattern: override suspend fun, expose callCount/lastX'
test_patterns:
  - 'kotlin.test @Test, backtick names'
  - 'runTest{} from kotlinx.coroutines.test for all suspend fun tests'
  - 'FakeMapTilerGeocodingProvider reused as GeocodingProvider in validator tests'
---

# Tech-Spec: POI Coordinate Validation — Bounding Box + Re-geocode

**Created:** 2026-03-08

## Overview

### Problem Statement

Gemini halluccinates slightly-offset lat/lng values for POIs in coastal areas, causing map pins to appear in the ocean instead of on land. Reproduced in Cayucos, CA. There is no coordinate validation step after `PortraitComplete` emits — Gemini's raw coords go straight to the map and the DB cache.

### Solution

Introduce a `PoiCoordinateValidator` domain service injected into `AreaRepositoryImpl`. Before POIs are passed to `enrichPoisWithImages()` and `writePoisToCache()`, the validator checks each POI's lat/lng against a ±0.15° bounding box around the area centroid (threaded via `AreaContext`). Outliers are re-geocoded serially by name via a new `GeocodingProvider` domain interface; verified coords replace Gemini's (with a secondary bbox check on the re-geocoded result to catch name-collision false positives). POIs that remain unresolvable are excluded. The corrected list is then enriched and written to cache — cold-start reloads also serve validated data.

### Scope

**In Scope:**
- New `GeocodingProvider` domain interface in `domain/provider`
- `MapTilerGeocodingProvider` implements `GeocodingProvider`
- Add `centroidLat: Double? = null` / `centroidLng: Double? = null` to `AreaContext`
- New `PoiCoordinateValidator` domain service depending on `GeocodingProvider` interface
- Fixed ±0.15° rectangular bounding box; secondary bbox check on re-geocoded coords
- MapTiler re-geocode by POI name for outliers, serial, limit=1, take first result that passes bbox
- Exclude POIs with null coords, unresolvable re-geocode, or re-geocoded result still outside bbox
- No-op guard when centroid is null (pass all through)
- Validate before `enrichPoisWithImages()` in all fresh-AI-data paths
- Cache write-back of corrected coords via existing `writePoisToCache()`
- Unit tests for validator + fake for downstream test use

**Out of Scope:**
- Land/water polygon snapping
- Dynamic bounding box sizing
- Changing the Gemini prompt
- iOS-specific map changes
- Changes to `AreaContextFactory.create()` signature (ViewModel sets centroid via `.copy()`)

## Context for Development

### Codebase Patterns

- **KMP**: All new code is pure Kotlin in `commonMain`. No Android imports.
- **Clean Architecture layer rule**: Domain services (`domain/service`) must NOT import from `data.remote`. `PoiCoordinateValidator` depends on the `GeocodingProvider` interface (`domain/provider`). `MapTilerGeocodingProvider` (`data/remote`) implements that interface. This is the same pattern as `AreaIntelligenceProvider` / `GeminiAreaIntelligenceProvider`.
- **Domain service pattern**: `open class` with suspend fun, constructor-injected domain interface. See `AreaContextFactory`. `open class` required so tests can subclass.
- **Koin**: `PoiCoordinateValidator` registered as `single {}` in `DataModule`. `GeocodingProvider` binding added to `DataModule` pointing to `MapTilerGeocodingProvider`. `AreaRepositoryImpl` receives validator as constructor arg.
- **AreaContext centroid**: `Double? = null` fields. Guard: `if (centroidLat == null || centroidLng == null) return pois`. Thread via `areaContextFactory.create().copy(centroidLat = lat, centroidLng = lng)` in MapViewModel. Existing callers with no centroid (tests, fakes) get null by default — safe no-op.
- **POI immutability**: `data class POI` — use `poi.copy(latitude = newLat, longitude = newLng)`.
- **Validation chain order**: `validate → enrichPoisWithImages → writePoisToCache`. Validate first to avoid wasting Wikipedia/tile API calls on excluded POIs.
- **Write paths in AreaRepositoryImpl** (4 total; 2 need validation):
  1. ✅ **Cache miss** (~line 136): fresh AI stream → validate before enrich+cache
  2. ✅ **Stale-while-revalidate background refresh** (~line 112): background AI refresh → validate before enrich+cache
  3. ⛔ **Cache hit / offline** (~lines 86, 101, 129): loads from DB — already-validated data, skip
  4. ⛔ **Exception-catch fallback** (~line 160): loads from DB via `loadPoisFromCache()` — already-validated data, skip
- **withContext asymmetry**: Cache-miss path runs inside `flowOn(ioDispatcher)` (bottom of `getAreaPortrait`) so the `collect` block is already on IO — no `withContext` needed. Stale-refresh runs inside `scope.launch {}` (Default dispatcher) so needs `withContext(ioDispatcher)` explicitly. Both patterns are correct; the asymmetry is intentional.
- **submitSearch() is a separate path**: `submitSearch()` calls `getAreaPortrait(query, context)` directly (not via `collectPortraitWithRetry()`). It needs its own centroid threading — see Task 6.
- **Retry context in collectPortraitWithRetry()**: there are TWO `areaContextFactory.create()` calls inside `collectPortraitWithRetry()` — the first at the top, and a second `retryContext` for the broad-query retry. Both need centroid set.
- **FakeX pattern**: Subclass real class (or implement interface), override `suspend fun`, expose `callCount` and captured inputs. `FakeMapTilerGeocodingProvider` already implements `GeocodingProvider` transitively and is reusable in `PoiCoordinateValidatorTest`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `commonMain/.../domain/provider/AreaIntelligenceProvider.kt` | Pattern for new `GeocodingProvider` interface |
| `commonMain/.../data/remote/GeminiAreaIntelligenceProvider.kt` | Pattern: data impl of domain interface |
| `commonMain/.../data/remote/MapTilerGeocodingProvider.kt` | Add `implements GeocodingProvider`; `search(query, limit=1)` |
| `commonMain/.../domain/model/AreaContext.kt` | Add `centroidLat: Double?`, `centroidLng: Double?` |
| `commonMain/.../domain/model/POI.kt` | `latitude: Double?`, `longitude: Double?` — nullable |
| `commonMain/.../domain/model/GeocodingSuggestion.kt` | Return type of `GeocodingProvider.search()` |
| `commonMain/.../domain/model/BucketUpdate.kt` | `PortraitComplete(pois)` — POI arrival point |
| `commonMain/.../data/repository/AreaRepositoryImpl.kt` | 2 write paths needing validation; constructor params |
| `commonMain/.../domain/service/AreaContextFactory.kt` | Service class pattern |
| `commonMain/.../di/DataModule.kt` | Register new bindings |
| `commonMain/.../ui/map/MapViewModel.kt` | `collectPortraitWithRetry()` + `submitSearch()` + retryContext |
| `commonTest/.../fakes/FakeMapTilerGeocodingProvider.kt` | Reuse as `GeocodingProvider` in validator tests |

### Technical Decisions

- **GeocodingProvider interface**: Clean arch requires domain services only import domain interfaces. Pattern: `AreaIntelligenceProvider` / `GeminiAreaIntelligenceProvider`. Same here.
- **Centroid as `Double?`**: Safer than `0.0` sentinel which would break validation for users near Null Island (0°N, 0°E, Gulf of Guinea). `null` unambiguously means "unknown".
- **Bounding box**: Fixed `BBOX_DEGREES = 0.15` constant. Rectangular (not haversine). Applied twice: once to Gemini coords (outlier detection), once to re-geocoded coords (name-collision guard).
- **Re-geocode**: Serial. Limit=1. Fail-silent → exclude. Re-geocoded result also checked against bbox.
- **Validator layer**: `AreaRepositoryImpl` owns cache write-back. Validator lives there, not in ViewModel.

## Implementation Plan

### Tasks

- [x] **Task 1: Create GeocodingProvider domain interface**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/GeocodingProvider.kt` (NEW)
  - Action:
    ```kotlin
    package com.areadiscovery.domain.provider

    import com.areadiscovery.domain.model.GeocodingSuggestion

    interface GeocodingProvider {
        suspend fun search(query: String, limit: Int = 5): Result<List<GeocodingSuggestion>>
    }
    ```
  - Notes: `domain/provider` directory already exists (contains `AreaIntelligenceProvider`, `WeatherProvider`). This is the established pattern for domain-layer service interfaces.

- [x] **Task 2: Make MapTilerGeocodingProvider implement GeocodingProvider**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MapTilerGeocodingProvider.kt`
  - Action: Add `: GeocodingProvider` to the class declaration:
    ```kotlin
    open class MapTilerGeocodingProvider(private val httpClient: HttpClient) : GeocodingProvider {
    ```
  - Add import: `import com.areadiscovery.domain.provider.GeocodingProvider`
  - Notes: The existing `search(query, limit)` signature already matches the interface. No other changes needed. `FakeMapTilerGeocodingProvider` transitively implements `GeocodingProvider` for free.

- [x] **Task 3: Add centroid fields to AreaContext**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/AreaContext.kt`
  - Action:
    ```kotlin
    data class AreaContext(
        val timeOfDay: String,
        val dayOfWeek: String,
        val visitCount: Int,
        val preferredLanguage: String,
        val centroidLat: Double? = null,
        val centroidLng: Double? = null,
    )
    ```
  - Notes: `Double? = null` is safer than `0.0` sentinel (avoids Null Island false no-op at 0°N,0°E). Default null means all existing callers (tests, fakes) compile and trigger the no-op guard unchanged.

- [x] **Task 4: Create PoiCoordinateValidator**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/service/PoiCoordinateValidator.kt` (NEW)
  - Action:
    ```kotlin
    package com.areadiscovery.domain.service

    import com.areadiscovery.domain.model.POI
    import com.areadiscovery.domain.provider.GeocodingProvider
    import com.areadiscovery.util.AppLogger

    open class PoiCoordinateValidator(
        private val geocodingProvider: GeocodingProvider,
    ) {
        companion object {
            const val BBOX_DEGREES = 0.15
        }

        open suspend fun validate(
            pois: List<POI>,
            centroidLat: Double?,
            centroidLng: Double?,
        ): List<POI> {
            // No-op guard: skip validation if centroid is unknown
            if (centroidLat == null || centroidLng == null) return pois

            return pois.mapNotNull { poi ->
                val lat = poi.latitude
                val lng = poi.longitude

                // Exclude POIs with no coordinates
                if (lat == null || lng == null) {
                    AppLogger.d { "PoiCoordinateValidator: excluding '${poi.name}' — null coords" }
                    return@mapNotNull null
                }

                // Within bbox — keep as-is
                if (kotlin.math.abs(lat - centroidLat) <= BBOX_DEGREES &&
                    kotlin.math.abs(lng - centroidLng) <= BBOX_DEGREES) {
                    return@mapNotNull poi
                }

                // Outside bbox — re-geocode by name
                AppLogger.d { "PoiCoordinateValidator: '${poi.name}' outside bbox ($lat,$lng) — re-geocoding" }
                val result = try {
                    geocodingProvider.search(poi.name, limit = 1)
                } catch (e: Exception) {
                    AppLogger.e(e) { "PoiCoordinateValidator: re-geocode failed for '${poi.name}'" }
                    Result.failure(e)
                }

                val suggestion = result.getOrNull()?.firstOrNull()
                when {
                    suggestion == null -> {
                        AppLogger.d { "PoiCoordinateValidator: excluding '${poi.name}' — no re-geocode result" }
                        null
                    }
                    // Secondary bbox check — guards against name-collision returning a
                    // same-named place in a different city/country
                    kotlin.math.abs(suggestion.latitude - centroidLat) > BBOX_DEGREES ||
                    kotlin.math.abs(suggestion.longitude - centroidLng) > BBOX_DEGREES -> {
                        AppLogger.d { "PoiCoordinateValidator: excluding '${poi.name}' — re-geocoded coords still outside bbox" }
                        null
                    }
                    else -> {
                        AppLogger.d { "PoiCoordinateValidator: '${poi.name}' re-geocoded to (${suggestion.latitude},${suggestion.longitude})" }
                        poi.copy(latitude = suggestion.latitude, longitude = suggestion.longitude)
                    }
                }
            }
        }
    }
    ```
  - Notes: Depends only on `domain.provider.GeocodingProvider` and `domain.model` — no `data.remote` imports. Clean Architecture compliant.

- [x] **Task 5: Register bindings in DataModule**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
  - Action: Add `GeocodingProvider` interface binding and `PoiCoordinateValidator` singleton. Update `AreaRepositoryImpl` to receive validator. The `MapTilerGeocodingProvider` binding already exists as a concrete `single {}` — add the interface binding alongside it:
    ```kotlin
    single { MapTilerGeocodingProvider(get()) }
    single<GeocodingProvider> { get<MapTilerGeocodingProvider>() }  // ADD
    single { PoiCoordinateValidator(get<GeocodingProvider>()) }     // ADD
    single<AreaRepository> {
        AreaRepositoryImpl(
            aiProvider = get(),
            database = get(),
            scope = get(named("appScope")),
            clock = get(),
            connectivityObserver = { get<ConnectivityMonitor>().observe() },
            wikipediaImageRepository = get(),
            poiCoordinateValidator = get(),  // ADD — before ioDispatcher default param
        )
    }
    ```
  - Add imports: `GeocodingProvider`, `PoiCoordinateValidator`

- [x] **Task 6: Inject validator into AreaRepositoryImpl; call before enrichment in 2 paths**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt`
  - Action 1 — Add constructor param. Insert `poiCoordinateValidator` after `wikipediaImageRepository` and before `ioDispatcher` (which has a default value and must remain last):
    ```kotlin
    internal class AreaRepositoryImpl(
        private val aiProvider: AreaIntelligenceProvider,
        private val database: AreaDiscoveryDatabase,
        private val scope: CoroutineScope,
        private val clock: AppClock = SystemClock(),
        private val connectivityObserver: () -> Flow<ConnectivityState>,
        private val wikipediaImageRepository: WikipediaImageRepository,
        private val poiCoordinateValidator: PoiCoordinateValidator,       // ADD HERE
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO    // keep last
    ) : AreaRepository {
    ```
  - Action 2 — **Cache-miss path** (inside `aiProvider.streamAreaPortrait(...).collect`). This block already runs on `ioDispatcher` (via `flowOn(ioDispatcher)` at the bottom of `getAreaPortrait`) — no `withContext` needed here:
    ```kotlin
    if (update is BucketUpdate.PortraitComplete) {
        val validated = if (update.pois.isNotEmpty())
            poiCoordinateValidator.validate(update.pois, context.centroidLat, context.centroidLng)
        else update.pois
        val enriched = if (validated.isNotEmpty()) enrichPoisWithImages(validated) else validated
        if (enriched.isNotEmpty()) writePoisToCache(enriched, areaName, language)
        emit(BucketUpdate.PortraitComplete(resolveTileRefs(enriched)))
    }
    ```
  - Action 3 — **Stale-while-revalidate background refresh** (inside `scope.launch {}`). This launches on `Dispatchers.Default` — must wrap in `withContext(ioDispatcher)`:
    ```kotlin
    if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
        withContext(ioDispatcher) {
            val validated = poiCoordinateValidator.validate(update.pois, context.centroidLat, context.centroidLng)
            val enriched = enrichPoisWithImages(validated)
            writePoisToCache(enriched, areaName, language)
        }
    }
    ```
  - **Cache-hit path** (~lines 86, 101, 129): loads from `loadPoisFromCache()` — already validated data — no change.
  - **Exception-catch fallback** (~line 160): also loads from `loadPoisFromCache()` — no change.
  - Add import: `PoiCoordinateValidator`

- [x] **Task 7: Thread centroid into AreaContext in MapViewModel (6 call sites)**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action 1 — Add centroid params to `collectPortraitWithRetry()` and set on **both** context objects:
    ```kotlin
    private suspend fun collectPortraitWithRetry(
        areaName: String,
        centroidLat: Double? = null,   // ADD
        centroidLng: Double? = null,   // ADD
        onComplete: suspend (pois: List<POI>, finalAreaName: String) -> Unit,
        onError: suspend (Exception) -> Unit,
    ) {
        val context = areaContextFactory.create()
            .copy(centroidLat = centroidLat, centroidLng = centroidLng)  // ADD
        // ... existing code ...
        // ALSO fix the retry context (~line 805):
        val retryContext = areaContextFactory.create()
            .copy(centroidLat = centroidLat, centroidLng = centroidLng)  // ADD
    ```
  - Action 2 — Update the **5 `collectPortraitWithRetry()` call sites**:
    - `loadLocation()`: `centroidLat = coords.latitude, centroidLng = coords.longitude`
    - `onGeocodingSuggestionSelected()`: `centroidLat = suggestion.latitude, centroidLng = suggestion.longitude`
    - `onRecentSelected()`: `centroidLat = recent.latitude, centroidLng = recent.longitude`
    - `onGeocodingSubmitEmpty()`: `centroidLat = lat, centroidLng = lng`
    - `returnToCurrentLocation()`: `centroidLat = coords.latitude, centroidLng = coords.longitude`
  - Action 3 — Fix **`submitSearch()` area-name branch** (direct `getAreaPortrait` call, not via `collectPortraitWithRetry`). Find the area-name search block inside `submitSearch()` and update its context:
    ```kotlin
    // In submitSearch(), area-name branch:
    val context = areaContextFactory.create()
        .copy(centroidLat = current.latitude, centroidLng = current.longitude)  // ADD
    getAreaPortrait(query, context)
        .catch { ... }
        .collect { ... }
    ```
  - Notes: Default `null` params mean any accidentally missed call sites compile safely and trigger the no-op guard.

- [x] **Task 8: Create FakePoiCoordinateValidator**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakePoiCoordinateValidator.kt` (NEW)
  - Action:
    ```kotlin
    package com.areadiscovery.fakes

    import com.areadiscovery.domain.model.POI
    import com.areadiscovery.domain.provider.GeocodingProvider
    import com.areadiscovery.domain.model.GeocodingSuggestion
    import com.areadiscovery.domain.service.PoiCoordinateValidator

    class FakePoiCoordinateValidator(
        var validatedResult: List<POI>? = null, // null = pass-through unchanged
    ) : PoiCoordinateValidator(
        // Anonymous GeocodingProvider — no data.remote import needed
        object : GeocodingProvider {
            override suspend fun search(query: String, limit: Int) =
                Result.success(emptyList<GeocodingSuggestion>())
        }
    ) {
        var callCount: Int = 0
            private set
        var lastCentroidLat: Double? = null
            private set
        var lastCentroidLng: Double? = null
            private set
        var lastPois: List<POI> = emptyList()
            private set

        override suspend fun validate(
            pois: List<POI>,
            centroidLat: Double?,
            centroidLng: Double?,
        ): List<POI> {
            callCount++
            lastCentroidLat = centroidLat
            lastCentroidLng = centroidLng
            lastPois = pois
            return validatedResult ?: pois
        }
    }
    ```
  - Notes: Uses anonymous `GeocodingProvider` object — no `data.remote` imports in the fake. `lastPois` added so tests can assert the validator received the correct input POI list.

- [x] **Task 9: Write PoiCoordinateValidatorTest**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/domain/service/PoiCoordinateValidatorTest.kt` (NEW)
  - Action: Use `FakeMapTilerGeocodingProvider` (which implements `GeocodingProvider` transitively) as the provider. Use `runTest {}` from `kotlinx.coroutines.test`. Implement these test cases:
    - `poi within bbox is kept with original coords and search not called`
    - `poi outside bbox is re-geocoded and new coords used`
    - `poi outside bbox re-geocoded result also outside bbox is excluded`
    - `poi with null latitude is excluded`
    - `poi with null longitude is excluded`
    - `poi outside bbox when re-geocode returns empty is excluded`
    - `poi outside bbox when re-geocode throws exception is excluded`
    - `all pois pass through when centroid is null`
    - `regression - coastal poi offset 0·2 degrees into ocean is re-geocoded to land coords`
  - Helper at top of test file:
    ```kotlin
    private fun testPoi(name: String, lat: Double?, lng: Double?) =
        POI(name = name, type = "landmark", description = "", confidence = Confidence.HIGH,
            latitude = lat, longitude = lng)
    ```

### Acceptance Criteria

- [x] **AC1:** Given a POI with coords within ±0.15° of the centroid, when `validate()` is called, then the POI is returned with original coords and `GeocodingProvider.search()` is never called.

- [x] **AC2:** Given a POI outside the bbox, when `validate()` is called, then `search(poi.name, 1)` is called and the suggestion's coords replace the original — provided the suggestion itself passes the secondary bbox check.

- [x] **AC3:** Given a POI outside the bbox and `search()` returns a result that is also outside ±0.15°, when `validate()` is called, then the POI is excluded (name-collision guard).

- [x] **AC4:** Given a POI with `latitude = null` or `longitude = null`, when `validate()` is called, then the POI is excluded.

- [x] **AC5:** Given a POI outside the bbox and `search()` returns an empty list, when `validate()` is called, then the POI is excluded.

- [x] **AC6:** Given a POI outside the bbox and `search()` throws an exception, when `validate()` is called, then the exception is caught, the POI is excluded, and no exception propagates to the caller.

- [x] **AC7:** Given `centroidLat == null || centroidLng == null`, when `validate()` is called, then all POIs are returned unchanged and `search()` is never called.

- [x] **AC8:** Given validated POIs returned from `PoiCoordinateValidator`, when `AreaRepositoryImpl` calls `writePoisToCache()`, then the DB stores the corrected coordinates, not Gemini's originals. (Manual: search area → kill app → reopen → pins still correct.)

- [x] **AC9:** Given a coastal area scenario (centroid lat=35.44, lng=-120.89) with a POI at lat=35.24, lng=-121.10 (0.21° into ocean), when validation runs and MapTiler returns a land-based result within bbox, then the corrected pin appears on land.

- [x] **AC10:** Given `submitSearch()` navigates to a new area, when the portrait loads, then the centroid from `MapUiState.Ready.latitude/longitude` is set on `AreaContext` and validation runs with the correct centroid.

## Additional Context

### Dependencies

- `MapTilerGeocodingProvider` — already in DI graph, `open class`
- `FakeMapTilerGeocodingProvider` — already exists; implements `GeocodingProvider` transitively after Task 2
- `area_poi_cache` — `insertOrReplacePois` already exists
- `kotlinx.coroutines.test.runTest` — already on test classpath
- No new Gradle dependencies required

### Testing Strategy

**Unit tests** (`./gradlew :composeApp:test`):
- `PoiCoordinateValidatorTest` — 9 test cases using `runTest{}` + `FakeMapTilerGeocodingProvider`

**Manual regression**:
- Search "Cayucos, CA" on device → verify no pins in ocean
- Search a large inland city → verify POI density normal (no false exclusions)
- Search area → kill app → reopen → verify pins still correct (cache write-back)
- Search area near `submitSearch()` path (text search, not geocoding suggestion) → verify validation fires

**Existing test impact**: `MapViewModelTest` unaffected — `FakePoiCoordinateValidator` passes through by default (`validatedResult = null`).

### Notes

- **withContext asymmetry (intentional)**: Cache-miss path is already on `ioDispatcher` via `flowOn(ioDispatcher)` at the end of `getAreaPortrait()`. Stale-refresh runs in `scope.launch {}` on Default dispatcher and needs `withContext(ioDispatcher)` explicitly. Both are correct.
- **4th PortraitComplete path (exception-catch)**: The catch block at ~line 160 of `AreaRepositoryImpl` calls `loadPoisFromCache()` which returns already-validated DB data. No validation needed — skip it.
- **Validate before enrich**: Excluded POIs must be dropped before `enrichPoisWithImages()` — the enrich step makes N concurrent Wikipedia + MapTiler tile API calls. No point enriching POIs that will be excluded.
- **Re-geocode is serial**: 8-12 POIs typical; 2-3 outliers max in coastal areas = 2-3 extra MapTiler calls. Serial avoids rate-limit 429s. Parallel not worth the risk.
- **Secondary bbox check**: Guards against MapTiler returning a same-named POI from a different city/country (name collision). Without this, a "Pier Street" re-geocode could return a Pier Street in another state.
- **BBOX_DEGREES = 0.15**: At 35°N, 0.15° lat ≈ 16.7km, 0.15° lng ≈ 13.7km. Sufficient for any real neighborhood POI.
- **Backlog**: Mark "Bug — POI Pins Appear in Water" as resolved after device testing confirms the fix.
