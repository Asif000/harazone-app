package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.repository.AreaRepository
import com.areadiscovery.domain.usecase.GetAreaPortraitUseCase
import com.areadiscovery.fakes.FakeAnalyticsTracker
import com.areadiscovery.fakes.FakeAreaContextFactory
import com.areadiscovery.fakes.FakeAreaRepository
import com.areadiscovery.fakes.FakeLocationProvider
import com.areadiscovery.location.GpsCoordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        locationProvider: com.areadiscovery.location.LocationProvider = FakeLocationProvider(),
        areaRepository: AreaRepository = FakeAreaRepository(),
        areaContextFactory: com.areadiscovery.domain.service.AreaContextFactory = FakeAreaContextFactory(),
        analyticsTracker: com.areadiscovery.util.AnalyticsTracker = FakeAnalyticsTracker(),
    ) = MapViewModel(
        locationProvider = locationProvider,
        getAreaPortrait = GetAreaPortraitUseCase(areaRepository),
        areaContextFactory = areaContextFactory,
        analyticsTracker = analyticsTracker,
    )

    @Test
    fun initialStateIsLoading() = runTest(testDispatcher) {

        val suspendingLocation = ResettableFakeLocationProvider()
        val viewModel = createViewModel(locationProvider = suspendingLocation)

        assertIs<MapUiState.Loading>(viewModel.uiState.value)
    }

    @Test
    fun locationSuccessTransitionsToReady() = runTest(testDispatcher) {

        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
        )

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Manhattan, New York", state.areaName)
        assertEquals(40.7128, state.latitude)
        assertEquals(-74.0060, state.longitude)
    }

    @Test
    fun locationFailureTransitionsToLocationFailed() = runTest(testDispatcher) {

        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.failure(RuntimeException("GPS unavailable")),
            ),
        )

        val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
    }

    @Test
    fun geocodeFailureTransitionsToLocationFailed() = runTest(testDispatcher) {

        val areaRepository = FakeAreaRepository()
        val contextFactory = FakeAreaContextFactory()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                geocodeResult = Result.failure(RuntimeException("Geocoding failed")),
            ),
            areaRepository = areaRepository,
            areaContextFactory = contextFactory,
        )

        val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
        assertEquals(0, areaRepository.callCount)
        assertEquals(0, contextFactory.callCount)
    }

    @Test
    fun retryResetsToLoadingAndReloads() = runTest(testDispatcher) {

        val resettableLocation = ResettableFakeLocationProvider()
        val viewModel = createViewModel(
            locationProvider = resettableLocation,
        )

        // Init: location suspends — state stays Loading
        assertIs<MapUiState.Loading>(viewModel.uiState.value)

        // Complete init with failure
        resettableLocation.complete(Result.failure(RuntimeException("GPS unavailable")))
        assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)

        // Reset fake for retry call
        resettableLocation.reset()

        // Call retry() on the SAME ViewModel
        viewModel.retry()

        // Verify Loading is set by retry() before coroutines resolve
        assertIs<MapUiState.Loading>(viewModel.uiState.value)

        // Complete retry with success
        resettableLocation.complete(Result.success(GpsCoordinates(38.7139, -9.1394)))

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Alfama, Lisbon", state.areaName)
        assertEquals(38.7139, state.latitude)
        assertEquals(-9.1394, state.longitude)
        assertEquals(emptyList(), state.pois)
    }

    @Test
    fun gpsTimeoutTransitionsToLocationFailed() {
        // StandardTestDispatcher controls time manually (unlike UnconfinedTestDispatcher)
        val timeoutDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(timeoutDispatcher)

        try {
            runTest(timeoutDispatcher) {
                val neverCompletingLocation = ResettableFakeLocationProvider()
                val viewModel = createViewModel(locationProvider = neverCompletingLocation)

                // StandardTestDispatcher doesn't run eagerly — state is still initial Loading
                assertIs<MapUiState.Loading>(viewModel.uiState.value)

                // Advance past LOCATION_TIMEOUT_MS (10_000L)
                advanceTimeBy(10_001L)

                val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
                assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    // --- Story 3.2 tests ---

    @Test
    fun poisAreEmptyWhenRepositoryEmitsNoUpdates() = runTest(testDispatcher) {

        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(updates = emptyList()),
        )

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(emptyList(), state.pois)
    }

    @Test
    fun poisPopulatedOnPortraitComplete() = runTest(testDispatcher) {

        val mockPOIs = listOf(
            POI("Statue of Liberty", "landmark", "Famous statue", Confidence.HIGH, 40.6892, -74.0445),
            POI("Central Park", "nature", "Large park", Confidence.HIGH, 40.7829, -73.9654),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(mockPOIs))
            ),
        )

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(mockPOIs, state.pois)
    }

    @Test
    fun analyticsMapOpenedFiredWithCorrectParams() = runTest(testDispatcher) {

        val mockPOIs = listOf(
            POI("Statue of Liberty", "landmark", "Famous statue", Confidence.HIGH, 40.6892, -74.0445),
        )
        val analyticsTracker = FakeAnalyticsTracker()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(mockPOIs))
            ),
            analyticsTracker = analyticsTracker,
        )

        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(1, analyticsTracker.recordedEvents.count { it.first == "map_opened" })
        analyticsTracker.assertEventTracked(
            "map_opened",
            mapOf("area_name" to "Manhattan, New York", "poi_count" to "1"),
        )
    }

    @Test
    fun analyticsMapOpenedFiresWithZeroPois() = runTest(testDispatcher) {
        val analyticsTracker = FakeAnalyticsTracker()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(emptyList()))
            ),
            analyticsTracker = analyticsTracker,
        )

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(emptyList(), state.pois)
        analyticsTracker.assertEventTracked(
            "map_opened",
            mapOf("area_name" to "Manhattan, New York", "poi_count" to "0"),
        )
    }

    @Test
    fun areaContextFactoryCalledExactlyOnce() = runTest(testDispatcher) {

        val contextFactory = FakeAreaContextFactory()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaContextFactory = contextFactory,
        )

        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(1, contextFactory.callCount)
    }

    @Test
    fun contextFactoryNotCalledOnLocationFailure() = runTest(testDispatcher) {

        val contextFactory = FakeAreaContextFactory()
        val areaRepository = FakeAreaRepository()
        val locationProvider = FakeLocationProvider(
            locationResult = Result.failure(RuntimeException("GPS unavailable")),
        )
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = areaRepository,
            areaContextFactory = contextFactory,
        )

        assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(0, areaRepository.callCount)
        assertEquals(0, contextFactory.callCount)
        assertEquals(1, locationProvider.locationCallCount)
    }
}

private class ResettableFakeLocationProvider(
    private val geocodeResult: Result<String> = Result.success("Alfama, Lisbon"),
) : com.areadiscovery.location.LocationProvider {
    private var deferred = CompletableDeferred<Result<GpsCoordinates>>()
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> = deferred.await()
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        geocodeResult
    fun complete(result: Result<GpsCoordinates>) { deferred.complete(result) }
    fun reset() { deferred = CompletableDeferred() }
}
