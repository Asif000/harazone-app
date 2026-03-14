package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.POI
import com.harazone.ui.theme.MapFloatingUiDark

@Composable
fun FloatingPoiCard(
    poi: POI,
    isSaved: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSaved) Color(0xFFFFD700).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .clickable { onTap() }
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.94f))
            .padding(12.dp, 10.dp),
    ) {
        Column {
            // Vibe label
            Text(
                text = poi.vibes.firstOrNull() ?: poi.vibe,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Name
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Rating
            if (poi.rating != null) {
                Text(
                    text = "\u2605 ${poi.rating}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFD700),
                )
            }
        }
        if (isSaved) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Saved",
                tint = Color(0xFFFFD700),
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopEnd),
            )
        }
    }
}
