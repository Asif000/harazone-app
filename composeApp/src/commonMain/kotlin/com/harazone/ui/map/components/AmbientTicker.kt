package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.POI
import com.harazone.ui.map.PinStatusColor
import com.harazone.ui.map.calculateSunsetMinutes
import com.harazone.ui.map.liveStatusToColor
import com.harazone.ui.map.resolveStatus
import com.harazone.ui.map.toComposeColor
import com.harazone.ui.theme.MapFloatingUiDark
import kotlinx.coroutines.delay

internal fun buildTickerSlots(
    pois: List<POI>,
    lat: Double,
    lng: Double,
    areaHighlights: List<String>,
    sunsetMinutesProvider: (Double, Double) -> Int = ::calculateSunsetMinutes,
    openCountOverride: Int? = null,
): List<String> {
    val slots = mutableListOf<String>()
    val openCount = openCountOverride
        ?: pois.count { liveStatusToColor(resolveStatus(it.liveStatus, it.hours)) == PinStatusColor.GREEN }
    if (openCount > 0) slots.add("$openCount open nearby")
    if (lat != 0.0 || lng != 0.0) {
        val sunsetMin = sunsetMinutesProvider(lat, lng)
        if (sunsetMin in 1..120) slots.add("Sunset in $sunsetMin min")
    }
    for (h in areaHighlights) {
        if (h.isNotBlank()) slots.add(h)
    }
    return slots
}

@Composable
fun AmbientTicker(
    pois: List<POI>,
    latitude: Double,
    longitude: Double,
    areaHighlights: List<String>,
    modifier: Modifier = Modifier,
) {
    val statusCounts = remember(pois) {
        pois.groupBy { liveStatusToColor(resolveStatus(it.liveStatus, it.hours)) }.mapValues { it.value.size }
    }
    val greenCount = statusCounts[PinStatusColor.GREEN] ?: 0
    val orangeCount = statusCounts[PinStatusColor.ORANGE] ?: 0
    val redCount = statusCounts[PinStatusColor.RED] ?: 0

    val slots = remember(pois, latitude, longitude, areaHighlights) {
        buildTickerSlots(pois, latitude, longitude, areaHighlights, openCountOverride = greenCount)
    }

    if (slots.isEmpty()) return

    var currentIndex by remember { mutableStateOf(0) }

    if (slots.size > 1) {
        LaunchedEffect(slots) {
            currentIndex = 0
            while (true) {
                delay(5000)
                currentIndex = (currentIndex + 1) % slots.size
            }
        }
    } else {
        currentIndex = 0
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(targetState = slots[currentIndex], modifier = Modifier.weight(1f)) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("●", color = PinStatusColor.GREEN.toComposeColor(), style = MaterialTheme.typography.labelSmall)
        Text(" $greenCount", color = Color.White, style = MaterialTheme.typography.labelSmall)
        Text("  ●", color = PinStatusColor.ORANGE.toComposeColor(), style = MaterialTheme.typography.labelSmall)
        Text(" $orangeCount", color = Color.White, style = MaterialTheme.typography.labelSmall)
        Text("  ●", color = PinStatusColor.RED.toComposeColor(), style = MaterialTheme.typography.labelSmall)
        Text(" $redCount", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
