package com.areadiscovery.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.areadiscovery.domain.model.GeocodingSuggestion
import com.areadiscovery.domain.model.RecentPlace
import com.areadiscovery.ui.theme.MapFloatingUiDark

@Composable
fun GeocodingSearchBar(
    query: String,
    suggestions: List<GeocodingSuggestion>,
    isGeocodingLoading: Boolean,
    selectedPlace: String?,
    isSearchingArea: Boolean,
    isGeocodingInitiatedSearch: Boolean,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (GeocodingSuggestion) -> Unit,
    onSubmitEmpty: () -> Unit,
    onClear: () -> Unit,
    onCancelLoad: () -> Unit,
    recentPlaces: List<RecentPlace> = emptyList(),
    onRecentSelected: (RecentPlace) -> Unit = {},
    onClearRecents: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spinning = isSearchingArea
    val showCancel = isSearchingArea && isGeocodingInitiatedSearch
    val selected = !spinning && selectedPlace != null

    // Track whether the text field is focused (user tapped to type)
    var isFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Active = focused OR has query/suggestions
    val active = !spinning && selectedPlace == null && (isFieldFocused || query.isNotBlank() || suggestions.isNotEmpty())

    Box(modifier = modifier) {
        // Reset focus when transitioning to spinning or selected
        if (spinning || selected) {
            SideEffect { isFieldFocused = false }
        }

        when {
            spinning -> SpinningState(showCancel, onCancelLoad)
            selected -> SelectedState(selectedPlace ?: "", onClear)
            active -> ActiveState(
                query = query,
                suggestions = suggestions,
                isGeocodingLoading = isGeocodingLoading,
                onQueryChanged = onQueryChanged,
                onSuggestionSelected = {
                    isFieldFocused = false
                    focusManager.clearFocus()
                    onSuggestionSelected(it)
                },
                onClear = {
                    isFieldFocused = false
                    focusManager.clearFocus()
                    onClear()
                },
                onSubmitEmpty = {
                    isFieldFocused = false
                    focusManager.clearFocus()
                    onSubmitEmpty()
                },
                recentPlaces = recentPlaces,
                onRecentSelected = {
                    isFieldFocused = false
                    focusManager.clearFocus()
                    onRecentSelected(it)
                },
                onClearRecents = onClearRecents,
                requestFocus = isFieldFocused && query.isBlank() && suggestions.isEmpty(),
                onFocusChanged = { isFieldFocused = it },
            )
            else -> IdleState(onTap = { isFieldFocused = true })
        }
    }
}

@Composable
private fun IdleState(onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MapFloatingUiDark.copy(alpha = 0.90f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onTap() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Search a place or refresh area",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ActiveState(
    query: String,
    suggestions: List<GeocodingSuggestion>,
    isGeocodingLoading: Boolean,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (GeocodingSuggestion) -> Unit,
    onClear: () -> Unit,
    onSubmitEmpty: () -> Unit,
    recentPlaces: List<RecentPlace>,
    onRecentSelected: (RecentPlace) -> Unit,
    onClearRecents: () -> Unit,
    requestFocus: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val hasRecents = query.isBlank() && recentPlaces.isNotEmpty()
        val hasDropdown = query.isNotBlank() && suggestions.isNotEmpty()
        val showPanel = hasRecents || hasDropdown
        Surface(
            shape = if (showPanel) {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            } else {
                RoundedCornerShape(50)
            },
            color = MapFloatingUiDark.copy(alpha = 0.95f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isBlank()) onSubmitEmpty()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search a place\u2026",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.4f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotBlank() || isGeocodingLoading) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onClear() },
                    )
                } else {
                    // Show refresh icon when query is empty — tapping refreshes current area
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh area",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onSubmitEmpty() },
                    )
                }
            }
        }
        if (showPanel) {
            Surface(
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MapFloatingUiDark.copy(alpha = 0.95f),
            ) {
                Column {
                    if (hasRecents) {
                        recentPlaces.forEachIndexed { i, recent ->
                            if (i > 0) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                            }
                            RecentPlaceRow(recent, onRecentSelected)
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClearRecents() }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = "Clear history",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    } else {
                        suggestions.forEachIndexed { i, s ->
                            if (i > 0) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                            }
                            SuggestionRow(s, query, onSuggestionSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: GeocodingSuggestion,
    query: String,
    onSelected: (GeocodingSuggestion) -> Unit,
) {
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
            tint = MaterialTheme.colorScheme.error,
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

@Composable
private fun RecentPlaceRow(
    recent: RecentPlace,
    onSelected: (RecentPlace) -> Unit,
) {
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
            tint = MaterialTheme.colorScheme.error,
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

@Composable
private fun SelectedState(selectedPlace: String, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MapFloatingUiDark.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = selectedPlace,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear selection",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onClear() },
            )
        }
    }
}

@Composable
private fun SpinningState(showCancel: Boolean, onCancelLoad: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MapFloatingUiDark.copy(alpha = 0.90f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Refreshing area\u2026",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (showCancel) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel loading",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onCancelLoad() },
                )
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
