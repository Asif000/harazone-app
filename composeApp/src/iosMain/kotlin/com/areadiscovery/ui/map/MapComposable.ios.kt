package com.areadiscovery.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe

@Composable
actual fun MapComposable(
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
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Map not yet available on iOS", style = MaterialTheme.typography.bodyMedium)
    }
}
