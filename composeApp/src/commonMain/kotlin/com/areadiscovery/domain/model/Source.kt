package com.areadiscovery.domain.model

data class Source(
    val title: String,
    val url: String?
)

data class SourceAttribution(
    val confidence: Confidence,
    val sources: List<Source>
)
