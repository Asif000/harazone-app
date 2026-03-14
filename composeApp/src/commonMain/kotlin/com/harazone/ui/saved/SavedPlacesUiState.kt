package com.harazone.ui.saved

import com.harazone.domain.model.SavedPoi
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class DistanceCapsule(
    val label: String,
    val count: Int,
    val isNearest: Boolean,
)

data class DiscoveryStory(
    val summary: String,
    val tags: List<String>,
)

data class SavedPlacesUiState(
    val saves: List<SavedPoi> = emptyList(),
    val filteredSaves: List<SavedPoi> = emptyList(),
    val capsules: List<DistanceCapsule> = emptyList(),
    val activeCapsule: String? = null,
    val searchQuery: String = "",
    val discoveryStory: DiscoveryStory? = null,
    val pendingUnsaveIds: Set<String> = emptySet(),
    val editingNotePoiId: String? = null,
)

// TODO(BACKLOG-MEDIUM): Move haversineKm to shared util — duplicated in MapViewModel. Deduplicate when a third caller appears.
internal fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLng = (lng2 - lng1) * PI / 180.0
    val a = sin(dLat / 2).pow(2) +
        cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
        sin(dLng / 2).pow(2)
    return r * 2 * asin(sqrt(a))
}
