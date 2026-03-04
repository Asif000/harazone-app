package com.areadiscovery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.ui.theme.ConfidenceHigh
import com.areadiscovery.ui.theme.ConfidenceLow
import com.areadiscovery.ui.theme.ConfidenceMedium
import com.areadiscovery.ui.theme.spacing

@Composable
fun ConfidenceTierBadge(
    confidence: Confidence,
    modifier: Modifier = Modifier,
) {
    val (label, color, icon) = confidenceDisplay(confidence)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.15f))
            .padding(
                horizontal = MaterialTheme.spacing.sm,
                vertical = MaterialTheme.spacing.xs,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Confidence level: $label"
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.spacing.md),
            tint = color,
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

private data class ConfidenceDisplay(
    val label: String,
    val color: Color,
    val icon: ImageVector,
)

private fun confidenceDisplay(confidence: Confidence): ConfidenceDisplay = when (confidence) {
    Confidence.HIGH -> ConfidenceDisplay("Verified", ConfidenceHigh, Icons.Filled.CheckCircle)
    Confidence.MEDIUM -> ConfidenceDisplay("Approximate", ConfidenceMedium, Icons.Filled.RemoveCircleOutline)
    Confidence.LOW -> ConfidenceDisplay("Limited Data", ConfidenceLow, Icons.AutoMirrored.Filled.HelpOutline)
}
