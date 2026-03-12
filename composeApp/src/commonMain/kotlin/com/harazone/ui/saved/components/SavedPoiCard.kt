package com.harazone.ui.saved.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.harazone.domain.model.SavedPoi

@Composable
fun SavedPoiCard(
    poi: SavedPoi,
    isPendingUnsave: Boolean,
    onUnsave: () -> Unit,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    onAskAi: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isPendingUnsave) 0.4f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1d24)),
    ) {
        // Image area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (poi.imageUrl != null) {
                AsyncImage(
                    model = poi.imageUrl,
                    contentDescription = poi.name,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFF2a2d34)),
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(gradientForType(poi.type)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emojiForType(poi.type),
                        fontSize = 44.sp,
                        modifier = Modifier.alpha(0.15f),
                    )
                }
            }
        }

        // TODO(BACKLOG-MEDIUM): Show userNote on SavedPoiCard — needs design feedback on placement, truncation, and styling
        // Card body
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = poi.areaName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            // Footer row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Type badge
                Text(
                    text = poi.type.replaceFirstChar { it.uppercaseChar() },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )

                Spacer(Modifier.width(8.dp))

                // Saved date
                Text(
                    text = formatSavedDate(poi.savedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f),
                )

                Spacer(Modifier.weight(1f))

                // 4 action buttons
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    IconButton(onClick = onUnsave, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Unsave",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDirections, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = "Directions",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onAskAi, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Ask AI",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun gradientForType(type: String): Brush {
    val t = type.lowercase()
    return when {
        t.contains("music") || t.contains("jazz") ->
            Brush.linearGradient(listOf(Color(0xFF6A1B9A), Color(0xFF9C27B0)))
        t.contains("food") || t.contains("restaurant") || t.contains("cafe") || t.contains("eat") ->
            Brush.linearGradient(listOf(Color(0xFFFF8F00), Color(0xFFFFA726)))
        t.contains("art") || t.contains("gallery") || t.contains("mural") || t.contains("street art") ->
            Brush.linearGradient(listOf(Color(0xFFFF8F00), Color(0xFFFFD54F)))
        t.contains("park") || t.contains("nature") || t.contains("garden") || t.contains("green") ->
            Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)))
        t.contains("museum") || t.contains("history") || t.contains("heritage") ->
            Brush.linearGradient(listOf(Color(0xFF00695C), Color(0xFF26A69A)))
        else ->
            Brush.linearGradient(listOf(Color(0xFF1a1d24), Color(0xFF1e2128)))
    }
}

private fun emojiForType(type: String): String {
    val t = type.lowercase()
    return when {
        t.contains("music") || t.contains("jazz") -> "\uD83C\uDFB5"
        t.contains("food") || t.contains("restaurant") || t.contains("cafe") -> "\uD83C\uDF7D\uFE0F"
        t.contains("art") || t.contains("gallery") || t.contains("mural") -> "\uD83C\uDFA8"
        t.contains("park") || t.contains("nature") || t.contains("garden") -> "\uD83C\uDF3F"
        t.contains("museum") || t.contains("history") || t.contains("heritage") -> "\uD83C\uDFDB\uFE0F"
        else -> "\uD83D\uDCCD"
    }
}

private fun formatSavedDate(epochMs: Long): String {
    val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    // Civil date from epoch ms — accounts for leap years
    var days = (epochMs / 86_400_000).toInt()
    var year = 1970
    while (true) {
        val daysInYear = if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 366 else 365
        if (days < daysInYear) break
        days -= daysInYear
        year++
    }
    val isLeap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val monthDays = intArrayOf(31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 0
    while (month < 11 && days >= monthDays[month]) {
        days -= monthDays[month]
        month++
    }
    return "${months[month]} ${days + 1}"
}
