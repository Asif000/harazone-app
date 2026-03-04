package com.areadiscovery.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.areadiscovery.ui.theme.spacing

@Composable
fun HighlightFactCallout(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.primary
    val borderWidth = MaterialTheme.spacing.borderAccent

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidthPx = borderWidth.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(strokeWidthPx / 2f, 0f),
                    end = Offset(strokeWidthPx / 2f, size.height),
                    strokeWidth = strokeWidthPx,
                )
            },
    ) {
        StreamingTextContent(
            text = text,
            isStreaming = isStreaming,
            modifier = Modifier.padding(MaterialTheme.spacing.bucketInternal),
        )
    }
}
