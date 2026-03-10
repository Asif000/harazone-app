package com.harazone.domain.model

data class AreaPortrait(
    val area: Area,
    val buckets: Map<BucketType, BucketContent>,
    val pois: List<POI>,
    val generatedAt: Long,
    val language: String
)
