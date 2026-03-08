package com.areadiscovery.domain.model

data class GeocodingSuggestion(
    val name: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double?,
)
