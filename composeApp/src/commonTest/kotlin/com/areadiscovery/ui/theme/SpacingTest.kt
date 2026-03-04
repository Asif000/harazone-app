package com.areadiscovery.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpacingTest {

    @Test
    fun spacing_xs_is4dp() {
        assertEquals(4.dp, Spacing.xs)
    }

    @Test
    fun spacing_sm_is8dp() {
        assertEquals(8.dp, Spacing.sm)
    }

    @Test
    fun spacing_bucketInternal_is12dp() {
        assertEquals(12.dp, Spacing.bucketInternal)
    }

    @Test
    fun spacing_md_is16dp() {
        assertEquals(16.dp, Spacing.md)
    }

    @Test
    fun spacing_lg_is24dp() {
        assertEquals(24.dp, Spacing.lg)
    }

    @Test
    fun spacing_xl_is32dp() {
        assertEquals(32.dp, Spacing.xl)
    }

    @Test
    fun spacing_touchTarget_is48dp() {
        // AC #8: All interactive elements use minimum 48dp touch targets
        assertEquals(48.dp, Spacing.touchTarget)
    }

    @Test
    fun spacing_allValues_are8dpBaseMultiples_exceptXsAndBucketInternal() {
        // AC #7: 8dp base unit
        assertTrue(Spacing.sm.value % 8f == 0f, "sm should be 8dp base multiple")
        assertTrue(Spacing.md.value % 8f == 0f, "md should be 8dp base multiple")
        assertTrue(Spacing.lg.value % 8f == 0f, "lg should be 8dp base multiple")
        assertTrue(Spacing.xl.value % 8f == 0f, "xl should be 8dp base multiple")
        assertTrue(Spacing.touchTarget.value % 8f == 0f, "touchTarget should be 8dp base multiple")
    }
}
