package com.harazone.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class POI(
    val name: String,
    val type: String,
    val description: String,
    val confidence: Confidence,
    val latitude: Double?,
    val longitude: Double?,
    val vibe: String = "",
    val vibes: List<String> = emptyList(),
    val insight: String = "",
    val hours: String? = null,
    val liveStatus: String? = null,
    val rating: Float? = null,
    val vibeInsights: Map<String, String> = emptyMap(),
    val wikiSlug: String? = null,
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val userNote: String? = null,
    val priceRange: String? = null,
    val reviewCount: Int? = null,
) {
    val savedId: String get() = "$name|${latitude ?: 0.0}|${longitude ?: 0.0}"

    /** First comma-separated vibe token, normalized for comparison. */
    val primaryVibe: String? get() = vibe.split(",").firstOrNull()?.trim()?.lowercase()?.ifBlank { null }

    /**
     * Merge enrichment data onto this POI. Non-default/non-null enrichment values win;
     * this POI's values are kept as fallback. Use this instead of cherry-picking fields
     * in merge functions — adding a field to POI automatically includes it here.
     */
    fun mergeFrom(other: POI): POI = copy(
        vibe = other.vibe.ifEmpty { vibe },
        vibes = other.vibes.ifEmpty { vibes },
        insight = other.insight.ifEmpty { insight },
        description = other.description.ifEmpty { description },
        hours = other.hours ?: hours,
        liveStatus = other.liveStatus ?: liveStatus,
        rating = other.rating ?: rating,
        priceRange = other.priceRange ?: priceRange,
        reviewCount = other.reviewCount ?: reviewCount,
        imageUrl = other.imageUrl ?: imageUrl,
        imageUrls = other.imageUrls.ifEmpty { imageUrls },
        wikiSlug = other.wikiSlug ?: wikiSlug,
        latitude = other.latitude ?: latitude,
        longitude = other.longitude ?: longitude,
        userNote = other.userNote ?: userNote,
    )
}
