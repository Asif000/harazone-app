package com.areadiscovery.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.domain.model.SavedPoi
import com.areadiscovery.domain.repository.SavedPoiRepository
import com.areadiscovery.util.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SavedPoiRepositoryImpl(
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SavedPoiRepository {

    override fun observeAll(): Flow<List<SavedPoi>> =
        database.saved_poisQueries
            .observeAll()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows ->
                rows.map {
                    SavedPoi(
                        id = it.poi_id,
                        name = it.name,
                        type = it.type,
                        areaName = it.area_name,
                        lat = it.lat,
                        lng = it.lng,
                        whySpecial = it.why_special,
                        savedAt = it.saved_at,
                        userNote = it.user_note,
                    )
                }
            }

    override fun observeSavedIds(): Flow<Set<String>> =
        database.saved_poisQueries
            .observeSavedIds()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.toSet() }

    override suspend fun save(poi: SavedPoi) {
        withContext(ioDispatcher) {
            database.saved_poisQueries.insertOrReplace(
                poi_id = poi.id,
                name = poi.name,
                type = poi.type,
                area_name = poi.areaName,
                lat = poi.lat,
                lng = poi.lng,
                why_special = poi.whySpecial,
                saved_at = clock.nowMs(),
                user_note = poi.userNote,
            )
        }
    }

    // DevSeeder only — allows backdating saves for DORMANT persona testing
    override suspend fun saveWithTimestamp(poi: SavedPoi, timestampMs: Long) {
        withContext(ioDispatcher) {
            database.saved_poisQueries.insertOrReplace(
                poi_id = poi.id,
                name = poi.name,
                type = poi.type,
                area_name = poi.areaName,
                lat = poi.lat,
                lng = poi.lng,
                why_special = poi.whySpecial,
                saved_at = timestampMs,
                user_note = poi.userNote,
            )
        }
    }

    override suspend fun unsave(poiId: String) {
        withContext(ioDispatcher) {
            database.saved_poisQueries.deleteById(poiId)
        }
    }
}
