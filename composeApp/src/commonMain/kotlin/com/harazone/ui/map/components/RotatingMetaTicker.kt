package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.harazone.domain.model.MetaLine
import com.harazone.domain.model.isFixed
import com.harazone.domain.model.text
import kotlinx.coroutines.delay

/**
 * Single 14px rotating meta line for the Discovery Header.
 * Cycles through priority-sorted MetaLines every 4s with crossfade.
 * Each line marquee-scrolls if it overflows the available width.
 * Safety warnings stay fixed (no rotation). Discovering state pauses rotation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RotatingMetaTicker(
    metaLines: List<MetaLine>,
    modifier: Modifier = Modifier,
) {
    if (metaLines.isEmpty()) return

    // If there's a fixed line (safety warning), show only that
    val fixedLine = metaLines.firstOrNull { it.isFixed() }
    if (fixedLine != null) {
        Text(
            text = fixedLine.text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
            color = fixedLine.displayColor(),
            maxLines = 1,
            modifier = modifier
                .basicMarquee(iterations = Int.MAX_VALUE)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
        return
    }

    // If discovering (paused), show static
    val discoveringLine = metaLines.firstOrNull { it is MetaLine.Discovering }
    if (discoveringLine != null) {
        Text(
            text = discoveringLine.text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
            ),
            color = discoveringLine.displayColor(),
            maxLines = 1,
            modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE),
        )
        return
    }

    // Rotate through lines every 4s
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(metaLines) {
        currentIndex = 0
        if (metaLines.size > 1) {
            while (true) {
                delay(4_000)
                currentIndex = (currentIndex + 1) % metaLines.size
            }
        }
    }

    val currentLine = metaLines[currentIndex.coerceIn(metaLines.indices)]

    AnimatedContent(
        targetState = currentLine,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "meta_rotation",
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
    ) { line ->
        Text(
            text = line.text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
            color = line.displayColor(),
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}

fun MetaLine.displayColor(): Color = when (this) {
    is MetaLine.SafetyWarning -> Color(0xFFFFB300)
    is MetaLine.RemoteContext -> Color(0xFF26A69A)
    is MetaLine.CurrencyContext -> Color(0xFF26A69A)
    is MetaLine.LanguageContext -> Color(0xFF26A69A)
    is MetaLine.VibeFilter -> Color(0xFFB39DDB)
    is MetaLine.CompanionNudge -> Color(0xFFB39DDB)
    is MetaLine.PoiHighlight -> Color(0xFF26A69A)
    is MetaLine.Default -> Color.White.copy(alpha = 0.7f)
    is MetaLine.GpsAcquiring -> Color.White.copy(alpha = 0.5f)
    is MetaLine.LocationDenied -> Color.White.copy(alpha = 0.5f)
    is MetaLine.Discovering -> Color.White.copy(alpha = 0.5f)
}
