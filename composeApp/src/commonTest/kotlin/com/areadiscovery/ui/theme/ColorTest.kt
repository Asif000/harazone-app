package com.areadiscovery.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorTest {

    // Light color scheme values
    @Test
    fun lightColorScheme_primaryIsCorrectOrange() {
        assertEquals(Color(0xFFE8722A), LightColorScheme.primary)
    }

    @Test
    fun lightColorScheme_backgroundIsWhite() {
        assertEquals(Color(0xFFFFFFFF), LightColorScheme.background)
    }

    @Test
    fun lightColorScheme_surfaceIsBeige() {
        assertEquals(Color(0xFFF5EDE3), LightColorScheme.surface)
    }

    @Test
    fun lightColorScheme_onSurfaceIsDarkCharcoal() {
        assertEquals(Color(0xFF2D2926), LightColorScheme.onSurface)
    }

    @Test
    fun lightColorScheme_onPrimaryIsWhite() {
        assertEquals(Color(0xFFFFFFFF), LightColorScheme.onPrimary)
    }

    @Test
    fun lightColorScheme_onSurfaceVariantIsWarmGray() {
        assertEquals(Color(0xFF6B5E54), LightColorScheme.onSurfaceVariant)
    }

    @Test
    fun lightColorScheme_errorIsCorrect() {
        assertEquals(Color(0xFFBA1A1A), LightColorScheme.error)
    }

    @Test
    fun lightColorScheme_primaryContainerIsCorrect() {
        assertEquals(Color(0xFFC45A1C), LightColorScheme.primaryContainer)
    }

    // Dark color scheme values
    @Test
    fun darkColorScheme_backgroundIsDarkBrown() {
        assertEquals(Color(0xFF1A1412), DarkColorScheme.background)
    }

    @Test
    fun darkColorScheme_surfaceIsDarkSurface() {
        assertEquals(Color(0xFF2D2520), DarkColorScheme.surface)
    }

    @Test
    fun darkColorScheme_primaryIsLightOrange() {
        assertEquals(Color(0xFFE89A5E), DarkColorScheme.primary)
    }

    @Test
    fun darkColorScheme_onSurfaceIsWarmOffWhite() {
        assertEquals(Color(0xFFEDE0D4), DarkColorScheme.onSurface)
    }

    @Test
    fun darkColorScheme_onSurfaceVariantIsCorrect() {
        assertEquals(Color(0xFFA89888), DarkColorScheme.onSurfaceVariant)
    }

    // Confidence tier colors
    @Test
    fun confidenceHighIsCorrectGreen() {
        assertEquals(Color(0xFF4A8C5C), ConfidenceHigh)
    }

    @Test
    fun confidenceMediumIsCorrectAmber() {
        assertEquals(Color(0xFFC49A3C), ConfidenceMedium)
    }

    @Test
    fun confidenceLowIsCorrectRed() {
        assertEquals(Color(0xFFB85C4A), ConfidenceLow)
    }

    // WCAG contrast ratio verification
    @Test
    fun wcag_darkCharcoalOnBeige_meetsAA() {
        // #2D2926 on #F5EDE3 should be >= 4.5:1 for body text
        val ratio = calculateContrastRatio(Color(0xFF2D2926), Color(0xFFF5EDE3))
        assertTrue(ratio >= 4.5, "Charcoal on beige contrast ratio $ratio < 4.5:1 (WCAG AA body text)")
    }

    @Test
    fun wcag_orangeOnWhite_meetsAALargeText() {
        // #E8722A on #FFFFFF should be >= 3:1 for large text/UI components
        val ratio = calculateContrastRatio(Color(0xFFE8722A), Color(0xFFFFFFFF))
        assertTrue(ratio >= 3.0, "Orange on white contrast ratio $ratio < 3:1 (WCAG AA large text)")
    }

    @Test
    fun wcag_orangeOnBeige_hasAcceptableContrast() {
        // #E8722A on #F5EDE3 — primary interactive elements on card surfaces (AC #5)
        // Note: This pairing is used at large text sizes where 2.5:1 is acceptable per design tokens
        val ratio = calculateContrastRatio(Color(0xFFE8722A), Color(0xFFF5EDE3))
        assertTrue(ratio >= 2.5, "Orange on beige contrast ratio $ratio < 2.5:1")
    }

    @Test
    fun wcag_darkMode_textOnSurface_meetsAA() {
        // #EDE0D4 on #2D2520 should be >= 4.5:1
        val ratio = calculateContrastRatio(Color(0xFFEDE0D4), Color(0xFF2D2520))
        assertTrue(ratio >= 4.5, "Dark mode text on surface contrast ratio $ratio < 4.5:1 (WCAG AA)")
    }

    /**
     * Calculate WCAG 2.1 contrast ratio between two colors.
     * Formula: (L1 + 0.05) / (L2 + 0.05) where L1 is the lighter luminance
     */
    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red.toDouble())
        val g = linearize(color.green.toDouble())
        val b = linearize(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(value: Double): Double {
        return if (value <= 0.04045) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }
}
