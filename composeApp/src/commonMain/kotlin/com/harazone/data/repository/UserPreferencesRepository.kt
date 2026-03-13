package com.harazone.data.repository

import com.harazone.data.local.AreaDiscoveryDatabase
import kotlinx.serialization.builtins.ListSerializer
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

    private fun getPreference(key: String): String? =
        db!!.user_preferencesQueries.get(key).executeAsOneOrNull()
}
