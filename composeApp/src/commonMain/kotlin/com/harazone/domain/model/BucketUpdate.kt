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
        val pois: List<POI>,
        val areaHighlights: List<String> = emptyList(),
    ) : BucketUpdate()

    data class PortraitComplete(
        val pois: List<POI>,
        val areaHighlights: List<String> = emptyList(),
    ) : BucketUpdate()

    data class ContentAvailabilityNote(
        val message: String
    ) : BucketUpdate()

    data class VibesReady(
        val vibes: List<DynamicVibe>,
        val pois: List<POI>,
        val fromCache: Boolean = false,
        val areaHighlights: List<String> = emptyList(),
    ) : BucketUpdate()

    data class DynamicVibeComplete(
        val content: DynamicVibeContent,
    ) : BucketUpdate()

    data class BackgroundBatchReady(
        val pois: List<POI>,
        val batchIndex: Int,
    ) : BucketUpdate()

    data class BackgroundEnrichmentComplete(
        val pois: List<POI>,
        val batchIndex: Int,
    ) : BucketUpdate()

    data object BackgroundFetchComplete : BucketUpdate()
}
