package com.areadiscovery.domain.repository

import com.areadiscovery.domain.model.SavedPoi
import kotlinx.coroutines.flow.Flow

interface SavedPoiRepository {
    fun observeAll(): Flow<List<SavedPoi>>
    fun observeSavedIds(): Flow<Set<String>>
    suspend fun save(poi: SavedPoi)
    suspend fun unsave(poiId: String)
    suspend fun saveWithTimestamp(poi: SavedPoi, timestampMs: Long)
}
