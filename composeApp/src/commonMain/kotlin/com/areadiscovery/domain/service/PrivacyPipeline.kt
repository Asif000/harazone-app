package com.areadiscovery.domain.service

import com.areadiscovery.location.LocationProvider
import com.areadiscovery.util.AppLogger
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enforces the location privacy policy (NFR11):
 * GPS coordinates NEVER leave this class.
 * Only the resolved area name string is returned to callers.
 *
 * This is the single architectural enforcement point — not a convention.
 */
open class PrivacyPipeline(private val locationProvider: LocationProvider) {

    /**
     * Resolves the current area name from GPS without exposing raw coordinates to callers.
     * Returns a [Result] containing either the area name string or a failure.
     */
    open suspend fun resolveAreaName(): Result<String> {
        val result = withTimeoutOrNull(GPS_TIMEOUT_MS) {
            resolveAreaNameInternal()
        }
        if (result == null) {
            AppLogger.e { "PrivacyPipeline: location resolution timed out after ${GPS_TIMEOUT_MS}ms" }
            return Result.failure(Exception("Location resolution timed out"))
        }
        return result
    }

    private suspend fun resolveAreaNameInternal(): Result<String> {
        val coordsResult = locationProvider.getCurrentLocation()
        if (coordsResult.isFailure) {
            AppLogger.e { "PrivacyPipeline: location unavailable — ${coordsResult.exceptionOrNull()?.message}" }
            return Result.failure(coordsResult.exceptionOrNull() ?: Exception("Unknown location error"))
        }

        val coords = coordsResult.getOrThrow()
        AppLogger.d { "PrivacyPipeline: resolving area name (coordinates withheld from log)" }

        val areaNameResult = locationProvider.reverseGeocode(coords.latitude, coords.longitude)
        return areaNameResult.also { r ->
            if (r.isSuccess) {
                AppLogger.d { "PrivacyPipeline resolved area: ${r.getOrThrow()}" }
            } else {
                AppLogger.e { "PrivacyPipeline: reverse geocoding failed — ${r.exceptionOrNull()?.message}" }
            }
        }
    }

    companion object {
        internal const val GPS_TIMEOUT_MS = 10_000L
    }
}
