package com.harazone.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true, name = "Light Theme Colors")
@Composable
private fun LightColorSwatchesPreview() {
    AreaDiscoveryTheme(darkTheme = false) {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Light Theme Colors", style = MaterialTheme.typography.headlineSmall)
                ColorSwatch("Primary", MaterialTheme.colorScheme.primary)
                ColorSwatch("On Primary", MaterialTheme.colorScheme.onPrimary)
                ColorSwatch("Surface", MaterialTheme.colorScheme.surface)
                ColorSwatch("On Surface", MaterialTheme.colorScheme.onSurface)
                ColorSwatch("Background", MaterialTheme.colorScheme.background)
                ColorSwatch("Error", MaterialTheme.colorScheme.error)
                ColorSwatch("Confidence High", ConfidenceHigh)
                ColorSwatch("Confidence Medium", ConfidenceMedium)
                ColorSwatch("Confidence Low", ConfidenceLow)
            }
        }
    }
}

@Preview(showBackground = true, name = "Dark Theme Colors")
@Composable
private fun DarkColorSwatchesPreview() {
    AreaDiscoveryTheme(darkTheme = true) {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dark Theme Colors", style = MaterialTheme.typography.headlineSmall)
                ColorSwatch("Primary", MaterialTheme.colorScheme.primary)
                ColorSwatch("Surface", MaterialTheme.colorScheme.surface)
                ColorSwatch("On Surface", MaterialTheme.colorScheme.onSurface)
                ColorSwatch("Background", MaterialTheme.colorScheme.background)
            }
        }
    }
}

@Preview(showBackground = true, name = "Typography Scale")
@Composable
private fun TypographyScalePreview() {
    AreaDiscoveryTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display Medium (28sp)", style = MaterialTheme.typography.displayMedium)
                Text("Headline Small (20sp)", style = MaterialTheme.typography.headlineSmall)
                Text("Title Medium (16sp)", style = MaterialTheme.typography.titleMedium)
                Text("Body Large (16sp)", style = MaterialTheme.typography.bodyLarge)
                Text("Body Medium (14sp)", style = MaterialTheme.typography.bodyMedium)
                Text("Label Medium (12sp)", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Preview(showBackground = true, name = "Shape Samples")
@Composable
private fun ShapeSamplesPreview() {
    AreaDiscoveryTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Shape System", style = MaterialTheme.typography.headlineSmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Small (8dp)", color = MaterialTheme.colorScheme.onPrimary)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Medium (16dp)", color = MaterialTheme.colorScheme.onPrimary)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(80.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Large (24dp)", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
