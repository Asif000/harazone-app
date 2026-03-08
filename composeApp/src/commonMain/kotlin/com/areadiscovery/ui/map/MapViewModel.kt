package com.areadiscovery.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.areadiscovery.data.remote.MapTilerGeocodingProvider
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.GeocodingSuggestion
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.RecentPlace
import com.areadiscovery.domain.repository.RecentPlacesRepository
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
    private val geocodingProvider: MapTilerGeocodingProvider,
    private val recentPlacesRepository: RecentPlacesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var cameraIdleJob: Job? = null
    private var returnToLocationJob: Job? = null
    private var geocodingJob: Job? = null
    private var pendingLat: Double = 0.0
    private var pendingLng: Double = 0.0
    private var pendingAreaName: String = ""
    private var latestRecents: List<RecentPlace> = emptyList()

    init {
        loadLocation()
        viewModelScope.launch {
            recentPlacesRepository.observeRecent().collect { recents ->
                latestRecents = recents
                val current = _uiState.value as? MapUiState.Ready ?: return@collect
                _uiState.value = current.copy(recentPlaces = recents)
            }
        }
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
        val cameraLat = if (pendingLat != 0.0) pendingLat else current.latitude
        val cameraLng = if (pendingLng != 0.0) pendingLng else current.longitude
        _uiState.value = current.copy(
            isSearchOverlayOpen = false,
            showMyLocation = isAwayFromGps(cameraLat, cameraLng, current),
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

    fun onGeocodingQueryChanged(query: String) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        geocodingJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = current.copy(
                geocodingQuery = "",
                geocodingSuggestions = emptyList(),
                isGeocodingLoading = false,
            )
            return
        }
        _uiState.value = current.copy(
            geocodingQuery = query,
            isGeocodingLoading = true,
        )
        geocodingJob = viewModelScope.launch {
            delay(300)
            val result = geocodingProvider.search(query)
            val updated = _uiState.value as? MapUiState.Ready ?: return@launch
            if (result.isFailure) {
                AppLogger.e(result.exceptionOrNull()) { "Geocoding search failed" }
                _uiState.value = updated.copy(isGeocodingLoading = false)
                return@launch
            }
            val raw = result.getOrThrow()
            val withDistance = raw.map { s ->
                if (updated.gpsLatitude == 0.0 && updated.gpsLongitude == 0.0) s
                else s.copy(distanceKm = haversineKm(updated.gpsLatitude, updated.gpsLongitude, s.latitude, s.longitude))
            }
            _uiState.value = updated.copy(
                geocodingSuggestions = withDistance,
                isGeocodingLoading = false,
            )
        }
    }

    fun onGeocodingSuggestionSelected(suggestion: GeocodingSuggestion) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        geocodingJob?.cancel()
        searchJob?.cancel()
        returnToLocationJob?.cancel()
        pendingLat = suggestion.latitude
        pendingLng = suggestion.longitude
        pendingAreaName = suggestion.name
        _uiState.value = current.copy(
            geocodingQuery = "",
            geocodingSuggestions = emptyList(),
            isGeocodingLoading = false,
            geocodingSelectedPlace = suggestion.name,
            isGeocodingInitiatedSearch = true,
            latitude = suggestion.latitude,
            longitude = suggestion.longitude,
            cameraMoveId = current.cameraMoveId + 1,
            isSearchingArea = true,
            showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, current),
            vibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeVibe = null,
        )
        searchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = suggestion.name,
                    onComplete = { pois, _ ->
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                        val counts = computeVibePoiCounts(pois)
                        _uiState.value = state.copy(
                            areaName = suggestion.name,
                            pois = pois,
                            vibePoiCounts = counts,
                            activeVibe = null,
                            isSearchingArea = false,
                            showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, state),
                        )
                    },
                    onError = { e ->
                        AppLogger.e(e) { "Geocoding selection: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(
                            isSearchingArea = false,
                            showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, s),
                        )
                        _errorEvents.tryEmit("Couldn't load area info. Try again.")
                    },
                )
            } catch (e: CancellationException) {
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Geocoding selection: unexpected error" }
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(
                    isSearchingArea = false,
                    showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, s),
                )
                _errorEvents.tryEmit("Couldn't load area info. Try again.")
            }
        }
        viewModelScope.launch {
            try {
                recentPlacesRepository.upsert(
                    RecentPlace(
                        name = suggestion.name,
                        latitude = suggestion.latitude,
                        longitude = suggestion.longitude,
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(e) { "Failed to upsert recent place" }
            }
        }
    }

    fun onRecentSelected(recent: RecentPlace) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        geocodingJob?.cancel()
        searchJob?.cancel()
        returnToLocationJob?.cancel()
        pendingLat = recent.latitude
        pendingLng = recent.longitude
        pendingAreaName = recent.name
        _uiState.value = current.copy(
            geocodingQuery = "",
            geocodingSuggestions = emptyList(),
            isGeocodingLoading = false,
            geocodingSelectedPlace = recent.name,
            isGeocodingInitiatedSearch = true,
            latitude = recent.latitude,
            longitude = recent.longitude,
            cameraMoveId = current.cameraMoveId + 1,
            isSearchingArea = true,
            showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, current),
            vibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeVibe = null,
        )
        searchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = recent.name,
                    onComplete = { pois, _ ->
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                        val counts = computeVibePoiCounts(pois)
                        _uiState.value = state.copy(
                            areaName = recent.name,
                            pois = pois,
                            vibePoiCounts = counts,
                            activeVibe = null,
                            isSearchingArea = false,
                            showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, state),
                        )
                    },
                    onError = { e ->
                        AppLogger.e(e) { "Recent selection: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(
                            isSearchingArea = false,
                            showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, s),
                        )
                        _errorEvents.tryEmit("Couldn't load area info. Try again.")
                    },
                )
            } catch (e: CancellationException) {
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Recent selection: unexpected error" }
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(
                    isSearchingArea = false,
                    showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, s),
                )
                _errorEvents.tryEmit("Couldn't load area info. Try again.")
            }
        }
        viewModelScope.launch {
            try {
                recentPlacesRepository.upsert(
                    RecentPlace(
                        name = recent.name,
                        latitude = recent.latitude,
                        longitude = recent.longitude,
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(e) { "Failed to upsert recent place" }
            }
        }
    }

    fun onClearRecents() {
        viewModelScope.launch {
            recentPlacesRepository.clearAll()
        }
    }

    fun onGeocodingSubmitEmpty() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cameraIdleJob?.cancel()
        geocodingJob?.cancel()
        returnToLocationJob?.cancel()
        val areaName = pendingAreaName.ifBlank { current.areaName }
        val lat = if (pendingLat != 0.0) pendingLat else current.latitude
        val lng = if (pendingLng != 0.0) pendingLng else current.longitude
        pendingAreaName = areaName
        pendingLat = lat
        pendingLng = lng
        _uiState.value = current.copy(
            isSearchingArea = true,
            isGeocodingInitiatedSearch = true,
            showMyLocation = isAwayFromGps(lat, lng, current),
            vibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeVibe = null,
        )
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = areaName,
                    onComplete = { pois, _ ->
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
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
                    },
                    onError = { e ->
                        AppLogger.e(e) { "Empty submit: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(
                            isSearchingArea = false,
                            showMyLocation = isAwayFromGps(lat, lng, s),
                        )
                        _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
                    },
                )
            } catch (e: CancellationException) {
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Empty submit: unexpected error" }
                val s = _uiState.value as? MapUiState.Ready
                if (s != null) _uiState.value = s.copy(
                    isSearchingArea = false,
                    showMyLocation = isAwayFromGps(lat, lng, s),
                )
                _errorEvents.tryEmit("Couldn't load area info. Try panning again.")
            }
        }
    }

    fun onGeocodingCleared() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        geocodingJob?.cancel()
        _uiState.value = current.copy(
            geocodingQuery = "",
            geocodingSuggestions = emptyList(),
            isGeocodingLoading = false,
            geocodingSelectedPlace = null,
            isGeocodingInitiatedSearch = false,
        )
    }

    fun onGeocodingCancelLoad() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        searchJob?.cancel()
        _uiState.value = current.copy(
            isSearchingArea = false,
            isGeocodingInitiatedSearch = false,
            isGeocodingLoading = false,
            geocodingQuery = "",
            geocodingSuggestions = emptyList(),
            geocodingSelectedPlace = null,
        )
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
            pendingLat = lat
            pendingLng = lng
            if (readyState.showMyLocation) {
                _uiState.value = readyState.copy(showMyLocation = false)
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
                showMyLocation = isAwayFromGps(lat, lng, current),
            )
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
                    val s = _uiState.value as? MapUiState.Ready
                    if (s != null) {
                        val camLat = if (pendingLat != 0.0) pendingLat else s.latitude
                        val camLng = if (pendingLng != 0.0) pendingLng else s.longitude
                        _uiState.value = s.copy(showMyLocation = isAwayFromGps(camLat, camLng, s))
                    }
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

                pendingLat = coords.latitude
                pendingLng = coords.longitude
                pendingAreaName = gpsAreaName

                if (isSameArea) {
                    _uiState.value = state.copy(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        gpsLatitude = coords.latitude,
                        gpsLongitude = coords.longitude,
                        showMyLocation = false,
                                    cameraMoveId = state.cameraMoveId + 1,
                        geocodingSelectedPlace = null,
                        isGeocodingInitiatedSearch = false,
                    )
                } else {
                    _uiState.value = state.copy(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        gpsLatitude = coords.latitude,
                        gpsLongitude = coords.longitude,
                        showMyLocation = false,
                                    cameraMoveId = state.cameraMoveId + 1,
                        isSearchingArea = true,
                        pois = emptyList(),
                        vibePoiCounts = emptyMap(),
                        activeVibe = null,
                        geocodingSelectedPlace = null,
                        isGeocodingInitiatedSearch = false,
                    )
                    collectPortraitWithRetry(
                        areaName = gpsAreaName,
                        onComplete = { pois, _ ->
                            val s = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                            val counts = computeVibePoiCounts(pois)
                            _uiState.value = s.copy(
                                areaName = gpsAreaName,
                                pois = pois,
                                vibePoiCounts = counts,
                                activeVibe = null,
                                isSearchingArea = false,
                            )
                        },
                        onError = { e ->
                            AppLogger.e(e) { "Return to location: portrait fetch failed" }
                            val s = _uiState.value as? MapUiState.Ready
                            if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                            _errorEvents.tryEmit("Couldn't load area info. Try again.")
                        },
                    )
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
                    recentPlaces = latestRecents,
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

                collectPortraitWithRetry(
                    areaName = areaName,
                    onComplete = { pois, _ ->
                        val current = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
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
                    },
                    onError = { e ->
                        AppLogger.e(e) { "Map: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(isSearchingArea = false)
                    },
                )
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

    private suspend fun collectPortraitWithRetry(
        areaName: String,
        onComplete: suspend (pois: List<POI>, finalAreaName: String) -> Unit,
        onError: suspend (Exception) -> Unit,
    ) {
        val context = areaContextFactory.create()
        try {
            var pois = emptyList<POI>()
            var fetchFailed = false
            getAreaPortrait(areaName, context)
                .catch { e ->
                    AppLogger.e(e) { "Portrait fetch failed for '$areaName'" }
                    fetchFailed = true
                    onError(e as? Exception ?: RuntimeException(e))
                }
                .collect { update ->
                    if (update is BucketUpdate.PortraitComplete) {
                        pois = update.pois
                    }
                }
            if (fetchFailed) return
            if (pois.isNotEmpty()) {
                onComplete(pois, areaName)
                return
            }
            // Retry with broader query
            AppLogger.d { "No POIs for '$areaName' — retrying with broader query" }
            val broadQuery = "$areaName points of interest landmarks restaurants parks"
            val retryContext = areaContextFactory.create()
            getAreaPortrait(broadQuery, retryContext)
                .catch { e -> AppLogger.e(e) { "Retry portrait fetch failed for '$areaName'" } }
                .collect { update ->
                    if (update is BucketUpdate.PortraitComplete) {
                        pois = update.pois
                    }
                }
            if (pois.isEmpty()) {
                _errorEvents.tryEmit("Nothing to see here — try another area")
            }
            onComplete(pois, areaName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * kotlin.math.PI / 180.0
        val dLon = (lon2 - lon1) * kotlin.math.PI / 180.0
        val rLat1 = lat1 * kotlin.math.PI / 180.0
        val rLat2 = lat2 * kotlin.math.PI / 180.0
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(rLat1) * kotlin.math.cos(rLat2) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
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
