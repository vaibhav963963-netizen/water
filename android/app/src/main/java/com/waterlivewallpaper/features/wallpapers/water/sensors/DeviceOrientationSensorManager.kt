package com.waterlivewallpaper.features.wallpapers.water.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs

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

    // Dynamic Stability Lock / Latch: eliminates sensor tremor & micro-fluctuations when device is steady
    private var latchedGx = 0f
    private var latchedGy = 9.8f
    private var isStableLocked = false
    private var stableSamplesCount = 0

    var displayRotation: Int = Surface.ROTATION_0

    private var lastCallbackGx = 0f
    private var lastCallbackGy = 0f

    private var lastShakeTimeNs: Long = 0L
    private val shakeThresholdSq = 2.6f * 2.6f // Calibrated dynamic acceleration (~2.6 m/s²) for responsive hand shake
    private val shakeCooldownNs = 280_000_000L  // 280ms cooldown between splashes
    private val impulseScale = 180f            // Calibrated impulse scaling for realistic water splash and droplet launch

    fun start(samplingPeriodUs: Int = 33_333) { // ~30 Hz: Optimal battery efficiency & silky fluid tracking
        isStableLocked = false
        stableSamplesCount = 0
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, samplingPeriodUs)
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
        }

        if (linearAccelSensor != null) {
            sensorManager.registerListener(this, linearAccelSensor, samplingPeriodUs)
        }
        if (accelerometer != null && linearAccelSensor == null && gravitySensor != null) {
            // Only need accelerometer if linearAccelSensor is missing (to provide fallback dynamic shake)
            sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun hasGravitySensor(): Boolean = gravitySensor != null
    fun hasAccelerometer(): Boolean = accelerometer != null
    fun hasRotationVector(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    /**
     * Transforms 2D device sensor coordinates (X right, Y up) into screen coordinates
     * (X right, Y down) for all 4 device orientations:
     * ROTATION_0 (Portrait), ROTATION_90 (Landscape), ROTATION_180 (Inverted Portrait), ROTATION_270 (Reverse Landscape).
     */
    private fun transformToScreenSpace(devX: Float, devY: Float): Pair<Float, Float> {
        return when (displayRotation) {
            Surface.ROTATION_90 -> Pair(devY, devX)
            Surface.ROTATION_180 -> Pair(devX, -devY)
            Surface.ROTATION_270 -> Pair(-devY, -devX)
            Surface.ROTATION_0 -> Pair(-devX, devY)
            else -> Pair(-devX, devY)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val rawX = event.values[0]
                val rawY = event.values[1]

                val (targetGx, targetGy) = transformToScreenSpace(rawX, rawY)

                // If currently locked in steady state, check for intentional rotation:
                if (isStableLocked) {
                    val dLatchedX = targetGx - latchedGx
                    val dLatchedY = targetGy - latchedGy
                    val intentionalDeltaSq = dLatchedX * dLatchedX + dLatchedY * dLatchedY

                    // Require at least ~2.5 deg tilt (0.42 m/s²) to break lock
                    if (intentionalDeltaSq >= 0.0625f) {
                        isStableLocked = false
                        stableSamplesCount = 0
                    } else {
                        // Phone is stable: remain locked, suppress all noise updates
                        return
                    }
                }

                // Check motion velocity
                val diffX = targetGx - smoothGx
                val diffY = targetGy - smoothGy
                val delta2 = diffX * diffX + diffY * diffY

                // Heavy smoothing for gentle movements, fast response for energetic tilt
                val alpha = when {
                    delta2 < 0.04f -> 0.90f
                    delta2 < 0.40f -> 0.60f
                    else -> 0.20f
                }

                smoothGx = alpha * smoothGx + (1f - alpha) * targetGx
                smoothGy = alpha * smoothGy + (1f - alpha) * targetGy

                // Stability detection: if sensor delta is consistently tiny (< 0.22 m/s²), latch steady state
                if (delta2 < 0.0484f) {
                    stableSamplesCount++
                    if (stableSamplesCount >= 6) { // ~200ms of stillness
                        isStableLocked = true
                        // Snap near-zero upright deadzones
                        if (abs(smoothGx) < 0.35f) smoothGx = 0f
                        if (abs(smoothGy - 9.8f) < 0.35f && smoothGx == 0f) smoothGy = 9.8f
                        if (abs(smoothGy - (-9.8f)) < 0.35f && smoothGx == 0f) smoothGy = -9.8f

                        latchedGx = smoothGx
                        latchedGy = smoothGy

                        val cbDiffX = smoothGx - lastCallbackGx
                        val cbDiffY = smoothGy - lastCallbackGy
                        if (cbDiffX * cbDiffX + cbDiffY * cbDiffY > 0.0016f) {
                            lastCallbackGx = smoothGx
                            lastCallbackGy = smoothGy
                            onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
                        }
                        return
                    }
                } else {
                    stableSamplesCount = 0
                }

                val cbDiffX = smoothGx - lastCallbackGx
                val cbDiffY = smoothGy - lastCallbackGy
                if (cbDiffX * cbDiffX + cbDiffY * cbDiffY > 0.0064f) {
                    lastCallbackGx = smoothGx
                    lastCallbackGy = smoothGy
                    onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
                }
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val lx = event.values[0]
                val ly = event.values[1]
                val lz = event.values[2]

                val (screenLinX, screenLinY) = transformToScreenSpace(lx, ly)
                val linMag2 = screenLinX * screenLinX + screenLinY * screenLinY + lz * lz

                val nowNs = System.nanoTime()
                if (linMag2 >= shakeThresholdSq && (nowNs - lastShakeTimeNs >= shakeCooldownNs)) {
                    lastShakeTimeNs = nowNs
                    isStableLocked = false
                    stableSamplesCount = 0

                    val impulseX = screenLinX * impulseScale
                    val impulseY = screenLinY * impulseScale
                    onOrientationUpdate(smoothGx, smoothGy, true, impulseX, impulseY)
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // Fallback gravity if gravity sensor is missing
                if (gravitySensor == null) {
                    val (targetGx, targetGy) = transformToScreenSpace(ax, ay)

                    if (isStableLocked) {
                        val dLatchedX = targetGx - latchedGx
                        val dLatchedY = targetGy - latchedGy
                        val intentionalDeltaSq = dLatchedX * dLatchedX + dLatchedY * dLatchedY
                        if (intentionalDeltaSq >= 0.0625f) {
                            isStableLocked = false
                            stableSamplesCount = 0
                        } else {
                            return
                        }
                    }

                    val diffX = targetGx - smoothGx
                    val diffY = targetGy - smoothGy
                    val delta2 = diffX * diffX + diffY * diffY

                    val alpha = when {
                        delta2 < 0.04f -> 0.90f
                        delta2 < 0.40f -> 0.60f
                        else -> 0.20f
                    }

                    smoothGx = alpha * smoothGx + (1f - alpha) * targetGx
                    smoothGy = alpha * smoothGy + (1f - alpha) * targetGy

                    if (delta2 < 0.0484f) {
                        stableSamplesCount++
                        if (stableSamplesCount >= 6) {
                            isStableLocked = true
                            if (abs(smoothGx) < 0.35f) smoothGx = 0f
                            if (abs(smoothGy - 9.8f) < 0.35f && smoothGx == 0f) smoothGy = 9.8f
                            if (abs(smoothGy - (-9.8f)) < 0.35f && smoothGx == 0f) smoothGy = -9.8f

                            latchedGx = smoothGx
                            latchedGy = smoothGy

                            val cbDiffX = smoothGx - lastCallbackGx
                            val cbDiffY = smoothGy - lastCallbackGy
                            if (cbDiffX * cbDiffX + cbDiffY * cbDiffY > 0.0016f) {
                                lastCallbackGx = smoothGx
                                lastCallbackGy = smoothGy
                                onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
                            }
                            return
                        }
                    } else {
                        stableSamplesCount = 0
                    }

                    val cbDiffX = smoothGx - lastCallbackGx
                    val cbDiffY = smoothGy - lastCallbackGy
                    if (cbDiffX * cbDiffX + cbDiffY * cbDiffY > 0.0064f) {
                        lastCallbackGx = smoothGx
                        lastCallbackGy = smoothGy
                        onOrientationUpdate(smoothGx, smoothGy, false, 0f, 0f)
                    }
                }

                // Fallback shake detection if linear acceleration sensor is missing
                if (linearAccelSensor == null) {
                    val (screenAx, screenAy) = transformToScreenSpace(ax, ay)
                    val screenLinX = screenAx - smoothGx
                    val screenLinY = screenAy - smoothGy
                    val dynMag2 = screenLinX * screenLinX + screenLinY * screenLinY

                    val nowNs = System.nanoTime()
                    if (dynMag2 >= shakeThresholdSq && (nowNs - lastShakeTimeNs >= shakeCooldownNs)) {
                        lastShakeTimeNs = nowNs
                        isStableLocked = false
                        stableSamplesCount = 0

                        val impulseX = screenLinX * impulseScale
                        val impulseY = screenLinY * impulseScale
                        onOrientationUpdate(smoothGx, smoothGy, true, impulseX, impulseY)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
