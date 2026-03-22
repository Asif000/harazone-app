package com.harazone.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.AdvisoryLevel

/**
 * Small colored dot indicating area safety level.
 * Color is secondary indicator; primary is the meta ticker text.
 */
@Composable
fun SafetyDot(
    advisoryLevel: AdvisoryLevel?,
    modifier: Modifier = Modifier,
) {
    val dotColor = advisoryLevel?.toDotColor() ?: return
    val a11yText = advisoryLevel.toAccessibilityText() ?: return

    val pulseSpeed = when (advisoryLevel) {
        AdvisoryLevel.DO_NOT_TRAVEL -> 1000 // Fast pulse
        AdvisoryLevel.CAUTION, AdvisoryLevel.RECONSIDER -> 2000 // Slow pulse
        else -> 0
    }
    val shouldPulse = pulseSpeed > 0

    Spacer(modifier = Modifier.width(6.dp))

    if (shouldPulse) {
        val transition = rememberInfiniteTransition(label = "safety_pulse")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(pulseSpeed), RepeatMode.Reverse),
            label = "safety_pulse_alpha",
        )
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha))
                .semantics { contentDescription = a11yText },
        )
    } else {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
                .semantics { contentDescription = a11yText },
        )
    }
}

private fun AdvisoryLevel.toDotColor(): Color? = when (this) {
    AdvisoryLevel.SAFE -> null
    AdvisoryLevel.CAUTION -> Color(0xFFE3B341)
    AdvisoryLevel.RECONSIDER -> Color(0xFFDB6D28)
    AdvisoryLevel.DO_NOT_TRAVEL -> Color(0xFFDA3633)
    AdvisoryLevel.UNKNOWN -> null
}

private fun AdvisoryLevel.toAccessibilityText(): String? = when (this) {
    AdvisoryLevel.SAFE -> null
    AdvisoryLevel.CAUTION -> "Area safety: exercise caution"
    AdvisoryLevel.RECONSIDER -> "Area safety: reconsider travel"
    AdvisoryLevel.DO_NOT_TRAVEL -> "Area safety: do not travel"
    AdvisoryLevel.UNKNOWN -> null
}
