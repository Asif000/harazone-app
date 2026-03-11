package com.harazone.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.repository.SavedPoiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SavedPlacesViewModel(
    private val savedPoiRepository: SavedPoiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedPlacesUiState())
    val uiState: StateFlow<SavedPlacesUiState> = _uiState.asStateFlow()

    private var allSaves: List<SavedPoi> = emptyList()
    private var userLat: Double? = null
    private var userLng: Double? = null

    init {
        viewModelScope.launch {
            savedPoiRepository.observeAll().collect { pois ->
                allSaves = pois
                recompute()
            }
        }
    }

    fun onLocationUpdated(lat: Double?, lng: Double?) {
        userLat = lat
        userLng = lng
        recompute()
    }

    fun selectCapsule(label: String?) {
        _uiState.update { it.copy(activeCapsule = label) }
        recompute()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recompute()
    }

    fun unsavePoi(poiId: String) {
        _uiState.update { it.copy(pendingUnsaveIds = it.pendingUnsaveIds + poiId) }
        recompute()
    }

    fun commitUnsave(poiId: String, undo: Boolean) {
        _uiState.update { it.copy(pendingUnsaveIds = it.pendingUnsaveIds - poiId) }
        if (!undo) {
            viewModelScope.launch {
                try {
                    savedPoiRepository.unsave(poiId)
                } catch (_: Exception) {
                    // DB flow will re-emit if delete failed — card reappears naturally
                }
            }
        } else {
            recompute()
        }
    }

    fun commitAllPendingUnsaves() {
        val ids = _uiState.value.pendingUnsaveIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(pendingUnsaveIds = emptySet()) }
        viewModelScope.launch {
            ids.forEach {
                try {
                    savedPoiRepository.unsave(it)
                } catch (_: Exception) {
                    // Best-effort on dispose — DB flow corrects state
                }
            }
        }
    }

    private fun recompute() {
        _uiState.update { state ->
            val pending = state.pendingUnsaveIds
            val visibleSaves = allSaves.filter { it.id !in pending }
            val capsules = buildCapsules(visibleSaves, userLat, userLng)
            val story = buildDiscoveryStory(visibleSaves)
            val filtered = visibleSaves.filter { poi ->
                matchesCapsule(poi, state.activeCapsule) &&
                    matchesSearch(poi, state.searchQuery)
            }
            state.copy(
                saves = allSaves,
                filteredSaves = filtered,
                capsules = capsules,
                discoveryStory = story,
            )
        }
    }

    private fun matchesCapsule(poi: SavedPoi, capsule: String?): Boolean {
        if (capsule == null || capsule == "All") return true
        return poi.areaName == capsule
    }

    private fun matchesSearch(poi: SavedPoi, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.lowercase()
        return poi.name.lowercase().contains(q) || poi.areaName.lowercase().contains(q)
    }

    companion object {
        internal fun buildCapsules(
            saves: List<SavedPoi>,
            userLat: Double?,
            userLng: Double?,
        ): List<DistanceCapsule> {
            if (saves.isEmpty()) return emptyList()

            val grouped = saves.groupBy { it.areaName }

            val areaCapsules = if (userLat != null && userLng != null) {
                grouped.map { (area, pois) ->
                    val minDist = pois.minOf { haversineKm(userLat, userLng, it.lat, it.lng) }
                    Triple(area, pois.size, minDist)
                }.sortedBy { it.third }
            } else {
                grouped.map { (area, pois) ->
                    Triple(area, pois.size, Double.MAX_VALUE)
                }.sortedByDescending { it.second }
            }

            val nearestArea = if (userLat != null && userLng != null) areaCapsules.firstOrNull()?.first else null

            val capsules = areaCapsules.map { (area, count, _) ->
                DistanceCapsule(
                    label = area,
                    count = count,
                    isNearest = area == nearestArea,
                )
            }

            val allCapsule = DistanceCapsule(label = "All", count = saves.size, isNearest = false)
            return listOf(allCapsule) + capsules
        }

        // TODO(BACKLOG-HIGH): Discovery story is bland — just counts and labels. Should surface interesting patterns: "You love coastal food spots", "All your saves are walkable from each other", time-of-day trends, vibe fingerprint insights. Consider Gemini-generated summary or richer static heuristics.
        internal fun buildDiscoveryStory(saves: List<SavedPoi>): DiscoveryStory? {
            if (saves.size < 2) return null

            val uniqueAreas = saves.map { it.areaName }.distinct().size
            val summary = "${saves.size} places across $uniqueAreas area${if (uniqueAreas == 1) "" else "s"}"

            val tags = mutableListOf<String>()

            val topType = saves.groupBy { it.type.lowercase() }
                .maxByOrNull { it.value.size }
                ?.key
                ?.replaceFirstChar { it.uppercaseChar() }
            if (topType != null) tags.add(topType)

            if (uniqueAreas > 1) tags.add("$uniqueAreas areas")

            if (saves.any { it.userNote != null }) tags.add("Note keeper")

            return DiscoveryStory(summary = summary, tags = tags.take(3))
        }
    }
}
