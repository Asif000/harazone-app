package com.areadiscovery.fakes

import com.areadiscovery.location.GpsCoordinates
import com.areadiscovery.location.LocationProvider

class FakeLocationProvider(
    private val locationResult: Result<GpsCoordinates> = Result.success(GpsCoordinates(38.7139, -9.1394)),
    private val geocodeResult: Result<String> = Result.success("Alfama, Lisbon")
) : LocationProvider {

    var locationCallCount = 0
    var geocodeCallCount = 0
    var lastGeocodedCoords: Pair<Double, Double>? = null

    override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
        locationCallCount++
        return locationResult
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
        geocodeCallCount++
        lastGeocodedCoords = latitude to longitude
        return geocodeResult
    }
}
