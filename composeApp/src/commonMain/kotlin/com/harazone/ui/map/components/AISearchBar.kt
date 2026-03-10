package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harazone.ui.theme.MapFloatingUiDark
import kotlinx.coroutines.delay

private val ghostPhrases = listOf(
    "What's the vibe here?",
    "Hidden gems nearby?",
    "Safe to walk at night?",
    "Best time to visit?",
)

@Composable
fun AISearchBar(
    onTap: () -> Unit,
    chatIsOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var phraseIndex by remember { mutableStateOf(0) }

    LaunchedEffect(chatIsOpen) {
        if (!chatIsOpen) {
            while (true) {
                delay(3_000)
                phraseIndex = (phraseIndex + 1) % ghostPhrases.size
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.80f))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = phraseIndex,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
            },
            label = "ghostText",
            modifier = Modifier.weight(1f),
        ) { index ->
            Text(
                text = ghostPhrases[index],
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.Mic,
            contentDescription = "Voice search",
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}
