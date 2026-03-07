package com.areadiscovery.domain.model

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
    val insight: String = "",
    val hours: String? = null,
    val liveStatus: String? = null,
    val rating: Float? = null,
    val vibeInsights: Map<String, String> = emptyMap(),
)
