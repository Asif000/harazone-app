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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Star
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
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.POI
import com.harazone.domain.model.Vibe
import com.harazone.domain.model.VisitState
import com.harazone.ui.map.ChatBubbleItem
import com.harazone.ui.map.ChatInputBar
import com.harazone.ui.map.ChatPoiCard
import com.harazone.ui.map.ChatPoiMiniCard
import com.harazone.ui.map.ChatUiState
import com.harazone.ui.map.ChatViewModel
import com.harazone.ui.map.SkeletonSection
import com.harazone.ui.map.pillDisplayLabel
import com.harazone.ui.theme.DetailPageLight
import com.harazone.ui.theme.toColor
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

private const val DETAIL_PAGE_CHAT_HINT = "Ask about this place..."
// TODO(BACKLOG-LOW): extract DETAIL_PAGE_CHAT_HINT to strings.xml
private val DetailPageTextDark = Color(0xFF2A2A2A)

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
    modifier: Modifier = Modifier,
) {
    var showGallery by remember(poi.savedId) { mutableStateOf(false) }
    val vibeColor = (Vibe.entries.firstOrNull { poi.vibe.contains(it.name, ignoreCase = true) } ?: Vibe.DEFAULT).toColor()

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

    PlatformBackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = modifier.background(DetailPageLight)
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
                            modifier = Modifier.fillMaxWidth(),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(DetailPageLight)
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
            val shimmerColor = Color(0xFFE0DDD9).copy(alpha = shimmerAlpha)
            // Shimmer placeholders
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
            // whyNow shimmer
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
                    color = DetailPageTextDark,
                )
            }
            if (!whyNow.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF0EDE9))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u23F0", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics { })
                    Text(
                        text = whyNow,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4A4A4A),
                    )
                }
            }
            if (!localTip.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\uD83D\uDCA1", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics { })
                    Text(
                        text = localTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                    )
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
) {

    Column {
        // Hero image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .then(
                    if (poi.imageUrls.isNotEmpty()) Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onImageClick() }
                    else Modifier
                ),
        ) {
            // Gradient fallback
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
            } else {
                Text(
                    text = stringResource(Res.string.poi_card_loading),
                    color = Color.White.copy(alpha = 0.25f),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Image count badge — hide for single image
            if (poi.imageUrls.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
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

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(modifier = Modifier.background(DetailPageLight).padding(16.dp)) {
            // Name
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleMedium,
                color = DetailPageTextDark,
            )
            // Type
            Text(
                text = poi.type.replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF6B6B6B),
            )

            // Vibe chip
            if (poi.vibe.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = poi.vibe,
                    style = MaterialTheme.typography.labelSmall,
                    color = DetailPageTextDark,
                    modifier = Modifier
                        .background(vibeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Rating + live status + buzz meter
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (poi.rating != null) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = " ${poi.rating}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DetailPageTextDark,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (poi.liveStatus != null) {
                    LiveStatusBadge(poi.liveStatus)
                    Spacer(Modifier.width(12.dp))
                    BuzzMeter(liveStatus = poi.liveStatus, vibeColor = vibeColor)
                }
            }

            // Price range
            if (poi.priceRange != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = poi.priceRange,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B6B6B),
                )
            }

            // Insight
            if (poi.insight.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = poi.insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4A4A4A),
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0DDD9)),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0DDD9).copy(alpha = 0.6f)),
                )
            }

            // User note
            if (poi.userNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u270F\uFE0F ${poi.userNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B6B6B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8E5E1), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action chips: Save, Directions, Show on Map
            val chipColors = AssistChipDefaults.assistChipColors(
                labelColor = DetailPageTextDark,
                leadingIconContentColor = DetailPageTextDark,
            )
            val chipBorder = BorderStroke(1.dp, Color(0xFFCCC8C3))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isVisited && visitState != null) {
                    val (visitLabel, visitColor) = when (visitState) {
                        VisitState.GO_NOW -> "✓ Go Now" to Color(0xFF4CAF50)
                        VisitState.PLAN_SOON -> "✓ Plan Soon" to Color(0xFFFF9800)
                        VisitState.WANT_TO_GO -> "✓ Want to Visit" to Color(0xFF9E9E9E)
                    }
                    AssistChip(
                        onClick = onUnvisit,
                        label = { Text(visitLabel) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = visitColor,
                        ),
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
