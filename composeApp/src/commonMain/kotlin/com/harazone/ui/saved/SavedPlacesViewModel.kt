package com.harazone.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.repository.SavedPoiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var saveNoteJob: Job? = null
    private var currentEditingNoteText: String = ""

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

    fun onStartEditingNote(poiId: String) {
        val previousPoiId = _uiState.value.editingNotePoiId
        if (previousPoiId != null && previousPoiId != poiId) {
            // Flush pending save for previously edited card before switching
            saveNoteJob?.cancel()
            saveNoteJob = null
            val trimmed = if (currentEditingNoteText.isBlank()) null else currentEditingNoteText
            viewModelScope.launch {
                try {
                    savedPoiRepository.updateUserNote(previousPoiId, trimmed)
                } catch (_: Exception) {
                    // Known limitation (v1): silent failure
                }
            }
        }
        currentEditingNoteText = ""
        _uiState.update { it.copy(editingNotePoiId = poiId) }
    }

    fun onNoteChanged(poiId: String, note: String) {
        currentEditingNoteText = note
        val trimmed = if (note.isBlank()) null else note
        saveNoteJob?.cancel()
        saveNoteJob = viewModelScope.launch {
            delay(500)
            try {
                savedPoiRepository.updateUserNote(poiId, trimmed)
            } catch (_: Exception) {
                // Known limitation (v1): silent failure — DB flow will reflect true state on next emit.
            }
        }
    }

    fun onStopEditingNote(finalNote: String) {
        saveNoteJob?.cancel()
        saveNoteJob = null
        val trimmed = if (finalNote.isBlank()) null else finalNote
        val poiId = _uiState.value.editingNotePoiId
        currentEditingNoteText = ""
        _uiState.update { it.copy(editingNotePoiId = null) }
        if (poiId != null) {
            viewModelScope.launch {
                try {
                    savedPoiRepository.updateUserNote(poiId, trimmed)
                } catch (_: Exception) {
                    // Known limitation (v1): silent failure — DB flow will reflect true state on next emit
                }
            }
        }
    }

    fun flushPendingNoteEdit() {
        val poiId = _uiState.value.editingNotePoiId ?: return
        saveNoteJob?.cancel()
        saveNoteJob = null
        val trimmed = if (currentEditingNoteText.isBlank()) null else currentEditingNoteText
        currentEditingNoteText = ""
        _uiState.update { it.copy(editingNotePoiId = null) }
        viewModelScope.launch {
            try {
                savedPoiRepository.updateUserNote(poiId, trimmed)
            } catch (_: Exception) {
                // Known limitation (v1): silent failure
            }
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

        internal fun buildDiscoveryStory(saves: List<SavedPoi>): DiscoveryStory? {
            if (saves.size < 5) return null

            val uniqueAreas = saves.map { it.areaName }.distinct()
            val vibeGroups = saves.filter { it.vibe.isNotEmpty() }.groupBy { it.vibe }
            val typeGroups = saves.groupBy { it.type.lowercase() }
            val topVibe = vibeGroups.maxByOrNull { it.value.size }
            val topType = typeGroups.maxByOrNull { it.value.size }

            // Build a personality-driven summary instead of just counts
            val summary = buildSummaryLine(saves, uniqueAreas, topVibe, topType)

            val tags = mutableListOf<String>()

            // Vibe fingerprint tag
            if (topVibe != null && topVibe.value.size >= 2) {
                val vibeName = topVibe.key.lowercase().replaceFirstChar { it.uppercaseChar() }
                tags.add("$vibeName lover")
            }

            // Explorer breadth tag
            when {
                uniqueAreas.size >= 4 -> tags.add("Globe trotter")
                uniqueAreas.size >= 2 -> tags.add("${uniqueAreas.size} areas explored")
            }

            // Type preference tag
            if (topType != null && topType.value.size >= 2) {
                val typeName = topType.key.replaceFirstChar { it.uppercaseChar() }
                tags.add("$typeName seeker")
            }

            // Curator tag
            val notedCount = saves.count { it.userNote != null }
            if (notedCount >= 2) tags.add("Note keeper")

            return DiscoveryStory(summary = summary, tags = tags.take(3))
        }

        private fun buildSummaryLine(
            saves: List<SavedPoi>,
            uniqueAreas: List<String>,
            topVibe: Map.Entry<String, List<SavedPoi>>?,
            topType: Map.Entry<String, List<SavedPoi>>?,
        ): String {
            val vibeName = topVibe?.key?.lowercase()?.replaceFirstChar { it.uppercaseChar() }
            val typeName = topType?.key?.lowercase()
            val areaList = formatAreaList(uniqueAreas)
            val vibeHint = if (vibeName != null) " — mostly chasing $vibeName vibes" else ""

            // Pattern: multi-area wanderer
            if (uniqueAreas.size >= 3) {
                return "${saves.size} places across $areaList$vibeHint. You don't just visit — you collect."
            }

            // Pattern: two areas
            if (uniqueAreas.size == 2) {
                return "From $areaList — ${saves.size} discoveries$vibeHint."
            }

            // Pattern: single area, dominant vibe
            if (topVibe != null && topVibe.value.size * 2 >= saves.size && vibeName != null) {
                return "You're drawn to the $vibeName side of $areaList. ${saves.size} discoveries and counting."
            }

            // Pattern: single area, type-heavy
            if (topType != null && topType.value.size * 2 >= saves.size && typeName != null) {
                return "A $typeName explorer in $areaList — ${topType.value.size} of your ${saves.size} saves."
            }

            // Default
            return "${saves.size} discoveries in $areaList. Your map is taking shape."
        }

        private fun formatAreaList(areas: List<String>): String = when {
            areas.size <= 2 -> areas.joinToString(" and ")
            else -> areas.dropLast(1).joinToString(", ") + " and " + areas.last()
        }
    }
}
