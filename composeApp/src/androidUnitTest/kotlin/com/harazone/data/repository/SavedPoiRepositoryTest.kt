package com.harazone.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.domain.model.SavedPoi
import com.harazone.fakes.FakeClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for saved_pois DB operations.
 *
 * Background: saved_pois was added without a migration file, so on devices already at schema
 * version 5 the table was never created. Fixed by adding 5.sqm (v5 → v6 migration).
 * These tests verify the full save/unsave/observe flow using an in-memory DB to catch
 * any future schema regressions.
 */
class SavedPoiRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AreaDiscoveryDatabase
    private lateinit var repository: SavedPoiRepositoryImpl
    private val fakeClock = FakeClock(nowMs = 1_000_000_000L)
    private val testScope = TestScope()

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AreaDiscoveryDatabase.Schema.create(driver)
        database = AreaDiscoveryDatabase(driver)
        repository = SavedPoiRepositoryImpl(
            database = database,
            clock = fakeClock,
            ioDispatcher = StandardTestDispatcher(testScope.testScheduler),
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun poi(name: String = "Test POI") = SavedPoi(
        id = "$name|1.0|2.0",
        name = name,
        type = "cafe",
        areaName = "Test Area",
        lat = 1.0,
        lng = 2.0,
        whySpecial = "Great vibes",
        savedAt = 0L,
    )

    @Test
    fun `save persists POI and observeAll emits it`() = testScope.runTest {
        val poi = poi()
        repository.observeAll().test {
            assertEquals(emptyList(), awaitItem())
            repository.save(poi)
            val saved = awaitItem()
            assertEquals(1, saved.size)
            assertEquals(poi.name, saved.first().name)
            assertEquals(fakeClock.nowMs, saved.first().savedAt)
        }
    }

    @Test
    fun `unsave removes POI and observeAll emits updated list`() = testScope.runTest {
        val poi = poi()
        repository.save(poi)
        repository.observeAll().test {
            assertEquals(1, awaitItem().size)
            repository.unsave(poi.id)
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `observeSavedIds emits id set on save and unsave`() = testScope.runTest {
        val poi = poi()
        repository.observeSavedIds().test {
            assertTrue(awaitItem().isEmpty())
            repository.save(poi)
            assertTrue(awaitItem().contains(poi.id))
            repository.unsave(poi.id)
            assertFalse(awaitItem().contains(poi.id))
        }
    }

    @Test
    fun `save multiple POIs all appear in observeAll`() = testScope.runTest {
        repository.observeAll().test {
            assertEquals(emptyList(), awaitItem())
            repository.save(poi("POI A"))
            assertEquals(1, awaitItem().size)
            repository.save(poi("POI B"))
            assertEquals(2, awaitItem().size)
        }
    }

    @Test
    fun `insertOrReplace on duplicate id updates existing row`() = testScope.runTest {
        val poi = poi()
        repository.save(poi)
        repository.save(poi.copy(whySpecial = "Updated reason"))
        repository.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Updated reason", list.first().whySpecial)
        }
    }
}
