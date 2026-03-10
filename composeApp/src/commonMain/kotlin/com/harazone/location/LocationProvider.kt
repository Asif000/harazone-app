package com.harazone.location

interface LocationProvider {
    /**
     * Returns the current device GPS coordinates.
     * Suspending — waits for a location fix.
     */
    suspend fun getCurrentLocation(): Result<GpsCoordinates>

    /**
     * Reverse geocodes the given coordinates to a human-readable area name
     * (e.g., "Alfama, Lisbon"). Returns a fallback area name if full geocoding fails.
     *
     * IMPORTANT: This is an internal function — callers should NEVER store
     * the coordinates passed here. Use [PrivacyPipeline] as the sole entry point.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String>
}
