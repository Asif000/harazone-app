package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.POI
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.ui.map.liveStatusToColor
import com.harazone.ui.map.resolveStatus
import com.harazone.ui.map.toComposeColor
import com.harazone.ui.theme.MapFloatingUiDark
import com.harazone.util.poiTypeEmoji
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.drop

private const val MAX_DOTS = 9

@Composable
fun PoiCarousel(
    pois: List<POI>,
    selectedIndex: Int?,
    visitedPoiIds: Set<String>,
    onCardSwiped: (Int) -> Unit,
    onSelectionCleared: () -> Unit,
    onVisitTapped: (POI) -> Unit,
    onDetailTapped: (POI) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(listState)
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        try {
            isProgrammaticScroll = true
            if (selectedIndex != null) listState.animateScrollToItem(selectedIndex)
        } finally {
            isProgrammaticScroll = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .drop(1)
            .collect { if (!isProgrammaticScroll) onCardSwiped(it) }
    }

    PlatformBackHandler(enabled = selectedIndex != null) { onSelectionCleared() }

    BoxWithConstraints(modifier = modifier) {
        val screenWidth = maxWidth

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LazyRow(
                state = listState,
                flingBehavior = snapBehavior,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pois, key = { it.savedId }) { poi ->
                    val liveStatusColor = liveStatusToColor(resolveStatus(poi.liveStatus, poi.hours)).toComposeColor()
                    val isVisited = poi.savedId in visitedPoiIds
                    Box(
                        Modifier
                            .width(screenWidth - 32.dp)
                            .height(148.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MapFloatingUiDark.copy(0.94f))
                            .clickable { onDetailTapped(poi) }
                    ) {
                        // Background image
                        if (poi.imageUrl != null) {
                            AsyncImage(
                                model = poi.imageUrl,
                                contentDescription = poi.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // Gradient scrim for text readability
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.3f),
                                            Color.Black.copy(alpha = 0.85f),
                                        )
                                    )
                                )
                        )
                        Row(Modifier.fillMaxHeight()) {
                            // Status stripe
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(liveStatusColor)
                            )
                            // Content
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(12.dp)
                            ) {
                                // Emoji + name
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = poiTypeEmoji(poi.type),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = poi.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // Vibe label
                                Text(
                                    text = poi.vibe,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                // Insight (vibe summary)
                                if (poi.insight.isNotBlank()) {
                                    Text(
                                        text = poi.insight,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                // Meta row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (poi.rating != null) {
                                        Text(
                                            "⭐ ${poi.rating}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                        )
                                    }
                                    if (poi.priceRange != null) {
                                        Text(
                                            " · ${poi.priceRange}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                        )
                                    }
                                    if (poi.hours != null) {
                                        val hourStatus = liveStatusToColor(resolveStatus(poi.liveStatus, poi.hours))
                                        Text(
                                            " · ${poi.hours} ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                        )
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(hourStatus.toComposeColor())
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                // CTA row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isVisited) {
                                        TextButton(onClick = {}, enabled = false) {
                                            Text("✓ Visited", color = Color(0xFF4CAF50).copy(alpha = 0.7f))
                                        }
                                    } else {
                                        TextButton(onClick = { onVisitTapped(poi) }) {
                                            Text("Visit", color = Color.White)
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { onDetailTapped(poi) }) {
                                        Text("Details →", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Page dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val visibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                val dotCount = minOf(pois.size, MAX_DOTS)
                (0 until dotCount).forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == visibleIndex) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == visibleIndex) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}
