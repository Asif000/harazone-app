package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.AreaContext
import kotlin.test.Test
import kotlin.test.assertTrue

class GeminiPromptBuilderTest {

    private val builder = GeminiPromptBuilder()

    private val testContext = AreaContext(
        timeOfDay = "afternoon",
        dayOfWeek = "Wednesday",
        visitCount = 0,
        preferredLanguage = "English"
    )

    @Test
    fun buildAreaPortraitPrompt_includesAreaName() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("Alfama, Lisbon"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesTimeOfDay() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("afternoon"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesDayOfWeek() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("Wednesday"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesPreferredLanguage() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("English"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesAllSixBucketTypes() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("SAFETY"))
        assertTrue(prompt.contains("CHARACTER"))
        assertTrue(prompt.contains("WHATS_HAPPENING"))
        assertTrue(prompt.contains("COST"))
        assertTrue(prompt.contains("HISTORY"))
        assertTrue(prompt.contains("NEARBY"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesBucketDelimiter() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("---BUCKET---"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesPoisDelimiter() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("---POIS---"))
    }

    @Test
    fun buildAreaPortraitPrompt_poiTemplateHasRealCoordinates() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        // Verify the template shows real coordinates, not null placeholders (C2 fix)
        assertTrue(
            prompt.contains("\"latitude\":51.5074") || prompt.contains("\"latitude\": 51.5074"),
            "POI template must show example coordinates, not null",
        )
        assertTrue(
            !prompt.contains("\"latitude\":null") && !prompt.contains("\"latitude\": null"),
            "POI template must NOT contain null latitude",
        )
    }

    @Test
    fun buildAreaPortraitPrompt_instructsGpsCoordinates() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(
            prompt.contains("decimal GPS coordinates"),
            "Prompt must instruct Gemini to provide GPS coordinates for POIs",
        )
    }
}
