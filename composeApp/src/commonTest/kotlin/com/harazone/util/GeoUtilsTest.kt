package com.harazone.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoUtilsTest {

    @Test
    fun samePoint_returnsZeroDistance() {
        val d = haversineDistanceMeters(38.7139, -9.1394, 38.7139, -9.1394)
        assertEquals(0.0, d, 0.001)
    }

    @Test
    fun pointsAbout50mApart_returnsApprox50() {
        // ~50m north of origin (0.00045 degrees latitude ~ 50m)
        val d = haversineDistanceMeters(38.7139, -9.1394, 38.71435, -9.1394)
        assertTrue(d in 45.0..55.0, "Expected ~50m, got $d")
    }

    @Test
    fun pointsAbout1000mApart() {
        // ~1km north
        val d = haversineDistanceMeters(38.7139, -9.1394, 38.7229, -9.1394)
        assertTrue(d in 950.0..1050.0, "Expected ~1000m, got $d")
    }

    @Test
    fun knownDistance_newYorkToNewark() {
        // NYC (40.7128, -74.0060) to Newark (40.7357, -74.1724) ~ 14.5 km
        val d = haversineDistanceMeters(40.7128, -74.0060, 40.7357, -74.1724)
        assertTrue(d in 13_000.0..16_000.0, "Expected ~14.5km, got ${d / 1000} km")
    }
}
