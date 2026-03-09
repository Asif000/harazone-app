package com.areadiscovery.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe

@Composable
expect fun MapComposable(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Double,
    cameraMoveId: Int,
    pois: List<POI>,
    activeVibe: Vibe?,
    onPoiSelected: (POI?) -> Unit,
    onMapRenderFailed: () -> Unit,
    onCameraIdle: (lat: Double, lng: Double) -> Unit,
    savedPoiIds: Set<String> = emptySet(),
)
