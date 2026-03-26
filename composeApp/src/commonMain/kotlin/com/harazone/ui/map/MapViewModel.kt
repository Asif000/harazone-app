package com.harazone.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harazone.data.remote.MAX_GALLERY_IMAGES
import com.harazone.data.remote.MapTilerGeocodingProvider
import com.harazone.data.remote.WikipediaImageRepository
import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.GeocodingSuggestion
import com.harazone.domain.model.GhostPin
import com.harazone.domain.model.POI
import com.harazone.domain.model.RecentPlace
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.VisitState
import com.harazone.domain.repository.RecentPlacesRepository
import com.harazone.domain.repository.SavedPoiRepository
import com.harazone.data.repository.UserPreferencesRepository
import com.harazone.domain.provider.WeatherProvider
import com.harazone.domain.service.AreaContextFactory
import com.harazone.domain.usecase.GetAreaPortraitUseCase
import com.harazone.location.LocationProvider
import com.harazone.util.AnalyticsTracker
import com.harazone.domain.companion.CompanionNudgeEngine
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.DiscoveryContext
import com.harazone.domain.model.AreaAdvisory
import com.harazone.domain.model.CompanionNudge
import com.harazone.domain.model.NudgeType
import com.harazone.domain.provider.AdvisoryProvider
import com.harazone.domain.provider.LocaleProvider
import com.harazone.util.AppLogger
import com.harazone.util.haversineDistanceMeters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    private val companionEngine: CompanionNudgeEngine,
    private val localeProvider: LocaleProvider,
    private val advisoryProvider: AdvisoryProvider,
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
        counterAnimJob?.cancel()
        counterAnimJob = null
        pendingPostSearchSlideshow = false
        val pre = _uiState.value as? MapUiState.Ready
        if (pre != null) {
            _uiState.value = pre.copy(poiStreamingCount = 0)
        }
        areaFetchJob?.cancel()
        areaFetchJob = null
        cameraIdleJob?.cancel()
        onboardingBubbleJob?.cancel()
        cameraIdleJob = null
        geocodingJob?.cancel()
        geocodingJob = null
        poiBatchesCache.clear()
        // Clear stale companion state when switching areas
        nudgeQueue.clear()
        slideshowJob?.cancel()
        slideshowJob = null
        val current = _uiState.value as? MapUiState.Ready
        if (current != null && (current.companionNudge != null || current.isCompanionPulsing || current.autoSlideshowIndex != null)) {
            _uiState.value = current.copy(companionNudge = null, isCompanionPulsing = false, autoSlideshowIndex = null, cameraZoomLevel = DEFAULT_ZOOM_LEVEL)
        }
    }

    private var pendingLat: Double = 0.0
    private var pendingLng: Double = 0.0
    private var pendingAreaName: String = ""
    private var preSearchSnapshot: MapUiState.Ready? = null
    private var latestRecents: List<RecentPlace> = emptyList()
    private var latestSavedPois: List<SavedPoi> = emptyList()
    private var latestVisitedPoiIds: Set<String> = emptySet()
    private var vibeBeforeSavedFilter: DynamicVibe? = null
    private var lastWeatherFetchMs: Long = 0L
    private var currentDynamicVibes: List<DynamicVibe> = emptyList()
    var pinnedVibeLabels: List<String> = emptyList()
        private set
    private var pendingColdStart = false
    private var onboardingBubbleJob: Job? = null
    private val poiBatchesCache: MutableList<List<POI>> = mutableListOf()

    // Companion nudge queue + slideshow
    private val nudgeQueue = ArrayDeque<CompanionNudge>()
    private var deltaCheckFired = false
    private var slideshowJob: Job? = null
    private var counterAnimJob: Job? = null
    private var pendingPostSearchSlideshow = false

    // Cache for GPS home area — avoids re-querying Gemini on return-to-location
    private var gpsAreaNameCache: String? = null
    private var gpsAreaPoisCache: List<POI> = emptyList()
    private var gpsAreaCacheMs: Long = 0L

    // Track last fetch coordinates + time for stale-area detection
    private var lastFetchLat: Double = 0.0
    private var lastFetchLng: Double = 0.0
    private var lastFetchMs: Long = 0L

    // Gallery image prefetch
    private val prefetchJobs = HashMap<String, Job>()
    private val imagePrefetchSemaphore = Semaphore(MAX_GALLERY_IMAGES)

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
                    visitedPois = pois,
                    visitedPoiCount = pois.size,
                    dynamicVibeAreaSaveCounts = computeDynamicVibeAreaSaveCounts(pois, current.areaName),
                    showSurpriseMe = true,
                )
                if (!deltaCheckFired && pois.isNotEmpty()) {
                    deltaCheckFired = true
                    viewModelScope.launch {
                        companionEngine.checkRelaunched(pois, localeProvider.languageTag)
                            ?.let { enqueueNudge(it) }
                    }
                }
            }
        }
        viewModelScope.launch {
            savedPoiRepository.observeSavedIds().collect { ids ->
                latestVisitedPoiIds = ids
                val current = _uiState.value as? MapUiState.Ready ?: return@collect
                _uiState.value = current.copy(visitedPoiIds = ids)
            }
        }
    }

    fun selectPoi(poi: POI?) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        // Look up from enriched pois list to pick up Places data (reviewCount, images, etc.)
        val enrichedPoi = if (poi != null) {
            current.pois.firstOrNull {
                it.name.trim().lowercase() == poi.name.trim().lowercase()
            } ?: current.allDiscoveredPois.firstOrNull {
                it.name.trim().lowercase() == poi.name.trim().lowercase()
            } ?: poi
        } else null
        _uiState.value = current.copy(
            selectedPoi = enrichedPoi,
            selectedPinIndex = current.selectedPinIndex,
        )
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

    private fun flyToCoordsWithZoom(lat: Double, lng: Double, zoom: Double) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(
            latitude = lat,
            longitude = lng,
            cameraZoomLevel = zoom,
            cameraMoveId = current.cameraMoveId + 1,
        )
    }

    fun selectPoiWithImageResolve(poi: POI) {
        selectPoi(poi)
        prefetchGalleryImages(poi)
        if (poi.imageUrl != null) return
        viewModelScope.launch {
            try {
                val url = wikipediaImageRepository.getImageUrl(poi.wikiSlug, poi.name)
                if (url != null) {
                    val current = _uiState.value as? MapUiState.Ready ?: return@launch
                    val sel = current.selectedPoi
                    if (sel != null && sel.name == poi.name &&
                        sel.latitude == poi.latitude &&
                        sel.longitude == poi.longitude
                    ) {
                        _uiState.value = current.copy(selectedPoi = sel.copy(imageUrl = url))
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
            visitedFilter = false,
            pois = visiblePois,
        )
        analyticsTracker.trackEvent("vibe_switched", mapOf("vibe" to (newVibe?.label ?: "all")))
    }

    fun visitPoi(poi: POI, areaName: String): VisitState {
        val poiId = poi.savedId
        val current = _uiState.value as? MapUiState.Ready ?: return VisitState.WANT_TO_GO
        val status = resolveStatus(poi.liveStatus, poi.hours)
        val visitState = when {
            status == "open" || status == "closing soon" -> VisitState.GO_NOW
            status == "closed" -> VisitState.PLAN_SOON
            else -> VisitState.WANT_TO_GO
        }
        val savedPoiObj = SavedPoi(
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
            visitState = visitState,
        )
        val newVisitedIds = current.visitedPoiIds + poiId
        val ghosts = generateGhostPins(poi, current.allDiscoveredPois, newVisitedIds)
        _uiState.value = current.copy(
            visitedPoiIds = newVisitedIds,
            visitedPois = current.visitedPois + savedPoiObj,
            ghostPins = ghosts,
        )
        if (ghosts.isNotEmpty()) {
            _errorEvents.tryEmit("Saved! ${ghosts.size} similar nearby")
        }
        viewModelScope.launch {
            companionEngine.checkInstantNeighbor(savedPoiObj, current.allDiscoveredPois, current.visitedPoiIds)
                ?.let { enqueueNudge(it) }
            companionEngine.checkVibeReveal(current.visitedPois + savedPoiObj)
                ?.let { enqueueNudge(it) }
            if (visitState == VisitState.WANT_TO_GO) {
                enqueueNudge(companionEngine.makeAnticipationSeed(poi))
            }
        }
        viewModelScope.launch {
            try {
                savedPoiRepository.visit(savedPoiObj)
            } catch (e: Exception) {
                AppLogger.e(e) { "MapViewModel: visit POI failed" }
                val s = _uiState.value as? MapUiState.Ready ?: return@launch
                _uiState.value = s.copy(
                    visitedPoiIds = s.visitedPoiIds - poiId,
                    visitedPois = s.visitedPois.filter { it.id != poiId },
                )
            }
        }
        return visitState
    }

    fun unvisitPoi(poi: POI) {
        val poiId = poi.savedId
        val current = _uiState.value as? MapUiState.Ready ?: return
        val removedPoi = current.visitedPois.find { it.id == poiId }
        _uiState.value = current.copy(
            visitedPoiIds = current.visitedPoiIds - poiId,
            visitedPois = current.visitedPois.filter { it.id != poiId },
        )
        viewModelScope.launch {
            try {
                savedPoiRepository.unsave(poiId)
            } catch (e: Exception) {
                AppLogger.e(e) { "MapViewModel: unvisit POI failed" }
                val s = _uiState.value as? MapUiState.Ready ?: return@launch
                _uiState.value = s.copy(
                    visitedPoiIds = s.visitedPoiIds + poiId,
                    visitedPois = if (removedPoi != null) s.visitedPois + removedPoi else s.visitedPois,
                )
            }
        }
    }

    // --- Companion Nudge Engine ---

    private fun enqueueNudge(nudge: CompanionNudge) {
        if (nudgeQueue.size >= MAX_NUDGE_QUEUE_SIZE) {
            // Drop lowest-priority (highest ordinal) tail entry
            val maxOrdinal = nudgeQueue.maxByOrNull { it.type.ordinal } ?: return
            if (nudge.type.ordinal >= maxOrdinal.type.ordinal) return // new nudge is lower priority, discard
            nudgeQueue.removeLastOrNull()
        }
        val insertIndex = nudgeQueue.indexOfFirst { it.type.ordinal > nudge.type.ordinal }
            .takeIf { it >= 0 } ?: nudgeQueue.size
        nudgeQueue.add(insertIndex, nudge)
        val current = _uiState.value as? MapUiState.Ready ?: return
        if (current.companionNudge == null) {
            _uiState.value = current.copy(isCompanionPulsing = true)
        }
    }

    fun showCompanionCard() {
        val nudge = nudgeQueue.removeFirstOrNull() ?: companionEngine.makeQuietNudge()
        stopAutoSlideshowIfRunning()
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(companionNudge = nudge, isCompanionPulsing = false)
    }

    fun dismissCompanionCard() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(
            companionNudge = null,
            isCompanionPulsing = nudgeQueue.isNotEmpty(),
        )
    }

    fun onIdleDetected() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        if (current.pois.isEmpty() || current.isSearchingArea) return
        if (current.companionNudge != null) return // H1: don't start slideshow while card is open
        val carouselVisible = !current.showListView && current.selectedPoi == null
        if (carouselVisible) {
            startAutoSlideshow()
        } else {
            viewModelScope.launch {
                companionEngine.checkAmbientWhisper(
                    areaName = current.areaName,
                    visiblePois = current.pois,
                    languageTag = localeProvider.languageTag,
                )?.let { enqueueNudge(it) }
            }
        }
    }

    private fun zoomToFitPois(pois: List<POI>): Double {
        val lats = pois.mapNotNull { it.latitude }
        val lngs = pois.mapNotNull { it.longitude }
        if (lats.isEmpty()) return DEFAULT_ZOOM_LEVEL
        val latSpan = (lats.max() - lats.min()).coerceAtLeast(0.001)
        val lngSpan = (lngs.max() - lngs.min()).coerceAtLeast(0.001)
        val maxSpan = maxOf(latSpan, lngSpan)
        // Approximate: zoom ~14 for 0.01 degree span, -1 zoom per 2x span
        val zoom = (14.0 - kotlin.math.ln(maxSpan / 0.01) / kotlin.math.ln(2.0)).coerceIn(11.0, 15.0)
        return zoom
    }

    private fun startAutoSlideshow(fitCamera: Boolean = true) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        if (current.pois.isEmpty()) return
        slideshowJob?.cancel()

        // Zoom camera to fit all visible pins once at slideshow start (idle-triggered only)
        if (fitCamera) {
            val fitZoom = zoomToFitPois(current.pois)
            val lats = current.pois.mapNotNull { it.latitude }
            val lngs = current.pois.mapNotNull { it.longitude }
            if (lats.isNotEmpty()) {
                val centerLat = (lats.min() + lats.max()) / 2.0
                val centerLng = (lngs.min() + lngs.max()) / 2.0
                flyToCoordsWithZoom(centerLat, centerLng, fitZoom)
            }
        }

        var thisJob: Job? = null
        thisJob = viewModelScope.launch {
            var index = 0
            try {
                while (true) {
                    val state = _uiState.value as? MapUiState.Ready ?: break
                    if (state.pois.isEmpty() || state.showListView || state.selectedPoi != null) break

                    if (index >= state.pois.size) {
                        // End of current pois — advance if more batches available
                        if (!state.showAllMode && poiBatchesCache.size > state.activeBatchIndex + 1) {
                            onNextBatch()
                        } else if (state.showAllMode && poiBatchesCache.size > 1) {
                            // Wrap back to first batch
                            val firstBatch = poiBatchesCache.firstOrNull()
                            if (firstBatch != null && firstBatch.isNotEmpty()) {
                                _uiState.value = state.copy(
                                    showAllMode = false,
                                    activeBatchIndex = 0,
                                    pois = firstBatch,
                                    selectedPoi = null,
                                )
                            }
                        }
                        index = 0
                        delay(500)
                        continue
                    }

                    val poi = state.pois[index]
                    if (poi.latitude == null || poi.longitude == null) {
                        index++
                        continue
                    }
                    _uiState.value = state.copy(autoSlideshowIndex = index)
                    delay(SLIDESHOW_INTERVAL_MS)
                    index++
                }
            } finally {
                (_uiState.value as? MapUiState.Ready)?.let { s ->
                    if (s.autoSlideshowIndex != null) {
                        _uiState.value = s.copy(autoSlideshowIndex = null, cameraZoomLevel = DEFAULT_ZOOM_LEVEL)
                    }
                }
                if (slideshowJob === thisJob) slideshowJob = null
            }
        }
        slideshowJob = thisJob
    }

    /** Called from hot-path pointer input — skips if slideshow isn't running. */
    private fun animatePoiCounter(targetCount: Int) {
        counterAnimJob?.cancel()
        counterAnimJob = viewModelScope.launch {
            val current = _uiState.value as? MapUiState.Ready ?: return@launch
            val startCount = current.poiStreamingCount
            for (i in (startCount + 1)..targetCount) {
                delay(150L)
                val s = _uiState.value as? MapUiState.Ready ?: break
                _uiState.value = s.copy(poiStreamingCount = i)
            }
        }
    }

    private fun schedulePostSearchSlideshow() {
        viewModelScope.launch {
            delay(IDLE_THRESHOLD_MS)
            val state = _uiState.value as? MapUiState.Ready ?: return@launch
            if (state.pois.isNotEmpty() && !state.isSearchingArea && !state.showListView && state.selectedPoi == null) {
                startAutoSlideshow(fitCamera = false)
            }
        }
    }

    fun stopAutoSlideshowIfRunning() {
        if (slideshowJob == null) return
        slideshowJob?.cancel()
        slideshowJob = null
        val current = _uiState.value as? MapUiState.Ready ?: return
        if (current.autoSlideshowIndex != null) {
            _uiState.value = current.copy(autoSlideshowIndex = null, cameraZoomLevel = DEFAULT_ZOOM_LEVEL)
        }
    }

    fun openVisitsSheet() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(showVisitsSheet = true)
    }

    fun onVisitedFilterSelected() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val newFilter = !current.visitedFilter
        if (newFilter) {
            vibeBeforeSavedFilter = current.activeDynamicVibe
        }
        _uiState.value = current.copy(
            visitedFilter = newFilter,
            activeDynamicVibe = if (newFilter) null else vibeBeforeSavedFilter,
        )
        if (!newFilter) vibeBeforeSavedFilter = null
    }

    // --- Saved Lens ---

    fun onSavedLensTap() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val entering = !current.savedLensActive
        if (entering) {
            vibeBeforeSavedFilter = current.activeDynamicVibe
        }
        _uiState.value = current.copy(
            savedLensActive = entering,
            visitedFilter = entering,
            activeDynamicVibe = if (entering) null else vibeBeforeSavedFilter,
        )
        if (!entering) vibeBeforeSavedFilter = null
    }

    fun onExitSavedLens() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        if (!current.savedLensActive) return
        _uiState.value = current.copy(
            savedLensActive = false,
            visitedFilter = false,
            activeDynamicVibe = vibeBeforeSavedFilter,
        )
        vibeBeforeSavedFilter = null
    }

    /**
     * Generate 2-3 ghost pins for a just-saved POI: same vibe, nearest by distance,
     * not already saved. Uses the current allDiscoveredPois — no new API call.
     */
    private fun generateGhostPins(
        savedPoi: POI,
        allPois: List<POI>,
        visitedIds: Set<String>,
    ): List<GhostPin> {
        val savedId = savedPoi.savedId
        val savedVibe = savedPoi.primaryVibe ?: return emptyList()
        val savedLat = savedPoi.latitude ?: return emptyList()
        val savedLng = savedPoi.longitude ?: return emptyList()

        val candidates = allPois
            .filter { poi ->
                poi.savedId != savedId &&
                poi.savedId !in visitedIds &&
                poi.latitude != null && poi.longitude != null &&
                poi.primaryVibe == savedVibe
            }
            .sortedBy { poi ->
                haversineDistanceMeters(savedLat, savedLng, poi.latitude!!, poi.longitude!!)
            }
            .take(3)
        // Spec D22: "2-3 ghost pins" — skip if fewer than 2
        if (candidates.size < 2) return emptyList()
        return candidates.map { poi -> GhostPin(poi = poi, sourcePoiSavedId = savedId) }
    }

    fun saveGhostPin(ghostPin: GhostPin) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val remainingGhosts = current.ghostPins.filter { it.poi.savedId != ghostPin.poi.savedId }
        _uiState.value = current.copy(ghostPins = remainingGhosts)
        // visitPoi generates new ghosts — suppress by saving the remaining list and restoring after
        visitPoi(ghostPin.poi, current.areaName)
        val afterVisit = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = afterVisit.copy(ghostPins = remainingGhosts)
    }

    fun closeVisitsSheet() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(showVisitsSheet = false)
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
            areaName = suggestion.name,
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
            selectedPinIndex = null,
            areaHighlights = emptyList(),
            advisory = null,
            isAdvisoryBannerDismissed = false,
            hasAcknowledgedGate = false,
        )
        fetchWeatherForLocation(suggestion.latitude, suggestion.longitude)
        fetchAdvisory(suggestion.latitude, suggestion.longitude, previousArea = PreviousAreaInfo(current.areaName, current.latitude, current.longitude))
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
            areaName = recent.name,
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
            selectedPinIndex = null,
            areaHighlights = emptyList(),
            advisory = null,
            isAdvisoryBannerDismissed = false,
            hasAcknowledgedGate = false,
        )
        fetchWeatherForLocation(recent.latitude, recent.longitude)
        fetchAdvisory(recent.latitude, recent.longitude, previousArea = PreviousAreaInfo(current.areaName, current.latitude, current.longitude))
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

    fun onSearchThisArea() {
        pendingPostSearchSlideshow = true
        onGeocodingSubmitEmpty() // already sets showSearchAreaPill = false
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
            showSearchAreaPill = false,
            dynamicVibePoiCounts = emptyMap(),
            pois = emptyList(),
            activeDynamicVibe = null,
            selectedPinIndex = null,
            areaHighlights = emptyList(),
            advisory = null,
            isAdvisoryBannerDismissed = false,
            hasAcknowledgedGate = false,
            ghostPins = emptyList(),
        )
        fetchWeatherForLocation(lat, lng)
        fetchAdvisory(lat, lng, previousArea = PreviousAreaInfo(current.areaName, current.latitude, current.longitude))
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
                        if (pendingPostSearchSlideshow) {
                            pendingPostSearchSlideshow = false
                            schedulePostSearchSlideshow()
                        }
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
                visitedPois = latestSavedPois,
                visitedPoiIds = latestVisitedPoiIds,
                visitedPoiCount = latestSavedPois.size,
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

    fun onPinTapped(index: Int) {
        val state = _uiState.value as? MapUiState.Ready ?: return
        if (state.pois.isEmpty()) return
        _uiState.value = state.copy(selectedPinIndex = index.coerceIn(0, state.pois.size - 1))
    }

    fun onSurpriseMe() {
        AppLogger.d { ">>> onSurpriseMe() CALLED" }
        val current = _uiState.value as? MapUiState.Ready ?: return
        // Derive taste profile from saves if available
        val vibeProfile = latestSavedPois
            .map { it.vibe }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
        // Always include surprise hint — specific vibes if user has taste data
        val tasteProfile = vibeProfile.ifEmpty { listOf(AreaContext.SURPRISE_SENTINEL) }
        // Use pendingAreaName (set by onCameraIdle when user pans) — matches onGeocodingSubmitEmpty behavior
        val areaName = pendingAreaName.ifBlank { current.areaName }
        cancelAreaFetch()
        // Re-read state AFTER cancelAreaFetch to avoid restoring stale companion/slideshow state
        val postCancel = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = postCancel.copy(
            isSearchingArea = true,
            isSurpriseSearching = true,
            isLoadingVibes = true,
            pois = emptyList(),
            poiStreamingCount = 0,
            activeDynamicVibe = null,
            selectedPinIndex = null,
            ghostPins = emptyList(),
        )
        areaFetchJob = viewModelScope.launch {
            collectPortraitWithRetry(
                areaName = areaName,
                tasteProfile = tasteProfile,
                skipCache = true,
                onComplete = { _, _ ->
                    // Force-clear enriching flags — no background batches for surprise queries
                    val s = _uiState.value as? MapUiState.Ready
                    if (s != null) _uiState.value = s.copy(
                        isSearchingArea = false,
                        isSurpriseSearching = false,
                        isEnrichingArea = false,
                        isBackgroundFetching = false,
                    )
                    schedulePostSearchSlideshow()
                },
                onError = { _ ->
                    val s = _uiState.value as? MapUiState.Ready
                    if (s != null) _uiState.value = s.copy(
                        isSearchingArea = false,
                        isEnrichingArea = false,
                    )
                    _errorEvents.tryEmit("Discovery failed — try again")
                },
            )
        }
    }

    fun onCarouselSwiped(index: Int) {
        val state = _uiState.value as? MapUiState.Ready ?: return
        if (state.pois.isEmpty()) return
        _uiState.value = state.copy(
            selectedPinIndex = index.coerceIn(0, state.pois.size - 1),
            cameraZoomLevel = DEFAULT_ZOOM_LEVEL,
        )
    }

    fun onCarouselSelectionCleared() {
        val state = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = state.copy(selectedPinIndex = null)
    }

    fun onCameraIdle(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        // Always track camera position so "Search here" / "Surprise here!" use current coordinates
        pendingLat = lat
        pendingLng = lng
        if (_uiState.value !is MapUiState.Ready) return
        val readyState = _uiState.value as? MapUiState.Ready ?: return
        // Saved lens blocks pan → Discover threshold detection
        if (readyState.savedLensActive) return
        if (!isAwayFromGps(lat, lng, readyState)) {
            if (readyState.showMyLocation) {
                _uiState.value = readyState.copy(showMyLocation = false)
            }
            return
        }
        cameraIdleJob?.cancel()
        cameraIdleJob = viewModelScope.launch {
            delay(500)
            // Always reverse geocode so pendingAreaName stays current
            val geocodeResult = locationProvider.reverseGeocode(lat, lng)
            if (geocodeResult.isFailure) return@launch
            val newAreaName = geocodeResult.getOrThrow()
            val current = _uiState.value as? MapUiState.Ready ?: return@launch
            val newToken = newAreaName.substringBefore(",").trim()
            val currentToken = current.areaName.substringBefore(",").trim()
            val isNew = !newToken.equals(currentToken, ignoreCase = true)
            pendingAreaName = if (isNew) newAreaName else current.areaName
            // Only show pill when no fetch is in progress — coordinates + area name are already updated above
            if (areaFetchJob?.isActive != true) {
                _uiState.value = current.copy(
                    showMyLocation = isAwayFromGps(lat, lng, current),
                    showSearchAreaPill = true,
                )
            }
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

                val homeSnapshot = preSearchSnapshot
                preSearchSnapshot = null

                val timeSinceLastFetch = clockMs() - lastFetchMs
                val distanceFromLastFetch = if (lastFetchLat != 0.0 || lastFetchLng != 0.0)
                    haversineKm(coords.latitude, coords.longitude, lastFetchLat, lastFetchLng) * 1000
                else 0.0
                val isStale = lastFetchMs > 0L && timeSinceLastFetch > STALE_REFRESH_THRESHOLD_MS
                val hasMovedSignificantly = distanceFromLastFetch > DISTANCE_REFRESH_THRESHOLD_M

                if (isStale || hasMovedSignificantly) {
                    val savedPois = (_uiState.value as? MapUiState.Ready)?.visitedPois ?: emptyList()
                    companionEngine.checkProximity(coords.latitude, coords.longitude, savedPois)
                        ?.let { enqueueNudge(it) }
                }

                if (isSameArea && !isStale && !hasMovedSignificantly) {
                    // Restore pagination from home snapshot (cancelAreaFetch cleared poiBatchesCache)
                    val homeBatches = homeSnapshot?.poiBatches ?: state.poiBatches
                    poiBatchesCache.clear()
                    poiBatchesCache.addAll(homeBatches)
                    _uiState.value = state.copy(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        gpsLatitude = coords.latitude,
                        gpsLongitude = coords.longitude,
                        showMyLocation = false,
                                    cameraMoveId = state.cameraMoveId + 1,
                        geocodingSelectedPlace = null,
                        isGeocodingInitiatedSearch = false,
                        pois = homeSnapshot?.pois ?: state.pois,
                        poiBatches = homeBatches,
                        allDiscoveredPois = homeSnapshot?.allDiscoveredPois ?: state.allDiscoveredPois,
                        activeBatchIndex = 0,
                        showAllMode = false,
                        dynamicVibes = homeSnapshot?.dynamicVibes ?: state.dynamicVibes,
                        dynamicVibePoiCounts = homeSnapshot?.dynamicVibePoiCounts ?: state.dynamicVibePoiCounts,
                        activeDynamicVibe = null,
                        isBackgroundFetching = false,
                    )
                } else {
                    // Check if we have cached POIs for the GPS area (avoids re-querying Gemini)
                    val hasCachedPois = gpsAreaNameCache != null &&
                        gpsAreaName.equals(gpsAreaNameCache, ignoreCase = true) &&
                        gpsAreaPoisCache.isNotEmpty() &&
                        (clockMs() - gpsAreaCacheMs) < GPS_CACHE_STALE_MS &&
                        !hasMovedSignificantly

                    if (hasCachedPois) {
                        val counts = computeDynamicVibePoiCounts(gpsAreaPoisCache)
                        // Chunk cached POIs into batches of 3 to restore pagination
                        val batches = gpsAreaPoisCache.chunked(3)
                        poiBatchesCache.clear()
                        poiBatchesCache.addAll(batches)
                        _uiState.value = state.copy(
                            areaName = gpsAreaNameCache!!,
                            latitude = coords.latitude,
                            longitude = coords.longitude,
                            gpsLatitude = coords.latitude,
                            gpsLongitude = coords.longitude,
                            showMyLocation = false,
                            cameraMoveId = state.cameraMoveId + 1,
                            pois = batches.first(),
                            dynamicVibePoiCounts = counts,
                            activeDynamicVibe = null,
                            isSearchingArea = false,
                            isEnrichingArea = false,
                            geocodingSelectedPlace = null,
                            isGeocodingInitiatedSearch = false,
                            poiBatches = batches,
                            allDiscoveredPois = gpsAreaPoisCache,
                            activeBatchIndex = 0,
                            showAllMode = false,
                            isBackgroundFetching = false,
                            selectedPinIndex = null,
                            areaHighlights = emptyList(),
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
                            selectedPinIndex = null,
                            areaHighlights = emptyList(),
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
                                lastFetchLat = coords.latitude
                                lastFetchLng = coords.longitude
                                lastFetchMs = clockMs()
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
                    visitedPois = latestSavedPois,
                    visitedPoiIds = latestVisitedPoiIds,
                    visitedPoiCount = latestSavedPois.size,
                )

                // Fetch weather + advisory in parallel
                fetchWeatherForLocation(coords.latitude, coords.longitude)
                fetchAdvisory(coords.latitude, coords.longitude)

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
                        lastFetchLat = coords.latitude
                        lastFetchLng = coords.longitude
                        lastFetchMs = clockMs()
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
        // Skip empty batches
        var nextIndex = current.activeBatchIndex + 1
        while (nextIndex < poiBatchesCache.size && poiBatchesCache[nextIndex].isEmpty()) {
            nextIndex++
        }
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
            // Find last non-empty batch
            var lastIdx = poiBatchesCache.size - 1
            while (lastIdx >= 0 && poiBatchesCache[lastIdx].isEmpty()) lastIdx--
            if (lastIdx < 0) return
            val visible = computeVisiblePois(poiBatchesCache[lastIdx], current.activeDynamicVibe)
            _uiState.value = current.copy(
                showAllMode = false,
                activeBatchIndex = lastIdx,
                pois = visible,
                selectedPoi = null,
            )
        } else if (current.activeBatchIndex > 0) {
            // Skip empty batches backwards
            var newIndex = current.activeBatchIndex - 1
            while (newIndex > 0 && poiBatchesCache[newIndex].isEmpty()) newIndex--
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
            selectedPinIndex = null,
            areaHighlights = emptyList(),
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
        tasteProfile: List<String> = emptyList(),
        skipCache: Boolean = false,
        onComplete: suspend (pois: List<POI>, finalAreaName: String) -> Unit,
        onError: suspend (Exception) -> Unit,
    ) {
        val context = areaContextFactory.create().copy(
            isNewUser = pendingColdStart,
            tasteProfile = tasteProfile,
            skipCache = skipCache,
        )
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
                                allDiscoveredPois = update.pois.withDiscoveryContext(
                                    areaName = s.areaName,
                                    advisory = s.advisory,
                                    currencyText = s.areaCurrencyText,
                                    languageText = s.areaLanguageText,
                                ),
                                activeBatchIndex = 0,
                                isBackgroundFetching = true,
                                showAllMode = false,
                                areaHighlights = update.areaHighlights,
                            )
                            animatePoiCounter(update.pois.size)
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
                                allDiscoveredPois = update.pois.withDiscoveryContext(
                                    areaName = s.areaName,
                                    advisory = s.advisory,
                                    currencyText = s.areaCurrencyText,
                                    languageText = s.areaLanguageText,
                                ),
                                activeBatchIndex = 0,
                                isBackgroundFetching = true,
                                showAllMode = false,
                                areaHighlights = update.areaHighlights,
                            )
                            animatePoiCounter(update.pois.size)
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
                            // Keep batch 0 in sync with enriched data
                            if (poiBatchesCache.isNotEmpty()) {
                                poiBatchesCache[0] = pois
                            }
                            val s = _uiState.value as? MapUiState.Ready
                            if (s != null) {
                                _uiState.value = s.copy(
                                    pois = pois,
                                    dynamicVibePoiCounts = computeDynamicVibePoiCounts(pois),
                                    // Only set allDiscoveredPois if not already populated by BackgroundBatchReady
                                    allDiscoveredPois = (if (s.allDiscoveredPois.size > pois.size) s.allDiscoveredPois else pois)
                                        .withDiscoveryContext(
                                            areaName = s.areaName,
                                            advisory = s.advisory,
                                            currencyText = update.currencyText,
                                            languageText = update.languageText,
                                        ),
                                    isLoadingVibes = false,
                                    // Only clear enriching if no background batches are in flight
                                    isEnrichingArea = s.isBackgroundFetching,
                                    areaHighlights = update.areaHighlights.ifEmpty { s.areaHighlights },
                                    areaCurrencyText = update.currencyText,
                                    areaLanguageText = update.languageText,
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
                                allDiscoveredPois = allPois.withDiscoveryContext(
                                    areaName = s.areaName,
                                    advisory = s.advisory,
                                    currencyText = s.areaCurrencyText,
                                    languageText = s.areaLanguageText,
                                ),
                                dynamicVibePoiCounts = computeDynamicVibePoiCounts(allPois),
                            )
                            animatePoiCounter(allPois.size)
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
                                allDiscoveredPois = allPois.withDiscoveryContext(
                                    areaName = s.areaName,
                                    advisory = s.advisory,
                                    currencyText = s.areaCurrencyText,
                                    languageText = s.areaLanguageText,
                                ),
                                dynamicVibePoiCounts = computeDynamicVibePoiCounts(allPois),
                            )
                            // Refresh selectedPoi with enriched data (images, Places fields)
                            val s2 = _uiState.value as? MapUiState.Ready
                            if (s2 != null && s2.selectedPoi != null) {
                                val updatedSelected = allPois.firstOrNull {
                                    it.name.trim().lowercase() == s2.selectedPoi.name.trim().lowercase()
                                }
                                if (updatedSelected != null) {
                                    _uiState.value = s2.copy(selectedPoi = updatedSelected)
                                }
                            }
                        }
                        is BucketUpdate.BackgroundFetchComplete -> {
                            val s = _uiState.value as? MapUiState.Ready ?: return@collect
                            // Only clear if we still have batches — guards against:
                            // 1. Stale event after onSearchDeeper clears batches
                            // 2. Surprise queries where Stage 3 skips (no batches added) —
                            //    BackgroundFetchComplete fires early but is harmlessly absorbed here
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
                                areaHighlights = update.areaHighlights,
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
                                areaHighlights = update.areaHighlights,
                            )
                        }
                        is BucketUpdate.PortraitComplete -> {
                            pois = if (stage1Pois.isNotEmpty()) mergePois(stage1Pois, update.pois) else update.pois
                            val s = _uiState.value as? MapUiState.Ready
                            if (s != null) {
                                _uiState.value = s.copy(
                                    pois = pois,
                                    dynamicVibePoiCounts = computeDynamicVibePoiCounts(pois),
                                    allDiscoveredPois = if (s.allDiscoveredPois.size > pois.size) s.allDiscoveredPois else pois,
                                    isLoadingVibes = false,
                                    isEnrichingArea = s.isBackgroundFetching,
                                    areaHighlights = update.areaHighlights.ifEmpty { s.areaHighlights },
                                    areaCurrencyText = update.currencyText,
                                    areaLanguageText = update.languageText,
                                )
                            }
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

    private fun prefetchGalleryImages(poi: POI) {
        if (poi.imageUrls.isNotEmpty()) return
        if (prefetchJobs.containsKey(poi.savedId)) return
        prefetchJobs[poi.savedId] = viewModelScope.launch {
            try {
                val urls = imagePrefetchSemaphore.withPermit {
                    wikipediaImageRepository.getImageUrls(poi.wikiSlug, poi.name)
                }
                if (urls.isNotEmpty()) updatePoiImages(poi.savedId, urls)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* silently ignore — prefetch is best-effort */ }
            finally {
                prefetchJobs.remove(poi.savedId)
            }
        }
    }

    private fun updatePoiImages(savedId: String, urls: List<String>) {
        _uiState.update { state ->
            val ready = state as? MapUiState.Ready ?: return@update state
            fun POI.withImages(): POI {
                // Ensure imageUrl (used by POI card + hero) is first in the gallery list
                val heroUrl = imageUrl ?: urls.first()
                val galleryUrls = (listOf(heroUrl) + urls).distinct().take(MAX_GALLERY_IMAGES)
                return copy(imageUrls = galleryUrls, imageUrl = heroUrl)
            }
            ready.copy(
                pois = ready.pois.map { p -> if (p.savedId == savedId) p.withImages() else p },
                allDiscoveredPois = ready.allDiscoveredPois.map { p -> if (p.savedId == savedId) p.withImages() else p },
                selectedPoi = ready.selectedPoi?.let { sel ->
                    if (sel.savedId == savedId) sel.withImages() else sel
                },
            )
        }
    }

    private fun mergePois(stage1: List<POI>, enrichments: List<POI>): List<POI> {
        val enrichMap = enrichments.associateBy { it.name.trim().lowercase() }
        val merged = stage1.map { pin ->
            val enrich = enrichMap[pin.name.trim().lowercase()]
            if (enrich != null) pin.mergeFrom(enrich) else pin
        }
        val stage1Keys = stage1.map { it.name.trim().lowercase() }.toSet()
        val newPois = enrichments.filter { it.name.trim().lowercase() !in stage1Keys }
        val result = merged + newPois
        // Prefetch gallery images for enriched POIs (fire-and-forget)
        result.forEach { prefetchGalleryImages(it) }
        return result
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        haversineDistanceMeters(lat1, lon1, lat2, lon2) / 1000.0

    private fun isAwayFromGps(cameraLat: Double, cameraLng: Double, state: MapUiState.Ready): Boolean {
        return kotlin.math.abs(cameraLat - state.gpsLatitude) > 0.0009 ||
               kotlin.math.abs(cameraLng - state.gpsLongitude) > 0.0009
    }

    // --- Safety Advisory ---

    private data class PreviousAreaInfo(val name: String, val lat: Double, val lng: Double)

    private fun fetchAdvisory(lat: Double, lng: Double, previousArea: PreviousAreaInfo? = null) {
        viewModelScope.launch {
            try {
                val geoInfo = geocodingProvider.reverseGeocodeInfo(lat, lng).getOrNull()
                    ?: return@launch
                if (geoInfo.countryCode.isBlank()) return@launch

                advisoryProvider.getAdvisory(geoInfo.countryCode, geoInfo.regionName)
                    .onSuccess { advisory ->
                        val current = _uiState.value as? MapUiState.Ready ?: return@onSuccess
                        _uiState.value = current.copy(
                            advisory = advisory,
                            isAdvisoryBannerDismissed = false,
                            hasAcknowledgedGate = false,
                            previousAreaName = previousArea?.name,
                            previousAreaLat = previousArea?.lat,
                            previousAreaLng = previousArea?.lng,
                            hasPendingSafetyNudge = advisory.level.isAtLeast(AdvisoryLevel.CAUTION),
                        )
                    }
                    .onFailure { e ->
                        AppLogger.w(e) { "Advisory fetch failed" }
                    }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppLogger.w(e) { "Advisory fetch error" }
            }
        }
    }

    fun dismissAdvisoryBanner() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(isAdvisoryBannerDismissed = true)
    }

    fun acknowledgeGate() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(hasAcknowledgedGate = true)
    }

    fun goBackToSafety() {
        val state = _uiState.value as? MapUiState.Ready ?: return
        val prevLat = state.previousAreaLat
        val prevLng = state.previousAreaLng
        val prevName = state.previousAreaName
        if (prevLat != null && prevLng != null && !prevName.isNullOrBlank()) {
            // Navigate back using saved coordinates — no network round-trip needed
            flyToCoords(prevLat, prevLng)
            pendingAreaName = prevName
            pendingLat = prevLat
            pendingLng = prevLng
            onGeocodingSubmitEmpty()
        } else {
            // No previous area (cold launch) — just dismiss the gate, show banner
            acknowledgeGate()
        }
    }

    fun enqueueSafetyNudge(text: String) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val advisory = current.advisory ?: return
        companionEngine.buildSafetyNudge(advisory, text)?.let { enqueueNudge(it) }
        // Re-read after enqueueNudge — it mutates _uiState (sets isCompanionPulsing)
        val updated = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = updated.copy(hasPendingSafetyNudge = false)
    }

    private fun List<POI>.withDiscoveryContext(
        areaName: String,
        advisory: AreaAdvisory?,
        currencyText: String?,
        languageText: String?,
    ): List<POI> {
        val ctx = DiscoveryContext(
            areaName = areaName,
            countryCode = advisory?.countryCode ?: "",
            currency = currencyText,
            language = languageText,
            advisoryLevel = advisory?.level,
            advisoryBlurb = advisory?.summary,
        )
        return map { it.copy(discoveryContext = ctx) }
    }

    companion object {
        internal const val MAX_BATCH_SLOTS = 4 // 3 POI batches + 1 Show All slot
        private const val WEATHER_STALE_MS = 5 * 60 * 1000L // 5 minutes
        private const val GPS_CACHE_STALE_MS = 30 * 60 * 1000L // 30 minutes
        internal const val STALE_REFRESH_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour
        internal const val DISTANCE_REFRESH_THRESHOLD_M = 100.0 // 100m (low for testing, increase via Remote Config #55)
        // TODO(BACKLOG-LOW): Generic location error message — detect permission denial vs GPS off and show specific guidance
        internal const val LOCATION_FAILURE_MESSAGE = "Can't find your location. Please try again."
        internal const val LOCATION_TIMEOUT_MS = 10_000L
        // TODO(BACKLOG-MEDIUM): Move to Remote Config (#55) for server-side tuning
        private const val MAX_NUDGE_QUEUE_SIZE = 5
        internal const val IDLE_THRESHOLD_MS = 10_000L
        internal const val SLIDESHOW_INTERVAL_MS = 10_000L
        internal const val SLIDESHOW_ZOOM_LEVEL = 16.0
        internal const val DEFAULT_ZOOM_LEVEL = 14.0
    }
}
