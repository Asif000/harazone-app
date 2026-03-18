package com.harazone.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.WeatherState
import com.harazone.ui.components.currentHour
import com.harazone.ui.components.currentMinute
import com.harazone.ui.components.currentTimeMillis
import com.harazone.ui.theme.MapFloatingUiDark
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

@Composable
fun TopContextBar(
    areaName: String,
    visitTag: String,
    weather: WeatherState?,
    advisoryLevel: AdvisoryLevel? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.70f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = areaName,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BulletSeparator()
        val displayVisitTag = if (visitTag == "First visit") stringResource(Res.string.visit_first) else visitTag
        Text(
            text = displayVisitTag,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
        )
        BulletSeparator()
        if (weather != null) {
            Text(
                text = "${weather.emoji} ${weather.temperatureF}\u00B0F",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
            )
        } else {
            ShimmerPlaceholder()
        }
        BulletSeparator()
        val amLabel = stringResource(Res.string.time_am)
        val pmLabel = stringResource(Res.string.time_pm)
        Text(
            text = formatCurrentTime(weather?.utcOffsetSeconds, amLabel, pmLabel),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
        )
        val dotColor = advisoryLevel?.dotColor()
        if (dotColor != null) {
            Spacer(modifier = Modifier.width(6.dp))
            SafetyDot(color = dotColor, pulse = advisoryLevel == AdvisoryLevel.DO_NOT_TRAVEL)
        }
    }
}

@Composable
private fun SafetyDot(color: Color, pulse: Boolean) {
    if (pulse) {
        val transition = rememberInfiniteTransition(label = "safety_pulse")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "safety_pulse_alpha",
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

private fun AdvisoryLevel.dotColor(): Color? = when (this) {
    AdvisoryLevel.SAFE -> null
    AdvisoryLevel.CAUTION -> Color(0xFFE3B341)
    AdvisoryLevel.RECONSIDER -> Color(0xFFDB6D28)
    AdvisoryLevel.DO_NOT_TRAVEL -> Color(0xFFDA3633)
    AdvisoryLevel.UNKNOWN -> Color(0xFF888888)
}

@Composable
private fun BulletSeparator() {
    Text(
        text = " \u2022 ",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.4f),
    )
}

@Composable
private fun ShimmerPlaceholder() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmer_alpha",
    )
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = alpha)),
    )
}

private fun formatCurrentTime(utcOffsetSeconds: Int?, amLabel: String = "AM", pmLabel: String = "PM"): String {
    val hour: Int
    val minute: Int
    if (utcOffsetSeconds != null) {
        // Compute location-local time from UTC + offset
        val nowUtcMs = currentTimeMillis()
        val localMs = nowUtcMs + (utcOffsetSeconds * 1000L)
        val totalMinutes = (localMs / 60_000) % (24 * 60)
        hour = (totalMinutes / 60).toInt()
        minute = (totalMinutes % 60).toInt()
    } else {
        hour = currentHour()
        minute = currentMinute()
    }
    val amPm = if (hour < 12) amLabel else pmLabel
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
}
