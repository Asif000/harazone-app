package com.areadiscovery.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.areadiscovery.ui.summary.BucketDisplayState
import com.areadiscovery.ui.theme.spacing

@Composable
fun BucketCard(
    state: BucketDisplayState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BucketSectionHeader(
            bucketType = state.bucketType,
            isStreaming = state.isStreaming,
            isComplete = state.isComplete,
        )

        if (state.highlightText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.bucketInternal))
            HighlightFactCallout(
                text = state.highlightText,
                isStreaming = false,
            )
        }

        if (state.bodyText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.bucketInternal))
            StreamingTextContent(
                text = state.bodyText,
                isStreaming = state.isStreaming,
            )
        }

        if (state.confidence != null) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
            ConfidenceTierBadge(confidence = state.confidence)
        }
    }
}
