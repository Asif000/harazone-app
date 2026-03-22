package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.GeocodingSuggestion
import com.harazone.domain.model.RecentPlace
import com.harazone.domain.model.WeatherState
import com.harazone.ui.components.currentHour
import com.harazone.ui.components.currentMinute
import com.harazone.ui.components.currentTimeMillis
import com.harazone.ui.theme.MapFloatingUiDark
import kotlinx.coroutines.delay

/**
 * Expanded Discovery Header panel — search, context grid, vibe chips,
 * intel strip, action buttons, recent explorations.
 */
@Composable
fun SmartSearchPanel(
    // Search
    query: String,
    suggestions: List<GeocodingSuggestion>,
    isGeocodingLoading: Boolean,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (GeocodingSuggestion) -> Unit,
    onSubmitEmpty: () -> Unit,
    onClear: () -> Unit,
    recentPlaces: List<RecentPlace>,
    onRecentSelected: (RecentPlace) -> Unit,
    onClearRecents: () -> Unit,
    // Context
    weather: WeatherState?,
    visitTag: String,
    // Vibes
    vibes: List<DynamicVibe>,
    activeVibeFilters: Set<String>,
    adHocFilters: List<String>,
    onVibeToggle: (String) -> Unit,
    onAdHocRemove: (String) -> Unit,
    // Intel strip
    areaHighlights: List<String>,
    onIntelTapped: (String) -> Unit,
    // Actions
    onSurprise: () -> Unit,
    onRefresh: () -> Unit,
    // Recent explorations
    recentExplorations: List<RecentPlace>,
    onTeleport: (RecentPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.95f))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        // 1. Search input
        SearchInput(
            query = query,
            isLoading = isGeocodingLoading,
            onQueryChanged = onQueryChanged,
            onClear = onClear,
            onSubmitEmpty = onSubmitEmpty,
            focusRequester = focusRequester,
        )

        // Search results
        if (query.isNotBlank() && suggestions.isNotEmpty()) {
            SearchResults(query = query, suggestions = suggestions, onSelected = onSuggestionSelected)
        } else if (query.isBlank() && recentPlaces.isNotEmpty()) {
            RecentPlaces(recents = recentPlaces, onSelected = onRecentSelected, onClear = onClearRecents)
        }

        Spacer(Modifier.height(12.dp))

        // 2. Context grid (weather, time, visit)
        ContextGrid(weather = weather, visitTag = visitTag)

        Spacer(Modifier.height(12.dp))

        // 3. Vibe filter chips
        VibeChipRow(
            vibes = vibes,
            activeVibeFilters = activeVibeFilters,
            adHocFilters = adHocFilters,
            onVibeToggle = onVibeToggle,
            onAdHocRemove = onAdHocRemove,
        )

        Spacer(Modifier.height(12.dp))

        // 4. Intel strip
        if (areaHighlights.isNotEmpty()) {
            IntelStrip(highlights = areaHighlights, onTap = onIntelTapped)
            Spacer(Modifier.height(12.dp))
        }

        // 5. Action buttons
        ActionButtons(onSurprise = onSurprise, onRefresh = onRefresh)

        // 6. Recent explorations (hidden if empty)
        if (recentExplorations.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RecentExplorations(recents = recentExplorations.take(3), onTap = onTeleport)
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    isLoading: Boolean,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSubmitEmpty: () -> Unit,
    focusRequester: FocusRequester,
) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                singleLine = true,
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isBlank()) onSubmitEmpty()
                }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search places, areas, or vibes...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotBlank()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onClear() },
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    suggestions: List<GeocodingSuggestion>,
    onSelected: (GeocodingSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        suggestions.forEachIndexed { i, suggestion ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = Color(0xFF26A69A),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            val lower = suggestion.name.lowercase()
                            val q = query.trim().lowercase()
                            val idx = lower.indexOf(q)
                            if (idx >= 0 && q.isNotEmpty()) {
                                append(suggestion.name.substring(0, idx))
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                    append(suggestion.name.substring(idx, idx + q.length))
                                }
                                append(suggestion.name.substring(idx + q.length))
                            } else {
                                append(suggestion.name)
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = suggestion.fullAddress,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (suggestion.distanceKm != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatDistance(suggestion.distanceKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentPlaces(
    recents: List<RecentPlace>,
    onSelected: (RecentPlace) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        recents.forEachIndexed { i, recent ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(recent) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = Color(0xFF26A69A),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = recent.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClear() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text = "Clear history",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun ContextGrid(
    weather: WeatherState?,
    visitTag: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        ContextTile(
            text = if (weather != null) "${weather.emoji} ${weather.temperatureF}\u00B0F" else "\u2026",
            modifier = Modifier.weight(1f),
        )
        ContextTile(
            text = formatCurrentTime(weather?.utcOffsetSeconds),
            modifier = Modifier.weight(1f),
        )
        ContextTile(
            text = visitTag,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ContextTile(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun IntelStrip(
    highlights: List<String>,
    onTap: (String) -> Unit,
) {
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(highlights) {
        currentIndex = 0
        if (highlights.size > 1) {
            while (true) {
                delay(8_000)
                currentIndex = (currentIndex + 1) % highlights.size
            }
        }
    }

    val current = highlights[currentIndex.coerceIn(highlights.indices)]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 2.dp,
                color = Color(0xFF26A69A).copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onTap(current) }
            .padding(12.dp),
    ) {
        // Teal accent border left is handled by the border above
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "intel_rotation",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF26A69A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionButtons(
    onSurprise: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Surprise (primary, purple)
        Surface(
            onClick = onSurprise,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF7C4DFF),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "\u2728 Surprise",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        // Refresh
        Surface(
            onClick = onRefresh,
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Refresh",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RecentExplorations(
    recents: List<RecentPlace>,
    onTap: (RecentPlace) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Recent explorations",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        recents.forEach { recent ->
            Surface(
                onClick = { onTap(recent) },
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color(0xFF26A69A),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = recent.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatDistance(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).toInt()} m"
    else -> {
        val rounded = kotlin.math.round(km * 10) / 10.0
        if (rounded % 1.0 == 0.0) "${rounded.toInt()} km" else "$rounded km"
    }
}

private fun formatCurrentTime(utcOffsetSeconds: Int?): String {
    val hour: Int
    val minute: Int
    if (utcOffsetSeconds != null) {
        val nowUtcMs = currentTimeMillis()
        val localMs = nowUtcMs + (utcOffsetSeconds * 1000L)
        val totalMinutes = (localMs / 60_000) % (24 * 60)
        hour = (totalMinutes / 60).toInt()
        minute = (totalMinutes % 60).toInt()
    } else {
        hour = currentHour()
        minute = currentMinute()
    }
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
}
