package com.areadiscovery.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.ui.theme.AreaDiscoveryTheme
import com.areadiscovery.ui.theme.spacing

@Composable
fun POIDetailCard(
    poi: POI,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onNavigateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.md),
        ) {
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Text(
                text = poi.type.replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Text(
                text = poi.description,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Row {
                IconButton(
                    onClick = onSaveClick,
                    modifier = Modifier.size(MaterialTheme.spacing.touchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save ${poi.name}",
                    )
                }
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.size(MaterialTheme.spacing.touchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share ${poi.name}",
                    )
                }
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                IconButton(
                    onClick = onNavigateClick,
                    modifier = Modifier.size(MaterialTheme.spacing.touchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Navigation,
                        contentDescription = "Navigate to ${poi.name}",
                    )
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            ConfidenceTierBadge(confidence = poi.confidence)
        }
    }
}

@Preview
@Composable
private fun POIDetailCardPreview() {
    AreaDiscoveryTheme {
        POIDetailCard(
            poi = POI(
                name = "Torre de Belem",
                type = "landmark",
                description = "A 16th-century fortified tower on the banks of the Tagus River, a UNESCO World Heritage Site.",
                confidence = Confidence.HIGH,
                latitude = 38.6916,
                longitude = -9.2160,
            ),
            onSaveClick = {},
            onShareClick = {},
            onNavigateClick = {},
        )
    }
}
