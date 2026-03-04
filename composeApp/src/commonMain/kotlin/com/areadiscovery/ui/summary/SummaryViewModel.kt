package com.areadiscovery.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SummaryViewModel(
    private val provider: AreaIntelligenceProvider,
    private val stateMapper: SummaryStateMapper,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private var collectionJob: Job? = null

    private val mockContext = AreaContext(
        timeOfDay = "morning",
        dayOfWeek = "Wednesday",
        visitCount = 0,
        preferredLanguage = "en",
    )

    init {
        loadPortrait()
    }

    fun refresh() {
        _uiState.value = SummaryUiState.Loading
        loadPortrait()
    }

    private fun loadPortrait() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            try {
                provider.streamAreaPortrait("Alfama, Lisbon", mockContext)
                    .collect { update ->
                        _uiState.value = stateMapper.processUpdate(
                            _uiState.value,
                            update,
                            "Alfama, Lisbon",
                        )
                        if (update is BucketUpdate.PortraitComplete) {
                            analyticsTracker.trackEvent(
                                "summary_viewed",
                                mapOf("source" to "mock"),
                            )
                            AppLogger.d { "Tracked: summary_viewed, source=mock" }
                        }
                    }
            } catch (e: Exception) {
                AppLogger.e(e) { "Portrait streaming failed" }
                _uiState.value = SummaryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
