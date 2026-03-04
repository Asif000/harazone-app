package com.areadiscovery.domain.model

sealed class BucketUpdate {
    data class ContentDelta(
        val bucketType: BucketType,
        val textDelta: String
    ) : BucketUpdate()

    data class BucketComplete(
        val content: BucketContent
    ) : BucketUpdate()

    data class PortraitComplete(
        val pois: List<POI>
    ) : BucketUpdate()
}
