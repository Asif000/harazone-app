package com.harazone.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.harazone.domain.model.DynamicVibe
import com.harazone.ui.components.CalloutDot
import com.harazone.ui.components.rememberReduceMotion

fun computeVibeSizeDp(count: Int, minCount: Int, maxCount: Int): Float =
    if (maxCount == minCount) 40f
    else (32f + 16f * (count - minCount) / (maxCount - minCount).toFloat()).coerceIn(32f, 48f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VibeRail(
    vibes: List<DynamicVibe> = emptyList(),
    activeDynamicVibe: DynamicVibe? = null,
    dynamicVibePoiCounts: Map<String, Int> = emptyMap(),
    dynamicVibeAreaSaveCounts: Map<String, Int> = emptyMap(),
    savedVibeActive: Boolean = false,
    totalAreaSaveCount: Int = 0,
    isLoadingVibes: Boolean = false,
    isOfflineVibes: Boolean = false,
    pinnedVibeLabels: List<String> = emptyList(),
    onVibeSelected: (DynamicVibe) -> Unit,
    onSavedVibeSelected: () -> Unit = {},
    onLongPressVibe: (DynamicVibe) -> Unit = {},
    onExploreRetry: () -> Unit = {},
    showCalloutDot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // REGRESSION: horizontalAlignment MUST be End — orbs right-align against the map edge.
    // No Compose UI test infra yet; verify visually if changed.
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Saved orb — pinned, not reordered
        Box {
            SavedVibeOrb(
                isActive = savedVibeActive,
                isFilterActive = activeDynamicVibe != null,
                totalAreaSaveCount = totalAreaSaveCount,
                onClick = onSavedVibeSelected,
            )
            if (showCalloutDot) {
                CalloutDot(modifier = Modifier.align(Alignment.TopEnd).padding(end = 0.dp))
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        when {
            isLoadingVibes -> {
                // Skeleton shimmer chips
                repeat(5) {
                    SkeletonChip()
                }
            }
            vibes.isEmpty() -> {
                // Fallback: exploring chip with pulsing dot
                ExploringChip(onClick = onExploreRetry)
            }
            else -> {
                // Normal dynamic vibe chips
                val minCount = dynamicVibePoiCounts.values.minOrNull() ?: 0
                val maxCount = dynamicVibePoiCounts.values.maxOrNull() ?: 0
                val isFilterActive = activeDynamicVibe != null || savedVibeActive

                for ((index, vibe) in vibes.withIndex()) {
                    val count = dynamicVibePoiCounts[vibe.label] ?: 0
                    val sizeDp = computeVibeSizeDp(count, minCount, maxCount).dp
                    val isPinned = vibe.label in pinnedVibeLabels
                    Box {
                        DynamicVibeOrb(
                            vibe = vibe,
                            isActive = activeDynamicVibe?.label == vibe.label,
                            isFilterActive = isFilterActive,
                            poiCount = count,
                            sizeDp = sizeDp,
                            isPinned = isPinned,
                            onClick = { onVibeSelected(vibe) },
                            onLongClick = { onLongPressVibe(vibe) },
                        )
                        if (showCalloutDot && index == 0) {
                            CalloutDot(modifier = Modifier.align(Alignment.TopEnd).padding(end = 0.dp))
                        }
                    }
                }
            }
        }

        // Offline indicator
        if (isOfflineVibes) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFFFF9800), CircleShape),
                )
                Text(
                    text = "Offline \u00B7 cached",
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DynamicVibeOrb(
    vibe: DynamicVibe,
    isActive: Boolean,
    isFilterActive: Boolean,
    poiCount: Int,
    sizeDp: androidx.compose.ui.unit.Dp,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val breathingAlpha = if (isActive && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "breathing")
        val alpha by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
            label = "breathing_alpha",
        )
        alpha
    } else 1.0f

    val vibeColor = Color(0xFF5B9BD5)
    val isDimmed = isFilterActive && !isActive

    val labelColor = when {
        isActive -> Color.White
        isDimmed -> Color.White.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.8f)
    }

    val columnModifier = if (isDimmed) Modifier.alpha(0.45f) else Modifier

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = columnModifier
                .minimumInteractiveComponentSize()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics { contentDescription = "${vibe.label}, $poiCount places" },
        ) {
            val circleModifier = Modifier
                .size(sizeDp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color(0xFF3a3f4a)),
                    ),
                    shape = CircleShape,
                )
                .let {
                    if (isActive) it.border(2.dp, Color.White, CircleShape)
                        .graphicsLayer { alpha = breathingAlpha }
                    else it
                }

            Box(
                contentAlignment = Alignment.Center,
                modifier = circleModifier,
            ) {
                Text(
                    text = vibe.icon,
                    fontSize = 18.sp,
                )
            }

            Text(
                text = if (isPinned) "\uD83D\uDCCC ${vibe.label}" else vibe.label,
                fontSize = 10.sp,
                color = labelColor,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        // Count badge — white badge with dark text, matching Saved orb design language
        if (poiCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                    .background(Color.White, CircleShape)
                    .border(1.5.dp, Color(0xFF0a0c10), CircleShape),
            ) {
                Text(
                    text = poiCount.toString(),
                    fontSize = 8.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SkeletonChip() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmer_alpha",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = shimmerAlpha)),
    )
}

@Composable
private fun ExploringChip(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse_alpha",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(pulseAlpha)
                    .background(Color.White, CircleShape),
            )
        }
        Text(
            text = "Exploring...",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
        )
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
