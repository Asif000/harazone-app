package com.harazone.domain.provider

import com.harazone.domain.model.POI

interface PlacesProvider {
    suspend fun enrichPoi(poi: POI): Result<POI>
}
