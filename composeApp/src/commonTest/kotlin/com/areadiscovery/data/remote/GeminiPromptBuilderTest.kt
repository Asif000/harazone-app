package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.AreaContext
import kotlin.test.Test
import kotlin.test.assertFalse
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
        // Verify the template shows 4-decimal-place coordinates (LLMs follow examples)
        assertTrue(
            prompt.contains("\"lat\":38.7100") || prompt.contains("\"lat\": 38.7100"),
            "POI template must show 4-decimal-place coordinates",
        )
        assertTrue(
            !prompt.contains("\"lat\":null") && !prompt.contains("\"lat\": null"),
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

    @Test
    fun buildAreaPortraitPrompt_includesPassionateLocalPersona() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("passionate local"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesUniquenessRule() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("UNIQUENESS RULE") || prompt.contains("genuine story") || prompt.contains("genuinely unique"))
    }

    @Test
    fun buildAreaPortraitPrompt_doesNotHardcodeChainBrandNames() {
        // Exclusion is principle-based, not brand-list-based — Starbucks/McDonald's must NOT appear in prompt
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertFalse(prompt.contains("Starbucks"))
        assertFalse(prompt.contains("McDonald"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesFoodGate() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("FOOD GATE"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesWhySpecialInstruction() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("WHY SPECIAL") || prompt.contains("why_special") || prompt.contains("\"w\""))
    }

    @Test
    fun buildAreaPortraitPrompt_includesDigDeeperInstruction() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("DIG DEEPER") || prompt.contains("less obvious"))
    }

    @Test
    fun buildAreaPortraitPrompt_poiTemplateUsesSlimKeys() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("\"n\":") && prompt.contains("\"w\":") && prompt.contains("\"lat\":"))
        assertFalse(prompt.contains("\"poi\":"))
        assertFalse(prompt.contains("\"insight\":"))
        assertFalse(prompt.contains("\"latitude\":"))
    }

    @Test
    fun buildAreaPortraitPrompt_doesNotIncludeSourcesInBucketTemplate() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertFalse(prompt.contains("\"sources\""))
    }
}
