package com.harazone.ui.map

import com.harazone.data.remote.WikipediaImageRepository
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.GhostPin
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.usecase.GetAreaPortraitUseCase
import com.harazone.fakes.FakeAnalyticsTracker
import com.harazone.fakes.FakeAreaContextFactory
import com.harazone.fakes.FakeAreaRepository
import com.harazone.fakes.FakeLocaleProvider
import com.harazone.fakes.FakeLocationProvider
import com.harazone.fakes.FakeMapTilerGeocodingProvider
import com.harazone.fakes.FakeRecentPlacesRepository
import com.harazone.fakes.FakeWeatherProvider
import com.harazone.location.GpsCoordinates
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val stubWikiRepo by lazy {
    WikipediaImageRepository(HttpClient(MockEngine { _ -> respond("{}", HttpStatusCode.OK) }))
}

@OptIn(ExperimentalCoroutinesApi::class)
class SavedLensTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun createViewModel(
        savedPoiRepository: com.harazone.domain.repository.SavedPoiRepository = com.harazone.fakes.FakeSavedPoiRepository(),
    ) = MapViewModel(
        locationProvider = FakeLocationProvider(
            locationResult = Result.success(GpsCoordinates(25.0, 55.0)),
            geocodeResult = Result.success("Dubai Marina"),
        ),
        getAreaPortrait = GetAreaPortraitUseCase(FakeAreaRepository()),
        areaContextFactory = FakeAreaContextFactory(),
        analyticsTracker = FakeAnalyticsTracker(),
        weatherProvider = FakeWeatherProvider(),
        geocodingProvider = FakeMapTilerGeocodingProvider(),
        recentPlacesRepository = FakeRecentPlacesRepository(),
        savedPoiRepository = savedPoiRepository,
        wikipediaImageRepository = stubWikiRepo,
        userPreferencesRepository = com.harazone.fakes.FakeUserPreferencesRepository(),
        companionEngine = com.harazone.domain.companion.CompanionNudgeEngine(
            com.harazone.fakes.FakeUserPreferencesRepository(),
            com.harazone.fakes.FakeAreaIntelligenceProvider(),
            com.harazone.fakes.FakeClock(),
        ),
        localeProvider = FakeLocaleProvider(),
        advisoryProvider = object : com.harazone.domain.provider.AdvisoryProvider {
            override suspend fun getAdvisory(countryCode: String, regionName: String?) =
                Result.success(com.harazone.domain.model.AreaAdvisory(
                    level = com.harazone.domain.model.AdvisoryLevel.SAFE,
                    countryName = "", countryCode = countryCode,
                    summary = "", details = emptyList(),
                    subNationalZones = emptyList(), sourceUrl = "",
                    lastUpdated = 0L, cachedAt = 0L,
                ))
        },
        aiProvider = com.harazone.fakes.FakeAreaIntelligenceProvider(),
        clockMs = { 1000L },
    )

    private fun makePoi(name: String, vibe: String, lat: Double, lng: Double) = POI(
        name = name,
        type = "restaurant",
        description = "A place",
        confidence = Confidence.HIGH,
        latitude = lat,
        longitude = lng,
        vibe = vibe,
        insight = "Great spot",
    )

    private fun readyState(vm: MapViewModel): MapUiState.Ready {
        return assertIs<MapUiState.Ready>(vm.uiState.value)
    }

    // --- Saved Lens Toggle ---

    @Test
    fun savedLensTapTogglesState() = runTest(testDispatcher) {
        val vm = createViewModel()
        val initial = readyState(vm)
        assertFalse(initial.savedLensActive)

        vm.onSavedLensTap()
        assertTrue(readyState(vm).savedLensActive)

        vm.onSavedLensTap()
        assertFalse(readyState(vm).savedLensActive)
    }

    @Test
    fun exitSavedLensClearsActive() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onSavedLensTap()
        assertTrue(readyState(vm).savedLensActive)

        vm.onExitSavedLens()
        assertFalse(readyState(vm).savedLensActive)
    }

    @Test
    fun exitSavedLensNoOpWhenNotActive() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertFalse(readyState(vm).savedLensActive)
        vm.onExitSavedLens() // should not crash
        assertFalse(readyState(vm).savedLensActive)
    }

    // --- Ghost Pin Generation ---

    @Test
    fun visitPoiGeneratesGhostPinsWithMatchingVibe() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = readyState(vm)

        // Seed allDiscoveredPois with several POIs of varying vibes
        val poi1 = makePoi("Restaurant A", "food", 25.001, 55.001)
        val poi2 = makePoi("Restaurant B", "food", 25.002, 55.002) // same vibe
        val poi3 = makePoi("Cafe C", "food", 25.003, 55.003) // same vibe
        val poi4 = makePoi("Museum D", "culture", 25.004, 55.004) // different vibe
        val poi5 = makePoi("Park E", "nature", 25.005, 55.005) // different vibe

        // Manually set allDiscoveredPois
        val withPois = state.copy(allDiscoveredPois = listOf(poi1, poi2, poi3, poi4, poi5))
        val field = MapViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<MapUiState>
        flow.value = withPois

        // Visit poi1 — should generate ghost pins from poi2, poi3 (same vibe), not poi4/poi5
        vm.visitPoi(poi1, "Dubai Marina")

        val result = readyState(vm)
        assertEquals(2, result.ghostPins.size) // exactly 2 matching food POIs
        // All ghost pins should have "food" vibe
        result.ghostPins.forEach { ghost ->
            assertEquals("food", ghost.poi.vibe.split(",").first().trim().lowercase())
        }
        // No ghost pin should be the saved POI itself
        result.ghostPins.forEach { ghost ->
            assertTrue(ghost.poi.savedId != poi1.savedId)
        }
    }

    @Test
    fun ghostPinsMaxThree() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = readyState(vm)

        val pois = (0..10).map { i ->
            makePoi("Place $i", "food", 25.0 + i * 0.001, 55.0 + i * 0.001)
        }
        val withPois = state.copy(allDiscoveredPois = pois)
        val field = MapViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<MapUiState>
        flow.value = withPois

        vm.visitPoi(pois[0], "Dubai Marina")
        assertTrue(readyState(vm).ghostPins.size <= 3)
    }

    @Test
    fun ghostPinsExcludeAlreadySaved() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = readyState(vm)

        val poi1 = makePoi("A", "food", 25.001, 55.001)
        val poi2 = makePoi("B", "food", 25.002, 55.002)
        val poi3 = makePoi("C", "food", 25.003, 55.003)
        val poi4 = makePoi("D", "food", 25.004, 55.004)

        // Pre-mark poi2 as saved — 3 unsaved food POIs remain (poi1 saved by visitPoi, poi3+poi4 as candidates)
        val withPois = state.copy(
            allDiscoveredPois = listOf(poi1, poi2, poi3, poi4),
            visitedPoiIds = setOf(poi2.savedId),
        )
        val field = MapViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<MapUiState>
        flow.value = withPois

        vm.visitPoi(poi1, "Dubai Marina")
        val ghosts = readyState(vm).ghostPins
        // poi2 should not appear as a ghost pin since it's already saved
        assertFalse(ghosts.any { it.poi.savedId == poi2.savedId })
    }

    // --- Ghost Pin Conversion ---

    @Test
    fun saveGhostPinRemovesFromGhostPinsAndSaves() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = readyState(vm)

        val poi1 = makePoi("A", "food", 25.001, 55.001)
        val ghostPoi = makePoi("B", "food", 25.002, 55.002)
        val ghost = GhostPin(poi = ghostPoi, sourcePoiSavedId = poi1.savedId)

        val withGhosts = state.copy(
            allDiscoveredPois = listOf(poi1, ghostPoi),
            ghostPins = listOf(ghost),
        )
        val field = MapViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<MapUiState>
        flow.value = withGhosts

        vm.saveGhostPin(ghost)
        val result = readyState(vm)
        // Ghost should be removed
        assertFalse(result.ghostPins.any { it.poi.savedId == ghostPoi.savedId })
        // POI should now be saved
        assertTrue(ghostPoi.savedId in result.visitedPoiIds)
    }

    // --- Pan Detection Disabled ---

    @Test
    fun cameraIdleNoOpWhenSavedLensActive() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onSavedLensTap()
        assertTrue(readyState(vm).savedLensActive)

        // Move camera — showSearchAreaPill should NOT appear
        vm.onCameraIdle(26.0, 56.0) // well beyond threshold
        assertFalse(readyState(vm).showSearchAreaPill)
    }

    // --- Ghost Pins Cleared on Search ---

    @Test
    fun ghostPinsClearedOnSearchThisArea() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = readyState(vm)

        val ghostPoi = makePoi("B", "food", 25.002, 55.002)
        val ghost = GhostPin(poi = ghostPoi, sourcePoiSavedId = "source|25.001|55.001")
        val withGhosts = state.copy(ghostPins = listOf(ghost))
        val field = MapViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<MapUiState>
        flow.value = withGhosts

        vm.onSearchThisArea()
        assertTrue(readyState(vm).ghostPins.isEmpty())
    }

    // --- Min 2 Ghost Pins ---

    @Test
    fun ghostPinsEmptyWhenFewerThanTwoCandidates() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = readyState(vm)

        // Only 2 food POIs total — saving one leaves 1 candidate, below min of 2
        val poi1 = makePoi("A", "food", 25.001, 55.001)
        val poi2 = makePoi("B", "food", 25.002, 55.002)
        val withPois = state.copy(allDiscoveredPois = listOf(poi1, poi2))
        val field = MapViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<MapUiState>
        flow.value = withPois

        vm.visitPoi(poi1, "Dubai Marina")
        assertTrue(readyState(vm).ghostPins.isEmpty())
    }

    // --- Saved Lens drives visitedFilter ---

    @Test
    fun savedLensTapSetsVisitedFilter() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertFalse(readyState(vm).visitedFilter)

        vm.onSavedLensTap()
        val entering = readyState(vm)
        assertTrue(entering.savedLensActive)
        assertTrue(entering.visitedFilter)

        vm.onExitSavedLens()
        val exited = readyState(vm)
        assertFalse(exited.savedLensActive)
        assertFalse(exited.visitedFilter)
    }

    // --- Saved List Sort Order (AC34) ---

    @Test
    fun savedPlacesSortedByMostRecentAreaThenAlpha() {
        // Test the sort order specified by AC34 via production buildCapsules
        val saves = listOf(
            SavedPoi(id = "1", name = "Cafe A", type = "cafe", areaName = "Dubai Marina",
                lat = 25.0, lng = 55.0, whySpecial = "", savedAt = 1000L),
            SavedPoi(id = "2", name = "Museum B", type = "museum", areaName = "Al Quoz",
                lat = 25.1, lng = 55.1, whySpecial = "", savedAt = 2000L),
            SavedPoi(id = "3", name = "Park C", type = "park", areaName = "Dubai Marina",
                lat = 25.2, lng = 55.2, whySpecial = "", savedAt = 3000L),
            SavedPoi(id = "4", name = "Bar D", type = "bar", areaName = "Bur Dubai",
                lat = 25.3, lng = 55.3, whySpecial = "", savedAt = 500L),
        )

        val capsules = com.harazone.ui.saved.SavedPlacesViewModel.buildCapsules(
            saves, userLat = null, userLng = null, sortByRecent = true,
        )
        // First capsule is "All", then areas sorted by most recent save
        val areaLabels = capsules.drop(1).map { it.label }
        // Dubai Marina has most recent save (3000L), then Al Quoz (2000L), then Bur Dubai (500L)
        assertEquals(listOf("Dubai Marina", "Al Quoz", "Bur Dubai"), areaLabels)
    }

    // --- Ghost pin chain suppression ---

    @Test
    fun saveGhostPinDoesNotChainNewGhosts() = runTest(testDispatcher) {
        val vm = createViewModel()
        val state = readyState(vm)

        val poi1 = makePoi("A", "food", 25.001, 55.001)
        val ghostPoi = makePoi("B", "food", 25.002, 55.002)
        val otherPoi = makePoi("C", "food", 25.003, 55.003)
        val ghost = GhostPin(poi = ghostPoi, sourcePoiSavedId = poi1.savedId)

        val withGhosts = state.copy(
            allDiscoveredPois = listOf(poi1, ghostPoi, otherPoi),
            ghostPins = listOf(ghost),
        )
        val field = MapViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<MapUiState>
        flow.value = withGhosts

        vm.saveGhostPin(ghost)
        val result = readyState(vm)
        // Ghost pin should be removed, no new ghosts generated from the chain
        assertTrue(result.ghostPins.isEmpty())
    }
}
