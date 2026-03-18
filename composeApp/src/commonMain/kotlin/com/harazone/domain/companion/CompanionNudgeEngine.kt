package com.harazone.domain.companion

import com.harazone.data.repository.UserPreferencesRepository
import com.harazone.domain.model.CompanionNudge
import com.harazone.domain.model.NudgeType
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.AreaAdvisory
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.provider.AreaIntelligenceProvider
import com.harazone.util.AppClock
import com.harazone.util.haversineDistanceMeters
import kotlin.math.roundToInt

class CompanionNudgeEngine(
    private val prefs: UserPreferencesRepository,
    private val aiProvider: AreaIntelligenceProvider,
    private val clock: AppClock,
) {
    companion object {
        private const val DELTA_MIN_INTERVAL_MS = 1 * 3_600_000L // 1 hour
        private const val PROXIMITY_THRESHOLD_M = 200.0
        private val VIBE_MILESTONES = listOf(5, 10, 20)
    }

    // #8 Welcome Back Delta — call on session start when savedPois first loads
    suspend fun checkRelaunched(savedPois: List<SavedPoi>, languageTag: String): CompanionNudge? {
        if (savedPois.isEmpty()) return null
        if (clock.nowMs() - prefs.getLastDeltaShownAt() < DELTA_MIN_INTERVAL_MS) return null
        val summary = savedPois.take(8).joinToString { "${it.name} (${it.vibe}, ${it.areaName})" }
        val text = aiProvider.generateCompanionNudge("relaunch_delta", summary, languageTag)
            ?: return null
        prefs.setLastDeltaShownAt(clock.nowMs())
        return CompanionNudge(NudgeType.RELAUNCH_DELTA, text)
    }

    // #17 Gentle Proximity Ping — call on each meaningful location refresh
    fun checkProximity(gpsLat: Double, gpsLng: Double, savedPois: List<SavedPoi>): CompanionNudge? {
        val nearest = savedPois.minByOrNull {
            haversineDistanceMeters(gpsLat, gpsLng, it.lat, it.lng)
        } ?: return null
        val dist = haversineDistanceMeters(gpsLat, gpsLng, nearest.lat, nearest.lng)
        if (dist > PROXIMITY_THRESHOLD_M) return null
        val dayKey = clock.nowMs() / 86_400_000L
        val key = "${nearest.id}:$dayKey"
        if (prefs.getLastProximityPingKey() == key) return null
        prefs.setLastProximityPingKey(key)
        val distText = if (dist < 50) "${dist.toInt()}m" else "${((dist / 50.0).roundToInt() * 50)}m"
        val text = "${nearest.name} — you're $distText away."
        return CompanionNudge(NudgeType.PROXIMITY, text, "Tell me about ${nearest.name}.")
    }

    // #34 Instant Neighbor — call after visitPoi() saves a new place
    fun checkInstantNeighbor(
        savedPoi: SavedPoi,
        allPois: List<POI>,
        visitedIds: Set<String>,
    ): CompanionNudge? {
        val neighbor = allPois
            .filter {
                it.name != savedPoi.name &&
                (it.savedId.isEmpty() || it.savedId !in visitedIds) &&
                (it.type == savedPoi.type || it.vibe == savedPoi.vibe || savedPoi.vibe in it.vibes)
            }
            .minByOrNull { haversineDistanceMeters(savedPoi.lat, savedPoi.lng, it.latitude ?: 0.0, it.longitude ?: 0.0) }
            ?: return null
        val text = "There's also ${neighbor.name} nearby — same vibe as ${savedPoi.name}."
        return CompanionNudge(NudgeType.INSTANT_NEIGHBOR, text, "Tell me about ${neighbor.name}.")
    }

    // #9 Safety Alert — call when advisory is active for an area
    fun buildSafetyNudge(advisory: AreaAdvisory?, nudgeText: String): CompanionNudge? {
        if (advisory == null || advisory.level == AdvisoryLevel.SAFE || advisory.level == AdvisoryLevel.UNKNOWN) return null
        return CompanionNudge(
            type = NudgeType.SAFETY_ALERT,
            text = nudgeText,
            chatContext = "Safety advisory for ${advisory.countryName}: ${advisory.summary}",
        )
    }

    // Quiet orb tap — nothing in the queue
    fun makeQuietNudge(): CompanionNudge =
        CompanionNudge(
            NudgeType.AMBIENT_WHISPER,
            "Nothing yet — I'll nudge you when I spot something worth sharing.",
        )

    // #37 Anticipation Seed — call when new save resolves to WANT_TO_GO
    fun makeAnticipationSeed(poi: POI): CompanionNudge =
        CompanionNudge(
            NudgeType.ANTICIPATION_SEED,
            "I'll keep an eye on ${poi.name} for you — looks worth the trip.",
            "What should I know about ${poi.name} before I go?",
        )

    // #13 Ambient Whisper — call after idle threshold exceeded
    suspend fun checkAmbientWhisper(
        areaName: String,
        visiblePois: List<POI>,
        languageTag: String,
    ): CompanionNudge? {
        if (visiblePois.isEmpty()) return null
        if (prefs.getWhisperShownForArea() == areaName) return null
        val poisSummary = visiblePois.take(5).joinToString { it.name }
        val context = "Area: $areaName. Places visible: $poisSummary."
        val text = aiProvider.generateCompanionNudge("ambient_whisper", context, languageTag)
            ?: return null
        prefs.setWhisperShownForArea(areaName)
        return CompanionNudge(NudgeType.AMBIENT_WHISPER, text)
    }

    // #39 Vibe Profile Reveal — call after each save to check pattern milestones
    fun checkVibeReveal(savedPois: List<SavedPoi>): CompanionNudge? {
        val seen = prefs.getVibeMilestonesSeenSet()
        val vibeCounts = savedPois
            .filter { it.vibe.isNotBlank() }
            .groupingBy { it.vibe }
            .eachCount()
        for ((vibe, count) in vibeCounts) {
            for (milestone in VIBE_MILESTONES.sortedDescending()) {
                val key = "$vibe:$milestone"
                if (count >= milestone && key !in seen) {
                    val allKeysToMark = VIBE_MILESTONES.filter { it <= milestone }.map { "$vibe:$it" }.toSet()
                    prefs.setVibeMilestonesSeenSet(seen + allKeysToMark)
                    val text = "You've saved $milestone ${vibe.lowercase()} spots. I'm starting to know your vibe."
                    return CompanionNudge(NudgeType.VIBE_REVEAL, text)
                }
            }
        }
        return null
    }
}
