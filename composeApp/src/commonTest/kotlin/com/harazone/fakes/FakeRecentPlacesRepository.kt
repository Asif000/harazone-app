package com.harazone.fakes

import com.harazone.domain.model.RecentPlace
import com.harazone.domain.repository.RecentPlacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeRecentPlacesRepository : RecentPlacesRepository {

    private val _recents = MutableStateFlow<List<RecentPlace>>(emptyList())

    var upsertCalls: List<RecentPlace> = emptyList()
        private set
    var clearAllCount: Int = 0
        private set

    fun setRecents(places: List<RecentPlace>) {
        _recents.value = places
    }

    override fun observeRecent(): Flow<List<RecentPlace>> = _recents.asStateFlow()

    override suspend fun upsert(place: RecentPlace) {
        upsertCalls = upsertCalls + place
        // TODO(BACKLOG-LOW): Dedup is case-sensitive here but real DB uses COLLATE NOCASE — inconsistent test behavior
        val updated = listOf(place) + _recents.value.filterNot { it.name == place.name }
        _recents.value = updated.take(10)
    }

    override suspend fun clearAll() {
        clearAllCount++
        _recents.value = emptyList()
    }
}
