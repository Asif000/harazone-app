package com.areadiscovery.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.domain.model.RecentPlace
import com.areadiscovery.domain.repository.RecentPlacesRepository
import com.areadiscovery.util.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RecentPlacesRepositoryImpl(
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RecentPlacesRepository {

    override fun observeRecent(): Flow<List<RecentPlace>> =
        database.recent_placesQueries
            .observeRecent()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { RecentPlace(it.place_name, it.lat, it.lng) } }

    override suspend fun upsert(place: RecentPlace) {
        withContext(ioDispatcher) {
            database.transaction {
                database.recent_placesQueries.upsertPlace(
                    place_name = place.name,
                    lat = place.latitude,
                    lng = place.longitude,
                    searched_at = clock.nowMs(),
                )
                database.recent_placesQueries.pruneOld()
            }
        }
    }

    override suspend fun clearAll() {
        withContext(ioDispatcher) {
            database.recent_placesQueries.clearAll()
        }
    }
}
