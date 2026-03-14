package com.harazone.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.ConnectivityState
import com.harazone.domain.model.DomainError
import com.harazone.domain.model.DomainErrorException
import com.harazone.domain.model.POI
import com.harazone.data.remote.WikipediaImageRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.harazone.fakes.FakeAreaIntelligenceProvider
import com.harazone.fakes.FakeClock
import com.harazone.fakes.FakeConnectivityMonitor
import com.harazone.fakes.defaultBucketEmissions
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AreaRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AreaDiscoveryDatabase
    private lateinit var fakeProvider: FakeAreaIntelligenceProvider
    private lateinit var fakeClock: FakeClock
    private lateinit var fakeConnectivity: FakeConnectivityMonitor
    private lateinit var repository: AreaRepositoryImpl
    private val testScope = TestScope()

    private val defaultContext = AreaContext(
        timeOfDay = "morning",
        dayOfWeek = "Monday",
        visitCount = 1,
        preferredLanguage = "en"
    )

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AreaDiscoveryDatabase.Schema.create(driver)
        database = AreaDiscoveryDatabase(driver)
        fakeProvider = FakeAreaIntelligenceProvider()
        fakeClock = FakeClock(nowMs = 1_000_000_000L)
        fakeConnectivity = FakeConnectivityMonitor()
        val testDispatcher = StandardTestDispatcher(testScope.testScheduler)
        repository = AreaRepositoryImpl(
            fakeProvider, database, testScope, fakeClock,
            connectivityObserver = { fakeConnectivity.observe() },
            wikipediaImageRepository = WikipediaImageRepository(HttpClient(MockEngine { _ -> respond("{}", HttpStatusCode.OK) })),
            ioDispatcher = testDispatcher
        )
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun cacheHitReturnsBucketsWithoutCallingAiProvider() = testScope.runTest {
        // Pre-populate all 6 buckets with future expires_at
        BucketType.entries.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Highlight $type",
                content = "Content $type",
                confidence = "HIGH",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() + 100_000L,
                created_at = fakeClock.nowMs()
            )
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // Should get 6 BucketComplete + 1 PortraitComplete
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        assertEquals(0, fakeProvider.callCount, "AI provider should not be called for cache hit")
    }

    @Test
    fun staleRevalidateEmitsStaleAndTriggersBackgroundRefresh() = testScope.runTest {
        // Pre-populate all 6 buckets with expired timestamps
        BucketType.entries.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Stale highlight $type",
                content = "Stale content $type",
                confidence = "MEDIUM",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() - 1L, // expired
                created_at = fakeClock.nowMs() - 100_000L
            )
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // Should get 6 stale BucketComplete + 1 PortraitComplete
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
                assertTrue(item.content.highlight.startsWith("Stale highlight"))
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        // Allow background coroutine to execute
        testScheduler.advanceUntilIdle()

        // Background refresh should have been triggered
        assertEquals(1, fakeProvider.callCount, "AI provider should be called once for background refresh")
    }

    @Test
    fun cacheMissStreamsFromAiProviderAndWritesToCache() = testScope.runTest {
        // Empty DB — no cached data

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // Should get all bucket updates from fake provider (6 BucketComplete + 1 PortraitComplete)
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        assertEquals(1, fakeProvider.callCount, "AI provider should be called once for cache miss")

        // Verify buckets were written to cache
        val cached = database.area_bucket_cacheQueries
            .getBucketsByAreaAndLanguage("Test Area", "en")
            .executeAsList()
        assertEquals(BucketType.entries.size, cached.size, "All buckets should be cached")
    }

    @Test
    fun languageSwitchTreatedAsCacheMiss() = testScope.runTest {
        // Pre-populate cache for language=en
        BucketType.entries.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "English highlight",
                content = "English content",
                confidence = "HIGH",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() + 100_000L,
                created_at = fakeClock.nowMs()
            )
        }

        // Request with language=fr
        val frContext = defaultContext.copy(preferredLanguage = "fr")
        repository.getAreaPortrait("Test Area", frContext).test {
            // Should stream from AI provider (cache miss for fr)
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        assertEquals(1, fakeProvider.callCount, "AI provider should be called for different language")
    }

    @Test
    fun cacheMissWritesCorrectTtlValues() = testScope.runTest {
        repository.getAreaPortrait("Test Area", defaultContext).test {
            // Consume all events
            repeat(BucketType.entries.size + 1) { awaitItem() }
            awaitComplete()
        }

        val cached = database.area_bucket_cacheQueries
            .getBucketsByAreaAndLanguage("Test Area", "en")
            .executeAsList()

        cached.forEach { row ->
            val bucketType = BucketType.valueOf(row.bucket_type)
            val expectedTtl = when (bucketType) {
                BucketType.HISTORY, BucketType.CHARACTER -> AreaRepositoryImpl.CACHE_TTL_STATIC_MS
                BucketType.COST, BucketType.NEARBY -> AreaRepositoryImpl.CACHE_TTL_SEMI_STATIC_MS
                BucketType.SAFETY, BucketType.WHATS_HAPPENING -> AreaRepositoryImpl.CACHE_TTL_DYNAMIC_MS
            }
            assertEquals(
                fakeClock.nowMs() + expectedTtl,
                row.expires_at,
                "TTL for $bucketType should be correct"
            )
        }
    }

    @Test
    fun unknownBucketTypeInCacheIsSkipped() = testScope.runTest {
        // Insert a row with an unknown bucket type
        database.area_bucket_cacheQueries.insertOrReplace(
            area_name = "Test Area",
            bucket_type = "UNKNOWN_TYPE",
            language = "en",
            highlight = "h",
            content = "c",
            confidence = "HIGH",
            sources_json = "[]",
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs()
        )

        // Should fall through to cache miss (not enough valid buckets)
        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        assertEquals(1, fakeProvider.callCount, "Should call AI provider when unknown types in cache")
    }

    @Test
    fun deleteExpiredBucketsCalledOnAccess() = testScope.runTest {
        // Insert expired bucket
        database.area_bucket_cacheQueries.insertOrReplace(
            area_name = "Old Area",
            bucket_type = BucketType.SAFETY.name,
            language = "en",
            highlight = "h",
            content = "c",
            confidence = "HIGH",
            sources_json = "[]",
            expires_at = fakeClock.nowMs() - 1000L, // expired
            created_at = fakeClock.nowMs() - 100_000L
        )

        // Access a different area — triggers cleanup
        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size + 1) { awaitItem() }
            awaitComplete()
        }

        // The expired "Old Area" bucket should have been cleaned up
        val oldCached = database.area_bucket_cacheQueries
            .getBucketsByAreaAndLanguage("Old Area", "en")
            .executeAsList()
        assertEquals(0, oldCached.size, "Expired buckets should be cleaned up")
    }

    @Test
    fun cacheHitWithExtraUnknownBucketTypeStillHits() = testScope.runTest {
        // Pre-populate all 6 known buckets (valid)
        BucketType.entries.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Highlight $type",
                content = "Content $type",
                confidence = "HIGH",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() + 100_000L,
                created_at = fakeClock.nowMs()
            )
        }
        // Add an extra unknown bucket type (e.g., from a newer app version)
        database.area_bucket_cacheQueries.insertOrReplace(
            area_name = "Test Area",
            bucket_type = "FUTURE_BUCKET",
            language = "en",
            highlight = "h",
            content = "c",
            confidence = "HIGH",
            sources_json = "[]",
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs()
        )

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // Should get 6 known BucketComplete (unknown skipped) + PortraitComplete
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        // Should be a cache hit — AI provider NOT called
        assertEquals(0, fakeProvider.callCount, "Cache hit should work even with extra unknown bucket types")
    }

    @Test
    fun mixedStaleAndValidEmitsBothAndTriggersRefresh() = testScope.runTest {
        val dynamicTypes = listOf(BucketType.SAFETY, BucketType.WHATS_HAPPENING, BucketType.COST)
        val staticTypes = listOf(BucketType.HISTORY, BucketType.CHARACTER, BucketType.NEARBY)

        // 3 stale (dynamic TTL expired)
        dynamicTypes.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Stale $type",
                content = "Stale content $type",
                confidence = "MEDIUM",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() - 1L,
                created_at = fakeClock.nowMs() - 100_000L
            )
        }
        // 3 valid (static TTL still fresh)
        staticTypes.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Valid $type",
                content = "Valid content $type",
                confidence = "HIGH",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() + 100_000L,
                created_at = fakeClock.nowMs()
            )
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // (a) Stale emitted first
            repeat(dynamicTypes.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
                assertTrue(item.content.highlight.startsWith("Stale"), "Stale buckets should come first")
            }
            // (b) Then valid
            repeat(staticTypes.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
                assertTrue(item.content.highlight.startsWith("Valid"), "Valid buckets should follow stale")
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        // (c) Background refresh triggered
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeProvider.callCount, "Background refresh should be triggered for mixed stale/valid")
    }

    // --- Resilience tests (Story 2.4) ---

    @Test
    fun offlineWithCacheServesCachedContentWithNote() = testScope.runTest {
        fakeConnectivity.setState(ConnectivityState.Offline)

        // Pre-populate stale cache
        BucketType.entries.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Cached $type",
                content = "Cached content $type",
                confidence = "MEDIUM",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() - 1L,
                created_at = fakeClock.nowMs() - 100_000L
            )
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val note = awaitItem()
            assertIs<BucketUpdate.ContentAvailabilityNote>(note)
            assertEquals("You're offline — showing last known content", note.message)
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        assertEquals(0, fakeProvider.callCount, "AI provider should NOT be called when offline")
    }

    @Test
    fun offlineNoCacheEmitsNoteAndPortraitComplete() = testScope.runTest {
        fakeConnectivity.setState(ConnectivityState.Offline)

        repository.getAreaPortrait("Test Area", defaultContext).test {
            val note = awaitItem()
            assertIs<BucketUpdate.ContentAvailabilityNote>(note)
            assertEquals("No content available offline for this area", note.message)
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        assertEquals(0, fakeProvider.callCount, "AI provider should NOT be called when offline")
    }

    @Test
    fun aiFailureWithPartialCacheServesCachedWithNote() = testScope.runTest {
        fakeConnectivity.setState(ConnectivityState.Online)

        // Insert 3 valid buckets (not all 6 — so not a full cache hit)
        val partialTypes = listOf(BucketType.HISTORY, BucketType.CHARACTER, BucketType.SAFETY)
        partialTypes.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Cached $type",
                content = "Cached content $type",
                confidence = "HIGH",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() + 100_000L,
                created_at = fakeClock.nowMs()
            )
        }

        // AI provider throws
        fakeProvider.responseFlow = flow {
            throw DomainErrorException(DomainError.NetworkError("Connection failed"))
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // Catch block re-queries DB — finds 3 cached entries
            repeat(partialTypes.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val note = awaitItem()
            assertIs<BucketUpdate.ContentAvailabilityNote>(note)
            assertEquals("Content from cache — may not be current", note.message)
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }
    }

    @Test
    fun aiFailureNoCacheEmitsNoteAndPortraitComplete() = testScope.runTest {
        fakeConnectivity.setState(ConnectivityState.Online)

        // No cache, AI fails
        fakeProvider.responseFlow = flow {
            throw DomainErrorException(DomainError.NetworkError("Connection failed"))
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            val note = awaitItem()
            assertIs<BucketUpdate.ContentAvailabilityNote>(note)
            assertEquals("Could not load area content — please try again", note.message)
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }
    }

    // --- POI cache tests ---

    private val mockPois = listOf(
        POI("Cafe Roma", "cafe", "Good coffee", Confidence.HIGH, 40.7128, -74.0060),
        POI("Central Park", "park", "Large urban park", Confidence.MEDIUM, 40.7829, -73.9654),
    )

    private fun populateAllBucketsValid(areaName: String = "Test Area") {
        BucketType.entries.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = areaName,
                bucket_type = type.name,
                language = "en",
                highlight = "Highlight $type",
                content = "Content $type",
                confidence = "HIGH",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() + 100_000L,
                created_at = fakeClock.nowMs()
            )
        }
    }

    private fun populateAllBucketsStale(areaName: String = "Test Area") {
        BucketType.entries.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = areaName,
                bucket_type = type.name,
                language = "en",
                highlight = "Stale $type",
                content = "Stale content $type",
                confidence = "MEDIUM",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() - 1L,
                created_at = fakeClock.nowMs() - 100_000L
            )
        }
    }

    private fun populatePoiCache(areaName: String = "Test Area", expiresAt: Long? = null) {
        val json = kotlinx.serialization.json.Json.encodeToString(mockPois)
        database.area_poi_cacheQueries.insertOrReplacePois(
            area_name = areaName,
            language = "en",
            pois_json = json,
            expires_at = expiresAt ?: (fakeClock.nowMs() + 100_000L),
            created_at = fakeClock.nowMs()
        )
    }

    @Test
    fun poisPersistedToCacheOnAiStreamPortraitComplete() = testScope.runTest {
        // Setup: AI emits buckets + PortraitComplete with POIs
        val emissionsWithPois = defaultBucketEmissions().map {
            if (it is BucketUpdate.PortraitComplete) BucketUpdate.PortraitComplete(pois = mockPois)
            else it
        }
        fakeProvider.emissions = emissionsWithPois

        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) { awaitItem() }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            // POIs are enriched with imageUrl (MapTiler satellite tile ref since mock Wikipedia returns no thumbnail)
            assertEquals(mockPois.size, portrait.pois.size)
            assertEquals(mockPois[0].name, portrait.pois[0].name)
            assertEquals(mockPois[1].name, portrait.pois[1].name)
            // H-4: verify MapTiler fallback actually populates imageUrl (resolved to real URL before emitting)
            assertNotNull(portrait.pois[0].imageUrl, "MapTiler fallback should populate imageUrl")
            assertTrue(portrait.pois[0].imageUrl!!.contains("maptiler.com"), "imageUrl should be a resolved MapTiler URL")
            assertNotNull(portrait.pois[1].imageUrl, "MapTiler fallback should populate imageUrl for second POI")
            awaitComplete()
        }

        // Verify POIs were persisted to cache
        val cached = database.area_poi_cacheQueries.getPois("Test Area", "en").executeAsOneOrNull()
        assertTrue(cached != null, "POI cache entry should exist")
        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<List<POI>>(cached!!.pois_json)
        assertEquals(mockPois.size, decoded.size)
        assertEquals(mockPois[0].name, decoded[0].name)
    }

    @Test
    fun poisRestoredOnFullCacheHit() = testScope.runTest {
        populateAllBucketsValid()
        populatePoiCache()

        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) { awaitItem() }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(mockPois, portrait.pois)
            awaitComplete()
        }

        assertEquals(0, fakeProvider.callCount, "AI provider should not be called for cache hit")
    }

    @Test
    fun poisRestoredOnStaleWhileRevalidatePath() = testScope.runTest {
        populateAllBucketsStale()
        populatePoiCache()

        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) { awaitItem() }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(mockPois, portrait.pois)
            awaitComplete()
        }
    }

    @Test
    fun emptyPoisReturnedWhenPoiCacheExpired() = testScope.runTest {
        populateAllBucketsValid()
        populatePoiCache(expiresAt = fakeClock.nowMs() - 1L)

        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) { awaitItem() }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(emptyList(), portrait.pois)
            awaitComplete()
        }
    }

    @Test
    fun poisRestoredOnErrorFallbackPath() = testScope.runTest {
        fakeConnectivity.setState(ConnectivityState.Online)

        // Insert partial valid buckets (not all 6 — triggers AI call path)
        val partialTypes = listOf(BucketType.HISTORY, BucketType.CHARACTER, BucketType.SAFETY)
        partialTypes.forEach { type ->
            database.area_bucket_cacheQueries.insertOrReplace(
                area_name = "Test Area",
                bucket_type = type.name,
                language = "en",
                highlight = "Cached $type",
                content = "Cached content $type",
                confidence = "HIGH",
                sources_json = "[]",
                expires_at = fakeClock.nowMs() + 100_000L,
                created_at = fakeClock.nowMs()
            )
        }
        populatePoiCache()

        // AI provider throws
        fakeProvider.responseFlow = flow {
            throw DomainErrorException(DomainError.NetworkError("Connection failed"))
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(partialTypes.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val note = awaitItem()
            assertIs<BucketUpdate.ContentAvailabilityNote>(note)
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(mockPois, portrait.pois, "POIs should be served from cache despite AI failure")
            awaitComplete()
        }
    }

    @Test
    fun cacheMissEnrichesPoisWithWikipediaImagesBeforeEmitting() = testScope.runTest {
        // Create a repository with a WikipediaImageRepository that returns a real thumbnail
        val wikiMockEngine = MockEngine { _ ->
            respond(
                content = """{"title":"Test","thumbnail":{"source":"https://upload.wikimedia.org/enriched.jpg","width":320,"height":240}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val testDispatcher = StandardTestDispatcher(testScope.testScheduler)
        val enrichingRepo = AreaRepositoryImpl(
            aiProvider = fakeProvider,
            database = database,
            scope = testScope,
            clock = fakeClock,
            connectivityObserver = { fakeConnectivity.observe() },
            wikipediaImageRepository = WikipediaImageRepository(HttpClient(wikiMockEngine)),
            ioDispatcher = testDispatcher,
        )

        // AI provider emits POIs with wikiSlug set
        val poisWithWiki = listOf(
            POI("Castle", "historic", "Old castle", Confidence.HIGH, 40.0, -9.0, wikiSlug = "Castle_Test"),
        )
        fakeProvider.emissions = defaultBucketEmissions().map {
            if (it is BucketUpdate.PortraitComplete) BucketUpdate.PortraitComplete(pois = poisWithWiki)
            else it
        }

        enrichingRepo.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) { awaitItem() }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(1, portrait.pois.size)
            assertNotNull(portrait.pois[0].imageUrl, "imageUrl should be populated by Wikipedia enrichment")
            assertEquals("https://upload.wikimedia.org/enriched.jpg", portrait.pois[0].imageUrl)
            awaitComplete()
        }
    }

    @Test
    fun cacheMissMultiPoiMixedWikiResults_failedPoiGetsMapTilerOthersUnaffected() = testScope.runTest {
        // POI with no wiki slug → all wiki lookups 404 → gets MapTiler fallback
        // POI with valid wiki slug → returns thumbnail
        val wikiMockEngine = MockEngine { request ->
            val url = request.url.toString()
            if (url.contains("Good_Slug") || url.contains("Famous")) {
                respond(
                    content = """{"title":"Test","thumbnail":{"source":"https://upload.wikimedia.org/good.jpg","width":320,"height":240}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond("Not found", status = HttpStatusCode.NotFound)
            }
        }
        val testDispatcher = StandardTestDispatcher(testScope.testScheduler)
        val mixedRepo = AreaRepositoryImpl(
            aiProvider = fakeProvider,
            database = database,
            scope = testScope,
            clock = fakeClock,
            connectivityObserver = { fakeConnectivity.observe() },
            wikipediaImageRepository = WikipediaImageRepository(HttpClient(wikiMockEngine)),
            ioDispatcher = testDispatcher,
        )

        val mixedPois = listOf(
            POI("Unknown Place", "cafe", "Nice", Confidence.HIGH, 40.0, -9.0, wikiSlug = "Bad_Slug"),
            POI("Famous Place", "landmark", "Great", Confidence.HIGH, 41.0, -8.0, wikiSlug = "Good_Slug"),
        )
        fakeProvider.emissions = defaultBucketEmissions().map {
            if (it is BucketUpdate.PortraitComplete) BucketUpdate.PortraitComplete(pois = mixedPois)
            else it
        }

        mixedRepo.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) { awaitItem() }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(2, portrait.pois.size)
            // POI 1: all wiki tiers failed -> resolved MapTiler URL
            assertNotNull(portrait.pois[0].imageUrl, "Failed POI should get MapTiler fallback")
            assertTrue(portrait.pois[0].imageUrl!!.contains("maptiler.com"), "Failed POI imageUrl should be a resolved MapTiler URL")
            // POI 2: wiki succeeded
            assertEquals("https://upload.wikimedia.org/good.jpg", portrait.pois[1].imageUrl)
            awaitComplete()
        }
    }

    @Test
    fun cacheHitDoesNotCallWikipediaImageRepository() = testScope.runTest {
        var wikiCallCount = 0
        val countingEngine = MockEngine { _ ->
            wikiCallCount++
            respond("{}", HttpStatusCode.OK)
        }
        val testDispatcher = StandardTestDispatcher(testScope.testScheduler)
        val countingRepo = AreaRepositoryImpl(
            aiProvider = fakeProvider,
            database = database,
            scope = testScope,
            clock = fakeClock,
            connectivityObserver = { fakeConnectivity.observe() },
            wikipediaImageRepository = WikipediaImageRepository(HttpClient(countingEngine)),
            ioDispatcher = testDispatcher,
        )

        populateAllBucketsValid()
        populatePoiCache()

        countingRepo.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) { awaitItem() }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(mockPois, portrait.pois)
            awaitComplete()
        }

        assertEquals(0, wikiCallCount, "Wikipedia should not be called on cache hit")
    }

    /**
     * Regression test: two-stage pipeline (PinsReady + PortraitComplete) must cache POIs
     * so that restarting the app does NOT re-run the Gemini query.
     *
     * Root cause (fixed): PinsReady was emitted but never cached; PortraitComplete Stage 2
     * had null coords so hasCoords was false and wasn't cached either. Second call always
     * hit AI provider again.
     */
    @Test
    fun twoStagePipelineCachesPoisAndServesFromCacheOnSecondCall() = testScope.runTest {
        // Stage 1: PinsReady with coords (slim POIs from Gemini)
        val stage1Pois = listOf(
            POI("Cafe Roma", "cafe", "Good coffee", Confidence.HIGH, 40.7128, -74.0060),
            POI("Central Park", "park", "Large park", Confidence.MEDIUM, 40.7829, -73.9654),
        )
        // Stage 2: PortraitComplete with enrichment-only POIs (no coords — matches real behavior)
        val stage2Pois = listOf(
            POI("Cafe Roma", "cafe", "Good coffee with a vibe", Confidence.HIGH, latitude = null, longitude = null, vibe = "Character"),
            POI("Central Park", "park", "Large park for walks", Confidence.MEDIUM, latitude = null, longitude = null, vibe = "History"),
        )

        // Configure provider to emit two-stage flow (no BucketComplete — matches real pipeline)
        fakeProvider.responseFlow = flow {
            emit(BucketUpdate.PinsReady(stage1Pois))
            emit(BucketUpdate.PortraitComplete(stage2Pois))
        }

        // First call — should hit AI provider
        repository.getAreaPortrait("Test Area", defaultContext).test {
            val pins = awaitItem()
            assertIs<BucketUpdate.PinsReady>(pins)
            assertEquals(stage1Pois.size, pins.pois.size)

            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            // Merged POIs should have Stage 1 coords + Stage 2 enrichment
            assertEquals(stage1Pois.size, portrait.pois.size)
            assertEquals(stage1Pois[0].latitude, portrait.pois[0].latitude, "Coords preserved from Stage 1")
            assertEquals("Character", portrait.pois[0].vibe, "Vibe merged from Stage 2")
            assertEquals("Good coffee with a vibe", portrait.pois[0].description, "Description merged from Stage 2")
            awaitComplete()
        }
        assertEquals(1, fakeProvider.callCount, "First call should hit AI provider")

        // Verify merged POIs were cached (coords + enrichment)
        val cached = requireNotNull(
            database.area_poi_cacheQueries.getPois("Test Area", "en").executeAsOneOrNull(),
            { "POIs should be cached after two-stage pipeline" }
        )
        val cachedPois = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<List<POI>>(cached.pois_json)
        assertEquals(stage1Pois[0].latitude, cachedPois[0].latitude, "Cached POI should have Stage 1 coords")
        assertEquals("Character", cachedPois[0].vibe, "Cached POI should have Stage 2 vibe")

        // Second call — should serve from POI cache, NOT call AI provider
        repository.getAreaPortrait("Test Area", defaultContext).test {
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(stage1Pois.size, portrait.pois.size, "Should serve cached POIs")
            assertEquals(stage1Pois[0].name, portrait.pois[0].name)
            assertEquals("Character", portrait.pois[0].vibe, "Cached POI should retain merged vibe")
            assertNotNull(portrait.pois[0].latitude, "Cached POI should retain coords")
            awaitComplete()
        }
        assertEquals(1, fakeProvider.callCount, "Second call should NOT hit AI provider — POIs served from cache")
    }

    @Test
    fun twoStagePipeline_stage2OnlyPoisAreAppended() = testScope.runTest {
        // Stage 1: two POIs
        val stage1Pois = listOf(
            POI("Cafe Roma", "cafe", "Good coffee", Confidence.HIGH, 40.7128, -74.0060),
            POI("Central Park", "park", "Large park", Confidence.MEDIUM, 40.7829, -73.9654),
        )
        // Stage 2: one matching + one new POI not in Stage 1
        val stage2Pois = listOf(
            POI("Cafe Roma", "cafe", "Good coffee enriched", Confidence.HIGH, latitude = null, longitude = null, vibe = "Character"),
            POI("Hidden Gem", "bar", "Secret bar", Confidence.HIGH, latitude = null, longitude = null, vibe = "Character"),
        )

        fakeProvider.responseFlow = flow {
            emit(BucketUpdate.PinsReady(stage1Pois))
            emit(BucketUpdate.PortraitComplete(stage2Pois))
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            val pins = awaitItem()
            assertIs<BucketUpdate.PinsReady>(pins)

            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            // Should have 3 POIs: 2 merged + 1 appended from Stage 2
            assertEquals(3, portrait.pois.size, "Stage 2-only POIs must be appended")
            assertEquals("Cafe Roma", portrait.pois[0].name)
            assertEquals("Central Park", portrait.pois[1].name)
            assertEquals("Hidden Gem", portrait.pois[2].name)
            // Merged POI should have Stage 2 enrichment
            assertEquals("Character", portrait.pois[0].vibe)
            assertEquals("Good coffee enriched", portrait.pois[0].description)
            // Appended POI should retain its data
            assertEquals("Secret bar", portrait.pois[2].description)
            awaitComplete()
        }
    }

    @Test
    fun poiOnlyCacheHit_unenrichedPoisEmittedImmediately() = testScope.runTest {
        // Write unenriched POIs (imageUrl = null) to POI cache — simulates Stage 1 cached before app kill
        val unenrichedPois = listOf(
            POI("Cafe Roma", "cafe", "Good coffee", Confidence.HIGH, 40.7128, -74.0060, imageUrl = null),
        )
        val json = kotlinx.serialization.json.Json.encodeToString(unenrichedPois)
        database.area_poi_cacheQueries.insertOrReplacePois(
            area_name = "Test Area",
            language = "en",
            pois_json = json,
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs()
        )

        // No bucket cache — pure POI-only path should emit immediately (not re-query AI)
        repository.getAreaPortrait("Test Area", defaultContext).test {
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(1, portrait.pois.size)
            assertEquals("Cafe Roma", portrait.pois[0].name)
            assertEquals(40.7128, portrait.pois[0].latitude)
            awaitComplete()
        }

        // AI provider should NOT have been called — POI-only cache hit path
        assertEquals(0, fakeProvider.callCount, "AI provider should not be called on POI-only cache hit")
    }

    @Test
    fun poiOnlyCacheHit_enrichedPoisSkipBackgroundEnrich() = testScope.runTest {
        // Write already-enriched POIs (imageUrl is set) to POI cache
        val enrichedPois = listOf(
            POI("Cafe Roma", "cafe", "Good coffee", Confidence.HIGH, 40.7128, -74.0060, imageUrl = "https://existing.jpg"),
        )
        val json = kotlinx.serialization.json.Json.encodeToString(enrichedPois)
        database.area_poi_cacheQueries.insertOrReplacePois(
            area_name = "Test Area",
            language = "en",
            pois_json = json,
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs()
        )

        repository.getAreaPortrait("Test Area", defaultContext).test {
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(1, portrait.pois.size)
            // Already enriched — should have the resolved MapTiler URL or existing URL
            assertNotNull(portrait.pois[0].imageUrl, "Already-enriched POIs should keep their imageUrl")
            awaitComplete()
        }

        assertEquals(0, fakeProvider.callCount, "AI provider should not be called on POI-only cache hit")
    }

    // --- 3-Pin batch pagination regression tests ---

    private val batch0Pois = listOf(
        POI("Cafe Roma", "cafe", "Good coffee", Confidence.HIGH, 40.7128, -74.0060, imageUrl = "https://img1.jpg"),
        POI("Central Park", "park", "Large park", Confidence.MEDIUM, 40.7829, -73.9654, imageUrl = "https://img2.jpg"),
        POI("Jazz Club", "music", "Live jazz", Confidence.HIGH, 40.7500, -73.9800, imageUrl = "https://img3.jpg"),
    )
    private val batch1Pois = listOf(
        POI("Bookshop", "shop", "Rare books", Confidence.HIGH, 40.7200, -74.0100, imageUrl = "https://img4.jpg"),
        POI("Art Gallery", "gallery", "Modern art", Confidence.MEDIUM, 40.7300, -74.0200, imageUrl = "https://img5.jpg"),
        POI("Rooftop Bar", "bar", "Great views", Confidence.HIGH, 40.7400, -74.0300, imageUrl = "https://img6.jpg"),
    )
    private val batch2Pois = listOf(
        POI("History Museum", "museum", "Local history", Confidence.HIGH, 40.7600, -73.9500, imageUrl = "https://img7.jpg"),
        POI("Sushi Spot", "restaurant", "Fresh sushi", Confidence.MEDIUM, 40.7700, -73.9600, imageUrl = "https://img8.jpg"),
        POI("Vinyl Records", "shop", "Classic vinyl", Confidence.HIGH, 40.7100, -74.0400, imageUrl = "https://img9.jpg"),
    )

    /**
     * Regression: Background batch POIs must be cached so that on app relaunch,
     * the POI cache contains all 9 POIs (3 batches × 3 POIs), not just batch 0.
     */
    @Test
    fun cacheMiss_backgroundBatchPoisAreCachedForRelaunch() = testScope.runTest {
        val vibes = listOf(com.harazone.domain.model.DynamicVibe("Art", "🎨"))
        fakeProvider.responseFlow = flow {
            emit(BucketUpdate.VibesReady(vibes, batch0Pois))
            emit(BucketUpdate.PortraitComplete(batch0Pois))
            emit(BucketUpdate.BackgroundBatchReady(batch1Pois, batchIndex = 1))
            emit(BucketUpdate.BackgroundEnrichmentComplete(batch1Pois, batchIndex = 1))
            emit(BucketUpdate.BackgroundBatchReady(batch2Pois, batchIndex = 2))
            emit(BucketUpdate.BackgroundEnrichmentComplete(batch2Pois, batchIndex = 2))
            emit(BucketUpdate.BackgroundFetchComplete)
        }

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // Consume all events
            while (true) {
                val item = awaitItem()
                if (item is BucketUpdate.BackgroundFetchComplete) break
            }
            awaitComplete()
        }

        // Verify all 9 POIs were cached (batch 0 + batch 1 + batch 2)
        val cached = database.area_poi_cacheQueries.getPois("Test Area", "en").executeAsOneOrNull()
        assertNotNull(cached, "POI cache should exist after background batches")
        val cachedPois = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<List<POI>>(cached!!.pois_json)
        assertEquals(9, cachedPois.size, "All 9 POIs (3 batches × 3) should be cached")
        // Verify batch 0 POIs
        assertTrue(cachedPois.any { it.name == "Cafe Roma" }, "Batch 0 POI should be cached")
        // Verify batch 1 POIs
        assertTrue(cachedPois.any { it.name == "Bookshop" }, "Batch 1 POI should be cached")
        // Verify batch 2 POIs
        assertTrue(cachedPois.any { it.name == "History Museum" }, "Batch 2 POI should be cached")
    }

    /**
     * Regression: On POI cache hit with 9 cached POIs, the repository must emit
     * batch events (BackgroundBatchReady) so pagination controls appear on relaunch.
     * Previously only PortraitComplete was emitted with all POIs, so batch nav was hidden.
     */
    @Test
    fun poiCacheHit_emitsBatchEventsForPagination() = testScope.runTest {
        // Pre-populate cache with 9 POIs (simulates prior session with 3 batches)
        val allPois = batch0Pois + batch1Pois + batch2Pois
        val vibes = listOf(com.harazone.domain.model.DynamicVibe("Art", "🎨"))
        val poisJson = kotlinx.serialization.json.Json.encodeToString(allPois)
        database.area_poi_cacheQueries.insertOrReplacePois(
            area_name = "Test Area",
            language = "en",
            pois_json = poisJson,
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs(),
        )
        // Also cache vibes so VibesReady is emitted
        val vibesJson = kotlinx.serialization.json.Json.encodeToString(vibes)
        database.area_vibe_cacheQueries.insertOrReplaceVibes(
            area_name = "Test Area",
            language = "en",
            vibes_json = vibesJson,
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs(),
        )

        repository.getAreaPortrait("Test Area", defaultContext).test {
            // 1. VibesReady with batch 0 only (3 POIs)
            val vibesReady = awaitItem()
            assertIs<BucketUpdate.VibesReady>(vibesReady)
            assertEquals(3, vibesReady.pois.size, "VibesReady should contain batch 0 (3 POIs)")
            assertEquals("Cafe Roma", vibesReady.pois[0].name)

            // 2. PortraitComplete with batch 0
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(3, portrait.pois.size, "PortraitComplete should contain batch 0 (3 POIs)")

            // 3. BackgroundBatchReady for batch 1
            val batch1 = awaitItem()
            assertIs<BucketUpdate.BackgroundBatchReady>(batch1)
            assertEquals(1, batch1.batchIndex)
            assertEquals(3, batch1.pois.size)
            assertEquals("Bookshop", batch1.pois[0].name)

            // 4. BackgroundBatchReady for batch 2
            val batch2 = awaitItem()
            assertIs<BucketUpdate.BackgroundBatchReady>(batch2)
            assertEquals(2, batch2.batchIndex)
            assertEquals(3, batch2.pois.size)
            assertEquals("History Museum", batch2.pois[0].name)

            // 5. BackgroundFetchComplete
            val complete = awaitItem()
            assertIs<BucketUpdate.BackgroundFetchComplete>(complete)

            awaitComplete()
        }

        // AI provider should NOT be called — served from cache
        assertEquals(0, fakeProvider.callCount, "AI provider should not be called on cache hit with batches")
    }

    /**
     * Regression: POI cache with exactly 3 POIs should NOT emit batch events
     * (only 1 batch = no pagination needed).
     */
    @Test
    fun poiCacheHit_singleBatchDoesNotEmitBatchEvents() = testScope.runTest {
        val vibes = listOf(com.harazone.domain.model.DynamicVibe("Art", "🎨"))
        val poisJson = kotlinx.serialization.json.Json.encodeToString(batch0Pois)
        database.area_poi_cacheQueries.insertOrReplacePois(
            area_name = "Test Area",
            language = "en",
            pois_json = poisJson,
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs(),
        )
        val vibesJson = kotlinx.serialization.json.Json.encodeToString(vibes)
        database.area_vibe_cacheQueries.insertOrReplaceVibes(
            area_name = "Test Area",
            language = "en",
            vibes_json = vibesJson,
            expires_at = fakeClock.nowMs() + 100_000L,
            created_at = fakeClock.nowMs(),
        )

        repository.getAreaPortrait("Test Area", defaultContext).test {
            val vibesReady = awaitItem()
            assertIs<BucketUpdate.VibesReady>(vibesReady)
            assertEquals(3, vibesReady.pois.size)

            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            assertEquals(3, portrait.pois.size)

            // No batch events — only 1 batch
            awaitComplete()
        }

        assertEquals(0, fakeProvider.callCount)
    }

    @Test
    fun onlineNormalPathUnchanged() = testScope.runTest {
        fakeConnectivity.setState(ConnectivityState.Online)

        // Cache miss, provider succeeds — normal path
        repository.getAreaPortrait("Test Area", defaultContext).test {
            repeat(BucketType.entries.size) {
                val item = awaitItem()
                assertIs<BucketUpdate.BucketComplete>(item)
            }
            val portrait = awaitItem()
            assertIs<BucketUpdate.PortraitComplete>(portrait)
            awaitComplete()
        }

        assertEquals(1, fakeProvider.callCount, "AI provider should be called once for cache miss")

        // Verify cache was populated
        val cached = database.area_bucket_cacheQueries
            .getBucketsByAreaAndLanguage("Test Area", "en")
            .executeAsList()
        assertEquals(BucketType.entries.size, cached.size)
    }
}
