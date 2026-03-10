package com.harazone.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasteProfileBuilderTest {

    private val nowMs = 1_000_000_000_000L
    private val dayMs = 24 * 60 * 60 * 1000L

    private fun save(type: String, daysAgo: Int = 0) = SavedPoi(
        id = "$type|1.0|${daysAgo.toDouble()}",
        name = "Test $type $daysAgo",
        type = type,
        areaName = "Test Area",
        lat = 1.0,
        lng = daysAgo.toDouble(),
        whySpecial = "test",
        savedAt = nowMs - (daysAgo.toLong() * dayMs),
    )

    @Test
    fun build_emptyList() {
        val profile = TasteProfileBuilder.build(emptyList(), nowMs)
        assertEquals(0, profile.totalSaves)
        assertTrue(profile.strongAffinities.isEmpty())
        assertTrue(profile.emergingInterests.isEmpty())
        assertNull(profile.diningStyle)
    }

    @Test
    fun build_strongAffinity_threeOrMoreSaves() {
        val saves = listOf(save("park", 1), save("park", 2), save("park", 3))
        val profile = TasteProfileBuilder.build(saves, nowMs)
        assertTrue("park" in profile.strongAffinities)
    }

    @Test
    fun build_emergingInterest_oneRecentSave() {
        val saves = listOf(save("museum", 5))
        val profile = TasteProfileBuilder.build(saves, nowMs)
        assertTrue("museum" in profile.emergingInterests)
    }

    @Test
    fun build_emergingInterest_notDuplicated_inStrongAffinity() {
        val saves = listOf(
            save("park", 1), save("park", 2), save("park", 3),
            save("park", 4),
        )
        val profile = TasteProfileBuilder.build(saves, nowMs)
        assertTrue("park" in profile.strongAffinities)
        assertTrue("park" !in profile.emergingInterests)
    }

    @Test
    fun build_notableAbsence_zeroSaves() {
        val saves = listOf(save("park", 1))
        val profile = TasteProfileBuilder.build(saves, nowMs)
        assertTrue("food" in profile.notableAbsences)
    }

    @Test
    fun build_diningStyle_twoFoodTypeSaves() {
        val saves = listOf(save("food", 1), save("food", 2))
        val profile = TasteProfileBuilder.build(saves, nowMs)
        assertEquals("food lover", profile.diningStyle)
    }

    @Test
    fun build_surprise_notableAbsencesAvailable() {
        val saves = listOf(save("park", 1), save("park", 2), save("park", 3))
        val profile = TasteProfileBuilder.build(saves, nowMs)
        assertTrue("food" in profile.notableAbsences)
    }

    @Test
    fun build_nowMs_required_recentFilter() {
        // Save 40 days ago — should NOT be in emergingInterests
        val saves = listOf(save("museum", 40))
        val profile = TasteProfileBuilder.build(saves, nowMs)
        assertTrue("museum" !in profile.emergingInterests)
    }
}
