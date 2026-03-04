package com.areadiscovery.ui.summary

import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.POI

sealed class SummaryUiState {
    data object Loading : SummaryUiState()

    data class Streaming(
        val buckets: Map<BucketType, BucketDisplayState>,
        val areaName: String,
    ) : SummaryUiState()

    data class Complete(
        val buckets: Map<BucketType, BucketDisplayState>,
        val pois: List<POI>,
        val areaName: String,
    ) : SummaryUiState()

    data class Error(
        val message: String,
    ) : SummaryUiState()
}
