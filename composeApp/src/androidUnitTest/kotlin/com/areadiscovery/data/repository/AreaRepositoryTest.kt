package com.areadiscovery.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.fakes.FakeAreaIntelligenceProvider
import com.areadiscovery.fakes.FakeClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AreaRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AreaDiscoveryDatabase
    private lateinit var fakeProvider: FakeAreaIntelligenceProvider
    private lateinit var fakeClock: FakeClock
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
        val testDispatcher = StandardTestDispatcher(testScope.testScheduler)
        repository = AreaRepositoryImpl(fakeProvider, database, testScope, fakeClock, testDispatcher)
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
}
