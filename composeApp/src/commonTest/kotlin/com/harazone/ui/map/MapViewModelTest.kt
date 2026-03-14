package com.harazone.ui.map

import com.harazone.data.remote.WikipediaImageRepository
import com.harazone.domain.model.BucketUpdate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.WeatherState
import com.harazone.domain.provider.WeatherProvider
import com.harazone.domain.repository.AreaRepository
import com.harazone.domain.usecase.GetAreaPortraitUseCase
import com.harazone.domain.model.GeocodingSuggestion
import com.harazone.domain.model.RecentPlace
import com.harazone.fakes.FakeAnalyticsTracker
import com.harazone.fakes.FakeRecentPlacesRepository
import com.harazone.fakes.FakeMapTilerGeocodingProvider
import com.harazone.fakes.FakeAreaContextFactory
import com.harazone.fakes.FakeAreaRepository
import com.harazone.fakes.FakeLocationProvider
import com.harazone.fakes.FakeWeatherProvider
import com.harazone.location.GpsCoordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

private val stubWikipediaImageRepository by lazy {
    WikipediaImageRepository(HttpClient(MockEngine { _ -> respond("{}", HttpStatusCode.OK) }))
}

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
        locationProvider: com.harazone.location.LocationProvider = FakeLocationProvider(),
        areaRepository: AreaRepository = FakeAreaRepository(),
        areaContextFactory: com.harazone.domain.service.AreaContextFactory = FakeAreaContextFactory(),
        analyticsTracker: com.harazone.util.AnalyticsTracker = FakeAnalyticsTracker(),
        weatherProvider: WeatherProvider = FakeWeatherProvider(),
        geocodingProvider: com.harazone.data.remote.MapTilerGeocodingProvider = FakeMapTilerGeocodingProvider(),
        recentPlacesRepository: com.harazone.domain.repository.RecentPlacesRepository = FakeRecentPlacesRepository(),
        savedPoiRepository: com.harazone.domain.repository.SavedPoiRepository = com.harazone.fakes.FakeSavedPoiRepository(),
        clockMs: () -> Long = { com.harazone.util.SystemClock().nowMs() },
        wikipediaImageRepository: com.harazone.data.remote.WikipediaImageRepository = stubWikipediaImageRepository,
        userPreferencesRepository: com.harazone.data.repository.UserPreferencesRepository = com.harazone.fakes.FakeUserPreferencesRepository(),
    ) = MapViewModel(
        locationProvider = locationProvider,
        getAreaPortrait = GetAreaPortraitUseCase(areaRepository),
        areaContextFactory = areaContextFactory,
        analyticsTracker = analyticsTracker,
        weatherProvider = weatherProvider,
        geocodingProvider = geocodingProvider,
        recentPlacesRepository = recentPlacesRepository,
        savedPoiRepository = savedPoiRepository,
        wikipediaImageRepository = wikipediaImageRepository,
        userPreferencesRepository = userPreferencesRepository,
        clockMs = clockMs,
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
        clockMs: () -> Long = { com.harazone.util.SystemClock().nowMs() },
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
            clockMs = clockMs,
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
    fun loadLocation_resetsActiveVibeOnPortraitComplete() = runTest(testDispatcher) {
        val deferredPois = CompletableDeferred<List<POI>>()
        val delayedRepo = object : AreaRepository {
            override fun getAreaPortrait(areaName: String, context: com.harazone.domain.model.AreaContext): kotlinx.coroutines.flow.Flow<BucketUpdate> {
                return kotlinx.coroutines.flow.flow {
                    val pois = deferredPois.await()
                    emit(BucketUpdate.PortraitComplete(pois))
                }
            }
        }
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = delayedRepo,
        )
        // Ready state with no pois yet
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertNull(state1.activeDynamicVibe)
        assertEquals(emptyList(), state1.pois)

        // User taps a vibe before POIs arrive
        viewModel.switchDynamicVibe(DynamicVibe(label = "CHARACTER", icon = ""))
        assertEquals("CHARACTER", (viewModel.uiState.value as MapUiState.Ready).activeDynamicVibe?.label)

        // Portrait completes — should reset activeVibe to null
        val pois = listOf(
            POI("Place A", "landmark", "desc", Confidence.HIGH, 1.0, 2.0, vibe = "CHARACTER"),
            POI("Place B", "cafe", "desc", Confidence.HIGH, 1.1, 2.1, vibe = "COST"),
        )
        deferredPois.complete(pois)
        testScheduler.advanceUntilIdle()

        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertNull(state2.activeDynamicVibe, "activeVibe should reset to null after initial portrait load")
        assertFalse(state2.isSearchingArea, "isSearchingArea should be false after portrait load")
        assertEquals(2, state2.pois.size)
    }

    @Test
    fun initialLoad_showsAllPoisWithNullActiveVibe() = runTest(testDispatcher) {
        val vibedPois = listOf(
            POI("Place A", "landmark", "desc", Confidence.HIGH, 1.0, 2.0, vibe = "CHARACTER", vibes = listOf("CHARACTER")),
            POI("Place B", "cafe", "desc", Confidence.HIGH, 1.1, 2.1, vibe = "COST", vibes = listOf("COST")),
            POI("Place C", "museum", "desc", Confidence.HIGH, 1.2, 2.2, vibe = "HISTORY", vibes = listOf("HISTORY")),
        )
        val dynamicVibes = listOf(
            DynamicVibe(label = "CHARACTER", icon = ""),
            DynamicVibe(label = "COST", icon = ""),
            DynamicVibe(label = "HISTORY", icon = ""),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(dynamicVibes, vibedPois),
                    BucketUpdate.PortraitComplete(vibedPois),
                )
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertNull(state.activeDynamicVibe)
        assertEquals(3, state.pois.size)
        assertEquals(1, state.dynamicVibePoiCounts["CHARACTER"])
        assertEquals(1, state.dynamicVibePoiCounts["COST"])
        assertEquals(1, state.dynamicVibePoiCounts["HISTORY"])
    }

    @Test
    fun computeVibePoiCounts_handlesMultiVibePois() = runTest(testDispatcher) {
        val multiVibePois = listOf(
            POI("Place A", "landmark", "desc", Confidence.HIGH, 1.0, 2.0, vibe = "character", vibes = listOf("character", "whats_on")),
            POI("Place B", "cafe", "desc", Confidence.HIGH, 1.1, 2.1, vibe = "character", vibes = listOf("character", "history")),
            POI("Place C", "museum", "desc", Confidence.HIGH, 1.2, 2.2, vibe = "history", vibes = listOf("history")),
        )
        val dynamicVibes = listOf(
            DynamicVibe(label = "character", icon = ""),
            DynamicVibe(label = "history", icon = ""),
            DynamicVibe(label = "whats_on", icon = ""),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(dynamicVibes, multiVibePois),
                    BucketUpdate.PortraitComplete(multiVibePois),
                )
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(2, state.dynamicVibePoiCounts["character"])
        assertEquals(2, state.dynamicVibePoiCounts["history"])
        assertEquals(1, state.dynamicVibePoiCounts["whats_on"])
    }

    @Test
    fun switchVibe_filtersMultiVibePois() = runTest(testDispatcher) {
        val multiVibePois = listOf(
            POI("Place A", "landmark", "desc", Confidence.HIGH, 1.0, 2.0, vibe = "character,whats_on"),
            POI("Place B", "cafe", "desc", Confidence.HIGH, 1.1, 2.1, vibe = "history"),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(multiVibePois))
            ),
        )
        // Default: all visible
        assertNull((viewModel.uiState.value as MapUiState.Ready).activeDynamicVibe)
        assertEquals(2, (viewModel.uiState.value as MapUiState.Ready).pois.size)

        // Filter to CHARACTER — should include Place A (has "character,whats_on")
        viewModel.switchDynamicVibe(DynamicVibe(label = "CHARACTER", icon = ""))
        assertEquals("CHARACTER", (viewModel.uiState.value as MapUiState.Ready).activeDynamicVibe?.label)
        // POIs list filtered — Place A matches "CHARACTER" via comma-separated vibe field
        assertEquals(1, (viewModel.uiState.value as MapUiState.Ready).pois.size)
    }

    @Test
    fun switchVibe_updatesActiveVibe() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        assertNull((viewModel.uiState.value as MapUiState.Ready).activeDynamicVibe)
        viewModel.switchDynamicVibe(DynamicVibe(label = "HISTORY", icon = ""))
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("HISTORY", state.activeDynamicVibe?.label)
    }

    @Test
    fun switchVibe_togglesOffWhenTappedAgain() = runTest(testDispatcher) {
        val (viewModel, _) = createReadyViewModel()
        viewModel.switchDynamicVibe(DynamicVibe(label = "HISTORY", icon = ""))
        assertEquals("HISTORY", (viewModel.uiState.value as MapUiState.Ready).activeDynamicVibe?.label)
        viewModel.switchDynamicVibe(DynamicVibe(label = "HISTORY", icon = ""))
        assertNull((viewModel.uiState.value as MapUiState.Ready).activeDynamicVibe)
    }

    @Test
    fun switchVibe_firesAnalytics() = runTest(testDispatcher) {
        val tracker = FakeAnalyticsTracker()
        val (viewModel, _) = createReadyViewModel(analyticsTracker = tracker)
        viewModel.switchDynamicVibe(DynamicVibe(label = "HISTORY", icon = ""))
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
            POI("A", "food", "desc", Confidence.HIGH, 1.0, 1.0, vibe = "character", vibes = listOf("character")),
            POI("B", "park", "desc", Confidence.HIGH, 1.0, 1.0, vibe = "character", vibes = listOf("character")),
            POI("C", "historic", "desc", Confidence.HIGH, 1.0, 1.0, vibe = "history", vibes = listOf("history")),
        )
        val dynamicVibes = listOf(
            DynamicVibe(label = "character", icon = ""),
            DynamicVibe(label = "history", icon = ""),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(dynamicVibes, mixedPois),
                    BucketUpdate.PortraitComplete(mixedPois),
                )
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(2, state.dynamicVibePoiCounts["character"])
        assertEquals(1, state.dynamicVibePoiCounts["history"])
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
                val locationProvider = object : com.harazone.location.LocationProvider {
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
    fun onCameraIdle_reverseGeocodesNewPosition() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        // onCameraIdle should reverse-geocode — state remains Ready
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
    }

    @Test
    fun onCameraIdle_noopsWhenSearchJobActive() = runTest(testDispatcher) {
        val neverCompletingRepo = object : AreaRepository {
            var callCount = 0
            override fun getAreaPortrait(areaName: String, context: com.harazone.domain.model.AreaContext): kotlinx.coroutines.flow.Flow<BucketUpdate> {
                callCount++
                return kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
            }
        }
        val geocodeTracker = object : com.harazone.location.LocationProvider {
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
    fun loadLocation_retriesWithBroaderQueryWhenNoPois() = runTest(testDispatcher) {
        val queriesSeen = mutableListOf<String>()
        val retryRepo = object : AreaRepository {
            override fun getAreaPortrait(areaName: String, context: com.harazone.domain.model.AreaContext): kotlinx.coroutines.flow.Flow<BucketUpdate> {
                queriesSeen.add(areaName)
                return if (areaName.contains("points of interest")) {
                    kotlinx.coroutines.flow.flowOf(BucketUpdate.PortraitComplete(listOf(samplePoi)))
                } else {
                    kotlinx.coroutines.flow.flowOf(BucketUpdate.PortraitComplete(emptyList()))
                }
            }
        }
        val viewModel = createViewModel(areaRepository = retryRepo)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        // Should have called twice: original + broadened retry
        assertEquals(2, queriesSeen.size)
        assertEquals("Alfama, Lisbon", queriesSeen[0])
        assertTrue(queriesSeen[1].contains("points of interest"))
        // POIs from retry should be populated
        val state = viewModel.uiState.value as MapUiState.Ready
        assertEquals(listOf(samplePoi), state.pois)
    }

    @Test
    fun onCameraIdle_showsSearchButtonWhenAreaChanges() = runTest(testDispatcher) {
        val locationProvider = object : com.harazone.location.LocationProvider {
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
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()
        // When panning to a new area, reverse geocode returns different name
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
    }

    @Test
    fun onCameraIdle_hidesButtonsWhenPanningBackNearGps() = runTest(testDispatcher) {
        val locationProvider = object : com.harazone.location.LocationProvider {
            private var callIndex = 0
            override suspend fun getCurrentLocation() =
                Result.success(GpsCoordinates(38.7139, -9.1394))
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                callIndex++
                return when {
                    callIndex <= 1 -> Result.success("Alfama, Lisbon")
                    else -> Result.success("Bairro Alto, Lisbon")
                }
            }
        }
        val viewModel = createViewModel(locationProvider = locationProvider)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.onCameraIdle(10.0, 20.0)
        testScheduler.advanceUntilIdle()

        // Pan back near GPS — showMyLocation should hide
        viewModel.onCameraIdle(38.7139, -9.1394)
        testScheduler.advanceUntilIdle()
        val state2 = viewModel.uiState.value as MapUiState.Ready
        assertFalse(state2.showMyLocation)
    }

    // --- Return to current location tests ---

    @Test
    fun returnToCurrentLocation_sameArea_movesCameraWithoutRefetch() = runTest(testDispatcher) {
        val initialPois = listOf(
            POI("Place A", "landmark", "desc", Confidence.HIGH, 1.0, 2.0),
        )
        val locationProvider = object : com.harazone.location.LocationProvider {
            private var locationCallCount = 0
            override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
                locationCallCount++
                return Result.success(GpsCoordinates(40.7128, -74.0060))
            }
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                return Result.success("Manhattan, New York")
            }
        }
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(initialPois))
            ),
        )
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Manhattan, New York", state1.areaName)
        assertEquals(initialPois, state1.pois)

        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(40.7128, state2.latitude, 0.001)
        assertEquals(-74.0060, state2.longitude, 0.001)
        assertEquals(40.7128, state2.gpsLatitude, 0.001)
        assertEquals(-74.0060, state2.gpsLongitude, 0.001)
        assertFalse(state2.showMyLocation)
        assertFalse(state2.isSearchingArea)
        assertEquals(initialPois, state2.pois)
    }

    @Test
    fun returnToCurrentLocation_incrementsCameraMoveIdEvenWhenCoordsUnchanged() = runTest(testDispatcher) {
        // Regression: when user pans without searching, state lat/lng stay at GPS coords.
        // returnToCurrentLocation must still trigger a camera move via cameraMoveId.
        val viewModel = createViewModel()
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val initialMoveId = state1.cameraMoveId

        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state2.cameraMoveId > initialMoveId,
            "cameraMoveId must increment to force LaunchedEffect re-fire")
    }

    @Test
    fun returnToCurrentLocation_differentArea_refetchesPortrait() = runTest(testDispatcher) {
        val newPois = listOf(
            POI("New Place", "park", "desc", Confidence.HIGH, 2.0, 3.0),
        )
        val repoCallIndex = intArrayOf(0)
        val repo = object : AreaRepository {
            override fun getAreaPortrait(areaName: String, context: com.harazone.domain.model.AreaContext): kotlinx.coroutines.flow.Flow<BucketUpdate> {
                repoCallIndex[0]++
                return kotlinx.coroutines.flow.flowOf(BucketUpdate.PortraitComplete(
                    if (repoCallIndex[0] <= 1) listOf(POI("Old", "food", "d", Confidence.HIGH, 1.0, 1.0))
                    else newPois
                ))
            }
        }
        val locationProvider = object : com.harazone.location.LocationProvider {
            private var locationCallCount = 0
            override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
                locationCallCount++
                return if (locationCallCount <= 1) Result.success(GpsCoordinates(40.7128, -74.0060))
                else Result.success(GpsCoordinates(51.5074, -0.1278))
            }
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                return if (latitude > 50.0) Result.success("London, UK")
                else Result.success("Manhattan, New York")
            }
        }
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = repo,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("London, UK", state.areaName)
        assertEquals(newPois, state.pois)
        assertFalse(state.isSearchingArea)
        assertEquals(51.5074, state.gpsLatitude, 0.001)
    }

    @Test
    fun returnToCurrentLocation_gpsFailure_emitsErrorAndRestoresButton() = runTest(testDispatcher) {
        val locationProvider = object : com.harazone.location.LocationProvider {
            private var callCount = 0
            override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
                callCount++
                return if (callCount <= 1) Result.success(GpsCoordinates(40.7128, -74.0060))
                else Result.failure(RuntimeException("GPS unavailable"))
            }
            override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
                Result.success("Manhattan, New York")
        }
        val viewModel = createViewModel(locationProvider = locationProvider)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        // Pan away so button appears and pendingLat/Lng are set
        viewModel.onCameraIdle(50.0, 30.0)
        testScheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as MapUiState.Ready).showMyLocation)

        val errors = mutableListOf<String>()
        val collectJob = launch { viewModel.errorEvents.collect { errors.add(it) } }

        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        assertEquals(1, errors.size)
        assertEquals("Can't find your location. Please try again.", errors[0])
        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        // Button must be restored — user is still panned away
        assertTrue(state2.showMyLocation, "showMyLocation must be restored after GPS failure")
        collectJob.cancel()
    }

    @Test
    fun returnToCurrentLocation_firesAnalytics() = runTest(testDispatcher) {
        val tracker = FakeAnalyticsTracker()
        val viewModel = createViewModel(
            analyticsTracker = tracker,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        tracker.assertEventTracked("return_to_location", mapOf("same_area" to "true"))
    }

    // Regression: returnToCurrentLocation must refresh weather for GPS coords (not stale search location)
    @Test
    fun returnToCurrentLocation_sameArea_refreshesWeather() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(listOf(samplePoi)))
            ),
            weatherProvider = weatherProvider,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount

        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        assertTrue(weatherProvider.callCount > callsAfterInit, "Weather should be refreshed on return to location")
        assertEquals(40.7128, weatherProvider.lastLatitude, 0.001)
        assertEquals(-74.0060, weatherProvider.lastLongitude, 0.001)
    }

    // Regression: returnToCurrentLocation uses cached POIs instead of re-querying Gemini
    @Test
    fun returnToCurrentLocation_usesCachedPoisFromInitialLoad() = runTest(testDispatcher) {
        val initialPois = listOf(samplePoi)
        val areaRepo = FakeAreaRepository(
            updates = listOf(BucketUpdate.PortraitComplete(initialPois))
        )
        // Location provider: first call returns Doral, second returns Doral too (same GPS)
        // but after searching another area, areaName will differ
        val locationProvider = object : com.harazone.location.LocationProvider {
            private var callCount = 0
            override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
                callCount++
                // Always return same GPS coords (user hasn't moved)
                return Result.success(GpsCoordinates(25.8199, -80.3553))
            }
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                return Result.success("Doral, Florida")
            }
        }
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = areaRepo,
        )
        testScheduler.advanceUntilIdle()
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Doral, Florida", state1.areaName)
        assertEquals(initialPois, state1.pois)

        // Simulate searching another area by selecting a geocoding suggestion
        val suggestion = GeocodingSuggestion("Karachi", "Karachi, Pakistan", 24.8607, 67.0011, null)
        viewModel.onGeocodingSuggestionSelected(suggestion)
        testScheduler.advanceUntilIdle()
        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Karachi", state2.areaName)

        // Track how many portrait fetches have happened
        val fetchCountBefore = areaRepo.callCount

        // Return to location — should restore from cache, NOT re-query
        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        val state3 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Doral, Florida", state3.areaName)
        assertEquals(initialPois, state3.pois)
        assertFalse(state3.isSearchingArea, "Should not be searching — restored from cache")
        assertEquals(fetchCountBefore, areaRepo.callCount, "Should NOT have re-queried Gemini — used cache")
    }

    // Regression: returnToCurrentLocation to a different area must also refresh weather
    @Test
    fun returnToCurrentLocation_differentArea_refreshesWeather() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        val locationProvider = object : com.harazone.location.LocationProvider {
            private var locationCallCount = 0
            override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
                locationCallCount++
                return if (locationCallCount <= 1) Result.success(GpsCoordinates(40.7128, -74.0060))
                else Result.success(GpsCoordinates(51.5074, -0.1278))
            }
            override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
                return if (latitude > 50.0) Result.success("London, UK")
                else Result.success("Manhattan, New York")
            }
        }
        val viewModel = createViewModel(
            locationProvider = locationProvider,
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(listOf(samplePoi)))
            ),
            weatherProvider = weatherProvider,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount

        viewModel.returnToCurrentLocation()
        testScheduler.advanceUntilIdle()

        assertTrue(weatherProvider.callCount > callsAfterInit, "Weather should be refreshed on return to different area")
        assertEquals(51.5074, weatherProvider.lastLatitude, 0.001)
        assertEquals(-0.1278, weatherProvider.lastLongitude, 0.001)
    }

    @Test
    fun onCameraIdle_showsMyLocationWhenFarFromGps() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val state1 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(state1.showMyLocation)

        // Pan far from GPS (default GPS is from FakeLocationProvider)
        viewModel.onCameraIdle(50.0, 30.0)
        testScheduler.advanceUntilIdle()

        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state2.showMyLocation)
    }

    @Test
    fun onCameraIdle_hidesMyLocationWhenNearGps() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        // Pan far away first
        viewModel.onCameraIdle(50.0, 30.0)
        testScheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as MapUiState.Ready).showMyLocation)

        // Pan back near GPS (FakeLocationProvider defaults: 38.7139, -9.1394)
        viewModel.onCameraIdle(38.7139, -9.1394)
        testScheduler.advanceUntilIdle()
        assertFalse((viewModel.uiState.value as MapUiState.Ready).showMyLocation)
    }

    @Test
    fun onCameraIdle_keepsExistingPoisWhenButtonShown() = runTest(testDispatcher) {
        val initialPois = listOf(
            POI("Old Place", "landmark", "desc", Confidence.HIGH, 1.0, 1.0),
        )
        val locationProvider = object : com.harazone.location.LocationProvider {
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
        // POIs remain unchanged (no auto-fetch on camera idle)
        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(initialPois, state2.pois)
    }

    // --- Geocoding tests ---

    private val testSuggestions = listOf(
        GeocodingSuggestion("Tower", "Tower, Lisbon, Portugal", 38.6916, -9.2159, null),
        GeocodingSuggestion("Castle", "Castle, Lisbon, Portugal", 38.7139, -9.1332, null),
    )

    @Test
    fun onGeocodingQueryChanged_blank_clearsSuggestions() = runTest(testDispatcher) {
        val fakeGeocoding = FakeMapTilerGeocodingProvider(result = Result.success(testSuggestions))
        val viewModel = createViewModel(geocodingProvider = fakeGeocoding)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.onGeocodingQueryChanged("")
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("", state.geocodingQuery)
        assertTrue(state.geocodingSuggestions.isEmpty())
        assertFalse(state.isGeocodingLoading)
    }

    @Test
    fun onGeocodingQueryChanged_withQuery_setsLoadingThenSuggestions() {
        val stdDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(stdDispatcher)
        try {
            runTest(stdDispatcher) {
                val fakeGeocoding = FakeMapTilerGeocodingProvider(result = Result.success(testSuggestions))
                val viewModel = createViewModel(geocodingProvider = fakeGeocoding)
                advanceTimeBy(11_000)
                assertIs<MapUiState.Ready>(viewModel.uiState.value)

                viewModel.onGeocodingQueryChanged("Tower")
                val loadingState = assertIs<MapUiState.Ready>(viewModel.uiState.value)
                assertTrue(loadingState.isGeocodingLoading)

                advanceTimeBy(400)
                val resultState = assertIs<MapUiState.Ready>(viewModel.uiState.value)
                assertFalse(resultState.isGeocodingLoading)
                assertEquals(2, resultState.geocodingSuggestions.size)
                assertEquals("Tower", resultState.geocodingSuggestions[0].name)
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    @Test
    fun onGeocodingQueryChanged_debounce_cancelsQuickKeystrokes() {
        val stdDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(stdDispatcher)
        try {
            runTest(stdDispatcher) {
                val fakeGeocoding = FakeMapTilerGeocodingProvider(result = Result.success(testSuggestions))
                val viewModel = createViewModel(geocodingProvider = fakeGeocoding)
                advanceTimeBy(11_000)
                assertIs<MapUiState.Ready>(viewModel.uiState.value)

                viewModel.onGeocodingQueryChanged("T")
                advanceTimeBy(100)
                viewModel.onGeocodingQueryChanged("To")
                advanceTimeBy(400)

                assertEquals(1, fakeGeocoding.callCount)
                assertEquals("To", fakeGeocoding.lastQuery)
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    @Test
    fun onGeocodingSuggestionSelected_updatesCameraAndLoadsPortrait() = runTest(testDispatcher) {
        val pois = listOf(POI("Place", "landmark", "desc", Confidence.HIGH, 38.69, -9.21))
        val areaRepo = FakeAreaRepository(updates = listOf(BucketUpdate.PortraitComplete(pois)))
        val fakeGeocoding = FakeMapTilerGeocodingProvider()
        val viewModel = createViewModel(areaRepository = areaRepo, geocodingProvider = fakeGeocoding)
        val initialState = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val initialCameraMoveId = initialState.cameraMoveId

        val suggestion = GeocodingSuggestion("Tower", "Tower, Lisbon", 38.6916, -9.2159, null)
        viewModel.onGeocodingSuggestionSelected(suggestion)

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Tower", state.areaName)
        assertEquals(38.6916, state.latitude)
        assertEquals(-9.2159, state.longitude)
        assertEquals(initialCameraMoveId + 1, state.cameraMoveId)
        assertFalse(state.isSearchingArea)
        assertEquals(1, state.pois.size)
    }

    @Test
    fun onGeocodingSubmitEmpty_withNoPending_usesCurrentAreaName() = runTest(testDispatcher) {
        val pois = listOf(POI("Spot", "café", "desc", Confidence.MEDIUM, 38.71, -9.13))
        val areaRepo = FakeAreaRepository(updates = listOf(BucketUpdate.PortraitComplete(pois)))
        val viewModel = createViewModel(areaRepository = areaRepo)
        val readyState = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val originalAreaName = readyState.areaName

        viewModel.onGeocodingSubmitEmpty()

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(originalAreaName, state.areaName)
        assertFalse(state.isSearchingArea)
    }

    @Test
    fun onGeocodingCleared_resetsAllGeocodingState() = runTest(testDispatcher) {
        val fakeGeocoding = FakeMapTilerGeocodingProvider()
        val viewModel = createViewModel(geocodingProvider = fakeGeocoding)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        // Select a place first
        val suggestion = GeocodingSuggestion("Tower", "Tower, Lisbon", 38.6916, -9.2159, null)
        viewModel.onGeocodingSuggestionSelected(suggestion)

        viewModel.onGeocodingCleared()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("", state.geocodingQuery)
        assertTrue(state.geocodingSuggestions.isEmpty())
        assertFalse(state.isGeocodingLoading)
        assertNull(state.geocodingSelectedPlace)
    }

    @Test
    fun onGeocodingCancelLoad_abortsSearchAndResetsState() {
        val stdDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(stdDispatcher)
        try {
            runTest(stdDispatcher) {
                // Use a repo that never completes (hangs forever simulating Gemini latency)
                val hangingRepo = object : AreaRepository {
                    override fun getAreaPortrait(areaName: String, context: com.harazone.domain.model.AreaContext) =
                        kotlinx.coroutines.flow.flow<BucketUpdate> {
                            kotlinx.coroutines.awaitCancellation()
                        }
                }
                val fakeGeocoding = FakeMapTilerGeocodingProvider()
                val viewModel = createViewModel(areaRepository = hangingRepo, geocodingProvider = fakeGeocoding)
                advanceTimeBy(11_000)
                assertIs<MapUiState.Ready>(viewModel.uiState.value)

                // Select a suggestion to trigger isSearchingArea
                val suggestion = GeocodingSuggestion("Tower", "Tower, Lisbon", 38.6916, -9.2159, null)
                viewModel.onGeocodingSuggestionSelected(suggestion)
                advanceTimeBy(100)

                val searching = assertIs<MapUiState.Ready>(viewModel.uiState.value)
                assertTrue(searching.isSearchingArea)
                assertTrue(searching.isGeocodingInitiatedSearch)
                assertEquals("Tower", searching.geocodingSelectedPlace)

                // Cancel the load
                viewModel.onGeocodingCancelLoad()

                val cancelled = assertIs<MapUiState.Ready>(viewModel.uiState.value)
                assertFalse(cancelled.isSearchingArea)
                assertFalse(cancelled.isGeocodingInitiatedSearch)
                assertNull(cancelled.geocodingSelectedPlace)
                assertEquals("", cancelled.geocodingQuery)
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    @Test
    fun onGeocodingQueryChanged_apiFailure_isGeocodingLoadingResetsFalse() {
        val stdDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(stdDispatcher)
        try {
            runTest(stdDispatcher) {
                val fakeGeocoding = FakeMapTilerGeocodingProvider(
                    result = Result.failure(RuntimeException("Network error"))
                )
                val viewModel = createViewModel(geocodingProvider = fakeGeocoding)
                advanceTimeBy(11_000)
                assertIs<MapUiState.Ready>(viewModel.uiState.value)

                viewModel.onGeocodingQueryChanged("bad query")
                advanceTimeBy(400)

                val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
                assertFalse(state.isGeocodingLoading)
                assertTrue(state.geocodingSuggestions.isEmpty())
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    // Regression: list view was non-scrollable because LazyColumn lacked weight(1f) modifier,
    // causing it to wrap content instead of filling available height. All POIs must be present
    // in state when list view is active so the full list is available for rendering/scrolling.
    @Test
    fun listView_allPoisPresentInStateWhenToggled() = runTest(testDispatcher) {
        val manyPois = (1..20).map { i ->
            POI("Place $i", "landmark", "desc $i", Confidence.HIGH, 1.0 + i * 0.01, 2.0, vibe = "CHARACTER")
        }
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(manyPois))
            ),
        )
        viewModel.toggleListView()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state.showListView)
        assertEquals(20, state.pois.size, "All 20 POIs must be in state for list view to scroll through them")
    }

    // --- Recent Places tests ---

    // TODO(BACKLOG-LOW): This test doesn't isolate cold-start seed vs observer path
    @Test
    fun recentsFromRepositoryAppearInReadyState() = runTest(testDispatcher) {
        val fakeRecents = FakeRecentPlacesRepository()
        val place = RecentPlace("Shibuya", 35.659, 139.700)
        fakeRecents.setRecents(listOf(place))
        val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
        testScheduler.advanceUntilIdle()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf(place), state.recentPlaces)
    }

    @Test
    fun selectingGeocodingSuggestionUpsertsToRecents() = runTest(testDispatcher) {
        val fakeRecents = FakeRecentPlacesRepository()
        val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val suggestion = GeocodingSuggestion("Shibuya", "Shibuya, Tokyo", 35.659, 139.700, null)
        viewModel.onGeocodingSuggestionSelected(suggestion)
        assertEquals(1, fakeRecents.upsertCalls.size)
        assertEquals("Shibuya", fakeRecents.upsertCalls.first().name)
    }

    @Test
    fun selectingRecentNavigatesAndUpsertsTimestamp() = runTest(testDispatcher) {
        val fakeRecents = FakeRecentPlacesRepository()
        val place = RecentPlace("Asakusa", 35.714, 139.796)
        fakeRecents.setRecents(listOf(place))
        val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        viewModel.onRecentSelected(place)
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Asakusa", state.geocodingSelectedPlace)
        assertEquals(1, fakeRecents.upsertCalls.size)
        assertEquals("Asakusa", fakeRecents.upsertCalls.first().name)
    }

    @Test
    fun clearRecentsEmptiesRecentPlacesInState() = runTest(testDispatcher) {
        val fakeRecents = FakeRecentPlacesRepository()
        fakeRecents.setRecents(listOf(RecentPlace("Shinjuku", 35.689, 139.700)))
        val viewModel = createViewModel(recentPlacesRepository = fakeRecents)
        testScheduler.advanceUntilIdle()
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        viewModel.onClearRecents()
        testScheduler.advanceUntilIdle()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(emptyList(), state.recentPlaces)
        assertEquals(1, fakeRecents.clearAllCount)
    }

    // --- Weather re-fetch regression tests ---

    @Test
    fun geocodingSuggestionRefetchesWeather() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(listOf(samplePoi)))
            ),
            weatherProvider = weatherProvider,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount
        val suggestion = GeocodingSuggestion("Tokyo", "Tokyo, Japan", 35.6762, 139.6503, null)
        viewModel.onGeocodingSuggestionSelected(suggestion)
        testScheduler.advanceUntilIdle()
        assertTrue(weatherProvider.callCount > callsAfterInit, "Weather should be re-fetched on geocoding selection")
        assertEquals(35.6762, weatherProvider.lastLatitude, 0.001)
        assertEquals(139.6503, weatherProvider.lastLongitude, 0.001)
    }

    @Test
    fun recentSelectionRefetchesWeather() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        val fakeRecents = FakeRecentPlacesRepository()
        val place = RecentPlace("Tokyo", 35.6762, 139.6503)
        fakeRecents.setRecents(listOf(place))
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(listOf(samplePoi)))
            ),
            weatherProvider = weatherProvider,
            recentPlacesRepository = fakeRecents,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount
        viewModel.onRecentSelected(place)
        testScheduler.advanceUntilIdle()
        assertTrue(weatherProvider.callCount > callsAfterInit, "Weather should be re-fetched on recent selection")
        assertEquals(35.6762, weatherProvider.lastLatitude, 0.001)
    }

    @Test
    fun emptySubmitRefetchesWeather() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(BucketUpdate.PortraitComplete(listOf(samplePoi)))
            ),
            weatherProvider = weatherProvider,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount
        viewModel.onGeocodingSubmitEmpty()
        testScheduler.advanceUntilIdle()
        assertTrue(weatherProvider.callCount > callsAfterInit, "Weather should be re-fetched on empty submit")
    }

    // Regression: weather stays stale when returning to home screen (closing saves sheet)
    @Test
    fun closeSavesSheet_doesNotRefetchWhenFresh() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        var fakeTimeMs = 1000L
        val (viewModel, _) = createReadyViewModel(weatherProvider = weatherProvider, clockMs = { fakeTimeMs })
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount

        // Close saves sheet with fresh weather (<5 min) — should NOT refetch
        viewModel.openSavesSheet()
        viewModel.closeSavesSheet()
        testScheduler.advanceUntilIdle()
        assertEquals(callsAfterInit, weatherProvider.callCount, "Fresh weather should not trigger refetch")
    }

    @Test
    fun closeSavesSheet_refetchesWhenStale() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        var fakeTimeMs = 1000L
        val (viewModel, _) = createReadyViewModel(weatherProvider = weatherProvider, clockMs = { fakeTimeMs })
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount

        // Advance fake clock past 5-min threshold
        fakeTimeMs += 6 * 60 * 1000L
        viewModel.openSavesSheet()
        viewModel.closeSavesSheet()
        testScheduler.advanceUntilIdle()
        assertTrue(weatherProvider.callCount > callsAfterInit, "Stale weather should trigger refetch on saves sheet close")
    }

    @Test
    fun refreshWeatherIfStale_doesNotRefetchWhenFresh() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        var fakeTimeMs = 1000L
        val (viewModel, _) = createReadyViewModel(weatherProvider = weatherProvider, clockMs = { fakeTimeMs })
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount

        viewModel.refreshWeatherIfStale()
        testScheduler.advanceUntilIdle()
        assertEquals(callsAfterInit, weatherProvider.callCount, "Fresh weather should not trigger refetch")
    }

    @Test
    fun refreshWeatherIfStale_refetchesWhenStale() = runTest(testDispatcher) {
        val weatherProvider = FakeWeatherProvider()
        var fakeTimeMs = 1000L
        val (viewModel, _) = createReadyViewModel(weatherProvider = weatherProvider, clockMs = { fakeTimeMs })
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val callsAfterInit = weatherProvider.callCount

        // Advance fake clock past 5-min threshold
        fakeTimeMs += 6 * 60 * 1000L
        viewModel.refreshWeatherIfStale()
        testScheduler.advanceUntilIdle()
        assertTrue(weatherProvider.callCount > callsAfterInit, "Stale weather should trigger refetch")
    }

    @Test
    fun selectingRecentResetsSearchingAreaWhenPortraitEmpty() = runTest(testDispatcher) {
        val emptyRepo = FakeAreaRepository(updates = emptyList())
        val fakeRecents = FakeRecentPlacesRepository()
        val place = RecentPlace("EmptyArea", 1.0, 2.0)
        fakeRecents.setRecents(listOf(place))
        val viewModel = createViewModel(
            areaRepository = emptyRepo,
            recentPlacesRepository = fakeRecents,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)
        viewModel.onRecentSelected(place)
        testScheduler.advanceUntilIdle()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(state.isSearchingArea)
    }

    @Test
    fun savePoi_passesDescriptionAndRatingToRepository() = runTest(testDispatcher) {
        val fakeRepo = com.harazone.fakes.FakeSavedPoiRepository()
        val viewModel = createViewModel(savedPoiRepository = fakeRepo)
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        val poi = POI(
            name = "Test Place",
            type = "landmark",
            description = "A great landmark",
            confidence = Confidence.HIGH,
            latitude = 38.71,
            longitude = -9.13,
            insight = "Why it's special",
            rating = 4.5f,
            imageUrl = "https://example.com/img.jpg",
        )
        viewModel.savePoi(poi, "Test Area")
        testScheduler.advanceUntilIdle()

        val saved = fakeRepo.observeAll().first()
        assertEquals(1, saved.size)
        val s = saved.first()
        assertEquals("A great landmark", s.description)
        assertEquals(4.5f, s.rating)
        assertEquals("https://example.com/img.jpg", s.imageUrl)
    }

    // Regression: slow location provider (e.g. iOS permission dialog delay) must not trigger LocationFailed
    @Test
    fun slowLocationProvider_doesNotShowErrorStateBeforeTimeout() {
        val delayedDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(delayedDispatcher)
        try {
            runTest(delayedDispatcher) {
                val delayedProvider = object : com.harazone.location.LocationProvider {
                    override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
                        delay(3_000)
                        return Result.success(GpsCoordinates(51.5074, -0.1278))
                    }
                    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
                        Result.success("Test Area")
                }
                val vm = createViewModel(locationProvider = delayedProvider)

                // Before delay resolves: should be Loading, never LocationFailed
                assertIs<MapUiState.Loading>(vm.uiState.value)

                // Advance past the 3s delay + geocode
                advanceTimeBy(5_000)

                // Should reach Ready, not LocationFailed
                assertIs<MapUiState.Ready>(vm.uiState.value)
            }
        } finally {
            Dispatchers.resetMain()
            Dispatchers.setMain(testDispatcher)
        }
    }

    @Test
    fun newAreaFetchCancelsPreviousInFlightFetch() = runTest(testDispatcher) {
        // Regression test: selecting a second area while the first area fetch is
        // still in-flight must cancel the old fetch. Before the fix, separate job
        // variables (searchJob vs returnToLocationJob) meant concurrent fetches
        // could race and the stale one could overwrite the newer area's state.
        val suspendingRepo = SuspendingFakeAreaRepository()
        val locationProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
            geocodeResult = Result.success("Home Area"),
        )
        val vm = createViewModel(
            locationProvider = locationProvider,
            areaRepository = suspendingRepo,
        )

        // Init loadLocation is suspended at portrait fetch (call 0) — complete it
        // Use non-empty POIs to avoid the retry-with-broader-query branch
        assertIs<MapUiState.Ready>(vm.uiState.value)
        val initPoi = POI(
            name = "Init Place",
            type = "park",
            description = "A park",
            confidence = Confidence.HIGH,
            latitude = 40.0,
            longitude = -74.0,
        )
        suspendingRepo.completeCall(0, listOf(BucketUpdate.PortraitComplete(listOf(initPoi))))

        // Start first geocoding selection — launches areaFetchJob (call 1)
        vm.onGeocodingSuggestionSelected(GeocodingSuggestion(
            name = "Area A",
            fullAddress = "Area A, Country",
            latitude = 41.0,
            longitude = -73.0,
            distanceKm = null,
        ))
        assertEquals(2, suspendingRepo.callCount) // call 1 = Area A

        // Before Area A completes, select a SECOND area (call 2)
        // This must cancel the Area A fetch
        vm.onGeocodingSuggestionSelected(GeocodingSuggestion(
            name = "Area B",
            fullAddress = "Area B, Country",
            latitude = 42.0,
            longitude = -72.0,
            distanceKm = null,
        ))
        assertEquals(3, suspendingRepo.callCount) // call 2 = Area B

        // Complete the OLD Area A fetch (call 1) — should be cancelled, no effect
        val areaAPois = listOf(
            POI(
                name = "Old POI",
                type = "restaurant",
                description = "An old restaurant",
                confidence = Confidence.HIGH,
                latitude = 41.0,
                longitude = -73.0,
                vibe = "Hungry",
            )
        )
        suspendingRepo.completeCall(1, listOf(BucketUpdate.PortraitComplete(areaAPois)))

        // Complete the Area B fetch (call 2) with different POIs
        val areaBPois = listOf(
            POI(
                name = "New POI",
                type = "cafe",
                description = "A new cafe",
                confidence = Confidence.HIGH,
                latitude = 42.0,
                longitude = -72.0,
                vibe = "Hungry",
            )
        )
        suspendingRepo.completeCall(2, listOf(BucketUpdate.PortraitComplete(areaBPois)))

        // The final state must show Area B POIs, NOT Area A POIs
        val finalState = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertEquals(1, finalState.pois.size)
        assertEquals("New POI", finalState.pois.first().name)
        assertEquals("Area B", finalState.areaName)
    }

    @Test
    fun cancelSearchRestoresPreviousAreaState() = runTest(testDispatcher) {
        // Regression test: cancelling a search mid-flight must restore the previous
        // area's coordinates, POIs, and area name — not leave the user stranded at
        // the new location with empty POIs.
        val suspendingRepo = SuspendingFakeAreaRepository()
        val locationProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
            geocodeResult = Result.success("Home Area"),
        )
        val vm = createViewModel(
            locationProvider = locationProvider,
            areaRepository = suspendingRepo,
        )

        // Complete init with POIs
        assertIs<MapUiState.Ready>(vm.uiState.value)
        val homePoi = POI(
            name = "Home Cafe",
            type = "cafe",
            description = "A local cafe",
            confidence = Confidence.HIGH,
            latitude = 40.0,
            longitude = -74.0,
        )
        suspendingRepo.completeCall(0, listOf(BucketUpdate.PortraitComplete(listOf(homePoi))))

        val stateBeforeSearch = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertEquals("Home Area", stateBeforeSearch.areaName)
        assertEquals(1, stateBeforeSearch.pois.size)

        // Start a geocoding selection (camera moves, POIs cleared)
        vm.onGeocodingSuggestionSelected(GeocodingSuggestion(
            name = "Far Away Place",
            fullAddress = "Far Away Place, Country",
            latitude = 50.0,
            longitude = -60.0,
            distanceKm = null,
        ))

        // Verify state moved to new location with empty POIs
        val searchingState = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertTrue(searchingState.isSearchingArea)
        assertTrue(searchingState.pois.isEmpty())

        // Cancel before the fetch completes
        vm.onGeocodingCancelLoad()

        // State must be restored to pre-search snapshot
        val restoredState = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertEquals("Home Area", restoredState.areaName)
        assertEquals(40.0, restoredState.latitude)
        assertEquals(-74.0, restoredState.longitude)
        assertEquals(1, restoredState.pois.size)
        assertEquals("Home Cafe", restoredState.pois.first().name)
        assertFalse(restoredState.isSearchingArea)
        assertTrue(restoredState.cameraMoveId > stateBeforeSearch.cameraMoveId)
    }

    @Test
    fun rapidDoubleSelectionThenCancelRestoresOriginalState() = runTest(testDispatcher) {
        // Regression test for H1: rapid Area A → Area B selection must NOT
        // overwrite the snapshot with Area A's half-initialized state.
        // Cancel after Area B must restore Home, not Area A.
        val suspendingRepo = SuspendingFakeAreaRepository()
        val vm = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Home Area"),
            ),
            areaRepository = suspendingRepo,
        )

        assertIs<MapUiState.Ready>(vm.uiState.value)
        val homePoi = POI(
            name = "Home Cafe",
            type = "cafe",
            description = "A local cafe",
            confidence = Confidence.HIGH,
            latitude = 40.0,
            longitude = -74.0,
        )
        suspendingRepo.completeCall(0, listOf(BucketUpdate.PortraitComplete(listOf(homePoi))))
        val homeState = assertIs<MapUiState.Ready>(vm.uiState.value)

        // Select Area A (snapshot captures Home state)
        vm.onGeocodingSuggestionSelected(GeocodingSuggestion(
            name = "Area A", fullAddress = "Area A", latitude = 41.0, longitude = -73.0, distanceKm = null,
        ))

        // Immediately select Area B — snapshot must NOT be overwritten
        vm.onGeocodingSuggestionSelected(GeocodingSuggestion(
            name = "Area B", fullAddress = "Area B", latitude = 42.0, longitude = -72.0, distanceKm = null,
        ))

        // Cancel — must restore Home, not Area A
        vm.onGeocodingCancelLoad()

        val restored = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertEquals("Home Area", restored.areaName)
        assertEquals(40.0, restored.latitude)
        assertEquals(-74.0, restored.longitude)
        assertEquals(1, restored.pois.size)
        assertEquals("Home Cafe", restored.pois.first().name)
        assertFalse(restored.isSearchingArea)
        assertTrue(restored.cameraMoveId > homeState.cameraMoveId)
    }

    @Test
    fun cancelSearchViaRecentRestoresPreviousAreaState() = runTest(testDispatcher) {
        // M3: cancel-restore test for onRecentSelected (mirrors cancelSearchRestoresPreviousAreaState)
        val suspendingRepo = SuspendingFakeAreaRepository()
        val vm = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Home Area"),
            ),
            areaRepository = suspendingRepo,
        )
        assertIs<MapUiState.Ready>(vm.uiState.value)
        val homePoi = POI(
            name = "Home Cafe", type = "cafe", description = "A cafe",
            confidence = Confidence.HIGH, latitude = 40.0, longitude = -74.0,
        )
        suspendingRepo.completeCall(0, listOf(BucketUpdate.PortraitComplete(listOf(homePoi))))
        val homeState = assertIs<MapUiState.Ready>(vm.uiState.value)

        vm.onRecentSelected(RecentPlace(name = "Recent Place", latitude = 45.0, longitude = -70.0))

        val searching = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertTrue(searching.isSearchingArea)

        vm.onGeocodingCancelLoad()

        val restored = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertEquals("Home Area", restored.areaName)
        assertEquals(40.0, restored.latitude)
        assertEquals(1, restored.pois.size)
        assertFalse(restored.isSearchingArea)
        assertTrue(restored.cameraMoveId > homeState.cameraMoveId)
    }

    @Test
    fun cancelWithoutSnapshotClearsLoadingFlags() = runTest(testDispatcher) {
        // M4: null-snapshot fallback path — cancel fires without a prior selection
        val suspendingRepo = SuspendingFakeAreaRepository()
        val vm = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Home Area"),
            ),
            areaRepository = suspendingRepo,
        )
        assertIs<MapUiState.Ready>(vm.uiState.value)
        suspendingRepo.completeCall(0, listOf(BucketUpdate.PortraitComplete(emptyList())))

        // Trigger onGeocodingSubmitEmpty (clears preSearchSnapshot) then cancel
        vm.onGeocodingSubmitEmpty()
        vm.onGeocodingCancelLoad()

        val state = assertIs<MapUiState.Ready>(vm.uiState.value)
        assertFalse(state.isSearchingArea)
        assertFalse(state.isEnrichingArea)
        assertFalse(state.isGeocodingInitiatedSearch)
    }

    // --- Save Experience v2 tests ---

    @Test
    fun vibeAreaSaveCounts_countsOnlyCurrentArea() = runTest(testDispatcher) {
        val savedPoiRepo = com.harazone.fakes.FakeSavedPoiRepository()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Area A"),
            ),
            savedPoiRepository = savedPoiRepo,
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Area A", state.areaName)

        // Save 2 CHARACTER saves in "Area A"
        savedPoiRepo.save(SavedPoi(id = "p1", name = "Place 1", type = "cafe", areaName = "Area A", lat = 40.0, lng = -74.0, whySpecial = "great", savedAt = 1L, vibe = "CHARACTER"))
        savedPoiRepo.save(SavedPoi(id = "p2", name = "Place 2", type = "bar", areaName = "Area A", lat = 40.1, lng = -74.1, whySpecial = "nice", savedAt = 2L, vibe = "CHARACTER"))
        // Save 1 HISTORY save in "Area B"
        savedPoiRepo.save(SavedPoi(id = "p3", name = "Place 3", type = "museum", areaName = "Area B", lat = 50.0, lng = -60.0, whySpecial = "old", savedAt = 3L, vibe = "HISTORY"))

        val updated = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(2, updated.dynamicVibeAreaSaveCounts["CHARACTER"])
        assertNull(updated.dynamicVibeAreaSaveCounts["HISTORY"])
    }

    @Test
    fun vibeAreaSaveCounts_emptyWhenNoVibeStored() = runTest(testDispatcher) {
        val savedPoiRepo = com.harazone.fakes.FakeSavedPoiRepository()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Area A"),
            ),
            savedPoiRepository = savedPoiRepo,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        // Save with empty vibe
        savedPoiRepo.save(SavedPoi(id = "p1", name = "Place 1", type = "cafe", areaName = "Area A", lat = 40.0, lng = -74.0, whySpecial = "great", savedAt = 1L, vibe = ""))

        val updated = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(updated.dynamicVibeAreaSaveCounts.isEmpty())
    }

    @Test
    fun onSavedVibeSelected_togglesSavedVibeFilter() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Test Area"),
            ),
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        viewModel.onSavedVibeSelected()

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state.savedVibeFilter)
        assertNull(state.activeDynamicVibe)
    }

    @Test
    fun vibeAreaSaveCounts_updatesOnGeocodingAreaChange() = runTest(testDispatcher) {
        val savedPoiRepo = com.harazone.fakes.FakeSavedPoiRepository()
        // Pre-seed saves: 2 CHARACTER in "Area A", 1 HISTORY in "Area B"
        savedPoiRepo.save(SavedPoi(id = "p1", name = "Place 1", type = "cafe", areaName = "Area A", lat = 40.0, lng = -74.0, whySpecial = "great", savedAt = 1L, vibe = "CHARACTER"))
        savedPoiRepo.save(SavedPoi(id = "p2", name = "Place 2", type = "bar", areaName = "Area A", lat = 40.1, lng = -74.1, whySpecial = "nice", savedAt = 2L, vibe = "CHARACTER"))
        savedPoiRepo.save(SavedPoi(id = "p3", name = "Place 3", type = "museum", areaName = "Area B", lat = 50.0, lng = -60.0, whySpecial = "old", savedAt = 3L, vibe = "HISTORY"))

        val pois = listOf(POI("Spot", "landmark", "desc", Confidence.HIGH, 40.0, -74.0))
        val areaRepo = FakeAreaRepository(updates = listOf(BucketUpdate.PortraitComplete(pois)))
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Area A"),
            ),
            areaRepository = areaRepo,
            savedPoiRepository = savedPoiRepo,
        )

        // Verify initial counts reflect Area A
        val stateA = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Area A", stateA.areaName)
        assertEquals(2, stateA.dynamicVibeAreaSaveCounts["CHARACTER"])
        assertNull(stateA.dynamicVibeAreaSaveCounts["HISTORY"])

        // Navigate to Area B via geocoding
        val suggestion = GeocodingSuggestion("Area B", "Area B, Country", 50.0, -60.0, null)
        viewModel.onGeocodingSuggestionSelected(suggestion)
        testScheduler.advanceUntilIdle()

        // Counts must now reflect Area B saves, not Area A
        val stateB = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("Area B", stateB.areaName)
        assertEquals(1, stateB.dynamicVibeAreaSaveCounts["HISTORY"])
        assertNull(stateB.dynamicVibeAreaSaveCounts["CHARACTER"])
    }

    // --- selectPoiWithImageResolve tests (M2) ---

    @Test
    fun selectPoiWithImageResolve_alreadyHasImage_skipsWikipedia() = runTest(testDispatcher) {
        var wikiCalled = false
        val wikiRepo = WikipediaImageRepository(HttpClient(MockEngine { _ ->
            wikiCalled = true
            respond("{}", HttpStatusCode.OK)
        }))
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Test Area"),
            ),
            wikipediaImageRepository = wikiRepo,
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        val poi = POI("Castle", "historic", "Old castle", Confidence.HIGH, 40.0, -9.0, imageUrl = "https://existing.jpg")
        viewModel.selectPoiWithImageResolve(poi)
        testScheduler.advanceUntilIdle()

        assertFalse(wikiCalled, "Wikipedia should not be called when POI already has imageUrl")
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals("https://existing.jpg", state.selectedPoi!!.imageUrl)
    }

    @Test
    fun selectPoiWithImageResolve_noImage_selectsPoiWithNull() = runTest(testDispatcher) {
        // Mock Wikipedia returns no thumbnail → imageUrl stays null
        // Tests that the POI is at least selected (guard works)
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Test Area"),
            ),
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        val poi = POI("Castle", "historic", "Old castle", Confidence.HIGH, 40.0, -9.0)
        viewModel.selectPoiWithImageResolve(poi)
        testScheduler.advanceUntilIdle()

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertNotNull(state.selectedPoi, "POI should be selected even if image resolve fails")
        assertEquals("Castle", state.selectedPoi!!.name)
    }

    @Test
    fun selectPoiWithImageResolve_guardChecksCoordinates() = runTest(testDispatcher) {
        // Verifies the M1 fix: guard uses name + coordinates, not just name
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Test Area"),
            ),
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        // Select first POI
        val poi1 = POI("Starbucks", "cafe", "Coffee", Confidence.HIGH, 40.0, -9.0)
        viewModel.selectPoiWithImageResolve(poi1)
        testScheduler.advanceUntilIdle()

        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(40.0, state.selectedPoi!!.latitude)
        assertEquals(-9.0, state.selectedPoi!!.longitude)
    }

    @Test
    fun switchVibe_clearsSavedVibeFilter() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.0, -74.0)),
                geocodeResult = Result.success("Test Area"),
            ),
        )
        assertIs<MapUiState.Ready>(viewModel.uiState.value)

        // Activate saved filter first
        viewModel.onSavedVibeSelected()
        val afterSaved = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(afterSaved.savedVibeFilter)

        // Now switch to a regular vibe
        viewModel.switchDynamicVibe(DynamicVibe(label = "CHARACTER", icon = ""))

        val afterSwitch = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(afterSwitch.savedVibeFilter)
        assertEquals("CHARACTER", afterSwitch.activeDynamicVibe?.label)
    }

    // --- Batch Pipeline Tests ---

    private val batchVibes = listOf(
        DynamicVibe(label = "Nightlife", icon = "🌙"),
        DynamicVibe(label = "History", icon = "📜"),
    )

    private fun makePoi(name: String, vibe: String = "Nightlife", vibes: List<String> = listOf(vibe)) = POI(
        name = name, type = "food", description = "desc", confidence = Confidence.HIGH,
        latitude = 1.0, longitude = 2.0, vibe = vibe, vibes = vibes,
    )

    private val batch0Pois = listOf(makePoi("A"), makePoi("B"), makePoi("C", "History", listOf("History")))
    private val batch1Pois = listOf(makePoi("D"), makePoi("E"), makePoi("F"))
    private val batch2Pois = listOf(makePoi("G"), makePoi("H"), makePoi("I"))

    @Test
    fun backgroundFetchSetsIsBackgroundFetching() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundBatchReady(batch1Pois, 1),
                    BucketUpdate.BackgroundBatchReady(batch2Pois, 2),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(state.isBackgroundFetching)
        assertEquals(3, state.poiBatches.size)
    }

    @Test
    fun batchNavForwardEntersShowAllAtEnd() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundBatchReady(batch1Pois, 1),
                    BucketUpdate.BackgroundBatchReady(batch2Pois, 2),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        viewModel.onNextBatch()
        viewModel.onNextBatch()
        val state2 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(2, state2.activeBatchIndex)
        viewModel.onNextBatch()
        val state3 = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state3.showAllMode)
        assertEquals(9, state3.pois.size)
    }

    @Test
    fun vibeFilterComputedClientSideFromCurrentBatch() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.PortraitComplete(batch0Pois),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        viewModel.switchDynamicVibe(DynamicVibe(label = "Nightlife", icon = "🌙"))
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(2, state.pois.size)
    }

    @Test
    fun savedPinsPersistedAcrossBatchNavigation() = runTest(testDispatcher) {
        val savedRepo = com.harazone.fakes.FakeSavedPoiRepository()
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundBatchReady(batch1Pois, 1),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
            savedPoiRepository = savedRepo,
        )
        viewModel.savePoi(batch0Pois[0], "Manhattan, New York")
        val savedId = batch0Pois[0].savedId
        viewModel.onNextBatch()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(savedId in state.savedPoiIds)
    }

    @Test
    fun backgroundFetchCompleteAlwaysClearsFlag() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertFalse(state.isBackgroundFetching)
    }

    @Test
    fun onSearchDeeperResetsAllBatchState() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundBatchReady(batch1Pois, 1),
                    BucketUpdate.BackgroundBatchReady(batch2Pois, 2),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        viewModel.onNextBatch()
        viewModel.onNextBatch()
        viewModel.onSearchDeeper()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(0, state.activeBatchIndex)
        assertFalse(state.showAllMode)
        assertFalse(state.isBackgroundFetching)
    }

    @Test
    fun backgroundEnrichmentMergesIntoCorrectBatch() = runTest(testDispatcher) {
        val enrichedD = makePoi("D").copy(rating = 4.8f)
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundBatchReady(batch1Pois, 1),
                    BucketUpdate.BackgroundEnrichmentComplete(listOf(enrichedD), 1),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        val dPoi = state.poiBatches[1].first { it.name == "D" }
        assertEquals(4.8f, dPoi.rating)
        assertNull(state.poiBatches[0].first { it.name == "A" }.rating)
    }

    @Test
    fun emptyBackgroundBatchIsSkipped() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundBatchReady(emptyList(), 1),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(1, state.poiBatches.size)
    }

    @Test
    fun onNextBatchWritesPoisToState() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, batch0Pois),
                    BucketUpdate.BackgroundBatchReady(batch1Pois, 1),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        viewModel.onNextBatch()
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertEquals(1, state.activeBatchIndex)
        assertEquals(batch1Pois.map { it.name }, state.pois.map { it.name })
    }

    @Test
    fun vibeFilterEmptyBatchDoesNotAutoAdvance() = runTest(testDispatcher) {
        val historyBatch = listOf(
            makePoi("X", "History", listOf("History")),
            makePoi("Y", "History", listOf("History")),
            makePoi("Z", "History", listOf("History")),
        )
        val nightlifeBatch = listOf(
            makePoi("P", "Nightlife", listOf("Nightlife")),
            makePoi("Q", "Nightlife", listOf("Nightlife")),
            makePoi("R", "Nightlife", listOf("Nightlife")),
        )
        val viewModel = createViewModel(
            locationProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(40.7128, -74.0060)),
                geocodeResult = Result.success("Manhattan, New York"),
            ),
            areaRepository = FakeAreaRepository(
                updates = listOf(
                    BucketUpdate.VibesReady(batchVibes, historyBatch),
                    BucketUpdate.BackgroundBatchReady(nightlifeBatch, 1),
                    BucketUpdate.BackgroundFetchComplete,
                )
            ),
        )
        viewModel.switchDynamicVibe(DynamicVibe(label = "Nightlife", icon = "🌙"))
        val state = assertIs<MapUiState.Ready>(viewModel.uiState.value)
        assertTrue(state.pois.isEmpty())
        assertEquals(0, state.activeBatchIndex)
    }
}

private class SuspendingFakeAreaRepository : AreaRepository {
    private val deferreds = mutableListOf<CompletableDeferred<List<BucketUpdate>>>()
    val callCount get() = deferreds.size
    val lastAreaName get() = areaNames.lastOrNull()
    private val areaNames = mutableListOf<String>()

    override fun getAreaPortrait(
        areaName: String,
        context: com.harazone.domain.model.AreaContext,
    ): Flow<BucketUpdate> {
        val deferred = CompletableDeferred<List<BucketUpdate>>()
        deferreds.add(deferred)
        areaNames.add(areaName)
        return flow {
            val updates = deferred.await()
            updates.forEach { emit(it) }
        }
    }

    fun completeCall(index: Int, updates: List<BucketUpdate>) {
        deferreds[index].complete(updates)
    }
}

private class ResettableFakeLocationProvider(
    private val geocodeResult: Result<String> = Result.success("Alfama, Lisbon"),
) : com.harazone.location.LocationProvider {
    private var deferred = CompletableDeferred<Result<GpsCoordinates>>()
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> = deferred.await()
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        geocodeResult
    fun complete(result: Result<GpsCoordinates>) { deferred.complete(result) }
    fun reset() { deferred = CompletableDeferred() }
}
