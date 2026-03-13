package com.harazone.domain.model

sealed class BucketUpdate {
    data class ContentDelta(
        val bucketType: BucketType,
        val textDelta: String
    ) : BucketUpdate()

    data class BucketComplete(
        val content: BucketContent
    ) : BucketUpdate()

    data class PinsReady(
        val pois: List<POI>
    ) : BucketUpdate()

    data class PortraitComplete(
        val pois: List<POI>
    ) : BucketUpdate()

    data class ContentAvailabilityNote(
        val message: String
    ) : BucketUpdate()

    data class VibesReady(
        val vibes: List<DynamicVibe>,
        val pois: List<POI>,
        val fromCache: Boolean = false,
    ) : BucketUpdate()

    data class DynamicVibeComplete(
        val content: DynamicVibeContent,
    ) : BucketUpdate()
}
