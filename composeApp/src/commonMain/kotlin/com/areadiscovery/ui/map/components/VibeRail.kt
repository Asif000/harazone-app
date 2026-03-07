package com.areadiscovery.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.areadiscovery.domain.model.Vibe

@Composable
fun VibeRail(
    vibes: Array<Vibe> = Vibe.entries.toTypedArray(),
    activeVibe: Vibe,
    vibePoiCounts: Map<Vibe, Int>,
    onVibeSelected: (Vibe) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 72.dp),
    ) {
        for (vibe in vibes) {
            VibeOrb(
                vibe = vibe,
                isActive = vibe == activeVibe,
                poiCount = vibePoiCounts[vibe] ?: 0,
                onClick = { onVibeSelected(vibe) },
            )
        }
    }
}
