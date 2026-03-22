package com.harazone.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harazone.domain.model.GhostPin
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.Vibe

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
    visitedPoiIds: Set<String> = emptySet(),
    visitedPois: List<SavedPoi> = emptyList(),
    visitedFilter: Boolean = false,
    onPinTapped: (Int) -> Unit = {},
    selectedPinIndex: Int? = null,
    ghostPins: List<GhostPin> = emptyList(),
    onGhostPinTapped: (GhostPin) -> Unit = {},
    savedLensActive: Boolean = false,
)
