package com.areadiscovery.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class POI(
    val name: String,
    val type: String,
    val description: String,
    val confidence: Confidence,
    val latitude: Double?,
    val longitude: Double?
)
