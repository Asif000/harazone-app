package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.POI
import com.harazone.ui.map.ScreenOffset
import com.harazone.ui.components.PlatformBackHandler
import kotlin.math.roundToInt

private const val PIN_ICON_HEIGHT_DP = 48   // emoji symbol height in dp
private const val CHIP_HEIGHT_DP = 44       // PinMiniChip height in dp
private const val HERO_CARD_HEIGHT_DP = 110 // PinHeroCard height in dp
private const val HERO_CARD_WIDTH_DP = 180  // PinHeroCard fixed width in dp

@Composable
fun PinCardLayer(
    pois: List<POI>,
    pinScreenPositions: Map<String, ScreenOffset>,
    savedPoiIds: Set<String>,
    selectedPinId: String?,
    cardsVisible: Boolean,
    screenHeightPx: Float,
    onChipTapped: (POI) -> Unit,
    onHeroTapped: (POI) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pinScreenPositions.isEmpty() || pois.isEmpty()) return

    val density = LocalDensity.current
    val heroPx     = with(density) { HERO_CARD_WIDTH_DP.dp.toPx() }
    val heroHeightPx = with(density) { HERO_CARD_HEIGHT_DP.dp.toPx() }
    val chipHeightPx = with(density) { CHIP_HEIGHT_DP.dp.toPx() }
    val chipHalfWidthPx = with(density) { 60.dp.toPx() }  // half of max chip width (120.dp max)
    val pinHeightPx  = with(density) { PIN_ICON_HEIGHT_DP.dp.toPx() }
    val chipGapPx = with(density) { 8.dp.toPx() }

    // Determine hero POI
    val heroPoi = if (selectedPinId != null) {
        pois.find { it.savedId == selectedPinId }
    } else null
    val effectiveHero = heroPoi ?: pois.first()

    val heroPinOffset = pinScreenPositions[effectiveHero.savedId]

    // Android back button: if a chip has been promoted to hero (selectedPinId != null),
    // back clears the selection and returns to nearest-as-hero.
    PlatformBackHandler(enabled = selectedPinId != null) {
        onChipTapped(pois.first { it.savedId == selectedPinId!! })  // toggle-deselect via onChipTapped
    }

    AnimatedVisibility(
        visible = cardsVisible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Leader line — drawn on full-screen Canvas
            if (heroPinOffset != null) {
                val heroCardX = (heroPinOffset.x - heroPx / 2f).coerceAtLeast(8f)
                val pinY = heroPinOffset.y
                val isAbove = pinY > screenHeightPx * 0.25f
                val heroCardY = if (isAbove) {
                    (pinY - heroHeightPx - chipGapPx).coerceAtLeast(0f)
                } else {
                    pinY + pinHeightPx + chipGapPx
                }

                // Leader line endpoints depend on whether card is above or below pin
                val cardAnchorX = heroCardX + heroPx / 2f
                val cardAnchorY: Float
                val pinAnchorX = heroPinOffset.x
                val pinAnchorY: Float
                val ctrlY: Float
                if (isAbove) {
                    // Card is above pin: line from card bottom-center down to pin top
                    cardAnchorY = heroCardY + heroHeightPx
                    pinAnchorY  = pinY
                    ctrlY = cardAnchorY + (pinAnchorY - cardAnchorY) * 0.4f
                } else {
                    // Card is below pin: line from card top-center up to pin bottom
                    cardAnchorY = heroCardY
                    pinAnchorY  = pinY + pinHeightPx
                    ctrlY = pinAnchorY + (cardAnchorY - pinAnchorY) * 0.4f
                }

                val leaderColor = Color.White.copy(alpha = 0.55f)

                Canvas(Modifier.fillMaxSize()) {
                    val path = Path().apply {
                        moveTo(cardAnchorX, cardAnchorY)
                        quadraticTo(
                            cardAnchorX,
                            ctrlY,
                            pinAnchorX,
                            pinAnchorY,
                        )
                    }
                    drawPath(
                        path = path,
                        color = leaderColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f),
                        ),
                    )
                }

                // Hero card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(heroCardX.roundToInt(), heroCardY.roundToInt()) },
                ) {
                    PinHeroCard(
                        poi = effectiveHero,
                        isSaved = effectiveHero.savedId in savedPoiIds,
                        onTap = { onHeroTapped(effectiveHero) },
                    )
                }
            }

            // Mini chips — all non-hero POIs
            pois.forEach { poi ->
                if (poi.savedId == effectiveHero.savedId) return@forEach
                val pinOffset = pinScreenPositions[poi.savedId] ?: return@forEach
                val chipX = (pinOffset.x - chipHalfWidthPx).coerceAtLeast(8f)
                val chipY = (pinOffset.y - chipHeightPx - chipGapPx).coerceAtLeast(0f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(chipX.roundToInt(), chipY.roundToInt()) },
                ) {
                    PinMiniChip(
                        poi = poi,
                        isSaved = poi.savedId in savedPoiIds,
                        isHero = false,
                        onClick = { onChipTapped(poi) },
                    )
                }
            }
        }
    }
}
