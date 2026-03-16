package com.harazone.domain.model

data class ProfileIdentity(
    val explorerName: String,
    val tagline: String,
    val avatarEmoji: String,
    val totalVisits: Int,
    val totalAreas: Int,
    val totalVibes: Int,
    val geoFootprint: List<GeoArea>,
    val vibeInsights: List<VibeInsight>,
)

data class GeoArea(
    val areaName: String,
    val countryCode: String,
)

data class VibeInsight(
    val vibeName: String,
    val insight: String,
)
