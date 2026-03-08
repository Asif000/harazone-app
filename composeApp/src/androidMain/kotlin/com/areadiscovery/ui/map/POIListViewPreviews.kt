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
                POI("Old Town Clock Tower", "landmark", "Iconic timepiece overlooking the harbor", Confidence.HIGH, 40.6892, -74.0445, vibe = "character"),
                POI("Lakeside Nature Reserve", "nature", "Trails through ancient woodland", Confidence.MEDIUM, 40.7829, -73.9654, vibe = "character"),
                POI("Family-Run Bakery", "food", "Three generations of sourdough", Confidence.LOW, 40.7306, -73.9866, vibe = "cost"),
            ),
            activeVibe = null,
            onVibeSelected = {},
            onPoiClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun POIListViewWithThumbnailPreview() {
    AreaDiscoveryTheme {
        POIListView(
            pois = listOf(
                POI(
                    "Historic Market Hall", "landmark",
                    "A beloved local gathering spot with artisan vendors",
                    Confidence.HIGH, 40.6892, -74.0445, vibe = "character",
                    imageUrl = "https://picsum.photos/200",
                ),
                POI(
                    "Riverside Park", "nature",
                    "Scenic walking trails along the waterfront",
                    Confidence.MEDIUM, 40.7829, -73.9654, vibe = "character",
                ),
                POI(
                    "Corner Bakery", "food",
                    "Famous for sourdough since the 1920s",
                    Confidence.LOW, 40.7306, -73.9866, vibe = "cost",
                    imageUrl = "https://picsum.photos/201",
                ),
            ),
            activeVibe = null,
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
            activeVibe = null,
            onVibeSelected = {},
            onPoiClick = {},
        )
    }
}
