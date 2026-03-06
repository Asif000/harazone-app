package com.areadiscovery.ui.search

import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.ui.summary.BucketDisplayState

internal val DEFAULT_CATEGORY_CHIPS = listOf("Popular cities", "Nearby areas", "Trending")

sealed class SearchUiState {
    data class Loading(
        val query: String,
    ) : SearchUiState()

    data class Idle(
        val recentSearches: List<String> = emptyList(),
        val categoryChips: List<String> = DEFAULT_CATEGORY_CHIPS,
    ) : SearchUiState()

    data class Streaming(
        val query: String,
        val areaName: String,
        val buckets: Map<BucketType, BucketDisplayState>,
    ) : SearchUiState()

    data class Complete(
        val query: String,
        val areaName: String,
        val buckets: Map<BucketType, BucketDisplayState>,
        val pois: List<POI>,
    ) : SearchUiState()

    data class Error(
        val query: String,
        val message: String,
    ) : SearchUiState()
}
