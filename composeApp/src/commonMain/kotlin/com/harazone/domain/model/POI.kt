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
}
