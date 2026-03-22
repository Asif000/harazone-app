package com.harazone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val bucketInternal = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val touchTarget = 48.dp

    // Component-specific size tokens
    val iconMd = 24.dp
    val indicatorDot = 8.dp
    val borderAccent = 3.dp
    val skeletonTextWidth = 120.dp
    val skeletonTextHeight = 20.dp
    val bottomBarHeight = 56.dp
    val discoveryHeaderOffset = 60.dp
    val safetyBannerHeight = 48.dp
}

val LocalSpacing = staticCompositionLocalOf { Spacing }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
