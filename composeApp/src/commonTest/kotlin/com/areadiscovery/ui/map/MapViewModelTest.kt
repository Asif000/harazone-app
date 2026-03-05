package com.areadiscovery.ui.map

import com.areadiscovery.fakes.FakeLocationProvider
import com.areadiscovery.fakes.FakePrivacyPipeline
import com.areadiscovery.location.GpsCoordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private lateinit var fakeLocationProvider: FakeLocationProvider
    private lateinit var fakePipeline: FakePrivacyPipeline

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MapViewModel(
        locationProvider = fakeLocationProvider,
        privacyPipeline = fakePipeline,
    )

    @Test
    fun initialStateIsLoading() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val suspendingPipeline = SuspendingFakePrivacyPipelineForMap()
        fakeLocationProvider = FakeLocationProvider()
        val viewModel = MapViewModel(
            locationProvider = fakeLocationProvider,
            privacyPipeline = suspendingPipeline,
        )

        assertIs<MapUiState.Loading>(viewModel.uiState.value)
    }

    @Test
    fun locationSuccessTransitionsToReady() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        fakeLocationProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
        )
        fakePipeline = FakePrivacyPipeline(result = Result.success("Manhattan, New York"))
        val viewModel = createViewModel()

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Manhattan, New York", state.areaName)
        assertEquals(40.7128, state.latitude)
        assertEquals(-74.0060, state.longitude)
    }

    @Test
    fun locationFailureTransitionsToLocationFailed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        fakeLocationProvider = FakeLocationProvider(
            locationResult = Result.failure(RuntimeException("GPS unavailable")),
        )
        fakePipeline = FakePrivacyPipeline()
        val viewModel = createViewModel()

        val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
    }

    @Test
    fun privacyPipelineFailureTransitionsToLocationFailed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        fakeLocationProvider = FakeLocationProvider()
        fakePipeline = FakePrivacyPipeline(result = Result.failure(RuntimeException("Geocoding failed")))
        val viewModel = createViewModel()

        val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
    }

    @Test
    fun retryResetsToLoadingAndReloads() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val suspendingLocation = SuspendingFakeLocationProvider()
        val suspendingPipeline = SuspendingFakePrivacyPipelineForMap()
        val viewModel = MapViewModel(
            locationProvider = suspendingLocation,
            privacyPipeline = suspendingPipeline,
        )

        // Init: both suspend — state stays Loading
        assertIs<MapUiState.Loading>(viewModel.uiState.value)

        // Complete with failure
        suspendingLocation.complete(Result.failure(RuntimeException("GPS unavailable")))
        assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)

        // Retry: reset state + new suspending calls
        val suspendingLocation2 = SuspendingFakeLocationProvider()
        val suspendingPipeline2 = SuspendingFakePrivacyPipelineForMap()
        val retryViewModel = MapViewModel(
            locationProvider = suspendingLocation2,
            privacyPipeline = suspendingPipeline2,
        )

        // Verify Loading is observable
        assertIs<MapUiState.Loading>(retryViewModel.uiState.value)

        // Complete with success
        suspendingLocation2.complete(Result.success(GpsCoordinates(38.7139, -9.1394)))
        suspendingPipeline2.complete(Result.success("Alfama, Lisbon"))

        val state = assertIs<MapUiState.Ready>(retryViewModel.uiState.value)
        assertEquals("Alfama, Lisbon", state.areaName)
        assertEquals(38.7139, state.latitude)
        assertEquals(-9.1394, state.longitude)
    }
}

private class SuspendingFakePrivacyPipelineForMap : FakePrivacyPipeline() {
    private val deferred = CompletableDeferred<Result<String>>()
    override suspend fun resolveAreaName(): Result<String> = deferred.await()
    fun complete(result: Result<String>) { deferred.complete(result) }
}

private class SuspendingFakeLocationProvider : com.areadiscovery.location.LocationProvider {
    private val deferred = CompletableDeferred<Result<GpsCoordinates>>()
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> = deferred.await()
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        Result.success("Test Area")
    fun complete(result: Result<GpsCoordinates>) { deferred.complete(result) }
}
