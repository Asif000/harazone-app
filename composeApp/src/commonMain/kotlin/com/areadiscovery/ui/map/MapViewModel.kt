package com.areadiscovery.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.areadiscovery.domain.service.PrivacyPipeline
import com.areadiscovery.location.LocationProvider
import com.areadiscovery.util.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MapViewModel(
    private val locationProvider: LocationProvider,
    private val privacyPipeline: PrivacyPipeline,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadLocation()
    }

    fun retry() {
        _uiState.value = MapUiState.Loading
        loadLocation()
    }

    private fun loadLocation() {
        viewModelScope.launch {
            try {
                val locationDeferred = async { locationProvider.getCurrentLocation() }
                val areaNameDeferred = async { privacyPipeline.resolveAreaName() }

                val locationResult = locationDeferred.await()

                if (locationResult.isFailure) {
                    areaNameDeferred.cancel()
                    AppLogger.e(locationResult.exceptionOrNull()) { "Map: location unavailable" }
                    _uiState.value = MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
                    return@launch
                }

                val areaNameResult = areaNameDeferred.await()

                if (areaNameResult.isFailure) {
                    AppLogger.e(areaNameResult.exceptionOrNull()) { "Map: area name resolution failed" }
                    _uiState.value = MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
                    return@launch
                }

                val coords = locationResult.getOrThrow()
                val areaName = areaNameResult.getOrThrow()

                _uiState.value = MapUiState.Ready(
                    areaName = areaName,
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Map: unexpected error during location resolution" }
                _uiState.value = MapUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
            }
        }
    }

    companion object {
        internal const val LOCATION_FAILURE_MESSAGE = "Can't find your location. Please try again."
    }
}
