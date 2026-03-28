package com.harazone.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.DiscoveryContext
import com.harazone.domain.model.DiscoveryMode
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.POI
import com.harazone.domain.model.ResidentData
import com.harazone.domain.model.Vibe
import com.harazone.domain.model.VisitState
import com.harazone.util.haversineDistanceMeters
import com.harazone.domain.provider.LocaleProvider
import org.koin.compose.koinInject
import com.harazone.ui.map.ChatBubbleItem
import com.harazone.ui.map.ChatInputBar
import com.harazone.ui.map.ChatPoiCard
import com.harazone.ui.map.ChatPoiMiniCard
import com.harazone.ui.map.ChatUiState
import com.harazone.ui.map.ChatViewModel
import com.harazone.ui.map.SkeletonSection
import com.harazone.ui.map.pillDisplayLabel
import com.harazone.ui.theme.toColor
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

private const val DETAIL_PAGE_CHAT_HINT = "Ask about this place..."
// TODO(BACKLOG-LOW): extract DETAIL_PAGE_CHAT_HINT to strings.xml

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiDetailPage(
    poi: POI,
    chatViewModel: ChatViewModel,
    chatState: ChatUiState,
    areaName: String,
    allPois: List<POI>,
    activeDynamicVibe: DynamicVibe?,
    isVisited: Boolean,
    visitState: VisitState?,
    onVisit: () -> Unit,
    onUnvisit: () -> Unit,
    onDirectionsClick: (Double, Double, String) -> Unit,
    onShowOnMap: (lat: Double, lng: Double) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToMaps: (Double, Double, String) -> Boolean,
    onDirectionsFailed: () -> Unit,
    isGhostPin: Boolean = false,
    onDiscoverGhostPin: () -> Unit = {},
    onPoiCardClick: (ChatPoiCard) -> Unit = {},
    discoveryMode: DiscoveryMode = DiscoveryMode.TRAVELER,
    residentData: ResidentData? = null,
    dailyLifePois: List<POI> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var showGallery by remember(poi.savedId) { mutableStateOf(false) }
    val vibeColor = (Vibe.entries.firstOrNull { poi.vibe.contains(it.name, ignoreCase = true) } ?: Vibe.DEFAULT).toColor()
    val localeProvider: LocaleProvider = koinInject()

    val listState = rememberLazyListState()

    // Auto-scroll only after user has manually scrolled down to chat area
    val hasUserScrolled = remember(poi.savedId) { mutableStateOf(false) }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex > 0) hasUserScrolled.value = true
    }
    val scrollKey = Pair(chatState.bubbles.size, chatState.isStreaming)
    LaunchedEffect(scrollKey) {
        if (!hasUserScrolled.value) return@LaunchedEffect
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex <= 0) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val isNearBottom = lastVisible >= lastIndex - 2
        if (isNearBottom) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    // Pre-seed AI intro for this POI — visit-aware when opened via Visit tap
    LaunchedEffect(poi.savedId) {
        if (visitState != null) {
            chatViewModel.openChatForPoiVisit(poi, visitState, areaName, allPois, activeDynamicVibe)
        } else {
            chatViewModel.openChatForPoi(poi, areaName, allPois, activeDynamicVibe)
        }
    }

    var overflowExpanded by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = overflowExpanded) { overflowExpanded = false }
    PlatformBackHandler(enabled = !overflowExpanded) { onDismiss() }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // Header card — hero image dark, info section light
                item(key = "header") {
                    PoiDetailHeader(
                        poi = poi,
                        vibeColor = vibeColor,
                        isVisited = isVisited,
                        visitState = visitState,
                        onVisit = onVisit,
                        onUnvisit = onUnvisit,
                        onDirectionsClick = onDirectionsClick,
                        onShowOnMap = onShowOnMap,
                        onDismiss = onDismiss,
                        onImageClick = { showGallery = true },
                        overflowExpanded = overflowExpanded,
                        onOverflowExpandedChange = { overflowExpanded = it },
                        localeProvider = localeProvider,
                    )
                }

                // Ghost pin CTA — "Discover this one too"
                if (isGhostPin && !isVisited) {
                    item(key = "ghost_cta") {
                        Button(
                            onClick = onDiscoverGhostPin,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.harazone.ui.theme.SavedLensTeal,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text("Discover this one too")
                        }
                    }
                }

                // Context block — between header and chat (hidden when empty and not loading)
                val hasContextContent = chatState.isContextLoading ||
                    !chatState.contextBlurb.isNullOrBlank() ||
                    !chatState.whyNow.isNullOrBlank() ||
                    !chatState.localTip.isNullOrBlank()
                if (hasContextContent) {
                    item(key = "context") {
                        PoiContextBlock(
                            contextBlurb = chatState.contextBlurb,
                            whyNow = chatState.whyNow,
                            localTip = chatState.localTip,
                            isLoading = chatState.isContextLoading,
                            isTipRefreshing = chatState.isTipRefreshing,
                            onRefreshTip = { chatViewModel.refreshLocalTip(poi, areaName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Safety card — CAUTION+ only (Bug #10 fix: disclaimer label)
                item(key = "safety_card") {
                    SafetyCardSection(poi.discoveryContext)
                }

                // Living Here section — resident mode only
                if (discoveryMode == DiscoveryMode.RESIDENT && residentData != null) {
                    item(key = "living_here") {
                        LivingHereSection(
                            poi = poi,
                            dailyLifePois = dailyLifePois,
                        )
                    }
                }

                // Chat bubbles with inline POI cards after each AI response
                chatState.bubbles.forEach { bubble ->
                    item(key = bubble.id) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            ChatBubbleItem(
                                bubble = bubble,
                                onRetry = { chatViewModel.retryLastMessage() },
                                lightMode = true,
                            )
                        }
                    }
                    // Render POI cards inline — filter out the current detail page POI
                    val cards = chatState.bubblePoiCards[bubble.id]
                        ?.filter { it.name != poi.name }
                    if (!cards.isNullOrEmpty()) {
                        items(cards, key = { "poi_${it.id}" }) { card ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                ChatPoiMiniCard(
                                    card = card,
                                    isSaved = card.id in chatState.savedPoiIds,
                                    onSave = { chatViewModel.savePoi(card, chatState.areaName) },
                                    onUnsave = { chatViewModel.unsavePoi(card.id) },
                                    onClick = { onPoiCardClick(card) },
                                    onDirections = {
                                        val handled = onNavigateToMaps(card.lat, card.lng, card.name)
                                        if (!handled) onDirectionsFailed()
                                    },
                                    onShowOnMap = {
                                        onShowOnMap(card.lat, card.lng)
                                    },
                                )
                            }
                        }
                    }
                }

                // Skeleton shimmer
                if (chatState.showSkeletons) {
                    item(key = "skeletons") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SkeletonSection(3)
                        }
                    }
                }

                // Persistent pills
                if (chatState.persistentPills.isNotEmpty() && !chatState.isStreaming) {
                    item(key = "pills") {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(chatState.persistentPills) { pill ->
                                val label = pillDisplayLabel(pill)
                                if (pill.label == ChatViewModel.LABEL_NEW_TOPIC) {
                                    SuggestionChip(
                                        onClick = { chatViewModel.resetToIntentPills() },
                                        label = { Text("${pill.emoji} $label", fontSize = 13.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                        ),
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = {
                                            if (chatState.bubbles.isEmpty()) chatViewModel.tapIntentPill(pill)
                                            else chatViewModel.tapPersistentPill(pill)
                                        },
                                        label = { Text("${pill.emoji} $label", fontSize = 13.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sticky input bar
            ChatInputBar(
                inputText = chatState.inputText,
                isStreaming = chatState.isStreaming,
                onInputChange = { chatViewModel.updateInput(it) },
                onSend = { chatViewModel.sendMessage() },
                placeholder = DETAIL_PAGE_CHAT_HINT,
            )
        }

        // Fullscreen image gallery overlay — last child for Z-order
        if (showGallery && poi.imageUrls.isNotEmpty()) {
            FullscreenImageGallery(
                images = poi.imageUrls,
                poiName = poi.name,
                vibeColor = vibeColor,
                onDismiss = { showGallery = false },
            )
        }
    }
}

@Composable
private fun PoiContextBlock(
    contextBlurb: String?,
    whyNow: String?,
    localTip: String?,
    isLoading: Boolean,
    isTipRefreshing: Boolean = false,
    onRefreshTip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (isLoading) Modifier.semantics { stateDescription = "Loading context" } else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isLoading) {
            val infiniteTransition = rememberInfiniteTransition(label = "contextShimmer")
            val shimmerAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "contextShimmerAlpha",
            )
            val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == 2) 0.65f else 0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor),
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor),
            )
        } else {
            if (!contextBlurb.isNullOrBlank()) {
                Text(
                    text = contextBlurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!whyNow.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u23F0", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics { })
                    Text(
                        text = whyNow,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Local tip with refresh button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\uD83D\uDCA1", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics { })
                val tipText = localTip
                    ?: if (isTipRefreshing) stringResource(Res.string.poi_tip_refreshing)
                    else if (!isLoading) stringResource(Res.string.poi_tip_refresh_fallback)
                    else ""
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (onRefreshTip != null && !isLoading) {
                    IconButton(onClick = onRefreshTip, modifier = Modifier.size(24.dp)) {
                        Text("\u21BB", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoiDetailHeader(
    poi: POI,
    vibeColor: Color,
    isVisited: Boolean,
    visitState: VisitState?,
    onVisit: () -> Unit,
    onUnvisit: () -> Unit,
    onDirectionsClick: (Double, Double, String) -> Unit,
    onShowOnMap: (lat: Double, lng: Double) -> Unit,
    onDismiss: () -> Unit,
    onImageClick: () -> Unit = {},
    overflowExpanded: Boolean = false,
    onOverflowExpandedChange: (Boolean) -> Unit = {},
    localeProvider: LocaleProvider,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column {
        // Hero image — 250dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .then(
                    if (poi.imageUrls.isNotEmpty()) Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onImageClick() }
                    else Modifier
                ),
        ) {
            // Vibe gradient fallback
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(vibeColor.copy(alpha = 0.6f), vibeColor.copy(alpha = 0.2f))
                        )
                    ),
            )
            if (poi.imageUrl != null) {
                AsyncImage(
                    model = poi.imageUrl,
                    contentDescription = poi.name,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(vibeColor.copy(alpha = 0.15f)),
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Bottom gradient — blends into surface below
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, surfaceColor))),
            )
            // Name + type + vibe chip overlaid bottom-left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 12.dp, end = 60.dp),
            ) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = poi.type.replaceFirstChar { it.uppercaseChar() },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                if (poi.vibe.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = poi.vibe,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .background(vibeColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            // Photo count badge
            if (poi.imageUrls.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "1/${poi.imageUrls.size} photos",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            // Close button — top-left
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 54.dp, start = 12.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            // Overflow button — top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 54.dp, end = 12.dp),
            ) {
                IconButton(
                    onClick = { onOverflowExpandedChange(true) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { onOverflowExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = { onOverflowExpandedChange(false) },
                    )
                    DropdownMenuItem(
                        text = { Text("Show on map") },
                        onClick = {
                            onOverflowExpandedChange(false)
                            if (poi.latitude != null && poi.longitude != null) {
                                onShowOnMap(poi.latitude, poi.longitude)
                                onDismiss()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Report") },
                        onClick = { onOverflowExpandedChange(false) },
                    )
                }
            }
        }

        // Rating line
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (poi.rating != null) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                Text("${poi.rating}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                if ((poi.reviewCount ?: 0) > 0)
                    Text("(${poi.reviewCount})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("\u00B7", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (poi.priceRange != null) {
                Text(poi.priceRange, style = MaterialTheme.typography.bodySmall, color = Color(0xFF3FB950), fontWeight = FontWeight.SemiBold)
                Text("\u00B7", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (poi.liveStatus != null) {
                LiveStatusBadge(poi.liveStatus)
                Text("\u00B7", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (poi.reviewCount != null) VerifiedByGoogleChip()
        }

        // Safety ticker — CAUTION+ only
        SafetyTicker(poi.discoveryContext)

        // Info ticker
        InfoTicker(poi, localeProvider)

        // Links + Social strip
        LinksSocialStrip(poi, onShowOnMap)

        // Insight
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            if (poi.insight.isNotEmpty()) {
                Text(
                    text = poi.insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // User note
            if (poi.userNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u270F\uFE0F ${poi.userNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action chips
            val chipColors = AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = MaterialTheme.colorScheme.onSurface,
            )
            val chipBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isVisited && visitState != null) {
                    val (visitLabel, visitColor) = when (visitState) {
                        VisitState.GO_NOW -> "\u2713 Go Now" to Color(0xFF4CAF50)
                        VisitState.PLAN_SOON -> "\u2713 Plan Soon" to Color(0xFFFF9800)
                        VisitState.WANT_TO_GO -> "\u2713 Want to Visit" to Color(0xFF9E9E9E)
                    }
                    AssistChip(
                        onClick = onUnvisit,
                        label = { Text(visitLabel) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = visitColor),
                        border = BorderStroke(1.dp, visitColor.copy(alpha = 0.5f)),
                    )
                } else {
                    AssistChip(
                        onClick = onVisit,
                        label = { Text("Visit") },
                        colors = chipColors,
                        border = chipBorder,
                    )
                }
                if (poi.latitude != null && poi.longitude != null) {
                    AssistChip(
                        onClick = { onDirectionsClick(poi.latitude!!, poi.longitude!!, poi.name) },
                        label = { Text(stringResource(Res.string.poi_card_directions)) },
                        leadingIcon = {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = chipColors,
                        border = chipBorder,
                    )
                }
                AssistChip(
                    onClick = { if (poi.latitude != null && poi.longitude != null) onShowOnMap(poi.latitude!!, poi.longitude!!) },
                    label = { Text("\uD83D\uDCCD ${stringResource(Res.string.chat_poi_show_on_map)}") },
                    colors = chipColors,
                    border = chipBorder,
                )
            }
        }
    }
}

@Composable
private fun SafetyTicker(context: DiscoveryContext?, modifier: Modifier = Modifier) {
    val level = context?.advisoryLevel ?: return
    val (bgColor, dotColor, text) = when (level) {
        AdvisoryLevel.CAUTION ->
            Triple(Color(0xFFF0C040).copy(alpha = 0.08f), Color(0xFFF0C040),
                "\u26A0 Exercise caution \u00B7 ${context.advisoryBlurb ?: context.areaName}")
        AdvisoryLevel.RECONSIDER ->
            Triple(Color(0xFFF08020).copy(alpha = 0.08f), Color(0xFFF08020),
                "\u26A0 Reconsider travel \u00B7 ${context.advisoryBlurb ?: context.areaName}")
        AdvisoryLevel.DO_NOT_TRAVEL ->
            Triple(Color(0xFFF47067).copy(alpha = 0.10f), Color(0xFFF47067),
                "\uD83D\uDEA8 Do not travel \u00B7 ${context.advisoryBlurb ?: context.areaName}")
        else -> return // SAFE, UNKNOWN, null — don't render
    }
    val infiniteTransition = rememberInfiniteTransition(label = "safetyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "safePulse",
    )
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).background(dotColor.copy(alpha = pulseAlpha), CircleShape))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = dotColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InfoTicker(poi: POI, localeProvider: LocaleProvider, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val items = buildList {
        poi.formattedAddress?.let { add("address" to it) }
        poi.internationalPhoneNumber?.let { add("phone" to it) }
        // Show currency/language only when foreign
        val ctx = poi.discoveryContext
        if (ctx?.currency != null && ctx.currency != localeProvider.homeCurrencyCode) {
            add("currency" to ctx.currency)
        }
        if (ctx?.language != null) {
            val homeCountry = localeProvider.languageTag.substringAfterLast("-").uppercase()
            val poiCountry = ctx.countryCode.uppercase()
            if (homeCountry.length != 2 || homeCountry != poiCountry) {
                add("language" to ctx.language)
            }
        }
    }
    if (items.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (type, value) ->
            item {
                if (type == "phone") {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3FB950),
                        modifier = Modifier.clickable { uriHandler.openUri("tel:$value") },
                    )
                } else {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index < items.lastIndex) {
                item {
                    Text("\u00B7", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LinksSocialStrip(
    poi: POI,
    onShowOnMap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val hasAny = poi.internationalPhoneNumber != null || poi.websiteUri != null ||
        poi.googleMapsUri != null || poi.instagram != null || poi.facebook != null || poi.twitter != null
    if (!hasAny) return

    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (poi.internationalPhoneNumber != null)
            LinkIcon(
                icon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF3FB950), modifier = Modifier.size(18.dp)) },
                bg = Color(0xFF3FB950).copy(alpha = 0.18f),
                contentDescription = "Call ${poi.name}",
                onClick = { uriHandler.openUri("tel:${poi.internationalPhoneNumber}") },
            )
        if (poi.websiteUri != null)
            LinkIcon(
                icon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                bg = MaterialTheme.colorScheme.surfaceVariant,
                contentDescription = "Website",
                onClick = { uriHandler.openUri(poi.websiteUri) },
            )
        if (poi.googleMapsUri != null)
            LinkIcon(
                icon = { Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF6AA3F4), modifier = Modifier.size(18.dp)) },
                bg = Color(0xFF4285F4).copy(alpha = 0.20f),
                contentDescription = "Open in Google Maps",
                onClick = { uriHandler.openUri(poi.googleMapsUri) },
            )

        val hasSocial = poi.instagram != null || poi.facebook != null || poi.twitter != null
        val hasUtility = poi.internationalPhoneNumber != null || poi.websiteUri != null || poi.googleMapsUri != null
        if (hasSocial && hasUtility) {
            Box(Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }

        if (poi.instagram != null)
            LinkIcon(
                icon = { Text("IG", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE94D82), fontWeight = FontWeight.Bold) },
                bg = Color(0xFFE1306C).copy(alpha = 0.20f),
                contentDescription = "Instagram",
                onClick = { uriHandler.openUri("https://instagram.com/${poi.instagram}") },
            )
        if (poi.facebook != null)
            LinkIcon(
                icon = { Text("FB", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5A7FC2), fontWeight = FontWeight.Bold) },
                bg = Color(0xFF4267B2).copy(alpha = 0.20f),
                contentDescription = "Facebook",
                onClick = { uriHandler.openUri("https://facebook.com/${poi.facebook}") },
            )
        if (poi.twitter != null)
            LinkIcon(
                icon = { Text("X", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) },
                bg = MaterialTheme.colorScheme.surfaceVariant,
                contentDescription = "X (Twitter)",
                onClick = { uriHandler.openUri("https://x.com/${poi.twitter}") },
            )
    }
}

@Composable
private fun LinkIcon(
    icon: @Composable () -> Unit,
    bg: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(bg, RoundedCornerShape(9.dp))
            .clickable(onClickLabel = contentDescription) { onClick() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun SafetyCardSection(context: DiscoveryContext?, modifier: Modifier = Modifier) {
    val level = context?.advisoryLevel ?: return
    if (level == AdvisoryLevel.SAFE || level == AdvisoryLevel.UNKNOWN) return
    val (cardBg, dotColor, levelText) = when (level) {
        AdvisoryLevel.CAUTION -> Triple(Color(0xFFF0C040).copy(alpha = 0.04f), Color(0xFFF0C040), "Exercise Caution")
        AdvisoryLevel.RECONSIDER -> Triple(Color(0xFFF08020).copy(alpha = 0.06f), Color(0xFFF08020), "Reconsider Travel")
        AdvisoryLevel.DO_NOT_TRAVEL -> Triple(Color(0xFFF47067).copy(alpha = 0.08f), Color(0xFFF47067), "Do Not Travel")
        else -> return
    }
    Column(modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
            "TRAVEL ADVISORY",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(cardBg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Text(levelText, style = MaterialTheme.typography.bodySmall, color = dotColor, fontWeight = FontWeight.SemiBold)
            }
            if (context.advisoryBlurb != null) {
                Text(context.advisoryBlurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Bug #10 fix — AI-generated safety disclaimer
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("\u2139", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clearAndSetSemantics { })
                Text(
                    stringResource(Res.string.advisory_ai_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
private fun LiveStatusBadge(status: String) {
    val statusOpen = stringResource(Res.string.poi_status_open)
    val statusBusy = stringResource(Res.string.poi_status_busy)
    val statusClosed = stringResource(Res.string.poi_status_closed)
    val (color, label) = when (status.lowercase()) {
        "open" -> Color(0xFF4CAF50) to statusOpen
        "busy" -> Color(0xFFFF9800) to statusBusy
        "closed" -> Color(0xFF9E9E9E) to statusClosed
        else -> Color.Gray to status
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun BuzzMeter(liveStatus: String, vibeColor: Color) {
    val filledSegments = when (liveStatus.lowercase()) {
        "busy" -> 3
        "open" -> 2
        "closed" -> 1
        else -> 0
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < filledSegments) vibeColor
                        else vibeColor.copy(alpha = 0.2f)
                    ),
            )
        }
    }
}

@Composable
private fun VerifiedByGoogleChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFFF1F1F1), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4285F4),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Verified by Google",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF5F6368),
        )
    }
}

@Composable
private fun LivingHereSection(
    poi: POI,
    dailyLifePois: List<POI>,
    modifier: Modifier = Modifier,
) {
    val poiLat = poi.latitude ?: return
    val poiLng = poi.longitude ?: return
    if (dailyLifePois.isEmpty()) return

    val proximityItems = remember(poi.savedId, dailyLifePois) { buildList {
        // Nearest transit
        dailyLifePois.filter { it.type.contains("transit", ignoreCase = true) }
            .minByOrNull { haversineDistanceMeters(poiLat, poiLng, it.latitude ?: 0.0, it.longitude ?: 0.0) }
            ?.let {
                val dist = haversineDistanceMeters(poiLat, poiLng, it.latitude ?: 0.0, it.longitude ?: 0.0)
                val distText = if (dist < 1000) "${dist.toInt()}m" else "${"%.1f".format(dist / 1000)}km"
                add("\uD83D\uDE89 Nearest transit: $distText")
            }
        // Grocery count within 500m
        val groceryCount = dailyLifePois.count { poi2 ->
            poi2.type.contains("grocery", ignoreCase = true) &&
                haversineDistanceMeters(poiLat, poiLng, poi2.latitude ?: 0.0, poi2.longitude ?: 0.0) < 500
        }
        if (groceryCount > 0) add("\uD83D\uDED2 $groceryCount grocery store${if (groceryCount > 1) "s" else ""} within 500m")
        // Nearest hospital
        dailyLifePois.filter { it.type.contains("hospital", ignoreCase = true) }
            .minByOrNull { haversineDistanceMeters(poiLat, poiLng, it.latitude ?: 0.0, it.longitude ?: 0.0) }
            ?.let {
                val dist = haversineDistanceMeters(poiLat, poiLng, it.latitude ?: 0.0, it.longitude ?: 0.0)
                val distText = if (dist < 1000) "${dist.toInt()}m" else "${"%.1f".format(dist / 1000)}km"
                add("\uD83C\uDFE5 Nearest hospital: $distText")
            }
        // School count within 1km
        val schoolCount = dailyLifePois.count { poi2 ->
            poi2.type.contains("school", ignoreCase = true) &&
                haversineDistanceMeters(poiLat, poiLng, poi2.latitude ?: 0.0, poi2.longitude ?: 0.0) < 1000
        }
        if (schoolCount > 0) add("\uD83C\uDFEB $schoolCount school${if (schoolCount > 1) "s" else ""} within 1km")
    } }
    if (proximityItems.isEmpty()) return

    Column(modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LIVING HERE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = com.harazone.ui.theme.MetaContextTeal,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "\uD83E\uDD16 AI Insight",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(6.dp))
        proximityItems.forEach { item ->
            Text(
                text = item,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        Text(
            text = "Based on Google Places data",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
