package com.harazone.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.harazone.domain.model.Vibe

// Confidence tier accent colors (standalone, not part of M3 color scheme)
val ConfidenceHigh = Color(0xFF4A8C5C)    // Muted Green — Verified
val ConfidenceMedium = Color(0xFFC49A3C)  // Muted Amber — Approximate
val ConfidenceLow = Color(0xFFB85C4A)     // Muted Red — Limited Data

// v3 map dark palette
val MapBackground = Color(0xFF0A0A0A)
val MapSurfaceDark = Color(0xFF161016)
val MapFloatingUiDark = Color(0xFF0A0A0A)
val DetailPageLight = Color(0xFFF5F2EF)

// Meta context / resident mode accent
val MetaContextTeal = Color(0xFF26A69A)

// Saved Lens feature colors
val SavedLensTeal = Color(0xFF00BFA5)
val SavedLensBg = Color(0xFF1A3A36)

// v3 vibe accent colors
val VibeCharacter = Color(0xFF2BBCB3)
val VibeHistory = Color(0xFFC4935A)
val VibeWhatsOn = Color(0xFF9B6ED8)
val VibeSafety = Color(0xFFE8A735)
val VibeNearby = Color(0xFF5B9BD5)
val VibeCost = Color(0xFF5CAD6F)

fun Vibe.toColor(): Color = when (this) {
    Vibe.CHARACTER -> VibeCharacter
    Vibe.HISTORY -> VibeHistory
    Vibe.WHATS_ON -> VibeWhatsOn
    Vibe.SAFETY -> VibeSafety
    Vibe.NEARBY -> VibeNearby
    Vibe.COST -> VibeCost
}

// On-surface variant warm gray for secondary text
private val OnSurfaceVariantLight = Color(0xFF6B5E54)
private val OnSurfaceVariantDark = Color(0xFFA89888)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE8722A),
    primaryContainer = Color(0xFFC45A1C),
    onPrimary = Color(0xFFFFFFFF),
    surface = Color(0xFFF5EDE3),
    onSurface = Color(0xFF2D2926),
    onSurfaceVariant = OnSurfaceVariantLight,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF2D2926),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE89A5E),
    onPrimary = Color(0xFF1A1412),  // dark brown — legible text on light orange buttons
    surface = Color(0xFF2D2520),
    onSurface = Color(0xFFEDE0D4),
    onSurfaceVariant = OnSurfaceVariantDark,
    background = Color(0xFF1A1412),
    onBackground = Color(0xFFEDE0D4),
)
