package com.harazone.ui.saved.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.VisitState
import com.harazone.ui.components.PlatformBackHandler
import androidx.compose.material3.Surface
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

@Composable
fun SavedPoiCard(
    poi: SavedPoi,
    isPendingUnsave: Boolean,
    onUnsave: () -> Unit,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    onAskAi: () -> Unit,
    onClick: () -> Unit = {},
    isEditingNote: Boolean = false,
    onStartEditingNote: () -> Unit = {},
    onNoteChanged: (String) -> Unit = {},
    onStopEditingNote: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var noteText by remember(poi.id, poi.userNote) { mutableStateOf(poi.userNote ?: "") }
    LaunchedEffect(isEditingNote, poi.id) {
        if (isEditingNote) focusRequester.requestFocus()
    }

    // Back button dismisses note editing with correct local text (fixes H1 — screen-level handler had stale DB value)
    PlatformBackHandler(enabled = isEditingNote) {
        onStopEditingNote(noteText)
    }

    // Flush on compose exit (sheet dismiss, card-switch recomposition) — uses rememberUpdatedState
    // to always capture the latest noteText and isEditingNote values
    val currentNoteText by rememberUpdatedState(noteText)
    val currentIsEditing by rememberUpdatedState(isEditingNote)
    val currentOnStop by rememberUpdatedState(onStopEditingNote)
    DisposableEffect(Unit) {
        onDispose {
            if (currentIsEditing) {
                currentOnStop(currentNoteText)
            }
        }
    }

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

            // Note area
            Spacer(Modifier.height(6.dp))
            if (isEditingNote) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = noteText,
                        onValueChange = { new ->
                            if (new.length <= 280) {
                                noteText = new
                                onNoteChanged(new)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f)),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                        // ImeAction.Default preserves the Enter key for newlines in this multi-line field.
                        // User dismisses editing via back button, tapping outside, or sheet dismiss.
                    )
                    if (noteText.length >= 240) {
                        Text(
                            text = "${280 - noteText.length}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (noteText.length >= 270) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            } else if (poi.userNote != null) {
                Row(
                    modifier = Modifier.clickable { onStartEditingNote() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = poi.userNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.Create,
                        contentDescription = "Edit note",
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.note_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.clickable { onStartEditingNote() },
                )
            }

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

                // VisitState badge
                if (poi.visitState != null) {
                    Spacer(Modifier.width(6.dp))
                    val (badgeColor, badgeLabel) = when (poi.visitState) {
                        VisitState.GO_NOW -> Color(0xFF4CAF50) to "Go Now"
                        VisitState.PLAN_SOON -> Color(0xFFFF9800) to "Plan Soon"
                        VisitState.WANT_TO_GO -> Color(0xFF9E9E9E) to "Want to Visit"
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeColor.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Saved date
                val monthAbbrevs = listOf(
                    stringResource(Res.string.month_jan), stringResource(Res.string.month_feb),
                    stringResource(Res.string.month_mar), stringResource(Res.string.month_apr),
                    stringResource(Res.string.month_may), stringResource(Res.string.month_jun),
                    stringResource(Res.string.month_jul), stringResource(Res.string.month_aug),
                    stringResource(Res.string.month_sep), stringResource(Res.string.month_oct),
                    stringResource(Res.string.month_nov), stringResource(Res.string.month_dec),
                )
                Text(
                    text = formatSavedDate(poi.savedAt, monthAbbrevs),
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

private fun formatSavedDate(epochMs: Long, months: List<String>): String {
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
