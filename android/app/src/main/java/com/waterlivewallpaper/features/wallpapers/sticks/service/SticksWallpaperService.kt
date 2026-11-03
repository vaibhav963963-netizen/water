package com.waterlivewallpaper.features.wallpapers.sticks.service

import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowManager
import com.waterlivewallpaper.features.wallpapers.sticks.gl.SticksGlRenderer
import com.waterlivewallpaper.features.wallpapers.sticks.simulation.FloatingSticksEngine
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences
import com.waterlivewallpaper.features.wallpapers.water.sensors.DeviceOrientationSensorManager
import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine

/**
 * Android Live Wallpaper Service for Floating Sticks Water.
 * Simulates fluid dynamics with 3D wooden floating sticks/twigs responding to tilt,
 * Archimedes buoyancy, viscous flow drag, and touch.
 */
open class SticksWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return SticksWallpaperEngine()
    }

    inner class SticksWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private lateinit var preferences: WallpaperPreferences
        private lateinit var simulationEngine: FluidSimulationEngine
        private lateinit var sticksEngine: FloatingSticksEngine
        private lateinit var sensorManager: DeviceOrientationSensorManager
        private var renderer: SticksGlRenderer? = null

        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var isVisibleState = false

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            preferences = WallpaperPreferences(this@SticksWallpaperService)
            preferences.registerListener(this)

            simulationEngine = FluidSimulationEngine(
                screenWidth = 1080,
                screenHeight = 2400,
                waterVolumeRatio = preferences.getWaterVolume(),
                physicsSensitivity = preferences.getPhysicsSensitivity(),
                shakeSensitivity = preferences.getShakeSensitivity(),
                flowSpeed = preferences.getFlowSpeed()
            )

            sticksEngine = FloatingSticksEngine(
                screenWidth = 1080,
                screenHeight = 2400
            )
            sticksEngine.initializeDefaultSticks(
                stickCount = preferences.getStickCount(),
                scale = preferences.getStickSize()
            )

            sensorManager = DeviceOrientationSensorManager(this@SticksWallpaperService) { gx, gy, isShake, impX, impY ->
                simulationEngine.updateGravity(gx, gy)
                if (isShake) {
                    renderer?.notifyInteraction()
                    simulationEngine.applyImpulse(impX, impY)
                    sticksEngine.applyShakeImpulse(impX, impY)
                }
            }

            renderer = SticksGlRenderer(this@SticksWallpaperService, preferences, simulationEngine, sticksEngine)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisibleState = visible
            if (visible) {
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                sensorManager.displayRotation = windowManager.defaultDisplay.rotation
                sensorManager.start()

                if (surfaceHolder?.surface?.isValid == true && surfaceWidth > 0 && surfaceHeight > 0) {
                    renderer?.start(surfaceHolder.surface, surfaceWidth, surfaceHeight)
                }
            } else {
                sensorManager.stop()
                renderer?.stop()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            if (isVisibleState && holder?.surface?.isValid == true && surfaceWidth > 0 && surfaceHeight > 0) {
                renderer?.start(holder.surface, surfaceWidth, surfaceHeight)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.surfaceWidth = width
            this.surfaceHeight = height

            simulationEngine.setDimensions(width, height)
            sticksEngine.setDimensions(width, height)
            renderer?.onResize(width, height)

            if (isVisibleState && holder?.surface?.isValid == true) {
                renderer?.start(holder.surface, width, height)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            renderer?.stop()
        }

        override fun onTouchEvent(event: MotionEvent?) {
            if (event == null) return
            renderer?.notifyInteraction()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        val touchX = event.getX(i)
                        val touchY = event.getY(i)
                        simulationEngine.addTouchDisturbance(touchX, touchY, strength = 700f)
                        sticksEngine.addTouchDisturbance(touchX, touchY, strength = 600f)
                    }
                }
            }
            super.onTouchEvent(event)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            simulationEngine.updateSettings(
                volume = preferences.getWaterVolume(),
                sensitivity = preferences.getPhysicsSensitivity(),
                shakeSens = preferences.getShakeSensitivity(),
                speed = preferences.getFlowSpeed(),
                batterySaver = preferences.isBatterySaver()
            )
            sticksEngine.initializeDefaultSticks(
                stickCount = preferences.getStickCount(),
                scale = preferences.getStickSize()
            )
        }

        override fun onDestroy() {
            super.onDestroy()
            sensorManager.stop()
            renderer?.stop()
            preferences.unregisterListener(this)
        }
    }
}
