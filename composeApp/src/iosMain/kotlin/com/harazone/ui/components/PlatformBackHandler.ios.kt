package com.harazone.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS — iOS uses swipe-back gesture handled by the navigation controller
}
