package com.harazone.data.remote

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.ChatIntent
import com.harazone.domain.model.EngagementLevel
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.TasteProfile
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

    private val emptyProfile = TasteProfile(
        strongAffinities = emptyList(),
        emergingInterests = emptyList(),
        notableAbsences = emptyList(),
        diningStyle = null,
        totalSaves = 0,
    )

    private fun chatContext(
        areaName: String = "Test Area",
        pois: List<POI> = emptyList(),
        intent: ChatIntent = ChatIntent.DISCOVER,
        level: EngagementLevel = EngagementLevel.LIGHT,
        saves: List<SavedPoi> = emptyList(),
        profile: TasteProfile = emptyProfile,
        poiCount: Int = 5,
        framingHint: String? = null,
        languageTag: String = "en",
    ): String = builder.buildChatSystemContext(
        areaName, pois, intent, level, saves, profile, poiCount, framingHint,
        languageTag = languageTag,
    )

    private fun save(
        name: String,
        areaName: String = "Test Area",
        type: String = "food",
        savedAt: Long = 1000L,
        userNote: String? = null,
    ) = SavedPoi(
        id = "$name|1.0|2.0",
        name = name,
        type = type,
        areaName = areaName,
        lat = 1.0,
        lng = 2.0,
        whySpecial = "test",
        savedAt = savedAt,
        userNote = userNote,
    )

    // --- buildAreaPortraitPrompt tests (must not break) ---

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

    // --- Updated old buildChatSystemContext tests (new signature) ---

    @Test
    fun buildChatSystemContext_includesSavedPlaces() {
        val saves = listOf(
            save("Blue Note", areaName = "Test Area"),
            save("Wynwood Walls", areaName = "Test Area"),
        )
        val result = chatContext(saves = saves)
        assertTrue(result.contains("Blue Note"))
        assertTrue(result.contains("Wynwood Walls"))
        assertTrue(result.contains("SAVED PLACES IN THIS AREA"))
    }

    @Test
    fun buildChatSystemContext_noSavesLine_whenSavesEmpty() {
        val result = chatContext(saves = emptyList())
        assertFalse(result.contains("SAVED PLACES IN THIS AREA"))
    }

    @Test
    fun buildChatSystemContext_savesSheetFramingLine() {
        val result = chatContext(
            framingHint = "The user is currently reviewing their saved places — lead with suggestions based on those first.",
        )
        assertTrue(result.contains("reviewing their saved places"))
    }

    @Test
    fun buildChatSystemContext_poiCardFramingLine() {
        val result = chatContext(
            framingHint = "The user is currently looking at Blue Note Jazz — lead with context about that place.",
        )
        assertTrue(result.contains("Blue Note Jazz"))
    }

    @Test
    fun buildChatSystemContext_nullFramingHint_noFramingLine() {
        val result = chatContext(framingHint = null)
        assertFalse(result.contains("currently"))
    }

    // --- New per-layer tests ---

    @Test
    fun buildChatSystemContext_layer1_persona() {
        val result = chatContext()
        assertTrue(result.contains("20 years"))
    }

    @Test
    fun buildChatSystemContext_layer3_tonight_intent() {
        val result = chatContext(intent = ChatIntent.TONIGHT)
        assertTrue(result.contains("tonight", ignoreCase = true))
    }

    @Test
    fun buildChatSystemContext_layer3_surprise_intent() {
        val result = chatContext(intent = ChatIntent.SURPRISE)
        assertTrue(
            result.contains("NEVER find on their own") || result.contains("Mischievous"),
        )
    }

    @Test
    fun buildChatSystemContext_layer4_fresh_engagement() {
        val result = chatContext(level = EngagementLevel.FRESH)
        assertTrue(result.contains("encouraging the user to save places"))
    }

    @Test
    fun buildChatSystemContext_layer4_dormant_engagement() {
        val result = chatContext(level = EngagementLevel.DORMANT)
        assertTrue(
            result.contains("CHANGED", ignoreCase = true) || result.contains("welcome-back", ignoreCase = true),
        )
    }

    @Test
    fun buildChatSystemContext_layer5a_saves_capped_at_8() {
        // Use names that aren't substrings of each other (e.g., "Alpha", "Bravo", ...)
        val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliet")
        val saves = names.mapIndexed { i, name ->
            save(name, savedAt = (i + 1).toLong())
        }
        val result = chatContext(saves = saves)
        // Most recent = Juliet (savedAt=10), 8th most recent = Charlie (savedAt=3)
        assertTrue(result.contains("Juliet"), "Should contain most recent save")
        assertTrue(result.contains("Charlie"), "Should contain 8th most recent save")
        assertFalse(result.contains("Bravo"), "Should NOT contain 9th save")
        assertFalse(result.contains("Alpha"), "Should NOT contain 10th save")
    }

    @Test
    fun buildChatSystemContext_layer5a_userNote_injected() {
        val saves = listOf(save("Sunset Cafe", userNote = "best at sunset"))
        val result = chatContext(saves = saves)
        assertTrue(result.contains("best at sunset"))
    }

    @Test
    fun buildChatSystemContext_layer5b_omitted_for_fresh() {
        val result = chatContext(
            level = EngagementLevel.FRESH,
            profile = emptyProfile.copy(totalSaves = 0),
        )
        assertFalse(result.contains("TASTE PROFILE"))
        assertFalse(result.contains("SURPRISE FILTER"))
    }

    @Test
    fun buildChatSystemContext_layer5b_omitted_for_dormant() {
        val profileWithSaves = emptyProfile.copy(
            totalSaves = 10,
            strongAffinities = listOf("park"),
        )
        val result = chatContext(level = EngagementLevel.DORMANT, profile = profileWithSaves)
        assertFalse(result.contains("TASTE PROFILE"))
        assertFalse(result.contains("SURPRISE FILTER"))
    }

    @Test
    fun buildChatSystemContext_layer5b_emergingInterests_injected() {
        val profile = emptyProfile.copy(
            totalSaves = 5,
            strongAffinities = listOf("park"),
            emergingInterests = listOf("arts", "beach"),
        )
        val result = chatContext(
            intent = ChatIntent.DISCOVER,
            level = EngagementLevel.REGULAR,
            profile = profile,
        )
        assertTrue(result.contains("arts"))
        assertTrue(result.contains("beach"))
        assertTrue(result.contains("Emerging interests"))
    }

    @Test
    fun buildChatSystemContext_layer5b_surprise_uses_absences() {
        val profile = emptyProfile.copy(
            totalSaves = 5,
            notableAbsences = listOf("park"),
            strongAffinities = listOf("restaurant"),
        )
        val result = chatContext(
            intent = ChatIntent.SURPRISE,
            level = EngagementLevel.REGULAR,
            profile = profile,
        )
        assertTrue(result.contains("park"))
        assertTrue(result.contains("NEVER saved", ignoreCase = true))
    }

    @Test
    fun buildChatSystemContext_layer6_high_confidence() {
        val result = chatContext(poiCount = 12)
        assertTrue(result.contains("insider", ignoreCase = true))
    }

    @Test
    fun buildChatSystemContext_layer6_low_confidence() {
        val result = chatContext(poiCount = 3)
        assertTrue(result.contains("LOW CONFIDENCE"))
    }

    @Test
    fun buildChatSystemContext_layer7_context_shift() {
        val result = chatContext()
        assertTrue(result.contains("CONTEXT SHIFTS"))
    }

    // --- buildPinOnlyPrompt isNewUser tests ---

    @Test
    fun buildPinOnlyPrompt_isNewUser_includesDiversityHint() {
        val prompt = builder.buildPinOnlyPrompt("Shoreditch", isNewUser = true)
        assertTrue(prompt.contains("NEW USER MODE"))
    }

    @Test
    fun buildPinOnlyPrompt_returningUser_noDiversityHint() {
        val prompt = builder.buildPinOnlyPrompt("Shoreditch", isNewUser = false)
        assertFalse(prompt.contains("NEW USER MODE"))
    }

    @Test
    fun buildPinOnlyPrompt_defaultIsNotNewUser() {
        val prompt = builder.buildPinOnlyPrompt("Shoreditch")
        assertFalse(prompt.contains("NEW USER MODE"))
    }

    // --- Language block tests ---

    @Test
    fun buildChatSystemContext_withPtBR_includesLanguageRule() {
        val result = chatContext(framingHint = null)
        val resultWithLang = builder.buildChatSystemContext(
            "Test Area", emptyList(), ChatIntent.DISCOVER, EngagementLevel.LIGHT,
            emptyList(), emptyProfile, 5, null, null, languageTag = "pt-BR",
        )
        assertTrue(resultWithLang.contains("LANGUAGE RULE"))
        assertTrue(resultWithLang.contains("pt-BR"))
    }

    @Test
    fun buildChatSystemContext_withEn_omitsLanguageRule() {
        val result = builder.buildChatSystemContext(
            "Test Area", emptyList(), ChatIntent.DISCOVER, EngagementLevel.LIGHT,
            emptyList(), emptyProfile, 5, null, null, languageTag = "en",
        )
        assertFalse(result.contains("LANGUAGE RULE"))
    }

    @Test
    fun buildChatSystemContext_withEnUS_omitsLanguageRule() {
        val result = builder.buildChatSystemContext(
            "Test Area", emptyList(), ChatIntent.DISCOVER, EngagementLevel.LIGHT,
            emptyList(), emptyProfile, 5, null, null, languageTag = "en-US",
        )
        assertFalse(result.contains("LANGUAGE RULE"))
    }
}
