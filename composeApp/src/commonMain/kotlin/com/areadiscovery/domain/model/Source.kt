package com.areadiscovery.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val title: String,
    val url: String?
)

data class SourceAttribution(
    val confidence: Confidence,
    val sources: List<Source>
)
