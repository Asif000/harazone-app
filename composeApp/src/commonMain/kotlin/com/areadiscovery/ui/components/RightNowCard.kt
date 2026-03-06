package com.areadiscovery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.areadiscovery.ui.summary.BucketDisplayState
import com.areadiscovery.ui.theme.spacing

@Composable
fun RightNowCard(
    whatsHappeningState: BucketDisplayState?,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val isLoading = whatsHappeningState != null &&
        !whatsHappeningState.isComplete &&
        (whatsHappeningState.isStreaming || whatsHappeningState.highlightText.isEmpty())
    // Finding #6: Use isNotBlank() instead of isNotEmpty() to catch whitespace-only highlights
    val visible = whatsHappeningState?.isComplete == true &&
        whatsHappeningState.highlightText.isNotBlank()

    if (isLoading) {
        HeroLoadingPlaceholder(
            icon = Icons.Filled.Event,
            label = "Right Now",
            reduceMotion = reduceMotion,
            modifier = modifier,
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(tween(200)) else fadeIn(tween(300)),
        modifier = modifier,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(MaterialTheme.spacing.sm))
                    Text(
                        text = "Right Now",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
                Text(
                    text = whatsHappeningState?.highlightText ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
    }
}
