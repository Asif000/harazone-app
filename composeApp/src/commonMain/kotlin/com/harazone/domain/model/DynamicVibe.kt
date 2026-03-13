package com.harazone.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DynamicVibe(
    val label: String,
    val icon: String,
    val poiIds: List<String> = emptyList(),
)

@Serializable
data class DynamicVibeContent(
    val label: String,
    val icon: String,
    val highlight: String,
    val content: String,
    val poiIds: List<String> = emptyList(),
)
