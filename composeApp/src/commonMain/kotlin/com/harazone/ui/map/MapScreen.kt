package com.harazone.ui.map

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
import androidx.compose.foundation.layout.width
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
import com.harazone.ui.map.components.OnboardingBubble
import com.harazone.ui.map.components.AiDetailPage
import com.harazone.ui.map.components.DiscoveryHeader
import com.harazone.ui.map.components.UnifiedBottomBar
import com.harazone.ui.profile.ProfileScreen
import com.harazone.ui.profile.ProfileViewModel
import com.harazone.ui.saved.SavedPlacesScreen
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.ui.map.components.SafetyBanner
import com.harazone.ui.map.components.SafetyGateModal
import com.harazone.ui.map.components.PoiCarousel
import com.harazone.ui.theme.MapFloatingUiDark
import com.harazone.ui.theme.Spacing
import com.harazone.ui.theme.spacing
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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

    // Hoisted bottom bar overlay state (AC38 back handler priority)
    var isHamburgerOpen by remember { mutableStateOf(false) }
    var isPeekOpen by remember { mutableStateOf(false) }

    fun dismissAllOverlays() {
        isHamburgerOpen = false
        isPeekOpen = false
        if (state.companionNudge != null) viewModel.dismissCompanionCard()
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

    // Measured onboarding target offset — only searchBarOffset remains (VibeRail/SavedFab replaced by header)
    var searchBarOffset by remember { mutableStateOf(Offset.Zero) }

    // Idle detection for companion — TODO(BACKLOG-MEDIUM): Move to Remote Config (#55)
    var idleTimerKey by remember { mutableLongStateOf(0L) }
    LaunchedEffect(idleTimerKey) {
        delay(MapViewModel.IDLE_THRESHOLD_MS)
        viewModel.onIdleDetected()
    }

    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    idleTimerKey++
                    viewModel.stopAutoSlideshowIfRunning()
                }
            }
        }.onGloballyPositioned { coords ->
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
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = statusBarPadding + 112.dp),
                visitedPoiIds = state.visitedPoiIds,
            )
        } else {
            // TODO(BACKLOG-MEDIUM): Add +/- zoom control buttons as Compose overlays — MapLibre 11.x removed built-in zoom controls
            MapComposable(
                modifier = Modifier.fillMaxSize(),
                latitude = state.latitude,
                longitude = state.longitude,
                zoomLevel = state.cameraZoomLevel,
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
                selectedPinIndex = state.autoSlideshowIndex ?: state.selectedPinIndex,
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


        // Discovery Header — replaces TopContextBar, GeocodingSearchBar,
        // AmbientTicker, SearchSurpriseTogglePill, and VibeRail
        val advisory = state.advisory
        var isHeaderExpanded by remember { mutableStateOf(false) }

        if (state.selectedPoi == null && !showProfile) {
            // Build meta lines from current state
            val weatherText = state.weather?.let { "${it.emoji} ${it.temperatureF}\u00B0F" }
            val timeText = remember(state.weather?.utcOffsetSeconds) {
                val utcOffset = state.weather?.utcOffsetSeconds
                val hour: Int
                val minute: Int
                if (utcOffset != null) {
                    val nowUtcMs = com.harazone.ui.components.currentTimeMillis()
                    val localMs = nowUtcMs + (utcOffset * 1000L)
                    val totalMinutes = (localMs / 60_000) % (24 * 60)
                    hour = (totalMinutes / 60).toInt()
                    minute = (totalMinutes % 60).toInt()
                } else {
                    hour = com.harazone.ui.components.currentHour()
                    minute = com.harazone.ui.components.currentMinute()
                }
                val amPm = if (hour < 12) "AM" else "PM"
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
            }
            val metaLines = remember(
                state.advisory, state.showMyLocation, state.activeVibeFilters,
                state.areaHighlights, weatherText, timeText, state.visitTag, state.isSearchingArea, state.areaName,
            ) {
                com.harazone.domain.model.buildMetaLines(
                    advisoryLevel = state.advisory?.level,
                    advisoryCountryName = state.advisory?.countryName,
                    isRemote = state.showMyLocation && state.gpsLatitude != state.latitude,
                    poiHighlights = state.areaHighlights,
                    weatherText = weatherText,
                    timeText = timeText,
                    visitTag = state.visitTag,
                    isSearching = state.isSearchingArea,
                    areaName = state.areaName,
                    activeVibeFilters = state.activeVibeFilters,
                    vibeMatchCount = state.pois.size,
                    totalPoiCount = state.allDiscoveredPois.size,
                )
            }

            val savedNearbyCount = state.allDiscoveredPois.count { it.savedId in state.visitedPoiIds }

            DiscoveryHeader(
                areaName = state.areaName,
                advisoryLevel = state.advisory?.level,
                isSearchingArea = state.isSearchingArea,
                isGpsAcquiring = state.areaName.isBlank() && !state.showMyLocation,
                // GPS acquired but area name not yet resolved — mutually exclusive with isGpsAcquiring
                isGeocodePending = state.showMyLocation && state.areaName.isBlank(),
                isLocationDenied = false, // TODO(BACKLOG-MEDIUM): Wire when LocationFailed→Ready refactor supports manual-search-only mode (spec H7)
                discoveredCount = state.pois.size,
                savedCount = savedNearbyCount,
                showDiscoverButton = state.showSearchAreaPill && !state.isSearchingArea,
                onDiscover = viewModel::onSearchThisArea,
                surpriseEnabled = state.showSurpriseMe || state.pois.isNotEmpty(),
                onSurprise = viewModel::onSurpriseMe,
                onSavedLensTap = { /* Commit C: saved lens */ },
                savedLensActive = false, // Commit C
                showCancel = state.isSearchingArea && state.isGeocodingInitiatedSearch,
                onCancel = { viewModel.onGeocodingCancelLoad() },
                metaLines = metaLines,
                searchQuery = state.geocodingQuery,
                searchSuggestions = state.geocodingSuggestions,
                isGeocodingLoading = state.isGeocodingLoading,
                onQueryChanged = { viewModel.onGeocodingQueryChanged(it) },
                onSuggestionSelected = { viewModel.onGeocodingSuggestionSelected(it) },
                onSubmitEmpty = { viewModel.onGeocodingSubmitEmpty() },
                onSearchClear = { viewModel.onGeocodingCleared() },
                recentPlaces = state.recentPlaces,
                onRecentSelected = { viewModel.onRecentSelected(it) },
                onClearRecents = { viewModel.onClearRecents() },
                weather = state.weather,
                visitTag = state.visitTag,
                vibes = state.dynamicVibes,
                activeVibeFilters = state.activeVibeFilters,
                adHocFilters = emptyList(), // TODO: ad-hoc filter state
                onVibeToggle = { label ->
                    val vibe = state.dynamicVibes.firstOrNull {
                        it.label.trim().equals(label.trim(), ignoreCase = true)
                    }
                    if (vibe != null) viewModel.switchDynamicVibe(vibe)
                },
                onAdHocRemove = { /* TODO: ad-hoc filter removal */ },
                areaHighlights = state.areaHighlights,
                onIntelTapped = { fact ->
                    chatViewModel.openChat(
                        areaName = state.areaName,
                        pois = state.pois,
                        activeDynamicVibe = state.activeDynamicVibe,
                        entryPoint = ChatEntryPoint.CompanionNudge(fact),
                        forceReset = true,
                    )
                },
                onRefresh = viewModel::onSearchThisArea,
                recentExplorations = state.recentPlaces.take(3),
                onTeleport = { viewModel.onRecentSelected(it) },
                isExpanded = isHeaderExpanded,
                onExpandedChanged = { isHeaderExpanded = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(2f),
            )
        }

        // Safety banner (below Discovery Header)
        val showBanner = advisory != null &&
            advisory.level.isAtLeast(AdvisoryLevel.CAUTION) &&
            !state.isAdvisoryBannerDismissed &&
            !showProfile
        if (!showProfile && advisory != null && advisory.level.isAtLeast(AdvisoryLevel.CAUTION)) {
            SafetyBanner(
                advisory = advisory,
                isVisible = showBanner,
                onDismiss = { viewModel.dismissAdvisoryBanner() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding + 60.dp)
                    .padding(horizontal = 16.dp)
                    .zIndex(1f),
            )
        }

        // Bottom carousel — snap-scroll POI cards
        if (state.pois.isNotEmpty() && !state.showListView && state.selectedPoi == null && !showProfile) {
            PoiCarousel(
                pois = state.pois,
                selectedIndex = state.autoSlideshowIndex ?: state.selectedPinIndex,
                visitedPoiIds = state.visitedPoiIds,
                onCardSwiped = { index ->
                    viewModel.stopAutoSlideshowIfRunning()
                    viewModel.onCarouselSwiped(index)
                },
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

        // VibeRail removed — replaced by vibe chips inside DiscoveryHeader expanded panel

        // Back button: List view > Show All mode > POI card > FAB (priority order)
        PlatformBackHandler(enabled = state.showListView) { viewModel.toggleListView() }
        PlatformBackHandler(enabled = state.showAllMode && state.selectedPoi == null) {
            viewModel.onPrevBatch()
        }
        PlatformBackHandler(enabled = state.selectedPoi == null && state.visitedFilter) {
            viewModel.onVisitedFilterSelected()
        }
        // Header collapse — higher priority than list/showAll/visitedFilter (H2)
        PlatformBackHandler(enabled = isHeaderExpanded) { isHeaderExpanded = false }
        // CompanionNudge back handler moved to UnifiedBottomBar (AC38)

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
                        top = statusBarPadding + Spacing.bottomBarHeight,
                        bottom = navBarPadding + Spacing.bottomBarHeight,
                    ),
            )
        }

        // MyLocation button (Position C — bottom-right, above bottom bar)
        AnimatedVisibility(
            visible = state.showMyLocation && !state.isSearchingArea && !chatState.isOpen && state.selectedPoi == null && !showProfile,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = navBarPadding + 72.dp),
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

        // Unified Bottom Bar — replaces CompanionOrb, CompanionCard, AISearchBar,
        // MapListToggle, and SavesNearbyPill (D14, D15, D16, D17)
        if (state.selectedPoi == null && !showProfile) {
            UnifiedBottomBar(
                showListView = state.showListView,
                onToggleListView = { viewModel.toggleListView() },
                areaName = state.areaName,
                companionNudge = state.companionNudge,
                isCompanionPulsing = state.isCompanionPulsing,
                isRemoteExploring = state.showMyLocation && state.gpsLatitude != state.latitude,
                isStreaming = state.isSearchingArea && state.poiStreamingCount > 0,
                activeVibeFilters = state.activeVibeFilters,
                onShowCompanionCard = { viewModel.showCompanionCard() },
                onOrbTap = {
                    // Orb tap → show companion popup (peek card)
                    viewModel.showCompanionCard()
                    isPeekOpen = true
                },
                onTextTap = {
                    // Text tap → open chat with keyboard for area questions
                    chatViewModel.openChat(
                        state.areaName, state.allDiscoveredPois, state.activeDynamicVibe,
                    )
                },
                onMicTap = {
                    // TODO(#59): STT voice input — for now opens chat
                    chatViewModel.openChat(
                        state.areaName, state.allDiscoveredPois, state.activeDynamicVibe,
                    )
                },
                onNudgeTellMeMore = {
                    val nudge = state.companionNudge
                    viewModel.dismissCompanionCard()
                    if (nudge != null) {
                        chatViewModel.openChat(
                            areaName = state.areaName,
                            pois = state.pois,
                            activeDynamicVibe = state.activeDynamicVibe,
                            entryPoint = ChatEntryPoint.CompanionNudge(nudge.chatContext),
                            forceReset = true,
                        )
                    }
                },
                onNudgeDismiss = { viewModel.dismissCompanionCard() },
                onSwipeUpToChat = {
                    val nudge = state.companionNudge
                    viewModel.dismissCompanionCard()
                    if (nudge != null) {
                        chatViewModel.openChat(
                            areaName = state.areaName,
                            pois = state.pois,
                            activeDynamicVibe = state.activeDynamicVibe,
                            entryPoint = ChatEntryPoint.CompanionNudge(nudge.chatContext),
                            forceReset = true,
                        )
                    }
                },
                onProfile = { showProfile = true },
                onAIPersonality = { /* TODO: AI Personality screen */ },
                onSettings = { showSettings = true },
                onFeedback = {
                    dismissAllOverlays()
                    pendingCapture = true
                },
                isHamburgerOpen = isHamburgerOpen,
                onHamburgerOpenChanged = { open ->
                    // M16: collapse header first when opening hamburger
                    if (open && isHeaderExpanded) {
                        isHeaderExpanded = false
                    } else {
                        isHamburgerOpen = open
                    }
                },
                isPeekOpen = isPeekOpen,
                onPeekOpenChanged = { isPeekOpen = it },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
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
                onShowSettings = {
                    showSettings = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        PlatformBackHandler(enabled = showProfile) { showProfile = false }

        // Onboarding bubble — first launch only
        OnboardingBubble(
            visible = state.showOnboardingBubble,
            onDismiss = { viewModel.onOnboardingBubbleDismissed() },
            searchBarOffset = searchBarOffset,
        )

        PlatformBackHandler(enabled = showSettings) {
            showSettings = false
        }
        PlatformBackHandler(enabled = showFeedbackPreview) {
            showFeedbackPreview = false
            feedbackScreenshot = null
        }

        // Suppressed when AiDetailPage is open (AiDetailPage has its own back handler)
        PlatformBackHandler(enabled = chatState.isOpen && state.selectedPoi == null) {
            chatViewModel.closeChat()
        }

        // Bottom bar overlay back handlers (AC38) — MUST be last-composed for highest priority
        PlatformBackHandler(enabled = isPeekOpen) {
            isPeekOpen = false
            viewModel.dismissCompanionCard()
        }
        PlatformBackHandler(enabled = isHamburgerOpen) {
            isHamburgerOpen = false
        }

        // Safety Gate Modal (Level 4: Do Not Travel)
        if (advisory != null &&
            advisory.level == AdvisoryLevel.DO_NOT_TRAVEL &&
            !state.hasAcknowledgedGate
        ) {
            SafetyGateModal(
                advisory = advisory,
                hasPreviousArea = !state.previousAreaName.isNullOrBlank(),
                onGoBack = { viewModel.goBackToSafety() },
                onContinue = { viewModel.acknowledgeGate() },
            )
        }

        // Resolve safety nudge text in Composable scope
        if (state.hasPendingSafetyNudge && advisory != null) {
            val nudgeText = when (advisory.level) {
                AdvisoryLevel.CAUTION -> stringResource(Res.string.advisory_nudge_caution, advisory.countryName)
                AdvisoryLevel.RECONSIDER -> stringResource(Res.string.advisory_nudge_reconsider, advisory.countryName)
                AdvisoryLevel.DO_NOT_TRAVEL -> stringResource(Res.string.advisory_nudge_danger, advisory.countryName)
                else -> null
            }
            if (nudgeText != null) {
                LaunchedEffect(state.hasPendingSafetyNudge) {
                    viewModel.enqueueSafetyNudge(nudgeText)
                }
            }
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


// SearchSurpriseTogglePill removed — replaced by DiscoveryHeader (Discover button + 🎲 Surprise)
// SavesNearbyPill removed — absorbed into count pill in DiscoveryHeader (Commit B)
// CompanionOrb, CompanionCard, AISearchBar, MapListToggle removed — replaced by UnifiedBottomBar (Commit B)
