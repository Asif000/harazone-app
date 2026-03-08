package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.GeocodingSuggestion
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.RecentPlace
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.domain.model.WeatherState

sealed class MapUiState {
    data object Loading : MapUiState()
    data class Ready(
        val areaName: String,
        val latitude: Double,
        val longitude: Double,
        val pois: List<POI> = emptyList(),
        val selectedPoi: POI? = null,
        val showListView: Boolean = false,
        val activeVibe: Vibe? = null,
        val vibePoiCounts: Map<Vibe, Int> = emptyMap(),
        val weather: WeatherState? = null,
        val visitTag: String = "First visit",
        val isSearchOverlayOpen: Boolean = false,
        val searchQuery: String = "",
        val aiResponse: String = "",
        val isAiResponding: Boolean = false,
        val followUpChips: List<String> = emptyList(),
        val isFabExpanded: Boolean = false,
        val mapRenderFailed: Boolean = false,
        val isSearchingArea: Boolean = false,
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
    ) : MapUiState()
    data class LocationFailed(val message: String) : MapUiState()
}
