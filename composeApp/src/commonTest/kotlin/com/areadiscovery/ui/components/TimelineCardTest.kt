package com.areadiscovery.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineCardTest {

    @Test
    fun parseTimelineEras_withMultipleYears_returnsErasSortedDescendingCappedAtTen() {
        val content = """
            In 1920 the area was founded as a small farming community.
            By 1935 the first school opened.
            In 1945 it became an industrial hub.
            In 1960 the population doubled.
            The 1972 expressway connected the region.
            The 1978 oil crisis changed the economy.
            By 1985 suburban growth accelerated.
            By 1995 tech companies arrived.
            In 2005 the downtown was revitalized.
            The 2012 arts district emerged.
            The 2020 pandemic reshaped work patterns.
        """.trimIndent()

        val eras = parseTimelineEras(content)

        assertEquals(10, eras.size)
        // Newest first, oldest (1920) dropped by cap
        assertEquals(listOf(2020, 2012, 2005, 1995, 1985, 1978, 1972, 1960, 1945, 1935), eras.map { it.year })
    }

    @Test
    fun parseTimelineEras_withNoYears_returnsEmptyList() {
        val content = "This area has a rich cultural heritage with many traditions passed down through generations."

        val eras = parseTimelineEras(content)

        assertTrue(eras.isEmpty())
    }

    @Test
    fun parseTimelineEras_withSingleYear_returnsSingleEra() {
        val content = "The city was established in 1850 by settlers from the east."

        val eras = parseTimelineEras(content)

        assertEquals(1, eras.size)
        assertEquals(1850, eras[0].year)
        assertTrue(eras[0].sentence.contains("1850"))
    }

    @Test
    fun parseTimelineEras_withDuplicateYears_deduplicatesByYear() {
        val content = "In 1920 the town was founded. Also in 1920 the first school opened."

        val eras = parseTimelineEras(content)

        assertEquals(1, eras.size)
        assertEquals(1920, eras[0].year)
    }

    @Test
    fun parseTimelineEras_withEmptyString_returnsEmptyList() {
        val eras = parseTimelineEras("")

        assertTrue(eras.isEmpty())
    }

    @Test
    fun parseTimelineEras_withBlankString_returnsEmptyList() {
        val eras = parseTimelineEras("   \n  \n  ")

        assertTrue(eras.isEmpty())
    }

    // Finding #1: Test newline-only splitting (no sentence-ending punctuation)
    @Test
    fun parseTimelineEras_withNewlineSeparatedContent_parsesCorrectly() {
        val content = """
            Founded in 1850 as a trading post
            Became a city in 1920 after the railroad arrived
            Modernized rapidly after 1975 with new infrastructure
        """.trimIndent()

        val eras = parseTimelineEras(content)

        assertEquals(3, eras.size)
        assertEquals(listOf(1975, 1920, 1850), eras.map { it.year })
    }

    // Finding #2: ZIP codes and non-year numbers should be excluded
    @Test
    fun parseTimelineEras_withZipCodesAndAddresses_excludesNonYearNumbers() {
        val content = "The area around 33126 was developed. Located at 90210 Beverly Hills. Population reached 45000 in recent years."

        val eras = parseTimelineEras(content)

        assertTrue(eras.isEmpty(), "ZIP codes and large numbers should not be parsed as years")
    }

    @Test
    fun parseTimelineEras_sortsDescendingByYear() {
        val content = "The 2010 renovation was impressive. The original 1890 building stood tall. A 1950 expansion doubled the size."

        val eras = parseTimelineEras(content)

        assertEquals(listOf(2010, 1950, 1890), eras.map { it.year })
    }
}
