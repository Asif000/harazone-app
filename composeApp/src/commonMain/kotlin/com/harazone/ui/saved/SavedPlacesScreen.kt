package com.harazone.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harazone.domain.model.SavedPoi
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.ui.saved.components.SavedPoiCard
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// TODO(BACKLOG-MEDIUM): Centralize hardcoded colors (storyPurple, storyGold, screenBg, card colors) into theme or CompositionLocal — makes future theming possible
private val storyPurple = Color(0xFF7C4DFF)
private val storyGold = Color(0xFFFFD700)
private val screenBg = Color(0xFF0f1115)

@Composable
fun SavedPlacesScreen(
    userLat: Double?,
    userLng: Double?,
    onDismiss: () -> Unit,
    onAskAi: (poi: SavedPoi?) -> Unit,
    onDirections: (Double, Double, String) -> Unit,
    onShare: (String) -> Unit,
    viewModel: SavedPlacesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(userLat, userLng) {
        viewModel.onLocationUpdated(userLat, userLng)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.commitAllPendingUnsaves() }
    }

    PlatformBackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarPadding + 12.dp, start = 16.dp, end = 16.dp),
            ) {
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Saved Places",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = "${uiState.saves.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                // Search bar (hidden when empty)
                if (uiState.saves.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        "Search saves...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.3f),
                                    )
                                }
                                BasicTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.White),
                                )
                            }
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChanged("") },
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // TODO(BACKLOG-HIGH): Capsule row has no scroll affordance — user can't tell more capsules exist off-screen. Also long area names hog space. Brainstorm: scroll hint, label truncation strategy, multi-level grouping, wrap layout. Critical for international saves.
                // Capsule row (hidden when empty)
                if (uiState.saves.isNotEmpty() && uiState.capsules.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.capsules, key = { it.label }) { capsule ->
                            val isActive = capsule.label == uiState.activeCapsule ||
                                (uiState.activeCapsule == null && capsule.label == "All")
                            FilterChip(
                                selected = isActive,
                                onClick = {
                                    viewModel.selectCapsule(
                                        if (capsule.label == "All") null else capsule.label,
                                    )
                                },
                                label = {
                                    Text(
                                        "${capsule.label} (${capsule.count})",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    labelColor = Color.White.copy(alpha = 0.7f),
                                    selectedContainerColor = if (capsule.isNearest) Color(0xFF2E7D32).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
                                    selectedLabelColor = Color.White,
                                ),
                                border = if (capsule.isNearest && isActive) {
                                    FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = true,
                                        borderColor = Color(0xFF4CAF50).copy(alpha = 0.4f),
                                        selectedBorderColor = Color(0xFF4CAF50).copy(alpha = 0.4f),
                                    )
                                } else {
                                    FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = true,
                                        borderColor = Color.White.copy(alpha = 0.1f),
                                        selectedBorderColor = Color.White.copy(alpha = 0.2f),
                                    )
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Scrollable content
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    bottom = 80.dp + navBarPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Discovery Story card
                if (uiState.discoveryStory != null) {
                    item(key = "discovery_story") {
                        DiscoveryStoryCard(story = uiState.discoveryStory!!)
                    }
                }

                // Empty state
                if (uiState.filteredSaves.isEmpty() && uiState.saves.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No saved places yet — start exploring!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    }
                }

                // No results for filter
                if (uiState.filteredSaves.isEmpty() && uiState.saves.isNotEmpty()) {
                    item(key = "no_results") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No matching saves",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    }
                }

                // TODO(BACKLOG-MEDIUM): Add Modifier.animateItem() to card items for smooth removal animation (requires Compose 1.7+)
                // Save cards
                items(uiState.filteredSaves, key = { it.id }) { poi ->
                    SavedPoiCard(
                        poi = poi,
                        isPendingUnsave = poi.id in uiState.pendingUnsaveIds,
                        onUnsave = {
                            viewModel.unsavePoi(poi.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Removed",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long,
                                )
                                viewModel.commitUnsave(
                                    poi.id,
                                    undo = result == SnackbarResult.ActionPerformed,
                                )
                            }
                        },
                        onDirections = { onDirections(poi.lat, poi.lng, poi.name) },
                        onShare = { onShare(poi.name) },
                        onAskAi = { onAskAi(poi) },
                    )
                }
            }
        }

        // TODO(BACKLOG-LOW): Bottom bar uses translucent bg without blur — list content ghosts through on scroll. Make opaque or add gradient fade.
        // Fixed bottom bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(screenBg.copy(alpha = 0.95f))
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                )
                .padding(bottom = navBarPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onAskAi(null) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = storyPurple,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Ask AI about saves...",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Map",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp + navBarPadding),
        )
    }
}

@Composable
private fun DiscoveryStoryCard(story: DiscoveryStory, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        storyPurple.copy(alpha = 0.07f),
                        storyGold.copy(alpha = 0.04f),
                    ),
                ),
                RoundedCornerShape(20.dp),
            )
            .border(1.dp, storyPurple.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "YOUR DISCOVERY STORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = storyPurple,
                )
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = storyPurple.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                story.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            if (story.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // TODO(BACKLOG-MEDIUM): Wire discovery story tag chips to filter/search action instead of no-op onClick
                    story.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = storyPurple.copy(alpha = 0.1f),
                                labelColor = Color.White.copy(alpha = 0.7f),
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = storyPurple.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }
        }
    }
}
