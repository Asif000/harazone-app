package com.areadiscovery.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
)
