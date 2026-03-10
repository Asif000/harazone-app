package com.harazone.ui.theme

import areadiscovery.composeapp.generated.resources.Res
import areadiscovery.composeapp.generated.resources.inter_bold
import areadiscovery.composeapp.generated.resources.inter_medium
import areadiscovery.composeapp.generated.resources.inter_regular
import areadiscovery.composeapp.generated.resources.inter_semibold
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

// Typography size constants — referenced by tests to ensure spec compliance.
// If any value changes here, tests will catch the regression.
internal val DISPLAY_MEDIUM_SIZE = 28.sp
internal val HEADLINE_SMALL_SIZE = 20.sp
internal val TITLE_MEDIUM_SIZE = 16.sp
internal val BODY_LARGE_SIZE = 16.sp
internal val BODY_MEDIUM_SIZE = 16.sp   // AC #4: all body text >= 16sp (raised from 14sp)
internal val LABEL_MEDIUM_SIZE = 12.sp

@Composable
fun InterFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
fun AreaDiscoveryTypography(): Typography {
    val interFontFamily = InterFontFamily()
    return Typography(
        displayMedium = TextStyle(
            fontFamily = interFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = DISPLAY_MEDIUM_SIZE,
            lineHeight = 33.6.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = interFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = HEADLINE_SMALL_SIZE,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = interFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = TITLE_MEDIUM_SIZE,
            lineHeight = 20.8.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = interFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = BODY_LARGE_SIZE,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = interFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = BODY_MEDIUM_SIZE,
            lineHeight = 24.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = interFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = LABEL_MEDIUM_SIZE,
            lineHeight = 16.8.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}
