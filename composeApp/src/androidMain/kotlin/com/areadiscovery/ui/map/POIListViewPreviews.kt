package com.areadiscovery.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.ui.theme.AreaDiscoveryTheme

@Preview(showBackground = true)
@Composable
private fun POIListViewPreview() {
    AreaDiscoveryTheme {
        POIListView(
            pois = listOf(
                POI("Statue of Liberty", "landmark", "Famous statue in New York Harbor", Confidence.HIGH, 40.6892, -74.0445, vibe = "character"),
                POI("Central Park", "nature", "Large urban park", Confidence.MEDIUM, 40.7829, -73.9654, vibe = "character"),
                POI("Joe's Pizza", "food", "Classic New York slice", Confidence.LOW, 40.7306, -73.9866, vibe = "cost"),
            ),
            activeVibe = Vibe.CHARACTER,
            onVibeSelected = {},
            onPoiClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun POIListViewEmptyPreview() {
    AreaDiscoveryTheme {
        POIListView(
            pois = emptyList(),
            activeVibe = Vibe.CHARACTER,
            onVibeSelected = {},
            onPoiClick = {},
        )
    }
}
