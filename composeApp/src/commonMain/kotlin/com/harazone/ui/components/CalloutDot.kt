package com.harazone.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Accent = Color(0xFF4ECDC4)

@Composable
fun CalloutDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "callout")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "calloutScale",
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size((10 * scale).dp)
                .clip(CircleShape)
                .background(Accent),
        )
    }
}
