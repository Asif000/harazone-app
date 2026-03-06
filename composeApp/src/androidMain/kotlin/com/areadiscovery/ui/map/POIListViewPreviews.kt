package com.areadiscovery.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.ui.theme.AreaDiscoveryTheme

@Preview(showBackground = true)
@Composable
private fun POIListViewPreview() {
    AreaDiscoveryTheme {
        POIListView(
            pois = listOf(
                POI("Statue of Liberty", "landmark", "Famous statue in New York Harbor", Confidence.HIGH, 40.6892, -74.0445),
                POI("Central Park", "nature", "Large urban park spanning 843 acres in the heart of Manhattan, offering walking trails, lakes, gardens, and recreational facilities for millions of visitors each year", Confidence.MEDIUM, 40.7829, -73.9654),
                POI("Joe's Pizza", "restaurant", "Classic New York slice", Confidence.LOW, 40.7306, -73.9866),
            ),
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
            onPoiClick = {},
        )
    }
}
