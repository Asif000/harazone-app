package com.harazone.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class POIMergeTest {

    private val basePoi = POI(
        name = "Test Place",
        type = "food",
        description = "Original description",
        confidence = Confidence.MEDIUM,
        latitude = 34.0,
        longitude = 135.0,
        vibe = "Dining",
        vibes = listOf("Dining", "Culture"),
        insight = "Original insight",
        hours = "9am-5pm",
        liveStatus = "open",
        rating = 3.5f,
        priceRange = "$",
        reviewCount = 100,
        imageUrl = "https://original.jpg",
        imageUrls = listOf("https://original.jpg"),
        wikiSlug = "Test_Place",
        userNote = "My note",
    )

    private val emptyEnrichment = POI(
        name = "Test Place",
        type = "food",
        description = "",
        confidence = Confidence.MEDIUM,
        latitude = null,
        longitude = null,
    )

    // --- enrichment values override base ---

    @Test
    fun mergeFrom_enrichment_overrides_non_null_fields() {
        val enrichment = emptyEnrichment.copy(
            hours = "10am-8pm",
            liveStatus = "closed",
            rating = 4.5f,
            priceRange = "$$",
            reviewCount = 500,
            imageUrl = "https://enriched.jpg",
            wikiSlug = "Enriched_Place",
        )
        val result = basePoi.mergeFrom(enrichment)
        assertEquals("10am-8pm", result.hours)
        assertEquals("closed", result.liveStatus)
        assertEquals(4.5f, result.rating)
        assertEquals("$$", result.priceRange)
        assertEquals(500, result.reviewCount)
        assertEquals("https://enriched.jpg", result.imageUrl)
        assertEquals("Enriched_Place", result.wikiSlug)
    }

    // --- null enrichment preserves base ---

    @Test
    fun mergeFrom_null_enrichment_preserves_base_values() {
        val result = basePoi.mergeFrom(emptyEnrichment)
        assertEquals("9am-5pm", result.hours)
        assertEquals("open", result.liveStatus)
        assertEquals(3.5f, result.rating)
        assertEquals("$", result.priceRange)
        assertEquals(100, result.reviewCount)
        assertEquals("https://original.jpg", result.imageUrl)
        assertEquals("Test_Place", result.wikiSlug)
        assertEquals("My note", result.userNote)
        assertEquals(34.0, result.latitude)
        assertEquals(135.0, result.longitude)
    }

    // --- empty strings/lists preserve base ---

    @Test
    fun mergeFrom_empty_strings_preserve_base() {
        val enrichment = emptyEnrichment.copy(
            vibe = "",
            insight = "",
            description = "",
        )
        val result = basePoi.mergeFrom(enrichment)
        assertEquals("Dining", result.vibe)
        assertEquals("Original insight", result.insight)
        assertEquals("Original description", result.description)
    }

    @Test
    fun mergeFrom_empty_lists_preserve_base() {
        val enrichment = emptyEnrichment.copy(
            vibes = emptyList(),
            imageUrls = emptyList(),
        )
        val result = basePoi.mergeFrom(enrichment)
        assertEquals(listOf("Dining", "Culture"), result.vibes)
        assertEquals(listOf("https://original.jpg"), result.imageUrls)
    }

    // --- non-empty strings/lists override ---

    @Test
    fun mergeFrom_non_empty_strings_override() {
        val enrichment = emptyEnrichment.copy(
            vibe = "Nightlife",
            insight = "New insight",
            description = "New description",
        )
        val result = basePoi.mergeFrom(enrichment)
        assertEquals("Nightlife", result.vibe)
        assertEquals("New insight", result.insight)
        assertEquals("New description", result.description)
    }

    @Test
    fun mergeFrom_non_empty_lists_override() {
        val enrichment = emptyEnrichment.copy(
            vibes = listOf("Nightlife"),
            imageUrls = listOf("https://new1.jpg", "https://new2.jpg"),
        )
        val result = basePoi.mergeFrom(enrichment)
        assertEquals(listOf("Nightlife"), result.vibes)
        assertEquals(listOf("https://new1.jpg", "https://new2.jpg"), result.imageUrls)
    }

    // --- identity fields preserved ---

    @Test
    fun mergeFrom_preserves_name_type_confidence() {
        val enrichment = emptyEnrichment.copy(
            name = "Different Name",
            type = "park",
            confidence = Confidence.HIGH,
        )
        val result = basePoi.mergeFrom(enrichment)
        // name/type/confidence come from base (copy() doesn't touch them)
        assertEquals("Test Place", result.name)
        assertEquals("food", result.type)
        assertEquals(Confidence.MEDIUM, result.confidence)
    }

    // --- coordinates ---

    @Test
    fun mergeFrom_enrichment_coords_override_when_present() {
        val enrichment = emptyEnrichment.copy(latitude = 35.0, longitude = 136.0)
        val result = basePoi.mergeFrom(enrichment)
        assertEquals(35.0, result.latitude)
        assertEquals(136.0, result.longitude)
    }

    @Test
    fun mergeFrom_null_coords_preserve_base() {
        val result = basePoi.mergeFrom(emptyEnrichment)
        assertEquals(34.0, result.latitude)
        assertEquals(135.0, result.longitude)
    }

    // --- merge onto empty base ---

    @Test
    fun mergeFrom_onto_empty_base_applies_all_enrichment() {
        val empty = POI(
            name = "Empty",
            type = "park",
            description = "",
            confidence = Confidence.LOW,
            latitude = null,
            longitude = null,
        )
        val enrichment = basePoi
        val result = empty.mergeFrom(enrichment)
        assertEquals("Dining", result.vibe)
        assertEquals("9am-5pm", result.hours)
        assertEquals(3.5f, result.rating)
        assertEquals(100, result.reviewCount)
        assertEquals("$", result.priceRange)
        assertEquals(34.0, result.latitude)
    }
}
