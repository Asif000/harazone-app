package com.harazone.data.repository

import com.harazone.data.local.AreaDiscoveryDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

open class UserPreferencesRepository(private val db: AreaDiscoveryDatabase?) {
    private val json = Json { ignoreUnknownKeys = true }

    open fun getColdStartSeen(): Boolean =
        getPreference("cold_start_seen") == "true"

    open fun setColdStartSeen() {
        db!!.user_preferencesQueries.set("cold_start_seen", "true")
    }

    open fun getPinnedVibes(): List<String> {
        val raw = getPreference("pinned_vibes") ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    open fun setPinnedVibes(labels: List<String>) {
        db!!.user_preferencesQueries.set(
            "pinned_vibes",
            json.encodeToString(ListSerializer(String.serializer()), labels)
        )
    }

    // Welcome Back Delta — timestamp (Long as string)
    open fun getLastDeltaShownAt(): Long =
        getPreference("companion_delta_at")?.toLongOrNull() ?: 0L

    open fun setLastDeltaShownAt(ts: Long) {
        db!!.user_preferencesQueries.set("companion_delta_at", ts.toString())
    }

    // Proximity Ping — "poiId:dayKey" composite string
    open fun getLastProximityPingKey(): String =
        getPreference("companion_proximity_key") ?: ""

    open fun setLastProximityPingKey(key: String) {
        db!!.user_preferencesQueries.set("companion_proximity_key", key)
    }

    // Vibe Milestones — JSON Set<String>, e.g. {"coffee:5","food:5","coffee:10"}
    open fun getVibeMilestonesSeenSet(): Set<String> {
        val raw = getPreference("companion_vibe_milestones") ?: return emptySet()
        return try {
            json.decodeFromString(SetSerializer(String.serializer()), raw)
        } catch (_: Exception) { emptySet() }
    }

    open fun setVibeMilestonesSeenSet(milestones: Set<String>) {
        db!!.user_preferencesQueries.set(
            "companion_vibe_milestones",
            json.encodeToString(SetSerializer(String.serializer()), milestones)
        )
    }

    // Ambient Whisper — last area name where whisper was shown
    open fun getWhisperShownForArea(): String =
        getPreference("companion_whisper_area") ?: ""

    open fun setWhisperShownForArea(area: String) {
        db!!.user_preferencesQueries.set("companion_whisper_area", area)
    }

    // Advisory cache — JSON string of AreaAdvisory keyed by country code
    open fun getAdvisoryCache(countryCode: String): String? =
        getPreference("advisory_$countryCode")

    open fun setAdvisoryCache(countryCode: String, advisoryJson: String) {
        db!!.user_preferencesQueries.set("advisory_$countryCode", advisoryJson)
    }

    open fun getAdvisoryCachedCountryName(countryCode: String): String? =
        getPreference("advisory_name_$countryCode")

    open fun setAdvisoryCachedCountryName(countryCode: String, name: String) {
        db!!.user_preferencesQueries.set("advisory_name_$countryCode", name)
    }

    private fun getPreference(key: String): String? =
        db!!.user_preferencesQueries.get(key).executeAsOneOrNull()
}
