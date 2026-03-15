package com.harazone.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
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
import com.harazone.ui.map.ChatBubbleItem
import com.harazone.ui.map.ChatInputBar
import com.harazone.ui.map.ChatPoiCard
import com.harazone.ui.map.ChatPoiMiniCard
import com.harazone.ui.map.ChatUiState
import com.harazone.ui.map.ChatViewModel
import com.harazone.ui.map.SkeletonSection
import com.harazone.ui.map.pillDisplayLabel
import com.harazone.ui.theme.DarkColorScheme
import com.harazone.ui.theme.MapSurfaceDark
import com.harazone.ui.theme.toColor
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiDetailPage(
    poi: POI,
    chatViewModel: ChatViewModel,
    chatState: ChatUiState,
    areaName: String,
    allPois: List<POI>,
    activeDynamicVibe: DynamicVibe?,
    isSaved: Boolean,
    onSave: () -> Unit,
    onUnsave: () -> Unit,
    onDirectionsClick: (Double, Double, String) -> Unit,
    onShowOnMap: (lat: Double, lng: Double) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToMaps: (Double, Double, String) -> Boolean,
    onDirectionsFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to latest content
    val scrollKey = Pair(chatState.bubbles.size, chatState.isStreaming)
    LaunchedEffect(scrollKey) {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex > 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    // Pre-seed AI intro for this POI
    LaunchedEffect(poi.savedId) {
        chatViewModel.openChatForPoi(poi, areaName, allPois, activeDynamicVibe)
    }

    PlatformBackHandler(enabled = true) { onDismiss() }

    // Force dark color scheme — AiDetailPage always uses a dark background,
    // so child composables (ChatBubbleItem, etc.) must resolve theme colors as dark.
    MaterialTheme(colorScheme = DarkColorScheme) {
    Box(
        modifier = modifier
            .background(MapSurfaceDark.copy(alpha = 0.97f))
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header card
                item(key = "header") {
                    PoiDetailHeader(
                        poi = poi,
                        isSaved = isSaved,
                        onSave = onSave,
                        onUnsave = onUnsave,
                        onDirectionsClick = onDirectionsClick,
                        onShowOnMap = onShowOnMap,
                        onDismiss = onDismiss,
                    )
                }

                // Chat bubbles
                items(chatState.bubbles, key = { it.id }) { bubble ->
                    ChatBubbleItem(
                        bubble = bubble,
                        onRetry = { chatViewModel.retryLastMessage() },
                    )
                }

                // Skeleton shimmer
                if (chatState.showSkeletons) {
                    item(key = "skeletons") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SkeletonSection(3)
                        }
                    }
                }

                // POI cards — inline vertical items (scroll up with conversation)
                if (chatState.poiCards.isNotEmpty()) {
                    items(chatState.poiCards, key = { "poi_${it.id}" }) { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            ChatPoiMiniCard(
                                card = card,
                                isSaved = card.id in chatState.savedPoiIds,
                                onSave = { chatViewModel.savePoi(card, chatState.areaName) },
                                onUnsave = { chatViewModel.unsavePoi(card.id) },
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
            )
        }
    }
    } // MaterialTheme
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoiDetailHeader(
    poi: POI,
    isSaved: Boolean,
    onSave: () -> Unit,
    onUnsave: () -> Unit,
    onDirectionsClick: (Double, Double, String) -> Unit,
    onShowOnMap: (lat: Double, lng: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val poiVibe = Vibe.entries.firstOrNull { poi.vibe.contains(it.name, ignoreCase = true) }
    val vibeColor = (poiVibe ?: Vibe.DEFAULT).toColor()

    Column {
        // Hero image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
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

        Column(modifier = Modifier.padding(16.dp)) {
            // Name
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            // Type
            Text(
                text = poi.type.replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )

            // Vibe chip
            if (poi.vibe.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = poi.vibe,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(vibeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
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
                        color = Color.White,
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
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            // Insight
            if (poi.insight.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = poi.insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                )
            }

            // User note
            if (poi.userNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u270F\uFE0F ${poi.userNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action chips: Save, Directions, Show on Map
            val chipColors = AssistChipDefaults.assistChipColors(
                labelColor = Color.White.copy(alpha = 0.9f),
                leadingIconContentColor = Color.White.copy(alpha = 0.9f),
            )
            val chipBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = { if (isSaved) onUnsave() else onSave() },
                    label = { Text(if (isSaved) stringResource(Res.string.poi_card_saved) else stringResource(Res.string.poi_card_save)) },
                    leadingIcon = {
                        Icon(
                            if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = chipColors,
                    border = chipBorder,
                )
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
