---
title: 'Detail Page V2 — Layout + Tickers + Links (Story A)'
slug: 'detail-page-v2-story-a'
created: '2026-03-22'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin', 'Compose Multiplatform', 'KMP', 'SQLDelight', 'Ktor', 'Koin', 'Gemini API', 'Google Places API v3', 'Foursquare Places API v3', 'Material 3']
files_to_modify:
  - composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/PoiMatchUtils.kt (new)
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GooglePlacesProvider.kt
  - composeApp/src/commonMain/sqldelight/com/harazone/data/local/places_enrichment_cache.sq
  - composeApp/src/commonMain/sqldelight/com/harazone/data/local/foursquare_social_cache.sq (new)
  - composeApp/src/commonMain/kotlin/com/harazone/domain/provider/ApiKeyProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/BuildKonfigApiKeyProvider.kt
  - composeApp/build.gradle.kts
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/FoursquareProvider.kt (new)
  - composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt
  - composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt
  - composeApp/src/commonMain/composeResources/values/strings.xml
code_patterns:
  - 'SQLDelight migrations: create migrations/N.sqm, bump nothing else — SQLDelight auto-detects by filename number'
  - 'BuildKonfig fields added in build.gradle.kts defaultConfigs block + local.properties key'
  - 'Koin DI: single<Interface> { Impl(get(), get(), ...) } in DataModule.kt'
  - 'enrichPois() chain in AreaRepositoryImpl: images → places → social'
  - 'POI.mergeFrom() is the canonical enrichment merge pattern — always update it when adding fields'
  - 'Material 3 theme: DarkColorScheme / LightColorScheme in Theme.kt, isSystemInDarkTheme() in AreaDiscoveryTheme'
  - 'PlatformBackHandler for every new dismissible overlay; inner handler must be enabled only when overlay is open, outer page handler must be disabled while inner is active'
test_patterns:
  - 'GooglePlacesProviderTest in androidUnitTest uses JdbcSqliteDriver — add test cases for new fields'
  - 'Unit test FoursquareProvider parse logic with mock JSON responses'
---

# Tech-Spec: Detail Page V2 — Layout + Tickers + Links (Story A)

**Created:** 2026-03-22

## Overview

### Problem Statement

`AiDetailPage` has a dense, flat info layout (160dp hero, sequential text blocks) with no visual hierarchy distinguishing safety context from operational data. Three bugs exist: (1) Bug #10 — the safety section has no "AI-generated safety context" disclaimer label; (2) Bug #11 — the POI detail page reads advisory/area context from the current map state at render time, causing wrong-area data to show when the user navigates back; (3) no mechanism to refresh the AI-generated local tip. Additionally, Google Places doesn't collect website, phone, or Maps link, and no social media links (Instagram/Facebook/Twitter) are surfaced anywhere.

### Solution

Restructure `AiDetailPage` into a new information hierarchy matching the v2 mockup: taller hero → compact rating line → safety ticker (CAUTION+ only) → scrolling info ticker → links+social strip → existing action chips (Story A; Save/Go/Been deferred to Story B) → AI context block (with tip refresh button) → conditional safety card → chat. Fix Bug #11 by introducing `DiscoveryContext` stamped onto the POI at discovery time. Fix Bug #10 by rendering the disclaimer in the safety card. Add a `refreshLocalTip()` to `ChatViewModel`. Expand Google Places field mask. Add new Foursquare Places v3 integration for social handles (with SQLDelight cache). Extract shared POI name-matching utility. Apply Material 3 adaptive theming.

### Scope

**In Scope:**
- New `DiscoveryContext` data class on `POI` (Bug #11 fix)
- Google Places field mask expansion: `websiteUri`, `googleMapsUri`, `internationalPhoneNumber`, `formattedAddress`
- DB migration 13: new columns on `places_enrichment_cache` + new `foursquare_social_cache` table
- NEW Foursquare Places v3 integration for `instagramId`, `facebookId`, `twitterId` with SQLDelight cache (NOTE: brainstorm assumed "existing call" — no Foursquare integration exists; this is a fresh integration)
- Shared `PoiMatchUtils.isConfidentMatch()` extracted from `GooglePlacesProvider` — used by both providers
- `ChatViewModel.refreshLocalTip()` public function
- `AiDetailPage` full layout restructure: hero 250dp, rating line, safety ticker (CAUTION+ only), info ticker, links+social strip, AI context with refresh, safety card with disclaimer (Bug #10), chat
- Overflow menu on hero (⋮) — Share, Show on Map, Report — with correct `PlatformBackHandler` priority
- Hero height increase: 160dp → 250dp
- Material 3 adaptive colors (replace hardcoded `DetailPageLight` / `DetailPageTextDark`)
- Currency/language in info ticker only when POI's country differs from user's home country
- Accessible link icons with `contentDescription` on every `LinkIcon`

**Out of Scope:**
- Save/Go/Been CTA lifecycle (Story B)
- Go bottom sheet (Story B)
- Distance calculation (Story B)
- Backend safety real-time feeds
- Voice input (STT)
- Itinerary/trip planning

---

## Context for Development

### Codebase Patterns

- **KMP Compose**: All UI in `commonMain`. No platform-specific UI code.
- **SQLDelight migrations**: Add a new `N.sqm` file in `migrations/`. Current latest is `12.sqm`. New migration = `13.sqm`. The SQLDelight plugin auto-detects by number. Also update the CREATE TABLE in the `.sq` file.
- **BuildKonfig**: API keys stored in `local.properties`, declared in `build.gradle.kts` `defaultConfigs` block, accessed via `BuildKonfig.FIELD_NAME`.
- **Koin DI**: `DataModule.kt` uses `single<Interface> { Impl(get(), get(), ...) }`. `AreaRepositoryImpl` is wired as `single<AreaRepository>` with named deps.
- **POI enrichment chain**: `AreaRepositoryImpl.enrichPois()` calls `enrichPoisWithImages()` then `enrichPoisWithPlaces()`. Foursquare goes here as `enrichPoisWithSocial()`.
- **Material 3 theme**: `AreaDiscoveryTheme` in `Theme.kt` switches `DarkColorScheme`/`LightColorScheme` via `isSystemInDarkTheme()`. Use `MaterialTheme.colorScheme.surface` / `.onSurface` / `.surfaceVariant` in `AiDetailPage` instead of hardcoded colors.
- **`PlatformBackHandler` priority**: When multiple handlers exist, only the topmost enabled one fires. Pattern: inner handler `enabled = overlayOpen`, outer page handler `enabled = !overlayOpen`. This is CLAUDE.md policy — never leave both enabled simultaneously.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | Data model to extend |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaAdvisory.kt` | `AdvisoryLevel` enum (SAFE/CAUTION/RECONSIDER/DO_NOT_TRAVEL/UNKNOWN) |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GooglePlacesProvider.kt` | Field mask expansion + new field parsing; extract `isConfidentMatch` from here |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/places_enrichment_cache.sq` | Cache schema to extend |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/12.sqm` | Latest migration (pattern: `ALTER TABLE ... ADD COLUMN ...`) |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt` | `enrichPois()` chain + Foursquare hook |
| `composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt` | Koin DI wiring |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/ApiKeyProvider.kt` | Interface — add `foursquareApiKey` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/BuildKonfigApiKeyProvider.kt` | Impl — add `foursquareApiKey = BuildKonfig.FOURSQUARE_API_KEY` |
| `composeApp/build.gradle.kts` | `buildkonfig { defaultConfigs { ... } }` — add `FOURSQUARE_API_KEY` field |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | DiscoveryContext stamp at discovery time — see exact per-update-type guidance in Task 8 |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | `advisory: AreaAdvisory?`, `areaCurrencyText: String?`, `areaLanguageText: String?` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt` | `BucketUpdate.PortraitComplete` has `currencyText`/`languageText`; `VibesReady`/`PinsReady`/`BackgroundBatchReady`/`BackgroundEnrichmentComplete` do NOT |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt` | Add `refreshLocalTip()` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt` | `contextBlurb`, `whyNow`, `localTip`, `isContextLoading` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt` | Full restructure; existing signature `onShowOnMap: (lat: Double, lng: Double) -> Unit` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/LocaleProvider.kt` | `homeCurrencyCode: String`, `languageTag: String` |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | `advisory_ai_disclaimer` at line 194; add `poi_tip_refresh_fallback` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/theme/Color.kt` | `DetailPageLight = Color(0xFFF5F2EF)` — replace with M3 adaptive |
| `_bmad-output/planning-artifacts/mockup-detail-page-v2.html` | Full visual reference — open in browser |

### Technical Decisions

1. **`DiscoveryContext` placement**: Inline in `POI.kt` as a nested `@Serializable data class`. Do NOT create a separate file.

2. **Shared `isConfidentMatch` utility**: Extract into a new `internal object PoiMatchUtils` in `composeApp/src/commonMain/kotlin/com/harazone/data/remote/PoiMatchUtils.kt`. Both `GooglePlacesProvider` and `FoursquareProvider` call `PoiMatchUtils.isConfidentMatch(poiName, displayName)`. Delete the private copy from `GooglePlacesProvider`.

3. **Foursquare API + cache**: New integration. Endpoint: `GET https://api.foursquare.com/v3/places/search?ll={lat},{lng}&query={name}&radius=300&limit=1&fields=name,social_media`. The `radius=300` (metres) prevents matching same-name venues in different parts of the city. Authorization header: `Authorization: {fsq_api_key}` (no "Bearer" prefix). Response path: `results[0].social_media.instagramId`, `.facebookId`, `.twitterId`. Social fields are cached in `foursquare_social_cache` (SQLDelight) with a 7-day TTL — longer than Places (24h) because social handles change rarely. Skip if key is blank (fail-safe). Skip if `poi.instagram != null` (already enriched / cache hit applied before call).

4. **Foursquare API key**: Must be added to `local.properties` as `FOURSQUARE_API_KEY=<key>`. Dev obtains from [developer.foursquare.com](https://developer.foursquare.com). If blank, `FoursquareProvider.enrichPoi()` returns original POI unchanged — no crash, no API call.

5. **`applyCache()` and `DiscoveryContext`**: `GooglePlacesProvider.applyCache()` uses `poi.copy(...)` which does NOT include `discoveryContext` in its params — so `discoveryContext` is naturally preserved from the original `poi`. Explicitly: do NOT add `discoveryContext` to the `applyCache()` copy block. The same applies to `FoursquareProvider`'s cache apply path. Context is set at discovery time in MapViewModel and must never be overwritten by enrichment providers.

6. **DiscoveryContext stamping — exact per-update-type guidance (F5 fix)**:
   - `BucketUpdate.VibesReady` (~line 1587): `allDiscoveredPois = update.pois` — `VibesReady` has NO `currencyText`. Use `s.areaCurrencyText`, `s.areaLanguageText`.
   - `BucketUpdate.PinsReady` (~line 1606): same — no currency fields. Use `s.areaCurrencyText`, `s.areaLanguageText`.
   - `BucketUpdate.PortraitComplete` (~line 1635): HAS `update.currencyText`, `update.languageText` ✓ — use these directly.
   - `BucketUpdate.BackgroundBatchReady` (~line 1680): `allDiscoveredPois = allPois` — no currency fields. Use `s.areaCurrencyText`, `s.areaLanguageText`.
   - `BucketUpdate.BackgroundEnrichmentComplete` (~line 1707): same — use `s.areaCurrencyText`, `s.areaLanguageText`.

7. **Social links display**: Show icons only when field is non-null. All `LinkIcon` composables must have a `contentDescription` param. Use `Icons.Default.Phone` (Material), `Icons.Default.Language` (Material), `Icons.Default.Map` (Material) for the utility links. For brand icons (IG/FB/X) — use letter labels "IG", "FB", "X" with `contentDescription = "Instagram"` / `"Facebook"` / `"X (Twitter)"`. Open links via `LocalUriHandler.current.openUri()`. URLs: `https://instagram.com/{handle}`, `https://facebook.com/{handle}`, `https://x.com/{handle}`.

8. **Safety ticker — only CAUTION+**: Do NOT render `SafetyTicker` for null, SAFE, or UNKNOWN advisory levels — these add visual noise with zero information value. The ticker appears only when `advisoryLevel` is `CAUTION`, `RECONSIDER`, or `DO_NOT_TRAVEL`. This aligns with `SafetyCardSection` behavior.

9. **Currency/language in info ticker**: Show `poi.discoveryContext?.currency` only when it differs from `localeProvider.homeCurrencyCode`. Show language only when `localeProvider.languageTag.substringAfterLast("-").uppercase()` (2-char check) differs from `poi.discoveryContext?.countryCode`. If derivation yields non-2-char string, show the field.

10. **`refreshLocalTip` fallback**: Do NOT use hardcoded English strings in ViewModels. If `result?.third` is blank/null, keep the existing `localTip` value (set `isContextLoading = false` without overwriting `localTip`). The fallback string `poi_tip_refresh_fallback` ("No new tip — try again later.") is added to `strings.xml` but used in the UI layer (see Task 10), not in the ViewModel.

11. **`onShowOnMap` signature**: The existing `AiDetailPage` signature is `onShowOnMap: (lat: Double, lng: Double) -> Unit`. The overflow menu "Show on map" item must call `onShowOnMap(poi.latitude ?: return, poi.longitude ?: return)`. Do NOT declare a zero-arg lambda anywhere in the detail page refactor.

12. **`PlatformBackHandler` priority in overflow menu**: The detail page has an outer `PlatformBackHandler(enabled = true) { onDismiss() }`. Add `var overflowExpanded by remember { mutableStateOf(false) }` in `PoiDetailHeader`. Set `PlatformBackHandler(enabled = overflowExpanded) { overflowExpanded = false }` immediately after. The outer page handler must be changed to `PlatformBackHandler(enabled = !overflowExpanded) { onDismiss() }`. This ensures back press closes the menu when open, and closes the page only when menu is closed.

13. **`SafetyTicker` truncation**: Do NOT use `?.take(60)` inside the text string — `maxLines=1` + `TextOverflow.Ellipsis` on the `Text` composable handles truncation cleanly with a proper `…` suffix.

14. **DB column naming**: The `places_enrichment_cache` new column for phone is named `international_phone_number` (not `phone`) to match the Places API field name and the POI model field name. All references must be consistent.

---

## Implementation Plan

### Tasks

---

**Task 1 — Add `DiscoveryContext` to `POI.kt` + new fields**

File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt`

Add nested data class **before** the `POI` class declaration (import `AdvisoryLevel` from same package):
```kotlin
@Serializable
data class DiscoveryContext(
    val areaName: String = "",
    val countryCode: String = "",
    val currency: String? = null,
    val language: String? = null,
    val advisoryLevel: AdvisoryLevel? = null,
    val advisoryBlurb: String? = null,
)
```

Add to `POI` data class after existing `reviewCount` field:
```kotlin
val websiteUri: String? = null,
val googleMapsUri: String? = null,
val internationalPhoneNumber: String? = null,
val formattedAddress: String? = null,
val instagram: String? = null,
val facebook: String? = null,
val twitter: String? = null,
val discoveryContext: DiscoveryContext? = null,
```

Update `mergeFrom()` — add after existing fields:
```kotlin
websiteUri = other.websiteUri ?: websiteUri,
googleMapsUri = other.googleMapsUri ?: googleMapsUri,
internationalPhoneNumber = other.internationalPhoneNumber ?: internationalPhoneNumber,
formattedAddress = other.formattedAddress ?: formattedAddress,
instagram = other.instagram ?: instagram,
facebook = other.facebook ?: facebook,
twitter = other.twitter ?: twitter,
// discoveryContext intentionally NOT in mergeFrom — set at creation time in MapViewModel, never overwritten by enrichment
```

---

**Task 2 — Extract shared `PoiMatchUtils`**

New file: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/PoiMatchUtils.kt`

```kotlin
package com.harazone.data.remote

internal object PoiMatchUtils {
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9 ]")

    fun isConfidentMatch(poiName: String, displayName: String): Boolean {
        val normalize = { s: String -> s.lowercase().replace(NON_ALNUM_REGEX, "").trim() }
        val poiTokens = normalize(poiName).split(" ").filter { it.length >= 3 }.toSet()
        val dispTokens = normalize(displayName).split(" ").filter { it.length >= 3 }.toSet()
        if (poiTokens.isEmpty() || dispTokens.isEmpty()) return false
        val (shorter, longer) = if (poiTokens.size <= dispTokens.size) poiTokens to dispTokens else dispTokens to poiTokens
        return shorter.all { it in longer }
    }
}
```

In `GooglePlacesProvider.kt`:
- Delete the existing private `isConfidentMatch()` function and the `NON_ALNUM_REGEX` constant.
- Replace the call at `if (!isConfidentMatch(poi.name, displayName))` with `if (!PoiMatchUtils.isConfidentMatch(poi.name, displayName))`.

---

**Task 3 — DB migration 13**

New file: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/13.sqm`

```sql
-- New columns for Google Places enrichment cache
ALTER TABLE places_enrichment_cache ADD COLUMN website_uri TEXT;
ALTER TABLE places_enrichment_cache ADD COLUMN google_maps_uri TEXT;
ALTER TABLE places_enrichment_cache ADD COLUMN international_phone_number TEXT;
ALTER TABLE places_enrichment_cache ADD COLUMN formatted_address TEXT;

-- New table for Foursquare social media cache
CREATE TABLE IF NOT EXISTS foursquare_social_cache (
    saved_id TEXT NOT NULL PRIMARY KEY,
    instagram TEXT,
    facebook TEXT,
    twitter TEXT,
    expires_at INTEGER NOT NULL,
    cached_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_foursquare_social_expires_at
    ON foursquare_social_cache(expires_at);
```

---

**Task 4 — Update `places_enrichment_cache.sq` schema**

File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/places_enrichment_cache.sq`

1. Add 4 columns to `CREATE TABLE IF NOT EXISTS places_enrichment_cache` (after `image_urls`):
   ```sql
   website_uri TEXT,
   google_maps_uri TEXT,
   international_phone_number TEXT,
   formatted_address TEXT,
   ```

2. Update `insertOrReplace` query:
   ```sql
   insertOrReplace:
   INSERT OR REPLACE INTO places_enrichment_cache(
     saved_id, hours, live_status, rating, review_count, price_range,
     image_url, image_urls,
     website_uri, google_maps_uri, international_phone_number, formatted_address,
     expires_at, cached_at
   )
   VALUES (
     :saved_id, :hours, :live_status, :rating, :review_count, :price_range,
     :image_url, :image_urls,
     :website_uri, :google_maps_uri, :international_phone_number, :formatted_address,
     :expires_at, :cached_at
   );
   ```

---

**Task 5 — Create `foursquare_social_cache.sq`**

New file: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/foursquare_social_cache.sq`

```sql
CREATE TABLE IF NOT EXISTS foursquare_social_cache (
    saved_id TEXT NOT NULL PRIMARY KEY,
    instagram TEXT,
    facebook TEXT,
    twitter TEXT,
    expires_at INTEGER NOT NULL,
    cached_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_foursquare_social_expires_at
    ON foursquare_social_cache(expires_at);

getSocialData:
SELECT * FROM foursquare_social_cache WHERE saved_id = :saved_id;

insertOrReplace:
INSERT OR REPLACE INTO foursquare_social_cache(saved_id, instagram, facebook, twitter, expires_at, cached_at)
VALUES (:saved_id, :instagram, :facebook, :twitter, :expires_at, :cached_at);

deleteExpired:
DELETE FROM foursquare_social_cache WHERE expires_at <= :current_time;
```

---

**Task 6 — Expand `GooglePlacesProvider.kt` field mask + parsing**

File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GooglePlacesProvider.kt`

**6a. Expand `FIELD_MASK` constant:**
```kotlin
private const val FIELD_MASK = "places.id,places.displayName,places.currentOpeningHours," +
    "places.regularOpeningHours,places.rating,places.userRatingCount,places.priceLevel," +
    "places.photos,places.websiteUri,places.googleMapsUri," +
    "places.internationalPhoneNumber,places.formattedAddress"
```

**6b. Extract new fields in `parsePlacesResponse()` after `photoRefs`:**
```kotlin
val websiteUri = place["websiteUri"]?.jsonPrimitive?.contentOrNull
val googleMapsUri = place["googleMapsUri"]?.jsonPrimitive?.contentOrNull
val phone = place["internationalPhoneNumber"]?.jsonPrimitive?.contentOrNull
val address = place["formattedAddress"]?.jsonPrimitive?.contentOrNull
```

**6c. Add to `enriched` POI.copy():**
```kotlin
websiteUri = websiteUri ?: poi.websiteUri,
googleMapsUri = googleMapsUri ?: poi.googleMapsUri,
internationalPhoneNumber = phone ?: poi.internationalPhoneNumber,
formattedAddress = address ?: poi.formattedAddress,
```

**6d. Update `insertOrReplace` call in `enrichPoi()`, add after `image_urls`:**
```kotlin
website_uri = enriched.websiteUri,
google_maps_uri = enriched.googleMapsUri,
international_phone_number = enriched.internationalPhoneNumber,
formatted_address = enriched.formattedAddress,
```

**6e. Update `applyCache()` — add new fields, do NOT add `discoveryContext`:**
```kotlin
websiteUri = cached.website_uri ?: poi.websiteUri,
googleMapsUri = cached.google_maps_uri ?: poi.googleMapsUri,
internationalPhoneNumber = cached.international_phone_number ?: poi.internationalPhoneNumber,
formattedAddress = cached.formatted_address ?: poi.formattedAddress,
// discoveryContext: NOT here — preserved automatically from poi param (poi.copy(...) keeps it)
```

**6f. Remove the private `isConfidentMatch()` and `NON_ALNUM_REGEX` from this class — replaced by `PoiMatchUtils` (Task 2).**

---

**Task 7 — Add Foursquare API key to build config**

**7a.** `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/ApiKeyProvider.kt` — add:
```kotlin
val foursquareApiKey: String
```

**7b.** `composeApp/src/commonMain/kotlin/com/harazone/data/remote/BuildKonfigApiKeyProvider.kt` — add:
```kotlin
override val foursquareApiKey: String = BuildKonfig.FOURSQUARE_API_KEY
```

**7c.** `composeApp/build.gradle.kts` — inside `buildkonfig { defaultConfigs { ... } }`, add after `GOOGLE_PLACES_API_KEY`:
```kotlin
buildConfigField(
    com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
    "FOURSQUARE_API_KEY",
    localProperties.getProperty("FOURSQUARE_API_KEY") ?: project.findProperty("FOURSQUARE_API_KEY")?.toString() ?: ""
)
```
Dev adds `FOURSQUARE_API_KEY=<key>` to `local.properties`.

---

**Task 8 — Create `FoursquareProvider.kt`**

New file: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/FoursquareProvider.kt`

```kotlin
package com.harazone.data.remote

import co.touchlab.kermit.Logger
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.domain.model.POI
import com.harazone.domain.provider.ApiKeyProvider
import com.harazone.util.AppClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

internal class FoursquareProvider(
    private val httpClient: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val log = Logger.withTag("FoursquareProvider")

    suspend fun enrichPoi(poi: POI): Result<POI> {
        if (apiKeyProvider.foursquareApiKey.isBlank()) return Result.success(poi)
        if (poi.latitude == null || poi.longitude == null) return Result.success(poi)
        if (poi.name.length < 6) return Result.success(poi)
        // Idempotency: skip if any social field already populated (cache hit applied upstream)
        if (poi.instagram != null || poi.facebook != null || poi.twitter != null) return Result.success(poi)

        return try {
            // Check cache first
            val cached = database.foursquare_social_cacheQueries
                .getSocialData(poi.savedId).executeAsOneOrNull()
            if (cached != null && cached.expires_at > clock.nowMs()) {
                val anySocial = cached.instagram != null || cached.facebook != null || cached.twitter != null
                return if (anySocial) {
                    Result.success(poi.copy(
                        instagram = cached.instagram,
                        facebook = cached.facebook,
                        twitter = cached.twitter,
                    ))
                } else {
                    Result.success(poi) // cached miss — no social data for this POI
                }
            }

            // Fetch from API
            val responseText = httpClient.get("https://api.foursquare.com/v3/places/search") {
                header("Authorization", apiKeyProvider.foursquareApiKey)
                url {
                    parameters.append("ll", "${poi.latitude},${poi.longitude}")
                    parameters.append("query", poi.name)
                    parameters.append("radius", "300")  // 300m — prevents cross-city false matches
                    parameters.append("limit", "1")
                    parameters.append("fields", "name,social_media")
                }
            }.bodyAsText()

            val root = json.parseToJsonElement(responseText).jsonObject
            val results = root["results"]?.jsonArray ?: return Result.success(poi)
            if (results.isEmpty()) return cacheAndReturn(poi, null, null, null)

            val place = results[0].jsonObject
            val displayName = place["name"]?.jsonPrimitive?.contentOrNull
                ?: return cacheAndReturn(poi, null, null, null)

            if (!PoiMatchUtils.isConfidentMatch(poi.name, displayName)) {
                return cacheAndReturn(poi, null, null, null)
            }

            val social = place["social_media"]?.jsonObject
            val instagram = social?.get("instagramId")?.jsonPrimitive?.contentOrNull
            val facebook = social?.get("facebookId")?.jsonPrimitive?.contentOrNull
            val twitter = social?.get("twitterId")?.jsonPrimitive?.contentOrNull

            cacheAndReturn(poi, instagram, facebook, twitter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Foursquare enrichment failed for '${poi.name}'" }
            Result.success(poi)
        }
    }

    private fun cacheAndReturn(
        poi: POI,
        instagram: String?,
        facebook: String?,
        twitter: String?,
    ): Result<POI> {
        val now = clock.nowMs()
        database.foursquare_social_cacheQueries.insertOrReplace(
            saved_id = poi.savedId,
            instagram = instagram,
            facebook = facebook,
            twitter = twitter,
            expires_at = now + CACHE_TTL_MS,
            cached_at = now,
        )
        return if (instagram != null || facebook != null || twitter != null) {
            Result.success(poi.copy(instagram = instagram, facebook = facebook, twitter = twitter))
        } else {
            Result.success(poi)
        }
    }

    companion object {
        internal const val CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days — social handles change rarely
    }
}
```

---

**Task 9 — Wire Foursquare into `AreaRepositoryImpl` + DI**

**9a.** `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt`

Add constructor param after `placesProvider`:
```kotlin
private val foursquareProvider: FoursquareProvider,
```

Add `enrichPoisWithSocial()` after `enrichPoisWithPlaces()`:
```kotlin
private suspend fun enrichPoisWithSocial(pois: List<POI>): List<POI> = coroutineScope {
    pois.map { poi ->
        async { foursquareProvider.enrichPoi(poi).getOrDefault(poi) }
    }.awaitAll()
}
```

Update `enrichPois()`:
```kotlin
private suspend fun enrichPois(pois: List<POI>): List<POI> {
    val imageEnriched = enrichPoisWithImages(pois)
    val placesEnriched = enrichPoisWithPlaces(imageEnriched)
    return enrichPoisWithSocial(placesEnriched)
}
```

**9b.** `composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt`

Add after `single<PlacesProvider>`:
```kotlin
single { FoursquareProvider(get(), get(), get(), get()) }
```

Update the `single<AreaRepository>` Koin call to pass `foursquareProvider = get()`.

---

**Task 10 — Stamp `DiscoveryContext` in `MapViewModel.kt`**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`

Add private extension function inside the class (near the bottom):
```kotlin
private fun List<POI>.withDiscoveryContext(
    areaName: String,
    advisory: AreaAdvisory?,
    currencyText: String?,
    languageText: String?,
): List<POI> {
    val ctx = DiscoveryContext(
        areaName = areaName,
        countryCode = advisory?.countryCode ?: "",
        currency = currencyText,
        language = languageText,
        advisoryLevel = advisory?.level,
        advisoryBlurb = advisory?.summary,
    )
    return map { it.copy(discoveryContext = ctx) }
}
```

Apply at each `allDiscoveredPois =` site. Use the exact per-update-type guidance — only `PortraitComplete` carries `update.currencyText`/`update.languageText`; all others use `s.areaCurrencyText`/`s.areaLanguageText`:

```kotlin
// BucketUpdate.VibesReady (~line 1587) — no currencyText on this update type
allDiscoveredPois = update.pois.withDiscoveryContext(
    areaName = s.areaName,
    advisory = s.advisory,
    currencyText = s.areaCurrencyText,
    languageText = s.areaLanguageText,
)

// BucketUpdate.PinsReady (~line 1606) — no currencyText on this update type
allDiscoveredPois = update.pois.withDiscoveryContext(
    areaName = s.areaName,
    advisory = s.advisory,
    currencyText = s.areaCurrencyText,
    languageText = s.areaLanguageText,
)

// BucketUpdate.PortraitComplete (~line 1635) — HAS update.currencyText ✓
allDiscoveredPois = (if (s.allDiscoveredPois.size > pois.size) s.allDiscoveredPois else pois)
    .withDiscoveryContext(
        areaName = s.areaName,
        advisory = s.advisory,
        currencyText = update.currencyText,
        languageText = update.languageText,
    )

// BucketUpdate.BackgroundBatchReady (~line 1680) — no currencyText on this update type
allDiscoveredPois = allPois.withDiscoveryContext(
    areaName = s.areaName,
    advisory = s.advisory,
    currencyText = s.areaCurrencyText,
    languageText = s.areaLanguageText,
)

// BucketUpdate.BackgroundEnrichmentComplete (~line 1707) — no currencyText on this update type
allDiscoveredPois = allPois.withDiscoveryContext(
    areaName = s.areaName,
    advisory = s.advisory,
    currencyText = s.areaCurrencyText,
    languageText = s.areaLanguageText,
)
```

---

**Task 11 — Add `poi_tip_refresh_fallback` string resource**

File: `composeApp/src/commonMain/composeResources/values/strings.xml`

Add after `advisory_ai_disclaimer`:
```xml
<string name="poi_tip_refresh_fallback">No new tip available — try again later.</string>
```

---

**Task 12 — Add `refreshLocalTip()` to `ChatViewModel.kt`**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`

Add public function after `openChatForPoiVisit()`:
```kotlin
fun refreshLocalTip(poi: POI, areaName: String) {
    poiContextJob?.cancel()
    _uiState.value = _uiState.value.copy(localTip = null, isContextLoading = true)
    poiContextJob = viewModelScope.launch {
        val hour = com.harazone.ui.components.currentHour()
        val timeHint = when {
            hour in 5..10 -> "morning"
            hour in 11..16 -> "afternoon"
            hour in 17..20 -> "evening"
            else -> "night"
        }
        val result = aiProvider.generatePoiContext(poi.name, poi.type, areaName, timeHint, localeProvider.languageTag)
        val newTip = result?.third?.ifBlank { null }
        // If generation returned nothing, restore the previous tip — do not overwrite with null
        _uiState.value = _uiState.value.copy(
            localTip = newTip ?: _uiState.value.localTip,
            isContextLoading = false,
        )
    }
}
```

Note: no hardcoded English fallback strings in ViewModels. The UI uses `stringResource(Res.string.poi_tip_refresh_fallback)` if `localTip` is null after loading.

---

**Task 13 — Restructure `AiDetailPage.kt`**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`

**13a. Import additions:**
```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.platform.LocalUriHandler
import com.harazone.domain.model.DiscoveryContext
```

**13b. Remove hardcoded color constants:**
- Delete `private val DetailPageTextDark = Color(0xFF2A2A2A)` — replaced with `MaterialTheme.colorScheme.onSurface` at use sites.
- Replace `modifier.background(DetailPageLight)` with `modifier.background(MaterialTheme.colorScheme.surface)` throughout.

**13c. Fix `PlatformBackHandler` priority for overflow menu:**

The existing page-level handler is `PlatformBackHandler(enabled = true) { onDismiss() }`. Change it to:
```kotlin
var overflowExpanded by remember { mutableStateOf(false) }
PlatformBackHandler(enabled = overflowExpanded) { overflowExpanded = false }
PlatformBackHandler(enabled = !overflowExpanded) { onDismiss() }
```
Pass `overflowExpanded` and its setter down to `PoiDetailHeader` so the hero box can control it.

**13d. Rewrite `PoiDetailHeader`** — new structure:

```
PoiDetailHeader:
  HeroBox (250dp)
    - AsyncImage / vibe-gradient fallback
    - Photo count badge bottom-left (hide if ≤ 1 image)
    - Gradient overlay: height 130dp bottom, surface-color opaque end (blends into info below)
    - POI name + type + vibe chip overlaid bottom-left (above gradient)
    - Close button (←) top-left, 36dp circle, black 45% alpha, ArrowBack icon
    - Overflow button (⋮) top-right, 36dp circle, black 45% alpha, MoreVert icon
        → DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false })
           - "Share" → TODO("Share POI")
           - "Show on map" → onShowOnMap(poi.latitude!!, poi.longitude!!); onDismiss()
           - "Report" → TODO("Report POI")
  RatingLine  (padding H=20dp, V=8dp)
  SafetyTicker  (only when advisoryLevel is CAUTION/RECONSIDER/DO_NOT_TRAVEL)
  InfoTicker  (scrollable LazyRow: address · phone · currency? · language?)
  LinksSocialStrip  (Call | Website | Maps | divider | IG | FB | X)
  Action chips  (Visit / Directions / ShowOnMap — unchanged from current)
```

**Hero box — 250dp:**
```kotlin
Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
    // vibe gradient fallback (always rendered behind image)
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
        listOf(vibeColor.copy(0.6f), vibeColor.copy(0.2f)))))
    // hero image
    if (poi.imageUrl != null) AsyncImage(...)
    // bottom gradient — use surface color as opaque end so it blends into content below
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp).background(
        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
    // name + type + vibe chip, bottom-left above gradient
    Column(Modifier.align(Alignment.BottomStart).padding(start=16.dp, bottom=12.dp, end=60.dp)) {
        Text(poi.name, style=titleMedium, color=Color.White, ...)
        Text(poi.type, style=labelMedium, color=Color.White.copy(0.85f))
        if (poi.vibe.isNotEmpty()) VibeChip(poi.vibe, vibeColor)
    }
    // photo count badge
    if (poi.imageUrls.size > 1) { /* "1/${poi.imageUrls.size} photos" — bottom-left, behind name */ }
    // close button
    IconButton(onClick = onDismiss,
        modifier = Modifier.align(Alignment.TopStart).padding(top=54.dp, start=12.dp)
            .size(36.dp).background(Color.Black.copy(0.45f), CircleShape)) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
    }
    // overflow button
    Box(Modifier.align(Alignment.TopEnd).padding(top=54.dp, end=12.dp)) {
        IconButton(onClick = { overflowExpanded = true },
            modifier = Modifier.size(36.dp).background(Color.Black.copy(0.45f), CircleShape)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
        }
        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
            DropdownMenuItem(text = { Text("Share") }, onClick = { overflowExpanded = false; TODO("Share POI") })
            DropdownMenuItem(text = { Text("Show on map") }, onClick = {
                overflowExpanded = false
                if (poi.latitude != null && poi.longitude != null) {
                    onShowOnMap(poi.latitude, poi.longitude)
                    onDismiss()
                }
            })
            DropdownMenuItem(text = { Text("Report") }, onClick = { overflowExpanded = false; TODO("Report POI") })
        }
    }
}
```

**Rating line** (below hero, H=20dp, V=8dp):
```kotlin
Row(Modifier.padding(horizontal=20.dp, vertical=8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    if (poi.rating != null) {
        Icon(Icons.Default.Star, tint=Color(0xFFFFD700), modifier=Modifier.size(14.dp))
        Text("${poi.rating}", style=bodySmall, fontWeight=Bold, color=Color(0xFFFFD700))
        if ((poi.reviewCount ?: 0) > 0)
            Text("(${poi.reviewCount})", style=bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("·", color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (poi.priceRange != null) {
        Text(poi.priceRange, style=bodySmall, color=Color(0xFF3FB950), fontWeight=SemiBold)
        Text("·", color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (poi.liveStatus != null) {
        LiveStatusBadge(poi.liveStatus)
        Text("·", color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (poi.reviewCount != null) VerifiedByGoogleChip()
}
```

**SafetyTicker** (only render for CAUTION/RECONSIDER/DO_NOT_TRAVEL — no green "all clear" ticker):
```kotlin
@Composable
private fun SafetyTicker(context: DiscoveryContext?, modifier: Modifier = Modifier) {
    val level = context?.advisoryLevel ?: return
    val (bgColor, dotColor, text) = when (level) {
        AdvisoryLevel.CAUTION ->
            Triple(Color(0xFFF0C040).copy(alpha=0.08f), Color(0xFFF0C040),
                   "⚠ Exercise caution · ${context.advisoryBlurb ?: context.areaName}")
        AdvisoryLevel.RECONSIDER ->
            Triple(Color(0xFFF08020).copy(alpha=0.08f), Color(0xFFF08020),
                   "⚠ Reconsider travel · ${context.advisoryBlurb ?: context.areaName}")
        AdvisoryLevel.DO_NOT_TRAVEL ->
            Triple(Color(0xFFF47067).copy(alpha=0.10f), Color(0xFFF47067),
                   "🚨 Do not travel · ${context.advisoryBlurb ?: context.areaName}")
        else -> return  // SAFE, UNKNOWN, null — don't render ticker
    }
    val infiniteTransition = rememberInfiniteTransition(label = "safetyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(1f, 0.3f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "safePulse")
    Row(
        modifier = modifier.padding(horizontal=20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal=12.dp, vertical=7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).background(dotColor.copy(alpha=pulseAlpha), CircleShape))
        // No take(60) — maxLines+Ellipsis handles truncation cleanly
        Text(text, style=MaterialTheme.typography.bodySmall, color=dotColor,
             maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
    }
}
```

**InfoTicker** (scrollable `LazyRow` with fade edges — no animated infinite scroll for Story A):
- Items: `formattedAddress` (if non-null), `internationalPhoneNumber` (if non-null, tappable green tel: link), `currency` (if foreign), `language` (if foreign)
- Separated by `·` dividers
- `LazyRow` with gradient fade left/right using `Box` overlay
- Phone item: wrapped in `clickable { uriHandler.openUri("tel:$phone") }`

**LinksSocialStrip** (uses Material vector icons + accessible `contentDescription`):
```kotlin
@Composable
private fun LinksSocialStrip(
    poi: POI,
    onShowOnMap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    // Only render if any link exists
    val hasAny = poi.internationalPhoneNumber != null || poi.websiteUri != null ||
        poi.googleMapsUri != null || poi.instagram != null || poi.facebook != null || poi.twitter != null
    if (!hasAny) return

    Row(modifier.padding(horizontal=20.dp, vertical=10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {

        if (poi.internationalPhoneNumber != null)
            LinkIcon(
                icon = { Icon(Icons.Default.Phone, contentDescription = null, tint=Color(0xFF3FB950), modifier=Modifier.size(18.dp)) },
                bg = Color(0xFF3FB950).copy(0.18f),
                contentDescription = "Call ${poi.name}",
                onClick = { uriHandler.openUri("tel:${poi.internationalPhoneNumber}") }
            )
        if (poi.websiteUri != null)
            LinkIcon(
                icon = { Icon(Icons.Default.Language, contentDescription = null, tint=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.size(18.dp)) },
                bg = MaterialTheme.colorScheme.surfaceVariant,
                contentDescription = "Website",
                onClick = { uriHandler.openUri(poi.websiteUri) }
            )
        if (poi.googleMapsUri != null)
            LinkIcon(
                icon = { Icon(Icons.Default.Map, contentDescription = null, tint=Color(0xFF6AA3F4), modifier=Modifier.size(18.dp)) },
                bg = Color(0xFF4285F4).copy(0.20f),
                contentDescription = "Open in Google Maps",
                onClick = { uriHandler.openUri(poi.googleMapsUri) }
            )

        val hasSocial = poi.instagram != null || poi.facebook != null || poi.twitter != null
        val hasUtility = poi.internationalPhoneNumber != null || poi.websiteUri != null || poi.googleMapsUri != null
        if (hasSocial && hasUtility) {
            Box(Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }

        // Brand icons use letter labels — Material Icons has no brand icons in KMP
        if (poi.instagram != null)
            LinkIcon(
                icon = { Text("IG", style=MaterialTheme.typography.labelSmall, color=Color(0xFFE94D82), fontWeight=FontWeight.Bold) },
                bg = Color(0xFFE1306C).copy(0.20f),
                contentDescription = "Instagram",
                onClick = { uriHandler.openUri("https://instagram.com/${poi.instagram}") }
            )
        if (poi.facebook != null)
            LinkIcon(
                icon = { Text("FB", style=MaterialTheme.typography.labelSmall, color=Color(0xFF5A7FC2), fontWeight=FontWeight.Bold) },
                bg = Color(0xFF4267B2).copy(0.20f),
                contentDescription = "Facebook",
                onClick = { uriHandler.openUri("https://facebook.com/${poi.facebook}") }
            )
        if (poi.twitter != null)
            LinkIcon(
                icon = { Text("X", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, fontWeight=FontWeight.Bold) },
                bg = MaterialTheme.colorScheme.surfaceVariant,
                contentDescription = "X (Twitter)",
                onClick = { uriHandler.openUri("https://x.com/${poi.twitter}") }
            )
    }
}

@Composable
private fun LinkIcon(
    icon: @Composable () -> Unit,
    bg: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(bg, RoundedCornerShape(9.dp))
            .clickable(onClickLabel = contentDescription) { onClick() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
```

**PoiContextBlock update** — add `onRefreshTip` + fallback string:
```kotlin
@Composable
private fun PoiContextBlock(
    contextBlurb: String?,
    whyNow: String?,
    localTip: String?,
    isLoading: Boolean,
    onRefreshTip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

In the `localTip` row:
```kotlin
Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(...).padding(...),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically) {
    Text("💡", modifier=Modifier.clearAndSetSemantics {})
    val tipText = localTip ?: if (!isLoading) stringResource(Res.string.poi_tip_refresh_fallback) else ""
    Text(tipText, style=bodySmall, color=Color(0xFF2E7D32), modifier=Modifier.weight(1f))
    if (onRefreshTip != null && !isLoading) {
        IconButton(onClick = onRefreshTip, modifier=Modifier.size(24.dp)) {
            // Use Text("↻") if Icons.Default.Refresh not available (requires material-icons-extended)
            Text("↻", color=Color(0xFF4CAF50), style=MaterialTheme.typography.bodyMedium)
        }
    }
}
```

**SafetyCardSection** (after AI context, before chat — CAUTION+ only):
```kotlin
@Composable
private fun SafetyCardSection(context: DiscoveryContext?, modifier: Modifier = Modifier) {
    val level = context?.advisoryLevel ?: return
    if (level == AdvisoryLevel.SAFE || level == AdvisoryLevel.UNKNOWN) return
    val (cardBg, dotColor, levelText) = when (level) {
        AdvisoryLevel.CAUTION -> Triple(Color(0xFFF0C040).copy(0.04f), Color(0xFFF0C040), "Exercise Caution")
        AdvisoryLevel.RECONSIDER -> Triple(Color(0xFFF08020).copy(0.06f), Color(0xFFF08020), "Reconsider Travel")
        AdvisoryLevel.DO_NOT_TRAVEL -> Triple(Color(0xFFF47067).copy(0.08f), Color(0xFFF47067), "Do Not Travel")
        else -> return
    }
    Column(modifier.padding(horizontal=20.dp, vertical=14.dp)) {
        Text("TRAVEL ADVISORY",
            style=MaterialTheme.typography.labelSmall.copy(letterSpacing=0.8.sp),
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.padding(bottom=8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cardBg).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Text(levelText, style=bodySmall, color=dotColor, fontWeight=SemiBold)
            }
            if (context.advisoryBlurb != null) {
                Text(context.advisoryBlurb, style=bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Bug #10 fix — AI-generated safety disclaimer
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("ℹ", style=labelSmall, color=MaterialTheme.colorScheme.outline,
                     modifier=Modifier.clearAndSetSemantics {})
                Text(stringResource(Res.string.advisory_ai_disclaimer),
                     style=labelSmall, color=MaterialTheme.colorScheme.outline,
                     fontStyle=FontStyle.Italic)
            }
        }
    }
}
```

**13e. Update `AiDetailPage` LazyColumn item order:**
```
item("header")       → PoiDetailHeader (new)
item("ghost_cta")    → unchanged
item("context")      → PoiContextBlock (add onRefreshTip = { chatViewModel.refreshLocalTip(poi, areaName) })
item("safety_card")  → SafetyCardSection(poi.discoveryContext)
(chat bubbles loop)  → unchanged
item("skeletons")    → unchanged
item("pills")        → unchanged
```

**13f. Update `AiDetailPage` top-level background:**
```kotlin
Box(modifier = modifier.background(MaterialTheme.colorScheme.surface))
```

---

### Acceptance Criteria

**AC1 — DiscoveryContext on POI (Bug #11)**

*Given* an area is discovered with an advisory level,
*When* a POI is added to `allDiscoveredPois`,
*Then* `poi.discoveryContext.advisoryLevel` matches `state.advisory?.level` and `poi.discoveryContext.areaName` matches `state.areaName`.

*Given* the user pans to a new area,
*When* a POI from the previous area is shown in detail,
*Then* `poi.discoveryContext.areaName` shows the POI's original area name (not the current map area).

*Given* `applyCache()` is called in `GooglePlacesProvider` or `FoursquareProvider`,
*When* the cached POI is returned,
*Then* `poi.discoveryContext` is unchanged from the pre-cache value (not null, not overwritten).

**AC2 — Google Places new fields**

*Given* a POI is enriched via Google Places,
*When* the API returns `websiteUri`, `googleMapsUri`, `internationalPhoneNumber`, `formattedAddress`,
*Then* all four fields are non-null on the returned POI.

*Given* the same POI is fetched within 24h,
*When* the cache is valid,
*Then* all four new fields are restored from the cache without an API call.

**AC3 — Foursquare social links + cache**

*Given* a POI has social handles in Foursquare and is within 300m radius,
*When* `FoursquareProvider.enrichPoi()` is called,
*Then* `poi.instagram`/`poi.facebook`/`poi.twitter` are populated.

*Given* the same POI is enriched again within 7 days,
*When* `FoursquareProvider.enrichPoi()` is called,
*Then* the cache is read and no Foursquare API call is made.

*Given* `FOURSQUARE_API_KEY` is blank,
*When* `FoursquareProvider.enrichPoi()` is called,
*Then* the original POI is returned unchanged (no crash, no API call, no cache write).

*Given* a Foursquare search returns a same-name venue more than 300m away,
*When* `FoursquareProvider.enrichPoi()` is called,
*Then* no social handles are attached (radius guard rejects distant result).

**AC4 — Links + Social strip**

*Given* `poi.websiteUri` is non-null,
*When* the user taps the Website icon,
*Then* `poi.websiteUri` opens in the system browser.

*Given* `poi.instagram` is non-null,
*When* the user taps the "IG" icon,
*Then* `https://instagram.com/{poi.instagram}` opens; the icon has `contentDescription = "Instagram"`.

*Given* all link fields are null,
*When* the detail page renders,
*Then* the `LinksSocialStrip` is not rendered at all (no empty row).

**AC5 — Safety ticker (CAUTION+ only)**

*Given* `poi.discoveryContext?.advisoryLevel == CAUTION`,
*When* the detail page renders,
*Then* a yellow pulsing safety ticker is visible below the rating line.

*Given* `poi.discoveryContext` is null, SAFE, or UNKNOWN,
*When* the detail page renders,
*Then* no safety ticker is rendered (zero layout space consumed).

**AC6 — Info ticker**

*Given* `poi.formattedAddress` is non-null,
*When* the detail page renders,
*Then* the address is visible in the info ticker.

*Given* the POI is in the same country as the user (matching currency code),
*When* the detail page renders,
*Then* currency and language are NOT shown in the info ticker.

*Given* the POI is in a different country,
*When* the detail page renders,
*Then* currency and language from `poi.discoveryContext` appear in the info ticker.

**AC7 — Overflow menu + PlatformBackHandler priority**

*Given* the detail page is open,
*When* the user taps the ⋮ button,
*Then* a dropdown shows "Share", "Show on map", "Report".

*Given* the overflow menu is open,
*When* the user presses Android back,
*Then* only the menu dismisses — the detail page stays open.

*Given* the overflow menu is closed,
*When* the user presses Android back,
*Then* the detail page dismisses.

*Given* the user taps "Show on map",
*When* the menu action fires,
*Then* `onShowOnMap(poi.latitude, poi.longitude)` is called (two-arg) and the detail page dismisses.

**AC8 — Local tip refresh**

*Given* the local tip is visible,
*When* the user taps ↻,
*Then* the tip card shows a loading shimmer and a new tip appears after generation.

*Given* the tip is loading,
*When* the user taps ↻ again,
*Then* the previous job is cancelled and a new one starts (no crash).

*Given* Gemini returns a blank/null tip,
*When* `refreshLocalTip` completes,
*Then* the previous tip text is preserved (not replaced with null or error text); the ViewModel does not use hardcoded English strings.

**AC9 — Safety card with disclaimer (Bug #10)**

*Given* `poi.discoveryContext?.advisoryLevel` is CAUTION, RECONSIDER, or DO_NOT_TRAVEL,
*When* the detail page renders,
*Then* a safety card appears after the AI context block with the `advisory_ai_disclaimer` string as an italic footer.

*Given* advisory is SAFE, UNKNOWN, or null,
*When* the detail page renders,
*Then* no safety card is shown.

**AC10 — Theme adaptation**

*Given* the device is in dark mode,
*When* the detail page opens,
*Then* the background uses `MaterialTheme.colorScheme.surface` (dark) — not hardcoded `0xFFF5F2EF`.

*Given* the device is in light mode,
*When* the detail page opens,
*Then* the background and text adapt to the light color scheme.

**AC11 — Hero 250dp + name overlay**

*Given* the detail page opens,
*When* the hero renders,
*Then* it is 250dp tall and the POI name/type/vibe chip appear overlaid at the hero's bottom-left.

---

## Additional Context

### Dependencies

- `Res.string.advisory_ai_disclaimer` — exists at `strings.xml` line 194
- `Res.string.poi_tip_refresh_fallback` — add in Task 11
- `Icons.Default.Phone`, `Icons.Default.Language`, `Icons.Default.Map`, `Icons.Default.MoreVert`, `Icons.Default.ArrowBack` — all in standard Material Icons (no extended dependency needed)
- `Icons.Default.Refresh` — in `material-icons-extended` (not standard). Use `Text("↻")` to avoid adding a dependency for one icon.
- `LocalUriHandler` — `androidx.compose.ui.platform` (already in project)
- Foursquare API key: dev must obtain from developer.foursquare.com and add to `local.properties`

### Testing Strategy

1. **`GooglePlacesProviderTest`** (existing, `androidUnitTest`):
   - Add cases: mock JSON with `websiteUri`, `googleMapsUri`, `internationalPhoneNumber`, `formattedAddress` → assert all four fields on returned POI.
   - Add case: `applyCache()` does not touch `discoveryContext` — pass a POI with non-null `discoveryContext`, call `applyCache()`, assert `discoveryContext` unchanged.
   - Verify `PoiMatchUtils.isConfidentMatch` is called (not a private copy).

2. **New `FoursquareProviderTest`** (`androidUnitTest`):
   - Valid response with `social_media` → POI has instagram/facebook/twitter
   - Blank API key → original POI unchanged, no exception
   - Display name mismatch → original POI unchanged, cache stores null social
   - API throws exception → `Result.success(originalPoi)` (fail-safe)
   - Cache hit within 7 days → no HTTP call made
   - Cache miss (expired) → HTTP call made, cache updated

3. **Manual verification**:
   - POI in CAUTION country → safety ticker yellow visible; safety card visible with disclaimer
   - POI in SAFE country → no safety ticker at all; no safety card
   - Toggle device dark/light → detail page background switches correctly
   - Tap ↻ on local tip → tip refreshes; contextBlurb and whyNow unchanged
   - Overflow ⋮ → menu shows; back press closes menu; second back press closes page
   - Tap "Show on map" in overflow → detail page dismisses and map centers on POI

### Notes

- **Foursquare "existing call" assumption**: The brainstorm stated social links come from expanding an existing Foursquare call — no Foursquare integration exists in the codebase. `FoursquareProvider.kt` is a new file from scratch.
- **Distance in info ticker**: Deliberately omitted from Story A. Story B concern.
- **`PoiMatchUtils` extraction**: `GooglePlacesProvider` tests that test `isConfidentMatch` by name will need to be updated to call `PoiMatchUtils.isConfidentMatch` instead.
- **Hero gradient end color**: Use `MaterialTheme.colorScheme.surface` as the opaque end of the bottom gradient so it blends seamlessly into the info section background below.
- **`BackgroundEnrichmentComplete` manual merge**: This handler (~line 1690) manually cherry-picks fields when merging enriched POIs back into existing batches. If new POI fields are added in future, this handler needs updating too — it does NOT call `mergeFrom()`. Noted as a pre-existing tech debt.
