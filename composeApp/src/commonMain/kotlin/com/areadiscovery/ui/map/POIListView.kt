package com.areadiscovery.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.ui.components.ConfidenceTierBadge
import com.areadiscovery.ui.theme.spacing

@Composable
fun POIListView(
    pois: List<POI>,
    onPoiClick: (POI) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pois.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No places found for this area yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            item { Spacer(Modifier.height(MaterialTheme.spacing.sm)) }
            items(pois, key = { "${it.name}_${it.type}" }) { poi ->
                val capitalizedType = poi.type.replaceFirstChar { it.uppercaseChar() }
                val description = buildString {
                    append(poi.name)
                    append(", ")
                    append(capitalizedType)
                    append(", ")
                    append(poi.description.take(100))
                    append(", Confidence: ")
                    append(confidenceLabelFor(poi.confidence))
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.spacing.md,
                            vertical = MaterialTheme.spacing.xs,
                        )
                        .clickable { onPoiClick(poi) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.md),
                    ) {
                        Text(
                            text = poi.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))
                        Text(
                            text = capitalizedType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))
                        Text(
                            text = poi.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))
                        ConfidenceTierBadge(confidence = poi.confidence)
                    }
                }
            }
            item { Spacer(Modifier.height(MaterialTheme.spacing.sm)) }
        }
    }
}

private fun confidenceLabelFor(confidence: Confidence): String = when (confidence) {
    Confidence.HIGH -> "Verified"
    Confidence.MEDIUM -> "Approximate"
    Confidence.LOW -> "Limited Data"
}
