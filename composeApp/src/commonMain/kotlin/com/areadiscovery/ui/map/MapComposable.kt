package com.areadiscovery.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.areadiscovery.domain.model.POI

@Composable
expect fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
    pois: List<POI>,
    onPoiSelected: (POI?) -> Unit,
)
