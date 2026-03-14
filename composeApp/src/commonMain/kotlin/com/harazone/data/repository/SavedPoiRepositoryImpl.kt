package com.harazone.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.repository.SavedPoiRepository
import com.harazone.util.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SavedPoiRepositoryImpl(
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
                        imageUrl = it.image_url,
                        description = it.description,
                        rating = it.rating?.toFloat(),
                        vibe = it.vibe,
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
                image_url = poi.imageUrl,
                description = poi.description,
                rating = poi.rating?.toDouble(),
                vibe = poi.vibe,
            )
        }
    }

    override suspend fun unsave(poiId: String) {
        withContext(ioDispatcher) {
            database.saved_poisQueries.deleteById(poiId)
        }
    }

    override suspend fun updateUserNote(poiId: String, note: String?) {
        withContext(ioDispatcher) {
            database.saved_poisQueries.updateUserNote(
                user_note = note,
                poi_id = poiId,
            )
        }
    }
}
