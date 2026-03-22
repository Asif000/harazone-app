package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.DynamicVibe

/**
 * Horizontal scrollable row of vibe filter chips.
 * Multi-select OR filter. Active = filled background, inactive = outlined.
 * Named vibe chips use vibe color; ad-hoc chips use neutral grey.
 */
@Composable
fun VibeChipRow(
    vibes: List<DynamicVibe>,
    activeVibeFilters: Set<String>,
    adHocFilters: List<String>,
    onVibeToggle: (String) -> Unit,
    onAdHocRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Named vibe chips
        for (vibe in vibes) {
            val isActive = vibe.label.trim().lowercase() in activeVibeFilters.map { it.trim().lowercase() }
            VibeChip(
                label = "${vibe.icon} ${vibe.label}",
                isActive = isActive,
                chipColor = vibeColor(vibe.label),
                onClick = { onVibeToggle(vibe.label) },
                contentDesc = "${vibe.label} filter, ${if (isActive) "active" else "inactive"}",
            )
        }

        // Ad-hoc filter chips (non-vibe search terms)
        for (adHoc in adHocFilters) {
            AdHocChip(
                label = adHoc,
                onRemove = { onAdHocRemove(adHoc) },
            )
        }
    }
}

@Composable
private fun VibeChip(
    label: String,
    isActive: Boolean,
    chipColor: Color,
    onClick: () -> Unit,
    contentDesc: String,
) {
    val shape = RoundedCornerShape(20.dp)
    val bgColor = if (isActive) chipColor.copy(alpha = 0.8f) else Color.Transparent
    val borderColor = if (isActive) Color.Transparent else chipColor.copy(alpha = 0.5f)
    val textColor = if (isActive) Color.White else chipColor

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = contentDesc },
    )
}

@Composable
private fun AdHocChip(
    label: String,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(Color.Gray.copy(alpha = 0.3f))
            .border(1.dp, Color.Gray.copy(alpha = 0.4f), shape)
            .clickable(onClick = onRemove)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .semantics { contentDescription = "$label custom filter, tap to remove" },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(start = 4.dp)
                .size(14.dp),
        )
    }
}

/** Map vibe label to a distinct chip color. */
private fun vibeColor(label: String): Color = when (label.trim().lowercase()) {
    "character" -> Color(0xFF42A5F5)
    "food & drink" -> Color(0xFFFF7043)
    "arts" -> Color(0xFFAB47BC)
    "nightlife" -> Color(0xFFEC407A)
    "nature" -> Color(0xFF66BB6A)
    "history" -> Color(0xFFFFA726)
    else -> Color(0xFF78909C)
}
