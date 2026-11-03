package com.waterlivewallpaper.features.wallpapers.sticks.gl

import android.content.Context
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
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.waterlivewallpaper.R
import com.waterlivewallpaper.features.wallpapers.sticks.simulation.FloatingSticksEngine
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences
import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine
import kotlin.math.max
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-Performance OpenGL ES 2.0 Renderer for Floating Sticks Live Wallpaper.
 * Renders:
 * 1. Background (aspect-ratio preserved cover image or smooth canvas)
 * 2. 3D Floating Wooden Sticks with orientation, scaling, rotation, and physical stacking
 */
class SticksGlRenderer(
    private val context: Context,
    private val preferences: WallpaperPreferences,
    private val simulationEngine: FluidSimulationEngine,
    private val sticksEngine: FloatingSticksEngine
) {
    companion object {
        private const val TAG = "SticksGlRenderer"
        private const val EGL_OPENGL_ES2_BIT = 4
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

        // Aspect-ratio preserving background shader (Cover Mode / No Stretching)
        private const val BG_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val BG_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uScreenResolution;
            uniform vec2 uBgResolution;
            varying vec2 vTexCoord;
            void main() {
                vec2 rawUv = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
                float screenAR = uScreenResolution.x / max(uScreenResolution.y, 1.0);
                float bgAR = uBgResolution.x / max(uBgResolution.y, 1.0);
                float r = screenAR / bgAR;
                vec2 bgUv;
                if (r > 1.0) {
                    bgUv = vec2(rawUv.x, 0.5 + (rawUv.y - 0.5) / r);
                } else {
                    bgUv = vec2(0.5 + (rawUv.x - 0.5) * r, rawUv.y);
                }
                gl_FragColor = texture2D(uTexture, bgUv);
            }
        """

        // 3D Wooden Stick Shader
        private const val STICK_VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val STICK_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                vec4 col = texture2D(uTexture, vTexCoord);
                if (col.a < 0.05) discard;
                gl_FragColor = col;
            }
        """
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private val isRunning = AtomicBoolean(false)
    private var renderThread: Thread? = null

    private var width = 0
    private var height = 0
    private var targetFps = 30
    private var frameIntervalNs = 1_000_000_000L / targetFps

    @Volatile private var interactionBoostUntilNs: Long = 0L

    fun notifyInteraction() {
        interactionBoostUntilNs = System.nanoTime() + 1_500_000_000L
    }

    // Custom background texture & state
    private var bgTextureId = 0
    private var bgWidth = 0
    private var bgHeight = 0
    private var currentBgPath: String? = null

    // Background Shader Handles
    private var bgProgramId = 0
    private var uBgTextureLoc = 0
    private var uBgScreenResLoc = 0
    private var uBgResLoc = 0
    private var aBgPositionLoc = 0
    private var aBgTexCoordLoc = 0

    // Stick Shader Program Handles
    private var stickProgramId = 0
    private var uStickMvpMatrixLoc = 0
    private var uStickTextureLoc = 0
    private var aStickPositionLoc = 0
    private var aStickTexCoordLoc = 0

    // Stick textures array (6 unique 3D twigs)
    private val stickTextureIds = IntArray(6)

    // Matrices
    private val projMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Fullscreen quad with UVs for background rendering
    private val bgQuadBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                // x, y, u, v
                -1f, -1f, 0f, 1f,
                 1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,
                 1f,  1f, 1f, 0f
            ))
            flip()
        }

    // Stick unit quad buffer: [-0.5, -0.5, 0.5, -0.5, -0.5, 0.5, 0.5, 0.5]
    // with tex coords: [0, 1, 1, 1, 0, 0, 1, 0]
    private val stickQuadBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                // x, y, u, v
                -0.5f, -0.5f, 0f, 1f,
                 0.5f, -0.5f, 1f, 1f,
                -0.5f,  0.5f, 0f, 0f,
                 0.5f,  0.5f, 1f, 0f
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
        }, "SticksGlRenderThread").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun onResize(w: Int, h: Int) {
        this.width = w
        this.height = h
        simulationEngine.setDimensions(w, h)
        sticksEngine.setDimensions(w, h)
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

        if (!initBgShader()) {
            Log.e(TAG, "Failed to initialize Background Shader Program")
            releaseEgl()
            return
        }

        if (!initStickShader()) {
            Log.e(TAG, "Failed to initialize Stick Shader Program")
            deleteBgShader()
            releaseEgl()
            return
        }

        loadStickTextures()

        var lastFrameTimeNs = System.nanoTime()

        while (isRunning.get() && !Thread.currentThread().isInterrupted) {
            val frameStartNs = System.nanoTime()
            val dt = ((frameStartNs - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0.008f, 0.05f)
            lastFrameTimeNs = frameStartNs

            // Physics step
            simulationEngine.step(dt)
            sticksEngine.step(dt, simulationEngine)

            // Render
            renderFrame()
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            // Dynamic Frame Pacing
            val isInteracting = frameStartNs < interactionBoostUntilNs
            val currentFrameIntervalNs = if (isInteracting) {
                1_000_000_000L / 60L
            } else if (preferences.isBatterySaver()) {
                1_000_000_000L / 24L
            } else {
                frameIntervalNs
            }

            val frameTimeNs = System.nanoTime() - frameStartNs
            val sleepNs = currentFrameIntervalNs - frameTimeNs
            if (sleepNs > 1_000_000L) {
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        deleteStickTextures()
        deleteStickShader()
        deleteBgShader()
        deleteBgTexture()
        releaseEgl()
    }

    private fun initBgShader(): Boolean {
        val vertShader = loadShader(GLES20.GL_VERTEX_SHADER, BG_VERTEX_SHADER)
        val fragShader = loadShader(GLES20.GL_FRAGMENT_SHADER, BG_FRAGMENT_SHADER)
        if (vertShader == 0 || fragShader == 0) return false

        bgProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(bgProgramId, vertShader)
        GLES20.glAttachShader(bgProgramId, fragShader)
        GLES20.glLinkProgram(bgProgramId)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(bgProgramId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Error linking bg program: " + GLES20.glGetProgramInfoLog(bgProgramId))
            GLES20.glDeleteProgram(bgProgramId)
            bgProgramId = 0
            return false
        }

        uBgTextureLoc = GLES20.glGetUniformLocation(bgProgramId, "uTexture")
        uBgScreenResLoc = GLES20.glGetUniformLocation(bgProgramId, "uScreenResolution")
        uBgResLoc = GLES20.glGetUniformLocation(bgProgramId, "uBgResolution")
        aBgPositionLoc = GLES20.glGetAttribLocation(bgProgramId, "aPosition")
        aBgTexCoordLoc = GLES20.glGetAttribLocation(bgProgramId, "aTexCoord")

        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(fragShader)
        return true
    }

    private fun deleteBgShader() {
        if (bgProgramId != 0) {
            GLES20.glDeleteProgram(bgProgramId)
            bgProgramId = 0
        }
    }

    private fun initStickShader(): Boolean {
        val vertShader = loadShader(GLES20.GL_VERTEX_SHADER, STICK_VERTEX_SHADER)
        val fragShader = loadShader(GLES20.GL_FRAGMENT_SHADER, STICK_FRAGMENT_SHADER)
        if (vertShader == 0 || fragShader == 0) return false

        stickProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(stickProgramId, vertShader)
        GLES20.glAttachShader(stickProgramId, fragShader)
        GLES20.glLinkProgram(stickProgramId)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(stickProgramId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Error linking stick program: " + GLES20.glGetProgramInfoLog(stickProgramId))
            GLES20.glDeleteProgram(stickProgramId)
            stickProgramId = 0
            return false
        }

        uStickMvpMatrixLoc = GLES20.glGetUniformLocation(stickProgramId, "uMvpMatrix")
        uStickTextureLoc = GLES20.glGetUniformLocation(stickProgramId, "uTexture")
        aStickPositionLoc = GLES20.glGetAttribLocation(stickProgramId, "aPosition")
        aStickTexCoordLoc = GLES20.glGetAttribLocation(stickProgramId, "aTexCoord")

        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(fragShader)
        return true
    }

    private fun deleteStickShader() {
        if (stickProgramId != 0) {
            GLES20.glDeleteProgram(stickProgramId)
            stickProgramId = 0
        }
    }

    private fun loadStickTextures() {
        val resIds = intArrayOf(
            R.drawable.stick_twig_1,
            R.drawable.stick_twig_2,
            R.drawable.stick_twig_3,
            R.drawable.stick_twig_4,
            R.drawable.stick_twig_5,
            R.drawable.stick_twig_6
        )

        GLES20.glGenTextures(6, stickTextureIds, 0)

        for (i in 0 until 6) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickTextureIds[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeResource(context.resources, resIds[i], opts)
            if (bmp != null) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                bmp.recycle()
            }
        }
    }

    private fun deleteStickTextures() {
        GLES20.glDeleteTextures(6, stickTextureIds, 0)
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
        GLES20.glViewport(0, 0, width, height)

        val bgColorInt = preferences.getCachedBackgroundColor()
        val inv255 = 1f / 255f
        val bgR = Color.red(bgColorInt) * inv255
        val bgG = Color.green(bgColorInt) * inv255
        val bgB = Color.blue(bgColorInt) * inv255

        GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // -------------------------------------------------------------
        // RENDER ASPECT-RATIO PRESERVED BACKGROUND IMAGE (COVER MODE)
        // -------------------------------------------------------------
        updateBackgroundTexture()
        if (bgTextureId != 0 && bgProgramId != 0) {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUseProgram(bgProgramId)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glUniform1i(uBgTextureLoc, 0)
            GLES20.glUniform2f(uBgScreenResLoc, width.toFloat(), height.toFloat())
            GLES20.glUniform2f(uBgResLoc, bgWidth.toFloat(), bgHeight.toFloat())

            bgQuadBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aBgPositionLoc)
            GLES20.glVertexAttribPointer(aBgPositionLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, bgQuadBuffer)

            bgQuadBuffer.position(2)
            GLES20.glEnableVertexAttribArray(aBgTexCoordLoc)
            GLES20.glVertexAttribPointer(aBgTexCoordLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, bgQuadBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aBgPositionLoc)
            GLES20.glDisableVertexAttribArray(aBgTexCoordLoc)
        }

        // -------------------------------------------------------------
        // RENDER 3D WOODEN STICKS (CLEAN, NO SHADOW PILLS, NO WATER)
        // -------------------------------------------------------------
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(stickProgramId)

        // Setup 2D Orthographic Projection Matrix: (0, 0) top-left to (width, height) bottom-right
        Matrix.orthoM(projMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)

        stickQuadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aStickPositionLoc)
        GLES20.glVertexAttribPointer(aStickPositionLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, stickQuadBuffer)

        stickQuadBuffer.position(2)
        GLES20.glEnableVertexAttribArray(aStickTexCoordLoc)
        GLES20.glVertexAttribPointer(aStickTexCoordLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, stickQuadBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(uStickTextureLoc, 0)

        val stickList = sticksEngine.sticks
        synchronized(stickList) {
            for (stick in stickList) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, stick.x, stick.y, 0f)
                val angleDeg = stick.angle * (180f / Math.PI.toFloat())
                Matrix.rotateM(modelMatrix, 0, angleDeg, 0f, 0f, 1f)
                val thickY = stick.radius * 2.6f
                Matrix.scaleM(modelMatrix, 0, stick.length, thickY, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, modelMatrix, 0)

                GLES20.glUniformMatrix4fv(uStickMvpMatrixLoc, 1, false, mvpMatrix, 0)

                val texId = stickTextureIds[(stick.textureIndex - 1).coerceIn(0, 5)]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
        }

        GLES20.glDisableVertexAttribArray(aStickPositionLoc)
        GLES20.glDisableVertexAttribArray(aStickTexCoordLoc)
        GLES20.glDisable(GLES20.GL_BLEND)
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
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            return false
        }

        val contextAttribs = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )

        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
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

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compilation error: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
