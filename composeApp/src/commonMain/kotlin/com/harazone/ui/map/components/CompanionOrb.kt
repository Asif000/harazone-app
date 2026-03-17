package com.harazone.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OrbGold = Color(0xFFFFD54F)
internal val CompanionOrange = Color(0xFFFF9800)
private val OrbQuietAlpha = 0.50f

@Composable
fun CompanionOrb(
    isPulsing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "orb_scale",
        )
        pulseScale
    } else 1f
    val gradientColors = if (isPulsing) {
        listOf(OrbGold, CompanionOrange)
    } else {
        listOf(OrbGold.copy(alpha = OrbQuietAlpha), CompanionOrange.copy(alpha = OrbQuietAlpha))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .scale(scale),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(gradientColors))
                .clickable(onClick = onClick),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Companion",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
