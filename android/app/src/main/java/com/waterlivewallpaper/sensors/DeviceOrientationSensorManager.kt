package com.waterlivewallpaper.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs
import kotlin.math.sqrt

class DeviceOrientationSensorManager(
    context: Context,
    private val onOrientationUpdate: (gravityX: Float, gravityY: Float, isShake: Boolean, impulseX: Float, impulseY: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private var smoothGx = 0f
    private var smoothGy = 9.8f

    private var prevAx = 0f
    private var prevAy = 0f
    private var prevAz = 0f

    var displayRotation: Int = Surface.ROTATION_0

    fun start(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME) {
        gravitySensor?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun hasGravitySensor(): Boolean = gravitySensor != null

    fun hasAccelerometer(): Boolean = accelerometer != null

    fun hasRotationVector(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val rawX = event.values[0]
                val rawY = event.values[1]

                // Transform raw sensor coordinates to Screen Space:
                // Down on phone (charging port) is +Y in screen space.
                // Right on phone is +X in screen space.
                // Raw accelerometer measures reaction force, so tilt right gives rawX < 0 -> screenGx = -rawX.
                var targetGx = -rawX
                var targetGy = rawY

                when (displayRotation) {
                    Surface.ROTATION_90 -> {
                        targetGx = -rawY
                        targetGy = -rawX
                    }
                    Surface.ROTATION_180 -> {
                        targetGx = rawX
                        targetGy = -rawY
                    }
                    Surface.ROTATION_270 -> {
                        targetGx = rawY
                        targetGy = rawX
                    }
                    Surface.ROTATION_0 -> {}
                }

                // Dynamic Adaptive Low-Pass Filter:
                // Heavy smoothing when still (no hand tremor/jitter),
                // Instant zero-lag responsiveness when shaken or tilted fast!
                val delta = sqrt((targetGx - smoothGx) * (targetGx - smoothGx) + (targetGy - smoothGy) * (targetGy - smoothGy))
                val alpha = when {
                    delta < 0.20f -> 0.94f // Resting: eliminate micro-jitter
                    delta < 0.70f -> 0.60f // Slow gentle tilt: smooth transition
                    delta < 2.00f -> 0.25f // Moderate tilt: rapid response
                    else -> 0.06f          // Fast shake: immediate instant tracking without lag!
                }

                smoothGx = alpha * smoothGx + (1f - alpha) * targetGx
                smoothGy = alpha * smoothGy + (1f - alpha) * targetGy

                // Deadzone clamp to prevent micro-jitter when resting
                if (abs(smoothGx) < 0.05f) smoothGx = 0f

                onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // Fallback gravity if TYPE_GRAVITY is missing
                if (gravitySensor == null) {
                    var targetGx = -ax
                    var targetGy = ay

                    when (displayRotation) {
                        Surface.ROTATION_90 -> {
                            targetGx = -ay
                            targetGy = -ax
                        }
                        Surface.ROTATION_180 -> {
                            targetGx = ax
                            targetGy = -ay
                        }
                        Surface.ROTATION_270 -> {
                            targetGx = ay
                            targetGy = ax
                        }
                        Surface.ROTATION_0 -> {}
                    }

                    smoothGx = 0.88f * smoothGx + 0.12f * targetGx
                    smoothGy = 0.88f * smoothGy + 0.12f * targetGy
                    onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
                }

                // Continuous dynamic motion detection (Jerk / High-frequency surge)
                val dax = ax - prevAx
                val day = ay - prevAy
                val daz = az - prevAz
                prevAx = ax
                prevAy = ay
                prevAz = az

                val jerkMag = sqrt(dax * dax + day * day + daz * daz)
                // If movement exceeds quiet hand tremor threshold (> 2.8 m/s² jerk)
                if (jerkMag > 2.8f) {
                    var screenJerkX = -dax
                    var screenJerkY = day

                    when (displayRotation) {
                        Surface.ROTATION_90 -> {
                            screenJerkX = -day
                            screenJerkY = -dax
                        }
                        Surface.ROTATION_180 -> {
                            screenJerkX = dax
                            screenJerkY = -day
                        }
                        Surface.ROTATION_270 -> {
                            screenJerkX = day
                            screenJerkY = dax
                        }
                        Surface.ROTATION_0 -> {}
                    }

                    // Quadratic kinetic boost: slow shakes stay natural and gentle,
                    // but fast energetic shakes scale up aggressively!
                    val intensityBoost = (1.0f + (jerkMag / 3.6f) * (jerkMag / 3.6f)).coerceAtMost(6.5f)
                    val impulseX = screenJerkX * 36f * intensityBoost
                    val impulseY = screenJerkY * 36f * intensityBoost
                    onOrientationUpdate(smoothGx, smoothGy, true, impulseX, impulseY)
                }
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val lx = event.values[0]
                val ly = event.values[1]
                val lz = event.values[2]
                val linMag = sqrt(lx * lx + ly * ly + lz * lz)

                if (linMag > 2.0f) {
                    var screenLinX = -lx
                    var screenLinY = ly

                    when (displayRotation) {
                        Surface.ROTATION_90 -> {
                            screenLinX = -ly
                            screenLinY = -lx
                        }
                        Surface.ROTATION_180 -> {
                            screenLinX = lx
                            screenLinY = -ly
                        }
                        Surface.ROTATION_270 -> {
                            screenLinX = ly
                            screenLinY = lx
                        }
                        Surface.ROTATION_0 -> {}
                    }

                    val intensityBoost = (1.0f + (linMag / 2.8f) * (linMag / 2.8f)).coerceAtMost(6.5f)
                    val impulseX = screenLinX * 30f * intensityBoost
                    val impulseY = screenLinY * 30f * intensityBoost
                    onOrientationUpdate(smoothGx, smoothGy, true, impulseX, impulseY)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
