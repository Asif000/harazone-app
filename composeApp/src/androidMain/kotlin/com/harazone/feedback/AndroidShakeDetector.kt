package com.harazone.feedback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class AndroidShakeDetector(context: Context) : ShakeDetector {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var listener: SensorEventListener? = null
    private var lastShakeMs = 0L
    private val thresholdG = 2.5f
    private val debounceMs = 1000L

    override fun start(onShake: () -> Unit) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0] / SensorManager.GRAVITY_EARTH
                val y = event.values[1] / SensorManager.GRAVITY_EARTH
                val z = event.values[2] / SensorManager.GRAVITY_EARTH
                val g = sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (g > thresholdG && now - lastShakeMs > debounceMs) {
                    lastShakeMs = now
                    onShake()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
    }

    override fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }
}
