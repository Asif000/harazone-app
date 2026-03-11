---
title: 'Progressive Area Loading — Two-Stage POI Pipeline'
slug: 'progressive-area-loading'
created: '2026-03-11'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Gemini AI (streaming)', 'Ktor SSE']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt'
code_patterns:
  - 'BucketUpdate is a sealed class — new PinsReady variant follows existing pattern'
  - 'MapViewModel.collectPortraitWithRetry() is the single shared entry point for all portrait fetches'
  - 'MapUiState.Ready is a plain data class — mutations via copy()'
  - 'GeminiAreaIntelligenceProvider.streamAreaPortrait() returns Flow<BucketUpdate>'
  - 'withRetry() wrapper handles retries — reuse for both stages'
  - 'searchJob cancels both coroutines cleanly when user switches area'
test_patterns:
  - 'MapViewModel tests use fake GetAreaPortraitUseCase that emits controlled BucketUpdate sequences'
  - 'Tests assert on uiState snapshots via Turbine'
---

# Tech-Spec: Progressive Area Loading — Two-Stage POI Pipeline

**Created:** 2026-03-11

## Overview

### Problem Statement

Every area search (GPS launch + manual search) fires a single Gemini call that returns 6 buckets + full POI array (~1000 output tokens). The call takes 15+ seconds with nothing visible — user stares at a spinner with no feedback. This affects all 5 entry points in MapViewModel: initial GPS launch, geocoding suggestion selection, recent place selection, empty submit, and return-to-location (different area branch).

### Solution

Split into two concurrent Gemini calls that fire simultaneously when an area search begins:

- **Stage 1** — new slim prompt: returns only POI name, lat/lng, type for ~15 POIs. Response in 2-3s. Pins appear on map immediately. `isSearchingArea = false`, `isEnrichingArea = true`, thin progress bar shows.
- **Stage 2** — new enrich prompt: injects Stage 1 POI names, returns description, whySpecial, rating, hours for each. Response in ~5-7s. VM merges enriched data into Stage 1 pins by name. Progress bar disappears.

Stage 1 failure degrades gracefully: Stage 2 runs as a standalone call (today's behavior). Bucket data (CHARACTER, HISTORY, etc.) is dropped entirely — MapViewModel already discards it and it will be revisited in a future brainstorm (Feature #23).

### Scope

**In Scope:**
- New `BucketUpdate.PinsReady(pois: List<POI>)` sealed class variant
- New `GeminiPromptBuilder.buildPinOnlyPrompt(areaName, context)` — slim prompt, name/lat/lng/type only
- New `GeminiPromptBuilder.buildEnrichmentPrompt(areaName, poiNames: List<String>, context)` — injects POI names, returns description/whySpecial/rating/hours
- `GeminiAreaIntelligenceProvider.streamAreaPortrait()` refactored to orchestrate both calls concurrently, emitting `PinsReady` then `PortraitComplete`
- `GeminiResponseParser` — new slim parser for Stage 1 (JSON array, minimal fields) and enrich parser for Stage 2
- `MapUiState.Ready` gains `isEnrichingArea: Boolean = false`
- `MapViewModel.collectPortraitWithRetry()` handles `PinsReady` (set pins + flip flags) + `PortraitComplete` (merge by name)
- POI merge logic: Stage 2 pois matched to Stage 1 pois by name (case-insensitive). Enriched fields (description, insight, rating, hours) copied over. Unmatched Stage 2 POIs appended.
- Thin progress bar in `MapScreen` visible when `isEnrichingArea = true`
- Shimmer on description/insight/rating fields in `ExpandablePoiCard` when fields are blank (Stage 1 pin tapped before Stage 2 arrives)
- All 6 MapViewModel portrait entry points covered: 5 via `collectPortraitWithRetry()` + `submitSearch()` updated inline (Task 7e)
- `onGeocodingCancelLoad()` updated to clear `isEnrichingArea` (Task 7f)

**Out of Scope:**
- Bucket data (CHARACTER, HISTORY, SAFETY, WHATS_HAPPENING, COST, NEARBY) — dropped, future brainstorm #23
- External API integration (Foursquare, Google Places) — Gemini only for v1
- Retry logic changes — existing `withRetry` wrapper reused as-is for both stages
- ChatViewModel — unaffected
- `AreaRepository` interface change — stays as single `Flow<BucketUpdate>`, orchestration is internal to the provider

---

## Context for Development

### Codebase Patterns

- `BucketUpdate` is a sealed class in `domain/model/`. Add `PinsReady` alongside existing `PortraitComplete`. Pure Kotlin, no Android deps.
- `MapViewModel` only ever reacts to `BucketUpdate.PortraitComplete` today. `collectPortraitWithRetry()` is the single entry point for all portrait fetches — `PinsReady` handling goes here, covering all 5 callers automatically.
- `GeminiAreaIntelligenceProvider.streamAreaPortrait()` currently uses `flow { }` builder. Must change to `channelFlow { }` to allow concurrent emissions from two launched coroutines. `AreaRepository` interface stays unchanged.
- Stage 2 uses a `CompletableDeferred<List<String>>` to receive POI names from Stage 1 before building the enrich prompt. Stage 1 and Stage 2 are not simultaneous from t=0 — Stage 2 fires as soon as Stage 1 names are available (~2-3s), which is still dramatically faster than today's single 15s call.
- `AreaRepositoryImpl` has three important constraints on `PinsReady`:
  1. PASS THROUGH without `enrichPoisWithImages()` — Stage 1 POIs have no `wikiSlug`, enrichment would defeat the fast path.
  2. DO NOT write to `area_poi_cacheQueries` — only `PortraitComplete` gets cached.
  3. FILTER OUT in the stale-while-revalidate `scope.launch { }` background refresh — user already has stale POIs visible; emitting `PinsReady` would cause a confusing partial overwrite.
- `MapUiState.Ready` is a plain `data class` — add `isEnrichingArea: Boolean = false`, mutate via `copy()`.
- `ExpandablePoiCard` already guards `poi.rating` and `poi.liveStatus` with null checks — those fields cleanly absent for Stage 1 pins with no change needed. Only `poi.insight` (guarded by `isNotEmpty()`) needs a shimmer placeholder when blank.
- `MapScreen.kt` uses `isSearchingArea` for `AnimatedVisibility` of two buttons. The `isEnrichingArea` progress bar is a new separate element, not tied to existing visibility guards.
- Test pattern: `MapViewModelTest` uses `FakeAreaRepository` that emits controlled `BucketUpdate` sequences. Tests assert on `uiState` snapshots. New tests will emit `PinsReady` then `PortraitComplete` with delays to verify flag transitions.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `domain/model/BucketUpdate.kt` | Add `PinsReady(pois: List<POI>)` variant |
| `data/remote/GeminiPromptBuilder.kt` | Add `buildPinOnlyPrompt()` + `buildEnrichmentPrompt()`; delete BACKLOG TODO |
| `data/remote/GeminiAreaIntelligenceProvider.kt` | Refactor `streamAreaPortrait()` to `channelFlow` with two-stage orchestration |
| `data/remote/GeminiResponseParser.kt` | Add `parsePinOnlyResponse()` + `parseEnrichmentResponse()` |
| `data/repository/AreaRepositoryImpl.kt` | Pass through `PinsReady` without enrichment/cache; filter from background refresh |
| `ui/map/MapUiState.kt` | Add `isEnrichingArea: Boolean = false` |
| `ui/map/MapViewModel.kt` | `collectPortraitWithRetry()` — handle `PinsReady` + merge logic on `PortraitComplete` |
| `ui/map/MapScreen.kt` | Add `LinearProgressIndicator` when `isEnrichingArea` |
| `ui/map/components/ExpandablePoiCard.kt` | Shimmer placeholder when `poi.insight.isEmpty()` |
| `commonTest/.../MapViewModelTest.kt` | Add two-stage tests to existing test class |
| `domain/model/POI.kt` | Reference only — field names for parsers |
| `util/RetryHelper.kt` | Reference only — `withRetry()` reused for each stage call |

### Technical Decisions

1. `AreaRepository.getAreaPortrait()` interface unchanged — single `Flow<BucketUpdate>`. Two-stage is an implementation detail inside `GeminiAreaIntelligenceProvider`.
2. `channelFlow { }` builder replaces `flow { }` in `streamAreaPortrait()` to allow concurrent `launch { }` blocks to `send()` updates from both stages.
3. Stage 2 waits for Stage 1 names via `CompletableDeferred<List<String>>`. If Stage 1 fails, `deferred.completeExceptionally()` → Stage 2 catches exception → falls back to single full call (existing behavior, graceful degradation).
4. POI merge in `MapViewModel`: build a `Map<String, POI>` from Stage 1 pois keyed by `name.trim().lowercase()`. On `PortraitComplete`, for each Stage 2 POI look up matching Stage 1 POI and merge enrichment fields (`insight`, `rating`, `hours`, `liveStatus`) onto it. Stage 2 POIs with no Stage 1 match are appended. Stage 1 POIs with no Stage 2 match keep blank fields.
5. Stage 1 prompt: JSON array only, no buckets. Fields: `n` (name), `t` (type), `v` (vibe), `lat`, `lng`. ~15 POIs. No `w`, `h`, `s`, `r`, `wiki`.
6. Stage 2 prompt: injects Stage 1 POI names, returns fields: `n` (must match Stage 1 name exactly), `w` (whySpecial), `h` (hours), `s` (liveStatus), `r` (rating). No coordinates.
7. `isSearchingArea` flips `false` when `PinsReady` received. `isEnrichingArea` flips `true` at same moment. Both clear on `PortraitComplete`.
8. Progress bar: indeterminate `LinearProgressIndicator` anchored at bottom of the map `Box`, above the FAB/AI bar, visible only when `isEnrichingArea = true`.

---

## Implementation Plan

### Tasks

- [x] **Task 1: Add `PinsReady` to `BucketUpdate`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt`
  - Action: Add new variant after `PortraitComplete`:
    ```kotlin
    data class PinsReady(
        val pois: List<POI>
    ) : BucketUpdate()
    ```
  - Notes: Pure Kotlin, no imports needed beyond existing `POI`.

- [x] **Task 2: Add prompt methods to `GeminiPromptBuilder`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action 2a — Delete the `TODO(BACKLOG-HIGH)` comment block on lines above `buildAreaPortraitPrompt()` (the one about 15+ second latency). This spec resolves it.
  - Action 2b — Add `buildPinOnlyPrompt(areaName: String, context: AreaContext): String` BEFORE `buildAreaPortraitPrompt`. Returns a prompt that asks for a JSON array of ~15 POIs with minimal fields only:
    ```kotlin
    fun buildPinOnlyPrompt(areaName: String, context: AreaContext): String {
        return """
    You are a local expert for "$areaName". List the 15 most interesting and varied points of interest in this area.

    Context:
    - Time of day: ${context.timeOfDay}
    - Day of week: ${context.dayOfWeek}
    - Preferred language: ${context.preferredLanguage}

    Output ONLY a JSON array. No other text. POI names MUST be in ${context.preferredLanguage} if possible, otherwise use the local name. Each object must have these exact fields:
    [{"n":"Name","t":"type","v":"vibe","lat":38.7100,"lng":-9.1300}]

    Valid t values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district
    Valid v values: character, history, whats_on, safety, nearby, cost

    IMPORTANT: Provide GPS coordinates to 4 decimal places for every POI. Omit any POI you cannot place on a map.
        """.trimIndent()
    }
    ```
  - Action 2c — Add `buildEnrichmentPrompt(areaName: String, poiNames: List<String>, context: AreaContext): String` AFTER `buildPinOnlyPrompt`. Injects POI names and asks for enrichment fields only:
    ```kotlin
    fun buildEnrichmentPrompt(areaName: String, poiNames: List<String>, context: AreaContext): String {
        val namesList = poiNames.joinToString("\n") { "- $it" }
        return """
    You are a passionate local who has lived in "$areaName" for 20 years.

    For each place listed below, provide enrichment details as a JSON array. The "n" field MUST exactly match the input name.

    Places to enrich:
    $namesList

    Context:
    - Time of day: ${context.timeOfDay}
    - Day of week: ${context.dayOfWeek}

    Output ONLY a JSON array. No other text:
    [{"n":"Name","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5}]

    Valid s values: open, busy, closed
    WHY SPECIAL REQUIRED: Every POI needs a compelling "w". Generic descriptions are not acceptable.
        """.trimIndent()
    }
    ```

- [x] **Task 3: Add parsers to `GeminiResponseParser`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt`
  - Action 3a — Add `parsePinOnlyResponse(text: String): List<POI>` as a new public method. Reuses the existing private `parsePoisJson()` but returns POI objects with only `name`, `type`, `vibe`, `latitude`, `longitude` populated (all other fields blank/null):
    ```kotlin
    fun parsePinOnlyResponse(text: String): List<POI> {
        return try {
            val cleaned = text.trim().let {
                if (it.startsWith("```")) it.lines().drop(1).dropLast(1).joinToString("\n") else it
            }
            val poisJson = json.decodeFromString<List<PoiJson>>(cleaned)
            poisJson.filter { it.n.isNotBlank() && it.lat != null && it.lng != null }.map { poiJson ->
                POI(
                    name = poiJson.n,
                    type = poiJson.t,
                    description = "",
                    confidence = Confidence.MEDIUM,
                    latitude = poiJson.lat,
                    longitude = poiJson.lng,
                    vibe = poiJson.v,
                    insight = "",
                    hours = null,
                    liveStatus = null,
                    rating = null,
                    vibeInsights = emptyMap(),
                    wikiSlug = null,
                )
            }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse pin-only response" }
            emptyList()
        }
    }
    ```
  - Action 3b — Add `@Serializable data class EnrichJson` (internal, matching `PoiJson` visibility) and `internal fun parseEnrichmentResponse(text: String): List<EnrichJson>`. Both must be `internal` — a `public` function cannot expose an `internal` return type without compiler warnings (F11 fix):
    ```kotlin
    @Serializable
    internal data class EnrichJson(
        val n: String = "",
        val w: String = "",
        val h: String? = null,
        val s: String? = null,
        val r: Float? = null,
    )

    internal fun parseEnrichmentResponse(text: String): List<EnrichJson> {
        return try {
            val cleaned = text.trim().let {
                if (it.startsWith("```")) it.lines().drop(1).dropLast(1).joinToString("\n") else it
            }
            json.decodeFromString<List<EnrichJson>>(cleaned)
                .filter { it.n.isNotBlank() }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse enrichment response" }
            emptyList()
        }
    }
    ```

- [x] **Task 4: Refactor `GeminiAreaIntelligenceProvider.streamAreaPortrait()`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt`
  - Action: Replace the existing `flow { }` body of `streamAreaPortrait()` with a `channelFlow { }` that orchestrates both stages. Add import `kotlinx.coroutines.channelFlow`, `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.launch`:
    ```kotlin
    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = channelFlow {
        val apiKey = apiKeyProvider.geminiApiKey
        if (apiKey.isBlank()) {
            throw DomainErrorException(DomainError.ApiError(0, "Gemini API key not configured"))
        }

        val stage1NamesDeferred = CompletableDeferred<List<String>>()

        // Stage 1 — fast pin call
        launch {
            try {
                val prompt = promptBuilder.buildPinOnlyPrompt(areaName, context)
                val requestBody = buildRequestBody(prompt)
                val fullText = StringBuilder()
                var hasEmitted = false
                val result = withRetry(maxAttempts = MAX_RETRY_ATTEMPTS, initialDelayMs = 200, maxDelayMs = 2000,
                    isRetryable = { e -> !hasEmitted && e is Exception && isRetryableError(e) }) {
                    httpClient.sse(urlString = "$BASE_URL/$GEMINI_MODEL:streamGenerateContent",
                        request = {
                            method = HttpMethod.Post
                            parameter("alt", "sse")
                            parameter("key", apiKey)
                            contentType(ContentType.Application.Json)
                            setBody(requestBody)
                        }
                    ) {
                        incoming.collect { event ->
                            val data = event.data ?: return@collect
                            val text = responseParser.extractTextFromSseEvent(data) ?: return@collect
                            fullText.append(text)
                            hasEmitted = true
                        }
                    }
                }
                if (result.isFailure) {
                    // Complete deferred BEFORE throwing so Stage 2 is never left hanging
                    stage1NamesDeferred.completeExceptionally(result.exceptionOrNull()!!)
                    throw result.exceptionOrNull()!!
                }
                val pois = responseParser.parsePinOnlyResponse(fullText.toString())
                // Single complete() call — handles both empty and non-empty cases (F1 fix)
                stage1NamesDeferred.complete(pois.map { it.name })
                if (pois.isNotEmpty()) {
                    send(BucketUpdate.PinsReady(pois))
                    AppLogger.d { "Stage 1 complete: ${pois.size} pins for '$areaName'" }
                } else {
                    AppLogger.d { "Stage 1 returned no pins for '$areaName' — Stage 2 will fallback" }
                }
            } catch (e: CancellationException) {
                stage1NamesDeferred.cancel()
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Stage 1 failed for '$areaName' — Stage 2 will fallback" }
                // Ensure deferred is completed so Stage 2 is never left hanging (F7 fix)
                if (!stage1NamesDeferred.isCompleted) stage1NamesDeferred.completeExceptionally(e)
            }
        }

        // Stage 2 — enrich call (waits for Stage 1 names)
        launch {
            try {
                val stage1Names = try { stage1NamesDeferred.await() } catch (e: Exception) { emptyList() }
                val prompt = if (stage1Names.isNotEmpty()) {
                    promptBuilder.buildEnrichmentPrompt(areaName, stage1Names, context)
                } else {
                    // Stage 1 failed — fallback to full portrait prompt for POI data
                    promptBuilder.buildAreaPortraitPrompt(areaName, context)
                }
                val requestBody = buildRequestBody(prompt)
                val fullText = StringBuilder()
                var hasEmitted = false
                val streamingParser = if (stage1Names.isEmpty()) responseParser.createStreamingParser() else null
                val result = withRetry(maxAttempts = MAX_RETRY_ATTEMPTS, initialDelayMs = 200, maxDelayMs = 2000,
                    isRetryable = { e -> !hasEmitted && e is Exception && isRetryableError(e) }) {
                    httpClient.sse(urlString = "$BASE_URL/$GEMINI_MODEL:streamGenerateContent",
                        request = {
                            method = HttpMethod.Post
                            parameter("alt", "sse")
                            parameter("key", apiKey)
                            contentType(ContentType.Application.Json)
                            setBody(requestBody)
                        }
                    ) {
                        incoming.collect { event ->
                            val data = event.data ?: return@collect
                            val text = responseParser.extractTextFromSseEvent(data) ?: return@collect
                            if (streamingParser != null) {
                                for (update in streamingParser.processChunk(text)) {
                                    if (update is BucketUpdate.PortraitComplete) send(update)
                                }
                            } else {
                                fullText.append(text)
                            }
                            hasEmitted = true
                        }
                    }
                    streamingParser?.finish()?.filterIsInstance<BucketUpdate.PortraitComplete>()?.forEach { send(it) }
                }
                if (result.isFailure) throw result.exceptionOrNull()!!
                if (stage1Names.isNotEmpty()) {
                    val enriched = responseParser.parseEnrichmentResponse(fullText.toString())
                    AppLogger.d { "Stage 2 complete: ${enriched.size} enriched for '$areaName'" }
                    // Emit enrichment-only POIs — latitude/longitude intentionally null.
                    // MapViewModel.mergePois() overlays these onto Stage 1 pins (which have real coords).
                    // AreaRepositoryImpl MUST NOT cache this PortraitComplete directly — see Task 5a NOTE.
                    send(BucketUpdate.PortraitComplete(enriched.map { e ->
                        POI(name = e.n, type = "", description = "", confidence = Confidence.MEDIUM,
                            latitude = null, longitude = null, insight = e.w,
                            hours = e.h, liveStatus = e.s, rating = e.r)
                    }))
                }
                AppLogger.d { "GeminiAreaIntelligenceProvider: portrait streaming complete for '$areaName'" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Stage 2 failed for '$areaName'" }
                throw mapToDomainErrorException(e)
            }
        }
    }
    ```
  - Action 4b — Add private helper `buildRequestBody(prompt: String): String` to avoid duplication:
    ```kotlin
    private fun buildRequestBody(prompt: String): String = json.encodeToString(
        GeminiRequest(contents = listOf(GeminiRequestContent(parts = listOf(GeminiRequestPart(text = prompt)))))
    )
    ```
  - Notes: Add imports `kotlinx.coroutines.channelFlow`, `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.launch`. Import `BucketUpdate` already exists. The `isRetryableError` and `mapToDomainErrorException` helpers are unchanged.

- [x] **Task 5: Update `AreaRepositoryImpl` to handle `PinsReady`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt`
  - Action 5a — In the cache miss `collect` block (lines 136–145), add `PinsReady` pass-through and cache-poison guard. CRITICAL (F5 fix): Stage 2 emits `PortraitComplete` with POIs that have `latitude=null`/`longitude=null`. These must NOT be enriched or cached — they are enrichment-only and must be merged onto Stage 1 pins by the ViewModel. Detect by checking if any POI has coords:
    ```kotlin
    aiProvider.streamAreaPortrait(areaName, context).collect { update ->
        when (update) {
            is BucketUpdate.PinsReady -> emit(update) // Pass through — no enrichment, no cache
            is BucketUpdate.PortraitComplete -> {
                val hasCoords = update.pois.any { it.latitude != null && it.longitude != null }
                if (hasCoords) {
                    // Full portrait POIs (Stage 1 fallback) — enrich images + cache as normal
                    val enriched = if (update.pois.isNotEmpty()) enrichPoisWithImages(update.pois) else update.pois
                    if (enriched.isNotEmpty()) writePoisToCache(enriched, areaName, language)
                    emit(BucketUpdate.PortraitComplete(resolveTileRefs(enriched)))
                } else {
                    // Stage 2 enrichment-only POIs (no coords) — do NOT cache, pass through for VM merge
                    emit(update)
                }
            }
            else -> {
                emit(update)
                if (update is BucketUpdate.BucketComplete) writeToCache(update.content, areaName, language)
            }
        }
    }
    ```
  - Action 5b — In the stale-while-revalidate background refresh `scope.launch { }` block (lines 112–128), filter out `PinsReady` — user already has stale POIs visible:
    Change:
    ```kotlin
    if (update is BucketUpdate.BucketComplete) {
    ```
    The `collect` inside this launch already only handles `BucketComplete` and `PortraitComplete`. Add an explicit guard at the top of the `collect` lambda:
    ```kotlin
    .collect { update ->
        if (update is BucketUpdate.PinsReady) return@collect // Skip — user has stale data already
        if (update is BucketUpdate.BucketComplete) { ... }
        if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) { ... }
    }
    ```
  - Notes: No DB schema changes. `enrichPoisWithImages()` and `resolveTileRefs()` unchanged.

- [x] **Task 6: Update `MapUiState`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Add `val isEnrichingArea: Boolean = false` field to `MapUiState.Ready`, after `isSearchingArea`:
    ```kotlin
    val isSearchingArea: Boolean = false,
    val isEnrichingArea: Boolean = false,
    ```

- [x] **Task 7: Update `MapViewModel.collectPortraitWithRetry()`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action 7a — In `collectPortraitWithRetry()`, update the `collect { update -> }` lambda to handle `PinsReady`:
    Replace:
    ```kotlin
    .collect { update ->
        if (update is BucketUpdate.PortraitComplete) {
            pois = update.pois
        }
    }
    ```
    With:
    ```kotlin
    .collect { update ->
        when (update) {
            is BucketUpdate.PinsReady -> {
                val s = _uiState.value as? MapUiState.Ready ?: return@collect
                val counts = computeVibePoiCounts(update.pois)
                stage1Pois = update.pois
                _uiState.value = s.copy(
                    pois = update.pois,
                    vibePoiCounts = counts,
                    isSearchingArea = false,
                    isEnrichingArea = true,
                )
            }
            is BucketUpdate.PortraitComplete -> {
                pois = if (stage1Pois.isNotEmpty()) {
                    mergePois(stage1Pois, update.pois)
                } else {
                    update.pois
                }
            }
            else -> { /* ContentDelta, BucketComplete, ContentAvailabilityNote — ignored */ }
        }
    }
    ```
  - Action 7b — Add `var stage1Pois = emptyList<POI>()` local variable at the top of `collectPortraitWithRetry()`, alongside the existing `var pois = emptyList<POI>()`.
  - Action 7c — Add `isEnrichingArea = false` to every `_uiState.value = s.copy(isSearchingArea = false, ...)` in the `onComplete` lambda (so enriching clears when portrait is done). Update `onComplete` call sites:
    ```kotlin
    onComplete = { pois, _ ->
        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
        val counts = computeVibePoiCounts(pois)
        _uiState.value = state.copy(
            areaName = ...,
            pois = pois,
            vibePoiCounts = counts,
            activeVibe = null,
            isSearchingArea = false,
            isEnrichingArea = false, // ADD THIS
        )
    }
    ```
  - Action 7d — Add private `mergePois(stage1: List<POI>, enrichments: List<POI>): List<POI>` helper:
    ```kotlin
    private fun mergePois(stage1: List<POI>, enrichments: List<POI>): List<POI> {
        val enrichMap = enrichments.associateBy { it.name.trim().lowercase() }
        val merged = stage1.map { pin ->
            val enrich = enrichMap[pin.name.trim().lowercase()]
            if (enrich != null) pin.copy(
                insight = enrich.insight,
                rating = enrich.rating,
                hours = enrich.hours,
                liveStatus = enrich.liveStatus,
            ) else pin
        }
        val stage1Keys = stage1.map { it.name.trim().lowercase() }.toSet()
        val newPois = enrichments.filter { it.name.trim().lowercase() !in stage1Keys }
        return merged + newPois
    }
    ```
  - Action 7e — Fix `submitSearch()` — it has its own inline `collect` lambda (not `collectPortraitWithRetry()`) and will silently ignore `PinsReady` after this change (F3 fix). Update its `collect` block to handle `PinsReady` identically to `collectPortraitWithRetry()`:
    ```kotlin
    searchJob = viewModelScope.launch {
        try {
            val context = areaContextFactory.create()
            var stage1Pois = emptyList<POI>()
            getAreaPortrait(query, context)
                .catch { e -> AppLogger.e(e) { "Area search failed" } }
                .collect { update ->
                    when (update) {
                        is BucketUpdate.PinsReady -> {
                            val state = _uiState.value as? MapUiState.Ready ?: return@collect
                            stage1Pois = update.pois
                            _uiState.value = state.copy(
                                pois = update.pois,
                                vibePoiCounts = computeVibePoiCounts(update.pois),
                                isSearchingArea = false,
                                isEnrichingArea = true,
                            )
                        }
                        is BucketUpdate.PortraitComplete -> {
                            val pois = if (stage1Pois.isNotEmpty()) mergePois(stage1Pois, update.pois) else update.pois
                            val state = _uiState.value as? MapUiState.Ready ?: return@collect
                            _uiState.value = state.copy(
                                pois = pois,
                                areaName = query,
                                vibePoiCounts = computeVibePoiCounts(pois),
                                activeVibe = null,
                                isSearchingArea = false,
                                isEnrichingArea = false,
                            )
                        }
                        else -> {}
                    }
                }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLogger.e(e) { "Area search error" } }
    }
    ```
  - Action 7f — Fix `onGeocodingCancelLoad()` — it cancels `searchJob` but doesn't clear `isEnrichingArea`. If Stage 1 completed before cancel, progress bar sticks (F6 fix). Add `isEnrichingArea = false` to its state reset:
    ```kotlin
    fun onGeocodingCancelLoad() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        searchJob?.cancel()
        _uiState.value = current.copy(
            isSearchingArea = false,
            isEnrichingArea = false, // ADD THIS
            isGeocodingInitiatedSearch = false,
            isGeocodingLoading = false,
            geocodingQuery = "",
            geocodingSuggestions = emptyList(),
            geocodingSelectedPlace = null,
        )
    }
    ```
  - Action 7g — Fix `collectPortraitWithRetry()` retry suppression — add guard so the broad-query retry does not fire when `stage1Pois` is non-empty (F2 fix). After the first `getAreaPortrait()` collect loop, change the empty-pois check:
    ```kotlin
    if (fetchFailed) return
    // If Stage 1 already delivered pins, treat as success even if Stage 2 enrichment was empty
    if (pois.isNotEmpty() || stage1Pois.isNotEmpty()) {
        onComplete(pois.ifEmpty { stage1Pois }, areaName)
        return
    }
    // Retry with broader query only if both stages produced nothing
    AppLogger.d { "No POIs for '$areaName' — retrying with broader query" }
    ```
  - Action 7h — Fix `selectedPoi` staleness after merge — when `PortraitComplete` arrives and merge updates `pois`, also update `selectedPoi` if it matches one of the merged pins (F8 fix). In the `PortraitComplete` branch of Task 7a's `when` block, after computing `pois`:
    ```kotlin
    is BucketUpdate.PortraitComplete -> {
        pois = if (stage1Pois.isNotEmpty()) mergePois(stage1Pois, update.pois) else update.pois
        // Also update selectedPoi if open, so shimmer clears without user closing card
        val s = _uiState.value as? MapUiState.Ready
        if (s != null && s.selectedPoi != null) {
            val updatedSelected = pois.firstOrNull {
                it.name.trim().lowercase() == s.selectedPoi.name.trim().lowercase()
            }
            if (updatedSelected != null) {
                _uiState.value = s.copy(selectedPoi = updatedSelected)
            }
        }
    }
    ```
  - Notes: All `onError` lambdas in the 5 `collectPortraitWithRetry()` callers must add `isEnrichingArea = false` alongside `isSearchingArea = false` to prevent stuck progress bar. Also note: `returnToCurrentLocation()` calls `collectPortraitWithRetry()` inside `returnToLocationJob`, not `searchJob` — if user switches area while returning to location, `searchJob` cancel does NOT stop the return-to-location fetch. These two fetches can race. This is a pre-existing architectural issue; do NOT attempt to fix it in this spec. Add a `TODO(BACKLOG-MEDIUM)` comment at the `returnToCurrentLocation()` call site noting the race condition.

- [x] **Task 8: Add progress bar to `MapScreen`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action: Inside the main `Box` that contains the map, add an indeterminate `LinearProgressIndicator` anchored at the bottom using `WindowInsets` for safe positioning (F12 fix — not hardcoded dp). Place it after the `MapComposable` and before the overlay buttons:
    ```kotlin
    if (state.isEnrichingArea) {
        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarInset + 72.dp), // 72dp = FAB/AI bar height above nav bar
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
    }
    ```
  - Notes: Add imports `androidx.compose.material3.LinearProgressIndicator`, `androidx.compose.foundation.layout.WindowInsets`, `androidx.compose.foundation.layout.navigationBars`, `androidx.compose.foundation.layout.asPaddingValues` if not already present. The `72.dp` constant accounts for the AI bar/FAB height — adjust if the bar height changes.

- [x] **Task 9: Add insight shimmer to `ExpandablePoiCard`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt`
  - Action: Find the insight block (lines ~173–180) and add an `else` shimmer branch when `insight` is empty:
    ```kotlin
    if (poi.insight.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = poi.insight,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    } else {
        // Stage 1 pin — enrich loading
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
    ```
  - Notes: No animation needed for v1 — static shimmer boxes are sufficient and simpler. The shimmer disappears naturally when the user re-opens the card after Stage 2 completes and the ViewModel has merged the enriched data.

### Acceptance Criteria

- [x] **AC-1 — Pins appear fast on GPS launch**
  Given: User opens the app for the first time in a new area (cache miss)
  When: GPS resolves and area name is determined
  Then: Within 3 seconds, map pins appear at correct coordinates
  And: `isSearchingArea = false`, `isEnrichingArea = true`, thin progress bar visible at bottom of map

- [x] **AC-2 — Pins appear fast on manual area search**
  Given: User selects an area from geocoding suggestions (cache miss)
  When: Area name is confirmed
  Then: Within 3 seconds, map pins appear
  And: Progress bar visible while enrichment loads

- [x] **AC-3 — Cards fill in after Stage 2**
  Given: Stage 1 pins are visible and user taps a pin
  When: The card opens before Stage 2 completes
  Then: Name and type are visible, two shimmer boxes shown where insight/description would be
  And: Rating and hours rows are absent (already null-guarded — no change needed)
  And: When Stage 2 arrives, the open card automatically updates (shimmer replaced by real insight) without user closing/reopening — `selectedPoi` is updated in the `PortraitComplete` handler

- [x] **AC-4 — Progress bar disappears after enrichment**
  Given: Stage 1 pins are visible, `isEnrichingArea = true`
  When: Stage 2 `PortraitComplete` is received
  Then: `isEnrichingArea = false`, progress bar disappears
  And: POIs in `MapUiState.Ready.pois` now have `insight`, `rating`, `hours` merged from Stage 2

- [x] **AC-5 — Stage 1 failure degrades gracefully**
  Given: Stage 1 Gemini call fails (network error, timeout, API error)
  When: Stage 2 coroutine receives the exception from `stage1NamesDeferred`
  Then: Stage 2 falls back to `buildAreaPortraitPrompt()` (existing full portrait call)
  And: No `PinsReady` emitted, `isSearchingArea` stays true until `PortraitComplete`
  And: User experience is identical to today's behavior (single call, ~15s)

- [x] **AC-6 — Area switch cancels both stages**
  Given: Stage 1 and Stage 2 are both in flight
  When: User selects a different area (new `searchJob` starts, old one cancelled)
  Then: Both coroutines inside `channelFlow` are cancelled cleanly
  And: No stale `PinsReady` or `PortraitComplete` events emitted for the old area

- [x] **AC-7 — Cache hit path unaffected**
  Given: User revisits a previously searched area (cache hit in `AreaRepositoryImpl`)
  When: `getAreaPortrait()` is called
  Then: POIs are served from cache immediately (no Gemini calls, no Stage 1/2)
  And: `isSearchingArea` clears as before, `isEnrichingArea` never set to true

- [x] **AC-8 — `onError` clears progress bar (unit test)**
  Given: Stage 1 succeeds (PinsReady emitted, isEnrichingArea = true) but Stage 2 fails
  When: `onError` callback is invoked in `collectPortraitWithRetry()`
  Then: `isEnrichingArea = false` in the resulting state (no stuck progress bar)

## Additional Context

### Dependencies

- No new libraries — `channelFlow`, `CompletableDeferred`, `launch` are in `kotlinx.coroutines` already present
- No DB schema changes
- No new Koin modules — `GeminiAreaIntelligenceProvider` wiring unchanged

### Testing Strategy

Add to `MapViewModelTest.kt`:

1. `pinsReady_showsPoisAndSetsEnrichingFlag` — emit `PinsReady(pois)` from fake repo → assert `uiState.pois == pois`, `uiState.isSearchingArea == false`, `uiState.isEnrichingArea == true`
2. `portraitComplete_afterPinsReady_mergesEnrichmentAndClearsFlag` — emit `PinsReady(stage1Pois)` then `PortraitComplete(enrichedPois)` → assert merged POIs have enrichment fields, `isEnrichingArea == false`
3. `portraitComplete_withoutPinsReady_setsFullPoisDirectly` — emit only `PortraitComplete(pois)` (Stage 1 failure path) → assert `pois` set directly, `isEnrichingArea == false`
4. `onError_afterPinsReady_clearsEnrichingFlag` — emit `PinsReady`, then trigger error in `collectPortraitWithRetry` → assert `isEnrichingArea == false`
5. `mergePois_enrichesMatchedAndAppendsUnmatched` — call `mergePois()` directly (make internal or test via state) with stage1 list + enrichments with one match + one new POI → assert merged correctly

### Notes

- `TODO(BACKLOG-HIGH)` in `GeminiPromptBuilder.buildAreaPortraitPrompt()` explicitly calls this problem out — this spec resolves it. Delete the TODO comment in Task 2.
- Bucket data is intentionally dropped. Revisit in Feature #23 brainstorm.
- F1 FIXED: `CompletableDeferred.complete()` called exactly once per Stage 1 execution path. The `else` branch that called `complete(emptyList())` a second time has been removed.
- F2 FIXED: `collectPortraitWithRetry()` retry suppressed when `stage1Pois.isNotEmpty()` — even with no enrichment, pins are visible and retry would cause confusing duplicate API calls (Task 7g).
- F3 FIXED: `submitSearch()` inline collect updated to handle `PinsReady` + merge (Task 7e).
- F5 FIXED: `AreaRepositoryImpl` uses `hasCoords` guard on `PortraitComplete` — Stage 2 enrichment-only POIs (null coords) are never written to cache (Task 5a).
- F6 FIXED: `onGeocodingCancelLoad()` now clears `isEnrichingArea = false` (Task 7f).
- F7 FIXED: Stage 1 completes `stage1NamesDeferred` before rethrowing on retry exhaustion — Stage 2 is never left hanging on `await()`.
- F8 FIXED: `selectedPoi` updated in `PortraitComplete` handler so open card auto-refreshes with enriched insight (Task 7h).
- F9 FIXED: `buildPinOnlyPrompt` includes `preferredLanguage` with instruction that POI names must match across Stage 1 and Stage 2 (Task 2b).
- F10 NOTE: If retry fires (broad query), `stage1Pois` is overwritten by the retry's `PinsReady`. The user will see the broad-query pins (slightly different set). This is acceptable — retry only fires when the original area returned no POIs at all.
- F11 FIXED: `parseEnrichmentResponse` is now `internal fun` to match `internal` return type `EnrichJson` (Task 3b).
- F12 FIXED: Progress bar uses `WindowInsets.navigationBars` instead of hardcoded `80.dp` (Task 8).
- RESIDUAL RISK: Gemini name matching between Stage 1 and Stage 2 — merge is case-insensitive + trim only, no fuzzy matching. If Gemini paraphrases names, some enrichments will be missed and appended as null-coord POIs (which AreaRepositoryImpl will not cache, so no poison). Acceptable for v1; add fuzzy matching only if testing shows >20% mismatch rate.
- RESIDUAL RISK: `returnToCurrentLocation()` uses `returnToLocationJob`, not `searchJob` — a concurrent new area search won't cancel a return-to-location fetch. These can race. Pre-existing architectural issue; a `TODO(BACKLOG-MEDIUM)` is added to the call site. Not in scope for this spec.
- Stage 1 POIs shown without images — gradient fallback in card header. Images arrive after Stage 2 triggers `enrichPoisWithImages()` in `AreaRepositoryImpl`. Acceptable for v1.
