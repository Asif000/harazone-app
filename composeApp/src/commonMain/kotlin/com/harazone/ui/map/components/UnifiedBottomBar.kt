package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.CompanionNudge
import com.harazone.ui.theme.MapFloatingUiDark
import com.harazone.ui.theme.Spacing

private val TealTint = Color(0xFF2BBCB3)

/**
 * Unified bottom bar replacing CompanionOrb, CompanionCard, AISearchBar,
 * MapListToggle, and SavesNearbyPill. (D14, D15, D16, D17)
 *
 * Layout: [☰] [▤/🗺] [🌟 orb bar ........... 🎤]
 *
 * Overlay state (isHamburgerOpen, isPeekOpen) is hoisted to the caller
 * so PlatformBackHandler can be placed at the correct composition priority.
 */
@Composable
fun UnifiedBottomBar(
    // View toggle
    showListView: Boolean,
    onToggleListView: () -> Unit,
    // Orb bar state inputs
    areaName: String,
    companionNudge: CompanionNudge?,
    isCompanionPulsing: Boolean,
    isRemoteExploring: Boolean,
    isStreaming: Boolean,
    activeVibeFilters: Set<String>,
    // Auto-show nudge from queue
    onShowCompanionCard: () -> Unit,
    // Orb actions
    onOrbTap: () -> Unit,
    onTextTap: () -> Unit,
    // Nudge actions
    onNudgeTellMeMore: () -> Unit,
    onNudgeDismiss: () -> Unit,
    onSwipeUpToChat: () -> Unit,
    // Hamburger actions
    onProfile: () -> Unit,
    onAIPersonality: () -> Unit,
    onSettings: () -> Unit,
    onFeedback: () -> Unit,
    // Hoisted overlay state (managed by caller for PlatformBackHandler priority)
    isHamburgerOpen: Boolean,
    onHamburgerOpenChanged: (Boolean) -> Unit,
    isPeekOpen: Boolean,
    onPeekOpenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-show nudges from queue: when pulsing but no nudge displayed,
    // call showCompanionCard() to pop the next nudge into view (D15)
    LaunchedEffect(isCompanionPulsing, companionNudge) {
        if (isCompanionPulsing && companionNudge == null) {
            onShowCompanionCard()
        }
    }

    // Close peek card when nudge is dismissed externally
    LaunchedEffect(companionNudge) {
        if (companionNudge == null) onPeekOpenChanged(false)
    }

    // Derive orb state
    val orbState = when {
        companionNudge != null -> OrbBarState.NUDGE
        isRemoteExploring -> OrbBarState.TELEPORTED
        isStreaming -> OrbBarState.STREAMING
        activeVibeFilters.isNotEmpty() -> OrbBarState.ACTIVE_VIBE_FILTER
        else -> OrbBarState.IDLE
    }

    val activeVibeFilterName = activeVibeFilters.firstOrNull() ?: ""
    // TODO(BACKLOG-LOW): L2 — spec M9 prescribes WindowInsets.safeDrawingBottom instead of navigationBars
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        // Bottom bar row — rendered first so overlays draw on top
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MapFloatingUiDark.copy(alpha = 0.95f))
                .padding(bottom = navBarPadding)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            // ☰ Hamburger / ✕ close (min 48dp touch target)
            IconButton(
                onClick = {
                    if (isHamburgerOpen) {
                        onHamburgerOpenChanged(false)
                    } else {
                        // Mutual exclusion: dismiss peek card
                        if (isPeekOpen) {
                            onPeekOpenChanged(false)
                            onNudgeDismiss()
                        }
                        onHamburgerOpenChanged(true)
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isHamburgerOpen) Icons.Default.Close else Icons.Default.MoreVert,
                    contentDescription = if (isHamburgerOpen) "Close menu" else "Open menu",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            // ▤/🗺 View toggle (min 48dp touch target)
            IconButton(
                onClick = onToggleListView,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (showListView) Icons.Default.Map else Icons.AutoMirrored.Default.List,
                    contentDescription = if (showListView) "Switch to map view" else "Switch to list view",
                    tint = if (showListView) TealTint else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            // 🌟 Orb bar (expands to fill remaining space)
            OrbBar(
                areaName = areaName,
                companionNudge = companionNudge,
                orbState = orbState,
                activeVibeFilterName = activeVibeFilterName,
                onOrbTap = onOrbTap,
                onTextTap = onTextTap,
                onNudgeTextTap = {
                    // Mutual exclusion: dismiss hamburger
                    onHamburgerOpenChanged(false)
                    onPeekOpenChanged(true)
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Overlays — rendered AFTER bar row so they draw on top
        HamburgerMenu(
            visible = isHamburgerOpen,
            onDismiss = { onHamburgerOpenChanged(false) },
            onProfile = onProfile,
            onAIPersonality = onAIPersonality,
            onSettings = onSettings,
            onFeedback = onFeedback,
            bottomBarHeight = Spacing.bottomBarHeight + navBarPadding,
        )

        if (companionNudge != null) {
            PeekCard(
                nudge = companionNudge,
                visible = isPeekOpen,
                onTellMeMore = {
                    onPeekOpenChanged(false)
                    onNudgeTellMeMore()
                },
                onDismiss = {
                    onPeekOpenChanged(false)
                    onNudgeDismiss()
                },
                onSwipeUp = {
                    onPeekOpenChanged(false)
                    onSwipeUpToChat()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = Spacing.bottomBarHeight + navBarPadding, end = 8.dp),
            )
        }
    }
}
