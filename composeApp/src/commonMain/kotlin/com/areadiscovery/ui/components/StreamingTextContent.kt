package com.areadiscovery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun StreamingTextContent(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()

    if (reduceMotion) {
        AnimatedVisibility(
            visible = text.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(200)),
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        val displayText = if (isStreaming) {
            val cursorAlpha by rememberInfiniteTransition(label = "cursor_blink")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "cursor_alpha",
                )
            val color = MaterialTheme.colorScheme.onSurface
            buildAnnotatedString {
                append(text)
                withStyle(SpanStyle(color = color.copy(alpha = cursorAlpha))) {
                    append("|")
                }
            }
        } else {
            buildAnnotatedString { append(text) }
        }

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
