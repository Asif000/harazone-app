package com.areadiscovery.domain.service

import com.areadiscovery.fakes.FakeLocationProvider
import com.areadiscovery.location.GpsCoordinates
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
        assertTrue(!result.getOrThrow().contains("38.7"))
        assertTrue(!result.getOrThrow().contains("-9.1"))
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
}
