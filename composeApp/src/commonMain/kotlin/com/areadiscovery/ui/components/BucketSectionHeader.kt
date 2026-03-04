package com.areadiscovery.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.ui.theme.spacing

@Composable
fun BucketSectionHeader(
    bucketType: BucketType,
    isStreaming: Boolean,
    isComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val title = bucketType.displayTitle()
    val icon = bucketType.icon()
    val isPending = !isStreaming && !isComplete

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics {
                heading()
                contentDescription = "$title section"
            }
            .padding(vertical = MaterialTheme.spacing.xs),
    ) {
        if (isPending) {
            // Skeleton state: gray placeholder icon
            Box(
                modifier = Modifier
                    .size(MaterialTheme.spacing.iconMd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.spacing.iconMd),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xs))

        if (isPending) {
            // Skeleton state: placeholder text
            Box(
                modifier = Modifier
                    .size(
                        width = MaterialTheme.spacing.skeletonTextWidth,
                        height = MaterialTheme.spacing.skeletonTextHeight,
                    )
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (isStreaming) {
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.sm))
            StreamingDot(reduceMotion = reduceMotion)
        } else if (isComplete) {
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.sm))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Complete",
                modifier = Modifier.size(MaterialTheme.spacing.md),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StreamingDot(reduceMotion: Boolean) {
    if (reduceMotion) {
        // Static dot when reduced motion
        Box(
            modifier = Modifier
                .size(MaterialTheme.spacing.indicatorDot)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "streaming_pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
        Box(
            modifier = Modifier
                .size(MaterialTheme.spacing.indicatorDot)
                .clip(CircleShape)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
