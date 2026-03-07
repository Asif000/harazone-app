package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.domain.model.WeatherState
import com.areadiscovery.domain.provider.WeatherProvider
import com.areadiscovery.domain.repository.AreaRepository
import com.areadiscovery.domain.usecase.GetAreaPortraitUseCase
import com.areadiscovery.fakes.FakeAnalyticsTracker
import com.areadiscovery.fakes.FakeAreaContextFactory
import com.areadiscovery.fakes.FakeAreaRepository
import com.areadiscovery.fakes.FakeLocationProvider
import com.areadiscovery.fakes.FakeWeatherProvider
import com.areadiscovery.location.GpsCoordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        weatherProvider: WeatherProvider = FakeWeatherProvider(),
    ) = MapViewModel(
        locationProvider = locationProvider,
        getAreaPortrait = GetAreaPortraitUseCase(areaRepository),
        areaContextFactory = areaContextFactory,
        analyticsTracker = analyticsTracker,
        weatherProvider = weatherProvider,
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
        val viewModel = createViewModel(locationProvider = resettableLocation)
        assertIs<MapUiState.Loading>(viewModel.uiState.value)

        resettableLocation.complete(Result.failure(RuntimeException("GPS unavailable")))
        assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)

        resettableLocation.reset()
        viewModel.retry()
        assertIs<MapUiState.Loading>(viewModel.uiState.value)

        resettableLocation.complete(Result.success(GpsCoordinates(38.7139, -9.1394)))
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Alfama, Lisbon", state.areaName)
    }

    @Test
    fun gpsTimeoutTransitionsToLocationFailed() {
        val timeoutDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(timeoutDispatcher)
        try {
            runTest(timeoutDispatcher) {
                val neverCompletingLocation = ResettableFakeLocationProvider()
                val viewModel = createViewModel(locationProvider = neverCompletingLocation)
                assertIs<MapUiState.Loading>(viewModel.uiState.value)
                advanceTimeBy(10_001L)
                val state = assertIs<MapUiState.LocationFailed>(viewModel.uiState.value)
                assertEquals(MapViewModel.LOCATION_FAILURE_MESSAGE, state.message)
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    // --- POI tests ---

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
        createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(mockPOIs))
            ),
            analyticsTracker = analyticsTracker,
        )
        analyticsTracker.assertEventTracked(
            "map_opened",
            mapOf("area_name" to "Manhattan, New York", "poi_count" to "1"),
        )
    }

    // --- POI selection tests ---

    private val samplePoi = POI(
        "Statue of Liberty", "landmark", "Famous statue",
        Confidence.HIGH, 40.6892, -74.0445,
    )

    private fun createReadyViewModel(
        analyticsTracker: FakeAnalyticsTracker = FakeAnalyticsTracker(),
        weatherProvider: WeatherProvider = FakeWeatherProvider(),
    ): Pair<MapViewModel, FakeAnalyticsTracker> {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(listOf(samplePoi)))
            ),
            analyticsTracker = analyticsTracker,
            weatherProvider = weatherProvider,
        )
        return viewModel to analyticsTracker
    }

    @Test
    fun selectPoiUpdatesSelectedPoiInReadyState() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.selectPoi(samplePoi)
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(samplePoi, state.selectedPoi)
    }

    @Test
    fun selectPoiFiresPoiTappedAnalytics() = runTest(testDispatcher) {
        val tracker = FakeAnalyticsTracker()
        val (viewModel, _) = createReadyViewModel(analyticsTracker = tracker)
        viewModel.selectPoi(samplePoi)
        tracker.assertEventTracked(
            "poi_tapped",
            mapOf(
                "area_name" to "Manhattan, New York",
                "poi_name" to "Statue of Liberty",
                "poi_type" to "landmark",
            ),
        )
    }

    @Test
    fun selectPoiNullClearsSelectionWithoutAnalytics() = runTest(testDispatcher) {
        val tracker = FakeAnalyticsTracker()
        val (viewModel, _) = createReadyViewModel(analyticsTracker = tracker)
        viewModel.selectPoi(samplePoi)
        val poiTappedCount = tracker.recordedEvents.count { it.first == "poi_tapped" }
        viewModel.selectPoi(null)
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertNull(state.selectedPoi)
        assertEquals(poiTappedCount, tracker.recordedEvents.count { it.first == "poi_tapped" })
    }

    @Test
    fun toggleListViewActivatesListView() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.toggleListView()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state.showListView)
    }

    @Test
    fun toggleListViewTwiceRestoresMapView() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.toggleListView()
        viewModel.toggleListView()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(state.showListView)
    }

    // --- v3 tests ---

    @Test
    fun switchVibe_updatesActiveVibe() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.switchVibe(Vibe.HISTORY)
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(Vibe.HISTORY, state.activeVibe)
    }

    @Test
    fun switchVibe_firesAnalytics() = runTest(testDispatcher) {
        val tracker = FakeAnalyticsTracker()
        val (viewModel, _) = createReadyViewModel(analyticsTracker = tracker)
        viewModel.switchVibe(Vibe.HISTORY)
        tracker.assertEventTracked("vibe_switched", mapOf("vibe" to "HISTORY"))
    }

    @Test
    fun onMapRenderFailed_setsListViewAndMapFailed() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.onMapRenderFailed()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state.showListView)
        assertTrue(state.mapRenderFailed)
    }

    @Test
    fun openSearchOverlay_setsFlag() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.openSearchOverlay()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state.isSearchOverlayOpen)
    }

    @Test
    fun closeSearchOverlay_clearsFlag() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.openSearchOverlay()
        viewModel.closeSearchOverlay()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(state.isSearchOverlayOpen)
    }

    @Test
    fun toggleFab_flipsExpanded() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        assertFalse((viewModel.uiState.value as MapUiState.Ready).isFabExpanded)
        viewModel.toggleFab()
        assertTrue((viewModel.uiState.value as MapUiState.Ready).isFabExpanded)
        viewModel.toggleFab()
        assertFalse((viewModel.uiState.value as MapUiState.Ready).isFabExpanded)
    }

    @Test
    fun portraitComplete_computesVibePoiCounts() = runTest(testDispatcher) {
        val mixedPois = listOf(
            POI("A", "food", "desc", Confidence.HIGH, 1.0, 1.0, vibe = "character"),
            POI("B", "park", "desc", Confidence.HIGH, 1.0, 1.0, vibe = "character"),
            POI("C", "historic", "desc", Confidence.HIGH, 1.0, 1.0, vibe = "history"),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(mixedPois))
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(2, state.vibePoiCounts[Vibe.CHARACTER])
        assertEquals(1, state.vibePoiCounts[Vibe.HISTORY])
        assertEquals(0, state.vibePoiCounts[Vibe.SAFETY])
    }

    @Test
    fun weatherFetchedAfterLocationResolves() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel(
            weatherProvider = FakeWeatherProvider(
                Result.success(WeatherState(72, 0, "Clear", "\u2600\uFE0F"))
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertNotNull(state.weather)
        assertEquals(72, state.weather!!.temperatureF)
    }

    @Test
    fun weatherFailureDoesNotBreakReadyState() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel(
            weatherProvider = FakeWeatherProvider(Result.failure(RuntimeException("Weather failed"))),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertNull(state.weather)
    }
    // --- Camera idle tests ---

    @Test
    fun onCameraIdle_debouncesCorrectly() {
        val stdDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(stdDispatcher)
        try {
            runTest(stdDispatcher) {
                val locationProvider = object : com.areadiscovery.location.LocationProvider {
                    var geocodeCallCount = 0
                    override suspend fun getCurrentLocation() =
                        Result.success(GpsCoordinates(40.0, -74.0))
                    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                        geocodeCallCount++
                        return Result.success("New Area")
                    }
                }
                val viewModel = createViewModel(locationProvider = locationProvider)
                advanceTimeBy(11_000) // let initial load complete
                assertIs<MapUiState.Ready>(viewModel.uiState.value)

                // Reset count after initial load
                locationProvider.geocodeCallCount = 0

                viewModel.onCameraIdle(1.0, 2.0)
                advanceTimeBy(200)
                viewModel.onCameraIdle(3.0, 4.0)
                advanceTimeBy(200)
                viewModel.onCameraIdle(5.0, 6.0)
                advanceTimeBy(600) // let final debounce settle

                assertEquals(1, locationProvider.geocodeCallCount)
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    @Test
    fun onSearchThisAreaTapped_fetchesPortraitAndHidesButton() = runTest(testDispatcher) {
        val newPois = listOf(
            POI("New Place", "landmark", "desc", Confidence.HIGH, 2.0, 3.0),
        )
        val locationProvider = object : com.areadiscovery.location.LocationProvider {
            private var callIndex = 0
            override suspend fun getCurrentLocation() =
                Result.success(GpsCoordinates(38.7139, -9.1394))
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                callIndex++
                return if (callIndex <= 1) Result.success("Alfama, Lisbon")
                else Result.success("Bairro Alto, Lisbon")
            }
        }
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(newPois))
            ),
        )
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Alfama, Lisbon", state1.areaName)

        // Pan to new area — button appears
        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as MapUiState.Ready).showSearchThisArea)

        // Tap button — fetches portrait, hides button
        viewModel.onSearchThisAreaTapped()
        testScheduler.advanceUntilIdle()
        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Bairro Alto, Lisbon", state2.areaName)
        assertEquals(newPois, state2.pois)
        assertFalse(state2.showSearchThisArea)
    }

    @Test
    fun onCameraIdle_hidesButtonWhenAreaNameUnchanged() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        assertFalse((viewModel.uiState.value as MapUiState.Ready).showSearchThisArea)
    }

    @Test
    fun onCameraIdle_noopsWhenSearchJobActive() = runTest(testDispatcher) {
        val neverCompletingRepo = object : AreaRepository {
            var callCount = 0
            override fun getAreaPortrait(areaName: String, context: com.areadiscovery.domain.model.AreaContext): kotlinx.coroutines.flow.Flow<BucketUpdate> {
                callCount++
                return kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
            }
        }
        val geocodeTracker = object : com.areadiscovery.location.LocationProvider {
            var geocodeCallCount = 0
            override suspend fun getCurrentLocation() =
                Result.success(GpsCoordinates(38.7139, -9.1394))
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                geocodeCallCount++
                return Result.success("Alfama, Lisbon")
            }
        }
        val viewModel = createViewModel(
            locationProvider = geocodeTracker,
            areaRepository = neverCompletingRepo,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val geocodeCountAfterLoad = geocodeTracker.geocodeCallCount

        // Start a search that never completes
        viewModel.submitSearch("some area")

        // Camera idle should be blocked
        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        assertEquals(geocodeCountAfterLoad, geocodeTracker.geocodeCallCount)
    }

    @Test
    fun onSearchThisAreaTapped_emitsErrorEventOnFetchFailure() = runTest(testDispatcher) {
        val failOnSecondCallRepo = object : AreaRepository {
            private var callIndex = 0
            override fun getAreaPortrait(areaName: String, context: com.areadiscovery.domain.model.AreaContext): kotlinx.coroutines.flow.Flow<BucketUpdate> {
                callIndex++
                return if (callIndex <= 1) {
                    kotlinx.coroutines.flow.flowOf(BucketUpdate.PortraitComplete(emptyList()))
                } else {
                    kotlinx.coroutines.flow.flow { throw RuntimeException("fetch failed") }
                }
            }
        }
        val locationProvider = object : com.areadiscovery.location.LocationProvider {
            private var callIndex = 0
            override suspend fun getCurrentLocation() =
                Result.success(GpsCoordinates(38.7139, -9.1394))
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                callIndex++
                return if (callIndex <= 1) Result.success("Alfama, Lisbon")
                else Result.success("New Area, Somewhere")
            }
        }
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = failOnSecondCallRepo,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        val collectedErrors = mutableListOf<String>()
        val collectJob = launch {
            viewModel.errorEvents.collect { collectedErrors.add(it) }
        }

        // Pan to new area, then tap search button
        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        viewModel.onSearchThisAreaTapped()
        testScheduler.advanceUntilIdle()
        assertEquals(1, collectedErrors.size)
        assertEquals("Couldn't load area info. Try panning again.", collectedErrors[0])
        collectJob.cancel()
    }

    @Test
    fun onCameraIdle_showsSearchButtonWhenAreaChanges() = runTest(testDispatcher) {
        val locationProvider = object : com.areadiscovery.location.LocationProvider {
            private var callIndex = 0
            override suspend fun getCurrentLocation() =
                Result.success(GpsCoordinates(38.7139, -9.1394))
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                callIndex++
                return if (callIndex <= 1) Result.success("Alfama, Lisbon")
                else Result.success("Bairro Alto, Lisbon")
            }
        }
        val viewModel = createViewModel(locationProvider = locationProvider)
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(state1.showSearchThisArea)

        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state2.showSearchThisArea)
    }

    @Test
    fun onCameraIdle_hidesSearchButtonWhenPanningBackToSameArea() = runTest(testDispatcher) {
        val locationProvider = object : com.areadiscovery.location.LocationProvider {
            private var callIndex = 0
            override suspend fun getCurrentLocation() =
                Result.success(GpsCoordinates(38.7139, -9.1394))
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                callIndex++
                return when {
                    callIndex <= 1 -> Result.success("Alfama, Lisbon")
                    callIndex == 2 -> Result.success("Bairro Alto, Lisbon")
                    else -> Result.success("Alfama, Lisbon")
                }
            }
        }
        val viewModel = createViewModel(locationProvider = locationProvider)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as MapUiState.Ready).showSearchThisArea)

        viewModel.onCameraIdle(38.7139, -9.1394)
        testScheduler.advanceUntilIdle()
        assertFalse((viewModel.uiState.value as MapUiState.Ready).showSearchThisArea)
    }

    @Test
    fun onCameraIdle_keepsExistingPoisWhenButtonShown() = runTest(testDispatcher) {
        val initialPois = listOf(
            POI("Old Place", "landmark", "desc", Confidence.HIGH, 1.0, 1.0),
        )
        val locationProvider = object : com.areadiscovery.location.LocationProvider {
            private var callIndex = 0
            override suspend fun getCurrentLocation() =
                Result.success(GpsCoordinates(38.7139, -9.1394))
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                callIndex++
                return if (callIndex <= 1) Result.success("Alfama, Lisbon")
                else Result.success("New Area, Somewhere")
            }
        }
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(initialPois))
            ),
        )
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(initialPois, state1.pois)

        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        // Button shows but pois remain unchanged (no auto-fetch)
        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(initialPois, state2.pois)
        assertTrue(state2.showSearchThisArea)
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
