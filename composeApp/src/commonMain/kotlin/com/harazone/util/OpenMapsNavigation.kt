package com.harazone.util

/**
 * Opens the platform's default maps app with navigation to the given coordinates.
 * Android: geo intent with label. iOS: Apple Maps URL scheme.
 * Returns true if the maps app was launched, false otherwise.
 */
expect fun openMapsNavigation(lat: Double, lng: Double, name: String): Boolean
