package com.harazone.util

import kotlin.math.*

fun haversineDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val R = 6_371_000.0
    val phi1 = lat1 * PI / 180.0
    val phi2 = lat2 * PI / 180.0
    val dPhi = (lat2 - lat1) * PI / 180.0
    val dLambda = (lng2 - lng1) * PI / 180.0
    val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}
