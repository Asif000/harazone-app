package com.harazone.ui.map.components

import com.harazone.domain.model.Confidence
import com.harazone.domain.model.POI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmbientTickerTest {

    private fun poi(liveStatus: String? = null) = POI(
        name = "Test", type = "cafe", description = "desc",
        confidence = Confidence.HIGH, latitude = 1.0, longitude = 2.0,
        liveStatus = liveStatus,
    )

    @Test
    fun buildTickerSlots_withOpenPois_showsOpenCount() {
        val pois = listOf(poi("open"), poi("open"), poi("closed"))
        val slots = buildTickerSlots(pois, 40.0, -74.0, emptyList(), sunsetMinutesProvider = { _, _ -> 200 })
        assertTrue(slots.any { it == "2 open nearby" })
    }

    @Test
    fun buildTickerSlots_noOpenPois_omitsOpenSlot() {
        val pois = listOf(poi("closed"), poi(null))
        val slots = buildTickerSlots(pois, 40.0, -74.0, emptyList(), sunsetMinutesProvider = { _, _ -> 200 })
        assertTrue(slots.none { it.contains("open nearby") })
    }

    @Test
    fun buildTickerSlots_sunsetWithinRange_showsSunsetSlot() {
        val slots = buildTickerSlots(listOf(poi()), 40.0, -74.0, emptyList(), sunsetMinutesProvider = { _, _ -> 45 })
        assertTrue(slots.any { it == "Sunset in 45 min" })
    }

    @Test
    fun buildTickerSlots_sunsetOutOfRange_omitsSunsetSlot() {
        val slots = buildTickerSlots(listOf(poi()), 40.0, -74.0, emptyList(), sunsetMinutesProvider = { _, _ -> 200 })
        assertTrue(slots.none { it.contains("Sunset") })
    }

    @Test
    fun buildTickerSlots_sunsetNegative_omitsSunsetSlot() {
        val slots = buildTickerSlots(listOf(poi()), 40.0, -74.0, emptyList(), sunsetMinutesProvider = { _, _ -> -1 })
        assertTrue(slots.none { it.contains("Sunset") })
    }

    @Test
    fun buildTickerSlots_withAreaHighlights_includesThem() {
        val highlights = listOf("Jazz at 9pm", "Market till 8")
        val slots = buildTickerSlots(emptyList(), 40.0, -74.0, highlights, sunsetMinutesProvider = { _, _ -> 200 })
        assertTrue(slots.contains("Jazz at 9pm"))
        assertTrue(slots.contains("Market till 8"))
    }

    @Test
    fun buildTickerSlots_blankHighlights_filtered() {
        val highlights = listOf("Jazz", "", "  ")
        val slots = buildTickerSlots(emptyList(), 40.0, -74.0, highlights, sunsetMinutesProvider = { _, _ -> 200 })
        assertEquals(1, slots.count { it.contains("Jazz") })
        assertEquals(1, slots.size)
    }

    @Test
    fun buildTickerSlots_emptyInputs_returnsEmptyList() {
        val slots = buildTickerSlots(emptyList(), 40.0, -74.0, emptyList(), sunsetMinutesProvider = { _, _ -> 200 })
        assertTrue(slots.isEmpty())
    }

    @Test
    fun buildTickerSlots_nullIsland_skipsSunset() {
        val slots = buildTickerSlots(emptyList(), 0.0, 0.0, emptyList(), sunsetMinutesProvider = { _, _ -> 45 })
        assertTrue(slots.none { it.contains("Sunset") })
    }
}
