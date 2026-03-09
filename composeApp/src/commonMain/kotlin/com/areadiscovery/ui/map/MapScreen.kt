package com.areadiscovery.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.areadiscovery.domain.model.SavedPoi
import com.areadiscovery.ui.components.AlertBanner
import com.areadiscovery.ui.components.ContentNoteBanner
import com.areadiscovery.ui.components.PlatformBackHandler
import com.areadiscovery.ui.map.components.AISearchBar
import com.areadiscovery.ui.map.components.ExpandablePoiCard
import com.areadiscovery.ui.map.components.GeocodingSearchBar
import com.areadiscovery.ui.map.components.FabMenu
import com.areadiscovery.ui.map.components.MapListToggle
import com.areadiscovery.ui.map.components.FabScrim
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
            val chatViewModel: ChatViewModel = koinViewModel()
            val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
            ReadyContent(state, viewModel, chatViewModel, chatState, onNavigateToMaps)
        }
    }
}

@Composable
private fun ReadyContent(
    state: MapUiState.Ready,
    viewModel: MapViewModel,
    chatViewModel: ChatViewModel,
    chatState: ChatUiState,
    onNavigateToMaps: (Double, Double, String) -> Boolean,
) {
    // TODO(BACKLOG-LOW): snackbarHostState created inside Ready branch — resets on Ready→Failed→Ready retry
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        // Base: map or list
        if (state.showListView) {
            POIListView(
                pois = state.pois,
                activeVibe = state.activeVibe,
                onVibeSelected = { viewModel.switchVibe(it) },
                onPoiClick = { viewModel.selectPoi(it) },
                modifier = Modifier
                    .fillMaxSize()
                    // TODO(BACKLOG-LOW): Magic number 112.dp for POI list top padding — should be named or derived
                    .padding(top = statusBarPadding + 112.dp),
            )
        } else {
            // TODO(BACKLOG-MEDIUM): Add +/- zoom control buttons as Compose overlays — MapLibre 11.x removed built-in zoom controls
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
                .padding(top = statusBarPadding + 8.dp),
        )

        // Geocoding search bar (always visible, replaces Refresh Area button)
        // statusBarPadding + 56dp = status bar + TopContextBar height + top inset padding
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
            recentPlaces = state.recentPlaces,
            onRecentSelected = { viewModel.onRecentSelected(it) },
            onClearRecents = { viewModel.onClearRecents() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarPadding + 56.dp)
                .fillMaxWidth(),
        )

        // Vibe rail (right side, bottom-aligned above FAB) — map mode only
        if (!state.showListView) {
            VibeRail(
                activeVibe = state.activeVibe,
                vibePoiCounts = state.vibePoiCounts,
                onVibeSelected = { viewModel.switchVibe(it) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = navBarPadding + 88.dp),
            )
        }

        // Back button: dismiss POI card > FAB (priority order)
        PlatformBackHandler(enabled = state.selectedPoi != null) {
            viewModel.clearPoiSelection()
        }
        PlatformBackHandler(enabled = state.selectedPoi == null && state.isFabExpanded) {
            viewModel.toggleFab()
        }

        // POI card scrim (tap outside to dismiss)
        if (state.selectedPoi != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { viewModel.clearPoiSelection() },
            )
        }

        // TODO(BACKLOG-LOW): Three-stop bottom sheet deferred — V1 uses two stops. Requires anchoredDraggable with custom snap points.
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
                    if (chatState.isStreaming) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("AI is still responding...")
                        }
                    } else {
                        chatViewModel.openChat(state.areaName, state.pois, state.activeVibe)
                        chatViewModel.sendMessage(query)
                    }
                },
                // TODO(BACKLOG-MEDIUM): Wire ExpandablePoiCard save to SavedPoiRepository (same as chat POI cards)
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
                viewModel.openSavesSheet()
            },
            savedCount = state.savedPoiCount,
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
            visible = state.showMyLocation && !state.isSearchingArea && !chatState.isOpen,
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
            visible = true,
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

        // AI search bar — tapping opens the ChatOverlay directly
        AISearchBar(
            onTap = { chatViewModel.openChat(state.areaName, state.pois, state.activeVibe) },
            chatIsOpen = chatState.isOpen,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = navBarPadding + 16.dp, end = 168.dp),
        )

        // Chat overlay
        if (chatState.isOpen) {
            ChatOverlay(
                viewModel = chatViewModel,
                chatState = chatState,
                onDismiss = { chatViewModel.closeChat() },
                onNavigateToMaps = onNavigateToMaps,
                onDirectionsFailed = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("No maps app available")
                    }
                },
            )
        }

        // Saves bottom sheet
        if (state.showSavesSheet) {
            SavesBottomSheet(
                savedPois = state.savedPois,
                onDismiss = { viewModel.closeSavesSheet() },
            )
        }

        // Back handler for saves sheet — before chat handler (lower priority)
        PlatformBackHandler(enabled = state.showSavesSheet) {
            viewModel.closeSavesSheet()
        }

        // Must be LAST PlatformBackHandler in ReadyContent — last-composed = highest priority
        PlatformBackHandler(enabled = chatState.isOpen) {
            chatViewModel.closeChat()
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

// TODO(BACKLOG-MEDIUM): Add swipe-to-delete or unsave button for each row in SavesBottomSheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavesBottomSheet(
    savedPois: List<SavedPoi>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            "Saved Places",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (savedPois.isEmpty()) {
            Text(
                "No saved places yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(savedPois, key = { it.id }) { poi ->
                    SavedPoiRow(poi)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SavedPoiRow(poi: SavedPoi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                poi.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${poi.areaName} · ${poi.type}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
