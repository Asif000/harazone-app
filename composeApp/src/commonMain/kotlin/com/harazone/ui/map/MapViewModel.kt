package com.harazone.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harazone.data.remote.MapTilerGeocodingProvider
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.GeocodingSuggestion
import com.harazone.domain.model.POI
import com.harazone.domain.model.RecentPlace
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.repository.RecentPlacesRepository
import com.harazone.domain.repository.SavedPoiRepository
import com.harazone.domain.model.Vibe
import com.harazone.domain.provider.WeatherProvider
import com.harazone.domain.service.AreaContextFactory
import com.harazone.domain.usecase.GetAreaPortraitUseCase
import com.harazone.location.LocationProvider
import com.harazone.util.AnalyticsTracker
import com.harazone.util.AppLogger
import com.harazone.util.haversineDistanceMeters
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
    private val geocodingProvider: MapTilerGeocodingProvider,
    private val recentPlacesRepository: RecentPlacesRepository,
    private val savedPoiRepository: SavedPoiRepository,
    private val clockMs: () -> Long = { com.harazone.util.SystemClock().nowMs() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var loadJob: Job? = null
    private var areaFetchJob: Job? = null
    private var cameraIdleJob: Job? = null
    private var geocodingJob: Job? = null

    private fun cancelAreaFetch() {
        areaFetchJob?.cancel()
        areaFetchJob = null
        cameraIdleJob?.cancel()
        geocodingJob?.cancel()
    }

    private var pendingLat: Double = 0.0
    private var pendingLng: Double = 0.0
    private var pendingAreaName: String = ""
    private var preSearchSnapshot: MapUiState.Ready? = null
    private var latestRecents: List<RecentPlace> = emptyList()
    private var latestSavedPois: List<SavedPoi> = emptyList()
    private var latestSavedPoiIds: Set<String> = emptySet()
    private var lastWeatherFetchMs: Long = 0L

    // Cache for GPS home area — avoids re-querying Gemini on return-to-location
    private var gpsAreaNameCache: String? = null
    private var gpsAreaPoisCache: List<POI> = emptyList()
    private var gpsAreaCacheMs: Long = 0L

    init {
        loadLocation()
        viewModelScope.launch {
            recentPlacesRepository.observeRecent().collect { recents ->
                latestRecents = recents
                val current = _uiState.value as? MapUiState.Ready ?: return@collect
                _uiState.value = current.copy(recentPlaces = recents)
            }
        }
        viewModelScope.launch {
            savedPoiRepository.observeAll().collect { pois ->
                latestSavedPois = pois
                val current = _uiState.value as? MapUiState.Ready ?: return@collect
                _uiState.value = current.copy(savedPois = pois, savedPoiCount = pois.size)
            }
        }
        viewModelScope.launch {
            savedPoiRepository.observeSavedIds().collect { ids ->
                latestSavedPoiIds = ids
                val current = _uiState.value as? MapUiState.Ready ?: return@collect
                _uiState.value = current.copy(savedPoiIds = ids)
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

    // TODO(BACKLOG-LOW): submitSearch — no current UI caller; preserve for programmatic search
    fun submitSearch(query: String) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cancelAreaFetch()

        // Questions are routed to ChatOverlay by MapScreen — submitSearch only handles area searches
        analyticsTracker.trackEvent("search_area_submitted", mapOf("query" to query))

        areaFetchJob = viewModelScope.launch {
            try {
                val context = areaContextFactory.create()
                var stage1Pois = emptyList<POI>()
                getAreaPortrait(query, context)
                    .catch { e -> AppLogger.e(e) { "Area search failed" } }
                    .collect { update ->
                        when (update) {
                            is BucketUpdate.PinsReady -> {
                                val state = _uiState.value as? MapUiState.Ready ?: return@collect
                                stage1Pois = update.pois
                                _uiState.value = state.copy(
                                    pois = update.pois,
                                    vibePoiCounts = computeVibePoiCounts(update.pois),
                                    isSearchingArea = false,
                                    isEnrichingArea = true,
                                )
                            }
                            is BucketUpdate.PortraitComplete -> {
                                val pois = if (stage1Pois.isNotEmpty()) mergePois(stage1Pois, update.pois) else update.pois
                                val state = _uiState.value as? MapUiState.Ready ?: return@collect
                                _uiState.value = state.copy(
                                    pois = pois,
                                    areaName = query,
                                    vibePoiCounts = computeVibePoiCounts(pois),
                                    activeVibe = null,
                                    isSearchingArea = false,
                                    isEnrichingArea = false,
                                )
                            }
                            else -> {}
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Area search error" }
            }
        }
    }

    fun savePoi(poi: POI, areaName: String) {
        val poiId = poi.savedId
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(savedPoiIds = current.savedPoiIds + poiId)
        viewModelScope.launch {
            try {
                savedPoiRepository.save(
                    SavedPoi(
                        id = poiId,
                        name = poi.name,
                        type = poi.type,
                        areaName = areaName,
                        lat = poi.latitude ?: 0.0,
                        lng = poi.longitude ?: 0.0,
                        whySpecial = poi.insight,
                        savedAt = 0L,
                        imageUrl = poi.imageUrl,
                        description = poi.description,
                        rating = poi.rating,
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(e) { "MapViewModel: save POI failed" }
                val s = _uiState.value as? MapUiState.Ready ?: return@launch
                _uiState.value = s.copy(savedPoiIds = s.savedPoiIds - poiId)
            }
        }
    }

    fun unsavePoi(poi: POI) {
        val poiId = poi.savedId
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(savedPoiIds = current.savedPoiIds - poiId)
        viewModelScope.launch {
            try {
                savedPoiRepository.unsave(poiId)
            } catch (e: Exception) {
                AppLogger.e(e) { "MapViewModel: unsave POI failed" }
                val s = _uiState.value as? MapUiState.Ready ?: return@launch
                _uiState.value = s.copy(savedPoiIds = s.savedPoiIds + poiId)
            }
        }
    }

    fun toggleFab() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(isFabExpanded = !current.isFabExpanded)
    }

    fun openSavesSheet() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(showSavesSheet = true)
    }

    fun closeSavesSheet() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(showSavesSheet = false)
        refreshWeatherIfStale()
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
        cancelAreaFetch()
        preSearchSnapshot = current
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
        fetchWeatherForLocation(suggestion.latitude, suggestion.longitude)
        areaFetchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = suggestion.name,
                    onComplete = { pois, _ ->
                        preSearchSnapshot = null
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                        val counts = computeVibePoiCounts(pois)
                        _uiState.value = state.copy(
                            areaName = suggestion.name,
                            pois = pois,
                            vibePoiCounts = counts,
                            activeVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
                            showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, state),
                        )
                    },
                    onError = { e ->
                        preSearchSnapshot = null
                        AppLogger.e(e) { "Geocoding selection: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(
                            isSearchingArea = false,
                            isEnrichingArea = false,
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

    // TODO(BACKLOG-LOW): ~50 lines duplicated between onRecentSelected and onGeocodingSuggestionSelected — extract shared helper
    fun onRecentSelected(recent: RecentPlace) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        cancelAreaFetch()
        preSearchSnapshot = current
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
        fetchWeatherForLocation(recent.latitude, recent.longitude)
        areaFetchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = recent.name,
                    onComplete = { pois, _ ->
                        preSearchSnapshot = null
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                        val counts = computeVibePoiCounts(pois)
                        _uiState.value = state.copy(
                            areaName = recent.name,
                            pois = pois,
                            vibePoiCounts = counts,
                            activeVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
                            showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, state),
                        )
                    },
                    onError = { e ->
                        preSearchSnapshot = null
                        AppLogger.e(e) { "Recent selection: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(
                            isSearchingArea = false,
                            isEnrichingArea = false,
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
        cancelAreaFetch()
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
        fetchWeatherForLocation(lat, lng)
        areaFetchJob = viewModelScope.launch {
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
                            isEnrichingArea = false,
                            showMyLocation = isAwayFromGps(lat, lng, state),
                        )
                    },
                    onError = { e ->
                        AppLogger.e(e) { "Empty submit: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(
                            isSearchingArea = false,
                            isEnrichingArea = false,
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
        cancelAreaFetch()
        val snapshot = preSearchSnapshot
        if (snapshot != null) {
            pendingLat = snapshot.latitude
            pendingLng = snapshot.longitude
            pendingAreaName = snapshot.areaName
            _uiState.value = snapshot.copy(
                cameraMoveId = snapshot.cameraMoveId + 1,
                isSearchingArea = false,
                isEnrichingArea = false,
                isGeocodingInitiatedSearch = false,
                isGeocodingLoading = false,
                geocodingQuery = "",
                geocodingSuggestions = emptyList(),
                geocodingSelectedPlace = null,
                savedPois = latestSavedPois,
                savedPoiIds = latestSavedPoiIds,
                savedPoiCount = latestSavedPois.size,
                recentPlaces = latestRecents,
            )
            preSearchSnapshot = null
        } else {
            val current = _uiState.value as? MapUiState.Ready ?: return
            _uiState.value = current.copy(
                isSearchingArea = false,
                isEnrichingArea = false,
                isGeocodingInitiatedSearch = false,
                isGeocodingLoading = false,
                geocodingQuery = "",
                geocodingSuggestions = emptyList(),
                geocodingSelectedPlace = null,
            )
        }
    }

    fun onMapRenderFailed() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(mapRenderFailed = true, showListView = true)
    }

    fun onCameraIdle(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        if (areaFetchJob?.isActive == true) return
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
            if (areaFetchJob?.isActive == true) return@launch
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
        cancelAreaFetch()

        // Hide button immediately for instant feedback (F1 + F3)
        _uiState.value = current.copy(showMyLocation = false)

        areaFetchJob = viewModelScope.launch {
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

                // Always refresh weather + time for GPS location
                fetchWeatherForLocation(coords.latitude, coords.longitude)

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
                    // Check if we have cached POIs for the GPS area (avoids re-querying Gemini)
                    val hasCachedPois = gpsAreaNameCache != null &&
                        gpsAreaName.equals(gpsAreaNameCache, ignoreCase = true) &&
                        gpsAreaPoisCache.isNotEmpty() &&
                        (clockMs() - gpsAreaCacheMs) < GPS_CACHE_STALE_MS

                    if (hasCachedPois) {
                        val counts = computeVibePoiCounts(gpsAreaPoisCache)
                        _uiState.value = state.copy(
                            areaName = gpsAreaNameCache!!,
                            latitude = coords.latitude,
                            longitude = coords.longitude,
                            gpsLatitude = coords.latitude,
                            gpsLongitude = coords.longitude,
                            showMyLocation = false,
                            cameraMoveId = state.cameraMoveId + 1,
                            pois = gpsAreaPoisCache,
                            vibePoiCounts = counts,
                            activeVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
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
                                val counts2 = computeVibePoiCounts(pois)
                                _uiState.value = s.copy(
                                    areaName = gpsAreaName,
                                    pois = pois,
                                    vibePoiCounts = counts2,
                                    activeVibe = null,
                                    isSearchingArea = false,
                                    isEnrichingArea = false,
                                )
                                // Update cache for future returns
                                gpsAreaNameCache = gpsAreaName
                                gpsAreaPoisCache = pois
                                gpsAreaCacheMs = clockMs()
                            },
                            onError = { e ->
                                AppLogger.e(e) { "Return to location: portrait fetch failed" }
                                val s = _uiState.value as? MapUiState.Ready
                                if (s != null) _uiState.value = s.copy(isSearchingArea = false, isEnrichingArea = false)
                                _errorEvents.tryEmit("Couldn't load area info. Try again.")
                            },
                        )
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
                    recentPlaces = latestRecents,
                    savedPois = latestSavedPois,
                    savedPoiIds = latestSavedPoiIds,
                    savedPoiCount = latestSavedPois.size,
                )

                // Fetch weather in parallel
                fetchWeatherForLocation(coords.latitude, coords.longitude)

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
                            isEnrichingArea = false,
                        )
                        // Cache GPS area POIs for instant return-to-location
                        gpsAreaNameCache = areaName
                        gpsAreaPoisCache = pois
                        gpsAreaCacheMs = clockMs()
                        analyticsTracker.trackEvent(
                            "map_opened",
                            mapOf("area_name" to areaName, "poi_count" to pois.size.toString()),
                        )
                    },
                    onError = { e ->
                        AppLogger.e(e) { "Map: portrait fetch failed" }
                        val s = _uiState.value as? MapUiState.Ready
                        if (s != null) _uiState.value = s.copy(isSearchingArea = false, isEnrichingArea = false)
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

    private fun fetchWeatherForLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            val weatherResult = weatherProvider.getWeather(lat, lng)
            if (weatherResult.isSuccess) {
                val current = _uiState.value as? MapUiState.Ready ?: return@launch
                _uiState.value = current.copy(weather = weatherResult.getOrNull())
                lastWeatherFetchMs = clockMs()
            } else {
                AppLogger.e(weatherResult.exceptionOrNull()) { "Weather fetch failed" }
            }
        }
    }

    fun refreshWeatherIfStale() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val elapsed = clockMs() - lastWeatherFetchMs
        if (elapsed >= WEATHER_STALE_MS) {
            fetchWeatherForLocation(current.latitude, current.longitude)
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

    private suspend fun collectPortraitWithRetry(
        areaName: String,
        onComplete: suspend (pois: List<POI>, finalAreaName: String) -> Unit,
        onError: suspend (Exception) -> Unit,
    ) {
        val context = areaContextFactory.create()
        try {
            var pois = emptyList<POI>()
            var stage1Pois = emptyList<POI>()
            var fetchFailed = false
            getAreaPortrait(areaName, context)
                .catch { e ->
                    AppLogger.e(e) { "Portrait fetch failed for '$areaName'" }
                    fetchFailed = true
                    onError(e as? Exception ?: RuntimeException(e))
                }
                .collect { update ->
                    when (update) {
                        is BucketUpdate.PinsReady -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            val counts = computeVibePoiCounts(update.pois)
                            stage1Pois = update.pois
                            _uiState.value = s.copy(
                                pois = update.pois,
                                vibePoiCounts = counts,
                                isSearchingArea = false,
                                isEnrichingArea = true,
                            )
                        }
                        is BucketUpdate.PortraitComplete -> {
                            pois = if (stage1Pois.isNotEmpty()) {
                                mergePois(stage1Pois, update.pois)
                            } else {
                                update.pois
                            }
                            // Update selectedPoi if open, so shimmer clears without user closing card
                            val s = _uiState.value as? MapUiState.Ready
                            if (s != null && s.selectedPoi != null) {
                                val updatedSelected = pois.firstOrNull {
                                    it.name.trim().lowercase() == s.selectedPoi.name.trim().lowercase()
                                }
                                if (updatedSelected != null) {
                                    _uiState.value = s.copy(selectedPoi = updatedSelected)
                                }
                            }
                        }
                        else -> { /* ContentDelta, BucketComplete, ContentAvailabilityNote — ignored */ }
                    }
                }
            if (fetchFailed) return
            // If Stage 1 already delivered pins, treat as success even if Stage 2 enrichment was empty
            if (pois.isNotEmpty() || stage1Pois.isNotEmpty()) {
                onComplete(pois.ifEmpty { stage1Pois }, areaName)
                return
            }
            // Retry with broader query only if both stages produced nothing
            AppLogger.d { "No POIs for '$areaName' — retrying with broader query" }
            val broadQuery = "$areaName points of interest landmarks restaurants parks"
            val retryContext = areaContextFactory.create()
            getAreaPortrait(broadQuery, retryContext)
                .catch { e -> AppLogger.e(e) { "Retry portrait fetch failed for '$areaName'" } }
                .collect { update ->
                    when (update) {
                        is BucketUpdate.PinsReady -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            stage1Pois = update.pois
                            _uiState.value = s.copy(
                                pois = update.pois,
                                vibePoiCounts = computeVibePoiCounts(update.pois),
                                isSearchingArea = false,
                                isEnrichingArea = true,
                            )
                        }
                        is BucketUpdate.PortraitComplete -> {
                            pois = if (stage1Pois.isNotEmpty()) mergePois(stage1Pois, update.pois) else update.pois
                        }
                        else -> {}
                    }
                }
            if (pois.isEmpty() && stage1Pois.isEmpty()) {
                _errorEvents.tryEmit("Nothing to see here — try another area")
            }
            onComplete(pois.ifEmpty { stage1Pois }, areaName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun mergePois(stage1: List<POI>, enrichments: List<POI>): List<POI> {
        val enrichMap = enrichments.associateBy { it.name.trim().lowercase() }
        val merged = stage1.map { pin ->
            val enrich = enrichMap[pin.name.trim().lowercase()]
            if (enrich != null) pin.copy(
                vibe = enrich.vibe.ifEmpty { pin.vibe },
                insight = enrich.insight,
                rating = enrich.rating,
                hours = enrich.hours,
                liveStatus = enrich.liveStatus,
                imageUrl = enrich.imageUrl ?: pin.imageUrl,
                wikiSlug = enrich.wikiSlug ?: pin.wikiSlug,
            ) else pin
        }
        val stage1Keys = stage1.map { it.name.trim().lowercase() }.toSet()
        val newPois = enrichments.filter { it.name.trim().lowercase() !in stage1Keys }
        return merged + newPois
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        haversineDistanceMeters(lat1, lon1, lat2, lon2) / 1000.0

    private fun isAwayFromGps(cameraLat: Double, cameraLng: Double, state: MapUiState.Ready): Boolean {
        return kotlin.math.abs(cameraLat - state.gpsLatitude) > 0.0009 ||
               kotlin.math.abs(cameraLng - state.gpsLongitude) > 0.0009
    }

    companion object {
        private const val WEATHER_STALE_MS = 5 * 60 * 1000L // 5 minutes
        private const val GPS_CACHE_STALE_MS = 30 * 60 * 1000L // 30 minutes
        // TODO(BACKLOG-LOW): Generic location error message — detect permission denial vs GPS off and show specific guidance
        internal const val LOCATION_FAILURE_MESSAGE = "Can't find your location. Please try again."
        internal const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
