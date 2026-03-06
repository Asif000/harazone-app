---
title: 'Fix Map Camera Reset, POI Cache-Hit, and Tile Provider'
slug: 'fix-map-camera-poi-cache-tile-provider'
created: '2026-03-05'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Jetpack Compose', 'MapLibre 11.11.0', 'SQLDelight', 'kotlinx.serialization', 'BuildKonfig', 'Koin']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Confidence.kt'
  - 'composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_poi_cache.sq'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt'
  - 'composeApp/build.gradle.kts'
  - 'composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/2.sqm'
  - 'composeApp/src/androidUnitTest/kotlin/com/areadiscovery/data/repository/AreaRepositoryImplTest.kt'
  - 'composeApp/src/androidUnitTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt'
code_patterns:
  - 'BuildKonfig for secrets (same as GEMINI_API_KEY)'
  - 'SQLDelight new .sq file per logical table group'
  - 'kotlinx.serialization @Serializable + Json.encodeToString/decodeFromString'
  - 'getMapAsync → setStyle callback → moveCamera (authoritative camera path)'
  - 'styleLoaded flag (remember { booleanArrayOf(false) }) set in setStyle callback — no repeated style reloads'
test_patterns:
  - 'androidUnitTest with in-memory SQLDelight driver (sqldelight.sqlite.driver)'
  - 'FakeAreaIntelligenceProvider returning configurable BucketUpdate list'
  - 'MapViewModelTest unchanged — FakeAreaRepository already handles POIs'
---

# Tech-Spec: Fix Map Camera Reset, POI Cache-Hit, and Tile Provider

**Created:** 2026-03-05

## Overview

### Problem Statement

Three map screen issues found during device testing after Story 3.2:

1. **Camera zoom reset** (`MapComposable.android.kt`): `moveCamera()` fires via `LaunchedEffect → getMapAsync` before MapLibre style loads. When style finishes loading it resets the camera viewport, discarding the update. Map renders zoomed to country level instead of zoom 14 at user location.

2. **POI cache-hit returns empty** (`AreaRepositoryImpl.kt`): All four cache-hit/fallback paths (full hit line 74, offline line 89, stale-while-revalidate line 111, error catch ~line 137) hardcode `PortraitComplete(pois = emptyList())`. After first AI call caches buckets, Map screen always gets empty POIs — markers never appear after the first session. Additionally, the stale-while-revalidate background refresh `scope.launch` never persists POIs from the refreshed AI stream.

3. **Demo tile style unusable** (`MapComposable.android.kt`): `demotiles.maplibre.org` renders land/water only — no roads, labels, or street detail. Cannot visually verify camera position or POI placement during development.

### Solution

1. **Camera:** Move `moveCamera` inside the `setStyle` callback (authoritative path). Track a `styleLoaded` flag to avoid redundant `setStyle` calls on subsequent location updates. Guard against (0.0, 0.0) coordinates before GPS resolves.
2. **POI cache:** Add `@Serializable` to `POI` and `Confidence`. Create `area_poi_cache` SQLDelight table with migration. Intercept `PortraitComplete` on AI stream and persist POIs (including in stale-while-revalidate background refresh). Restore POIs from cache in all 4 cache-hit/fallback paths. Delete corrupted cache entries on parse failure.
3. **Tile provider:** Add `MAPTILER_API_KEY` to BuildKonfig (same pattern as `GEMINI_API_KEY`). Switch `MAP_STYLE_URL` to Maptiler `streets-v2`.

### Scope

**In Scope:**
- `POI.kt` + `Confidence.kt` — add `@Serializable`
- `area_poi_cache.sq` — new SQLDelight table + 4 queries + index on `expires_at`
- `2.sqm` — SQLDelight migration for existing installs
- `AreaRepositoryImpl.kt` — persist + restore POIs (all 4 paths + background refresh), expire POI cache, delete corrupted entries
- `MapComposable.android.kt` — fix camera (styleLoaded flag, null-island guard), switch tile URL
- `build.gradle.kts` — `MAPTILER_API_KEY` BuildKonfig field
- `AreaRepositoryImplTest.kt` — new test file with 5 POI persistence tests
- `FakeAreaIntelligenceProvider.kt` — new fake in `fakes/` package (consistent with existing pattern)

**Out of Scope:**
- Tile provider abstraction layer
- Full offline tile caching (Epic 7)
- iOS map implementation
- Custom map style design
- Stale POI / fresh bucket TTL alignment (Epic 7)

## Context for Development

### Codebase Patterns

- **Secrets:** `BuildKonfig` reads `local.properties` first, then `gradle.properties`. See `build.gradle.kts:22-30` for exact 3-line `buildConfigField` pattern. `MAPTILER_API_KEY` follows the same pattern as `GEMINI_API_KEY`. `MAPTILER_API_KEY` is already in `local.properties`.
- **SQLDelight:** One `.sq` file per table group in `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/`. Generated queries auto-exposed as `database.{tableName}Queries`. Adding `area_poi_cache.sq` auto-generates `database.area_poi_cacheQueries` — no manual wiring needed.
- **JSON serialization:** `AreaRepositoryImpl` uses `private val json = Json { ignoreUnknownKeys = true }`. `POI` needs `@Serializable`; `Confidence` (referenced inside `POI`) also needs `@Serializable` — confirmed neither has it yet.
- **MapLibre camera:** `getMapAsync { map -> ... }` fires when map object is ready (before style loads). `map.setStyle(url) { style -> ... }` callback fires after style is fully applied — **safe to call `moveCamera` here**. Use a `styleLoaded` flag (`remember { booleanArrayOf(false) }`) set `true` in the `setStyle` callback — do NOT use `map.style?.isFullyLoaded` as it can be transiently false and trigger redundant `setStyle` calls.
- **`isDestroyed` flag:** `remember { booleanArrayOf(false) }` set `true` in `DisposableEffect.onDispose`. All `getMapAsync` and `setStyle` callbacks must check `if (isDestroyed[0]) return@...` before accessing the map.
- **Flow dispatcher:** `getAreaPortrait` flow uses `.flowOn(ioDispatcher)` — all `collect` block code already runs on IO. No `withContext(ioDispatcher)` needed inside `collect`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt` | Camera fix + tile URL — full file read required |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt` | POI persistence — lines 56-138, all 4 cache-hit/fallback paths |
| `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_bucket_cache.sq` | Reference schema and query pattern for new `area_poi_cache.sq` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt` | Add `@Serializable` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Confidence.kt` | Add `@Serializable` |
| `composeApp/build.gradle.kts:22-30` | BuildKonfig block — replicate for `MAPTILER_API_KEY` |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaRepository.kt` | Reference fake pattern for new `FakeAreaIntelligenceProvider` |
| `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/` | Migration files directory — check for existing `.sqm` files to determine next version |

### Technical Decisions

- **POI TTL:** `CACHE_TTL_SEMI_STATIC_MS` (3 days). POIs are semi-stable. Known V1 limitation: POI TTL is independent of bucket TTL — stale POIs can appear alongside refreshed buckets. Deferred to Epic 7.
- **POI storage key:** `(area_name, language)` composite primary key — mirrors bucket cache pattern.
- **`MAP_STYLE_URL` type:** Changed from `private const val` to `private val` — `BuildKonfig.MAPTILER_API_KEY` is a runtime value, not a compile-time constant.
- **Maptiler style:** `streets-v2` — roads, labels, building names, works at all zoom levels, free tier 100k tiles/month.
- **Camera fix approach:** `setStyle` callback is the authoritative camera path. A `styleLoaded` flag (set once in callback) handles subsequent location updates — no repeated `setStyle` calls. Guard `LaunchedEffect` against (0.0, 0.0) default coordinates to prevent camera moving to null island before GPS resolves.
- **`AreaRepositoryImplTest`:** Does not exist — must be created in `androidUnitTest` (not `commonTest`). Use in-memory SQLite driver (`sqldelight.sqlite.driver` already in `androidUnitTest` dependencies).
- **Corrupted cache entry cleanup:** `loadPoisFromCache` deletes the entry on `decodeFromString` failure rather than leaving it to expire naturally (up to 3 days).
- **API key security (V1):** `MAPTILER_API_KEY` is embedded in the style URL string — extractable from APK. Restrict the key on the Maptiler dashboard to the app's package name (Android bundle restriction). Full key protection via backend proxy deferred to Epic 7+.
- **Empty API key (V1):** If `MAPTILER_API_KEY` is empty, Maptiler returns 403 and map shows no tiles. Documented in README setup instructions. No runtime fallback for V1.
- **Maptiler free tier (V1):** 100k tiles/month. No monitoring or fallback planned for V1. Acceptable for development and early users.

## Implementation Plan

### Tasks

- [x] **Task 1: Add `@Serializable` to `POI` and `Confidence`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt`
    - Add `import kotlinx.serialization.Serializable`
    - Add `@Serializable` annotation above `data class POI`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Confidence.kt`
    - Add `import kotlinx.serialization.Serializable`
    - Add `@Serializable` annotation above `enum class Confidence`
  - Note: Additive only — no existing code changes, no runtime behaviour change

- [x] **Task 2: Create `area_poi_cache.sq`**
  - File: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_poi_cache.sq` *(NEW)*
  - Content:
    ```sql
    CREATE TABLE IF NOT EXISTS area_poi_cache (
        area_name TEXT NOT NULL,
        language TEXT NOT NULL,
        pois_json TEXT NOT NULL,
        expires_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY (area_name, language)
    );

    getPois:
    SELECT * FROM area_poi_cache
    WHERE area_name = :area_name AND language = :language;

    insertOrReplacePois:
    INSERT OR REPLACE INTO area_poi_cache(area_name, language, pois_json, expires_at, created_at)
    VALUES (:area_name, :language, :pois_json, :expires_at, :created_at);

    deleteExpiredPois:
    DELETE FROM area_poi_cache WHERE expires_at <= :current_time;

    deletePoisByArea:
    DELETE FROM area_poi_cache WHERE area_name = :area_name;
    ```
  - Add index (consistent with `area_bucket_cache.sq` pattern):
    ```sql
    CREATE INDEX IF NOT EXISTS idx_area_poi_cache_expires_at ON area_poi_cache(expires_at);
    ```
  - Create migration file: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/2.sqm` *(NEW)*
    ```sql
    CREATE TABLE IF NOT EXISTS area_poi_cache (
        area_name TEXT NOT NULL,
        language TEXT NOT NULL,
        pois_json TEXT NOT NULL,
        expires_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY (area_name, language)
    );
    CREATE INDEX IF NOT EXISTS idx_area_poi_cache_expires_at ON area_poi_cache(expires_at);
    ```
  - Bump database version in SQLDelight driver setup to match migration number
  - Note: SQLDelight auto-generates `database.area_poi_cacheQueries` on next build — no manual wiring

- [x] **Task 3: Update `AreaRepositoryImpl` to persist and restore POIs**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt`
  - Add `writePoisToCache` private function after `writeToCache`:
    ```kotlin
    private fun writePoisToCache(pois: List<POI>, areaName: String, language: String) {
        val now = clock.nowMs()
        database.area_poi_cacheQueries.insertOrReplacePois(
            area_name = areaName,
            language = language,
            pois_json = json.encodeToString(pois),
            expires_at = now + CACHE_TTL_SEMI_STATIC_MS,
            created_at = now,
        )
    }
    ```
  - Add `loadPoisFromCache` private function after `writePoisToCache`:
    ```kotlin
    private fun loadPoisFromCache(areaName: String, language: String, now: Long): List<POI> {
        val cached = database.area_poi_cacheQueries.getPois(areaName, language).executeAsOneOrNull()
        if (cached == null || cached.expires_at <= now) return emptyList()
        return try {
            json.decodeFromString(cached.pois_json)
        } catch (e: Exception) {
            AppLogger.e(e) { "Failed to parse cached POIs for $areaName, deleting corrupted entry" }
            database.area_poi_cacheQueries.deletePoisByArea(areaName)
            emptyList()
        }
    }
    ```
  - Add `deleteExpiredPois` call at line 65, directly after `deleteExpiredBuckets(now)`:
    ```kotlin
    database.area_bucket_cacheQueries.deleteExpiredBuckets(now)
    database.area_poi_cacheQueries.deleteExpiredPois(now)  // ADD THIS
    ```
  - In AI stream path (`collect` block, ~line 117-122): intercept `PortraitComplete` to persist POIs:
    ```kotlin
    aiProvider.streamAreaPortrait(areaName, context).collect { update ->
        emit(update)
        if (update is BucketUpdate.BucketComplete) {
            writeToCache(update.content, areaName, language)
        }
        if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
            writePoisToCache(update.pois, areaName, language)  // ADD THIS
        }
    }
    ```
  - Replace all 4 hardcoded `emit(BucketUpdate.PortraitComplete(pois = emptyList()))`:
    - Line 74 (full cache hit): `emit(BucketUpdate.PortraitComplete(pois = loadPoisFromCache(areaName, language, now)))`
    - Line 89 (offline): `emit(BucketUpdate.PortraitComplete(pois = loadPoisFromCache(areaName, language, now)))`
    - Line 111 (stale-while-revalidate): `emit(BucketUpdate.PortraitComplete(pois = loadPoisFromCache(areaName, language, now)))`
    - ~Line 137 (catch/error fallback): `emit(BucketUpdate.PortraitComplete(pois = loadPoisFromCache(areaName, language, now)))`
  - In stale-while-revalidate `scope.launch` background refresh block: add `writePoisToCache` call when `PortraitComplete` is received:
    ```kotlin
    // Inside scope.launch background refresh (stale-while-revalidate):
    if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
        writePoisToCache(update.pois, areaName, language)
    }
    ```
  - Add import: `import com.areadiscovery.domain.model.POI`

- [x] **Task 4: Fix camera and swap tile URL in `MapComposable.android.kt`**
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt`
  - Change `MAP_STYLE_URL` (line 25):
    ```kotlin
    // Remove:
    private const val MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json"
    // Add:
    private val MAP_STYLE_URL = "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildKonfig.MAPTILER_API_KEY}"
    ```
    Add import: `import com.areadiscovery.BuildKonfig`
  - In `remember { MapView(...).apply { ... } }` block: remove `getMapAsync { map -> map.setStyle(MAP_STYLE_URL) }` — style loading moves to `LaunchedEffect`
  - Add `styleLoaded` flag alongside existing `isDestroyed`:
    ```kotlin
    val styleLoaded = remember { booleanArrayOf(false) }
    ```
  - Replace `LaunchedEffect(latitude, longitude)` body entirely:
    ```kotlin
    LaunchedEffect(latitude, longitude) {
        // Guard: skip camera move until GPS resolves (avoid null island)
        if (latitude == 0.0 && longitude == 0.0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (isDestroyed[0]) return@getMapAsync
            if (styleLoaded[0]) {
                // Fast path: style already loaded (e.g. location update after initial render)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoomLevel))
            } else {
                // Authoritative path: set style then move camera inside loaded callback
                map.setStyle(MAP_STYLE_URL) { _ ->
                    if (!isDestroyed[0]) {
                        styleLoaded[0] = true
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoomLevel))
                    }
                }
            }
        }
    }
    ```

- [x] **Task 5: Add `MAPTILER_API_KEY` to BuildKonfig**
  - File: `composeApp/build.gradle.kts`
  - Inside `buildkonfig { defaultConfigs { ... } }`, add after the `GEMINI_API_KEY` field:
    ```kotlin
    buildConfigField(
        com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
        "MAPTILER_API_KEY",
        localProperties.getProperty("MAPTILER_API_KEY") ?: project.findProperty("MAPTILER_API_KEY")?.toString() ?: ""
    )
    ```

- [x] **Task 6: Create `FakeAreaIntelligenceProvider` and `AreaRepositoryImplTest`**
  - File: `composeApp/src/androidUnitTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt` *(NEW)*
    - Place in `fakes/` package consistent with existing `FakeAreaRepository` pattern
    - Accepts a configurable list of `BucketUpdate` to emit via `Flow`
  - File: `composeApp/src/androidUnitTest/kotlin/com/areadiscovery/data/repository/AreaRepositoryImplTest.kt` *(NEW)*
  - Use in-memory SQLDelight driver (`sqldelight.sqlite.driver` already in `androidUnitTest` dependencies).
  - Required tests:
    ```kotlin
    @Test fun poisPersistedToCacheOnAiStreamPortraitComplete()
    // Setup: FakeAIProvider emits BucketComplete(x6) + PortraitComplete(mockPOIs)
    // Assert: database.area_poi_cacheQueries.getPois(areaName, language).executeAsOneOrNull() != null
    // Assert: decoded pois_json == mockPOIs
    // Assert: collected flow contains PortraitComplete with pois == mockPOIs

    @Test fun poisRestoredOnFullCacheHit()
    // Setup: pre-populate area_bucket_cache (6 valid buckets) + area_poi_cache with mockPOIs
    // Collect flow, find PortraitComplete
    // Assert: PortraitComplete.pois == mockPOIs

    @Test fun poisRestoredOnStaleWhileRevalidatePath()
    // Setup: pre-populate area_bucket_cache (stale buckets) + area_poi_cache with mockPOIs
    // Collect flow, find PortraitComplete
    // Assert: PortraitComplete.pois == mockPOIs

    @Test fun emptyPoisReturnedWhenPoiCacheExpired()
    // Setup: pre-populate area_poi_cache with expires_at = clock.nowMs() - 1
    // Collect flow (cache hit), find PortraitComplete
    // Assert: PortraitComplete.pois == emptyList()

    @Test fun poisRestoredOnErrorFallbackPath()
    // Setup: FakeAIProvider throws exception. Pre-populate area_bucket_cache + area_poi_cache with mockPOIs
    // Collect flow, find PortraitComplete
    // Assert: PortraitComplete.pois == mockPOIs (served from cache despite AI failure)
    ```

### Acceptance Criteria

- [ ] **AC1:** Given the app launches fresh and GPS resolves, when the Map screen enters `Ready` state, then the map centres on the user's coordinates at zoom 14 **and maintains that zoom** after tiles finish loading — the map does not reset to country-level view.

- [ ] **AC2:** Given the Summary screen has already triggered an AI portrait call (buckets cached), when the user switches to the Map tab, then POI markers are rendered on the map matching the POIs from the first AI call.

- [ ] **AC3:** Given POIs were loaded from AI on a previous session (POI cache populated), when the app is relaunched and the Map tab is opened, then POI markers appear without a new AI call (served from `area_poi_cache`).

- [ ] **AC4:** Given the Maptiler API key is in `local.properties`, when the Map screen renders at zoom 14, then street names, road network, and area labels are visible.

- [ ] **AC5:** Given the composable is disposed (navigate away from Map tab) while a `getMapAsync` or `setStyle` callback is queued, when the callback fires, then no crash occurs — `isDestroyed[0]` guard prevents map access.

- [ ] **AC6:** Given a POI cache entry exists but is expired (`expires_at <= now`), when the cache-hit path runs, then `PortraitComplete(pois = emptyList())` is emitted — expired entries do not surface stale POIs.

- [ ] **AC7:** Given the AI call fails after POIs were previously cached, when the error fallback serves cached buckets, then POIs are also served from cache — `PortraitComplete` contains cached POIs, not an empty list.

## Additional Context

### Dependencies

- `MAPTILER_API_KEY` must be in `local.properties` — **already added by user**
- `kotlinx-serialization` already in project — no new dependencies needed
- `sqldelight.sqlite.driver` already in `androidUnitTest` dependencies — available for `AreaRepositoryImplTest`

### Testing Strategy

- **Unit tests (new):** `AreaRepositoryImplTest` (in `androidUnitTest`) — 5 tests covering POI persistence, restoration, expiry, and error fallback
- **Unit tests (existing):** `MapViewModelTest` — no changes needed; `FakeAreaRepository` already handles `PortraitComplete(pois)` correctly
- **No unit test for camera fix** — MapLibre composable cannot be unit tested. Verified by manual device test only:
  - Fresh install → grant location → tap Map tab → confirm map centres at user location with street labels at zoom 14
  - Navigate away and back → confirm camera position maintained

### Notes

- `@Serializable` on `POI` and `Confidence` is purely additive — no runtime behaviour change for existing code
- **V1 known limitation:** POI TTL (3 days) is independent of bucket TTL. If buckets expire and refresh via stale-while-revalidate but POI cache hasn't expired, the user sees stale POIs alongside fresh bucket content. Accepted for V1, deferred to Epic 7.
- SQLDelight generates `area_poi_cacheQueries` automatically from the new `.sq` file on next build — verify by running `./gradlew generateCommonMainAreaDiscoveryDatabaseInterface` after creating the file
- The `styleLoaded` flag eliminates the previous concern about `map.style?.isFullyLoaded` being transiently false — `setStyle` is now called exactly once, and subsequent location updates only call `moveCamera`
- The `(0.0, 0.0)` guard in `LaunchedEffect` prevents the camera from jumping to null island before GPS resolves — the style will be loaded on the first real coordinate update

## Review Notes
- Adversarial review completed
- Findings: 7 total, 2 fixed, 5 skipped (3 noise, 2 documented V1 limitations)
- Resolution approach: auto-fix
- F1 (fixed): Added `styleLoading` flag to prevent concurrent `setStyle` calls on rapid location updates
- F2 (fixed): POI markers now queue in `pendingPois` until style loads, then flush in `setStyle` callback
- F3-F7 (skipped): Stale `now` negligible with 3-day TTL; API key exposure documented in tech spec; empty key behavior documented
