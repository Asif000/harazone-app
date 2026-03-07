package com.areadiscovery.ui.map.components

import kotlin.test.Test
import kotlin.test.assertEquals

class VibeRailSizingTest {

    @Test
    fun normalRange_minGets32_maxGets48() {
        assertEquals(32f, computeVibeSizeDp(count = 1, minCount = 1, maxCount = 6))
        assertEquals(48f, computeVibeSizeDp(count = 6, minCount = 1, maxCount = 6))
    }

    @Test
    fun normalRange_midValue_isProportional() {
        // count=3, min=1, max=6 → 32 + 16*(3-1)/(6-1) = 32 + 6.4 = 38.4
        val result = computeVibeSizeDp(count = 3, minCount = 1, maxCount = 6)
        assertEquals(38.4f, result, 0.01f)
    }

    @Test
    fun allEqual_returns40dp() {
        assertEquals(40f, computeVibeSizeDp(count = 5, minCount = 5, maxCount = 5))
    }

    @Test
    fun zeroCount_withOthersNonZero_returns32dp() {
        assertEquals(32f, computeVibeSizeDp(count = 0, minCount = 0, maxCount = 10))
    }

    @Test
    fun allZero_returns40dp() {
        // All-equal (including all-zero) returns midpoint 40dp
        assertEquals(40f, computeVibeSizeDp(count = 0, minCount = 0, maxCount = 0))
    }

    @Test
    fun singleVibeHasPois_othersZero() {
        // The vibe with POIs gets 48dp, zeros get 32dp
        assertEquals(48f, computeVibeSizeDp(count = 10, minCount = 0, maxCount = 10))
        assertEquals(32f, computeVibeSizeDp(count = 0, minCount = 0, maxCount = 10))
    }

    @Test
    fun maxCountEqualsMinCount_noDivByZero() {
        // Should not crash — early return handles it
        val result = computeVibeSizeDp(count = 3, minCount = 3, maxCount = 3)
        assertEquals(40f, result)
    }

    @Test
    fun countBelowMin_clampsTo32dp() {
        // Vibe not in map defaults to 0, but minCount from map values may be > 0
        // Formula would go negative — must clamp to 32f
        val result = computeVibeSizeDp(count = 0, minCount = 3, maxCount = 8)
        assertEquals(32f, result)
    }

    @Test
    fun countAboveMax_clampsTo48dp() {
        // Defensive: count should never exceed maxCount but guard anyway
        val result = computeVibeSizeDp(count = 100, minCount = 1, maxCount = 6)
        assertEquals(48f, result)
    }
}
