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
import com.waterlivewallpaper.features.wallpapers.water.gl.WaterShaderProgram
import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenGL ES 2.0 Renderer for the Floating Sticks Live Wallpaper.
 * Renders:
 * 1. Background (custom image or dynamic gradient)
 * 2. Fluid water metaballs isosurface
 * 3. 3D floating sticks/twigs with rotation, texture mapping, and underwater immersion
 * 4. Splashing water droplets
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
            uniform float uSubmerged;
            uniform vec3 uWaterColor;
            varying vec2 vTexCoord;
            void main() {
                vec4 col = texture2D(uTexture, vTexCoord);
                if (col.a < 0.05) discard;
                // Underwater depth tinting: submerged portions darken slightly and pick up fluid hue
                vec3 deepWater = mix(col.rgb, uWaterColor * 0.70 + col.rgb * 0.30, 0.35);
                vec3 finalRgb = mix(col.rgb, deepWater, uSubmerged);
                gl_FragColor = vec4(finalRgb, col.a);
            }
        """
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
        interactionBoostUntilNs = System.nanoTime() + 1_500_000_000L
    }

    // FBO for fluid density splatting
    private var fboId = 0
    private var fboTextureId = 0
    private var fboWidth = 0
    private var fboHeight = 0

    // Custom background texture
    private var bgTextureId = 0
    private var currentBgPath: String? = null

    // Stick shader program handles
    private var stickProgramId = 0
    private var uStickMvpMatrixLoc = 0
    private var uStickTextureLoc = 0
    private var uStickSubmergedLoc = 0
    private var uStickWaterColorLoc = 0
    private var aStickPositionLoc = 0
    private var aStickTexCoordLoc = 0

    // Stick textures array (6 unique 3D twigs)
    private val stickTextureIds = IntArray(6)

    // Matrices
    private val projMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Fullscreen quad buffer [-1,-1, 1,-1, -1,1, 1,1]
    private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
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
            Log.e(TAG, "Failed to initialize Water Shader Program")
            releaseEgl()
            return
        }

        if (!initStickShader()) {
            Log.e(TAG, "Failed to initialize Stick Shader Program")
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
            } else if (preferences.isBatterySaver() && simulationEngine.isFluidCalm()) {
                1_000_000_000L / 20L
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
        deleteFbo()
        deleteBgTexture()
        shaderProgram.release()
        releaseEgl()
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
        uStickSubmergedLoc = GLES20.glGetUniformLocation(stickProgramId, "uSubmerged")
        uStickWaterColorLoc = GLES20.glGetUniformLocation(stickProgramId, "uWaterColor")
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

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader $type: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
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

    private fun initFbo(w: Int, h: Int) {
        val scale = if (preferences.isBatterySaver()) 0.40f else 0.50f
        fboWidth = max(64, (w * scale).toInt())
        fboHeight = max(64, (h * scale).toInt())

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

        // Custom Background Image support
        updateBackgroundTexture()
        if (bgTextureId != 0) {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUseProgram(shaderProgram.isoProgramId)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glUniform1i(shaderProgram.uIsoBackgroundTextureLoc, 1)
            GLES20.glUniform1i(shaderProgram.uIsoHasCustomBgLoc, 1)

            quadBuffer.position(0)
            GLES20.glEnableVertexAttribArray(shaderProgram.aIsoQuadPositionLoc)
            GLES20.glVertexAttribPointer(shaderProgram.aIsoQuadPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(shaderProgram.aIsoQuadPositionLoc)
        }

        // -------------------------------------------------------------
        // RENDER FLOATING 3D WOODEN STICKS (WITHOUT WATER AT BACKGROUND)
        // -------------------------------------------------------------
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(stickProgramId)

        // Setup 2D Orthographic Projection Matrix: (0, 0) at top-left to (width, height) at bottom-right
        Matrix.orthoM(projMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
        GLES20.glUniform3f(uStickWaterColorLoc, bgR * 0.9f, bgG * 0.9f, bgB * 0.9f)

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
            // Draw clean 3D sticks directly (no background shadow)
            for (stick in stickList) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, stick.x, stick.y, 0f)
                val angleDeg = stick.angle * (180f / Math.PI.toFloat())
                Matrix.rotateM(modelMatrix, 0, angleDeg, 0f, 0f, 1f)
                val thickY = stick.radius * 2.6f
                Matrix.scaleM(modelMatrix, 0, stick.length, thickY, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, modelMatrix, 0)

                GLES20.glUniformMatrix4fv(uStickMvpMatrixLoc, 1, false, mvpMatrix, 0)
                GLES20.glUniform1f(uStickSubmergedLoc, 0f)

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
