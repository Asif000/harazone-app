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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private var loadJob: Job? = null
    private var searchJob: Job? = null

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
        _uiState.value = current.copy(activeVibe = vibe)
        analyticsTracker.trackEvent("vibe_switched", mapOf("vibe" to vibe.name))
    }

    fun openSearchOverlay() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(
            isSearchOverlayOpen = true,
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
        _uiState.value = current.copy(isSearchOverlayOpen = false)
        searchJob?.cancel()
    }

    fun submitSearch(query: String) {
        val current = _uiState.value as? MapUiState.Ready ?: return
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
                                val newActiveVibe = if ((counts[state.activeVibe] ?: 0) == 0) {
                                    Vibe.entries.maxByOrNull { counts[it] ?: 0 } ?: Vibe.DEFAULT
                                } else {
                                    state.activeVibe
                                }
                                _uiState.value = state.copy(
                                    pois = pois,
                                    areaName = query,
                                    vibePoiCounts = counts,
                                    activeVibe = newActiveVibe,
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
                                )
                                analyticsTracker.trackEvent(
                                    "map_opened",
                                    mapOf("area_name" to areaName, "poi_count" to pois.size.toString()),
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
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
            Vibe.entries.associateWith { v -> pois.count { it.vibe.equals(v.name, ignoreCase = true) } }
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

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }

    companion object {
        internal const val LOCATION_FAILURE_MESSAGE = "Can't find your location. Please try again."
        internal const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
