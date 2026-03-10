package com.harazone.domain.model

enum class BucketType {
    SAFETY,
    CHARACTER,
    WHATS_HAPPENING,
    COST,
    HISTORY,
    NEARBY
}

data class BucketContent(
    val type: BucketType,
    val highlight: String,
    val content: String,
    val confidence: Confidence,
    val sources: List<Source>
)
