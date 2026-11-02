package com.waterlivewallpaper.bridge

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.waterlivewallpaper.config.WallpaperPreferences
import com.waterlivewallpaper.gl.WaterGlRenderer
import com.waterlivewallpaper.sensors.DeviceOrientationSensorManager
import com.waterlivewallpaper.simulation.FluidSimulationEngine

class WaterPreviewGlView(context: Context) : GLSurfaceView(context) {

    private val preferences = WallpaperPreferences(context)
    val simulationEngine: FluidSimulationEngine = FluidSimulationEngine(
        screenWidth = 720,
        screenHeight = 1280,
        waterVolumeRatio = preferences.getWaterVolume(),
        physicsSensitivity = preferences.getPhysicsSensitivity(),
        shakeSensitivity = preferences.getShakeSensitivity()
    )

    private val sensorManager = DeviceOrientationSensorManager(context) { gx, gy, isShake, impX, impY ->
        simulationEngine.updateGravity(gx, gy)
        if (isShake) {
            simulationEngine.applyImpulse(impX, impY)
        }
    }

    private var rendererInstance: WaterGlRenderer? = null

    init {
        setEGLContextClientVersion(2)
        // Renderer runs on dedicated surface thread
        rendererInstance = WaterGlRenderer(preferences, simulationEngine)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.stop()
        rendererInstance?.stop()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && preferences.isTouchEnabled()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    simulationEngine.addTouchDisturbance(event.x, event.y, 550f)
                }
            }
        }
        return true
    }
}
