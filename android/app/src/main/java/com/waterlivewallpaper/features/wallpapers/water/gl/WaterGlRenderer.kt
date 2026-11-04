package com.waterlivewallpaper.features.wallpapers.water.gl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences
import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Authentic Container Liquid OpenGL ES 2.0 Renderer.
 * Renders:
 * 1. Background image (aspect-ratio preserved cover mode, zero stretching) or solid background color.
 * 2. Continuous liquid body filling the container from the surface wave line down to the bottom.
 * 3. Specular surface meniscus highlight and luminous crest.
 * 4. Realistic liquid depth transparency and airborne splash droplets.
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

    // Background fullscreen quad buffer: [pos.x, pos.y, tex.u, tex.v]
    private val bgQuadBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f,  0f, 0f,
                 1f, -1f,  1f, 0f,
                -1f,  1f,  0f, 1f,
                 1f,  1f,  1f, 1f
            ))
            flip()
        }

    // Water mesh buffer: [x, y, depth] x 256 vertices
    private var meshBuffer: FloatBuffer = ByteBuffer.allocateDirect(128 * 2 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // Droplet point buffer: [x, y, radius] x 64 points
    private var dropletBuffer: FloatBuffer = ByteBuffer.allocateDirect(64 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

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
            // 2. Pace down to 15-20 FPS idle rate when water has settled at rest
            // 3. Otherwise run at selected Quality Mode target (Balanced = 30 FPS)
            val isBoosted = frameStartNs < interactionBoostUntilNs
            val isSettled = simulationEngine.isFluidSettled()

            val targetPacingFps = when {
                isBoosted -> min(60, max(baseFps, 45))
                isSettled -> if (preferences.isBatterySaverMode()) 15 else 20
                else -> baseFps
            }
            currentFrameIntervalNs = 1_000_000_000L / targetPacingFps

            val dt = ((frameStartNs - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.045f)
            lastFrameTimeNs = frameStartNs

            // 1. Advance container water simulation
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
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                try {
                    val boundsOpt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, boundsOpt)

                    val reqW = if (width > 0) width else 1080
                    val reqH = if (height > 0) height else 1920
                    var inSample = 1
                    while ((boundsOpt.outWidth / (inSample * 2)) >= reqW && (boundsOpt.outHeight / (inSample * 2)) >= reqH) {
                        inSample *= 2
                    }

                    val decodeOpt = BitmapFactory.Options().apply {
                        inSampleSize = inSample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeFile(path, decodeOpt)
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
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
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
        val w = if (width > 0) width else 1080
        val h = if (height > 0) height else 2400
        GLES20.glViewport(0, 0, w, h)

        val bgColorInt = preferences.getCachedBackgroundColor()
        val inv255 = 1f / 255f
        val bgR = Color.red(bgColorInt) * inv255
        val bgG = Color.green(bgColorInt) * inv255
        val bgB = Color.blue(bgColorInt) * inv255

        GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // -------------------------------------------------------------
        // PASS 1: RENDER BACKGROUND (ASPECT-RATIO PRESERVED COVER MODE)
        // -------------------------------------------------------------
        updateBackgroundTexture()
        if (shaderProgram.bgProgramId != 0) {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUseProgram(shaderProgram.bgProgramId)

            val hasTex = if (bgTextureId != 0) 1 else 0
            GLES20.glUniform1i(shaderProgram.uBgHasTextureLoc, hasTex)
            GLES20.glUniform2f(shaderProgram.uBgScreenResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform2f(shaderProgram.uBgResLoc, bgWidth.toFloat(), bgHeight.toFloat())
            GLES20.glUniform4f(shaderProgram.uBgSolidColorLoc, bgR, bgG, bgB, 1.0f)

            if (hasTex == 1) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
                GLES20.glUniform1i(shaderProgram.uBgTextureLoc, 0)
            }

            bgQuadBuffer.position(0)
            GLES20.glEnableVertexAttribArray(shaderProgram.aBgPositionLoc)
            GLES20.glVertexAttribPointer(shaderProgram.aBgPositionLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, bgQuadBuffer)

            bgQuadBuffer.position(2)
            GLES20.glEnableVertexAttribArray(shaderProgram.aBgTexCoordLoc)
            GLES20.glVertexAttribPointer(shaderProgram.aBgTexCoordLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, bgQuadBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(shaderProgram.aBgPositionLoc)
            GLES20.glDisableVertexAttribArray(shaderProgram.aBgTexCoordLoc)
        }

        // -------------------------------------------------------------
        // PASS 2: RENDER CONTINUOUS CONTAINER WATER MESH
        // -------------------------------------------------------------
        if (shaderProgram.waterMeshProgramId != 0) {
            val vertCount = simulationEngine.populateMeshBuffer(meshBuffer)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            GLES20.glUseProgram(shaderProgram.waterMeshProgramId)

            val waterColorInt = preferences.getCachedWaterColor()
            val secColorInt = preferences.getCachedWaterSecondaryColor()
            val glowIntensity = preferences.getCachedGlowIntensity()

            GLES20.glUniform2f(shaderProgram.uWaterResolutionLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform4f(
                shaderProgram.uWaterColorLoc,
                Color.red(waterColorInt) * inv255,
                Color.green(waterColorInt) * inv255,
                Color.blue(waterColorInt) * inv255,
                1.0f
            )
            GLES20.glUniform4f(
                shaderProgram.uWaterSecondaryColorLoc,
                Color.red(secColorInt) * inv255,
                Color.green(secColorInt) * inv255,
                Color.blue(secColorInt) * inv255,
                1.0f
            )
            GLES20.glUniform1f(shaderProgram.uWaterGlowIntensityLoc, glowIntensity)

            // Stride: 3 floats (x, y, depth)
            val stride = 3 * 4

            meshBuffer.position(0)
            GLES20.glEnableVertexAttribArray(shaderProgram.aWaterPositionLoc)
            GLES20.glVertexAttribPointer(shaderProgram.aWaterPositionLoc, 2, GLES20.GL_FLOAT, false, stride, meshBuffer)

            meshBuffer.position(2)
            GLES20.glEnableVertexAttribArray(shaderProgram.aWaterDepthLoc)
            GLES20.glVertexAttribPointer(shaderProgram.aWaterDepthLoc, 1, GLES20.GL_FLOAT, false, stride, meshBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertCount)

            GLES20.glDisableVertexAttribArray(shaderProgram.aWaterPositionLoc)
            GLES20.glDisableVertexAttribArray(shaderProgram.aWaterDepthLoc)

            // -------------------------------------------------------------
            // PASS 3: RENDER SPLASH SPRAY DROPLETS (IF ANY)
            // -------------------------------------------------------------
            val droplets = simulationEngine.getDroplets()
            if (droplets.isNotEmpty() && shaderProgram.dropletProgramId != 0) {
                dropletBuffer.clear()
                var dropCount = 0
                for (d in droplets) {
                    if (dropCount >= 64) break
                    dropletBuffer.put(d.x)
                    dropletBuffer.put(d.y)
                    dropletBuffer.put(d.radius)
                    dropCount++
                }
                dropletBuffer.flip()

                if (dropCount > 0) {
                    GLES20.glUseProgram(shaderProgram.dropletProgramId)
                    GLES20.glUniform2f(shaderProgram.uDropletResolutionLoc, w.toFloat(), h.toFloat())
                    GLES20.glUniform4f(
                        shaderProgram.uDropletColorLoc,
                        Color.red(waterColorInt) * inv255,
                        Color.green(waterColorInt) * inv255,
                        Color.blue(waterColorInt) * inv255,
                        1.0f
                    )

                    val dropStride = 3 * 4
                    dropletBuffer.position(0)
                    GLES20.glEnableVertexAttribArray(shaderProgram.aDropletPositionLoc)
                    GLES20.glVertexAttribPointer(shaderProgram.aDropletPositionLoc, 2, GLES20.GL_FLOAT, false, dropStride, dropletBuffer)

                    dropletBuffer.position(2)
                    GLES20.glEnableVertexAttribArray(shaderProgram.aDropletRadiusLoc)
                    GLES20.glVertexAttribPointer(shaderProgram.aDropletRadiusLoc, 1, GLES20.GL_FLOAT, false, dropStride, dropletBuffer)

                    GLES20.glDrawArrays(GLES20.GL_POINTS, 0, dropCount)

                    GLES20.glDisableVertexAttribArray(shaderProgram.aDropletPositionLoc)
                    GLES20.glDisableVertexAttribArray(shaderProgram.aDropletRadiusLoc)
                }
            }

            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun initEgl(surface: Surface): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] == 0) {
            return false
        }
        val config = configs[0]

        val contextAttribs = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false

        return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
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
