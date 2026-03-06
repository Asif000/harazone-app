package com.areadiscovery.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.domain.service.AreaContextFactory
import com.areadiscovery.domain.usecase.SearchAreaUseCase
import com.areadiscovery.ui.summary.SummaryStateMapper
import com.areadiscovery.ui.summary.SummaryUiState
import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.AppClock
import com.areadiscovery.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class SearchViewModel(
    private val searchAreaUseCase: SearchAreaUseCase,
    private val areaContextFactory: AreaContextFactory,
    private val stateMapper: SummaryStateMapper,
    private val analyticsTracker: AnalyticsTracker,
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecentSearches()
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return

        searchJob?.cancel()
        _uiState.value = SearchUiState.Loading(query = trimmedQuery)
        searchJob = viewModelScope.launch {
            persistSearch(trimmedQuery)

            val context = areaContextFactory.create()
            var summaryState: SummaryUiState = SummaryUiState.Loading

            try {
                searchAreaUseCase(trimmedQuery, context).collect { update ->
                    summaryState = stateMapper.processUpdate(summaryState, update, trimmedQuery)
                    _uiState.value = summaryState.toSearchUiState(trimmedQuery)

                    if (summaryState is SummaryUiState.Complete) {
                        analyticsTracker.trackEvent(
                            "summary_viewed",
                            mapOf("source" to "search", "area_name" to trimmedQuery),
                        )
                        AppLogger.d { "Tracked: summary_viewed, source=search, area=$trimmedQuery" }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Search portrait streaming failed" }
                _uiState.value = SearchUiState.Error(
                    query = trimmedQuery,
                    message = e.message ?: "Search failed. Please try again.",
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        loadRecentSearches()
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            val recent = database.search_historyQueries.getRecentSearches().executeAsList()
            _uiState.value = SearchUiState.Idle(recentSearches = recent)
        }
    }

    private suspend fun persistSearch(query: String) {
        database.search_historyQueries.upsertSearch(
            query = query,
            searched_at = clock.nowMs(),
        )
    }

    private fun SummaryUiState.toSearchUiState(query: String): SearchUiState = when (this) {
        is SummaryUiState.Streaming -> SearchUiState.Streaming(
            query = query,
            areaName = areaName,
            buckets = buckets,
        )
        is SummaryUiState.Complete -> SearchUiState.Complete(
            query = query,
            areaName = areaName,
            buckets = buckets,
            pois = pois,
        )
        is SummaryUiState.Error -> SearchUiState.Error(query = query, message = message)
        else -> SearchUiState.Streaming(
            query = query,
            areaName = query,
            buckets = emptyMap(),
        )
    }
}
