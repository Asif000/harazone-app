package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.POI

sealed class MapUiState {
    data object Loading : MapUiState()
    data class Ready(
        val areaName: String,
        val latitude: Double,
        val longitude: Double,
        val pois: List<POI> = emptyList(),
        val selectedPoi: POI? = null,
        val showListView: Boolean = false,
    ) : MapUiState()
    data class LocationFailed(val message: String) : MapUiState()
}
