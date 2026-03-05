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
        val mutableLocationProvider = MutableFakeLocationProvider()
        mutableLocationProvider.locationResult = Result.failure(RuntimeException("GPS unavailable"))
        val mutablePipeline = FakePrivacyPipeline(result = Result.success("Alfama, Lisbon"))
        val viewModel = MapViewModel(
            locationProvider = mutableLocationProvider,
            privacyPipeline = mutablePipeline,
        )

        assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)

        mutableLocationProvider.locationResult = Result.success(GpsCoordinates(38.7139, -9.1394))
        viewModel.retry()

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Alfama, Lisbon", state.areaName)
        assertEquals(38.7139, state.latitude)
        assertEquals(-9.1394, state.longitude)
    }
}

private class SuspendingFakePrivacyPipelineForMap : FakePrivacyPipeline() {
    private val deferred = CompletableDeferred<Result<String>>()
    override suspend fun resolveAreaName(): Result<String> = deferred.await()
}

private class MutableFakeLocationProvider : com.areadiscovery.location.LocationProvider {
    var locationResult: Result<GpsCoordinates> = Result.success(GpsCoordinates(38.7139, -9.1394))

    override suspend fun getCurrentLocation(): Result<GpsCoordinates> = locationResult
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        Result.success("Test Area")
}
