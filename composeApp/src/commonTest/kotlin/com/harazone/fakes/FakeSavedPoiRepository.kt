package com.harazone.fakes

import com.harazone.domain.model.SavedPoi
import com.harazone.domain.repository.SavedPoiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSavedPoiRepository : SavedPoiRepository {

    private val _pois = MutableStateFlow<List<SavedPoi>>(emptyList())
    var shouldThrow: Boolean = false
    var lastUpdatedPoiId: String? = null
    var lastUpdatedNote: String? = null

    override fun observeAll(): Flow<List<SavedPoi>> = _pois

    override fun observeSavedIds(): Flow<Set<String>> =
        _pois.map { list -> list.map { it.id }.toSet() }

    override suspend fun save(poi: SavedPoi) {
        if (shouldThrow) throw RuntimeException("Test error")
        _pois.value = _pois.value + poi
    }

    override suspend fun unsave(poiId: String) {
        if (shouldThrow) throw RuntimeException("Test error")
        _pois.value = _pois.value.filter { it.id != poiId }
    }

    override suspend fun updateUserNote(poiId: String, note: String?) {
        if (shouldThrow) throw RuntimeException("Test error")
        lastUpdatedPoiId = poiId
        lastUpdatedNote = note
        _pois.value = _pois.value.map {
            if (it.id == poiId) it.copy(userNote = note) else it
        }
    }
}
