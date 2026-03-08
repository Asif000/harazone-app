package com.areadiscovery.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areadiscovery.ui.components.AlertBanner
import com.areadiscovery.ui.components.ContentNoteBanner
import com.areadiscovery.ui.map.components.AISearchBar
import com.areadiscovery.ui.map.components.ExpandablePoiCard
import com.areadiscovery.ui.map.components.GeocodingSearchBar
import com.areadiscovery.ui.map.components.FabMenu
import com.areadiscovery.ui.map.components.MapListToggle
import com.areadiscovery.ui.map.components.FabScrim
import com.areadiscovery.ui.map.components.SearchOverlay
import com.areadiscovery.ui.map.components.TopContextBar
import com.areadiscovery.ui.map.components.VibeRail
import com.areadiscovery.ui.theme.MapFloatingUiDark
import com.areadiscovery.ui.theme.spacing
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Boolean = { _, _, _ -> false },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is MapUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is MapUiState.LocationFailed -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ContentNoteBanner(message = state.message)
                Spacer(Modifier.height(MaterialTheme.spacing.md))
                Button(onClick = { viewModel.retry() }) {
                    Text("Retry")
                }
            }
        }

        is MapUiState.Ready -> {
            ReadyContent(state, viewModel, onNavigateToMaps)
        }
    }
}

@Composable
private fun ReadyContent(
    state: MapUiState.Ready,
    viewModel: MapViewModel,
    onNavigateToMaps: (Double, Double, String) -> Boolean,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        // Base: map or list
        if (state.showListView) {
            POIListView(
                pois = state.pois,
                activeVibe = state.activeVibe,
                onVibeSelected = { viewModel.switchVibe(it) },
                onPoiClick = { viewModel.selectPoi(it) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MapComposable(
                modifier = Modifier.fillMaxSize(),
                latitude = state.latitude,
                longitude = state.longitude,
                zoomLevel = 14.0,
                cameraMoveId = state.cameraMoveId,
                pois = state.pois,
                activeVibe = state.activeVibe,
                onPoiSelected = { poi -> viewModel.selectPoi(poi) },
                onMapRenderFailed = { viewModel.onMapRenderFailed() },
                onCameraIdle = { lat, lng -> viewModel.onCameraIdle(lat, lng) },
            )
        }

        // Alert banner for map render failure
        if (state.mapRenderFailed && state.showListView) {
            AlertBanner(
                message = "Map unavailable. Showing list view.",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            )
        }

        // Top context bar
        TopContextBar(
            areaName = state.areaName,
            visitTag = state.visitTag,
            weather = state.weather,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )

        // Geocoding search bar (always visible, replaces Refresh Area button)
        // 56dp = TopContextBar height + top inset padding
        GeocodingSearchBar(
            query = state.geocodingQuery,
            suggestions = state.geocodingSuggestions,
            isGeocodingLoading = state.isGeocodingLoading,
            selectedPlace = state.geocodingSelectedPlace,
            isSearchingArea = state.isSearchingArea,
            isGeocodingInitiatedSearch = state.isGeocodingInitiatedSearch,
            onQueryChanged = { viewModel.onGeocodingQueryChanged(it) },
            onSuggestionSelected = { viewModel.onGeocodingSuggestionSelected(it) },
            onSubmitEmpty = { viewModel.onGeocodingSubmitEmpty() },
            onClear = { viewModel.onGeocodingCleared() },
            onCancelLoad = { viewModel.onGeocodingCancelLoad() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .fillMaxWidth(),
        )

        // Vibe rail (right side, bottom-aligned above FAB)
        VibeRail(
            activeVibe = state.activeVibe,
            vibePoiCounts = state.vibePoiCounts,
            onVibeSelected = { viewModel.switchVibe(it) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = navBarPadding + 88.dp),
        )

        // Expandable POI card
        if (state.selectedPoi != null) {
            ExpandablePoiCard(
                poi = state.selectedPoi,
                activeVibe = state.activeVibe,
                onDismiss = { viewModel.clearPoiSelection() },
                onDirectionsClick = { lat, lon, name ->
                    val handled = onNavigateToMaps(lat, lon, name)
                    if (!handled) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("No maps app available")
                        }
                    }
                },
                onAskAiClick = { query ->
                    viewModel.clearPoiSelection()
                    viewModel.openSearchOverlay()
                    viewModel.submitSearch(query)
                },
                onSaveClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Bookmarks coming soon")
                    }
                },
                onShareClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Sharing coming soon")
                    }
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // FAB scrim
        FabScrim(
            visible = state.isFabExpanded,
            onClick = { viewModel.toggleFab() },
        )

        // FAB menu
        FabMenu(
            isExpanded = state.isFabExpanded,
            onToggle = { viewModel.toggleFab() },
            onSavedPlaces = {
                viewModel.toggleFab()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Coming soon")
                }
            },
            onSettings = {
                viewModel.toggleFab()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Coming soon")
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = navBarPadding + 16.dp, end = 16.dp),
        )

        // MyLocation button (Position C — left side, above AI bar)
        AnimatedVisibility(
            visible = state.showMyLocation && !state.isSearchingArea && !state.isSearchOverlayOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = navBarPadding + 84.dp),
        ) {
            Surface(
                onClick = { viewModel.returnToCurrentLocation() },
                shape = RoundedCornerShape(50),
                color = MapFloatingUiDark.copy(alpha = 0.92f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Return to my location",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // Map/List toggle
        AnimatedVisibility(
            visible = !state.isSearchOverlayOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = navBarPadding + 16.dp, end = 80.dp),
        ) {
            MapListToggle(
                showListView = state.showListView,
                onToggle = { viewModel.toggleListView() },
            )
        }

        // AI search bar
        AISearchBar(
            onTap = { viewModel.openSearchOverlay() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = navBarPadding + 16.dp, end = 168.dp),
        )

        // Search overlay
        if (state.isSearchOverlayOpen) {
            SearchOverlay(
                query = state.searchQuery,
                aiResponse = state.aiResponse,
                isAiResponding = state.isAiResponding,
                followUpChips = state.followUpChips,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSubmit = { viewModel.submitSearch(it) },
                onDismiss = { viewModel.closeSearchOverlay() },
            )
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }
}
