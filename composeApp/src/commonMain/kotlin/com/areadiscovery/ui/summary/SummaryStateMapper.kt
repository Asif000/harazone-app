package com.areadiscovery.ui.summary

import com.areadiscovery.domain.model.BucketUpdate

class SummaryStateMapper {

    fun processUpdate(
        currentState: SummaryUiState,
        update: BucketUpdate,
        areaName: String,
    ): SummaryUiState {
        return when (update) {
            is BucketUpdate.ContentDelta -> handleContentDelta(currentState, update, areaName)
            is BucketUpdate.BucketComplete -> handleBucketComplete(currentState, update)
            is BucketUpdate.PortraitComplete -> handlePortraitComplete(currentState, update)
            is BucketUpdate.ContentAvailabilityNote -> handleContentAvailabilityNote(currentState, update)
        }
    }

    private fun handleContentDelta(
        currentState: SummaryUiState,
        delta: BucketUpdate.ContentDelta,
        areaName: String,
    ): SummaryUiState {
        val buckets = when (currentState) {
            is SummaryUiState.Loading, is SummaryUiState.LocationResolving -> emptyMap()
            is SummaryUiState.Streaming -> currentState.buckets
            else -> return currentState
        }

        val resolvedAreaName = (currentState as? SummaryUiState.Streaming)?.areaName ?: areaName

        val existing = buckets[delta.bucketType] ?: BucketDisplayState(
            bucketType = delta.bucketType,
        )

        val updated = existing.copy(
            bodyText = existing.bodyText + delta.textDelta,
            isStreaming = true,
            isComplete = false,
        )

        val newBuckets = buckets + (delta.bucketType to updated)
        return SummaryUiState.Streaming(buckets = newBuckets, areaName = resolvedAreaName)
    }

    private fun handleBucketComplete(
        currentState: SummaryUiState,
        complete: BucketUpdate.BucketComplete,
    ): SummaryUiState {
        val streaming = currentState as? SummaryUiState.Streaming ?: return currentState
        val content = complete.content
        val bucketType = content.type

        val existing = streaming.buckets[bucketType] ?: BucketDisplayState(
            bucketType = bucketType,
        )

        val updated = existing.copy(
            highlightText = content.highlight,
            confidence = content.confidence,
            sources = content.sources,
            isStreaming = false,
            isComplete = true,
        )

        val newBuckets = streaming.buckets + (bucketType to updated)
        return streaming.copy(buckets = newBuckets)
    }

    private fun handleContentAvailabilityNote(
        currentState: SummaryUiState,
        note: BucketUpdate.ContentAvailabilityNote,
    ): SummaryUiState {
        return when (currentState) {
            is SummaryUiState.Streaming -> currentState.copy(contentNote = note.message)
            is SummaryUiState.Complete -> currentState.copy(contentNote = note.message)
            else -> currentState
        }
    }

    private fun handlePortraitComplete(
        currentState: SummaryUiState,
        complete: BucketUpdate.PortraitComplete,
    ): SummaryUiState {
        val streaming = currentState as? SummaryUiState.Streaming ?: return currentState

        // Finalize all buckets
        val finalBuckets = streaming.buckets.mapValues { (_, state) ->
            state.copy(isStreaming = false, isComplete = true)
        }

        return SummaryUiState.Complete(
            buckets = finalBuckets,
            pois = complete.pois,
            areaName = streaming.areaName,
            contentNote = streaming.contentNote,
        )
    }
}
