---
title: 'POI Images — Wikipedia + Gemini Wiki Hint + Gradient Fallback'
slug: 'tech-spec-poi-images'
created: '2026-03-07'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (commonMain/androidMain/iosMain)'
  - 'Ktor 3.4.0 (existing singleton HttpClient in HttpClientFactory)'
  - 'Coil 3.1.0 (coil3:coil-compose + coil3:coil-network-ktor3) — NEW'
  - 'SQLDelight (POIs stored as JSON blob — no migration needed)'
  - 'Koin (DI — follow existing dataModule single{} pattern)'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/WikipediaImageRepository.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ExpandablePoiCard.kt'
  - 'composeApp/build.gradle.kts'
  - 'gradle/libs.versions.toml'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiResponseParserTest.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/WikipediaImageRepositoryTest.kt (NEW)'
code_patterns:
  - 'POI is @Serializable — adding nullable fields with defaults automatically handles old cache entries'
  - 'AreaRepositoryImpl intercepts BucketUpdate.PortraitComplete before emitting — enrich POIs here'
  - 'WikipediaImageRepository follows GeminiAreaIntelligenceProvider pattern (inject HttpClient singleton)'
  - 'Koin DI: single { WikipediaImageRepository(get()) } in dataModule'
  - 'Wikipedia REST API: GET https://en.wikipedia.org/api/rest_v1/page/summary/{title} → thumbnail.source'
test_patterns:
  - 'commonTest with ktor-client-mock (already in test deps)'
  - 'GeminiResponseParserTest for wiki field parsing'
  - 'WikipediaImageRepositoryTest with MockEngine for happy path, 404, and missing thumbnail'
---

# Tech-Spec: POI Images — Wikipedia + Gemini Wiki Hint + Gradient Fallback

**Created:** 2026-03-07

## Overview

### Problem Statement

The POI detail card (`ExpandablePoiCard`) has a blank gradient placeholder where an image should appear. No image source, no image loading library, and no image URL field exist in the codebase. The placeholder actively looks like a broken UI to users.

### Solution

Gemini returns an optional `wiki` slug per POI in the existing POI JSON → a new `WikipediaImageRepository` (Ktor, `commonMain`) fetches the thumbnail URL from the Wikipedia REST API → the URL is persisted to the existing Room POI cache → Coil 3 (`coil3:coil-compose`, KMP-compatible) loads the image in `ExpandablePoiCard` with the vibe-colour gradient as final fallback.

### Scope

**In Scope:**
- Add `wiki: String?` to `POI` domain model, `PoiJson`, and Gemini prompt schema
- `WikipediaImageRepository` in `commonMain` using existing Ktor client
- No DB migration needed — POIs stored as JSON blob; new fields serialize automatically
- Coil 3 added to KMP build (Android + iOS) with `ImageLoader` wired to Ktor
- `ExpandablePoiCard` uses `AsyncImage` with gradient fallback
- Image URL fetched after portrait load and persisted via existing POI cache mechanism

**Out of Scope:**
- Full-screen image viewer / zoom
- Image prefetching before card is opened
- Separate image CDN or proxy

## Context for Development

### Codebase Patterns

- **POI serialization:** `POI` is `@Serializable`. `AreaRepositoryImpl` stores POIs as `json.encodeToString(pois)` blob in `area_poi_cache.pois_json`. Adding nullable fields with defaults to `POI` requires zero schema changes — existing cached blobs deserialize with nulls. `Json { ignoreUnknownKeys = true }` handles forward compat.
- **HTTP client:** `HttpClientFactory.create()` is a Koin `single {}` — shared singleton. `WikipediaImageRepository` injects it as `HttpClient`. Do NOT create a new client.
- **AreaRepositoryImpl intercept pattern:** The collect loop emits `update` then writes to cache. For `PortraitComplete`, intercept before emitting: fetch images in parallel, enrich POIs, write enriched list to cache, then emit `PortraitComplete(enrichedPois)`. This ensures first-load images appear immediately.
- **Parallel image fetching:** Use `pois.map { async { repo.getImageUrl(poi.wikiSlug, poi.name) } }.awaitAll()` inside a `coroutineScope {}` block.
- **Koin DI:** All bindings in `dataModule` in `DataModule.kt`. Use `single { WikipediaImageRepository(get()) }`.
- **Coil 3 KMP + Ktor:** `coil3:coil-compose` and `coil3:coil-network-ktor3` go in `commonMain.dependencies`. Coil 3 does NOT auto-wire itself to Ktor — you must construct an `ImageLoader` with `KtorNetworkFetcherFactory(httpClient)` and provide it via `CompositionLocalProvider(LocalImageLoader provides imageLoader)` in `App.kt`. See Task 1b.
- **HttpClient reuse (intentional):** `WikipediaImageRepository` reuses the existing Koin singleton `HttpClient` (same one used for Gemini). This avoids a second engine but means the SSE plugin is installed unnecessarily for Wikipedia calls — acceptable trade-off for MVP simplicity.
- **ExpandablePoiCard:** Receives `poi: POI` directly. Replace the placeholder `Box` (lines 83–92) with a layered `Box`: gradient as base, `AsyncImage` overlaid when `poi.imageUrl != null`. Gradient shows through on any image failure.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `App.kt` | Wrap root content with `CompositionLocalProvider(LocalImageLoader provides imageLoader)` |
| `domain/model/POI.kt` | Add `wikiSlug: String?` and `imageUrl: String?` fields |
| `data/remote/GeminiResponseParser.kt` | Add `wiki: String?` to `PoiJson`; map to `POI.wikiSlug` in `parsePoisJson` |
| `data/remote/GeminiPromptBuilder.kt` | Add `"wiki":"Article_Title"` to POI example + instruction line |
| `data/remote/WikipediaImageRepository.kt` | NEW — Ktor GET to Wikipedia REST API |
| `data/repository/AreaRepositoryImpl.kt` | Inject repo; enrich POIs before emitting `PortraitComplete` |
| `di/DataModule.kt` | Wire `WikipediaImageRepository` singleton |
| `ui/map/components/ExpandablePoiCard.kt` | Replace placeholder Box (line 83) with `AsyncImage` |
| `build.gradle.kts` | Add Coil 3 deps to `commonMain` |
| `gradle/libs.versions.toml` | Add `coil3` version + lib entries |
| `data/remote/GeminiResponseParserTest.kt` | Add `wiki` field parse test |
| `data/remote/WikipediaImageRepositoryTest.kt` | NEW — mock Ktor tests |

### Technical Decisions

1. **No SQLDelight migration.** POIs are a JSON blob — adding fields to the domain model is sufficient.
2. **Enrich before emitting PortraitComplete** (not lazily on card open). First load shows real images; no UI re-render needed.
3. **Fallback chain in `WikipediaImageRepository`:** try `wikiSlug` first; if null or returns no thumbnail, try URL-encoded `poi.name`; if still null return `null` (gradient fallback in UI).
4. **`coil3:coil-network-ktor3`** uses the existing Ktor HttpClient — no new network engine, consistent with the rest of the app.
5. **Stale-while-revalidate path** (background refresh in `AreaRepositoryImpl`) also enriches images before writing back to cache. Cache hit path already returns enriched POIs.

## Implementation Plan

### Tasks

- [x] Task 1: Add Coil 3 dependencies to build
  - File: `gradle/libs.versions.toml`
  - Action: Add version entry `coil3 = "3.1.0"` and two lib entries:
    ```toml
    coil3-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil3" }
    coil3-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil3" }
    ```
  - File: `composeApp/build.gradle.kts`
  - Action: Add both to `commonMain.dependencies {}`:
    ```kotlin
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.ktor3)
    ```
  - Notes: Both go in `commonMain` — Coil 3 is fully KMP-compatible. `coil3-network-ktor3` uses the existing Ktor engine for HTTP image loading.

- [x] Task 1b: Configure Coil `ImageLoader` with Ktor in `App.kt`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt`
  - Action: In the `App()` composable, retrieve the `HttpClient` from Koin and build a Coil `ImageLoader` with `KtorNetworkFetcherFactory`. Provide it via `CompositionLocalProvider`:
    ```kotlin
    import coil3.ImageLoader
    import coil3.compose.LocalImageLoader
    import coil3.network.ktor3.KtorNetworkFetcherFactory
    import coil3.compose.LocalPlatformContext
    import org.koin.compose.koinInject

    @Composable
    fun App(platformConfig: PlatformConfig) {
        val httpClient: HttpClient = koinInject()
        val context = LocalPlatformContext.current
        val imageLoader = remember(context) {
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient)) }
                .build()
        }
        CompositionLocalProvider(LocalImageLoader provides imageLoader) {
            // existing App content unchanged
        }
    }
    ```
  - Notes: `LocalPlatformContext` is Coil 3's KMP-safe alternative to Android's `LocalContext`. `remember(context)` ensures the ImageLoader is not recreated on recomposition. Wrap ONLY the outermost content — do not restructure App internals.

- [x] Task 2: Add `wikiSlug` and `imageUrl` fields to POI domain model
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt`
  - Action: Add two nullable fields with defaults at the end of the data class:
    ```kotlin
    val wikiSlug: String? = null,
    val imageUrl: String? = null,
    ```
  - Notes: Both default to `null` so existing cached JSON blobs deserialize cleanly without any migration. `Json { ignoreUnknownKeys = true }` already handles this.

- [x] Task 3: Add `wiki` field to `PoiJson` and map it in `parsePoisJson`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt`
  - Action 1: Add `val wiki: String? = null` to `PoiJson` data class (after `val r`).
  - Action 2: In `parsePoisJson`, add `wikiSlug = poiJson.wiki` to the `POI(...)` constructor call.

- [x] Task 4: Update Gemini prompt to include `wiki` field in POI schema
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
  - Action 1: Update the POI JSON example line to include `"wiki":"Wikipedia_Article_Title"`:
    ```
    [{"n":"Name","t":"type","v":"vibe","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5,"lat":38.7100,"lng":-9.1300,"wiki":"Wikipedia_Article_Title"}]
    ```
  - Action 2: Add an instruction line under the existing `IMPORTANT` block:
    ```
    - For each POI, include "wiki" with the exact Wikipedia article title (underscores, e.g. "Igreja_Matriz_Nossa_Senhora_da_Penha"). Only include if you are confident in the article name. Omit "wiki" rather than guessing.
    ```

- [x] Task 5: Create `WikipediaImageRepository`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/WikipediaImageRepository.kt` (NEW)
  - Action: Create the class with a two-step fallback, proper URL encoding, and 5s per-call timeout:
    ```kotlin
    package com.areadiscovery.data.remote

    import com.areadiscovery.util.AppLogger
    import io.ktor.client.HttpClient
    import io.ktor.client.plugins.timeout
    import io.ktor.client.request.get
    import io.ktor.client.statement.bodyAsText
    import io.ktor.http.encodeURLPathPart
    import io.ktor.http.isSuccess
    import kotlinx.serialization.Serializable
    import kotlinx.serialization.json.Json

    internal class WikipediaImageRepository(private val httpClient: HttpClient) {

        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class WikiSummary(val thumbnail: WikiThumbnail? = null)

        @Serializable
        private data class WikiThumbnail(val source: String = "")

        /**
         * Tries wikiSlug first (more accurate), then falls back to poiName.
         * Returns null if both attempts yield no thumbnail.
         */
        suspend fun getImageUrl(wikiSlug: String?, poiName: String): String? {
            if (wikiSlug != null) {
                val url = fetchThumbnail(wikiSlug)
                if (url != null) return url
            }
            return fetchThumbnail(poiName)
        }

        private suspend fun fetchThumbnail(title: String): String? {
            return try {
                val encoded = title.encodeURLPathPart()
                val response = httpClient.get(
                    "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
                ) {
                    timeout {
                        requestTimeoutMillis = 5_000
                        socketTimeoutMillis = 5_000
                    }
                }
                if (!response.status.isSuccess()) return null
                val summary = json.decodeFromString<WikiSummary>(response.bodyAsText())
                summary.thumbnail?.source?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                AppLogger.d { "WikipediaImageRepository: failed to fetch image for '$title': ${e.message}" }
                null
            }
        }
    }
    ```
  - Notes: `encodeURLPathPart()` is Ktor's built-in URL encoder — handles non-ASCII chars like `ã`, `é`, `ç` correctly (e.g. "São Jorge" → "S%C3%A3o_Jorge"). Per-request `timeout {}` block overrides the client-level `socketTimeoutMillis` (30s) for image calls only — prevents a stalled Wikipedia call from blocking portrait emit for 30s. The 5s limit means worst-case enrichment latency is ~5s regardless of POI count.

- [x] Task 6: Wire `WikipediaImageRepository` in `DataModule`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
  - Action 1: Add import: `import com.areadiscovery.data.remote.WikipediaImageRepository`
  - Action 2: Add singleton binding after `single { HttpClientFactory.create() }`:
    ```kotlin
    single { WikipediaImageRepository(get()) }
    ```
  - Action 3: In the existing `AreaRepositoryImpl(...)` constructor call, add the new parameter:
    ```kotlin
    single<AreaRepository> {
        AreaRepositoryImpl(
            aiProvider = get(),
            database = get(),
            scope = get(named("appScope")),
            clock = get(),
            connectivityObserver = { get<ConnectivityMonitor>().observe() },
            wikipediaImageRepository = get(),   // ADD THIS LINE
        )
    }
    ```
  - Notes: Both changes are in DataModule.kt — do them together in one file edit.

- [x] Task 7: Inject `WikipediaImageRepository` into `AreaRepositoryImpl` and enrich POIs before emitting `PortraitComplete`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt`
  - Action 1: Add constructor parameter:
    ```kotlin
    private val wikipediaImageRepository: WikipediaImageRepository,
    ```
  - Action 2: Add import: `import com.areadiscovery.data.remote.WikipediaImageRepository`
  - Action 3: Add import: `import kotlinx.coroutines.async`, `import kotlinx.coroutines.awaitAll`, `import kotlinx.coroutines.coroutineScope`
  - Action 4: Add a private helper function:
    ```kotlin
    private suspend fun enrichPoisWithImages(pois: List<POI>): List<POI> = coroutineScope {
        pois.map { poi ->
            async {
                val imageUrl = wikipediaImageRepository.getImageUrl(poi.wikiSlug, poi.name)
                poi.copy(imageUrl = imageUrl)
            }
        }.awaitAll()
    }
    ```
  - Action 5: In the cache miss `collect` block, intercept `PortraitComplete` before emitting:
    Replace:
    ```kotlin
    aiProvider.streamAreaPortrait(areaName, context).collect { update ->
        emit(update)
        if (update is BucketUpdate.BucketComplete) {
            writeToCache(update.content, areaName, language)
        }
        if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
            writePoisToCache(update.pois, areaName, language)
        }
    }
    ```
    With:
    ```kotlin
    aiProvider.streamAreaPortrait(areaName, context).collect { update ->
        if (update is BucketUpdate.PortraitComplete) {
            val enriched = if (update.pois.isNotEmpty()) enrichPoisWithImages(update.pois) else update.pois
            if (enriched.isNotEmpty()) writePoisToCache(enriched, areaName, language)
            emit(BucketUpdate.PortraitComplete(enriched))
        } else {
            emit(update)
            if (update is BucketUpdate.BucketComplete) writeToCache(update.content, areaName, language)
        }
    }
    ```
  - Action 6: In the stale-while-revalidate background refresh block (the `scope.launch {}` at line ~103), replace the `PortraitComplete` cache write as follows:
    Replace:
    ```kotlin
    if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
        withContext(ioDispatcher) {
            writePoisToCache(update.pois, areaName, language)
        }
    }
    ```
    With:
    ```kotlin
    if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
        withContext(ioDispatcher) {
            val enriched = enrichPoisWithImages(update.pois)
            writePoisToCache(enriched, areaName, language)
        }
    }
    ```
    Note: `enrichPoisWithImages` uses `coroutineScope {}` internally, which is valid inside `withContext(ioDispatcher)` — it inherits the coroutine context and all child coroutines are bounded to the `withContext` scope. The background refresh path does NOT emit enriched POIs to the UI (by design — stale-while-revalidate only updates cache for next visit).
  - Notes: `coroutineScope {}` inside a `flow {}` builder is valid — it inherits the flow's coroutine context. All POIs are fetched in parallel with a 5s per-call timeout. Typical latency ~300–600ms on good connections; worst-case ~5s on a very slow connection before the timeout fires and returns null for stuck calls.

- [x] Task 8: Replace placeholder Box in `ExpandablePoiCard` with `AsyncImage`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ExpandablePoiCard.kt`
  - Action 1: Add imports:
    ```kotlin
    import androidx.compose.ui.layout.ContentScale
    import coil3.compose.AsyncImage
    ```
    Note: verify exact import against Coil 3.1.0 — the package is `coil3.compose.AsyncImage`. If the IDE shows an unresolved reference, check `coil3.compose.rememberAsyncImagePainter` as an alternative. The module `coil3:coil-compose` must be on the classpath (Task 1) before this compiles.
  - Action 2: Replace the placeholder `Box` block (lines 82–92) with:
    ```kotlin
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        // Gradient always visible as base layer (fallback when no image)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(vibeColor.copy(alpha = 0.6f), vibeColor.copy(alpha = 0.2f))
                    )
                ),
        )
        // Image overlaid on top if URL is available
        if (poi.imageUrl != null) {
            AsyncImage(
                model = poi.imageUrl,
                contentDescription = poi.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
    ```
  - Notes: Height increased from 120dp to 160dp for better image presentation. Gradient is always rendered as base layer — if image load fails or URL is null, gradient shows through naturally. No Coil error/placeholder wiring needed.

- [x] Task 9: Add tests
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiResponseParserTest.kt`
  - Action: In the companion object, add the constant:
    ```kotlin
    const val V3_POIS_WITH_WIKI_RESPONSE = """{"type":"SAFETY","highlight":"Safe area","content":"Low crime."}
---POIS---
[{"n":"Igreja Matriz","t":"historic","v":"history","w":"Built in 1400, oldest church in the district","lat":38.7100,"lng":-9.1300,"wiki":"Igreja_Matriz"},{"n":"Local Market","t":"food","v":"character","w":"Daily produce market running since 1892","lat":38.7200,"lng":-9.1400}]"""
    ```
    Then add the test:
    ```kotlin
    @Test
    fun parseFullResponse_slimPois_parsesWikiSlug() {
        val result = parser.parseFullResponse(V3_POIS_WITH_WIKI_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertEquals("Igreja_Matriz", portrait.pois[0].wikiSlug)
        assertNull(portrait.pois[1].wikiSlug)
    }
    ```
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/WikipediaImageRepositoryTest.kt` (NEW)
  - Action: Create test class using `MockEngine` from `ktor-client-mock`:
    ```kotlin
    package com.areadiscovery.data.remote

    import io.ktor.client.HttpClient
    import io.ktor.client.engine.mock.MockEngine
    import io.ktor.client.engine.mock.respond
    import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
    import io.ktor.http.HttpHeaders
    import io.ktor.http.HttpStatusCode
    import io.ktor.http.headersOf
    import io.ktor.serialization.kotlinx.json.json
    import kotlinx.coroutines.test.runTest
    import kotlinx.serialization.json.Json
    import kotlin.test.Test
    import kotlin.test.assertEquals
    import kotlin.test.assertNull
    import kotlin.test.assertTrue

    private const val JSON_CONTENT_TYPE = "application/json"

    class WikipediaImageRepositoryTest {

        private fun buildRepo(handler: suspend MockEngine.(io.ktor.client.engine.mock.MockRequestHandleScope, io.ktor.client.request.HttpRequestData) -> io.ktor.client.engine.mock.HttpResponseData): WikipediaImageRepository {
            val client = HttpClient(MockEngine(handler)) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            return WikipediaImageRepository(client)
        }

        @Test
        fun getImageUrl_happyPath_returnsThumbnailSource() = runTest {
            val repo = buildRepo { _, _ ->
                respond(
                    content = """{"title":"Test","thumbnail":{"source":"https://upload.wikimedia.org/img.jpg","width":320,"height":240}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CONTENT_TYPE)
                )
            }
            assertEquals("https://upload.wikimedia.org/img.jpg", repo.getImageUrl(null, "Test_Place"))
        }

        @Test
        fun getImageUrl_404_returnsNull() = runTest {
            val repo = buildRepo { _, _ ->
                respond("Not found", status = HttpStatusCode.NotFound)
            }
            assertNull(repo.getImageUrl(null, "Unknown_Place"))
        }

        @Test
        fun getImageUrl_noThumbnailField_returnsNull() = runTest {
            val repo = buildRepo { _, _ ->
                respond(
                    content = """{"title":"Test","extract":"Some text with no thumbnail"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CONTENT_TYPE)
                )
            }
            assertNull(repo.getImageUrl(null, "Test_Place"))
        }

        @Test
        fun getImageUrl_networkException_returnsNullWithoutThrowing() = runTest {
            val client = HttpClient(MockEngine { throw RuntimeException("Network error") })
            val repo = WikipediaImageRepository(client)
            assertNull(repo.getImageUrl(null, "Test_Place"))
        }

        @Test
        fun getImageUrl_nonAsciiName_encodedInUrl() = runTest {
            var capturedUrl = ""
            val repo = buildRepo { request, _ ->
                capturedUrl = request.url.toString()
                respond("""{"title":"Test"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, JSON_CONTENT_TYPE))
            }
            repo.getImageUrl(null, "São Jorge")
            assertTrue(capturedUrl.contains("S%C3%A3o"), "Non-ASCII 'ã' must be percent-encoded in the URL")
        }

        @Test
        fun getImageUrl_wikiSlugSucceeds_poiNameNotCalled() = runTest {
            var callCount = 0
            val repo = buildRepo { _, _ ->
                callCount++
                respond(
                    content = """{"title":"Test","thumbnail":{"source":"https://img.example.com/photo.jpg","width":100,"height":100}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CONTENT_TYPE)
                )
            }
            val result = repo.getImageUrl("Valid_Wiki_Slug", "POI Name")
            assertEquals("https://img.example.com/photo.jpg", result)
            assertEquals(1, callCount, "Only one Wikipedia call when wikiSlug succeeds")
        }

        @Test
        fun getImageUrl_wikiSlugFails_fallsBackToPoiName() = runTest {
            var callCount = 0
            val repo = buildRepo { _, _ ->
                callCount++
                if (callCount == 1) respond("", HttpStatusCode.NotFound)
                else respond(
                    content = """{"title":"Test","thumbnail":{"source":"https://img.example.com/fallback.jpg","width":100,"height":100}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CONTENT_TYPE)
                )
            }
            val result = repo.getImageUrl("Bad_Wiki_Slug", "POI Name")
            assertEquals("https://img.example.com/fallback.jpg", result)
            assertEquals(2, callCount, "Two Wikipedia calls when wikiSlug fails and poiName fallback is used")
        }
    }
    ```

### Acceptance Criteria

- [ ] AC 1: Given Gemini returns a POI with `"wiki":"Igreja_Matriz_Nossa_Senhora_da_Penha"`, when the portrait is parsed, then `poi.wikiSlug == "Igreja_Matriz_Nossa_Senhora_da_Penha"`.

- [ ] AC 2: Given a portrait loads for an area, when `PortraitComplete` is emitted, then each POI with a valid Wikipedia article has a non-null `imageUrl` set before the event reaches the ViewModel.

- [ ] AC 3: Given a POI has `imageUrl` set, when the user taps the POI marker and the card opens, then an image is displayed in the 160dp header area using Coil `AsyncImage`.

- [ ] AC 4: Given a POI has `imageUrl == null` (Wikipedia returned no thumbnail), when the card opens, then the vibe-colour horizontal gradient is displayed in the header — no blank or broken image state.

- [ ] AC 5: Given Wikipedia returns a 404 or network error for a POI, when `enrichPoisWithImages` runs, then that POI's `imageUrl` remains `null` and other POIs in the list are unaffected.

- [ ] AC 6: Given POIs are cached with `imageUrl` values, when the same area is loaded again (cache hit), then POIs are returned from cache with `imageUrl` already populated — no Wikipedia call is made.

- [ ] AC 7: Given the app is on a device with the existing cache (pre-spec, no `imageUrl`), when the old cache entry is deserialised, then `imageUrl` and `wikiSlug` default to `null` with no crash.

- [ ] AC 8: Given a POI has `wikiSlug` set but Wikipedia returns no thumbnail for that slug, when `getImageUrl` is called, then Wikipedia is called a second time using `poi.name` as the lookup title before returning `null`.

## Additional Context

### Dependencies

- **Coil 3.1.0** — `io.coil-kt.coil3:coil-compose` + `io.coil-kt.coil3:coil-network-ktor3`. Both new. Add to `libs.versions.toml` and `build.gradle.kts`.
- **Wikipedia REST API** — `https://en.wikipedia.org/api/rest_v1/page/summary/{title}`. No auth. Rate limit 200 req/s. No SDK needed.
- **Existing Ktor HttpClient singleton** — already wired in Koin. `WikipediaImageRepository` uses `get()` to inject it.
- **`ktor-client-mock`** — already in test dependencies. Used for `WikipediaImageRepositoryTest`.

### Testing Strategy

**Unit tests (commonTest — run with `./gradlew :composeApp:test`):**
- `GeminiResponseParserTest`: add one test verifying `wiki` field is parsed to `wikiSlug`
- `WikipediaImageRepositoryTest` (new): 5 tests using `MockEngine` — happy path, 404, missing thumbnail, network exception, space-in-title encoding

**Manual device testing:**
- **Before first test run:** `adb uninstall com.areadiscovery.debug` — clears stale POI cache that has no `imageUrl` field. Without this, the cache hit path returns old POIs with `imageUrl=null` and images won't appear, masking whether the feature worked.
- Load a well-known area (e.g. a historic city centre) — expect images on landmark POIs
- Load an obscure residential suburb — expect gradient fallback on most POIs
- Load the same area twice — second load returns from cache; POI cards should show images immediately (no Wikipedia calls)
- Open a POI card while offline after a prior cached load — cached image URL should still display
- Run `./gradlew :composeApp:installDebug && adb shell am start -n com.areadiscovery.debug/com.areadiscovery.MainActivity`

**Do NOT run `PromptComparisonTest`** — prompt change is additive (new optional field), not a quality change.

### Notes

- **Risk: Wikipedia coverage** — famous landmarks get images; generic restaurants or modern businesses often don't. Gradient fallback handles gracefully. Gemini's `wiki` slug hint significantly improves hit rate for historic/cultural POIs.
- **Risk: Wikipedia thumbnail quality** — some thumbnails are small or low quality (maps, diagrams). Coil's `ContentScale.Crop` handles aspect ratio. No minimum size filter needed for MVP.
- **Risk: First-load latency** — parallel Wikipedia calls add ~300–600ms. Gemini portrait already takes 6–8s; this is an acceptable increment. Monitor on slow connections.
- **Future:** Cache `imageUrl` TTL could be extended — Wikipedia images rarely change. Current approach inherits the POI cache TTL (3 days semi-static), which is fine.
- **Future:** If Gemini doesn't return `wiki`, use POI name directly. Works well for named landmarks; less reliable for generic types. Consider adding a Gemini fallback call (`"What is the Wikipedia article for X in Y?"`) only when both wikiSlug and name-based lookup return null — deferred to v2 of this feature.
