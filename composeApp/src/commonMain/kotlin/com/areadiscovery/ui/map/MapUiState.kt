package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.POI
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
        val activeVibe: Vibe = Vibe.DEFAULT,
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
        val showSearchThisArea: Boolean = false,
    ) : MapUiState()
    data class LocationFailed(val message: String) : MapUiState()
}
