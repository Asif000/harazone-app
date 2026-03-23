---
title: 'Google Places API Integration + Area Currency & Language Context'
slug: 'google-places-poi-enrichment'
created: '2026-03-22'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (KMP) — commonMain only, no Android-only SDK'
  - 'Compose Multiplatform'
  - 'Ktor (HttpClient) — REST calls'
  - 'Koin — DI'
  - 'SQLDelight — local SQLite cache'
  - 'BuildKonfig — API key injection'
  - 'kotlinx.serialization — JSON parsing'
  - 'Kermit — logging (co.touchlab.kermit.Logger)'
files_to_modify:
  - 'composeApp/build.gradle.kts'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/MetaLine.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/provider/ApiKeyProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/BuildKonfigApiKeyProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt'
code_patterns:
  - 'Provider interface in domain/provider/, Ktor impl in data/remote/'
  - 'Result<T> return type — never throw from provider (catch + log, return success with original)'
  - 'CancellationException must be rethrown, never swallowed'
  - 'Logger.withTag("ClassName") for Kermit logging'
  - 'coroutineScope { pois.map { async { semaphore.withPermit { ... } } }.awaitAll() } for concurrent enrichment'
  - 'BucketUpdate.BackgroundEnrichmentComplete for re-emitting enriched POIs after initial emit'
  - 'SQLDelight .sq file for schema + queries — INSERT OR REPLACE pattern'
  - 'BuildKonfig block in build.gradle.kts for API key, read from local.properties'
test_patterns:
  - 'commonTest, kotlin.test + runTest'
  - 'MockEngine { request -> respond(content, status, headers) } for Ktor HTTP mocking'
  - 'FakeClock(nowMs) for time control'
  - 'Inline JSON fixture strings in companion object'
  - 'Test file: commonTest/kotlin/com/harazone/data/remote/GooglePlacesProviderTest.kt'
---

# Tech-Spec: Google Places API Integration + Area Currency & Language Context

**Created:** 2026-03-22

## Overview

### Problem Statement

Gemini hallucinates POI operational data: `hours`, `liveStatus`, `rating`, and `priceRange` are AI-generated and frequently wrong (e.g. "24/7" for parks that close, "open" for a closed venue). Users are misled by confidently-presented but fabricated data.

### Solution

Add a Google Places API (New) enrichment layer that fetches verified `hours`, `liveStatus`, `rating`, `reviewCount`, and `priceRange` per POI after Gemini generates them. Enrichment is best-effort, runs as a background pass, and is silent on failure — Gemini values remain as fallback. A "Verified by Google" chip appears in the detail card when Places data is present (`reviewCount != null`). Results are cached 24h per POI in a dedicated `places_enrichment_cache` SQLite table.

### Scope

**In Scope:**
- `PlacesProvider` interface in `domain/provider/`
- `GooglePlacesProvider` implementation via Places API (New) REST + Ktor — KMP-compatible, no Android SDK
- `GOOGLE_PLACES_API_KEY` added to BuildKonfig (`build.gradle.kts` + `local.properties`)
- `placesApiKey: String` on `ApiKeyProvider` interface; `BuildKonfigApiKeyProvider` reads it
- New `reviewCount: Int?` field on `POI.kt` (null = Gemini source, non-null = Places verified)
- New `places_enrichment_cache.sq` SQLite table, keyed by `poi_saved_id`, 24h TTL
- `enrichPoisWithPlaces()` private method in `AreaRepositoryImpl`, chained after `enrichPoisWithImages()` via a new `enrichPois()` wrapper; all 4 existing call sites updated
- `PlacesProvider` and updated `AreaRepositoryImpl` registered in `DataModule`
- "Verified by Google" chip in `AiDetailPage` when `poi.reviewCount != null`
- `hours` field rendered in `AiDetailPage` (currently missing from UI — field already exists on `POI`)
- `reviewCount` displayed alongside rating (e.g. "4.2 ⭐ (1,840)")

**Currency & Language Context (Gemini-sourced, no separate API):**
- New top-level fields `cc` (currency + rate) and `lg` (language) added to Gemini area portrait prompt schema
- `GeminiResponseParser` extracts them from the portrait JSON response
- `BucketUpdate.PortraitComplete` carries two new nullable fields: `currencyText: String?` and `languageText: String?`
- `AreaRepositoryImpl` preserves these fields when re-emitting `PortraitComplete` with enriched POIs
- `MapUiState.Ready` gains `areaCurrencyText: String?` and `areaLanguageText: String?`
- `MapViewModel` populates them from `PortraitComplete` and passes to `buildMetaLines()`
- Two new `MetaLine` variants: `CurrencyContext(text)` (priority 2, teal) and `LanguageContext(text)` (priority 2, teal)
- `buildMetaLines()` adds these lines only when `isRemote = true` and values are non-null
- Fallback: Gemini fills if available; null if unknown (meta lines simply absent — no error, no placeholder)
- Currency/language only available on fresh Gemini fetch; null on POI cache hit (acceptable for beta)

**Out of Scope:**
- Individual user review text
- User-submitted photos (existing image pipeline unchanged)
- Reservations / booking
- Live exchange rate API (Gemini provides a reasonable approximation)
- Persisting currency/language in cache across app restarts (beta: in-memory only)

---

## Prerequisites / Blockers

> **HARD BLOCKER:** Do not begin implementation until the **Home Screen Redesign PR** (`feature/discovery-header-redesign`) has merged into `main`. That branch modifies both `MapViewModel.kt` and `AreaRepositoryImpl.kt` — the same files this spec touches. Starting before the merge will produce conflicts that are expensive to resolve.

---

## Context for Development

### Codebase Patterns

**Provider pattern (follow exactly):**
- Interface: `domain/provider/SomeName.kt` — `interface PlacesProvider { suspend fun enrichPoi(poi: POI): Result<POI> }`
- Implementation: `data/remote/GooglePlacesProvider.kt` — constructor-injected `HttpClient`, `ApiKeyProvider`, `AreaDiscoveryDatabase`, `AppClock`
- Error handling: `try { ... } catch (e: CancellationException) { throw e } catch (e: Exception) { log.w(e) { "..." }; Result.success(poi) }` — always return original POI on failure
- Example to mirror: `FcdoAdvisoryProvider.kt` + `AdvisoryProvider.kt`

**Enrichment pipeline (follow exactly):**
- Introduce `enrichPois(pois: List<POI>): List<POI>` in `AreaRepositoryImpl`:
  ```kotlin
  private suspend fun enrichPois(pois: List<POI>): List<POI> {
      val imageEnriched = enrichPoisWithImages(pois)
      return enrichPoisWithPlaces(imageEnriched)
  }
  ```
- Add `enrichPoisWithPlaces(pois: List<POI>): List<POI>` using same `coroutineScope { map { async { semaphore.withPermit { ... } } }.awaitAll() }` pattern as `enrichPoisWithImages()`
- Replace all 4 existing `enrichPoisWithImages(...)` call sites in `AreaRepositoryImpl` with `enrichPois(...)`
- `placesProvider` injected as constructor param on `AreaRepositoryImpl`
- `PLACES_MAX_CONCURRENT_REQUESTS = 3` (constant in companion object)

**Google Places API (New) — exact REST call:**
- Endpoint: `POST https://places.googleapis.com/v1/places:searchText`
- Request headers:
  - `X-Goog-Api-Key: {apiKey}`
  - `X-Goog-FieldMask: places.id,places.displayName,places.currentOpeningHours,places.regularOpeningHours,places.rating,places.userRatingCount,places.priceLevel`
  - `Content-Type: application/json`
- Request body: **DO NOT use string interpolation** — use a `@Serializable` request data class and `Json.encodeToString()` to avoid malformed JSON from POI names containing `"`, `\`, or newlines (e.g. "McDonald's", "L'Opera", `"The Spot"`). Define:
  ```kotlin
  @Serializable private data class PlacesRequest(
      val textQuery: String,
      val locationBias: LocationBias,
      val maxResultCount: Int = 1,
  )
  @Serializable private data class LocationBias(val circle: Circle)
  @Serializable private data class Circle(val center: LatLng, val radius: Double = 100.0)
  @Serializable private data class LatLng(val latitude: Double, val longitude: Double)
  ```
  Then: `setBody(Json.encodeToString(PlacesRequest(poi.name, LocationBias(Circle(LatLng(poi.latitude!!, poi.longitude!!))))))`
- Response: `{ "places": [ { "id": "...", "displayName": { "text": "..." }, "currentOpeningHours": { "openNow": true, "weekdayDescriptions": [...] }, "regularOpeningHours": { "weekdayDescriptions": [...] }, "rating": 4.2, "userRatingCount": 1840, "priceLevel": "PRICE_LEVEL_MODERATE" } ] }`
- **Match check** — skip enrichment and return original POI if any of:
  - `poi.name.length < 6` (too short to match reliably)
  - `places` array is empty or missing
  - Token match fails (see below)
- **Token match logic** (implement as `internal fun isConfidentMatch(poiName: String, displayName: String): Boolean`):
  ```kotlin
  val normalize = { s: String -> s.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim() }
  val poiTokens = normalize(poiName).split(" ").filter { it.length >= 3 }.toSet()
  val dispTokens = normalize(displayName).split(" ").filter { it.length >= 3 }.toSet()
  if (poiTokens.isEmpty() || dispTokens.isEmpty()) return false
  // All significant tokens of the SHORTER name must be present in the longer name's token set
  val (shorter, longer) = if (poiTokens.size <= dispTokens.size) poiTokens to dispTokens else dispTokens to poiTokens
  shorter.all { it in longer }
  ```
  This correctly rejects "Park" ⊄ {"hyde","park","hotel"} — wait, "park" IS in that set. The fix is the token approach still passes "Park"→"Hyde Park Hotel". BUT `poi.name.length < 6` guard eliminates "Park" (4 chars). Combined guards are sufficient for beta.

**Field mapping:**
- `places[0].currentOpeningHours.openNow: Boolean?` → `liveStatus`: `true` → `"open"`, `false` → `"closed"`, null → keep existing `poi.liveStatus`
- Hours: try `currentOpeningHours.weekdayDescriptions` first (live schedule); fall back to `regularOpeningHours.weekdayDescriptions` if `currentOpeningHours` is absent (e.g. after hours, no live data). Join with `"\n"`. If both null, keep existing `poi.hours`. This justifies keeping `regularOpeningHours` in the field mask — it is now consumed, not wasted.
- `places[0].rating: Double?` → `poi.rating: Float?` (`.toFloat()`)
- `places[0].userRatingCount: Int?` → `poi.reviewCount: Int?`
- `places[0].priceLevel: String?` mapping:
  - `"PRICE_LEVEL_FREE"` → `"Free"`
  - `"PRICE_LEVEL_INEXPENSIVE"` → `"$"`
  - `"PRICE_LEVEL_MODERATE"` → `"$$"`
  - `"PRICE_LEVEL_EXPENSIVE"` → `"$$$"`
  - `"PRICE_LEVEL_VERY_EXPENSIVE"` → `"$$$$"`
  - null / unknown → keep existing `poi.priceRange`

**SQLite cache (separate table):**
- New file: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/places_enrichment_cache.sq`
- Schema:
  ```sql
  CREATE TABLE IF NOT EXISTS places_enrichment_cache (
      saved_id TEXT NOT NULL PRIMARY KEY,
      hours TEXT,
      live_status TEXT,
      rating REAL,
      review_count INTEGER,
      price_range TEXT,
      expires_at INTEGER NOT NULL,
      cached_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_places_cache_expires_at ON places_enrichment_cache(expires_at);
  ```
- Queries: `getPlacesData` (by saved_id), `insertOrReplace`, `deleteExpired` (by current_time)
- `CACHE_TTL_MS = 24 * 60 * 60 * 1000L` (companion constant in `GooglePlacesProvider`)
- Cache write: only when `reviewCount != null` (confident match found). No-match results are not cached.
- Cache read: check `expires_at > clock.nowMs()` before using. Apply cached fields using `?: poi.existingField` fallback.
- **Cache cleanup:** call `database.places_enrichment_cacheQueries.deleteExpired(clock.nowMs())` at the start of `enrichPoi()`, before the cache read. This is lazy TTL cleanup — runs once per enrichment pass, bounded by the number of POIs processed. Ensures the table does not grow indefinitely.

**POI.savedId:** `"$name|${latitude ?: 0.0}|${longitude ?: 0.0}"` — use as `saved_id` in `places_enrichment_cache`

**BuildKonfig API key:**
- `build.gradle.kts` — inside `buildkonfig { defaultConfigs { ... } }`, add:
  ```kotlin
  buildConfigField(
      com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
      "GOOGLE_PLACES_API_KEY",
      localProperties.getProperty("GOOGLE_PLACES_API_KEY") ?: project.findProperty("GOOGLE_PLACES_API_KEY")?.toString() ?: ""
  )
  ```
- `ApiKeyProvider.kt`: add `val placesApiKey: String`
- `BuildKonfigApiKeyProvider.kt`: add `override val placesApiKey: String = BuildKonfig.GOOGLE_PLACES_API_KEY`

**AiDetailPage insertion points (by line reference):**
- Rating row (~line 513): change `text = " ${poi.rating}"` to `text = " ${poi.rating}${if ((poi.reviewCount ?: 0) > 0) " (${poi.reviewCount})" else ""}"` — show count only when > 0; chip still shows for reviewCount = 0 (see applyCache note below)
- After priceRange block (~line 535): add `hours` row:
  ```kotlin
  if (poi.hours != null) {
      Spacer(Modifier.height(4.dp))
      Text(
          text = poi.hours,
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFF6B6B6B),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,  // prevents 7-line layout explosion
      )
  }
  // TODO(BACKLOG-LOW): replace with "Today: X (tap for full schedule)" affordance post-beta
  ```
- After hours row: add "Verified by Google" chip — `if (poi.reviewCount != null) { Spacer(4.dp); VerifiedByGoogleChip() }`
- `VerifiedByGoogleChip` is a private `@Composable` in the same file: a small `Row` with a "G" icon (or `Icons.Default.Verified` if available) and text `"Verified by Google"` in `labelSmall`, styled as a subtle chip (e.g. light grey background `0xFFF1F1F1`, rounded 6.dp, padding 4dp/2dp)

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `domain/provider/AdvisoryProvider.kt` | Provider interface pattern to mirror |
| `data/remote/FcdoAdvisoryProvider.kt` | Ktor impl, cache + error handling pattern |
| `commonTest/.../FcdoAdvisoryProviderTest.kt` | Test pattern — MockEngine, FakeClock, runTest, inline JSON |
| `domain/model/POI.kt` | Add `reviewCount: Int? = null` |
| `domain/provider/ApiKeyProvider.kt` | Add `val placesApiKey: String` |
| `data/remote/BuildKonfigApiKeyProvider.kt` | Add `placesApiKey` override |
| `data/repository/AreaRepositoryImpl.kt` | Add `enrichPoisWithPlaces()`, `enrichPois()` wrapper, replace 4 call sites |
| `di/DataModule.kt` | Register `PlacesProvider` + update `AreaRepositoryImpl` binding |
| `ui/map/components/AiDetailPage.kt` | Add hours row, reviewCount, "Verified by Google" chip |
| `sqldelight/.../area_poi_cache.sq` | Reference schema + query style |
| `composeApp/build.gradle.kts` | Add `GOOGLE_PLACES_API_KEY` BuildKonfig field |
| `commonTest/fakes/FakeClock.kt` | Reference for test fake construction |
| `domain/model/MetaLine.kt` | Add `CurrencyContext` and `LanguageContext`; update `buildMetaLines()` |
| `domain/model/BucketUpdate.kt` | Add `currencyText: String? = null` and `languageText: String? = null` to `PortraitComplete` |
| `data/remote/GeminiPromptBuilder.kt` | Add `cc`/`lg` to pin-only prompt schema + rules |
| `data/remote/GeminiResponseParser.kt` | Extract `cc` and `lg` from top-level area portrait JSON |
| `ui/map/MapUiState.kt` | Add `areaCurrencyText: String?` and `areaLanguageText: String?` to `Ready` |
| `ui/map/MapViewModel.kt` | Populate new state fields from `PortraitComplete`; pass to `buildMetaLines()` |

### Technical Decisions

1. **Separate `places_enrichment_cache` table** — keeps Places data isolated, enables independent 24h TTL without invalidating the Gemini POI JSON blob cache.
2. **`enrichPois()` wrapper + separate `enrichPoisWithPlaces()` pass** — cleaner than merging into `enrichPoisWithImages()`; both remain individually testable and independently callable.
3. **`reviewCount: Int?` as the Places source signal** — non-null = Places verified; drives badge without a separate boolean flag.
4. **Silent failures everywhere** — network error, quota exceeded, no match, no coordinates: all return original POI unchanged. Error is logged at `warn` level only.
5. **Match threshold: substring check** — `poi.name.lowercase()` ⊆ `displayName.text.lowercase()` OR vice versa. Simple, no extra library needed.
6. **No cache for no-match results** — avoids polluting the cache with empty entries. Re-fetches on next load (acceptable for beta, revisit post-beta).
7. **`locationBias.radius = 100.0` metres** — tight radius (we have Gemini-provided coords); reduces false-match risk for densely-spaced venues.
8. **`hours` field rendered even without Places data** — if Gemini provided hours and Places didn't match, still show the Gemini hours (no badge).
9. **Currency/language: Gemini prompt only, no separate API** — Gemini already returns accurate currency and language for areas worldwide. Adding structured `cc`/`lg` fields to the prompt is sufficient and zero-cost.
**[F10 fix] Safe `cc`/`lg` parsing in `GeminiResponseParser`:** Use `(root["cc"] as? JsonPrimitive)?.contentOrNull` rather than `root["cc"]?.jsonPrimitive?.contentOrNull`. The `.jsonPrimitive` property throws `IllegalArgumentException` if the element is a `JsonObject` or `JsonArray`; the safe cast silently returns null on unexpected types, preventing parser crash.
**[F11 fix] `applyCache` defensive guard:** `cached.review_count?.toInt() ?: 0` — if `review_count` is unexpectedly null in a DB row (shouldn't happen per write logic, but defensive), use `0` rather than null. `reviewCount = 0` still triggers the "Verified by Google" chip (chip gates on `!= null`); the count text is suppressed (gates on `> 0`). This keeps the chip invariant: any row in `places_enrichment_cache` = Places-verified = chip shows.
**[F13 fix] Exchange rate disclaimer:** Instruct Gemini to prefix the rate value with `~` to signal approximation (e.g. `"¥ · ~149 JPY/USD"`). This presents Gemini-approximate data as clearly non-authoritative without adding a full disclaimer string. Do not remove the rate — it provides genuine context. Post-beta: wire to a live exchange rate API and remove the `~`.
**[F14] `savedId` null-coord collision:** `POI.savedId` uses `0.0` as fallback for null lat/lng. This cannot cause a cache collision in `places_enrichment_cache` because `enrichPoi()` returns early (no cache read/write) when `latitude == null || longitude == null`. No action needed; documented as a known safe assumption.
**[F15] SQLDelight schema migration:** The new `places_enrichment_cache` table uses `CREATE TABLE IF NOT EXISTS`, making it safe for fresh installs. For upgrade paths, verify that `DatabaseDriverFactory` either (a) does not use a fixed schema version, or (b) bumps the schema version to include the new table. Add AC18 to cover this.
10. **Currency/language only on fresh Gemini fetch** — these fields flow through `PortraitComplete` only; not stored in `area_poi_cache`. On cache hit, they're null and meta lines are absent. Acceptable for beta — cache TTL is 12–14 days for static content; most users will see them on first visit.
11. **`MetaLine.CurrencyContext` and `MetaLine.LanguageContext` both at priority 2** — same as `RemoteContext` (all are "remote area context"). They rotate with `RemoteContext` in the ticker. When `isRemote = false`, none of these priority-2 lines are added.
12. **No `isRemote` change needed** — `buildMetaLines()` already receives `isRemote: Boolean`. Just gate the new lines on that param.
13. **`PortraitComplete` field propagation** — `AreaRepositoryImpl` re-emits `PortraitComplete` with enriched POIs in several places. In every case, the `currencyText`/`languageText` from the upstream `PortraitComplete` must be copied to the new emission. Implementer must audit all `emit(BucketUpdate.PortraitComplete(...))` call sites in `AreaRepositoryImpl`.
14. **Gemini prompt field format** — `cc` example: `"¥ · 1 USD = 149 JPY"`. Instruct Gemini to omit `cc` for USD regions (US, EC countries) and omit `lg` for English-speaking regions. This avoids noise for local users.

---

## Implementation Plan

### Tasks

- [ ] **T1: Add `GOOGLE_PLACES_API_KEY` to BuildKonfig**
  - File: `composeApp/build.gradle.kts`
  - Action: Inside `buildkonfig { defaultConfigs { } }`, add `buildConfigField(STRING, "GOOGLE_PLACES_API_KEY", localProperties.getProperty("GOOGLE_PLACES_API_KEY") ?: project.findProperty("GOOGLE_PLACES_API_KEY")?.toString() ?: "")` — follow the existing `GEMINI_API_KEY` and `MAPTILER_API_KEY` entries exactly
  - Notes: Also add `GOOGLE_PLACES_API_KEY=<your_key>` to `local.properties` (not committed). BuildKonfig regenerates `BuildKonfig.kt` on next build.

- [ ] **T2: Add `reviewCount: Int?` to `POI.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt`
  - Action: Add `val reviewCount: Int? = null` after `val priceRange: String? = null` (line 24). Default `null` preserves backward compat for all existing POI construction sites.
  - Notes: `POI` is `@Serializable` — no serialization changes needed; default null fields are handled by kotlinx.serialization.

- [ ] **T3: Create `PlacesProvider` interface**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/PlacesProvider.kt` (new)
  - Action:
    ```kotlin
    package com.harazone.domain.provider
    import com.harazone.domain.model.POI
    interface PlacesProvider {
        suspend fun enrichPoi(poi: POI): Result<POI>
    }
    ```

- [ ] **T4: Extend `ApiKeyProvider` and `BuildKonfigApiKeyProvider`**
  - Files: `domain/provider/ApiKeyProvider.kt`, `data/remote/BuildKonfigApiKeyProvider.kt`
  - Action in `ApiKeyProvider.kt`: add `val placesApiKey: String` after `val geminiApiKey: String`
  - Action in `BuildKonfigApiKeyProvider.kt`: add `override val placesApiKey: String = BuildKonfig.GOOGLE_PLACES_API_KEY`
  - Notes: Depends on T1 (BuildKonfig must have the field before it can be referenced).

- [ ] **T5: Create `places_enrichment_cache.sq`**
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/places_enrichment_cache.sq` (new)
  - Action: Write exactly:
    ```sql
    CREATE TABLE IF NOT EXISTS places_enrichment_cache (
        saved_id TEXT NOT NULL PRIMARY KEY,
        hours TEXT,
        live_status TEXT,
        rating REAL,
        review_count INTEGER,
        price_range TEXT,
        expires_at INTEGER NOT NULL,
        cached_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_places_cache_expires_at
        ON places_enrichment_cache(expires_at);

    getPlacesData:
    SELECT * FROM places_enrichment_cache WHERE saved_id = :saved_id;

    insertOrReplace:
    INSERT OR REPLACE INTO places_enrichment_cache(saved_id, hours, live_status, rating, review_count, price_range, expires_at, cached_at)
    VALUES (:saved_id, :hours, :live_status, :rating, :review_count, :price_range, :expires_at, :cached_at);

    deleteExpired:
    DELETE FROM places_enrichment_cache WHERE expires_at <= :current_time;
    ```
  - Notes: SQLDelight auto-generates `database.places_enrichment_cacheQueries` accessor after rebuild. Run `./gradlew :composeApp:generateCommonMainDatabaseInterface` to verify generation before proceeding to T6.

- [ ] **T6: Create `GooglePlacesProvider.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GooglePlacesProvider.kt` (new)
  - Action: Implement `PlacesProvider` interface:
    ```kotlin
    class GooglePlacesProvider(
        private val httpClient: HttpClient,
        private val apiKeyProvider: ApiKeyProvider,
        private val database: AreaDiscoveryDatabase,
        private val clock: AppClock,
    ) : PlacesProvider {

        private val json = Json { ignoreUnknownKeys = true }
        private val log = Logger.withTag("GooglePlacesProvider")

        override suspend fun enrichPoi(poi: POI): Result<POI> {
            if (poi.latitude == null || poi.longitude == null) return Result.success(poi)
            if (poi.name.length < 6) return Result.success(poi) // too short to match reliably
            return try {
                // Lazy cache cleanup (prevents unbounded growth)
                database.places_enrichment_cacheQueries.deleteExpired(clock.nowMs())
                // Cache check
                val cached = database.places_enrichment_cacheQueries
                    .getPlacesData(poi.savedId).executeAsOneOrNull()
                if (cached != null && cached.expires_at > clock.nowMs()) {
                    return Result.success(applyCache(poi, cached))
                }
                // Fetch
                val responseText = fetchPlacesData(poi)
                val enriched = parsePlacesResponse(responseText, poi)
                // Write cache only on confident match (reviewCount non-null)
                if (enriched.reviewCount != null) {
                    val now = clock.nowMs()
                    database.places_enrichment_cacheQueries.insertOrReplace(
                        saved_id = poi.savedId,
                        hours = enriched.hours,
                        live_status = enriched.liveStatus,
                        rating = enriched.rating?.toDouble(),
                        review_count = enriched.reviewCount.toLong(),
                        price_range = enriched.priceRange,
                        expires_at = now + CACHE_TTL_MS,
                        cached_at = now,
                    )
                }
                Result.success(enriched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Places enrichment failed for '${poi.name}'" }
                Result.success(poi)
            }
        }

        private suspend fun fetchPlacesData(poi: POI): String {
            // Use serializable data class — NOT string interpolation — to avoid malformed JSON
            // from names with quotes, backslashes, or special chars (e.g. "McDonald's", "L'Opera")
            val request = PlacesRequest(
                textQuery = poi.name,
                locationBias = LocationBias(Circle(LatLng(poi.latitude!!, poi.longitude!!)))
            )
            return httpClient.post(PLACES_SEARCH_URL) {
                header("X-Goog-Api-Key", apiKeyProvider.placesApiKey)
                header("X-Goog-FieldMask", FIELD_MASK)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(request))
            }.bodyAsText()
        }

        internal fun parsePlacesResponse(responseText: String, poi: POI): POI {
            val root = json.parseToJsonElement(responseText).jsonObject
            val places = root["places"]?.jsonArray ?: return poi
            if (places.isEmpty()) return poi

            val place = places[0].jsonObject
            val displayName = place["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return poi

            // Token match: normalize, split into ≥3-char tokens, require all shorter-name tokens
            // present in longer-name tokens. Combined with name.length < 6 guard above, this
            // eliminates single-word false positives (e.g. "Park" ← "Hyde Park Hotel" still
            // passes token check, but is blocked by the length < 6 guard since "Park" has 4 chars)
            if (!isConfidentMatch(poi.name, displayName)) return poi

            val currentHours = place["currentOpeningHours"]?.jsonObject
            val regularHours = place["regularOpeningHours"]?.jsonObject
            val openNow = currentHours?.get("openNow")?.jsonPrimitive?.booleanOrNull
            // Use currentOpeningHours.weekdayDescriptions first; fall back to regularOpeningHours
            val weekdays = (currentHours ?: regularHours)?.get("weekdayDescriptions")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content }

            // Safe cast for primitive fields — avoids IllegalArgumentException if Gemini/Places
            // ever returns an unexpected object type for these fields
            val rating = (place["rating"] as? JsonPrimitive)?.doubleOrNull
            val reviewCount = (place["userRatingCount"] as? JsonPrimitive)?.intOrNull
            val priceLevel = (place["priceLevel"] as? JsonPrimitive)?.contentOrNull

            return poi.copy(
                liveStatus = when (openNow) { true -> "open"; false -> "closed"; null -> poi.liveStatus },
                hours = if (weekdays != null) weekdays.joinToString("\n") else poi.hours,
                rating = rating?.toFloat() ?: poi.rating,
                reviewCount = reviewCount,
                priceRange = mapPriceLevel(priceLevel) ?: poi.priceRange,
            )
        }

        internal fun isConfidentMatch(poiName: String, displayName: String): Boolean {
            val normalize = { s: String -> s.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim() }
            val poiTokens = normalize(poiName).split(" ").filter { it.length >= 3 }.toSet()
            val dispTokens = normalize(displayName).split(" ").filter { it.length >= 3 }.toSet()
            if (poiTokens.isEmpty() || dispTokens.isEmpty()) return false
            val (shorter, longer) = if (poiTokens.size <= dispTokens.size) poiTokens to dispTokens else dispTokens to poiTokens
            return shorter.all { it in longer }
        }

        private fun applyCache(poi: POI, cached: Places_enrichment_cache): POI = poi.copy(
            hours = cached.hours ?: poi.hours,
            liveStatus = cached.live_status ?: poi.liveStatus,
            rating = cached.rating?.toFloat() ?: poi.rating,
            // Defensive ?: 0 — row in cache = Places-verified = chip shows; count display gates on > 0
            reviewCount = cached.review_count?.toInt() ?: 0,
            priceRange = cached.price_range ?: poi.priceRange,
        )

        internal fun mapPriceLevel(priceLevel: String?): String? = when (priceLevel) {
            "PRICE_LEVEL_FREE" -> "Free"
            "PRICE_LEVEL_INEXPENSIVE" -> "$"
            "PRICE_LEVEL_MODERATE" -> "$$"
            "PRICE_LEVEL_EXPENSIVE" -> "$$$"
            "PRICE_LEVEL_VERY_EXPENSIVE" -> "$$$$"
            else -> null
        }

        companion object {
            private const val PLACES_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
            private const val FIELD_MASK = "places.id,places.displayName,places.currentOpeningHours,places.regularOpeningHours,places.rating,places.userRatingCount,places.priceLevel"
            internal const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        }
    }
    ```
  - Notes: `Places_enrichment_cache` is the SQLDelight-generated type. Use `io.ktor.client.request.post`, `io.ktor.client.request.header`, `io.ktor.client.request.setBody`, `io.ktor.http.ContentType`. Import `io.ktor.client.statement.bodyAsText`. Mark `parsePlacesResponse`, `mapPriceLevel`, and `isConfidentMatch` as `internal` for testability. Define the request data classes (`PlacesRequest`, `LocationBias`, `Circle`, `LatLng`) as `@Serializable private data class` inside the `GooglePlacesProvider` class body (or as file-private top-level if preferred). Import `kotlinx.serialization.json.JsonPrimitive` for the safe cast.

- [ ] **T8: Add `enrichPoisWithPlaces()` and `enrichPois()` wrapper to `AreaRepositoryImpl`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt`
  - Action 1: Add `private val placesProvider: PlacesProvider` as a constructor parameter after `wikipediaImageRepository`
  - Action 2: Add to companion object: `private const val PLACES_MAX_CONCURRENT_REQUESTS = 3`
  - Action 3: Add private function after `enrichPoisWithImages()`:
    ```kotlin
    private suspend fun enrichPoisWithPlaces(pois: List<POI>): List<POI> = coroutineScope {
        val semaphore = Semaphore(PLACES_MAX_CONCURRENT_REQUESTS)
        pois.map { poi ->
            async {
                semaphore.withPermit {
                    // enrichPoi() catches all non-cancellation exceptions internally and
                    // returns Result.success(poi) — the outer call cannot throw. No try/catch needed.
                    placesProvider.enrichPoi(poi).getOrDefault(poi)
                }
            }
        }.awaitAll()
    }
    ```
  - Action 4: Add private wrapper after `enrichPoisWithPlaces()`:
    ```kotlin
    private suspend fun enrichPois(pois: List<POI>): List<POI> {
        val imageEnriched = enrichPoisWithImages(pois)
        return enrichPoisWithPlaces(imageEnriched)
    }
    ```
  - Action 5: Replace all 4 `enrichPoisWithImages(...)` call sites with `enrichPois(...)` (search for `enrichPoisWithImages` in the file — exactly 4 occurrences). Do NOT rename `enrichPoisWithImages` itself; keep it as a private helper called by `enrichPois`.
  - Notes: Import `com.harazone.domain.provider.PlacesProvider`. The `Result.getOrDefault(poi)` call requires no extra import — it's a stdlib extension.

- [ ] **T7: Write `GooglePlacesProviderTest.kt`**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GooglePlacesProviderTest.kt` (new)
  - Action: Write tests covering:
    1. `parsePlacesResponse_returns_enriched_poi_on_confident_match` — provide JSON with matching `displayName.text`, assert `reviewCount`, `rating`, `liveStatus`, `priceRange`, `hours` are applied
    2. `parsePlacesResponse_returns_original_poi_on_name_mismatch` — `displayName.text = "Completely Different Place"`, assert returned POI equals original
    3. `parsePlacesResponse_returns_original_poi_on_empty_places_array` — `{ "places": [] }`, assert returned POI equals original
    4. `parsePlacesResponse_openNow_true_sets_liveStatus_open`
    5. `parsePlacesResponse_openNow_false_sets_liveStatus_closed`
    6. `parsePlacesResponse_openNow_null_preserves_gemini_liveStatus`
    7. `parsePlacesResponse_uses_regularOpeningHours_when_currentOpeningHours_absent` — no `currentOpeningHours`, has `regularOpeningHours.weekdayDescriptions`; assert `poi.hours` is populated
    8. `mapPriceLevel_maps_all_levels_correctly` — assert each of the 5 levels + null
    9. `isConfidentMatch_returns_true_for_token_subset_match` — "Kyoto Tower" matches "Kyoto Tower Observatory"
    10. `isConfidentMatch_returns_false_for_no_token_overlap` — "Sakura Cafe" vs "Tokyo Ramen House"
    11. `enrichPoi_skips_poi_with_name_shorter_than_6_chars_without_http_call` — name = "Dojo"; MockEngine that errors if called; assert Result.success(originalPoi)
    12. `enrichPoi_returns_cached_result_within_24h` — use in-memory SQLDelight driver; populate cache; MockEngine that errors if called; assert cached values applied
    13. `enrichPoi_network_failure_returns_original_poi` — MockEngine returns 500; assert Result.success(originalPoi)
    14. `enrichPoi_poi_without_coords_returns_original_immediately` — no HTTP call, no cache check
    15. `enrichPoi_calls_deleteExpired_before_cache_read` — verify cleanup runs (use FakeClock + spy on DB calls if possible, else assert via cache entry that expired is not returned)
  - Notes: Mark `parsePlacesResponse`, `mapPriceLevel`, and `isConfidentMatch` as `internal` in T6 to enable direct testing. Use in-memory SQLDelight driver (follow existing DB test pattern if present, else use `JdbcSqliteDriver("jdbc:sqlite::memory:")` for JVM tests). Use `FakeClock` for time control.

- [ ] **T9: Register `PlacesProvider` in `DataModule.kt` and update `AreaRepositoryImpl` binding**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt`
  - Action 1: Add import `com.harazone.data.remote.GooglePlacesProvider` and `com.harazone.domain.provider.PlacesProvider`
  - Action 2: Add before the `AreaRepository` binding: `single<PlacesProvider> { GooglePlacesProvider(get(), get(), get(), get()) }`
  - Action 3: Update `AreaRepositoryImpl` binding to include `placesProvider = get()`:
    ```kotlin
    single<AreaRepository> {
        AreaRepositoryImpl(
            aiProvider = get(),
            database = get(),
            scope = get(named("appScope")),
            clock = get(),
            connectivityObserver = { get<ConnectivityMonitor>().observe() },
            wikipediaImageRepository = get(),
            placesProvider = get(),
        )
    }
    ```
  - Notes: `get()` for `GooglePlacesProvider` resolves `HttpClient`, `ApiKeyProvider`, `AreaDiscoveryDatabase`, `AppClock` in that constructor order.

- [ ] **T10: Update `AiDetailPage.kt` — hours, reviewCount, "Verified by Google" chip**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`
  - Action 1: In the rating `Text` composable (~line 513), change `text = " ${poi.rating}"` to:
    `text = if ((poi.reviewCount ?: 0) > 0) " ${poi.rating} (${poi.reviewCount})" else " ${poi.rating}"` — count shown only when > 0; chip still shows for reviewCount = 0
  - Action 2: After the closing `}` of the priceRange block (~line 535), add:
    ```kotlin
    // Hours (maxLines=2 prevents 7-line weekday schedule from breaking layout)
    // TODO(BACKLOG-LOW): replace with "Today: X (tap for full schedule)" affordance post-beta
    if (poi.hours != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = poi.hours,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B6B6B),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // Verified by Google chip
    if (poi.reviewCount != null) {
        Spacer(Modifier.height(6.dp))
        VerifiedByGoogleChip()
    }
    ```
  - Action 3: Add private composable at the bottom of the file (before the last closing brace / after `BuzzMeter`):
    ```kotlin
    @Composable
    private fun VerifiedByGoogleChip() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFFF1F1F1), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(
                Icons.Default.Verified,
                contentDescription = null,
                tint = Color(0xFF4285F4),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Verified by Google",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5F6368),
            )
        }
    }
    ```
  - Notes: If `Icons.Default.Verified` is not available in the material icons set used, use `Icons.Default.CheckCircle` as fallback. `hours` may be multi-line (weekday descriptions are newline-separated); `Text` will wrap naturally.

- [ ] **T11: Update Gemini pin-only prompt to include `cc` and `lg` fields**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: In `buildPinOnlyPrompt()`, update the `Schema:` line to add `"cc":"¥ · ~149 JPY/USD","lg":"日本語 · Japanese"` as top-level JSON keys alongside `vibes`, `pois`, `ah`. The `~` prefix signals approximation to the user. Example:
    `Schema: {"cc":"¥ · ~149 JPY/USD","lg":"日本語 · Japanese","vibes":[...],"pois":[...],"ah":[...]}`
  - Action: Add two new rules in the `Rules:` block:
    - `cc: Currency symbol + approximate USD rate prefixed with ~ (e.g. "¥ · ~149 JPY/USD"). Omit cc entirely for USD-based regions. Approximate is fine — use current knowledge.`
    - `lg: Primary local language in format "NativeName · EnglishName" (e.g. "日本語 · Japanese"). Omit lg entirely for English-speaking regions.`
  - Notes: Only `buildPinOnlyPrompt` — this is the initial area portrait prompt that establishes area-level context. Background batch and enrichment prompts are POI-level and should not be modified.

- [ ] **T12: Add `currencyText`/`languageText` to `BucketUpdate.PortraitComplete`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt`
  - Action: Update `PortraitComplete` data class:
    ```kotlin
    data class PortraitComplete(
        val pois: List<POI>,
        val areaHighlights: List<String> = emptyList(),
        val currencyText: String? = null,
        val languageText: String? = null,
    ) : BucketUpdate()
    ```
  - Notes: Default null values ensure all existing `BucketUpdate.PortraitComplete(pois = ...)` call sites compile without change. Only `GeminiResponseParser` (or `GeminiAreaIntelligenceProvider`) and `AreaRepositoryImpl` re-emissions need updating.

- [ ] **T13: Extract `cc`/`lg` in `GeminiResponseParser` and propagate through `GeminiAreaIntelligenceProvider` + `AreaRepositoryImpl`**
  - **File 1: `GeminiResponseParser.kt`** — two locations where `BucketUpdate.PortraitComplete` is constructed:
    - **Non-streaming path (~line 406):** `updates.add(BucketUpdate.PortraitComplete(poisResult.pois, areaHighlights = poisResult.areaHighlights))` — update to extract and pass `cc`/`lg`
    - **Streaming path (`StreamingParser.finish()`, ~line 617):** `results.add(BucketUpdate.PortraitComplete(poisResult.pois, areaHighlights = poisResult.areaHighlights))` — same update
  - In both locations, extract using safe cast (avoids crash if Gemini returns an unexpected type):
    ```kotlin
    val currencyText = (root["cc"] as? JsonPrimitive)?.contentOrNull
    val languageText = (root["lg"] as? JsonPrimitive)?.contentOrNull
    ```
    Then: `BucketUpdate.PortraitComplete(pois = ..., areaHighlights = ..., currencyText = currencyText, languageText = languageText)`
  - **File 2: `GeminiAreaIntelligenceProvider.kt`** — two Stage 2 `PortraitComplete` emissions that originate from enrichment (not from the `cc`/`lg`-bearing Stage 1 prompt). Pass `currencyText = null, languageText = null` explicitly:
    - **Line ~204:** `send(BucketUpdate.PortraitComplete(enrichedPois))` — add `currencyText = null, languageText = null`
    - **Line ~209:** the multi-line `send(BucketUpdate.PortraitComplete(pois = enriched.map { ... }))` — add `currencyText = null, languageText = null`
  - **File 3: `AreaRepositoryImpl.kt`** — wherever it re-emits `PortraitComplete` with enriched POIs (from an upstream `update: BucketUpdate.PortraitComplete`), propagate the fields:
    ```kotlin
    emit(BucketUpdate.PortraitComplete(
        pois = resolveTileRefs(enriched),
        areaHighlights = update.areaHighlights,
        currencyText = update.currencyText,
        languageText = update.languageText,
    ))
    ```
  - Cache-hit `PortraitComplete` constructions (no upstream event) use `currencyText = null, languageText = null` — intentional (Decision 10).

- [ ] **T14: Add `CurrencyContext` and `LanguageContext` to `MetaLine.kt`; update `buildMetaLines()`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/MetaLine.kt`
  - Action 1: Add two new sealed class variants after `RemoteContext`:
    ```kotlin
    /** Priority 2 — local currency + exchange rate. Teal text. Remote areas only. */
    data class CurrencyContext(val text: String) : MetaLine(2)

    /** Priority 2 — primary local language. Teal text. Remote areas only. */
    data class LanguageContext(val text: String) : MetaLine(2)
    ```
  - Action 2: Add to the `val MetaLine.text` extension property:
    ```kotlin
    is MetaLine.CurrencyContext -> text
    is MetaLine.LanguageContext -> text
    ```
  - Action 3: Add to `displayColor()`:
    ```kotlin
    is MetaLine.CurrencyContext -> Color(0xFF26A69A)  // same teal as RemoteContext
    is MetaLine.LanguageContext -> Color(0xFF26A69A)
    ```
  - Action 4: Add to `isFixed()` — both return `false` (they rotate).
  - Action 5: Update `buildMetaLines()` signature to add:
    ```kotlin
    currencyText: String? = null,
    languageText: String? = null,
    ```
  - Action 6: Inside `buildMetaLines()`, in the Priority 2 block (after `RemoteContext`):
    ```kotlin
    if (isRemote && currencyText != null) {
        lines.add(MetaLine.CurrencyContext(currencyText))
    }
    if (isRemote && languageText != null) {
        lines.add(MetaLine.LanguageContext(languageText))
    }
    ```
  - Notes: Default param values ensure all existing `buildMetaLines(...)` call sites compile without change.

- [ ] **T15: Add `areaCurrencyText`/`areaLanguageText` to `MapUiState.Ready`; update `MapViewModel`**
  - File 1: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Add to `MapUiState.Ready` data class (after `activeVibeFilters`):
    ```kotlin
    val areaCurrencyText: String? = null,
    val areaLanguageText: String? = null,
    ```
  - File 2: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action 1: In the `BucketUpdate.PortraitComplete` handler, extract and store:
    ```kotlin
    is BucketUpdate.PortraitComplete -> {
        // existing POI handling...
        _state.update { s ->
            if (s is MapUiState.Ready) s.copy(
                // existing fields...
                areaCurrencyText = update.currencyText,
                areaLanguageText = update.languageText,
            ) else s
        }
    }
    ```
  - Action 2: In the `buildMetaLines(...)` call site in `MapViewModel`, pass:
    ```kotlin
    currencyText = state.areaCurrencyText,
    languageText = state.areaLanguageText,
    ```
  - Notes: `MapViewModel` already computes `isRemote` to pass to `buildMetaLines()` — the new params sit alongside existing params in that same call.

- [ ] **T16: Write `GeminiResponseParserTest` cc/lg cases + `MetaLine` currency/language tests**
  - File 1: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiResponseParserTest.kt` (extend existing)
  - Actions — add test cases:
    1. `parsePinsAndPortrait_extracts_cc_and_lg_from_top_level_json` — JSON includes `"cc":"¥ · ~149 JPY/USD","lg":"日本語 · Japanese"`; assert `PortraitComplete.currencyText` and `.languageText` are set
    2. `parsePinsAndPortrait_returns_null_cc_and_lg_when_fields_absent` — JSON has no `cc`/`lg`; assert both null
    3. `parsePinsAndPortrait_returns_null_when_cc_is_unexpected_type` — `"cc": {"object": true}`; assert `currencyText = null` (safe cast, no crash)
  - File 2: `composeApp/src/commonTest/kotlin/com/harazone/domain/model/MetaLineTest.kt` (new or extend)
  - Actions — add test cases:
    4. `buildMetaLines_includes_currency_and_language_lines_when_remote` — `isRemote = true`, `currencyText = "¥ · ~149 JPY/USD"`, `languageText = "日本語 · Japanese"`; assert both `CurrencyContext` and `LanguageContext` in result
    5. `buildMetaLines_excludes_currency_and_language_lines_when_not_remote` — `isRemote = false`, values set; assert neither line present
    6. `buildMetaLines_excludes_currency_line_when_null_even_if_remote` — `isRemote = true`, `currencyText = null`; assert no `CurrencyContext`

---

## Acceptance Criteria

- [ ] **AC1:** Given a POI with name and coordinates, when the Places API returns a confident match with `openNow=true`, then `poi.liveStatus = "open"` and `poi.reviewCount` is non-null (Places source confirmed).

- [ ] **AC2:** Given a POI with Places-verified data, when the detail card is opened, then a "Verified by Google" chip is visible below the hours/price fields.

- [ ] **AC3:** Given a POI with `reviewCount > 0`, when the detail card rating row renders, then the rating displays as "4.2 (1,840)". Given `reviewCount = 0`, the chip shows but no count is appended to the rating.

- [ ] **AC4:** Given a POI where `poi.hours != null` (from either Gemini or Places), when the detail card is opened, then the hours text is rendered below the price range.

- [ ] **AC5:** Given the Places API returns HTTP 500 or throws a network exception, when enrichment runs, then the original POI is returned unchanged and no error is surfaced to the UI.

- [ ] **AC6:** Given the Places API returns a result whose token set does not satisfy the confident-match rule, OR `poi.name.length < 6`, when enrichment is attempted, then the original POI is returned unchanged (Gemini values preserved) and no cache entry is written.

- [ ] **AC7:** Given a POI has been enriched in the last 24h (cache hit), when `enrichPoi()` is called again, then no HTTP call is made and the cached values are applied to the POI.

- [ ] **AC8:** Given cached Places data is older than 24h (expired), when `enrichPoi()` is called, then a fresh HTTP request is made and the cache is updated.

- [ ] **AC9:** Given a POI with `latitude = null` or `longitude = null`, when `enrichPoi()` is called, then the original POI is returned immediately without any network call.

- [ ] **AC10:** Given the Places API returns `priceLevel = "PRICE_LEVEL_MODERATE"`, when enrichment applies the result, then `poi.priceRange = "$$"`.

- [ ] **AC11:** Given `openNow = null` in the Places response, when enrichment applies the result, then the existing `poi.liveStatus` (Gemini value) is preserved.

- [ ] **AC12:** Given a fresh POI load (cache miss, API succeeds), when Places enrichment completes, then the enriched values are written to `places_enrichment_cache` and POI is re-emitted via `BucketUpdate.BackgroundEnrichmentComplete`.

- [ ] **AC13:** Given a fresh Gemini area portrait for a remote area (e.g. Tokyo), when the portrait completes, then `PortraitComplete.currencyText = "¥ · ~149 JPY/USD"` (with `~` prefix) and `PortraitComplete.languageText = "日本語 · Japanese"` are set from the parsed `cc`/`lg` JSON fields.

- [ ] **AC14:** Given `areaCurrencyText` and `areaLanguageText` are set in `MapUiState.Ready` and `isRemote = true`, when `buildMetaLines()` runs, then both `MetaLine.CurrencyContext` and `MetaLine.LanguageContext` are present in the returned list and visible in the `RotatingMetaTicker`.

- [ ] **AC15:** Given the user is browsing their local/home area (`isRemote = false`), when `buildMetaLines()` runs, then no `CurrencyContext` or `LanguageContext` lines are included regardless of whether those values are set.

- [ ] **AC16:** Given a Gemini response for a US city (USD region), when parsed, then `currencyText = null` (Gemini omits `cc`) and no currency line appears in the ticker.

- [ ] **AC17:** Given the app loads an area from POI cache (no fresh Gemini call), then `areaCurrencyText = null` and `areaLanguageText = null`, and no currency/language meta lines appear (no error, no placeholder).

- [ ] **AC18:** Given an existing installed app with a prior database schema (no `places_enrichment_cache` table), when the new version launches, then `CREATE TABLE IF NOT EXISTS` creates the table successfully and the app does not crash on upgrade. Implementer must verify `DatabaseDriverFactory` handles additive schema additions without a hard version bump, or increment the schema version accordingly.

---

## Additional Context

### Dependencies

- **Google Places API (New)** — API key required; enable "Places API (New)" in Google Cloud Console. Note: "Places API (New)" and "Places API" (legacy) are separate products — ensure the correct one is enabled.
- **No new library dependencies** — uses existing Ktor `HttpClient` and kotlinx.serialization.
- **SQLDelight schema change** — new `.sq` file triggers schema migration. Since `AreaDiscoveryDatabase` uses `CREATE TABLE IF NOT EXISTS` in all existing tables, and the new table is additive, no explicit migration version is needed for development builds. Verify existing `DatabaseDriverFactory` creates the DB without a migration version bump.
- **Blocked by:** Home Screen Redesign PR must merge first — both touch `MapViewModel.kt` / `AreaRepositoryImpl.kt`. Implement only after that PR is merged to avoid conflicts.

### Testing Strategy

**Unit tests (T10):**
- `parsePlacesResponse` is `internal` — directly testable without HTTP
- `mapPriceLevel` is `internal` — directly testable
- Full `enrichPoi()` flow tested via `MockEngine` (Ktor test pattern from `FcdoAdvisoryProviderTest`)
- Cache behavior via `FakeClock` time manipulation

**Manual testing steps:**
1. Build and run on Android device
2. Open a map area with restaurants/cafes (high Places coverage)
3. Tap a POI → detail card opens with Gemini values initially
4. After ~2-3s, detail card should update with verified data + "Verified by Google" chip
5. Tap same POI again → chip appears immediately (cache hit, no delay)
6. Revoke network or use wrong API key → detail card shows Gemini values without chip, no crash
7. Search for a remote foreign area (e.g. "Tokyo", "Paris", "Bangkok") → within 3s of portrait loading, currency and language lines appear in the Discovery Header meta ticker
8. Search for a US city or English-speaking city → no currency/language lines in ticker (Gemini omits them)
9. Kill and relaunch app, re-open same remote area → if cache hit, no currency/language lines; if fresh fetch, they reappear

### Notes

- **Quota awareness:** Places API (New) has per-request pricing. At beta scale this is negligible, but implement `PLACES_MAX_CONCURRENT_REQUESTS = 3` to throttle burst requests.
- **`hours` multi-line display:** `weekdayDescriptions` returns 7 strings (Mon–Sun). A `Text` composable will wrap all 7 lines. For beta this is acceptable; post-beta consider an expandable row or showing today's hours only.
- **Empty-key guard:** If `GOOGLE_PLACES_API_KEY` is `""` (not set in local.properties), the API call will return 403. `GooglePlacesProvider` will catch this as a network exception and silently return the original POI. No special guard needed — silent fallback handles it.
- **Future:** Cache "no match" results with a `matched = false` flag to avoid redundant API calls for POIs that consistently don't match (e.g. Gemini-invented POIs with no real Places equivalent). Out of scope for now.
- **Future:** Persist `currencyText`/`languageText` in a simple area-keyed cache (e.g. `UserPreferencesRepository`) so they survive cache hits and app restarts. Out of scope for beta.
- **Currency precision:** Gemini provides approximate exchange rates. Rate may be months stale. Acceptable for context (user needs to know "yen not dollars", not the precise rate). If accuracy matters post-beta, wire to a live exchange rate API.
- **`PortraitComplete` construction sites (confirmed):** `GeminiResponseParser` constructs it at lines ~406 (non-streaming) and ~617 (streaming `finish()`). `GeminiAreaIntelligenceProvider` constructs it at lines ~204 and ~209 (Stage 2 only — these receive `currencyText = null, languageText = null`). No tracing needed; all sites are documented in T13.
