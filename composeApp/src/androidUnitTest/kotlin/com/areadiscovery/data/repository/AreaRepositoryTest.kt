package com.areadiscovery.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.fakes.FakeAreaIntelligenceProvider
import com.areadiscovery.fakes.FakeClock
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
        repository = AreaRepositoryImpl(fakeProvider, database, testScope, fakeClock)
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
            // Should get stale 6 BucketComplete + 1 PortraitComplete
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
}
