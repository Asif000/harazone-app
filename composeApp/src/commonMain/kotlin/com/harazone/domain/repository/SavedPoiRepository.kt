package com.harazone.domain.repository

import com.harazone.domain.model.SavedPoi
import kotlinx.coroutines.flow.Flow

interface SavedPoiRepository {
    fun observeAll(): Flow<List<SavedPoi>>
    fun observeSavedIds(): Flow<Set<String>>
    suspend fun save(poi: SavedPoi)
    suspend fun unsave(poiId: String)
    suspend fun updateUserNote(poiId: String, note: String?)
    suspend fun visit(poi: SavedPoi)
    suspend fun markBeen(poi: SavedPoi)
    suspend fun unmarkBeen(poiId: String)
    suspend fun recordGoIntent(poiId: String, timestamp: Long)
}
