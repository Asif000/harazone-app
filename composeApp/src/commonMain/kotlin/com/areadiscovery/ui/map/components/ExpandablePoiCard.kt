package com.areadiscovery.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.IconButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.ui.theme.MapSurfaceDark
import com.areadiscovery.ui.theme.toColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandablePoiCard(
    poi: POI,
    activeVibe: Vibe?,
    onDismiss: () -> Unit,
    onDirectionsClick: (Double, Double, String) -> Unit,
    onAskAiClick: (String) -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val poiVibe = Vibe.entries.firstOrNull { poi.vibe.contains(it.name, ignoreCase = true) }
    val vibeColor = (activeVibe ?: poiVibe ?: Vibe.DEFAULT).toColor()

    Column(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(16.dp))
            .background(MapSurfaceDark.copy(alpha = 0.97f))
            .border(1.dp, vibeColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            // Gradient always visible as base layer (fallback when no image)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(vibeColor.copy(alpha = 0.6f), vibeColor.copy(alpha = 0.2f))
                        )
                    ),
            )
            // Image overlaid on top if URL is available
            if (poi.imageUrl != null) {
                AsyncImage(
                    model = poi.imageUrl,
                    contentDescription = poi.name,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(vibeColor.copy(alpha = 0.15f)),
                    modifier = Modifier.matchParentSize(),
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
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Name + type
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = poi.type.replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )

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

            // Insight
            if (poi.insight.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = poi.insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action chips
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
                    onClick = { saved = !saved; onSaveClick() },
                    label = { Text(if (saved) "Saved" else "Save") },
                    leadingIcon = {
                        Icon(
                            if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = chipColors,
                    border = chipBorder,
                )
                AssistChip(
                    onClick = onShareClick,
                    label = { Text("Share") },
                    leadingIcon = {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = chipColors,
                    border = chipBorder,
                )
                if (poi.latitude != null && poi.longitude != null) {
                    AssistChip(
                        onClick = { onDirectionsClick(poi.latitude!!, poi.longitude!!, poi.name) },
                        label = { Text("Directions") },
                        leadingIcon = {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = chipColors,
                        border = chipBorder,
                    )
                }
                AssistChip(
                    onClick = { onAskAiClick("Tell me more about ${poi.name}") },
                    label = { Text("Ask AI") },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = chipColors,
                    border = chipBorder,
                )
            }

            // Expandable section
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = poi.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    if (poi.hours != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Hours: ${poi.hours}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    if (poi.vibeInsights.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Vibe Insights",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        for ((vibeName, insight) in poi.vibeInsights) {
                            val vibe = Vibe.entries.find { it.name.equals(vibeName, ignoreCase = true) }
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(8.dp)
                                        .background(vibe?.toColor() ?: Color.Gray, CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = insight,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) "Less" else "More details",
                    color = vibeColor,
                )
            }
        }
    }
}

@Composable
private fun LiveStatusBadge(status: String) {
    val (color, label) = when (status.lowercase()) {
        "open" -> Color(0xFF4CAF50) to "Open"
        "busy" -> Color(0xFFFF9800) to "Busy"
        "closed" -> Color(0xFF9E9E9E) to "Closed"
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
    val activityLabel = when (liveStatus.lowercase()) {
        "busy" -> "Busy"
        "open" -> "Open"
        "closed" -> "Closed"
        else -> "Unknown"
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.semantics { contentDescription = "Activity: $activityLabel" },
    ) {
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
