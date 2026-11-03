package com.waterlivewallpaper.features.wallpapers.water.service

import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowManager
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences
import com.waterlivewallpaper.features.wallpapers.water.gl.WaterGlRenderer
import com.waterlivewallpaper.features.wallpapers.water.sensors.DeviceOrientationSensorManager
import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine

class WaterWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return WaterWallpaperEngine()
    }

    inner class WaterWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private lateinit var preferences: WallpaperPreferences
        private lateinit var simulationEngine: FluidSimulationEngine
        private lateinit var sensorManager: DeviceOrientationSensorManager
        private var renderer: WaterGlRenderer? = null

        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var isVisibleState = false

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            preferences = WallpaperPreferences(this@WaterWallpaperService)
            preferences.registerListener(this)

            simulationEngine = FluidSimulationEngine(
                screenWidth = 1080,
                screenHeight = 2400,
                waterVolumeRatio = preferences.getWaterVolume(),
                physicsSensitivity = preferences.getPhysicsSensitivity(),
                shakeSensitivity = preferences.getShakeSensitivity(),
                flowSpeed = preferences.getFlowSpeed()
            )

            sensorManager = DeviceOrientationSensorManager(this@WaterWallpaperService) { gx, gy, isShake, impX, impY ->
                simulationEngine.updateGravity(gx, gy)
                if (isShake) {
                    renderer?.notifyInteraction()
                    simulationEngine.applyImpulse(impX, impY)
                }
            }

            renderer = WaterGlRenderer(preferences, simulationEngine)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisibleState = visible
            if (visible) {
                // Update display rotation
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                sensorManager.displayRotation = windowManager.defaultDisplay.rotation
                sensorManager.start()

                // If surface is already ready, start rendering
                if (surfaceHolder?.surface?.isValid == true && surfaceWidth > 0 && surfaceHeight > 0) {
                    renderer?.start(surfaceHolder.surface, surfaceWidth, surfaceHeight)
                }
            } else {
                // Screen turned off or app covered: pause sensors and render thread immediately
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
            super.onTouchEvent(event)
            if (event == null || !preferences.isTouchEnabled()) return

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    renderer?.notifyInteraction()
                    simulationEngine.addTouchDisturbance(event.x, event.y, 600f)
                }
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            simulationEngine.updateSettings(
                volume = preferences.getWaterVolume(),
                sensitivity = preferences.getPhysicsSensitivity(),
                shakeSens = preferences.getShakeSensitivity(),
                speed = preferences.getFlowSpeed(),
                batterySaver = preferences.isBatterySaverMode()
            )
        }

        override fun onDestroy() {
            super.onDestroy()
            sensorManager.stop()
            renderer?.stop()
            preferences.unregisterListener(this)
            renderer = null
        }
    }
}
