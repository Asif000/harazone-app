package com.harazone.ui.map

import com.harazone.domain.model.AreaAdvisory
import com.harazone.domain.model.CompanionNudge
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.GeocodingSuggestion
import com.harazone.domain.model.GhostPin
import com.harazone.domain.model.POI
import com.harazone.domain.model.RecentPlace
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.WeatherState

sealed class MapUiState {
    data object Loading : MapUiState()
    data class Ready(
        val areaName: String,
        val latitude: Double,
        val longitude: Double,
        val pois: List<POI> = emptyList(),
        val selectedPoi: POI? = null,
        val showListView: Boolean = false,
        val activeDynamicVibe: DynamicVibe? = null,
        val dynamicVibePoiCounts: Map<String, Int> = emptyMap(),
        val weather: WeatherState? = null,
        val visitTag: String = "First visit",
        val mapRenderFailed: Boolean = false,
        val isSearchingArea: Boolean = false,
        val isEnrichingArea: Boolean = false,
        val gpsLatitude: Double = 0.0,
        val gpsLongitude: Double = 0.0,
        val showMyLocation: Boolean = false,
        val cameraMoveId: Int = 0,
        val geocodingQuery: String = "",
        val geocodingSuggestions: List<GeocodingSuggestion> = emptyList(),
        val isGeocodingLoading: Boolean = false,
        val geocodingSelectedPlace: String? = null,
        val isGeocodingInitiatedSearch: Boolean = false,
        val recentPlaces: List<RecentPlace> = emptyList(),
        val visitedPois: List<SavedPoi> = emptyList(),
        val visitedPoiCount: Int = 0,
        val visitedPoiIds: Set<String> = emptySet(),
        val showVisitsSheet: Boolean = false,
        val visitedFilter: Boolean = false,
        val dynamicVibeAreaSaveCounts: Map<String, Int> = emptyMap(),
        val dynamicVibes: List<DynamicVibe> = emptyList(),
        val isLoadingVibes: Boolean = false,
        val isOfflineVibes: Boolean = false,
        val showOnboardingBubble: Boolean = false,
        val poiBatches: List<List<POI>> = emptyList(),
        val allDiscoveredPois: List<POI> = emptyList(),
        val activeBatchIndex: Int = 0,
        val isBackgroundFetching: Boolean = false,
        val showAllMode: Boolean = false,
        val selectedPinIndex: Int? = null,
        val areaHighlights: List<String> = emptyList(),
        val companionNudge: CompanionNudge? = null,
        val isCompanionPulsing: Boolean = false,
        val autoSlideshowIndex: Int? = null,
        val cameraZoomLevel: Double = 14.0,
        val showSearchAreaPill: Boolean = false,
        val advisory: AreaAdvisory? = null,
        val isAdvisoryBannerDismissed: Boolean = false,
        val hasAcknowledgedGate: Boolean = false,
        val hasPendingSafetyNudge: Boolean = false,
        val previousAreaName: String? = null,
        val previousAreaLat: Double? = null,
        val previousAreaLng: Double? = null,
        val poiStreamingCount: Int = 0,
        val showSurpriseMe: Boolean = false,
        val activeVibeFilters: Set<String> = emptySet(),
        val areaCurrencyText: String? = null,
        val areaLanguageText: String? = null,
        val savedLensActive: Boolean = false,
        val ghostPins: List<GhostPin> = emptyList(),
    ) : MapUiState()
    data class LocationFailed(val message: String) : MapUiState()
}
