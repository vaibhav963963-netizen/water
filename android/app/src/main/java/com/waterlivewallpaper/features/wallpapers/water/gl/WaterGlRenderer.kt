package com.waterlivewallpaper.features.wallpapers.water.gl

import android.graphics.Color
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences
import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-High-Performance OpenGL ES 2.0 Renderer for Authentic Container Live WaterPaper.
 *
 * Renders:
 * 1. Aspect-ratio preserved background (custom wallpaper or smooth canvas).
 * 2. Translucent continuous container fluid body (triangle strip mesh with optical wave refraction,
 *    depth shading, and luminous meniscus glint).
 * 3. Realistic fine spray droplets (antialiased spherical drops with specular highlights).
 */
class WaterGlRenderer(
    private val preferences: WallpaperPreferences,
    private val simulationEngine: FluidSimulationEngine
) {
    companion object {
        private const val TAG = "WaterGlRenderer"
        private const val EGL_OPENGL_ES2_BIT = 4
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private val isRunning = AtomicBoolean(false)
    private var renderThread: Thread? = null

    private val shaderProgram = WaterShaderProgram()
    private var width = 0
    private var height = 0
    private var targetFps = 30
    private var frameIntervalNs = 1_000_000_000L / targetFps

    @Volatile private var interactionBoostUntilNs: Long = 0L

    fun notifyInteraction() {
        interactionBoostUntilNs = System.nanoTime() + 1_500_000_000L // 1.5s boost on touch/shake
    }

    // Custom background texture
    private var bgTextureId = 0
    private var bgWidth = 0
    private var bgHeight = 0
    private var currentBgPath: String? = null

    // Fullscreen quad buffer [-1,-1, 1,-1, -1,1, 1,1]
    private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            ))
            flip()
        }

    @Synchronized
    fun start(surface: Surface, w: Int, h: Int) {
        if (isRunning.get()) stop()

        this.width = w
        this.height = h
        this.targetFps = preferences.getEffectiveFps()
        this.frameIntervalNs = 1_000_000_000L / targetFps.coerceIn(20, 120)

        isRunning.set(true)
        renderThread = Thread({
            runRenderLoop(surface)
        }, "WaterGlRenderThread").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun onResize(w: Int, h: Int) {
        this.width = w
        this.height = h
        simulationEngine.setDimensions(w, h)
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        renderThread?.interrupt()
        try {
            renderThread?.join(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        renderThread = null
    }

    private fun runRenderLoop(surface: Surface) {
        if (!initEgl(surface)) {
            Log.e(TAG, "Failed to initialize EGL")
            return
        }

        if (!shaderProgram.initialize()) {
            Log.e(TAG, "Failed to initialize Shader Program")
            releaseEgl()
            return
        }

        val baseFps = preferences.getEffectiveFps()
        var currentFrameIntervalNs = 1_000_000_000L / baseFps.coerceIn(20, 120)
        var lastFrameTimeNs = System.nanoTime()

        while (isRunning.get()) {
            val frameStartNs = System.nanoTime()

            // Dynamic Frame Pacing & Quality Mode:
            // 1. Boost to 45-60 FPS for 1.5s during user touch or vigorous shake
            // 2. Pace down to 15-20 FPS idle rate when fluid has settled at rest
            // 3. Otherwise run at selected Quality Mode target (Balanced = 30 FPS)
            val isBoosted = frameStartNs < interactionBoostUntilNs
            val isSettled = simulationEngine.isFluidSettled()

            val targetPacingFps = when {
                isBoosted -> kotlin.math.min(60, kotlin.math.max(baseFps, 45))
                isSettled -> if (preferences.isBatterySaverMode()) 15 else 20
                else -> baseFps
            }
            currentFrameIntervalNs = 1_000_000_000L / targetPacingFps

            val dt = ((frameStartNs - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.050f)
            lastFrameTimeNs = frameStartNs

            // 1. Advance fluid simulation
            simulationEngine.step(dt)

            // 2. Render frame
            renderFrame()

            // 3. Swap buffers
            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                Log.w(TAG, "eglSwapBuffers failed: " + EGL14.eglGetError())
                break
            }

            // 4. Rate-limit to dynamic target FPS
            val elapsedNs = System.nanoTime() - frameStartNs
            val sleepNs = currentFrameIntervalNs - elapsedNs
            if (sleepNs > 1_000_000L) {
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        deleteBgTexture()
        currentBgPath = null
        shaderProgram.release()
        releaseEgl()
    }

    private fun updateBackgroundTexture() {
        val path = preferences.getCachedCustomImagePath()
        if (path == currentBgPath && bgTextureId != 0) return
        if (path == null && bgTextureId == 0) return

        deleteBgTexture()
        currentBgPath = path

        if (path != null) {
            val file = java.io.File(path)
            if (file.exists() && file.length() > 0) {
                try {
                    val boundsOpt = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(path, boundsOpt)

                    val reqW = if (width > 0) width else 1080
                    val reqH = if (height > 0) height else 1920
                    var inSample = 1
                    while ((boundsOpt.outWidth / (inSample * 2)) >= reqW && (boundsOpt.outHeight / (inSample * 2)) >= reqH) {
                        inSample *= 2
                    }

                    val decodeOpt = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = inSample
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(path, decodeOpt)
                    if (bitmap != null) {
                        bgWidth = bitmap.width
                        bgHeight = bitmap.height
                        val textures = IntArray(1)
                        GLES20.glGenTextures(1, textures, 0)
                        bgTextureId = textures[0]

                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load custom background texture: " + e.message)
                    deleteBgTexture()
                    currentBgPath = null
                }
            }
        }
    }

    private fun deleteBgTexture() {
        if (bgTextureId != 0) {
            val textures = intArrayOf(bgTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
            bgTextureId = 0
            bgWidth = 0
            bgHeight = 0
        }
    }

    private fun renderFrame() {
        GLES20.glViewport(0, 0, width, height)

        val waterColorInt = preferences.getCachedWaterColor()
        val secColorInt = preferences.getCachedWaterSecondaryColor()
        val bgColorInt = preferences.getCachedBackgroundColor()
        val glowIntensity = preferences.getCachedGlowIntensity()
        val inv255 = 1f / 255f

        val bgR = Color.red(bgColorInt) * inv255
        val bgG = Color.green(bgColorInt) * inv255
        val bgB = Color.blue(bgColorInt) * inv255

        GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        updateBackgroundTexture()

        // -------------------------------------------------------------
        // STEP 1: Fullscreen Background (Aspect-ratio preserved cover)
        // -------------------------------------------------------------
        GLES20.glUseProgram(shaderProgram.bgProgramId)

        if (bgTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glUniform1i(shaderProgram.uBgTextureLoc, 0)
            GLES20.glUniform1i(shaderProgram.uBgHasCustomBgLoc, 1)
            GLES20.glUniform2f(shaderProgram.uBgScreenResLoc, width.toFloat(), height.toFloat())
            GLES20.glUniform2f(shaderProgram.uBgResLoc, bgWidth.toFloat(), bgHeight.toFloat())
        } else {
            GLES20.glUniform1i(shaderProgram.uBgHasCustomBgLoc, 0)
        }

        GLES20.glUniform4f(shaderProgram.uBgColorLoc, bgR, bgG, bgB, 1.0f)

        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(shaderProgram.aBgQuadPositionLoc)
        GLES20.glVertexAttribPointer(shaderProgram.aBgQuadPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(shaderProgram.aBgQuadPositionLoc)

        // -------------------------------------------------------------
        // STEP 2: Translucent Continuous Container Fluid Body Mesh
        // -------------------------------------------------------------
        val vertexCount = simulationEngine.populateRenderBuffer()
        val meshBuffer = simulationEngine.getRenderBuffer()

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(shaderProgram.fluidProgramId)
        GLES20.glUniform2f(shaderProgram.uFluidResolutionLoc, width.toFloat(), height.toFloat())

        if (bgTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glUniform1i(shaderProgram.uFluidBackgroundTextureLoc, 0)
            GLES20.glUniform1i(shaderProgram.uFluidHasCustomBgLoc, 1)
            GLES20.glUniform2f(shaderProgram.uFluidScreenResLoc, width.toFloat(), height.toFloat())
            GLES20.glUniform2f(shaderProgram.uFluidBgResLoc, bgWidth.toFloat(), bgHeight.toFloat())
        } else {
            GLES20.glUniform1i(shaderProgram.uFluidHasCustomBgLoc, 0)
        }

        GLES20.glUniform4f(
            shaderProgram.uFluidWaterColorLoc,
            Color.red(waterColorInt) * inv255,
            Color.green(waterColorInt) * inv255,
            Color.blue(waterColorInt) * inv255,
            1.0f
        )
        GLES20.glUniform4f(
            shaderProgram.uFluidSecondaryColorLoc,
            Color.red(secColorInt) * inv255,
            Color.green(secColorInt) * inv255,
            Color.blue(secColorInt) * inv255,
            1.0f
        )
        GLES20.glUniform4f(shaderProgram.uFluidBackgroundColorLoc, bgR, bgG, bgB, 1.0f)
        GLES20.glUniform1f(shaderProgram.uFluidGlowIntensityLoc, glowIntensity)

        val stride = 4 * 4 // [x, y, depth, slope]

        meshBuffer.position(0)
        GLES20.glEnableVertexAttribArray(shaderProgram.aFluidPositionLoc)
        GLES20.glVertexAttribPointer(shaderProgram.aFluidPositionLoc, 2, GLES20.GL_FLOAT, false, stride, meshBuffer)

        meshBuffer.position(2)
        GLES20.glEnableVertexAttribArray(shaderProgram.aFluidDepthLoc)
        GLES20.glVertexAttribPointer(shaderProgram.aFluidDepthLoc, 1, GLES20.GL_FLOAT, false, stride, meshBuffer)

        meshBuffer.position(3)
        GLES20.glEnableVertexAttribArray(shaderProgram.aFluidSlopeLoc)
        GLES20.glVertexAttribPointer(shaderProgram.aFluidSlopeLoc, 1, GLES20.GL_FLOAT, false, stride, meshBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(shaderProgram.aFluidPositionLoc)
        GLES20.glDisableVertexAttribArray(shaderProgram.aFluidDepthLoc)
        GLES20.glDisableVertexAttribArray(shaderProgram.aFluidSlopeLoc)

        // -------------------------------------------------------------
        // STEP 3: Fine Spray Droplets (Antialiased spherical drops)
        // -------------------------------------------------------------
        val dropCount = simulationEngine.populateDropletBuffer()
        if (dropCount > 0) {
            val dropBuffer = simulationEngine.getDropletBuffer()

            GLES20.glUseProgram(shaderProgram.dropletProgramId)
            GLES20.glUniform2f(shaderProgram.uDropResolutionLoc, width.toFloat(), height.toFloat())
            GLES20.glUniform4f(
                shaderProgram.uDropWaterColorLoc,
                Color.red(waterColorInt) * inv255,
                Color.green(waterColorInt) * inv255,
                Color.blue(waterColorInt) * inv255,
                1.0f
            )

            dropBuffer.position(0)
            GLES20.glEnableVertexAttribArray(shaderProgram.aDropPositionLoc)
            GLES20.glVertexAttribPointer(shaderProgram.aDropPositionLoc, 2, GLES20.GL_FLOAT, false, stride, dropBuffer)

            dropBuffer.position(2)
            GLES20.glEnableVertexAttribArray(shaderProgram.aDropRadiusLoc)
            GLES20.glVertexAttribPointer(shaderProgram.aDropRadiusLoc, 1, GLES20.GL_FLOAT, false, stride, dropBuffer)

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, dropCount)

            GLES20.glDisableVertexAttribArray(shaderProgram.aDropPositionLoc)
            GLES20.glDisableVertexAttribArray(shaderProgram.aDropRadiusLoc)
        }

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun initEgl(surface: Surface): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay failed")
            return false
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize failed")
            return false
        }

        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            Log.e(TAG, "eglChooseConfig failed")
            return false
        }
        val config = configs[0]

        val contextAttribs = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed")
            return false
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed")
            return false
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            Log.e(TAG, "eglMakeCurrent failed")
            return false
        }

        return true
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
