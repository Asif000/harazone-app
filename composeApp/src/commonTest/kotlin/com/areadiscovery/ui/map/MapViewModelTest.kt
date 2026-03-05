package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.fakes.FakeAnalyticsTracker
import com.areadiscovery.fakes.FakeAreaContextFactory
import com.areadiscovery.fakes.FakeAreaRepository
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

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        locationProvider: com.areadiscovery.location.LocationProvider = FakeLocationProvider(),
        privacyPipeline: com.areadiscovery.domain.service.PrivacyPipeline = FakePrivacyPipeline(),
        areaRepository: com.areadiscovery.domain.repository.AreaRepository = FakeAreaRepository(),
        areaContextFactory: com.areadiscovery.domain.service.AreaContextFactory = FakeAreaContextFactory(),
        analyticsTracker: com.areadiscovery.util.AnalyticsTracker = FakeAnalyticsTracker(),
    ) = MapViewModel(
        locationProvider = locationProvider,
        privacyPipeline = privacyPipeline,
        areaRepository = areaRepository,
        areaContextFactory = areaContextFactory,
        analyticsTracker = analyticsTracker,
    )

    @Test
    fun initialStateIsLoading() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val suspendingPipeline = ResettableFakePrivacyPipeline()
        val viewModel = createViewModel(privacyPipeline = suspendingPipeline)

        assertIs<MapUiState.Loading>(viewModel.uiState.value)
    }

    @Test
    fun locationSuccessTransitionsToReady() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
            ),
            privacyPipeline = FakePrivacyPipeline(result = Result.success("Manhattan, New York")),
        )

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Manhattan, New York", state.areaName)
        assertEquals(40.7128, state.latitude)
        assertEquals(-74.0060, state.longitude)
    }

    @Test
    fun locationFailureTransitionsToLocationFailed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.failure(RuntimeException("GPS unavailable")),
            ),
        )

        val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
    }

    @Test
    fun privacyPipelineFailureTransitionsToLocationFailed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            privacyPipeline = FakePrivacyPipeline(result = Result.failure(RuntimeException("Geocoding failed"))),
        )

        val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
    }

    @Test
    fun retryResetsToLoadingAndReloads() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val resettableLocation = ResettableFakeLocationProvider()
        val resettablePipeline = ResettableFakePrivacyPipeline()
        val viewModel = createViewModel(
            locationProvider = resettableLocation,
            privacyPipeline = resettablePipeline,
        )

        // Init: both suspend — state stays Loading
        assertIs<MapUiState.Loading>(viewModel.uiState.value)

        // Complete init with failure
        resettableLocation.complete(Result.failure(RuntimeException("GPS unavailable")))
        assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)

        // Reset fakes for retry call
        resettableLocation.reset()
        resettablePipeline.reset()

        // Call retry() on the SAME ViewModel
        viewModel.retry()

        // Verify Loading is set by retry() before coroutines resolve
        assertIs<MapUiState.Loading>(viewModel.uiState.value)

        // Complete retry with success
        resettableLocation.complete(Result.success(GpsCoordinates(38.7139, -9.1394)))
        resettablePipeline.complete(Result.success("Alfama, Lisbon"))

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Alfama, Lisbon", state.areaName)
        assertEquals(38.7139, state.latitude)
        assertEquals(-9.1394, state.longitude)
    }

    // --- Story 3.2 tests ---

    @Test
    fun poisAreEmptyWhenRepositoryEmitsNoUpdates() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
            ),
            privacyPipeline = FakePrivacyPipeline(result = Result.success("Manhattan, New York")),
            areaRepository = FakeAreaRepository(updates = emptyList()),
        )

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(emptyList(), state.pois)
    }

    @Test
    fun poisPopulatedOnPortraitComplete() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val mockPOIs = listOf(
            POI("Statue of Liberty", "landmark", "Famous statue", Confidence.HIGH, 40.6892, -74.0445),
            POI("Central Park", "nature", "Large park", Confidence.HIGH, 40.7829, -73.9654),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
            ),
            privacyPipeline = FakePrivacyPipeline(result = Result.success("Manhattan, New York")),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(mockPOIs))
            ),
        )

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(mockPOIs, state.pois)
    }

    @Test
    fun analyticsMapOpenedFiredWithCorrectParams() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val mockPOIs = listOf(
            POI("Statue of Liberty", "landmark", "Famous statue", Confidence.HIGH, 40.6892, -74.0445),
        )
        val analyticsTracker = FakeAnalyticsTracker()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
            ),
            privacyPipeline = FakePrivacyPipeline(result = Result.success("Manhattan, New York")),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(mockPOIs))
            ),
            analyticsTracker = analyticsTracker,
        )

        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        analyticsTracker.assertEventTracked(
            "map_opened",
            mapOf("area_name" to "Manhattan, New York", "poi_count" to "1"),
        )
    }

    @Test
    fun noPoisLoadedIfLocationFails() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val areaRepository = FakeAreaRepository()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.failure(RuntimeException("GPS unavailable")),
            ),
            areaRepository = areaRepository,
        )

        assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(0, areaRepository.callCount)
    }
}

private class ResettableFakePrivacyPipeline : FakePrivacyPipeline() {
    private var deferred = CompletableDeferred<Result<String>>()
    override suspend fun resolveAreaName(): Result<String> = deferred.await()
    fun complete(result: Result<String>) { deferred.complete(result) }
    fun reset() { deferred = CompletableDeferred() }
}

private class ResettableFakeLocationProvider : com.areadiscovery.location.LocationProvider {
    private var deferred = CompletableDeferred<Result<GpsCoordinates>>()
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> = deferred.await()
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        Result.success("Test Area")
    fun complete(result: Result<GpsCoordinates>) { deferred.complete(result) }
    fun reset() { deferred = CompletableDeferred() }
}
