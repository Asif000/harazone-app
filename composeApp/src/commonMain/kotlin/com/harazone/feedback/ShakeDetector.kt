package com.harazone.feedback

interface ShakeDetector {
    fun start(onShake: () -> Unit)
    fun stop()
}
