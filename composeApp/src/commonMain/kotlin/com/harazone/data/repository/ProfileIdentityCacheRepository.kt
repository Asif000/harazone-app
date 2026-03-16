package com.harazone.data.repository

import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.domain.model.GeoArea
import com.harazone.domain.model.ProfileIdentity
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.VibeInsight
import com.harazone.util.AppClock
import com.harazone.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ProfileIdentityJson(
    val explorerName: String,
    val tagline: String,
    val avatarEmoji: String,
    val totalVisits: Int,
    val totalAreas: Int,
    val totalVibes: Int,
    val geoFootprint: List<GeoAreaJson>,
    val vibeInsights: List<VibeInsightJson>,
)

@Serializable
internal data class GeoAreaJson(
    val areaName: String,
    val countryCode: String,
)

@Serializable
internal data class VibeInsightJson(
    val vibeName: String,
    val insight: String,
)

class ProfileIdentityCacheRepository(
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCached(): Pair<ProfileIdentity, String>? {
        val row = database.profile_identity_cacheQueries.getIdentity().executeAsOneOrNull()
            ?: return null
        return try {
            val parsed = json.decodeFromString<ProfileIdentityJson>(row.identity_json)
            val identity = parsed.toDomain()
            Pair(identity, row.input_hash)
        } catch (e: Exception) {
            AppLogger.e(e) { "ProfileIdentityCacheRepository: failed to deserialize cached identity" }
            null
        }
    }

    suspend fun cache(identity: ProfileIdentity, inputHash: String) {
        val serialized = json.encodeToString(identity.toJson())
        database.profile_identity_cacheQueries.upsertIdentity(
            identity_json = serialized,
            input_hash = inputHash,
            created_at = clock.nowMs(),
        )
    }

    fun computeInputHash(savedPois: List<SavedPoi>): String {
        val sortedIds = savedPois.map { it.id }.sorted()
        val input = sortedIds.joinToString(",") + "|" + savedPois.size
        return input.hashCode().toString()
    }

    private fun ProfileIdentityJson.toDomain() = ProfileIdentity(
        explorerName = explorerName,
        tagline = tagline,
        avatarEmoji = avatarEmoji,
        totalVisits = totalVisits,
        totalAreas = totalAreas,
        totalVibes = totalVibes,
        geoFootprint = geoFootprint.map { GeoArea(areaName = it.areaName, countryCode = it.countryCode) },
        vibeInsights = vibeInsights.map { VibeInsight(vibeName = it.vibeName, insight = it.insight) },
    )

    private fun ProfileIdentity.toJson() = ProfileIdentityJson(
        explorerName = explorerName,
        tagline = tagline,
        avatarEmoji = avatarEmoji,
        totalVisits = totalVisits,
        totalAreas = totalAreas,
        totalVibes = totalVibes,
        geoFootprint = geoFootprint.map { GeoAreaJson(areaName = it.areaName, countryCode = it.countryCode) },
        vibeInsights = vibeInsights.map { VibeInsightJson(vibeName = it.vibeName, insight = it.insight) },
    )
}
