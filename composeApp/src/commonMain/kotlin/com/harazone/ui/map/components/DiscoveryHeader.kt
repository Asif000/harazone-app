package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.GeocodingSuggestion
import com.harazone.domain.model.MetaLine
import com.harazone.domain.model.RecentPlace
import com.harazone.domain.model.WeatherState
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.ui.theme.MapFloatingUiDark

/**
 * Unified Discovery Header — replaces TopContextBar, GeocodingSearchBar,
 * AmbientTicker, SearchSurpriseTogglePill, and VibeRail.
 *
 * Collapsed state: ~52dp bar with area name, safety dot, rotating meta,
 * count pill, 🎲 surprise, chevron.
 *
 * Expanded state: full panel with search, context grid, vibe chips,
 * intel strip, action buttons, recent explorations.
 */
@Composable
fun DiscoveryHeader(
    // Area state
    areaName: String,
    advisoryLevel: AdvisoryLevel?,
    isSearchingArea: Boolean,
    isGpsAcquiring: Boolean,
    isGeocodePending: Boolean,
    isLocationDenied: Boolean,
    // Counts
    discoveredCount: Int,
    savedCount: Int,
    // Pan state
    showDiscoverButton: Boolean,
    onDiscover: () -> Unit,
    // Surprise
    surpriseEnabled: Boolean,
    onSurprise: () -> Unit,
    // Saved lens
    onSavedLensTap: () -> Unit,
    savedLensActive: Boolean,
    // Cancel
    showCancel: Boolean,
    onCancel: () -> Unit,
    // Meta
    metaLines: List<MetaLine>,
    // Search (expanded panel)
    searchQuery: String,
    searchSuggestions: List<GeocodingSuggestion>,
    isGeocodingLoading: Boolean,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (GeocodingSuggestion) -> Unit,
    onSubmitEmpty: () -> Unit,
    onSearchClear: () -> Unit,
    recentPlaces: List<RecentPlace>,
    onRecentSelected: (RecentPlace) -> Unit,
    onClearRecents: () -> Unit,
    // Context (expanded panel)
    weather: WeatherState?,
    visitTag: String,
    // Vibes (expanded panel)
    vibes: List<DynamicVibe>,
    activeVibeFilters: Set<String>,
    adHocFilters: List<String>,
    onVibeToggle: (String) -> Unit,
    onAdHocRemove: (String) -> Unit,
    // Intel strip (expanded panel)
    areaHighlights: List<String>,
    onIntelTapped: (String) -> Unit,
    // Actions (expanded panel)
    onRefresh: () -> Unit,
    // Recent explorations (expanded panel)
    recentExplorations: List<RecentPlace>,
    onTeleport: (RecentPlace) -> Unit,
    // Callbacks
    onHeaderExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    fun collapse() {
        isExpanded = false
        onHeaderExpandedChanged(false)
    }

    fun expand() {
        isExpanded = true
        onHeaderExpandedChanged(true)
    }

    // Back handler for expanded state
    PlatformBackHandler(enabled = isExpanded) { collapse() }

    Box(modifier = modifier.zIndex(2f)) {
        // Scrim — behind panel content, consumes all pointer events to block MapLibre
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { change ->
                                    if (!change.isConsumed) {
                                        change.consume()
                                    }
                                }
                                // Tap on scrim collapses
                                if (event.changes.any { it.pressed && !it.previousPressed }) {
                                    collapse()
                                }
                            }
                        }
                    },
            )
        }

        // Panel content — above scrim in z-order
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .zIndex(2f),
        ) {
            // Collapsed bar — always visible
            CollapsedBar(
                areaName = areaName,
                advisoryLevel = advisoryLevel,
                isSearchingArea = isSearchingArea,
                isGpsAcquiring = isGpsAcquiring,
                isGeocodePending = isGeocodePending,
                isLocationDenied = isLocationDenied,
                discoveredCount = discoveredCount,
                savedCount = savedCount,
                showDiscoverButton = showDiscoverButton,
                onDiscover = onDiscover,
                surpriseEnabled = surpriseEnabled,
                onSurprise = onSurprise,
                onSavedLensTap = onSavedLensTap,
                savedLensActive = savedLensActive,
                showCancel = showCancel,
                onCancel = onCancel,
                metaLines = metaLines,
                isExpanded = isExpanded,
                onTap = { if (isExpanded) collapse() else expand() },
            )

            // Expanded panel
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                ) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SmartSearchPanel(
                    query = searchQuery,
                    suggestions = searchSuggestions,
                    isGeocodingLoading = isGeocodingLoading,
                    onQueryChanged = onQueryChanged,
                    onSuggestionSelected = { suggestion ->
                        collapse()
                        onSuggestionSelected(suggestion)
                    },
                    onSubmitEmpty = {
                        collapse()
                        onSubmitEmpty()
                    },
                    onClear = onSearchClear,
                    recentPlaces = recentPlaces,
                    onRecentSelected = { recent ->
                        collapse()
                        onRecentSelected(recent)
                    },
                    onClearRecents = onClearRecents,
                    weather = weather,
                    visitTag = visitTag,
                    vibes = vibes,
                    activeVibeFilters = activeVibeFilters,
                    adHocFilters = adHocFilters,
                    onVibeToggle = onVibeToggle,
                    onAdHocRemove = onAdHocRemove,
                    areaHighlights = areaHighlights,
                    onIntelTapped = { fact ->
                        collapse()
                        onIntelTapped(fact)
                    },
                    onSurprise = {
                        collapse()
                        onSurprise()
                    },
                    onRefresh = {
                        collapse()
                        onRefresh()
                    },
                    recentExplorations = recentExplorations,
                    onTeleport = { recent ->
                        collapse()
                        onTeleport(recent)
                    },
                )
            }
        }

    }
}

@Composable
private fun CollapsedBar(
    areaName: String,
    advisoryLevel: AdvisoryLevel?,
    isSearchingArea: Boolean,
    isGpsAcquiring: Boolean,
    isGeocodePending: Boolean,
    isLocationDenied: Boolean,
    discoveredCount: Int,
    savedCount: Int,
    showDiscoverButton: Boolean,
    onDiscover: () -> Unit,
    surpriseEnabled: Boolean,
    onSurprise: () -> Unit,
    onSavedLensTap: () -> Unit,
    savedLensActive: Boolean,
    showCancel: Boolean,
    onCancel: () -> Unit,
    metaLines: List<MetaLine>,
    isExpanded: Boolean,
    onTap: () -> Unit,
) {
    Surface(
        color = MapFloatingUiDark.copy(alpha = 0.85f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(52.dp),
    ) {
        when {
            // Spinning / loading state
            isSearchingArea -> SpinningRow(
                areaName = areaName,
                showCancel = showCancel,
                onCancel = onCancel,
                activeVibeFilters = emptySet(), // TODO: pass through
                onTap = onTap,
            )
            else -> NormalRow(
                areaName = areaName,
                advisoryLevel = advisoryLevel,
                isGpsAcquiring = isGpsAcquiring,
                isGeocodePending = isGeocodePending,
                isLocationDenied = isLocationDenied,
                discoveredCount = discoveredCount,
                savedCount = savedCount,
                showDiscoverButton = showDiscoverButton,
                onDiscover = onDiscover,
                surpriseEnabled = surpriseEnabled,
                onSurprise = onSurprise,
                onSavedLensTap = onSavedLensTap,
                savedLensActive = savedLensActive,
                metaLines = metaLines,
                isExpanded = isExpanded,
                onTap = onTap,
            )
        }
    }
}

@Composable
private fun NormalRow(
    areaName: String,
    advisoryLevel: AdvisoryLevel?,
    isGpsAcquiring: Boolean,
    isGeocodePending: Boolean,
    isLocationDenied: Boolean,
    discoveredCount: Int,
    savedCount: Int,
    showDiscoverButton: Boolean,
    onDiscover: () -> Unit,
    surpriseEnabled: Boolean,
    onSurprise: () -> Unit,
    onSavedLensTap: () -> Unit,
    savedLensActive: Boolean,
    metaLines: List<MetaLine>,
    isExpanded: Boolean,
    onTap: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp),
    ) {
        // Area name + safety dot
        when {
            isGpsAcquiring -> {
                Text(
                    text = "Locating...",
                    style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
            isGeocodePending -> {
                ShimmerPlaceholder()
            }
            isLocationDenied -> {
                Text(
                    text = "Search to explore",
                    style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
            else -> {
                Text(
                    text = areaName.ifBlank { "Unknown Area" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (areaName.isBlank()) Color.White.copy(alpha = 0.5f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
                if (!isGpsAcquiring && !isGeocodePending && !isLocationDenied) {
                    SafetyDot(advisoryLevel = advisoryLevel)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Rotating meta line
        RotatingMetaTicker(
            metaLines = metaLines,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(6.dp))

        // Count pill OR Discover button
        when {
            savedLensActive -> {
                // Pill hidden in saved lens mode
            }
            showDiscoverButton -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF26A69A))
                        .clickable(onClick = onDiscover),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Discover this area",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            !isGpsAcquiring && !isGeocodePending && !isLocationDenied -> {
                CountPill(
                    discoveredCount = discoveredCount,
                    savedCount = savedCount,
                    onSavedTap = onSavedLensTap,
                )
            }
        }

        // 🎲 Surprise button
        SurpriseButton(
            enabled = surpriseEnabled && !isGpsAcquiring && !isGeocodePending && !isLocationDenied,
            onClick = onSurprise,
        )

        // Chevron removed — entire bar is tappable for expand/collapse
    }
}

@Composable
private fun SpinningRow(
    areaName: String,
    showCancel: Boolean,
    onCancel: () -> Unit,
    activeVibeFilters: Set<String>,
    onTap: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.padding(end = 8.dp).height(14.dp).width(14.dp),
            color = Color.White,
            strokeWidth = 2.dp,
        )
        Text(
            text = "Discovering $areaName...",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (activeVibeFilters.isNotEmpty()) {
            Text(
                text = "\uD83C\uDFAD ${activeVibeFilters.first()}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB39DDB),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (showCancel) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ShimmerPlaceholder() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            tween(800),
            RepeatMode.Reverse,
        ),
        label = "shimmer_alpha",
    )
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = alpha)),
    )
}
