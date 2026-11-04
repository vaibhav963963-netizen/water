package com.waterlivewallpaper.features.wallpapers.water.gl

import android.opengl.GLES20
import android.util.Log

class WaterShaderProgram {

    companion object {
        private const val TAG = "WaterShaderProgram"
    }

    // 1. Background Program
    var bgProgramId: Int = 0
        private set
    var aBgQuadPositionLoc: Int = -1
        private set
    var uBgTextureLoc: Int = -1
        private set
    var uBgHasCustomBgLoc: Int = -1
        private set
    var uBgColorLoc: Int = -1
        private set
    var uBgScreenResLoc: Int = -1
        private set
    var uBgResLoc: Int = -1
        private set

    // 2. Fluid Body Mesh Program
    var fluidProgramId: Int = 0
        private set
    var aFluidPositionLoc: Int = -1
        private set
    var aFluidDepthLoc: Int = -1
        private set
    var aFluidSlopeLoc: Int = -1
        private set
    var uFluidResolutionLoc: Int = -1
        private set
    var uFluidWaterColorLoc: Int = -1
        private set
    var uFluidSecondaryColorLoc: Int = -1
        private set
    var uFluidBackgroundColorLoc: Int = -1
        private set
    var uFluidGlowIntensityLoc: Int = -1
        private set
    var uFluidBackgroundTextureLoc: Int = -1
        private set
    var uFluidHasCustomBgLoc: Int = -1
        private set
    var uFluidScreenResLoc: Int = -1
        private set
    var uFluidBgResLoc: Int = -1
        private set

    // 3. Fine Spray Droplet Program
    var dropletProgramId: Int = 0
        private set
    var aDropPositionLoc: Int = -1
        private set
    var aDropRadiusLoc: Int = -1
        private set
    var uDropResolutionLoc: Int = -1
        private set
    var uDropWaterColorLoc: Int = -1
        private set

    fun initialize(): Boolean {
        // 1. Compile Background Program
        val bgVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.BG_VERTEX_SHADER)
        val bgFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.BG_FRAGMENT_SHADER)
        if (bgVert == 0 || bgFrag == 0) return false

        bgProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(bgProgramId, bgVert)
        GLES20.glAttachShader(bgProgramId, bgFrag)
        GLES20.glLinkProgram(bgProgramId)

        val linkStatusBg = IntArray(1)
        GLES20.glGetProgramiv(bgProgramId, GLES20.GL_LINK_STATUS, linkStatusBg, 0)
        if (linkStatusBg[0] == 0) {
            Log.e(TAG, "Background program link failed: " + GLES20.glGetProgramInfoLog(bgProgramId))
            return false
        }

        aBgQuadPositionLoc = GLES20.glGetAttribLocation(bgProgramId, "a_QuadPosition")
        uBgTextureLoc = GLES20.glGetUniformLocation(bgProgramId, "u_BackgroundTexture")
        uBgHasCustomBgLoc = GLES20.glGetUniformLocation(bgProgramId, "u_HasCustomBackground")
        uBgColorLoc = GLES20.glGetUniformLocation(bgProgramId, "u_BackgroundColor")
        uBgScreenResLoc = GLES20.glGetUniformLocation(bgProgramId, "u_ScreenResolution")
        uBgResLoc = GLES20.glGetUniformLocation(bgProgramId, "u_BgResolution")

        // 2. Compile Fluid Body Mesh Program
        val fluidVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.FLUID_VERTEX_SHADER)
        val fluidFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.FLUID_FRAGMENT_SHADER)
        if (fluidVert == 0 || fluidFrag == 0) return false

        fluidProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(fluidProgramId, fluidVert)
        GLES20.glAttachShader(fluidProgramId, fluidFrag)
        GLES20.glLinkProgram(fluidProgramId)

        val linkStatusFluid = IntArray(1)
        GLES20.glGetProgramiv(fluidProgramId, GLES20.GL_LINK_STATUS, linkStatusFluid, 0)
        if (linkStatusFluid[0] == 0) {
            Log.e(TAG, "Fluid program link failed: " + GLES20.glGetProgramInfoLog(fluidProgramId))
            return false
        }

        aFluidPositionLoc = GLES20.glGetAttribLocation(fluidProgramId, "a_Position")
        aFluidDepthLoc = GLES20.glGetAttribLocation(fluidProgramId, "a_Depth")
        aFluidSlopeLoc = GLES20.glGetAttribLocation(fluidProgramId, "a_Slope")
        uFluidResolutionLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_Resolution")
        uFluidWaterColorLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_WaterColor")
        uFluidSecondaryColorLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_SecondaryColor")
        uFluidBackgroundColorLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_BackgroundColor")
        uFluidGlowIntensityLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_GlowIntensity")
        uFluidBackgroundTextureLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_BackgroundTexture")
        uFluidHasCustomBgLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_HasCustomBackground")
        uFluidScreenResLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_ScreenResolution")
        uFluidBgResLoc = GLES20.glGetUniformLocation(fluidProgramId, "u_BgResolution")

        // 3. Compile Spray Droplet Program
        val dropVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.DROPLET_VERTEX_SHADER)
        val dropFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.DROPLET_FRAGMENT_SHADER)
        if (dropVert == 0 || dropFrag == 0) return false

        dropletProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(dropletProgramId, dropVert)
        GLES20.glAttachShader(dropletProgramId, dropFrag)
        GLES20.glLinkProgram(dropletProgramId)

        val linkStatusDrop = IntArray(1)
        GLES20.glGetProgramiv(dropletProgramId, GLES20.GL_LINK_STATUS, linkStatusDrop, 0)
        if (linkStatusDrop[0] == 0) {
            Log.e(TAG, "Droplet program link failed: " + GLES20.glGetProgramInfoLog(dropletProgramId))
            return false
        }

        aDropPositionLoc = GLES20.glGetAttribLocation(dropletProgramId, "a_Position")
        aDropRadiusLoc = GLES20.glGetAttribLocation(dropletProgramId, "a_Radius")
        uDropResolutionLoc = GLES20.glGetUniformLocation(dropletProgramId, "u_Resolution")
        uDropWaterColorLoc = GLES20.glGetUniformLocation(dropletProgramId, "u_WaterColor")

        return true
    }

    fun release() {
        if (bgProgramId != 0) {
            GLES20.glDeleteProgram(bgProgramId)
            bgProgramId = 0
        }
        if (fluidProgramId != 0) {
            GLES20.glDeleteProgram(fluidProgramId)
            fluidProgramId = 0
        }
        if (dropletProgramId != 0) {
            GLES20.glDeleteProgram(dropletProgramId)
            dropletProgramId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shaderId = GLES20.glCreateShader(type)
        if (shaderId == 0) return 0

        GLES20.glShaderSource(shaderId, source)
        GLES20.glCompileShader(shaderId)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader $type: " + GLES20.glGetShaderInfoLog(shaderId))
            GLES20.glDeleteShader(shaderId)
            return 0
        }
        return shaderId
    }
}
