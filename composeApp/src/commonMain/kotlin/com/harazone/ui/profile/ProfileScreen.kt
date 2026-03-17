package com.harazone.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harazone.domain.model.SavedPoi
import com.harazone.ui.components.PlatformBackHandler
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

private val BgColor = Color(0xFFF5F2EE)
private val TextPrimary = Color(0xFF2A2A2A)
private val TextSecondary = Color(0xFF6B6B6B)
private val TealAccent = Color(0xFF2E7D32)
private val PurpleAccent = Color(0xFF7C4DFF)
private val ErrorBg = Color(0xFFFFEBEE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit,
    onOpenDetail: (SavedPoi, String) -> Unit,
    onShowSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val profileCloseDesc = stringResource(Res.string.profile_close)
    val profileSendDesc = stringResource(Res.string.profile_send)
    val profileLoadingDesc = stringResource(Res.string.profile_loading)
    val profileRefreshDesc = stringResource(Res.string.profile_refresh_available)
    val profileRetryDesc = stringResource(Res.string.profile_chat_retry)

    PlatformBackHandler(enabled = true) { onDismiss() }

    // Provide localized greeting and pills to VM
    val greetingTemplate = stringResource(Res.string.profile_chat_greeting)
    val greetingFallback = stringResource(Res.string.profile_chat_greeting_fallback)
    val pillBlindspot = stringResource(Res.string.profile_pill_blindspot)
    val pillTryNext = stringResource(Res.string.profile_pill_try_next)
    val pillWhyName = stringResource(Res.string.profile_pill_why_name)
    LaunchedEffect(state.identity, state.isLoading) {
        if (!state.isLoading && state.chatBubbles.isEmpty()) {
            val identity = state.identity
            val greeting = if (identity != null) {
                greetingTemplate
                    .replace("%1\$s", identity.explorerName)
                    .replace("%2\$d", identity.totalVisits.toString())
                    .replace("%3\$d", identity.totalAreas.toString())
            } else {
                greetingFallback
            }
            viewModel.setLocalizedStrings(greeting, listOf(pillBlindspot, pillTryNext, pillWhyName))
        }
    }

    // Auto-scroll to latest bubble
    LaunchedEffect(state.chatBubbles.size, state.isStreaming) {
        if (state.chatBubbles.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(top = statusBarPadding),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header row: gear + close
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onShowSettings,
                    modifier = Modifier.defaultMinSize(48.dp, 48.dp),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .semantics { contentDescription = profileCloseDesc },
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary)
                }
            }

            // Main scrollable content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                // Empty state
                if (state.identity == null && !state.isLoading && state.error == null) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.profile_empty_state),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                    return@LazyColumn
                }

                // Shimmer / Identity strip with crossfade
                item {
                    AnimatedContent(
                        targetState = state.isLoading to state.identity,
                        transitionSpec = { fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) },
                        label = "identity-crossfade",
                    ) { (loading, identity) ->
                        if (loading) {
                            ShimmerIdentityStrip()
                        } else {
                            IdentityStrip(
                                identity = identity,
                                identityRefreshAvailable = state.identityRefreshAvailable,
                                onRefreshTap = { viewModel.applyRefreshedIdentity() },
                                onRetryIdentity = { viewModel.retryIdentity() },
                                error = state.error,
                            )
                        }
                    }
                }

                // Geographic footprint
                if (state.geoFootprint.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        GeoFootprintRow(state.geoFootprint)
                    }
                }

                // Vibe capsules
                if (state.vibeGroups.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        VibeCapsules(
                            vibeGroups = state.vibeGroups,
                            expandedVibe = state.expandedVibe,
                            onToggle = { viewModel.toggleVibe(it) },
                            onPlaceTap = { poi -> onOpenDetail(poi, poi.areaName) },
                        )
                    }
                }

                // Chat section
                item { Spacer(Modifier.height(24.dp)) }

                // Chat bubbles
                items(state.chatBubbles, key = { it.id }) { bubble ->
                    ChatBubbleItem(
                        bubble = bubble,
                        isLatest = bubble.id == state.chatBubbles.lastOrNull()?.id,
                        isStreaming = state.isStreaming,
                        onRetry = { viewModel.retryLastMessage() },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Suggestion pills
                if (state.suggestionPills.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.suggestionPills.forEach { pill ->
                                Surface(
                                    onClick = { viewModel.tapSuggestionPill(pill) },
                                    enabled = !state.isStreaming,
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFE8E5E1),
                                    modifier = Modifier
                                        .defaultMinSize(48.dp, 48.dp)
                                        .alpha(if (state.isStreaming) 0.5f else 1f),
                                ) {
                                    Text(
                                        text = pill,
                                        color = TealAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom spacing for input bar
                item { Spacer(Modifier.height(72.dp)) }
            }

            // Offline banner
            AnimatedVisibility(
                visible = state.isOffline,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    onClick = { viewModel.retryConnection() },
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                ) {
                    Text(
                        text = stringResource(Res.string.profile_offline) + " · " + stringResource(Res.string.profile_offline_retry),
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Input bar
            if (state.identity != null || state.chatBubbles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE0DDD9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .padding(bottom = navBarPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(stringResource(Res.string.profile_chat_placeholder), color = TextSecondary)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = TealAccent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (!state.isStreaming && inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        }),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            if (!state.isStreaming && !state.isOffline && inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = !state.isStreaming && !state.isOffline && inputText.isNotBlank(),
                        modifier = Modifier
                            .defaultMinSize(48.dp, 48.dp)
                            .alpha(if (state.isStreaming || state.isOffline) 0.3f else if (inputText.isBlank()) 0.5f else 1f)
                            .semantics { contentDescription = profileSendDesc },
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = TealAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerIdentityStrip() {
    val loadingDesc = stringResource(Res.string.profile_loading)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .semantics {
                contentDescription = loadingDesc
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar placeholder
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8E5E1)),
        )
        Spacer(Modifier.height(12.dp))
        // Name placeholder
        Box(
            Modifier
                .width(120.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE8E5E1)),
        )
        Spacer(Modifier.height(8.dp))
        // Tagline placeholder
        Box(
            Modifier
                .width(180.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE8E5E1)),
        )
    }
}

@Composable
private fun IdentityStrip(
    identity: com.harazone.domain.model.ProfileIdentity?,
    identityRefreshAvailable: Boolean,
    onRefreshTap: () -> Unit,
    onRetryIdentity: () -> Unit,
    error: String?,
) {
    val refreshDesc = stringResource(Res.string.profile_refresh_available)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(PurpleAccent.copy(alpha = 0.6f), TealAccent.copy(alpha = 0.6f)))
                )
                .semantics { contentDescription = "" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = identity?.avatarEmoji ?: "🌍",
                fontSize = 32.sp,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Explorer name
        Text(
            text = identity?.explorerName ?: "Explorer",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))

        // Tagline
        if (identity?.tagline?.isNotBlank() == true) {
            Text(
                text = identity.tagline,
                color = TealAccent,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Stats row
        if (identity != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                StatItem(value = identity.totalVisits.toString(), label = stringResource(Res.string.profile_visits))
                StatItem(value = identity.totalAreas.toString(), label = stringResource(Res.string.profile_areas))
                StatItem(value = identity.totalVibes.toString(), label = stringResource(Res.string.profile_vibes))
            }
        }

        // Error indicator with retry
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.profile_error),
                color = Color(0xFFD32F2F),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = onRetryIdentity,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                modifier = Modifier.defaultMinSize(48.dp, 48.dp),
            ) {
                Text(
                    text = stringResource(Res.string.profile_chat_retry),
                    color = Color(0xFFD32F2F),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Refresh pill
        AnimatedVisibility(
            visible = identityRefreshAvailable,
            enter = fadeIn() + slideInVertically { -it },
        ) {
            Surface(
                onClick = onRefreshTap,
                shape = RoundedCornerShape(16.dp),
                color = TealAccent.copy(alpha = 0.15f),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .defaultMinSize(48.dp, 48.dp)
                    .semantics { contentDescription = refreshDesc },
            ) {
                Text(
                    text = stringResource(Res.string.profile_refresh_available),
                    color = TealAccent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GeoFootprintRow(geoFootprint: List<GeoEntry>) {
    val geoPillCd = stringResource(Res.string.profile_geo_pill_cd)
    val geoPillHomeCd = stringResource(Res.string.profile_geo_pill_home_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        geoFootprint.forEach { entry ->
            val template = if (entry.isHome) geoPillHomeCd else geoPillCd
            val cdText = template
                .replace("%1\$s", entry.areaName)
                .replace("%2\$d", entry.poiCount.toString())
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFE8E5E1),
                modifier = Modifier
                    .defaultMinSize(48.dp, 48.dp)
                    .then(
                        if (entry.isHome) Modifier.border(1.dp, TealAccent, RoundedCornerShape(16.dp))
                        else Modifier
                    )
                    .semantics { contentDescription = cdText },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.countryFlag, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(entry.areaName, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Text("${entry.poiCount}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VibeCapsules(
    vibeGroups: List<VibeGroup>,
    expandedVibe: String?,
    onToggle: (String) -> Unit,
    onPlaceTap: (SavedPoi) -> Unit,
) {
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vibeGroups.forEach { group ->
                val isExpanded = expandedVibe == group.vibeName
                Surface(
                    onClick = { onToggle(group.vibeName) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isExpanded) PurpleAccent.copy(alpha = 0.2f) else Color(0xFFE8E5E1),
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .then(
                            if (group.isTop) Modifier.border(1.dp, TealAccent, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .semantics {
                            stateDescription = if (isExpanded) "expanded" else "collapsed"
                            role = Role.Button
                        },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(group.vibeName, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(4.dp))
                        Text("${group.poiCount}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Expanded vibe content
        val expandedGroup = vibeGroups.firstOrNull { it.vibeName == expandedVibe }
        AnimatedVisibility(visible = expandedGroup != null) {
            expandedGroup?.let { group ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color(0xFFE0DDD9), RoundedCornerShape(12.dp))
                        .border(1.dp, PurpleAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    if (group.aiInsight.isNotBlank()) {
                        Row {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(40.dp)
                                    .background(PurpleAccent, RoundedCornerShape(2.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                group.aiInsight,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    group.places.forEach { poi ->
                        Surface(
                            onClick = { onPlaceTap(poi) },
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(48.dp, 48.dp),
                        ) {
                            Row(
                                Modifier.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("📍", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(poi.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    Text(poi.areaName, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    bubble: ProfileChatBubble,
    isLatest: Boolean,
    isStreaming: Boolean,
    onRetry: () -> Unit,
) {
    val retryDesc = stringResource(Res.string.profile_chat_retry)
    val bgColor = when {
        bubble.isError -> ErrorBg
        bubble.isUser -> TealAccent.copy(alpha = 0.15f)
        else -> PurpleAccent.copy(alpha = 0.15f)
    }
    val alignment = if (bubble.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val roleDesc = when {
        bubble.isError -> stringResource(Res.string.profile_bubble_error_role)
        bubble.isUser -> stringResource(Res.string.profile_bubble_user_role)
        else -> stringResource(Res.string.profile_bubble_ai_role)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = roleDesc }
            .then(
                if (bubble.isError) {
                    Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                } else if (!bubble.isUser && isLatest && !isStreaming) {
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                } else Modifier
            ),
        contentAlignment = alignment,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(12.dp)
                .then(
                    if (bubble.isUser) Modifier.fillMaxWidth(0.85f)
                    else Modifier.fillMaxWidth(0.9f)
                ),
        ) {
            if (bubble.isError) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = bubble.text,
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFD32F2F).copy(alpha = 0.2f),
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .semantics { contentDescription = retryDesc },
                ) {
                    Text(
                        text = stringResource(Res.string.profile_chat_retry),
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Text(
                    text = bubble.text,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
