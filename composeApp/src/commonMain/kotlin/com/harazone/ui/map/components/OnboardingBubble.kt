package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.harazone.ui.components.CalloutDot
import com.harazone.ui.components.PlatformBackHandler
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

private val Accent = Color(0xFF4ECDC4)
private val DarkCard = Color(0xFF1A1224)

@Serializable
private data class OnboardingTip(val icon: String, val action: String, val description: String)

@Serializable
private data class OnboardingTipsData(
    val avatar_emoji: String,
    val title: String,
    val footer_text: String,
    val dismiss_label: String,
    val tips: List<OnboardingTip>,
)

private val fallbackTips = OnboardingTipsData(
    avatar_emoji = "\u2728",
    title = "I'm your AI travel guide!",
    footer_text = "The more you explore, the smarter I get",
    dismiss_label = "Let's go!",
    tips = listOf(
        OnboardingTip("\uD83D\uDC46", "Tap pins", "to discover what makes each place special"),
        OnboardingTip("\uD83D\uDD16", "Save places", "you like \u2014 I'll learn your taste and recommend better spots"),
        OnboardingTip("\uD83C\uDFA8", "Tap vibes", "on the right to filter by what excites you"),
        OnboardingTip("\uD83D\uDCAC", "Chat with me", "anytime \u2014 ask about hidden gems, safety, food, anything"),
    ),
)

@Composable
fun OnboardingBubble(
    visible: Boolean,
    onDismiss: () -> Unit,
    searchBarOffset: Offset = Offset.Zero,
) {
    val localizedFallback = OnboardingTipsData(
        avatar_emoji = "\u2728",
        title = stringResource(Res.string.onboarding_title),
        footer_text = stringResource(Res.string.onboarding_footer),
        dismiss_label = stringResource(Res.string.onboarding_dismiss),
        tips = listOf(
            OnboardingTip("\uD83D\uDC46", stringResource(Res.string.onboarding_tip1_title), stringResource(Res.string.onboarding_tip1_body)),
            OnboardingTip("\uD83D\uDD16", stringResource(Res.string.onboarding_tip2_title), stringResource(Res.string.onboarding_tip2_body)),
            OnboardingTip("\uD83C\uDFA8", stringResource(Res.string.onboarding_tip3_title), stringResource(Res.string.onboarding_tip3_body)),
            OnboardingTip("\uD83D\uDCAC", stringResource(Res.string.onboarding_tip4_title), stringResource(Res.string.onboarding_tip4_body)),
        ),
    )
    var tipsData by remember { mutableStateOf(fallbackTips) }

    LaunchedEffect(localizedFallback) {
        tipsData = localizedFallback
    }

    PlatformBackHandler(enabled = visible) { onDismiss() }

    // M5 fix: removed outer AnimatedVisibility(visible=visible) — it was redundant since the
    // inner AnimatedVisibility already handles fade+slide for the card.
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        ) {
            // Callout dots — positioned at measured layout bounds of target UI elements.
            // Offset.Zero means not yet measured (first frame); dots skip rendering until measured.
            // vibeRailOffset and savedFabOffset removed — VibeRail and SavedFab no longer exist (M3)
            if (searchBarOffset != Offset.Zero) {
                CalloutDot(
                    modifier = Modifier.offset {
                        IntOffset(searchBarOffset.x.roundToInt(), searchBarOffset.y.roundToInt())
                    }
                )
            }

            // Bubble card — anchored above bottom bar
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 80.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .clickable(enabled = false) {} // absorb clicks so scrim doesn't fire
                        .padding(20.dp),
                ) {
                    // Avatar
                    Text(tipsData.avatar_emoji, fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))

                    // Title
                    Text(
                        tipsData.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Tips
                    tipsData.tips.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 10.dp),
                        ) {
                            Text(tip.icon, fontSize = 18.sp, modifier = Modifier.padding(top = 1.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                buildString {
                                    append(tip.action)
                                    append(" ")
                                    append(tip.description)
                                },
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Footer row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            tipsData.footer_text,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                tipsData.dismiss_label,
                                color = Color(0xFF111111),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
