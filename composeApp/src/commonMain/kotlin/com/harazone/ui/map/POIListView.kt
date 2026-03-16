package com.harazone.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.POI
import com.harazone.ui.theme.MapFloatingUiDark
import com.harazone.ui.theme.spacing
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

private val CardScrimGradient = Brush.verticalGradient(
    listOf(Color.Black.copy(0.1f), Color.Black.copy(0.75f))
)

@Composable
fun POIListView(
    pois: List<POI>,
    dynamicVibes: List<DynamicVibe>,
    activeDynamicVibe: DynamicVibe?,
    onDynamicVibeSelected: (DynamicVibe) -> Unit,
    onPoiClick: (POI) -> Unit,
    onVisitTapped: (POI) -> Unit,
    onUnvisitTapped: (POI) -> Unit,
    onNavigateTapped: (POI) -> Unit,
    onChatTapped: (POI) -> Unit,
    modifier: Modifier = Modifier,
    visitedPoiIds: Set<String> = emptySet(),
) {
    Column(modifier = modifier) {
        // Dynamic vibe chip strip
        if (dynamicVibes.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            ) {
                items(dynamicVibes, key = { it.label }) { vibe ->
                    FilterChip(
                        selected = vibe.label == activeDynamicVibe?.label,
                        onClick = { onDynamicVibeSelected(vibe) },
                        label = { Text("${vibe.icon} ${vibe.label}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        if (pois.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (activeDynamicVibe != null)
                        stringResource(Res.string.poi_list_empty_vibe, activeDynamicVibe.label)
                    else
                        stringResource(Res.string.poi_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(pois, key = { it.savedId }) { poi ->
                    val isVisited = poi.savedId in visitedPoiIds
                    PoiListCard(
                        poi = poi,
                        isVisited = isVisited,
                        onClick = { onPoiClick(poi) },
                        onVisitToggled = { if (isVisited) onUnvisitTapped(poi) else onVisitTapped(poi) },
                        onNavigateTapped = { onNavigateTapped(poi) },
                        onChatTapped = { onChatTapped(poi) },
                    )
                }
                item { Spacer(Modifier.height(MaterialTheme.spacing.sm)) }
            }
        }
    }
}

@Composable
private fun PoiListCard(
    poi: POI,
    isVisited: Boolean,
    onClick: () -> Unit,
    onVisitToggled: () -> Unit,
    onNavigateTapped: () -> Unit,
    onChatTapped: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MapFloatingUiDark)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${poi.name}, ${poi.type}" },
    ) {
        // Background image
        if (poi.imageUrl != null) {
            AsyncImage(
                model = poi.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Dark gradient scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(CardScrimGradient)
        )
        // Content overlay
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.align(Alignment.TopStart)) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val capitalizedType = poi.type.replaceFirstChar { it.uppercaseChar() }
                val subtitle = if (poi.vibe.isNotBlank())
                    "$capitalizedType \u00B7 ${poi.vibe}"
                else
                    capitalizedType
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                if (poi.insight.isNotBlank()) {
                    Text(
                        text = poi.insight,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Rating + live status row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (poi.rating != null) {
                        Text(
                            "\u2B50 ${poi.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                    if (poi.liveStatus != null) {
                        val statusColor = liveStatusToColor(resolveStatus(poi.liveStatus, poi.hours)).toComposeColor()
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            poi.liveStatus.replaceFirstChar { it.uppercaseChar() },
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                    }
                }
            }
            // Icon CTAs
            Row(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onVisitToggled,
                    modifier = Modifier.semantics {
                        contentDescription = if (isVisited) "Visited" else "Mark as Visit"
                    },
                ) {
                    Icon(
                        imageVector = if (isVisited) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (isVisited) Color(0xFF4CAF50) else Color.White,
                    )
                }
                if (poi.latitude != null && poi.longitude != null) {
                    IconButton(
                        onClick = onNavigateTapped,
                        modifier = Modifier.semantics {
                            contentDescription = "Navigate to ${poi.name}"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
                IconButton(
                    onClick = onChatTapped,
                    modifier = Modifier.semantics {
                        contentDescription = "Ask AI about ${poi.name}"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
