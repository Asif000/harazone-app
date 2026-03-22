package com.harazone.ui.map.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Count pill showing discovered + saved counts: 📍5 ♥2
 * ♥ is a separate tap target (48dp min) for saved lens toggle.
 * Pop animation on count change (max once per 2s).
 */
@Composable
fun CountPill(
    discoveredCount: Int,
    savedCount: Int,
    onSavedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pop animation on count change, max once per 2s
    var popTarget by remember { mutableStateOf(false) }
    var lastPopTime by remember { mutableStateOf(0L) }

    LaunchedEffect(discoveredCount) {
        val now = com.harazone.ui.components.currentTimeMillis()
        if (now - lastPopTime > 2000) {
            lastPopTime = now
            popTarget = true
            delay(300)
            popTarget = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (popTarget) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "count_pop",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = "$discoveredCount discovered, $savedCount saved" },
    ) {
        Text(
            text = "\uD83D\uDCCD$discoveredCount",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        // ♥ has a 48dp tap target via padding around the text
        Text(
            text = "\u2665$savedCount",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFF6B6B),
            modifier = Modifier
                .clickable(onClick = onSavedTap)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}
