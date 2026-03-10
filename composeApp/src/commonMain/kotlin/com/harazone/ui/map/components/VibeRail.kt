package com.harazone.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.Vibe

fun computeVibeSizeDp(count: Int, minCount: Int, maxCount: Int): Float =
    if (maxCount == minCount) 40f
    else (32f + 16f * (count - minCount) / (maxCount - minCount).toFloat()).coerceIn(32f, 48f)

@Composable
fun VibeRail(
    vibes: Array<Vibe> = Vibe.entries.toTypedArray(),
    activeVibe: Vibe?,
    vibePoiCounts: Map<Vibe, Int>,
    onVibeSelected: (Vibe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = vibePoiCounts.values
    val minCount = counts.minOrNull() ?: 0
    val maxCount = counts.maxOrNull() ?: 0

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        for (vibe in vibes) {
            val count = vibePoiCounts[vibe] ?: 0
            val sizeDp = computeVibeSizeDp(count, minCount, maxCount).dp
            VibeOrb(
                vibe = vibe,
                isActive = vibe == activeVibe,
                isFilterActive = activeVibe != null,
                poiCount = count,
                sizeDp = sizeDp,
                onClick = { onVibeSelected(vibe) },
            )
        }
    }
}
