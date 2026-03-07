package com.areadiscovery.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.ui.components.rememberReduceMotion
import com.areadiscovery.ui.theme.toColor

fun Vibe.toImageVector(): ImageVector = when (this) {
    Vibe.CHARACTER -> Icons.Default.Palette
    Vibe.HISTORY -> Icons.Default.History
    Vibe.WHATS_ON -> Icons.Default.Event
    Vibe.SAFETY -> Icons.Default.Shield
    Vibe.NEARBY -> Icons.Default.Explore
    Vibe.COST -> Icons.Default.Payments
}

@Composable
fun VibeOrb(
    vibe: Vibe,
    isActive: Boolean,
    poiCount: Int,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 0.9f,
        label = "orb_scale",
    )
    val reduceMotion = rememberReduceMotion()
    val breathingAlpha = if (isActive && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "breathing")
        val alpha by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
            label = "breathing_alpha",
        )
        alpha
    } else {
        if (isActive) 1.0f else 0.9f
    }
    val bgAlpha = if (isActive) 0.9f else 0.4f
    val vibeColor = vibe.toColor()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .background(
                color = vibeColor.copy(alpha = bgAlpha * breathingAlpha),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${vibe.displayName}, $poiCount places" },
    ) {
        Icon(
            imageVector = vibe.toImageVector(),
            contentDescription = vibe.displayName,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        if (poiCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .background(vibeColor, CircleShape),
            ) {
                Text(
                    text = "$poiCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
