package com.harazone.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harazone.data.remote.MapTilerGeocodingProvider
import com.harazone.data.remote.WikipediaImageRepository
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.GeocodingSuggestion
import com.harazone.domain.model.POI
import com.harazone.domain.model.RecentPlace
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.repository.RecentPlacesRepository
import com.harazone.domain.repository.SavedPoiRepository
import com.harazone.data.repository.UserPreferencesRepository
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
    private val wikipediaImageRepository: WikipediaImageRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
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
        onboardingBubbleJob?.cancel()
        cameraIdleJob = null
        geocodingJob?.cancel()
        geocodingJob = null
        poiBatchesCache.clear()
    }

    private var pendingLat: Double = 0.0
    private var pendingLng: Double = 0.0
    private var pendingAreaName: String = ""
    private var preSearchSnapshot: MapUiState.Ready? = null
    private var latestRecents: List<RecentPlace> = emptyList()
    private var latestSavedPois: List<SavedPoi> = emptyList()
    private var latestSavedPoiIds: Set<String> = emptySet()
    private var vibeBeforeSavedFilter: DynamicVibe? = null
    private var lastWeatherFetchMs: Long = 0L
    private var currentDynamicVibes: List<DynamicVibe> = emptyList()
    var pinnedVibeLabels: List<String> = emptyList()
        private set
    private var pendingColdStart = false
    private var onboardingBubbleJob: Job? = null
    private val poiBatchesCache: MutableList<List<POI>> = mutableListOf()

    // Cache for GPS home area — avoids re-querying Gemini on return-to-location
    private var gpsAreaNameCache: String? = null
    private var gpsAreaPoisCache: List<POI> = emptyList()
    private var gpsAreaCacheMs: Long = 0L

    init {
        loadLocation()
        viewModelScope.launch {
            pinnedVibeLabels = userPreferencesRepository.getPinnedVibes()
            val coldStartSeen = userPreferencesRepository.getColdStartSeen()
            if (!coldStartSeen) {
                pendingColdStart = true
            }
        }
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
                _uiState.value = current.copy(
                    savedPois = pois,
                    savedPoiCount = pois.size,
                    dynamicVibeAreaSaveCounts = computeDynamicVibeAreaSaveCounts(pois, current.areaName),
                )
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

    fun flyToCoords(lat: Double, lng: Double) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(
            latitude = lat,
            longitude = lng,
            cameraMoveId = current.cameraMoveId + 1,
        )
    }

    fun selectPoiWithImageResolve(poi: POI) {
        selectPoi(poi)
        if (poi.imageUrl != null) return
        viewModelScope.launch {
            try {
                val url = wikipediaImageRepository.getImageUrl(poi.wikiSlug, poi.name)
                if (url != null) {
                    val current = _uiState.value as? MapUiState.Ready ?: return@launch
                    if (current.selectedPoi?.name == poi.name &&
                        current.selectedPoi?.latitude == poi.latitude &&
                        current.selectedPoi?.longitude == poi.longitude
                    ) {
                        _uiState.value = current.copy(selectedPoi = poi.copy(imageUrl = url))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppLogger.w(e) { "Wikipedia image resolve failed for '${poi.name}'" }
            }
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

    fun switchDynamicVibe(vibe: DynamicVibe) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val newVibe = if (current.activeDynamicVibe?.label == vibe.label) null else vibe
        val visiblePois = if (current.showAllMode) {
            computeVisiblePois(current.allDiscoveredPois, newVibe)
        } else if (poiBatchesCache.isNotEmpty()) {
            computeVisiblePois(poiBatchesCache.getOrElse(current.activeBatchIndex) { emptyList() }, newVibe)
        } else {
            computeVisiblePois(current.pois, newVibe)
        }
        _uiState.value = current.copy(
            activeDynamicVibe = newVibe,
            savedVibeFilter = false,
            pois = visiblePois,
            selectedPinId = null,
            cardsVisible = false,
            pinScreenPositions = emptyMap(),
        )
        analyticsTracker.trackEvent("vibe_switched", mapOf("vibe" to (newVibe?.label ?: "all")))
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
                        vibe = current.activeDynamicVibe?.label
                            ?: poi.vibe.split(",").firstOrNull()?.trim() ?: "",
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

    fun onSavedVibeSelected() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val newFilter = !current.savedVibeFilter
        if (newFilter) {
            vibeBeforeSavedFilter = current.activeDynamicVibe
        }
        _uiState.value = current.copy(
            savedVibeFilter = newFilter,
            activeDynamicVibe = if (newFilter) null else vibeBeforeSavedFilter,
            selectedPinId = null,
            cardsVisible = false,
            pinScreenPositions = emptyMap(),
        )
        if (!newFilter) vibeBeforeSavedFilter = null
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
        if (preSearchSnapshot == null) preSearchSnapshot = current
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
                isLoadingVibes = true,
            showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, current),
            dynamicVibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeDynamicVibe = null,
            selectedPinId = null,
            cardsVisible = false,
            pinScreenPositions = emptyMap(),
        )
        fetchWeatherForLocation(suggestion.latitude, suggestion.longitude)
        areaFetchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = suggestion.name,
                    onComplete = { pois, _ ->
                        preSearchSnapshot = null
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                        val counts = computeDynamicVibePoiCounts(pois)
                        _uiState.value = state.copy(
                            areaName = suggestion.name,
                            pois = pois,
                            dynamicVibePoiCounts = counts,
                            activeDynamicVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
                            showMyLocation = isAwayFromGps(suggestion.latitude, suggestion.longitude, state),
                            dynamicVibeAreaSaveCounts = computeDynamicVibeAreaSaveCounts(latestSavedPois, suggestion.name),
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
        if (preSearchSnapshot == null) preSearchSnapshot = current
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
                isLoadingVibes = true,
            showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, current),
            dynamicVibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeDynamicVibe = null,
            selectedPinId = null,
            cardsVisible = false,
            pinScreenPositions = emptyMap(),
        )
        fetchWeatherForLocation(recent.latitude, recent.longitude)
        areaFetchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = recent.name,
                    onComplete = { pois, _ ->
                        preSearchSnapshot = null
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                        val counts = computeDynamicVibePoiCounts(pois)
                        _uiState.value = state.copy(
                            areaName = recent.name,
                            pois = pois,
                            dynamicVibePoiCounts = counts,
                            activeDynamicVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
                            showMyLocation = isAwayFromGps(recent.latitude, recent.longitude, state),
                            dynamicVibeAreaSaveCounts = computeDynamicVibeAreaSaveCounts(latestSavedPois, recent.name),
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
        preSearchSnapshot = null
        val areaName = pendingAreaName.ifBlank { current.areaName }
        val lat = if (pendingLat != 0.0) pendingLat else current.latitude
        val lng = if (pendingLng != 0.0) pendingLng else current.longitude
        pendingAreaName = areaName
        pendingLat = lat
        pendingLng = lng
        _uiState.value = current.copy(
            isSearchingArea = true,
                isLoadingVibes = true,
            isGeocodingInitiatedSearch = true,
            showMyLocation = isAwayFromGps(lat, lng, current),
            dynamicVibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeDynamicVibe = null,
        )
        fetchWeatherForLocation(lat, lng)
        areaFetchJob = viewModelScope.launch {
            try {
                collectPortraitWithRetry(
                    areaName = areaName,
                    onComplete = { pois, _ ->
                        val state = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                        val counts = computeDynamicVibePoiCounts(pois)
                        _uiState.value = state.copy(
                            areaName = areaName,
                            latitude = lat,
                            longitude = lng,
                            pois = pois,
                            dynamicVibePoiCounts = counts,
                            activeDynamicVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
                            showMyLocation = isAwayFromGps(lat, lng, state),
                            dynamicVibeAreaSaveCounts = computeDynamicVibeAreaSaveCounts(latestSavedPois, areaName),
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

    fun onPinsProjected(positions: Map<String, ScreenOffset>) {
        val state = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = state.copy(pinScreenPositions = positions, cardsVisible = true)
    }

    fun onMapGestureStart() {
        val state = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = state.copy(cardsVisible = false)
    }

    fun onPinChipTapped(poiId: String) {
        val state = _uiState.value as? MapUiState.Ready ?: return
        val newSelected = if (state.selectedPinId == poiId) null else poiId
        _uiState.value = state.copy(selectedPinId = newSelected)
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
        if (preSearchSnapshot == null) preSearchSnapshot = current

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

                preSearchSnapshot = null
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
                        val counts = computeDynamicVibePoiCounts(gpsAreaPoisCache)
                        _uiState.value = state.copy(
                            areaName = gpsAreaNameCache!!,
                            latitude = coords.latitude,
                            longitude = coords.longitude,
                            gpsLatitude = coords.latitude,
                            gpsLongitude = coords.longitude,
                            showMyLocation = false,
                            cameraMoveId = state.cameraMoveId + 1,
                            pois = gpsAreaPoisCache,
                            dynamicVibePoiCounts = counts,
                            activeDynamicVibe = null,
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
                isLoadingVibes = true,
                            pois = emptyList(),
                            dynamicVibePoiCounts = emptyMap(),
                            activeDynamicVibe = null,
                            geocodingSelectedPlace = null,
                            isGeocodingInitiatedSearch = false,
                        )
                        collectPortraitWithRetry(
                            areaName = gpsAreaName,
                            onComplete = { pois, _ ->
                                val s = _uiState.value as? MapUiState.Ready ?: return@collectPortraitWithRetry
                                val counts2 = computeDynamicVibePoiCounts(pois)
                                _uiState.value = s.copy(
                                    areaName = gpsAreaName,
                                    pois = pois,
                                    dynamicVibePoiCounts = counts2,
                                    activeDynamicVibe = null,
                                    isSearchingArea = false,
                                    isEnrichingArea = false,
                                    dynamicVibeAreaSaveCounts = computeDynamicVibeAreaSaveCounts(latestSavedPois, gpsAreaName),
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
                isLoadingVibes = true,
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
                        val counts = computeDynamicVibePoiCounts(pois)
                        _uiState.value = current.copy(
                            pois = pois,
                            dynamicVibePoiCounts = counts,
                            activeDynamicVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
                            dynamicVibeAreaSaveCounts = computeDynamicVibeAreaSaveCounts(latestSavedPois, areaName),
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

    private fun computeDynamicVibePoiCounts(pois: List<POI>, vibes: List<DynamicVibe> = currentDynamicVibes): Map<String, Int> {
        if (vibes.isEmpty()) return emptyMap()
        return vibes.associate { dv ->
            dv.label to pois.count { poi -> poi.vibe == dv.label || dv.label in poi.vibes }
        }
    }

    private fun computeDynamicVibeAreaSaveCounts(saves: List<SavedPoi>, areaName: String): Map<String, Int> {
        val normalizedArea = areaName.lowercase().trim()
        return saves.filter { it.areaName.lowercase().trim() == normalizedArea && it.vibe.isNotEmpty() }
            .groupBy { it.vibe }
            .mapValues { it.value.size }
    }

    fun buildChipRowVibes(geminiVibes: List<DynamicVibe>, areaName: String): List<DynamicVibe> {
        // 1. Collect saved-POI vibes not in Gemini list
        val geminiLabels = geminiVibes.map { it.label }.toSet()
        val normalizedArea = areaName.lowercase().trim()
        val savedPoiVibes = latestSavedPois
            .filter { it.areaName.lowercase().trim() == normalizedArea && it.vibe.isNotBlank() && it.vibe !in geminiLabels }
            .map { it.vibe }
            .distinct()
            .map { DynamicVibe(label = it, icon = "\uD83D\uDD16") } // 🔖

        // 2. Sort: pinned first → saved-POI vibes → remaining Gemini vibes
        val pinnedSet = pinnedVibeLabels.toSet()
        val pinned = pinnedVibeLabels.mapNotNull { label ->
            geminiVibes.firstOrNull { it.label == label }
                ?: savedPoiVibes.firstOrNull { it.label == label }
                ?: if (label in pinnedSet) DynamicVibe(label = label, icon = "\uD83D\uDCCC") else null // 📌
        }
        val pinnedLabels = pinned.map { it.label }.toSet()
        val savedNotPinned = savedPoiVibes.filter { it.label !in pinnedLabels }
        val geminiNotPinned = geminiVibes.filter { it.label !in pinnedLabels }

        return (pinned + savedNotPinned + geminiNotPinned).take(6)
    }

    fun togglePin(vibe: DynamicVibe) {
        if (vibe.label in pinnedVibeLabels) {
            pinnedVibeLabels = pinnedVibeLabels - vibe.label
        } else if (pinnedVibeLabels.size < 3) {
            pinnedVibeLabels = pinnedVibeLabels + vibe.label
        } else {
            _errorEvents.tryEmit("Maximum 3 pinned vibes")
            return
        }
        viewModelScope.launch { userPreferencesRepository.setPinnedVibes(pinnedVibeLabels) }
        val current = _uiState.value as? MapUiState.Ready ?: return
        val chipRow = buildChipRowVibes(currentDynamicVibes, current.areaName)
        _uiState.value = current.copy(dynamicVibes = chipRow)
    }

    fun onOnboardingBubbleDismissed() {
        viewModelScope.launch { userPreferencesRepository.setColdStartSeen() }
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(showOnboardingBubble = false)
    }

    fun onNextBatch() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        if (current.showAllMode) return
        val nextIndex = current.activeBatchIndex + 1
        if (nextIndex < poiBatchesCache.size) {
            val visible = computeVisiblePois(poiBatchesCache[nextIndex], current.activeDynamicVibe)
            _uiState.value = current.copy(
                activeBatchIndex = nextIndex,
                pois = visible,
                selectedPoi = null,
            )
        } else {
            // Enter Show All mode
            val allPois = if (current.activeDynamicVibe != null) {
                computeVisiblePois(current.allDiscoveredPois, current.activeDynamicVibe)
            } else {
                current.allDiscoveredPois
            }
            _uiState.value = current.copy(
                showAllMode = true,
                pois = allPois,
                selectedPoi = null,
            )
        }
    }

    fun onPrevBatch() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        if (current.showAllMode) {
            val lastIdx = poiBatchesCache.size - 1
            val visible = computeVisiblePois(poiBatchesCache.getOrElse(lastIdx) { emptyList() }, current.activeDynamicVibe)
            _uiState.value = current.copy(
                showAllMode = false,
                activeBatchIndex = lastIdx.coerceAtLeast(0),
                pois = visible,
                selectedPoi = null,
            )
        } else if (current.activeBatchIndex > 0) {
            val newIndex = current.activeBatchIndex - 1
            val visible = computeVisiblePois(poiBatchesCache[newIndex], current.activeDynamicVibe)
            _uiState.value = current.copy(
                activeBatchIndex = newIndex,
                pois = visible,
                selectedPoi = null,
            )
        }
    }

    fun onSearchDeeper() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        // M3 fix: cancel job (and clear poiBatchesCache) BEFORE resetting UI state so no
        // stale BackgroundBatchReady event can repopulate the cache between clear and cancel.
        cancelAreaFetch()
        _uiState.value = current.copy(
            activeBatchIndex = 0,
            showAllMode = false,
            isBackgroundFetching = false,
            poiBatches = emptyList(),
            allDiscoveredPois = emptyList(),
            pois = emptyList(),
        )
        retryAreaFetch()
    }

    private fun computeVisiblePois(batch: List<POI>, activeVibe: DynamicVibe?): List<POI> {
        if (activeVibe == null) return batch
        val label = activeVibe.label
        return batch.filter { poi ->
            poi.vibes.any { it.equals(label, ignoreCase = true) } ||
                poi.vibe.equals(label, ignoreCase = true) ||
                poi.vibe.split(",").any { it.trim().equals(label, ignoreCase = true) }
        }
    }

    fun retryAreaFetch() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        onGeocodingSubmitEmpty()
    }

    private suspend fun collectPortraitWithRetry(
        areaName: String,
        onComplete: suspend (pois: List<POI>, finalAreaName: String) -> Unit,
        onError: suspend (Exception) -> Unit,
    ) {
        val context = areaContextFactory.create().copy(isNewUser = pendingColdStart)
        try {
            var pois = emptyList<POI>()
            var stage1Pois = emptyList<POI>()
            var fetchFailed = false
            var stage2Complete = false
            getAreaPortrait(areaName, context)
                .catch { e ->
                    AppLogger.e(e) { "Portrait fetch failed for '$areaName'" }
                    fetchFailed = true
                    // H4 fix: clear loading state on error so skeleton shimmer doesn't persist
                    val s = _uiState.value as? MapUiState.Ready
                    if (s != null) _uiState.value = s.copy(isLoadingVibes = false)
                    onError(e as? Exception ?: RuntimeException(e))
                }
                .collect { update ->
                    when (update) {
                        is BucketUpdate.VibesReady -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            stage1Pois = update.pois
                            currentDynamicVibes = update.vibes
                            val chipRow = buildChipRowVibes(update.vibes, areaName)
                            val counts = computeDynamicVibePoiCounts(update.pois, chipRow)
                            poiBatchesCache.clear()
                            poiBatchesCache.add(update.pois)
                            if (pendingColdStart) {
                                pendingColdStart = false
                                onboardingBubbleJob?.cancel()
                                onboardingBubbleJob = viewModelScope.launch {
                                    delay(2000)
                                    val s2 = _uiState.value as? MapUiState.Ready ?: return@launch
                                    _uiState.value = s2.copy(showOnboardingBubble = true)
                                }
                            }
                            _uiState.value = s.copy(
                                pois = update.pois,
                                dynamicVibes = chipRow,
                                dynamicVibePoiCounts = counts,
                                isLoadingVibes = false,
                                isOfflineVibes = update.fromCache,
                                isSearchingArea = false,
                                isEnrichingArea = true,
                                poiBatches = listOf(update.pois),
                                allDiscoveredPois = update.pois,
                                activeBatchIndex = 0,
                                isBackgroundFetching = true,
                                showAllMode = false,
                            )
                        }
                        is BucketUpdate.PinsReady -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            stage1Pois = update.pois
                            poiBatchesCache.clear()
                            poiBatchesCache.add(update.pois)
                            _uiState.value = s.copy(
                                pois = update.pois,
                                isSearchingArea = false,
                                isEnrichingArea = true,
                                isLoadingVibes = false,
                                poiBatches = listOf(update.pois),
                                allDiscoveredPois = update.pois,
                                activeBatchIndex = 0,
                                isBackgroundFetching = true,
                                showAllMode = false,
                            )
                        }
                        is BucketUpdate.DynamicVibeComplete -> {
                            currentDynamicVibes = currentDynamicVibes.map { dv ->
                                if (dv.label == update.content.label) dv.copy(poiIds = update.content.poiIds) else dv
                            }
                        }
                        is BucketUpdate.PortraitComplete -> {
                            pois = if (stage1Pois.isNotEmpty()) {
                                mergePois(stage1Pois, update.pois)
                            } else {
                                update.pois
                            }
                            val s = _uiState.value as? MapUiState.Ready
                            if (s != null) {
                                _uiState.value = s.copy(
                                    dynamicVibePoiCounts = computeDynamicVibePoiCounts(pois),
                                    isLoadingVibes = false,
                                )
                            }
                            // Update selectedPoi if open, so shimmer clears without user closing card
                            val s2 = _uiState.value as? MapUiState.Ready
                            if (s2 != null && s2.selectedPoi != null) {
                                val updatedSelected = pois.firstOrNull {
                                    it.name.trim().lowercase() == s2.selectedPoi.name.trim().lowercase()
                                }
                                if (updatedSelected != null) {
                                    _uiState.value = s2.copy(selectedPoi = updatedSelected)
                                }
                            }
                            // Fire onComplete immediately — don't wait for background batches
                            if (!stage2Complete) {
                                stage2Complete = true
                                onComplete(pois.ifEmpty { stage1Pois }, areaName)
                            }
                        }
                        is BucketUpdate.BackgroundBatchReady -> {
                            // TODO(BACKLOG-MEDIUM): M1 — stale BackgroundBatchReady events can still
                            // arrive after cancelAreaFetch() clears the cache if cancellation is delayed.
                            // Guard: check poiBatchesCache.isEmpty() as a proxy for "fetch was reset"
                            // and discard the event. Requires a generation counter or cancellation token
                            // to do this reliably without a concurrent-modification race of its own.
                            if (update.pois.isEmpty()) return@collect
                            // Pad cache to correct index if a prior batch was empty/skipped
                            while (poiBatchesCache.size < update.batchIndex) {
                                poiBatchesCache.add(emptyList())
                            }
                            if (update.batchIndex < poiBatchesCache.size) {
                                poiBatchesCache[update.batchIndex] = update.pois
                            } else {
                                poiBatchesCache.add(update.pois)
                            }
                            val allPois = poiBatchesCache.flatten()
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            _uiState.value = s.copy(
                                poiBatches = poiBatchesCache.toList(),
                                allDiscoveredPois = allPois,
                                dynamicVibePoiCounts = computeDynamicVibePoiCounts(allPois),
                            )
                        }
                        is BucketUpdate.BackgroundEnrichmentComplete -> {
                            if (update.batchIndex < 1 || update.batchIndex >= poiBatchesCache.size) return@collect
                            val existingBatch = poiBatchesCache[update.batchIndex]
                            val enrichMap = update.pois.associateBy { it.name.trim().lowercase() }
                            val merged = existingBatch.map { existing ->
                                val enriched = enrichMap[existing.name.trim().lowercase()]
                                if (enriched != null) existing.copy(
                                    vibe = enriched.vibe.ifEmpty { existing.vibe },
                                    vibes = enriched.vibes.ifEmpty { existing.vibes },
                                    insight = enriched.insight.ifEmpty { existing.insight },
                                    rating = enriched.rating ?: existing.rating,
                                    hours = enriched.hours ?: existing.hours,
                                    liveStatus = enriched.liveStatus ?: existing.liveStatus,
                                    imageUrl = enriched.imageUrl ?: existing.imageUrl,
                                    wikiSlug = enriched.wikiSlug ?: existing.wikiSlug,
                                ) else existing
                            }
                            poiBatchesCache[update.batchIndex] = merged
                            val allPois = poiBatchesCache.flatten()
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            _uiState.value = s.copy(
                                poiBatches = poiBatchesCache.toList(),
                                allDiscoveredPois = allPois,
                                dynamicVibePoiCounts = computeDynamicVibePoiCounts(allPois),
                            )
                        }
                        is BucketUpdate.BackgroundFetchComplete -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            // Only clear if we still have batches (guards against stale event after onSearchDeeper)
                            if (s.poiBatches.isNotEmpty()) {
                                _uiState.value = s.copy(isBackgroundFetching = false)
                            }
                        }
                        else -> { /* ContentDelta, BucketComplete, ContentAvailabilityNote — ignored */ }
                    }
                }
            if (fetchFailed) return
            // If Stage 1 already delivered pins, treat as success even if Stage 2 enrichment was empty
            if (pois.isNotEmpty() || stage1Pois.isNotEmpty()) {
                if (!stage2Complete) onComplete(pois.ifEmpty { stage1Pois }, areaName)
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
                        is BucketUpdate.VibesReady -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            currentDynamicVibes = update.vibes
                            stage1Pois = update.pois
                            val chipVibes = buildChipRowVibes(update.vibes, areaName)
                            _uiState.value = s.copy(
                                pois = update.pois,
                                dynamicVibes = chipVibes,
                                dynamicVibePoiCounts = computeDynamicVibePoiCounts(update.pois),
                                isSearchingArea = false,
                                isEnrichingArea = true,
                                isLoadingVibes = false,
                            )
                        }
                        is BucketUpdate.PinsReady -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            stage1Pois = update.pois
                            _uiState.value = s.copy(
                                pois = update.pois,
                                dynamicVibePoiCounts = computeDynamicVibePoiCounts(update.pois),
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
                vibes = enrich.vibes.ifEmpty { pin.vibes },
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
        internal const val MAX_BATCH_SLOTS = 4 // 3 POI batches + 1 Show All slot
        private const val WEATHER_STALE_MS = 5 * 60 * 1000L // 5 minutes
        private const val GPS_CACHE_STALE_MS = 30 * 60 * 1000L // 30 minutes
        // TODO(BACKLOG-LOW): Generic location error message — detect permission denial vs GPS off and show specific guidance
        internal const val LOCATION_FAILURE_MESSAGE = "Can't find your location. Please try again."
        internal const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
