package com.areadiscovery.ui.map

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.ui.theme.MapSurfaceDark
import com.areadiscovery.ui.theme.spacing
import com.areadiscovery.ui.theme.toColor

@Composable
fun POIListView(
    pois: List<POI>,
    activeVibe: Vibe?,
    onVibeSelected: (Vibe) -> Unit,
    onPoiClick: (POI) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredPois = if (activeVibe != null) {
        pois.filter { it.vibe.contains(activeVibe.name, ignoreCase = true) }
    } else {
        pois
    }

    Column(modifier = modifier) {
        // Vibe chip strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
        ) {
            items(Vibe.entries.toList()) { vibe ->
                FilterChip(
                    selected = vibe == activeVibe,
                    onClick = { onVibeSelected(vibe) },
                    label = { Text(vibe.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = vibe.toColor().copy(alpha = 0.8f),
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }

        if (filteredPois.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (activeVibe != null) "No places found for ${activeVibe.displayName}"
                           else "No places found for this area",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredPois, key = { "${it.name}_${it.type}" }) { poi ->
                    PoiListCard(poi = poi, onClick = { onPoiClick(poi) })
                }
                item { Spacer(Modifier.height(MaterialTheme.spacing.sm)) }
            }
        }
    }
}

@Composable
private fun PoiListCard(poi: POI, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${poi.name}, ${poi.type}"
            },
        colors = CardDefaults.cardColors(containerColor = MapSurfaceDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = poi.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Text(
                        text = poi.type.replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                if (poi.rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${poi.rating}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
                if (poi.liveStatus != null) {
                    Spacer(Modifier.width(8.dp))
                    val statusColor = when (poi.liveStatus.lowercase()) {
                        "open" -> Color(0xFF4CAF50)
                        "busy" -> Color(0xFFFF9800)
                        else -> Color(0xFF9E9E9E)
                    }
                    Text(
                        text = poi.liveStatus.replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
            }
            if (poi.insight.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = poi.insight,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
