package com.harazone.ui.map.components

import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.MetaLine
import com.harazone.domain.model.buildMetaLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for buildMetaLines() — replaces AmbientTickerTest.
 * Verifies priority sorting, safety override, rotation pause,
 * vibe filter display, and fallback behavior.
 */
class RotatingMetaTickerTest {

    @Test
    fun buildMetaLines_safeArea_noSafetyWarning() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        assertTrue(lines.none { it is MetaLine.SafetyWarning })
        assertTrue(lines.any { it is MetaLine.Default })
    }

    @Test
    fun buildMetaLines_cautionArea_hasSafetyWarningAtPriority1() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.CAUTION,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        val first = lines.first()
        assertTrue(first is MetaLine.SafetyWarning, "First line should be safety warning, got $first")
        assertEquals(1, first.priority)
    }

    @Test
    fun buildMetaLines_reconsiderArea_hasSafetyWarning() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.RECONSIDER,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        assertTrue(lines.any { it is MetaLine.SafetyWarning })
        assertTrue(lines.first() is MetaLine.SafetyWarning)
    }

    @Test
    fun buildMetaLines_doNotTravelArea_hasSafetyWarning() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.DO_NOT_TRAVEL,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        assertTrue(lines.any { it is MetaLine.SafetyWarning })
        val warning = lines.first() as MetaLine.SafetyWarning
        assertTrue(warning.text.contains("Do not travel"))
    }

    @Test
    fun buildMetaLines_unknownAdvisory_noSafetyWarning() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.UNKNOWN,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        assertTrue(lines.none { it is MetaLine.SafetyWarning })
    }

    @Test
    fun buildMetaLines_remoteArea_hasRemoteContext() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            isRemote = true,
            homeCity = "Dubai",
            remoteDistance = "8,300 mi",
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        val remote = lines.firstOrNull { it is MetaLine.RemoteContext }
        assertTrue(remote != null, "Should have remote context line")
        assertEquals(2, remote!!.priority)
    }

    @Test
    fun buildMetaLines_activeVibeFilter_hasVibeFilterLine() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            activeVibeFilters = setOf("Arts", "History"),
            vibeMatchCount = 3,
            totalPoiCount = 5,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        val vibe = lines.firstOrNull { it is MetaLine.VibeFilter }
        assertTrue(vibe != null, "Should have vibe filter line")
        assertEquals(3, vibe!!.priority)
    }

    @Test
    fun buildMetaLines_vibeFilterAtPriority3_aboveCompanionNudge() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            activeVibeFilters = setOf("Arts"),
            vibeMatchCount = 2,
            totalPoiCount = 5,
            companionNudgeText = "Try Surprise!",
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        val vibeIndex = lines.indexOfFirst { it is MetaLine.VibeFilter }
        val nudgeIndex = lines.indexOfFirst { it is MetaLine.CompanionNudge }
        assertTrue(vibeIndex < nudgeIndex, "VibeFilter (p3) should come before CompanionNudge (p4)")
    }

    @Test
    fun buildMetaLines_poiHighlights_included() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            poiHighlights = listOf("Jazz at 9pm", "Market till 8"),
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        val highlights = lines.filterIsInstance<MetaLine.PoiHighlight>()
        assertEquals(2, highlights.size)
    }

    @Test
    fun buildMetaLines_blankHighlights_filtered() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            poiHighlights = listOf("Jazz", "", "  "),
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        val highlights = lines.filterIsInstance<MetaLine.PoiHighlight>()
        assertEquals(1, highlights.size)
    }

    @Test
    fun buildMetaLines_defaultAlwaysPresent() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        assertTrue(lines.any { it is MetaLine.Default })
    }

    @Test
    fun buildMetaLines_defaultContainsWeatherAndVisit() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            weatherText = "\u2600\uFE0F 82\u00B0F",
            visitTag = "Visited 3\u00D7",
        )
        val default = lines.filterIsInstance<MetaLine.Default>().first()
        assertTrue(default.text.contains("82"), "Default should contain weather: ${default.text}")
        assertTrue(default.text.contains("Visited"), "Default should contain visit tag: ${default.text}")
    }

    @Test
    fun buildMetaLines_searching_returnsSingleDiscoveringLine() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.SAFE,
            isSearching = true,
            areaName = "Dubai Marina",
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        assertEquals(1, lines.size)
        assertTrue(lines.first() is MetaLine.Discovering)
        val discovering = lines.first() as MetaLine.Discovering
        assertEquals("Dubai Marina", discovering.areaName)
    }

    @Test
    fun buildMetaLines_prioritySorted() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.CAUTION,
            isRemote = true,
            homeCity = "Dubai",
            remoteDistance = "100 mi",
            activeVibeFilters = setOf("Arts"),
            vibeMatchCount = 2,
            totalPoiCount = 5,
            companionNudgeText = "Try Surprise",
            poiHighlights = listOf("Great park"),
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        // Verify sorted by priority
        for (i in 0 until lines.size - 1) {
            assertTrue(
                lines[i].priority <= lines[i + 1].priority,
                "Lines should be sorted by priority: ${lines[i].priority} <= ${lines[i + 1].priority}",
            )
        }
    }

    @Test
    fun buildMetaLines_noWeather_defaultStillPresent() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            weatherText = null,
            visitTag = "First visit",
        )
        assertTrue(lines.any { it is MetaLine.Default })
        val default = lines.filterIsInstance<MetaLine.Default>().first()
        assertTrue(default.text.contains("First visit"))
    }

    @Test
    fun buildMetaLines_nullAdvisory_noSafetyWarning() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            weatherText = "82\u00B0F",
            visitTag = "First visit",
        )
        assertTrue(lines.none { it is MetaLine.SafetyWarning })
    }

    // --- Migration from AmbientTickerTest: equivalent behavior tests ---

    @Test
    fun buildMetaLines_emptyInputs_returnsFallback() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            weatherText = null,
            visitTag = "First visit",
        )
        assertEquals(1, lines.size)
        assertTrue(lines.first() is MetaLine.Default)
    }

    @Test
    fun buildMetaLines_withAreaHighlights_includesThem() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            poiHighlights = listOf("Jazz at 9pm", "Market till 8"),
            weatherText = null,
            visitTag = "First visit",
        )
        assertTrue(lines.any { it is MetaLine.PoiHighlight })
        val highlights = lines.filterIsInstance<MetaLine.PoiHighlight>()
        assertTrue(highlights.any { it.text == "Jazz at 9pm" })
        assertTrue(highlights.any { it.text == "Market till 8" })
    }
}
