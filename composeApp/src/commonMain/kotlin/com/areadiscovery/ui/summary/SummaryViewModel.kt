package com.areadiscovery.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.areadiscovery.domain.service.AreaContextFactory
import com.areadiscovery.domain.service.PrivacyPipeline
import com.areadiscovery.domain.usecase.GetAreaPortraitUseCase
import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class SummaryViewModel(
    private val privacyPipeline: PrivacyPipeline,
    private val areaContextFactory: AreaContextFactory,
    private val getAreaPortrait: GetAreaPortraitUseCase,
    private val stateMapper: SummaryStateMapper,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private var collectionJob: Job? = null
    private var resolvedAreaName: String? = null
    private var maxScrollDepthPercent: Int = 0

    init {
        loadPortrait()
    }

    fun refresh() {
        _uiState.value = SummaryUiState.Loading
        resolvedAreaName = null
        maxScrollDepthPercent = 0
        loadPortrait()
    }

    fun updateScrollDepth(depthPercent: Int) {
        if (depthPercent > maxScrollDepthPercent) {
            maxScrollDepthPercent = depthPercent
        }
    }

    fun onScreenExit() {
        val areaName = resolvedAreaName ?: return
        if (maxScrollDepthPercent > 0) {
            analyticsTracker.trackEvent(
                "summary_scroll_depth",
                mapOf(
                    "area_name" to areaName,
                    "depth_percent" to maxScrollDepthPercent.toString(),
                ),
            )
            AppLogger.d { "Tracked: summary_scroll_depth, depth=$maxScrollDepthPercent%" }
        }
    }

    private fun loadPortrait() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            _uiState.value = SummaryUiState.LocationResolving
            val startMs = System.currentTimeMillis()

            val areaName = resolveLocation()
            if (areaName == null) return@launch
            AppLogger.d { "Summary: location resolved in ${System.currentTimeMillis() - startMs}ms" }

            resolvedAreaName = areaName
            val context = areaContextFactory.create()

            try {
                getAreaPortrait(areaName, context)
                    .collect { update ->
                        val newState = stateMapper.processUpdate(
                            _uiState.value,
                            update,
                            areaName,
                        )
                        _uiState.value = newState
                        if (newState is SummaryUiState.Complete) {
                            AppLogger.d { "Summary: complete in ${System.currentTimeMillis() - startMs}ms" }
                            analyticsTracker.trackEvent(
                                "summary_viewed",
                                mapOf(
                                    "source" to "gps",
                                    "area_name" to areaName,
                                ),
                            )
                            AppLogger.d { "Tracked: summary_viewed, source=gps, area=$areaName" }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Portrait streaming failed" }
                _uiState.value = SummaryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun resolveLocation(): String? {
        val result = privacyPipeline.resolveAreaName()

        return result.getOrElse { error ->
            AppLogger.e(error) { "Location resolution failed" }
            _uiState.value = SummaryUiState.LocationFailed(LOCATION_FAILURE_MESSAGE)
            null
        }
    }

    companion object {
        internal const val LOCATION_FAILURE_MESSAGE =
            "Can't find your location. Search an area instead?"
    }
}
