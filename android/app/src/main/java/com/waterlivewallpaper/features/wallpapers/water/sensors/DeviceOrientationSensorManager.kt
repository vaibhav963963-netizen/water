package com.waterlivewallpaper.features.wallpapers.water.sensors

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

    private var lastCallbackGx = 0f
    private var lastCallbackGy = 0f

    private var lastShakeTimeNs: Long = 0L
    private val shakeThresholdSq = 11.0f * 11.0f // Require vigorous shake gesture (> 11 m/s²)
    private val shakeCooldownNs = 320_000_000L   // 320ms cooldown between splashes

    fun start(samplingPeriodUs: Int = 33_333) { // ~30 Hz: Optimal battery efficiency & silky fluid tracking
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, samplingPeriodUs)
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
        }

        if (linearAccelSensor != null) {
            sensorManager.registerListener(this, linearAccelSensor, samplingPeriodUs)
        } else if (gravitySensor != null && accelerometer != null) {
            // Accelerometer only needed as fallback when linear acceleration sensor is missing
            sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
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

                // Check change delta
                val diffX = targetGx - smoothGx
                val diffY = targetGy - smoothGy
                val delta2 = diffX * diffX + diffY * diffY

                // Filter resting hand tremor (< 0.15 m/s²)
                val alpha = when {
                    delta2 < 0.0225f -> 0.92f // Resting
                    delta2 < 0.36f -> 0.55f   // Gentle tilt
                    else -> 0.15f             // Fast tilt / energetic movement
                }

                smoothGx = alpha * smoothGx + (1f - alpha) * targetGx
                smoothGy = alpha * smoothGy + (1f - alpha) * targetGy

                // Enhanced deadzone for physiological hand tremor (< 0.45 m/s² ~ 2.6 deg)
                if (abs(smoothGx) < 0.45f) smoothGx = 0f
                if (targetGy > 3.5f && smoothGx == 0f) {
                    smoothGy = 9.8f
                } else if (abs(smoothGy - 9.8f) < 0.35f) {
                    smoothGy = 9.8f
                }

                // Deliver updates if gravity changed or if it just snapped back to 0 at rest
                val cbDiffX = smoothGx - lastCallbackGx
                val cbDiffY = smoothGy - lastCallbackGy
                val shouldUpdate = (cbDiffX * cbDiffX + cbDiffY * cbDiffY > 0.0064f) || 
                                   (smoothGx == 0f && lastCallbackGx != 0f)
                if (shouldUpdate) {
                    lastCallbackGx = smoothGx
                    lastCallbackGy = smoothGy
                    onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
                }
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val lx = event.values[0]
                val ly = event.values[1]
                val lz = event.values[2]
                val linMag2 = lx * lx + ly * ly + lz * lz

                val nowNs = System.nanoTime()
                if (linMag2 >= shakeThresholdSq && (nowNs - lastShakeTimeNs >= shakeCooldownNs)) {
                    lastShakeTimeNs = nowNs
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

                    val scaleFactor = 30f
                    val impulseX = screenLinX * scaleFactor
                    val impulseY = screenLinY * scaleFactor
                    onOrientationUpdate(smoothGx, smoothGy, true, impulseX, impulseY)
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // Fallback gravity if gravity sensor is missing
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

                    smoothGx = 0.85f * smoothGx + 0.15f * targetGx
                    smoothGy = 0.85f * smoothGy + 0.15f * targetGy

                    if (abs(smoothGx) < 0.45f) smoothGx = 0f
                    if (targetGy > 3.5f && smoothGx == 0f) {
                        smoothGy = 9.8f
                    } else if (abs(smoothGy - 9.8f) < 0.35f) {
                        smoothGy = 9.8f
                    }

                    val cbDiffX = smoothGx - lastCallbackGx
                    val cbDiffY = smoothGy - lastCallbackGy
                    val shouldUpdate = (cbDiffX * cbDiffX + cbDiffY * cbDiffY > 0.0064f) || 
                                       (smoothGx == 0f && lastCallbackGx != 0f)
                    if (shouldUpdate) {
                        lastCallbackGx = smoothGx
                        lastCallbackGy = smoothGy
                        onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
                    }
                }

                // If linear acceleration sensor is missing, calculate dynamic acceleration = total - gravity
                if (linearAccelSensor == null) {
                    val dax = ax - (-smoothGx)
                    val day = ay - smoothGy
                    val dynMag2 = dax * dax + day * day

                    val nowNs = System.nanoTime()
                    if (dynMag2 >= shakeThresholdSq && (nowNs - lastShakeTimeNs >= shakeCooldownNs)) {
                        lastShakeTimeNs = nowNs
                        var screenLinX = -dax
                        var screenLinY = day

                        when (displayRotation) {
                            Surface.ROTATION_90 -> {
                                screenLinX = -day
                                screenLinY = -dax
                            }
                            Surface.ROTATION_180 -> {
                                screenLinX = dax
                                screenLinY = -day
                            }
                            Surface.ROTATION_270 -> {
                                screenLinX = day
                                screenLinY = dax
                            }
                            Surface.ROTATION_0 -> {}
                        }

                        val impulseX = screenLinX * 30f
                        val impulseY = screenLinY * 30f
                        onOrientationUpdate(smoothGx, smoothGy, true, impulseX, impulseY)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
