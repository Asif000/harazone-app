package com.harazone.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.Vibe
import com.harazone.ui.components.rememberReduceMotion

fun computeVibeSizeDp(count: Int, minCount: Int, maxCount: Int): Float =
    if (maxCount == minCount) 40f
    else (32f + 16f * (count - minCount) / (maxCount - minCount).toFloat()).coerceIn(32f, 48f)

@Composable
fun VibeRail(
    vibes: Array<Vibe> = Vibe.entries.toTypedArray(),
    activeVibe: Vibe?,
    vibePoiCounts: Map<Vibe, Int>,
    vibeAreaSaveCounts: Map<Vibe, Int> = emptyMap(),
    savedVibeActive: Boolean = false,
    totalAreaSaveCount: Int = 0,
    onVibeSelected: (Vibe) -> Unit,
    onSavedVibeSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Saved orb — pinned, not reordered
        SavedVibeOrb(
            isActive = savedVibeActive,
            isFilterActive = activeVibe != null,
            totalAreaSaveCount = totalAreaSaveCount,
            onClick = onSavedVibeSelected,
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 6 vibe orbs — dynamically reordered by area save count
        val sortedVibes = remember(vibeAreaSaveCounts) {
            vibes.sortedByDescending { vibeAreaSaveCounts[it] ?: 0 }
        }
        val minCount = vibePoiCounts.values.minOrNull() ?: 0
        val maxCount = vibePoiCounts.values.maxOrNull() ?: 0
        val isFilterActive = activeVibe != null || savedVibeActive

        for (vibe in sortedVibes) {
            val count = vibePoiCounts[vibe] ?: 0
            val sizeDp = computeVibeSizeDp(count, minCount, maxCount).dp
            VibeOrb(
                vibe = vibe,
                isActive = vibe == activeVibe,
                isFilterActive = isFilterActive,
                poiCount = count,
                sizeDp = sizeDp,
                saveCount = vibeAreaSaveCounts[vibe] ?: 0,
                onClick = { onVibeSelected(vibe) },
            )
        }
    }
}

@Composable
private fun SavedVibeOrb(
    isActive: Boolean,
    isFilterActive: Boolean,
    totalAreaSaveCount: Int,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val breathingAlpha = if (isActive && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "saved_breathing")
        val alpha by transition.animateFloat(
            initialValue = 0.85f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
            label = "saved_breathing_alpha",
        )
        alpha
    } else 1.0f

    val isDimmed = isFilterActive && !isActive
    val goldColor = Color(0xFFFFD700)

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(if (isDimmed) 0.45f else 1f)
                .minimumInteractiveComponentSize()
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Saved places, $totalAreaSaveCount in this area" },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = Brush.radialGradient(listOf(Color(0xFFFFE26A), goldColor)),
                        shape = CircleShape,
                    )
                    .let {
                        if (isActive) it.border(2.dp, Color.White, CircleShape)
                                          .graphicsLayer { alpha = breathingAlpha }
                        else it
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "Saved",
                fontSize = 10.sp,
                color = if (isDimmed) Color.White.copy(alpha = 0.35f) else goldColor,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        // Gold count badge
        if (totalAreaSaveCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                    .background(goldColor, CircleShape)
                    .border(1.5.dp, Color(0xFF0a0c10), CircleShape),
            ) {
                Text(
                    text = totalAreaSaveCount.toString(),
                    fontSize = 8.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
