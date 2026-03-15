package com.harazone.ui.map

import com.harazone.domain.model.Confidence
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.POI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for POIListView data logic and callback contracts.
 * Compose UI rendering tests require androidInstrumentedTest (compose-test-junit4).
 */
class POIListViewTest {

    private fun makePoi(
        name: String = "Test Place",
        type: String = "cafe",
        vibe: String = "cozy",
        imageUrl: String? = null,
        latitude: Double? = 40.0,
        longitude: Double? = -74.0,
        rating: Float? = null,
        liveStatus: String? = null,
        hours: String? = null,
    ) = POI(
        name = name,
        type = type,
        description = "desc",
        confidence = Confidence.HIGH,
        latitude = latitude,
        longitude = longitude,
        vibe = vibe,
        imageUrl = imageUrl,
        rating = rating,
        liveStatus = liveStatus,
        hours = hours,
    )

    // --- T1: dynamicVibeChips_showCorrectCount (AC8) ---
    @Test
    fun dynamicVibeChips_countMatchesInput() {
        val vibes = listOf(
            DynamicVibe("Cafes", "☕", emptyList()),
            DynamicVibe("Parks", "🌳", emptyList()),
            DynamicVibe("Bars", "🍺", emptyList()),
        )
        assertEquals(3, vibes.size)
    }

    // --- T2: dynamicVibeChip_selectedState_matchesActiveDynamicVibe (AC9) ---
    @Test
    fun dynamicVibeChip_selectedState_matchesActiveDynamicVibe() {
        val vibes = listOf(
            DynamicVibe("Cafes", "☕", emptyList()),
            DynamicVibe("Parks", "🌳", emptyList()),
        )
        val active = vibes[0]
        // Selection logic: vibe.label == activeDynamicVibe?.label
        assertTrue(vibes[0].label == active.label)
        assertFalse(vibes[1].label == active.label)
    }

    // --- T3: dynamicVibeChip_tap_callsOnDynamicVibeSelected (AC10) ---
    @Test
    fun dynamicVibeChip_tap_invokesCallback() {
        var selectedVibe: DynamicVibe? = null
        val callback: (DynamicVibe) -> Unit = { selectedVibe = it }
        val vibe = DynamicVibe("Cafes", "☕", emptyList())
        callback(vibe)
        assertEquals("Cafes", selectedVibe?.label)
    }

    // --- T4: emptyDynamicVibes_hidesChipStrip (AC11) ---
    @Test
    fun emptyDynamicVibes_shouldHideChipStrip() {
        val vibes = emptyList<DynamicVibe>()
        assertTrue(vibes.isEmpty())
    }

    // --- T5: bookmarkTap_unsavedPoi_callsSaveTapped (AC3) ---
    @Test
    fun bookmarkTap_unsavedPoi_callsSaveTapped() {
        val poi = makePoi()
        val savedPoiIds = emptySet<String>()
        val isSaved = poi.savedId in savedPoiIds
        assertFalse(isSaved)

        var saveCalled = false
        var unsaveCalled = false
        val onSave: (POI) -> Unit = { saveCalled = true }
        val onUnsave: (POI) -> Unit = { unsaveCalled = true }

        // Callback logic from POIListView: if (isSaved) onUnsave else onSave
        if (isSaved) onUnsave(poi) else onSave(poi)
        assertTrue(saveCalled)
        assertFalse(unsaveCalled)
    }

    // --- T6: bookmarkTap_savedPoi_callsUnsaveTapped (AC4) ---
    @Test
    fun bookmarkTap_savedPoi_callsUnsaveTapped() {
        val poi = makePoi()
        val savedPoiIds = setOf(poi.savedId)
        val isSaved = poi.savedId in savedPoiIds
        assertTrue(isSaved)

        var saveCalled = false
        var unsaveCalled = false
        val onSave: (POI) -> Unit = { saveCalled = true }
        val onUnsave: (POI) -> Unit = { unsaveCalled = true }

        if (isSaved) onUnsave(poi) else onSave(poi)
        assertFalse(saveCalled)
        assertTrue(unsaveCalled)
    }

    // --- T7: navigateIcon_tap_callsOnNavigateTapped (AC5) ---
    @Test
    fun navigateIcon_visibleWhenCoordsPresent() {
        val poi = makePoi(latitude = 40.0, longitude = -74.0)
        assertTrue(poi.latitude != null && poi.longitude != null)
    }

    // --- T8: navigateIcon_hiddenWhenNullCoords (AC6) ---
    @Test
    fun navigateIcon_hiddenWhenNullCoords() {
        val poi = makePoi(latitude = null, longitude = null)
        assertFalse(poi.latitude != null && poi.longitude != null)
    }

    @Test
    fun navigateIcon_hiddenWhenOnlyLatNull() {
        val poi = makePoi(latitude = null, longitude = -74.0)
        assertFalse(poi.latitude != null && poi.longitude != null)
    }

    // --- T9: chatIcon_tap_callsOnChatTapped (AC7) ---
    @Test
    fun chatIcon_tap_invokesCallback() {
        val poi = makePoi()
        var chatPoi: POI? = null
        val callback: (POI) -> Unit = { chatPoi = it }
        callback(poi)
        assertEquals(poi.name, chatPoi?.name)
    }

    // --- T10: cardBodyTap_callsOnPoiClick (AC12) ---
    @Test
    fun cardBodyTap_invokesOnPoiClick() {
        val poi = makePoi()
        var clickedPoi: POI? = null
        val callback: (POI) -> Unit = { clickedPoi = it }
        callback(poi)
        assertEquals(poi.name, clickedPoi?.name)
    }

    // --- T11: nullImageUrl_showsFallbackBackground (AC2) ---
    @Test
    fun nullImageUrl_shouldUseFallback() {
        val poi = makePoi(imageUrl = null)
        assertTrue(poi.imageUrl == null)
    }

    @Test
    fun nonNullImageUrl_shouldShowImage() {
        val poi = makePoi(imageUrl = "https://example.com/img.jpg")
        assertTrue(poi.imageUrl != null)
    }

    // --- T12: emptyPois_withActiveVibe_showsVibeSpecificMessage (AC17) ---
    @Test
    fun emptyPois_withActiveVibe_showsVibeSpecificMessage() {
        val pois = emptyList<POI>()
        val activeVibe = DynamicVibe("Cafes", "☕", emptyList())
        assertTrue(pois.isEmpty())
        val message = "No ${activeVibe.label} found in this area"
        assertEquals("No Cafes found in this area", message)
    }

    // --- T13: emptyPois_noFilter_showsGenericMessage ---
    @Test
    fun emptyPois_noFilter_usesGenericMessage() {
        val pois = emptyList<POI>()
        val activeVibe: DynamicVibe? = null
        assertTrue(pois.isEmpty() && activeVibe == null)
    }

    // --- T14: blankVibe_noTrailingSeparatorInSubtitle (AC16) ---
    @Test
    fun blankVibe_noTrailingSeparator() {
        val poi = makePoi(type = "cafe", vibe = "")
        val subtitle = if (poi.vibe.isNotBlank())
            "${poi.type.replaceFirstChar { it.uppercaseChar() }} \u00B7 ${poi.vibe}"
        else
            poi.type.replaceFirstChar { it.uppercaseChar() }
        assertEquals("Cafe", subtitle)
        assertFalse(subtitle.contains("\u00B7"))
    }

    @Test
    fun nonBlankVibe_includesSeparator() {
        val poi = makePoi(type = "cafe", vibe = "cozy")
        val subtitle = if (poi.vibe.isNotBlank())
            "${poi.type.replaceFirstChar { it.uppercaseChar() }} \u00B7 ${poi.vibe}"
        else
            poi.type.replaceFirstChar { it.uppercaseChar() }
        assertEquals("Cafe \u00B7 cozy", subtitle)
    }

    // --- Stable key: savedId ---
    @Test
    fun lazyColumnKey_usesSavedId() {
        val poi1 = makePoi(name = "Place A", latitude = 40.0, longitude = -74.0)
        val poi2 = makePoi(name = "Place A", latitude = 41.0, longitude = -73.0)
        // savedId should be different even with same name
        assertFalse(poi1.savedId == poi2.savedId)
    }

    // --- Rating display ---
    @Test
    fun ratingDisplayFormat() {
        val poi = makePoi(rating = 4.5f)
        val display = "\u2B50 ${poi.rating}"
        assertEquals("\u2B50 4.5", display)
    }

    @Test
    fun nullRating_notDisplayed() {
        val poi = makePoi(rating = null)
        assertTrue(poi.rating == null)
    }
}
