@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.harazone.feedback

import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970
import kotlin.math.sqrt

class IosShakeDetector : ShakeDetector {
    private val motionManager = CMMotionManager()
    private val thresholdG = 2.5
    private var lastShakeMs = 0L
    private val debounceMs = 1000L

    override fun start(onShake: () -> Unit) {
        if (!motionManager.accelerometerAvailable) return
        motionManager.accelerometerUpdateInterval = 0.1 // 10 Hz
        motionManager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            data?.let {
                it.acceleration.useContents {
                    val g = sqrt(x * x + y * y + z * z)
                    val nowMs = (NSDate().timeIntervalSince1970 * 1000).toLong()
                    if (g > thresholdG && nowMs - lastShakeMs > debounceMs) {
                        lastShakeMs = nowMs
                        onShake()
                    }
                }
            }
        }
    }

    override fun stop() {
        motionManager.stopAccelerometerUpdates()
    }
}
