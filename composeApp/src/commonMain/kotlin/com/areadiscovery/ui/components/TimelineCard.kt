package com.areadiscovery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.areadiscovery.ui.summary.BucketDisplayState
import com.areadiscovery.ui.theme.spacing

internal data class TimelineEra(val year: Int, val sentence: String)

// Finding #2: Range-guard years to 1000–2099 to avoid ZIP codes, addresses, populations
private val YEAR_REGEX = Regex("\\b(1\\d{3}|20\\d{2})\\b")
// Finding #1: Split on sentence-ending punctuation AND newlines
private val SENTENCE_SPLIT_REGEX = Regex("[.!?\\n]+")

internal fun parseTimelineEras(content: String): List<TimelineEra> {
    if (content.isBlank()) return emptyList()

    return content
        .split(SENTENCE_SPLIT_REGEX)
        .mapNotNull { sentence ->
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val match = YEAR_REGEX.find(trimmed) ?: return@mapNotNull null
            TimelineEra(year = match.groupValues[1].toInt(), sentence = trimmed)
        }
        .distinctBy { it.year }
        .sortedByDescending { it.year }
        .take(10)
}

@Composable
fun TimelineCard(
    historyState: BucketDisplayState?,
    modifier: Modifier = Modifier,
) {
    val eras = remember(historyState?.bodyText) {
        parseTimelineEras(historyState?.bodyText ?: "")
    }
    val reduceMotion = rememberReduceMotion()
    val isLoading = historyState != null &&
        !historyState.isComplete &&
        (historyState.isStreaming || historyState.bodyText.isEmpty())
    val visible = historyState?.isComplete == true && eras.isNotEmpty()

    // Finding #3: If history is complete but no eras parsed, show highlightText as fallback
    val showFallback = historyState?.isComplete == true &&
        eras.isEmpty() &&
        historyState.highlightText.isNotBlank()

    if (isLoading) {
        HeroLoadingPlaceholder(
            icon = Icons.Filled.History,
            label = "The Story",
            reduceMotion = reduceMotion,
            modifier = modifier,
        )
    }

    AnimatedVisibility(
        visible = visible || showFallback,
        enter = if (reduceMotion) fadeIn(tween(200)) else fadeIn(tween(300)),
        modifier = modifier,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Text(
                    text = "The Story",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(MaterialTheme.spacing.sm))

            if (eras.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.xs),
                    modifier = Modifier.semantics {
                        contentDescription = "Place history timeline"
                    },
                ) {
                    items(eras, key = { it.year }) { era ->
                        Card(
                            colors = CardDefaults.outlinedCardColors(),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.width(200.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(MaterialTheme.spacing.md),
                            ) {
                                Text(
                                    text = era.year.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                                // Finding #5: Cap lines to prevent inconsistent card heights
                                Text(
                                    text = era.sentence,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else {
                // Fallback: show highlight text when no eras could be parsed
                Text(
                    text = historyState?.highlightText ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun HeroLoadingPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = MaterialTheme.spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(MaterialTheme.spacing.iconMd),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        if (reduceMotion) {
            Box(
                modifier = Modifier
                    .size(MaterialTheme.spacing.indicatorDot)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "hero_loading_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "hero_pulse_alpha",
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
}
