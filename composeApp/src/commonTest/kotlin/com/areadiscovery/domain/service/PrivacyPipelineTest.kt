package com.areadiscovery.domain.service

import com.areadiscovery.fakes.FakeLocationProvider
import com.areadiscovery.location.GpsCoordinates
import com.areadiscovery.location.LocationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivacyPipelineTest {

    @Test
    fun resolveAreaName_returns_area_name_string_not_raw_coordinates() = runTest {
        val fakeProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(38.7139, -9.1394)),
            geocodeResult = Result.success("Alfama, Lisbon")
        )
        val pipeline = PrivacyPipeline(fakeProvider)

        val result = pipeline.resolveAreaName()

        assertTrue(result.isSuccess)
        assertEquals("Alfama, Lisbon", result.getOrThrow())
        // Verify result contains no numeric coordinate patterns from input
        val areaName = result.getOrThrow()
        assertTrue(areaName.none { it.isDigit() }, "Area name should not contain digits from coordinates: $areaName")
    }

    @Test
    fun resolveAreaName_returns_failure_when_location_unavailable() = runTest {
        val fakeProvider = FakeLocationProvider(
            locationResult = Result.failure(Exception("Permission denied"))
        )
        val pipeline = PrivacyPipeline(fakeProvider)

        val result = pipeline.resolveAreaName()

        assertTrue(result.isFailure)
        assertEquals("Permission denied", result.exceptionOrNull()?.message)
    }

    @Test
    fun resolveAreaName_returns_failure_when_reverse_geocoding_fails() = runTest {
        val fakeProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(0.0, 0.0)),
            geocodeResult = Result.failure(Exception("Geocoder unavailable"))
        )
        val pipeline = PrivacyPipeline(fakeProvider)

        val result = pipeline.resolveAreaName()

        assertTrue(result.isFailure)
    }

    @Test
    fun resolveAreaName_calls_location_then_geocoder_exactly_once() = runTest {
        val fakeProvider = FakeLocationProvider()
        val pipeline = PrivacyPipeline(fakeProvider)

        pipeline.resolveAreaName()

        assertEquals(1, fakeProvider.locationCallCount)
        assertEquals(1, fakeProvider.geocodeCallCount)
    }

    @Test
    fun resolveAreaName_passes_coordinates_to_geocoder() = runTest {
        val coords = GpsCoordinates(41.3851, 2.1734)
        val fakeProvider = FakeLocationProvider(
            locationResult = Result.success(coords),
            geocodeResult = Result.success("Eixample, Barcelona")
        )
        val pipeline = PrivacyPipeline(fakeProvider)

        pipeline.resolveAreaName()

        assertEquals(41.3851 to 2.1734, fakeProvider.lastGeocodedCoords)
    }

    @Test
    fun resolveAreaName_returns_fallback_area_name_when_locality_missing() = runTest {
        // AC1: fallback extracts best available area name (e.g., adminArea if locality is null)
        val fakeProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(38.5, -8.0)),
            geocodeResult = Result.success("Alentejo")  // adminArea only (rural area)
        )
        val pipeline = PrivacyPipeline(fakeProvider)

        val result = pipeline.resolveAreaName()

        assertTrue(result.isSuccess)
        assertEquals("Alentejo", result.getOrThrow())
    }

    @Test
    fun resolveAreaName_returns_failure_on_timeout() = runTest {
        val neverCompletingProvider = object : LocationProvider {
            override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
                CompletableDeferred<Unit>().await() // suspends forever
                error("unreachable")
            }
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                error("should not be called")
            }
        }
        val pipeline = PrivacyPipeline(neverCompletingProvider)

        val result = pipeline.resolveAreaName()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("timed out") == true)
    }

    @Test
    fun resolveAreaName_returns_partial_area_name_without_subLocality() = runTest {
        // AC1: fallback when subLocality is null but locality + adminArea exist
        val fakeProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(38.7, -9.1)),
            geocodeResult = Result.success("Lisbon, Lisboa")  // locality + adminArea
        )
        val pipeline = PrivacyPipeline(fakeProvider)

        val result = pipeline.resolveAreaName()

        assertTrue(result.isSuccess)
        assertEquals("Lisbon, Lisboa", result.getOrThrow())
    }
}
