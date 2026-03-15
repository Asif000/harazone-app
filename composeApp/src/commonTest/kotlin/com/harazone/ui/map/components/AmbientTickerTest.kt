package com.harazone.ui.map.components

import com.harazone.domain.model.Confidence
import com.harazone.domain.model.POI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmbientTickerTest {

    private fun poi(liveStatus: String? = null, hours: String? = null) = POI(
        name = "Test", type = "cafe", description = "desc",
        confidence = Confidence.HIGH, latitude = 1.0, longitude = 2.0,
        liveStatus = liveStatus, hours = hours,
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
    fun buildTickerSlots_emptyInputs_returnsFallback() {
        val slots = buildTickerSlots(emptyList(), 40.0, -74.0, emptyList(), sunsetMinutesProvider = { _, _ -> 200 })
        assertEquals(1, slots.size)
        assertEquals("Exploring area\u2026", slots.first())
    }

    @Test
    fun buildTickerSlots_nullIsland_skipsSunset() {
        val slots = buildTickerSlots(emptyList(), 0.0, 0.0, emptyList(), sunsetMinutesProvider = { _, _ -> 45 })
        assertTrue(slots.none { it.contains("Sunset") })
    }

    @Test
    fun buildTickerSlots_allClosedLateNight_showsClosedFallback() {
        // Regression: when all POIs are closed, sunset out of range, no highlights,
        // the ticker must NOT return empty (which causes it to disappear).
        val pois = listOf(poi("closed"), poi("closed"), poi("closed"))
        val slots = buildTickerSlots(
            pois, 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> -60 }, // well past sunset
        )
        assertTrue(slots.isNotEmpty(), "Ticker must not be empty when all POIs are closed")
        assertEquals("All 3 places closed nearby", slots.first())
    }

    @Test
    fun buildTickerSlots_allClosedWithHighlights_usesHighlightNotFallback() {
        val pois = listOf(poi("closed"), poi("closed"))
        val slots = buildTickerSlots(
            pois, 40.0, -74.0, listOf("Night market open"),
            sunsetMinutesProvider = { _, _ -> -60 },
        )
        assertTrue(slots.isNotEmpty())
        assertEquals("Night market open", slots.first())
        assertTrue(slots.none { it.contains("places closed") }, "Fallback should not appear when highlights exist")
    }

    // --- Sunrise countdown tests ---

    @Test
    fun buildTickerSlots_sunriseWithinRange_showsSunriseSlot() {
        val slots = buildTickerSlots(
            listOf(poi()), 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 200 },
            sunriseMinutesProvider = { _, _ -> 30 },
        )
        assertTrue(slots.any { it == "Sunrise in 30 min" })
    }

    @Test
    fun buildTickerSlots_sunriseOutOfRange_omitsSunriseSlot() {
        val slots = buildTickerSlots(
            listOf(poi()), 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 200 },
            sunriseMinutesProvider = { _, _ -> 200 },
        )
        assertTrue(slots.none { it.contains("Sunrise") })
    }

    @Test
    fun buildTickerSlots_sunriseNegative_omitsSunriseSlot() {
        val slots = buildTickerSlots(
            listOf(poi()), 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 200 },
            sunriseMinutesProvider = { _, _ -> -1 },
        )
        assertTrue(slots.none { it.contains("Sunrise") })
    }

    @Test
    fun buildTickerSlots_nullIsland_skipsSunrise() {
        val slots = buildTickerSlots(
            emptyList(), 0.0, 0.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 45 },
            sunriseMinutesProvider = { _, _ -> 30 },
        )
        assertTrue(slots.none { it.contains("Sunrise") })
    }

    // --- Early morning "places open from" tests ---

    @Test
    fun buildTickerSlots_earlyMorning_showsPlacesOpenFromHint() {
        val pois = listOf(
            poi("closed", hours = "8am-10pm"),
            poi("closed", hours = "6am-11pm"),
        )
        val slots = buildTickerSlots(
            pois, 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 200 },
            sunriseMinutesProvider = { _, _ -> 200 },
            nowHourOverride = 3,
        )
        assertTrue(slots.any { it == "Places open from 6 AM" }, "Should show earliest opening: $slots")
    }

    @Test
    fun buildTickerSlots_earlyMorning_noOpeningHours_noHint() {
        val pois = listOf(poi("closed"))
        val slots = buildTickerSlots(
            pois, 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 200 },
            sunriseMinutesProvider = { _, _ -> 200 },
            nowHourOverride = 2,
        )
        assertTrue(slots.none { it.contains("Places open from") })
    }

    @Test
    fun buildTickerSlots_notEarlyMorning_noOpenHint() {
        val pois = listOf(poi("closed", hours = "8am-10pm"))
        val slots = buildTickerSlots(
            pois, 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 200 },
            sunriseMinutesProvider = { _, _ -> 200 },
            nowHourOverride = 10, // Not early morning
        )
        assertTrue(slots.none { it.contains("Places open from") })
    }

    @Test
    fun buildTickerSlots_earlyMorning_24h_skipsAlreadyOpen() {
        // 24h places have opening hour 0, which is not > nowHour=3, so should be skipped
        val pois = listOf(
            poi("open", hours = "24 hours"),
            poi("closed", hours = "7am-9pm"),
        )
        val slots = buildTickerSlots(
            pois, 40.0, -74.0, emptyList(),
            sunsetMinutesProvider = { _, _ -> 200 },
            sunriseMinutesProvider = { _, _ -> 200 },
            nowHourOverride = 3,
        )
        assertTrue(slots.any { it == "Places open from 7 AM" }, "Should show 7 AM, not 12 AM: $slots")
    }
}
