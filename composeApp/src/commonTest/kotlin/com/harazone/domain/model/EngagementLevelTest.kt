package com.harazone.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EngagementLevelTest {

    private val nowMs = 1_000_000_000_000L
    private val dayMs = 24 * 60 * 60 * 1000L

    private fun save(daysAgo: Int = 0) = SavedPoi(
        id = "test|1.0|${daysAgo.toDouble()}",
        name = "Test",
        type = "park",
        areaName = "Test Area",
        lat = 1.0,
        lng = daysAgo.toDouble(),
        whySpecial = "test",
        savedAt = nowMs - (daysAgo.toLong() * dayMs),
    )

    private fun saves(count: Int, daysAgo: Int = 0): List<SavedPoi> =
        (1..count).map { i ->
            SavedPoi(
                id = "test$i|$i.0|$i.0",
                name = "Test$i",
                type = "park",
                areaName = "Test Area",
                lat = i.toDouble(),
                lng = i.toDouble(),
                whySpecial = "test",
                savedAt = nowMs - (daysAgo.toLong() * dayMs),
            )
        }

    @Test
    fun from_emptySaves() {
        assertEquals(EngagementLevel.FRESH, EngagementLevel.from(emptyList(), nowMs))
    }

    @Test
    fun from_oneSave() {
        assertEquals(EngagementLevel.LIGHT, EngagementLevel.from(listOf(save()), nowMs))
    }

    @Test
    fun from_fiveSaves() {
        assertEquals(EngagementLevel.LIGHT, EngagementLevel.from(saves(5), nowMs))
    }

    @Test
    fun from_sixSaves() {
        assertEquals(EngagementLevel.REGULAR, EngagementLevel.from(saves(6), nowMs))
    }

    @Test
    fun from_twentyNineSaves() {
        assertEquals(EngagementLevel.REGULAR, EngagementLevel.from(saves(29), nowMs))
    }

    @Test
    fun from_thirtySaves() {
        assertEquals(EngagementLevel.POWER, EngagementLevel.from(saves(30), nowMs))
    }

    @Test
    fun from_dormant_14daysExact() {
        // Exactly 14 days = NOT dormant (> 14 days is DORMANT)
        val result = EngagementLevel.from(saves(30, daysAgo = 14), nowMs)
        assertNotEquals(EngagementLevel.DORMANT, result)
    }

    @Test
    fun from_dormant_15days() {
        assertEquals(EngagementLevel.DORMANT, EngagementLevel.from(saves(5, daysAgo = 15), nowMs))
    }

    @Test
    fun from_dormant_overridesPower() {
        assertEquals(EngagementLevel.DORMANT, EngagementLevel.from(saves(50, daysAgo = 20), nowMs))
    }

    @Test
    fun from_singleSave_dormant() {
        assertEquals(EngagementLevel.DORMANT, EngagementLevel.from(listOf(save(daysAgo = 20)), nowMs))
    }
}
