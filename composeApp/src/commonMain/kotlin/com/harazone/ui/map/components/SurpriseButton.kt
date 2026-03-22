package com.harazone.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SurprisePurpleLight = Color(0xFFB39DDB)
private val SurprisePurpleDark = Color(0xFF7C4DFF)

/**
 * 🎲 Surprise button — 38dp visual, 48dp tap target.
 * Purple gradient + shimmer when enabled; greyed out when disabled.
 */
@Composable
fun SurpriseButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shimmerAlpha = if (enabled) {
        val transition = rememberInfiniteTransition(label = "surprise_shimmer")
        val alpha by transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "surprise_shimmer_alpha",
        )
        alpha
    } else 0.4f

    val gradient = if (enabled) {
        Brush.linearGradient(listOf(SurprisePurpleLight, SurprisePurpleDark))
    } else {
        Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.3f)))
    }

    // 48dp tap target wrapping 38dp visual
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Surprise me" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(gradient)
                .alpha(shimmerAlpha),
        ) {
            Text(
                text = "\uD83C\uDFB2",
                fontSize = 20.sp,
            )
        }
    }
}
