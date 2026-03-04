package com.areadiscovery.domain.service

import com.areadiscovery.location.LocationProvider
import com.areadiscovery.util.AppLogger

/**
 * Enforces the location privacy policy (NFR11):
 * GPS coordinates NEVER leave this class.
 * Only the resolved area name string is returned to callers.
 *
 * This is the single architectural enforcement point — not a convention.
 */
class PrivacyPipeline(private val locationProvider: LocationProvider) {

    /**
     * Resolves the current area name from GPS without exposing raw coordinates to callers.
     * Returns a [Result] containing either the area name string or a failure.
     */
    suspend fun resolveAreaName(): Result<String> {
        val coordsResult = locationProvider.getCurrentLocation()
        if (coordsResult.isFailure) {
            AppLogger.e { "PrivacyPipeline: location unavailable — ${coordsResult.exceptionOrNull()?.message}" }
            return Result.failure(coordsResult.exceptionOrNull()!!)
        }

        val coords = coordsResult.getOrThrow()
        AppLogger.d { "PrivacyPipeline: resolving area name (coordinates withheld from log)" }

        val areaNameResult = locationProvider.reverseGeocode(coords.latitude, coords.longitude)
        return areaNameResult.also { result ->
            if (result.isSuccess) {
                AppLogger.d { "PrivacyPipeline resolved area: ${result.getOrThrow()}" }
            } else {
                AppLogger.e { "PrivacyPipeline: reverse geocoding failed — ${result.exceptionOrNull()?.message}" }
            }
        }
    }
}
