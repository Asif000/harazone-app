package com.areadiscovery.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    isFilterActive: Boolean,
    poiCount: Int,
    sizeDp: Dp,
    onClick: () -> Unit,
) {
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
        1.0f
    }

    val vibeColor = vibe.toColor()
    val isDimmed = isFilterActive && !isActive

    val labelColor = when {
        isActive -> Color.White
        isDimmed -> Color.White.copy(alpha = 0.35f)
        else -> vibeColor
    }
    val labelWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

    // Two-level alpha system: Modifier.alpha(0.45f) dims the entire Column (circle + label)
    // for the dimmed state. graphicsLayer { alpha = breathingAlpha } pulses only the circle
    // Box for the active breathing effect. These are intentionally separate — do not merge.
    val columnModifier = if (isDimmed) {
        Modifier.alpha(0.45f)
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = columnModifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${vibe.displayName}, $poiCount places" },
    ) {
        val circleModifier = Modifier
            .size(sizeDp)
            .background(
                brush = Brush.radialGradient(
                    listOf(lerp(vibeColor, Color.White, 0.4f), vibeColor),
                ),
                shape = CircleShape,
            )
            .let {
                if (isActive) {
                    it.border(2.dp, Color.White, CircleShape)
                        .graphicsLayer { alpha = breathingAlpha }
                } else {
                    it
                }
            }

        Box(
            contentAlignment = Alignment.Center,
            modifier = circleModifier,
        ) {
            Icon(
                imageVector = vibe.toImageVector(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = vibe.displayName,
            fontSize = 10.sp,
            color = labelColor,
            fontWeight = labelWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
