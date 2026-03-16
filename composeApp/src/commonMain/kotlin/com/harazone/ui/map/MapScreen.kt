package com.harazone.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harazone.getPlatform
import com.harazone.domain.model.Confidence
import com.harazone.feedback.FeedbackReporter
import com.harazone.feedback.ShakeDetector
import com.harazone.util.AppLogger
import com.harazone.util.ringBufferLogWriter
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.ui.components.AlertBanner
import com.harazone.ui.components.ContentNoteBanner
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.ui.settings.FeedbackPreviewSheet
import com.harazone.ui.settings.SettingsSheet
import com.harazone.ui.map.components.AISearchBar
import com.harazone.ui.map.components.OnboardingBubble
import com.harazone.ui.map.components.AiDetailPage
import com.harazone.ui.map.components.GeocodingSearchBar
import com.harazone.ui.profile.ProfileScreen
import com.harazone.ui.profile.ProfileViewModel
import com.harazone.ui.saved.SavedPlacesScreen
import com.harazone.ui.map.components.FabMenu
import com.harazone.ui.map.components.MapListToggle
import com.harazone.ui.map.components.FabScrim
import com.harazone.ui.map.components.TopContextBar
import com.harazone.ui.map.components.AmbientTicker
import com.harazone.ui.map.components.PoiCarousel
import com.harazone.ui.map.components.VibeRail
import com.harazone.ui.theme.MapFloatingUiDark
import com.harazone.ui.theme.spacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Boolean = { _, _, _ -> false },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh weather when app resumes from background (if stale >5 min)
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshWeatherIfStale()
        onPauseOrDispose { }
    }

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
                    Text(stringResource(Res.string.map_retry))
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
    val noMapsAppMessage = stringResource(Res.string.map_no_maps_app)
    val returnToSaves = remember { booleanArrayOf(false) }
    val returnToChat = remember { booleanArrayOf(false) }
    val returnToProfile = remember { booleanArrayOf(false) }

    // Profile state
    var showProfile by remember { mutableStateOf(false) }

    // Feedback / settings state
    val feedbackReporter: FeedbackReporter = koinInject()
    val shakeDetector: ShakeDetector = koinInject()
    var showSettings by remember { mutableStateOf(false) }
    var showFeedbackPreview by remember { mutableStateOf(false) }
    var feedbackScreenshot by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCapture by remember { mutableStateOf(false) }
    var pendingShakeCapture by remember { mutableStateOf(false) }

    fun dismissAllOverlays() {
        if (state.isFabExpanded) viewModel.toggleFab()
        if (chatState.isOpen) chatViewModel.closeChat()
        if (state.showVisitsSheet) viewModel.closeVisitsSheet()
        if (state.selectedPoi != null) viewModel.clearPoiSelection()
        showProfile = false
        showSettings = false
        showFeedbackPreview = false
    }

    // Settings path capture: wait one frame after SettingsSheet closes
    LaunchedEffect(pendingCapture) {
        if (pendingCapture) {
            kotlinx.coroutines.yield()
            feedbackScreenshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                feedbackReporter.captureScreenshot()
            }
            pendingCapture = false
            showFeedbackPreview = true
        }
    }

    // Shake path capture: 350ms budget for exit animations
    LaunchedEffect(pendingShakeCapture) {
        if (pendingShakeCapture) {
            delay(350)
            feedbackScreenshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                feedbackReporter.captureScreenshot()
            }
            pendingShakeCapture = false
            showFeedbackPreview = true
        }
    }

    // Shake detector wiring
    DisposableEffect(Unit) {
        shakeDetector.start {
            if (!showFeedbackPreview && !pendingShakeCapture) {
                dismissAllOverlays()
                pendingShakeCapture = true
            }
        }
        onDispose { shakeDetector.stop() }
    }

    LaunchedEffect(viewModel) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var screenHeightPx by remember { mutableStateOf(0f) }

    // Measured onboarding target offsets (Task 11)
    var vibeRailOffset by remember { mutableStateOf(Offset.Zero) }
    var savedFabOffset by remember { mutableStateOf(Offset.Zero) }
    var searchBarOffset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { coords ->
        screenHeightPx = coords.size.height.toFloat()
    }) {
        // Base: map or list
        if (state.showListView) {
            POIListView(
                pois = state.pois,
                dynamicVibes = state.dynamicVibes,
                activeDynamicVibe = state.activeDynamicVibe,
                onDynamicVibeSelected = viewModel::switchDynamicVibe,
                onPoiClick = { viewModel.selectPoi(it) },
                onVisitTapped = { poi -> viewModel.visitPoi(poi, state.areaName) },
                onUnvisitTapped = { poi -> viewModel.unvisitPoi(poi) },
                onNavigateTapped = { poi ->
                    if (poi.latitude != null && poi.longitude != null) {
                        val handled = onNavigateToMaps(poi.latitude, poi.longitude, poi.name)
                        if (!handled) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(noMapsAppMessage)
                            }
                        }
                    }
                },
                onChatTapped = { poi ->
                    chatViewModel.openChat(
                        state.areaName, state.allDiscoveredPois, state.activeDynamicVibe,
                        entryPoint = ChatEntryPoint.PoiCard(poi),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarPadding + 112.dp),
                visitedPoiIds = state.visitedPoiIds,
            )
        } else {
            // TODO(BACKLOG-MEDIUM): Add +/- zoom control buttons as Compose overlays — MapLibre 11.x removed built-in zoom controls
            MapComposable(
                modifier = Modifier.fillMaxSize(),
                latitude = state.latitude,
                longitude = state.longitude,
                zoomLevel = 14.0,
                cameraMoveId = state.cameraMoveId,
                pois = if (state.visitedFilter) emptyList() else if (state.showAllMode) state.allDiscoveredPois else state.pois,
                activeVibe = null, // Dynamic vibes — old Vibe enum filtering disabled on map
                onPoiSelected = { poi -> viewModel.selectPoi(poi) },
                onMapRenderFailed = { viewModel.onMapRenderFailed() },
                onCameraIdle = { lat, lng -> viewModel.onCameraIdle(lat, lng) },
                visitedPoiIds = state.visitedPoiIds,
                visitedPois = state.visitedPois,
                visitedFilter = state.visitedFilter,
                onPinTapped = { index -> viewModel.onPinTapped(index) },
                selectedPinIndex = state.selectedPinIndex,
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

        // Enrichment progress bar
        if (state.isEnrichingArea) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarPadding + 72.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }


        // Top context bar
        if (!showProfile) {
        TopContextBar(
            areaName = state.areaName,
            visitTag = state.visitTag,
            weather = state.weather,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarPadding + 8.dp),
        )
        }

        // Geocoding search bar (always visible, replaces Refresh Area button)
        // statusBarPadding + 56dp = status bar + TopContextBar height + top inset padding
        if (!showProfile) GeocodingSearchBar(
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
            showBatchNav = state.poiBatches.size > 1 && !state.isSearchingArea,
            batchIndex = if (state.showAllMode) state.poiBatches.size else state.activeBatchIndex,
            batchTotal = MapViewModel.MAX_BATCH_SLOTS,
            onPrevBatch = { viewModel.onPrevBatch() },
            onNextBatch = { viewModel.onNextBatch() },
            onSearchDeeper = { viewModel.onSearchDeeper() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarPadding + 56.dp)
                .fillMaxWidth()
                .zIndex(1f), // Above AmbientTicker so suggestions aren't blocked
        )

        // Ambient ticker — rotating area intel below search bar
        if (state.pois.isNotEmpty() && !state.showListView && state.selectedPoi == null && !showProfile) {
            AmbientTicker(
                pois = state.pois,
                latitude = state.latitude,
                longitude = state.longitude,
                areaHighlights = state.areaHighlights,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding + 56.dp + 48.dp + 4.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
        }

        // Bottom carousel — snap-scroll POI cards
        if (state.pois.isNotEmpty() && !state.showListView && state.selectedPoi == null && !showProfile) {
            PoiCarousel(
                pois = state.pois,
                selectedIndex = state.selectedPinIndex,
                visitedPoiIds = state.visitedPoiIds,
                onCardSwiped = { index -> viewModel.onCarouselSwiped(index) },
                onSelectionCleared = { viewModel.onCarouselSelectionCleared() },
                onVisitTapped = { poi ->
                    viewModel.visitPoi(poi, state.areaName)
                    viewModel.selectPoiWithImageResolve(poi)
                },
                onDetailTapped = { poi -> viewModel.selectPoiWithImageResolve(poi) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarPadding + 72.dp),
            )
        }

        // Vibe rail (right side, bottom-aligned above FAB) — map mode only
        if (!state.showListView && state.selectedPoi == null && !showProfile) {
            VibeRail(
                vibes = state.dynamicVibes,
                activeDynamicVibe = state.activeDynamicVibe,
                dynamicVibePoiCounts = state.dynamicVibePoiCounts,
                dynamicVibeAreaSaveCounts = state.dynamicVibeAreaSaveCounts,
                savedVibeActive = state.visitedFilter,
                totalAreaSaveCount = state.visitedPois.size,
                isLoadingVibes = state.isLoadingVibes,
                isOfflineVibes = state.isOfflineVibes,
                pinnedVibeLabels = viewModel.pinnedVibeLabels,
                onVibeSelected = { viewModel.switchDynamicVibe(it) },
                onSavedVibeSelected = viewModel::onVisitedFilterSelected,
                onProfileSelected = { showProfile = true },
                onLongPressVibe = { viewModel.togglePin(it) },
                onExploreRetry = { viewModel.retryAreaFetch() },
                showCalloutDot = state.showOnboardingBubble,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = navBarPadding + 88.dp)
                    .onGloballyPositioned { coords ->
                        vibeRailOffset = coords.boundsInRoot().centerLeft
                    },
            )
        }

        // Back button: List view > Show All mode > POI card > FAB (priority order)
        PlatformBackHandler(enabled = state.showListView) { viewModel.toggleListView() }
        PlatformBackHandler(enabled = state.showAllMode && state.selectedPoi == null) {
            viewModel.onPrevBatch()
        }
        PlatformBackHandler(enabled = state.selectedPoi == null && state.visitedFilter) {
            viewModel.onVisitedFilterSelected()
        }
        PlatformBackHandler(enabled = state.selectedPoi == null && !state.visitedFilter && state.isFabExpanded) {
            viewModel.toggleFab()
        }

        // AI Detail Page — replaces ExpandablePoiCard + scrim
        if (state.selectedPoi != null) {
            val dismissDetail: () -> Unit = {
                viewModel.clearPoiSelection()
                if (returnToProfile[0]) {
                    returnToProfile[0] = false
                    chatViewModel.closeChat()
                    showProfile = true
                } else if (returnToSaves[0]) {
                    returnToSaves[0] = false
                    viewModel.openVisitsSheet()
                } else if (!returnToChat[0]) {
                    chatViewModel.closeChat()
                }
                returnToChat[0] = false
            }

            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { dismissDetail() },
            )

            AiDetailPage(
                poi = state.selectedPoi,
                chatViewModel = chatViewModel,
                chatState = chatState,
                areaName = state.areaName,
                allPois = state.allDiscoveredPois,
                activeDynamicVibe = state.activeDynamicVibe,
                isVisited = state.selectedPoi.savedId in state.visitedPoiIds,
                visitState = state.visitedPois.find { it.id == state.selectedPoi.savedId }?.visitState,
                onVisit = {
                    val poi = state.selectedPoi
                    val visitState = viewModel.visitPoi(poi, state.areaName)
                    chatViewModel.sendVisitMessage(poi, visitState)
                },
                onUnvisit = { viewModel.unvisitPoi(state.selectedPoi) },
                onDirectionsClick = { lat, lon, name ->
                    val handled = onNavigateToMaps(lat, lon, name)
                    if (!handled) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(noMapsAppMessage)
                        }
                    }
                },
                onShowOnMap = { lat, lng ->
                    chatViewModel.closeChat()
                    viewModel.clearPoiSelection()
                    returnToChat[0] = false
                    returnToSaves[0] = false
                    viewModel.flyToCoords(lat, lng)
                },
                onDismiss = dismissDetail,
                onNavigateToMaps = onNavigateToMaps,
                onDirectionsFailed = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(noMapsAppMessage)
                    }
                },
                onPoiCardClick = { card ->
                    val enriched = state.allDiscoveredPois.firstOrNull { it.name.equals(card.name, ignoreCase = true) }
                    val fallbackPoi = POI(
                        name = card.name,
                        type = card.type,
                        description = card.whySpecial,
                        confidence = Confidence.HIGH,
                        latitude = card.lat,
                        longitude = card.lng,
                        insight = card.whySpecial,
                        imageUrl = card.imageUrl,
                    )
                    viewModel.selectPoiWithImageResolve(enriched ?: fallbackPoi)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = statusBarPadding + 56.dp,
                        bottom = navBarPadding + 56.dp,
                    ),
            )
        }

        // Saves nearby pill
        // Cross-reference current-area POIs against savedPoiIds for accurate nearby count.
        // NOTE: state.visitedPois contains ALL saves across all areas; the .count filter IS the area-scoping
        // — do not simplify to savedPoiIds.size (that would count saves from every area ever visited).
        val savedNearbyCount = state.allDiscoveredPois.count { it.savedId in state.visitedPoiIds }
        val carouselVisible = state.pois.isNotEmpty() && !state.showListView && state.selectedPoi == null
        AnimatedVisibility(
            visible = savedNearbyCount > 0 && !state.isSearchingArea && !chatState.isOpen && !state.isFabExpanded && !carouselVisible && state.selectedPoi == null && !showProfile,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarPadding + 72.dp),
        ) {
            // TODO(BACKLOG-LOW): upgrade saves nearby to haversine radius — areaName match misses saves when geocoding drifts
            SavesNearbyPill(count = savedNearbyCount)
        }

        // FAB scrim
        FabScrim(
            visible = state.isFabExpanded && !showProfile,
            onClick = { viewModel.toggleFab() },
        )

        // FAB menu
        if (!showProfile) FabMenu(
            isExpanded = state.isFabExpanded,
            onToggle = { viewModel.toggleFab() },
            onVisitedPlaces = {
                viewModel.toggleFab()
                viewModel.openVisitsSheet()
            },
            visitedCount = state.visitedPoiCount,
            onSettings = {
                viewModel.toggleFab()
                showSettings = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = navBarPadding + 16.dp, end = 16.dp)
                .onGloballyPositioned { coords ->
                    savedFabOffset = coords.boundsInRoot().center
                },
        )

        // MyLocation button (Position C — left side, above AI bar)
        AnimatedVisibility(
            visible = state.showMyLocation && !state.isSearchingArea && !chatState.isOpen && state.selectedPoi == null && !showProfile,
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
        if (state.selectedPoi == null && !showProfile) {
            MapListToggle(
                showListView = state.showListView,
                onToggle = { viewModel.toggleListView() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = navBarPadding + 16.dp, end = 80.dp),
            )
        }

        // AI search bar — tapping opens the ChatOverlay directly
        if (state.selectedPoi == null && !showProfile) {
            AISearchBar(
                onTap = { chatViewModel.openChat(state.areaName, state.allDiscoveredPois, state.activeDynamicVibe) },
                chatIsOpen = chatState.isOpen,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = navBarPadding + 16.dp, end = 168.dp)
                    .onGloballyPositioned { coords ->
                        searchBarOffset = coords.boundsInRoot().center
                    },
            )
        }

        // Chat overlay — suppressed when AiDetailPage is open (conflict guard)
        if (chatState.isOpen && state.selectedPoi == null) {
            ChatOverlay(
                viewModel = chatViewModel,
                chatState = chatState,
                onDismiss = { chatViewModel.closeChat() },
                onNavigateToMaps = onNavigateToMaps,
                onDirectionsFailed = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(noMapsAppMessage)
                    }
                },
                onPoiCardClick = { card ->
                    chatViewModel.closeChat()
                    returnToChat[0] = true
                    // Prefer enriched POI from map (has image, rating, vibe) over chat card
                    val enriched = state.allDiscoveredPois.firstOrNull { it.name.equals(card.name, ignoreCase = true) }
                    val fallbackPoi = POI(
                        name = card.name,
                        type = card.type,
                        description = card.whySpecial,
                        confidence = Confidence.HIGH,
                        latitude = card.lat,
                        longitude = card.lng,
                        insight = card.whySpecial,
                        imageUrl = card.imageUrl,
                    )
                    if (enriched != null) {
                        viewModel.selectPoi(enriched)
                    } else {
                        viewModel.selectPoiWithImageResolve(fallbackPoi)
                    }
                },
                onShowOnMap = { card ->
                    chatViewModel.closeChat()
                    viewModel.flyToCoords(card.lat, card.lng)
                },
            )
        }

        // Saved places full-screen overlay
        AnimatedVisibility(
            visible = state.showVisitsSheet,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SavedPlacesScreen(
                userLat = state.gpsLatitude.takeIf { state.showMyLocation },
                userLng = state.gpsLongitude.takeIf { state.showMyLocation },
                onDismiss = { viewModel.closeVisitsSheet() },
                onAskAi = { poi ->
                    viewModel.closeVisitsSheet()
                    if (poi != null) {
                        chatViewModel.openChat(
                            state.areaName, state.allDiscoveredPois, state.activeDynamicVibe,
                            entryPoint = ChatEntryPoint.SavedCard(poi.name),
                        )
                    } else {
                        chatViewModel.openChat(
                            state.areaName, state.allDiscoveredPois, state.activeDynamicVibe,
                            entryPoint = ChatEntryPoint.SavesSheet,
                        )
                    }
                },
                onDirections = { lat, lng, name -> onNavigateToMaps(lat, lng, name) },
                onShare = { /* TODO: platform share intent */ },
                onPoiSelected = { saved ->
                    viewModel.closeVisitsSheet()
                    returnToSaves[0] = true
                    viewModel.selectPoi(
                        POI(
                            name = saved.name,
                            type = saved.type,
                            description = saved.description ?: saved.whySpecial,
                            confidence = Confidence.HIGH,
                            latitude = saved.lat,
                            longitude = saved.lng,
                            vibe = saved.vibe,
                            insight = saved.whySpecial,
                            imageUrl = saved.imageUrl,
                            rating = saved.rating,
                            userNote = saved.userNote,
                        )
                    )
                },
            )
        }

        // Settings sheet (secondary feedback entry point)
        if (showSettings) {
            SettingsSheet(
                onDismiss = { showSettings = false },
                onSendFeedback = {
                    showSettings = false
                    pendingCapture = true
                },
            )
        }

        // Feedback preview (shared by both entry points)
        if (showFeedbackPreview) {
            FeedbackPreviewSheet(
                screenshotBytes = feedbackScreenshot,
                onDismiss = {
                    showFeedbackPreview = false
                    feedbackScreenshot = null
                },
                onSend = { desc ->
                    feedbackReporter.launchEmail(
                        screenshot = feedbackScreenshot,
                        description = desc,
                        deviceInfo = getPlatform().name,
                        logs = ringBufferLogWriter.getLines(),
                    )
                    showFeedbackPreview = false
                    feedbackScreenshot = null
                },
            )
        }

        // Profile full-screen overlay
        if (showProfile) {
            val profileViewModel: ProfileViewModel = koinViewModel()
            ProfileScreen(
                viewModel = profileViewModel,
                onDismiss = { showProfile = false },
                onOpenDetail = { savedPoi, _ ->
                    showProfile = false
                    returnToProfile[0] = true
                    viewModel.selectPoi(profileViewModel.getPoiForDetail(savedPoi))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        PlatformBackHandler(enabled = showProfile) { showProfile = false }

        // Onboarding bubble — first launch only
        OnboardingBubble(
            visible = state.showOnboardingBubble,
            onDismiss = { viewModel.onOnboardingBubbleDismissed() },
            vibeRailOffset = vibeRailOffset,
            savedFabOffset = savedFabOffset,
            searchBarOffset = searchBarOffset,
        )

        PlatformBackHandler(enabled = showSettings) {
            showSettings = false
        }
        PlatformBackHandler(enabled = showFeedbackPreview) {
            showFeedbackPreview = false
            feedbackScreenshot = null
        }

        // Must be LAST PlatformBackHandler in ReadyContent — last-composed = highest priority
        // Suppressed when AiDetailPage is open (AiDetailPage has its own back handler)
        PlatformBackHandler(enabled = chatState.isOpen && state.selectedPoi == null) {
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


@Composable
private fun SavesNearbyPill(count: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1A2A).copy(alpha = 0.92f))
            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color(0xFFFFD700), CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = pluralStringResource(Res.plurals.saved_places_nearby, count, count),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE0E0E0),
        )
    }
}
