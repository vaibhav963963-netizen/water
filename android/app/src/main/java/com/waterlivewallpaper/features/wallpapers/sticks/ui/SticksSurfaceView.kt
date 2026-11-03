package com.waterlivewallpaper.features.wallpapers.sticks.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.waterlivewallpaper.features.wallpapers.sticks.gl.SticksGlRenderer
import com.waterlivewallpaper.features.wallpapers.sticks.simulation.FloatingSticksEngine
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences
import com.waterlivewallpaper.features.wallpapers.water.sensors.DeviceOrientationSensorManager
import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine

class SticksSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    val preferences = WallpaperPreferences(context)
    val simulationEngine = FluidSimulationEngine(
        screenWidth = 1080,
        screenHeight = 2400,
        waterVolumeRatio = preferences.getWaterVolume(),
        physicsSensitivity = preferences.getPhysicsSensitivity(),
        shakeSensitivity = preferences.getShakeSensitivity(),
        flowSpeed = preferences.getFlowSpeed()
    )

    val sticksEngine = FloatingSticksEngine(
        screenWidth = 1080,
        screenHeight = 2400
    )

    private val sensorManager = DeviceOrientationSensorManager(context) { gx, gy, isShake, impX, impY ->
        simulationEngine.updateGravity(gx, gy)
        if (isShake) {
            renderer.notifyInteraction()
            simulationEngine.applyImpulse(impX, impY)
            sticksEngine.applyShakeImpulse(impX, impY)
        }
    }

    private val renderer = SticksGlRenderer(context, preferences, simulationEngine, sticksEngine)
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var isStarted = false

    init {
        holder.addCallback(this)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE && isStarted) {
            stopSensors()
        }
    }

    fun startSensors() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            sensorManager.displayRotation = wm.defaultDisplay.rotation
        }
        sensorManager.start()
        isStarted = true
        if (surfaceWidth > 0 && surfaceHeight > 0 && holder.surface.isValid) {
            renderer.start(holder.surface, surfaceWidth, surfaceHeight)
        }
    }

    fun stopSensors() {
        sensorManager.stop()
        renderer.stop()
        isStarted = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (isStarted && surfaceWidth > 0 && surfaceHeight > 0 && holder.surface.isValid) {
            renderer.start(holder.surface, surfaceWidth, surfaceHeight)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        simulationEngine.setDimensions(width, height)
        sticksEngine.setDimensions(width, height)
        renderer.onResize(width, height)
        if (isStarted && holder.surface.isValid) {
            renderer.start(holder.surface, width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderer.stop()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && preferences.isTouchEnabled()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    renderer.notifyInteraction()
                    simulationEngine.addTouchDisturbance(event.x, event.y, 600f)
                    sticksEngine.addTouchDisturbance(event.x, event.y, 500f)
                }
            }
        }
        return true
    }
}
