package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.CompanionNudge
import com.harazone.ui.theme.MapFloatingUiDark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

private val OrbGold = Color(0xFFFFD54F)
private val OrbOrange = Color(0xFFFF9800)
private val PurpleGlow = Color(0xFF9B6ED8)
private val TealGlow = Color(0xFF2BBCB3)
private val RedGlow = Color(0xFFE57373)
private val NudgePipColor = Color(0xFFE040FB)

enum class OrbBarState {
    IDLE,
    NUDGE,
    TELEPORTED,
    STREAMING,
    ACTIVE_VIBE_FILTER,
}

@Composable
fun OrbBar(
    areaName: String,
    companionNudge: CompanionNudge?,
    orbState: OrbBarState,
    activeVibeFilterName: String,
    onOrbTap: () -> Unit,
    onTextTap: () -> Unit,
    onNudgeTextTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ghost phrase rotation for idle state — includes area-specific prompt
    val ghostPhrases = listOf(
        "Ask about $areaName...",
        stringResource(Res.string.ghost_vibe_here),
        stringResource(Res.string.ghost_hidden_gems),
        stringResource(Res.string.ghost_safe_walk),
        stringResource(Res.string.ghost_best_time),
    )
    var phraseIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            phraseIndex = (phraseIndex + 1) % ghostPhrases.size
        }
    }

    // Determine display text and content description
    val displayText = when (orbState) {
        OrbBarState.NUDGE -> companionNudge?.text ?: "Ask about $areaName..."
        OrbBarState.TELEPORTED -> "Tell me about $areaName..."
        OrbBarState.STREAMING -> "Want to know about these places?"
        OrbBarState.ACTIVE_VIBE_FILTER -> "Showing $activeVibeFilterName spots near you"
        OrbBarState.IDLE -> ghostPhrases[phraseIndex]
    }

    val a11yDescription = when (orbState) {
        OrbBarState.NUDGE -> "AI companion: ${companionNudge?.text ?: ""}, tap for details"
        OrbBarState.TELEPORTED -> "AI companion: Tell me about $areaName"
        OrbBarState.STREAMING -> "AI companion: Want to know about these places"
        OrbBarState.ACTIVE_VIBE_FILTER -> "AI companion: Showing $activeVibeFilterName spots"
        OrbBarState.IDLE -> "AI companion: Ask about $areaName"
    }

    // Glow / border per state
    val glowBorder: Modifier = when (orbState) {
        OrbBarState.NUDGE -> Modifier.border(1.5.dp, PurpleGlow, CircleShape)
        OrbBarState.TELEPORTED -> Modifier.border(1.5.dp, TealGlow, CircleShape)
        else -> Modifier
    }

    // Orb pulse animation (for nudge state)
    val isPulsing = orbState == OrbBarState.NUDGE
    val orbScale = if (isPulsing) {
        val transition = rememberInfiniteTransition(label = "orb_pulse")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "orb_scale",
        )
        scale
    } else 1f

    val gradientColors = if (isPulsing) {
        listOf(OrbGold, OrbOrange)
    } else {
        listOf(OrbGold.copy(alpha = 0.50f), OrbOrange.copy(alpha = 0.50f))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.92f))
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .semantics { contentDescription = a11yDescription },
    ) {
        // Orb icon — gold/orange gradient circle with white star (preserved from CompanionOrb)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .scale(orbScale)
                .then(glowBorder)
                .clip(CircleShape)
                .background(Brush.radialGradient(gradientColors))
                .clickable(onClick = onOrbTap),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            // Notification pip for nudge state
            if (orbState == OrbBarState.NUDGE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(NudgePipColor, CircleShape),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Text area — nudge text opens peek card, otherwise opens chat with keyboard
        val textTapAction = if (orbState == OrbBarState.NUDGE) onNudgeTextTap else onTextTap

        AnimatedContent(
            targetState = displayText,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
            },
            label = "orbBarText",
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = textTapAction),
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = if (orbState == OrbBarState.NUDGE) 0.9f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

    }
}
