package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.CompanionNudge

private val CardBg = Color(0xFFF5F2EF)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF888888)
private val AccentOrange = Color(0xFFFF9800)

/**
 * Peek card that slides up from the bottom bar showing nudge details.
 * Swipe up → opens full chat. Swipe down or tap dismiss → closes.
 * Z-order: renders above POI carousel. (M4, D15)
 */
@Composable
fun PeekCard(
    nudge: CompanionNudge,
    visible: Boolean,
    onTellMeMore: () -> Unit,
    onDismiss: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(CardBg)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -40f) onSwipeUp()
                        if (dragAmount > 40f) onDismiss()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Header: orb icon + dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\u2726",
                    fontSize = 16.sp,
                    color = AccentOrange,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Nudge text
            Text(
                text = nudge.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onTellMeMore) {
                    Text("Tell me more \u2192", color = AccentOrange)
                }
            }
        }
    }
}
