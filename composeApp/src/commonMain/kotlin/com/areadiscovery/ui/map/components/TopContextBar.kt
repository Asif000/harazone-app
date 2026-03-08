package com.areadiscovery.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
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
import com.areadiscovery.domain.model.WeatherState
import com.areadiscovery.ui.components.currentHour
import com.areadiscovery.ui.components.currentMinute
import com.areadiscovery.ui.components.currentTimeMillis
import com.areadiscovery.ui.theme.MapFloatingUiDark

@Composable
fun TopContextBar(
    areaName: String,
    visitTag: String,
    weather: WeatherState?,
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
        Text(
            text = visitTag,
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
        Text(
            text = formatCurrentTime(weather?.utcOffsetSeconds),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
        )
    }
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

private fun formatCurrentTime(utcOffsetSeconds: Int?): String {
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
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
}
