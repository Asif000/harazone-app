package com.areadiscovery.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.domain.provider.WeatherProvider
import com.areadiscovery.domain.service.AreaContextFactory
import com.areadiscovery.domain.usecase.GetAreaPortraitUseCase
import com.areadiscovery.location.LocationProvider
import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MapViewModel(
    private val locationProvider: LocationProvider,
    private val getAreaPortrait: GetAreaPortraitUseCase,
    private val areaContextFactory: AreaContextFactory,
    private val analyticsTracker: AnalyticsTracker,
    private val weatherProvider: WeatherProvider,
    private val aiProvider: AreaIntelligenceProvider? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var cameraIdleJob: Job? = null
    private var returnToLocationJob: Job? = null
    private var pendingLat: Double = 0.0
    private var pendingLng: Double = 0.0
    private var pendingAreaName: String = ""

    init {
        loadLocation()
    }

    fun selectPoi(poi: POI?) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(selectedPoi = poi)
        if (poi != null) {
            analyticsTracker.trackEvent(
                "poi_tapped",
                mapOf(
                    "area_name" to current.areaName,
                    "poi_name" to poi.name,
                    "poi_type" to poi.type,
                )
            )
        }
    }

    fun clearPoiSelection() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(selectedPoi = null)
    }

    fun toggleListView() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(showListView = !current.showListView)
    }

    fun switchVibe(vibe: Vibe) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val newVibe = if (current.activeVibe == vibe) null else vibe
        _uiState.value = current.copy(activeVibe = newVibe)
        analyticsTracker.trackEvent("vibe_switched", mapOf("vibe" to (newVibe?.name ?: "all")))
    }

    fun openSearchOverlay() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(
            isSearchOverlayOpen = true,
            showMyLocation = false,
            searchQuery = "",
            aiResponse = "",
            followUpChips = emptyList(),
            isAiResponding = false,
        )
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(searchQuery = query)
    }

    fun closeSearchOverlay() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(
            isSearchOverlayOpen = false,
            showMyLocation = isAwayFromGps(current.latitude, current.longitude, current),
        )
        searchJob?.cancel()
    }

    fun submitSearch(query: String) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        searchJob?.cancel()

        if (isQuestion(query)) {
            _uiState.value = current.copy(
                searchQuery = query,
                aiResponse = "",
                isAiResponding = true,
                followUpChips = emptyList(),
            )
            analyticsTracker.trackEvent("search_question_submitted", mapOf("query" to query))

            searchJob = viewModelScope.launch {
                try {
                    aiProvider?.streamChatResponse(query, current.areaName, emptyList())
                        ?.catch { e ->
                            AppLogger.e(e) { "AI search failed" }
                            val s = _uiState.value as? MapUiState.Ready ?: return@catch
                            _uiState.value = s.copy(
                                aiResponse = "Something went wrong. Please try again.",
                                isAiResponding = false,
                            )
                        }
                        ?.collect { token ->
                            val state = _uiState.value as? MapUiState.Ready ?: return@collect
                            if (token.isComplete) {
                                _uiState.value = state.copy(
                                    isAiResponding = false,
                                    followUpChips = computeFollowUpChips(query),
                                )
                            } else {
                                _uiState.value = state.copy(
                                    aiResponse = state.aiResponse + token.text,
                                )
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(e) { "AI search error" }
                    val state = _uiState.value as? MapUiState.Ready ?: return@launch
                    _uiState.value = state.copy(
                        aiResponse = "Something went wrong. Please try again.",
                        isAiResponding = false,
                    )
                }
            }
        } else {
            _uiState.value = current.copy(
                searchQuery = query,
                isAiResponding = true,
            )
            analyticsTracker.trackEvent("search_area_submitted", mapOf("query" to query))

            searchJob = viewModelScope.launch {
                try {
                    val context = areaContextFactory.create()
                    getAreaPortrait(query, context)
                        .catch { e -> AppLogger.e(e) { "Area search failed" } }
                        .collect { update ->
                            if (update is BucketUpdate.PortraitComplete) {
                                val pois = update.pois
                                val state = _uiState.value as? MapUiState.Ready ?: return@collect
                                val counts = computeVibePoiCounts(pois)
                                _uiState.value = state.copy(
                                    pois = pois,
                                    areaName = query,
                                    vibePoiCounts = counts,
                                    activeVibe = null,
                                    isSearchOverlayOpen = false,
                                    isAiResponding = false,
                                )
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(e) { "Area search error" }
                    val state = _uiState.value as? MapUiState.Ready ?: return@launch
                    _uiState.value = state.copy(isAiResponding = false)
                }
            }
        }
    }

    fun toggleFab() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(isFabExpanded = !current.isFabExpanded)
    }

    fun onMapRenderFailed() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(mapRenderFailed = true, showListView = true)
    }

    fun onCameraIdle(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        if (searchJob?.isActive == true) return
        if (_uiState.value !is MapUiState.Ready) return
        val readyState = _uiState.value as? MapUiState.Ready ?: return
        if (!isAwayFromGps(lat, lng, readyState)) {
            if (readyState.showMyLocation || readyState.showSearchThisArea) {
                _uiState.value = readyState.copy(showMyLocation = false, showSearchThisArea = false)
            }
            return
        }
        cameraIdleJob?.cancel()
        cameraIdleJob = viewModelScope.launch {
            delay(500)
            if (searchJob?.isActive == true) return@launch
            val geocodeResult = locationProvider.reverseGeocode(lat, lng)
            if (geocodeResult.isFailure) return@launch
            val newAreaName = geocodeResult.getOrThrow()
            val current = _uiState.value as? MapUiState.Ready ?: return@launch
            val newToken = newAreaName.substringBefore(",").trim()
            val currentToken = current.areaName.substringBefore(",").trim()
            val isNew = !newToken.equals(currentToken, ignoreCase = true)
            pendingLat = lat
            pendingLng = lng
            pendingAreaName = if (isNew) newAreaName else current.areaName
            _uiState.value = current.copy(
                showSearchThisArea = true,
                isNewArea = isNew,
                showMyLocation = isAwayFromGps(lat, lng, current),
            )
        }
    }

    fun onSearchThisAreaTapped() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        val areaName = pendingAreaName.ifBlank { return }
        val lat = pendingLat
        val lng = pendingLng
        _uiState.value = current.copy(
            showSearchThisArea = false,
            showMyLocation = false,
            isSearchingArea = true,
            vibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeVibe = null,
        )
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val context = areaContextFactory.create()
                getAreaPortrait(areaName, context)
                    .catch { e ->
                        AppLogger.e(e) { "Search this area: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready ?: return@catch
                        _uiState.value = s.copy(isSearchingArea = false)
                        _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
                    }
                    .collect { update ->
                        if (update is BucketUpdate.PortraitComplete) {
                            val pois = update.pois
                            val state = _uiState.value as? MapUiState.Ready ?: return@collect
                            val counts = computeVibePoiCounts(pois)
                            _uiState.value = state.copy(
                                areaName = areaName,
                                latitude = lat,
                                longitude = lng,
                                pois = pois,
                                vibePoiCounts = counts,
                                activeVibe = null,
                                isSearchingArea = false,
                                showMyLocation = isAwayFromGps(lat, lng, state),
                            )
                        }
                    }
            } catch (e: CancellationException) {
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Search this area: unexpected error" }
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
            }
        }
    }

    fun returnToCurrentLocation() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        searchJob?.cancel()
        returnToLocationJob?.cancel()

        // Hide button immediately for instant feedback (F1 + F3)
        _uiState.value = current.copy(showMyLocation = false)

        returnToLocationJob = viewModelScope.launch {
            try {
                val locResult = locationProvider.getCurrentLocation()
                if (locResult.isFailure) {
                    _errorEvents.tryEmit("Can't find your location. Please try again.")
                    return@launch
                }
                val coords = locResult.getOrThrow()
                val geocodeResult = locationProvider.reverseGeocode(coords.latitude, coords.longitude)
                val gpsAreaName = geocodeResult.getOrNull() ?: current.areaName

                val state = _uiState.value as? MapUiState.Ready ?: return@launch
                val gpsToken = gpsAreaName.substringBefore(",").trim()
                val currentToken = state.areaName.substringBefore(",").trim()
                val isSameArea = gpsToken.equals(currentToken, ignoreCase = true)

                analyticsTracker.trackEvent(
                    "return_to_location",
                    mapOf("same_area" to isSameArea.toString()),
                )

                if (isSameArea) {
                    _uiState.value = state.copy(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        gpsLatitude = coords.latitude,
                        gpsLongitude = coords.longitude,
                        showMyLocation = false,
                        showSearchThisArea = false,
                        cameraMoveId = state.cameraMoveId + 1,
                    )
                } else {
                    _uiState.value = state.copy(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        gpsLatitude = coords.latitude,
                        gpsLongitude = coords.longitude,
                        showMyLocation = false,
                        showSearchThisArea = false,
                        cameraMoveId = state.cameraMoveId + 1,
                        isSearchingArea = true,
                        pois = emptyList(),
                        vibePoiCounts = emptyMap(),
                        activeVibe = null,
                    )
                    val context = areaContextFactory.create()
                    getAreaPortrait(gpsAreaName, context)
                        .catch { e ->
                            AppLogger.e(e) { "Return to location: portrait fetch failed" }
                            val s = _uiState.value as? MapUiState.Ready ?: return@catch
                            _uiState.value = s.copy(isSearchingArea = false)
                            _errorEvents.tryEmit("Couldn't load area info. Try again.")
                        }
                        .collect { update ->
                            if (update is BucketUpdate.PortraitComplete) {
                                val pois = update.pois
                                val s = _uiState.value as? MapUiState.Ready ?: return@collect
                                val counts = computeVibePoiCounts(pois)
                                _uiState.value = s.copy(
                                    areaName = gpsAreaName,
                                    pois = pois,
                                    vibePoiCounts = counts,
                                    activeVibe = null,
                                    isSearchingArea = false,
                                )
                            }
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Return to location: unexpected error" }
                _errorEvents.tryEmit("Can't find your location. Please try again.")
            }
        }
    }

    fun retry() {
        loadJob?.cancel()
        _uiState.value = MapUiState.Loading
        loadLocation()
    }

    private fun loadLocation() {
        loadJob = viewModelScope.launch {
            try {
                val locationAndGeocode = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    val locResult = locationProvider.getCurrentLocation()
                    if (locResult.isFailure) return@withTimeoutOrNull locResult to null
                    val coords = locResult.getOrThrow()
                    locResult to locationProvider.reverseGeocode(coords.latitude, coords.longitude)
                }

                if (locationAndGeocode == null) {
                    AppLogger.e(null) { "Map: GPS/geocode timeout" }
                    _uiState.value = MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
                    return@launch
                }

                val (locationResult, areaNameResult) = locationAndGeocode

                if (locationResult.isFailure) {
                    AppLogger.e(locationResult.exceptionOrNull()) { "Map: location unavailable" }
                    _uiState.value = MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
                    return@launch
                }

                val coords = locationResult.getOrThrow()

                if (areaNameResult == null || areaNameResult.isFailure) {
                    AppLogger.e(areaNameResult?.exceptionOrNull()) { "Map: area name resolution failed" }
                    _uiState.value = MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
                    return@launch
                }

                val areaName = areaNameResult.getOrThrow()

                _uiState.value = MapUiState.Ready(
                    areaName = areaName,
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                    gpsLatitude = coords.latitude,
                    gpsLongitude = coords.longitude,
                    isSearchingArea = true,
                )

                // Fetch weather in parallel
                launch {
                    val weatherResult = weatherProvider.getWeather(coords.latitude, coords.longitude)
                    if (weatherResult.isSuccess) {
                        val current = _uiState.value as? MapUiState.Ready ?: return@launch
                        _uiState.value = current.copy(weather = weatherResult.getOrNull())
                    } else {
                        AppLogger.e(weatherResult.exceptionOrNull()) { "Map: weather fetch failed" }
                    }
                }

                val context = areaContextFactory.create()
                getAreaPortrait(areaName, context)
                    .catch { e -> AppLogger.e(e) { "Map: portrait fetch failed" } }
                    .collect { update ->
                        if (update is BucketUpdate.PortraitComplete) {
                            val pois = update.pois
                            val current = _uiState.value
                            if (current is MapUiState.Ready) {
                                val counts = computeVibePoiCounts(pois)
                                _uiState.value = current.copy(
                                    pois = pois,
                                    vibePoiCounts = counts,
                                    activeVibe = null,
                                    isSearchingArea = false,
                                )
                                analyticsTracker.trackEvent(
                                    "map_opened",
                                    mapOf("area_name" to areaName, "poi_count" to pois.size.toString()),
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Map: unexpected error during location resolution" }
                _uiState.value = MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
            }
        }
    }

    private fun computeVibePoiCounts(pois: List<POI>): Map<Vibe, Int> {
        val hasAnyVibes = pois.any { it.vibe.isNotBlank() }
        return if (hasAnyVibes) {
            Vibe.entries.associateWith { v -> pois.count { it.vibe.contains(v.name, ignoreCase = true) } }
        } else {
            // No vibes assigned — show total count on every vibe
            Vibe.entries.associateWith { pois.size }
        }
    }

    private fun isQuestion(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.endsWith("?")) return true
        val words = q.split(" ", limit = 2)
        if (words.size < 2) return false
        val questionStarters = setOf("what", "where", "when", "who", "how", "is", "are", "can", "does", "why")
        return words[0] in questionStarters
    }

    private fun computeFollowUpChips(query: String): List<String> {
        val q = query.lowercase()
        return when {
            q.containsAny("safe", "crime", "danger") -> listOf("Is it safe at night?", "What areas to avoid?")
            q.containsAny("food", "eat", "restaurant", "drink") -> listOf("Best time to visit?", "Vegetarian options?")
            q.containsAny("history", "historic", "old", "founded") -> listOf("When was it built?", "Any famous events here?")
            q.containsAny("cost", "price", "expensive", "cheap") -> listOf("Budget tips?", "Free things to do?")
            else -> listOf("Tell me more", "What's nearby?")
        }
    }

    private fun isAwayFromGps(cameraLat: Double, cameraLng: Double, state: MapUiState.Ready): Boolean {
        return kotlin.math.abs(cameraLat - state.gpsLatitude) > 0.0009 ||
               kotlin.math.abs(cameraLng - state.gpsLongitude) > 0.0009
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }

    companion object {
        internal const val LOCATION_FAILURE_MESSAGE = "Can't find your location. Please try again."
        internal const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
