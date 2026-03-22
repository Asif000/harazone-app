package com.harazone.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.harazone.BuildKonfig
import com.harazone.domain.model.ContextualPill
import com.harazone.domain.model.MessageRole
import com.harazone.ui.components.MarkdownText
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

private val IndigoGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
)
private val AiBubbleColor = Color.White.copy(alpha = 0.07f)
private val AiBubbleLightBorder = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.25f))
private val HandleColor = Color.White.copy(alpha = 0.14f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChatOverlay(
    viewModel: ChatViewModel,
    chatState: ChatUiState,
    onDismiss: () -> Unit,
    onNavigateToMaps: (Double, Double, String) -> Boolean = { _, _, _ -> false },
    onDirectionsFailed: () -> Unit = {},
    onPoiCardClick: (ChatPoiCard) -> Unit = {},
    onShowOnMap: (ChatPoiCard) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Scroll to last item: bubbles + skeleton (1 item if shown)
    val totalItems = chatState.bubbles.size +
        (if (chatState.showSkeletons) 1 else 0)
    // Scroll when content changes OR when overlay reopens with new context
    val scrollKey = Pair(chatState.bubbles.size, chatState.isStreaming)
    LaunchedEffect(scrollKey, chatState.isOpen) {
        if (totalItems > 0) {
            listState.animateScrollToItem(maxOf(0, totalItems - 1))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HandleColor),
            )
        },
        modifier = Modifier.fillMaxHeight(0.84f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                // AI orb
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(IndigoGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AI", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.chat_input_hint),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = buildString {
                        append("\uD83D\uDCCD ${chatState.areaName}")
                        chatState.vibeName?.let { append(" · $it") }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close chat")
                }
            }

            chatState.contextBanner?.let { banner ->
                ContextBanner(text = banner, onDismiss = { viewModel.dismissContextBanner() })
            }

            // Return dialog for expired conversations
            if (chatState.showReturnDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.continueConversation() },
                    title = { Text(stringResource(Res.string.chat_return_title)) },
                    text = { Text(stringResource(Res.string.chat_return_message)) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.continueConversation() }) { Text(stringResource(Res.string.chat_return_continue)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.startFreshConversation() }) { Text(stringResource(Res.string.chat_return_start_fresh)) }
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            // Messages area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chatState.bubbles.isEmpty() && chatState.persistentPills.isNotEmpty()) {
                    item(key = "empty_state") {
                        EmptyStateText(areaName = chatState.areaName)
                    }
                } else if (chatState.bubbles.isEmpty()) {
                    item(key = "empty_prompt") {
                        Text(
                            stringResource(Res.string.chat_suggestion_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(chatState.bubbles, key = { it.id }) { bubble ->
                        ChatBubbleItem(
                            bubble = bubble,
                            onRetry = { viewModel.retryLastMessage() },
                        )
                    }

                    // Skeleton shimmer section
                    if (chatState.showSkeletons) {
                        item(key = "skeletons") {
                            SkeletonSection(3)
                        }
                    }
                }
            }

            // POI card rail — accumulates across turns
            if (chatState.poiCards.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chatState.poiCards, key = { it.id }) { card ->
                        ChatPoiMiniCard(
                            card = card,
                            isSaved = card.id in chatState.savedPoiIds,
                            onSave = { viewModel.savePoi(card, chatState.areaName) },
                            onUnsave = { viewModel.unsavePoi(card.id) },
                            onDirections = {
                                val handled = onNavigateToMaps(card.lat, card.lng, card.name)
                                if (!handled) onDirectionsFailed()
                            },
                            onClick = { onPoiCardClick(card) },
                            onShowOnMap = { onShowOnMap(card) },
                        )
                    }
                }
            }

            // Persistent pill rail — always visible when pills exist
            if (chatState.persistentPills.isNotEmpty() && !chatState.isStreaming) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chatState.persistentPills) { pill ->
                        val pillDisplayLabel = pillDisplayLabel(pill)
                        if (pill.label == ChatViewModel.LABEL_NEW_TOPIC) {
                            SuggestionChip(
                                onClick = { viewModel.resetToIntentPills() },
                                label = { Text("${pill.emoji} $pillDisplayLabel", fontSize = 13.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                ),
                            )
                        } else {
                            SuggestionChip(
                                onClick = {
                                    if (chatState.bubbles.isEmpty()) viewModel.tapIntentPill(pill)
                                    else viewModel.tapPersistentPill(pill)
                                },
                                label = { Text("${pill.emoji} $pillDisplayLabel", fontSize = 13.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            // Input bar
            ChatInputBar(
                inputText = chatState.inputText,
                isStreaming = chatState.isStreaming,
                onInputChange = { viewModel.updateInput(it) },
                onSend = { viewModel.sendMessage() },
                autoFocus = true,
            )
        }
    }
}

@Composable
private fun EmptyStateText(areaName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IndigoGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text("AI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.chat_ask_anything, areaName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.chat_suggestion_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ContextBanner(
    text: String,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SkeletonSection(count: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
                    ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatPoiMiniCard(
    card: ChatPoiCard,
    isSaved: Boolean,
    onSave: () -> Unit,
    onUnsave: () -> Unit,
    onDirections: () -> Unit,
    onClick: () -> Unit = {},
    onShowOnMap: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        // Background satellite image
        AsyncImage(
            model = poiThumbnailUrl(card.lat, card.lng),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Gradient scrim for text readability
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.3f to Color.Black.copy(alpha = 0.7f),
                        1f to Color.Black.copy(alpha = 0.85f),
                    )
                ),
        )
        // Content
        Column(modifier = Modifier.padding(10.dp, 10.dp, 10.dp, 8.dp)) {
            // Row 1: Type badge + Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(poiTypeEmoji(card.type), fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        card.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            // Row 2: Why special
            Text(
                card.whySpecial,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Row 3: Action chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                SuggestionChip(
                    onClick = { if (!isSaved) onSave() },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isSaved) "✓ Visited" else "Visit",
                                fontSize = 12.sp,
                                color = if (isSaved) Color(0xFF4CAF50) else Color.White,
                            )
                        }
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isSaved)
                            Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else
                            Color.White.copy(alpha = 0.15f),
                    ),
                    border = BorderStroke(1.dp, if (isSaved) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.3f)),
                )
                SuggestionChip(
                    onClick = onDirections,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Directions,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.chat_poi_directions), fontSize = 12.sp, color = Color.White)
                        }
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                )
                SuggestionChip(
                    onClick = onShowOnMap,
                    label = { Text("\uD83D\uDCCD ${stringResource(Res.string.chat_poi_show_on_map)}", fontSize = 11.sp, color = Color.White) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

internal fun poiThumbnailUrl(lat: Double, lng: Double): String {
    // Convert lat/lng to satellite tile coords (zoom 15) — same approach as AreaRepositoryImpl
    val z = 15
    val n = 1 shl z
    val x = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = lat * kotlin.math.PI / 180.0
    val y = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / kotlin.math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    return "https://api.maptiler.com/tiles/satellite-v2/$z/$x/$y.jpg?key=${BuildKonfig.MAPTILER_API_KEY}"
}

internal fun poiTypeEmoji(type: String): String = com.harazone.util.poiTypeEmoji(type.lowercase())

@Composable
internal fun ChatBubbleItem(
    bubble: ChatBubble,
    onRetry: () -> Unit,
    lightMode: Boolean = false,
) {
    val isUser = bubble.role == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (bubble.isError) {
            // Error bubble
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clickable { onRetry() },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        bubble.content,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (isUser) {
            // User bubble
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .background(IndigoGradient)
                    .padding(12.dp),
            ) {
                Text(
                    bubble.content,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            // AI bubble
            val aiBg = if (lightMode) Color.White else AiBubbleColor
            val aiBorder = if (lightMode) AiBubbleLightBorder else null
            val aiTextColor = if (lightMode) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.onSurface
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = aiBg,
                border = aiBorder,
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    MarkdownText(
                        text = bubble.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = aiTextColor,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (bubble.isStreaming) {
                        BlinkingCursor()
                    }
                }
            }
        }
    }
    // TODO(BACKLOG-LOW): render source attribution links under AI chat bubbles
}

@Composable
internal fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    Text(
        "\u258D",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.alpha(alpha),
    )
}

@Composable
internal fun ChatInputBar(
    inputText: String,
    isStreaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String? = null,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(300) // wait for overlay animation
            focusRequester.requestFocus()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        TextField(
            value = inputText,
            onValueChange = onInputChange,
            enabled = !isStreaming,
            placeholder = { Text(placeholder ?: stringResource(Res.string.chat_input_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .then(if (autoFocus) Modifier.focusRequester(focusRequester) else Modifier),
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = { if (!isStreaming && inputText.isNotBlank()) onSend() },
            shape = CircleShape,
            color = if (!isStreaming && inputText.isNotBlank())
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (!isStreaming && inputText.isNotBlank())
                        Color.White
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun pillDisplayLabel(pill: ContextualPill): String = when {
    pill.label == ChatViewModel.LABEL_NEW_TOPIC                     -> stringResource(Res.string.pill_new_topic)
    pill.label == "Areas to avoid"                -> stringResource(Res.string.pill_areas_to_avoid)
    pill.label == "Best time to go"               -> stringResource(Res.string.pill_best_time)
    pill.label == "Tell me more"                  -> stringResource(Res.string.pill_tell_me_more)
    pill.label == "Surprise me"                   -> stringResource(Res.string.pill_surprise_me)
    pill.label == "Best food right now"           -> stringResource(Res.string.pill_best_food)
    pill.label == "Show me hidden gems"           -> stringResource(Res.string.pill_hidden_gems)
    pill.label == "Get me outside"                -> stringResource(Res.string.pill_get_outside)
    pill.label == "Plan a day trip from my saves" -> stringResource(Res.string.pill_day_trip)
    pill.label == "Find patterns in my saves"     -> stringResource(Res.string.pill_find_patterns)
    pill.label == "Safe at night?"                -> stringResource(Res.string.pill_safe_at_night)
    pill.label == "Veggie options?"               -> stringResource(Res.string.pill_veggie_options)
    pill.label == "Famous events?"                -> stringResource(Res.string.pill_famous_events)
    pill.label == "What's nearby?"                -> stringResource(Res.string.pill_whats_nearby)
    pill.label == "Is this worth visiting?"       -> stringResource(Res.string.pill_is_worth_visiting)
    pill.label == "What am I missing?"            -> stringResource(Res.string.pill_what_am_i_missing)
    pill.label.startsWith("What's on tonight")    -> stringResource(Res.string.pill_whats_on_tonight, pill.label.substringAfter("in ").trimEnd('?'))
    pill.label.startsWith("Surprise me in")       -> stringResource(Res.string.pill_surprise_me_in, pill.label.substringAfter("in "))
    pill.label.startsWith("Tell me more about")   -> stringResource(Res.string.pill_tell_me_more_about, pill.label.substringAfter("about "))
    else                                          -> pill.label
}
