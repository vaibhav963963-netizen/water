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

    // FBO for density splatting
    private var fboId = 0
    private var fboTextureId = 0
    private var fboWidth = 0
    private var fboHeight = 0

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
        // Rebuild FBO on resize
        deleteFbo()
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

        initFbo(width, height)

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

        deleteFbo()
        deleteBgTexture()
        currentBgPath = null
        shaderProgram.release()
        releaseEgl()
    }

    private fun initFbo(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        deleteFbo()

        // Half-resolution FBO provides natural anti-aliasing and ultra-fast 120 FPS performance
        fboWidth = (w / 2).coerceAtLeast(1)
        fboHeight = (h / 2).coerceAtLeast(1)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        fboTextureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            fboWidth, fboHeight, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val framebuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffers, 0)
        fboId = framebuffers[0]

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, fboTextureId, 0
        )

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Framebuffer incomplete, status: $status")
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun deleteFbo() {
        if (fboId != 0) {
            val fbos = intArrayOf(fboId)
            GLES20.glDeleteFramebuffers(1, fbos, 0)
            fboId = 0
        }
        if (fboTextureId != 0) {
            val textures = intArrayOf(fboTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
            fboTextureId = 0
        }
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
        if (fboId == 0) {
            initFbo(width, height)
        }

        val count = simulationEngine.populateRenderBuffer()
        val buffer = simulationEngine.getRenderBuffer()

        // -------------------------------------------------------------
        // PASS 1: Splat smooth particle densities additively into FBO
        // -------------------------------------------------------------
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)

        GLES20.glUseProgram(shaderProgram.densityProgramId)
        GLES20.glUniform2f(shaderProgram.uDensityResolutionLoc, width.toFloat(), height.toFloat())

        val stride = 4 * 4 // [x, y, radius, isDroplet]

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(shaderProgram.aDensityPositionLoc)
        GLES20.glVertexAttribPointer(shaderProgram.aDensityPositionLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        buffer.position(2)
        GLES20.glEnableVertexAttribArray(shaderProgram.aDensityRadiusLoc)
        GLES20.glVertexAttribPointer(shaderProgram.aDensityRadiusLoc, 1, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        GLES20.glDisableVertexAttribArray(shaderProgram.aDensityPositionLoc)
        GLES20.glDisableVertexAttribArray(shaderProgram.aDensityRadiusLoc)

        // -------------------------------------------------------------
        // PASS 2: Fullscreen Isosurface & Luminous Glow reconstruction
        // -------------------------------------------------------------
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glUseProgram(shaderProgram.isoProgramId)

        // Bind density texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glUniform1i(shaderProgram.uIsoDensityTextureLoc, 0)

        // Custom Background Image support (uses cached path, zero per-frame file checks)
        updateBackgroundTexture()
        if (bgTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glUniform1i(shaderProgram.uIsoBackgroundTextureLoc, 1)
            GLES20.glUniform1i(shaderProgram.uIsoHasCustomBgLoc, 1)
            GLES20.glUniform2f(shaderProgram.uIsoScreenResLoc, width.toFloat(), height.toFloat())
            GLES20.glUniform2f(shaderProgram.uIsoBgResLoc, bgWidth.toFloat(), bgHeight.toFloat())
        } else {
            GLES20.glUniform1i(shaderProgram.uIsoHasCustomBgLoc, 0)
        }

        // Colors (zero SharedPreferences lookups or hex parsing inside render loop)
        val waterColorInt = preferences.getCachedWaterColor()
        val secColorInt = preferences.getCachedWaterSecondaryColor()
        val bgColorInt = preferences.getCachedBackgroundColor()
        val glowIntensity = preferences.getCachedGlowIntensity()

        val inv255 = 1f / 255f
        GLES20.glUniform4f(
            shaderProgram.uIsoWaterColorLoc,
            Color.red(waterColorInt) * inv255,
            Color.green(waterColorInt) * inv255,
            Color.blue(waterColorInt) * inv255,
            1.0f
        )
        GLES20.glUniform4f(
            shaderProgram.uIsoSecondaryColorLoc,
            Color.red(secColorInt) * inv255,
            Color.green(secColorInt) * inv255,
            Color.blue(secColorInt) * inv255,
            1.0f
        )
        GLES20.glUniform4f(
            shaderProgram.uIsoBackgroundColorLoc,
            Color.red(bgColorInt) * inv255,
            Color.green(bgColorInt) * inv255,
            Color.blue(bgColorInt) * inv255,
            1.0f
        )
        GLES20.glUniform1f(shaderProgram.uIsoGlowIntensityLoc, glowIntensity)

        // Draw quad
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(shaderProgram.aIsoQuadPositionLoc)
        GLES20.glVertexAttribPointer(shaderProgram.aIsoQuadPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(shaderProgram.aIsoQuadPositionLoc)
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
