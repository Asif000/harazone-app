package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.POI
import com.harazone.ui.theme.MapFloatingUiDark

/** Maps a vibe name to its accent color. Mirrors the lookup in VibeRail.kt. */
private fun vibeAccentColor(vibeName: String?): Color = when (vibeName?.lowercase()) {
    "food", "eat"       -> Color(0xFFFF7043)
    "coffee"            -> Color(0xFF8D6E63)
    "drinks", "bar"     -> Color(0xFF7E57C2)
    "outdoors", "park"  -> Color(0xFF66BB6A)
    "culture", "art"    -> Color(0xFFEC407A)
    "shopping"          -> Color(0xFF29B6F6)
    "nightlife"         -> Color(0xFFAB47BC)
    "wellness", "spa"   -> Color(0xFF26C6DA)
    else                -> Color(0xFF90CAF9)  // default — soft blue
}

@Composable
fun PinMiniChip(
    poi: POI,
    isSaved: Boolean,
    isHero: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClosed = poi.liveStatus?.contains("closed", ignoreCase = true) == true
    val isOpen = poi.liveStatus?.contains("open", ignoreCase = true) == true
    val statusColor = when {
        isOpen   -> Color(0xFF4CAF50)
        isClosed -> Color(0xFFF44336)
        else     -> Color(0xFFFFAB40)
    }
    val vibeColor = vibeAccentColor(poi.vibes.firstOrNull() ?: poi.vibe)
    val borderColor = when {
        isSaved  -> Color(0xFFFFD700).copy(alpha = 0.7f)
        isHero   -> Color.White.copy(alpha = 0.4f)
        else     -> Color.White.copy(alpha = 0.1f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 120.dp)
            .alpha(if (isClosed) 0.5f else 1f)
            .clickable { onClick() }
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.94f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Dual-ring status dot: outer ring = vibe color, inner fill = status color
        Box(
            modifier = Modifier
                .size(12.dp)
                .border(2.dp, vibeColor, CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = poi.name,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (isClosed) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (poi.rating != null) {
                Text(
                    text = "★ ${poi.rating}",
                    fontSize = 10.sp,
                    color = Color(0xFFFFD700),
                )
            }
        }
    }
}
