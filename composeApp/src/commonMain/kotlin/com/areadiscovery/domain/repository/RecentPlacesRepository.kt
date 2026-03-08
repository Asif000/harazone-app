package com.areadiscovery.domain.repository

import com.areadiscovery.domain.model.RecentPlace
import kotlinx.coroutines.flow.Flow

interface RecentPlacesRepository {
    fun observeRecent(): Flow<List<RecentPlace>>
    suspend fun upsert(place: RecentPlace)
    suspend fun clearAll()
}
