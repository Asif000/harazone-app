package com.harazone.domain.companion

import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.AreaAdvisory
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.NudgeType
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.fakes.FakeAreaIntelligenceProvider
import com.harazone.fakes.FakeClock
import com.harazone.fakes.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionNudgeEngineTest {

    private fun createEngine(
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        aiProvider: FakeAreaIntelligenceProvider = FakeAreaIntelligenceProvider(),
        clock: FakeClock = FakeClock(),
    ) = Triple(CompanionNudgeEngine(prefs, aiProvider, clock), prefs, clock)

    private fun savedPoi(
        id: String = "poi1",
        name: String = "Test Place",
        type: String = "cafe",
        vibe: String = "coffee",
        lat: Double = 0.001,
        lng: Double = 0.001,
    ) = SavedPoi(
        id = id, name = name, type = type, areaName = "TestArea",
        lat = lat, lng = lng, whySpecial = "", savedAt = 0L, vibe = vibe,
    )

    private fun poi(
        name: String = "Test POI",
        type: String = "cafe",
        lat: Double = 0.002,
        lng: Double = 0.002,
    ) = POI(
        name = name, type = type, description = "", confidence = Confidence.HIGH,
        latitude = lat, longitude = lng,
    )

    // --- checkRelaunched ---

    @Test
    fun checkRelaunched_skips_when_rate_limited() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val clock = FakeClock(nowMs = 10_000_000L)
        prefs.setLastDeltaShownAt(clock.nowMs - 30 * 60_000L) // 30 minutes ago (under 1h limit)
        val engine = CompanionNudgeEngine(prefs, FakeAreaIntelligenceProvider(), clock)
        val pois = listOf(savedPoi(id = "p1", name = "Place 1"))
        val result = engine.checkRelaunched(pois, "en")
        assertNull(result)
    }

    @Test
    fun checkRelaunched_fires_and_persists_timestamp() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val clock = FakeClock(nowMs = 100_000_000L)
        prefs.setLastDeltaShownAt(clock.nowMs - 2 * 3_600_000L) // 2 hours ago (over 1h limit)
        val aiProvider = FakeAreaIntelligenceProvider()
        aiProvider.companionNudgeResult = "Interesting fact"
        val engine = CompanionNudgeEngine(prefs, aiProvider, clock)
        val pois = listOf(savedPoi(id = "p1", name = "Place 1"))
        val result = engine.checkRelaunched(pois, "en")
        assertNotNull(result)
        assertEquals(NudgeType.RELAUNCH_DELTA, result.type)
        assertEquals("Interesting fact", result.text)
        assertEquals(clock.nowMs, prefs.getLastDeltaShownAt())
    }

    // --- checkProximity ---

    @Test
    fun checkProximity_fires_within_200m() {
        val (engine, _, _) = createEngine()
        // ~157m apart (0.001 degrees at equator)
        val pois = listOf(savedPoi(lat = 0.001, lng = 0.001))
        val result = engine.checkProximity(0.0, 0.0, pois)
        assertNotNull(result)
        assertEquals(NudgeType.PROXIMITY, result.type)
        assertTrue(result.text.contains("Test Place"))
    }

    @Test
    fun checkProximity_skips_when_already_pinged_today() {
        val prefs = FakeUserPreferencesRepository()
        val clock = FakeClock(nowMs = 86_400_000L * 100) // day 100
        val dayKey = clock.nowMs / 86_400_000L
        prefs.setLastProximityPingKey("poi1:$dayKey")
        val engine = CompanionNudgeEngine(prefs, FakeAreaIntelligenceProvider(), clock)
        val pois = listOf(savedPoi(lat = 0.001, lng = 0.001))
        val result = engine.checkProximity(0.0, 0.0, pois)
        assertNull(result)
    }

    // --- checkVibeReveal ---

    @Test
    fun checkVibeReveal_fires_at_milestone_5() {
        val (engine, _, _) = createEngine()
        val pois = (1..5).map { savedPoi(id = "p$it", name = "Place $it", vibe = "coffee") }
        val result = engine.checkVibeReveal(pois)
        assertNotNull(result)
        assertEquals(NudgeType.VIBE_REVEAL, result.type)
        assertTrue(result.text.contains("5"))
        assertTrue(result.text.contains("coffee"))
    }

    @Test
    fun checkVibeReveal_skips_already_seen_milestone() {
        val prefs = FakeUserPreferencesRepository()
        prefs.setVibeMilestonesSeenSet(setOf("coffee:5"))
        val engine = CompanionNudgeEngine(prefs, FakeAreaIntelligenceProvider(), FakeClock())
        val pois = (1..5).map { savedPoi(id = "p$it", name = "Place $it", vibe = "coffee") }
        val result = engine.checkVibeReveal(pois)
        assertNull(result)
    }

    // --- checkInstantNeighbor ---

    @Test
    fun checkInstantNeighbor_returns_nearest_same_type_unvisited_POI() {
        val (engine, _, _) = createEngine()
        val saved = savedPoi(lat = 0.0, lng = 0.0)
        val closerPoi = poi(name = "Close Cafe", type = "cafe", lat = 0.001, lng = 0.001)
        val fartherPoi = poi(name = "Far Cafe", type = "cafe", lat = 0.01, lng = 0.01)
        val result = engine.checkInstantNeighbor(saved, listOf(closerPoi, fartherPoi), emptySet())
        assertNotNull(result)
        assertEquals(NudgeType.INSTANT_NEIGHBOR, result.type)
        assertTrue(result.text.contains("Close Cafe"))
    }

    // --- checkAmbientWhisper ---

    @Test
    fun checkAmbientWhisper_skips_same_area() = runTest {
        val prefs = FakeUserPreferencesRepository()
        prefs.setWhisperShownForArea("Lisbon")
        val aiProvider = FakeAreaIntelligenceProvider()
        val engine = CompanionNudgeEngine(prefs, aiProvider, FakeClock())
        val result = engine.checkAmbientWhisper("Lisbon", listOf(poi()), "en")
        assertNull(result)
        assertEquals(0, aiProvider.companionNudgeCallCount)
    }

    // --- buildSafetyNudge ---

    private fun advisory(level: AdvisoryLevel) = AreaAdvisory(
        level = level,
        countryName = "TestCountry",
        countryCode = "TC",
        summary = "Test summary",
        details = emptyList(),
        subNationalZones = emptyList(),
        sourceUrl = "",
        lastUpdated = 0L,
        cachedAt = 0L,
    )

    @Test
    fun buildSafetyNudge_returns_nudge_for_CAUTION() {
        val (engine, _, _) = createEngine()
        val result = engine.buildSafetyNudge(advisory(AdvisoryLevel.CAUTION), "Be careful")
        assertNotNull(result)
        assertEquals(NudgeType.SAFETY_ALERT, result.type)
        assertEquals("Be careful", result.text)
    }

    @Test
    fun buildSafetyNudge_returns_nudge_for_RECONSIDER() {
        val (engine, _, _) = createEngine()
        val result = engine.buildSafetyNudge(advisory(AdvisoryLevel.RECONSIDER), "Reconsider travel")
        assertNotNull(result)
        assertEquals(NudgeType.SAFETY_ALERT, result.type)
    }

    @Test
    fun buildSafetyNudge_returns_nudge_for_DO_NOT_TRAVEL() {
        val (engine, _, _) = createEngine()
        val result = engine.buildSafetyNudge(advisory(AdvisoryLevel.DO_NOT_TRAVEL), "Do not travel")
        assertNotNull(result)
        assertEquals(NudgeType.SAFETY_ALERT, result.type)
    }

    @Test
    fun buildSafetyNudge_returns_null_for_SAFE() {
        val (engine, _, _) = createEngine()
        val result = engine.buildSafetyNudge(advisory(AdvisoryLevel.SAFE), "Safe")
        assertNull(result)
    }

    @Test
    fun buildSafetyNudge_returns_null_for_UNKNOWN() {
        val (engine, _, _) = createEngine()
        val result = engine.buildSafetyNudge(advisory(AdvisoryLevel.UNKNOWN), "Unknown")
        assertNull(result)
    }

    @Test
    fun buildSafetyNudge_returns_null_for_null_advisory() {
        val (engine, _, _) = createEngine()
        val result = engine.buildSafetyNudge(null, "No advisory")
        assertNull(result)
    }
}
