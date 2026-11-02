package com.waterlivewallpaper.gl

import android.opengl.GLES20
import android.util.Log

class WaterShaderProgram {

    companion object {
        private const val TAG = "WaterShaderProgram"
    }

    // Pass 1: Density splat program
    var densityProgramId: Int = 0
        private set
    var aDensityPositionLoc: Int = -1
        private set
    var aDensityRadiusLoc: Int = -1
        private set
    var uDensityResolutionLoc: Int = -1
        private set

    // Pass 2: Isosurface threshold & glow program
    var isoProgramId: Int = 0
        private set
    var aIsoQuadPositionLoc: Int = -1
        private set
    var uIsoDensityTextureLoc: Int = -1
        private set
    var uIsoWaterColorLoc: Int = -1
        private set
    var uIsoSecondaryColorLoc: Int = -1
        private set
    var uIsoBackgroundColorLoc: Int = -1
        private set
    var uIsoGlowIntensityLoc: Int = -1
        private set

    fun initialize(): Boolean {
        // 1. Build Density Splat Program
        val densVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.DENSITY_VERTEX_SHADER)
        val densFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.DENSITY_FRAGMENT_SHADER)
        if (densVert == 0 || densFrag == 0) return false

        densityProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(densityProgramId, densVert)
        GLES20.glAttachShader(densityProgramId, densFrag)
        GLES20.glLinkProgram(densityProgramId)

        val linkStatus1 = IntArray(1)
        GLES20.glGetProgramiv(densityProgramId, GLES20.GL_LINK_STATUS, linkStatus1, 0)
        if (linkStatus1[0] == 0) {
            Log.e(TAG, "Density program link failed: " + GLES20.glGetProgramInfoLog(densityProgramId))
            return false
        }

        aDensityPositionLoc = GLES20.glGetAttribLocation(densityProgramId, "a_Position")
        aDensityRadiusLoc = GLES20.glGetAttribLocation(densityProgramId, "a_Radius")
        uDensityResolutionLoc = GLES20.glGetUniformLocation(densityProgramId, "u_Resolution")

        // 2. Build Isosurface Program
        val isoVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.ISOSURFACE_VERTEX_SHADER)
        val isoFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.ISOSURFACE_FRAGMENT_SHADER)
        if (isoVert == 0 || isoFrag == 0) return false

        isoProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(isoProgramId, isoVert)
        GLES20.glAttachShader(isoProgramId, isoFrag)
        GLES20.glLinkProgram(isoProgramId)

        val linkStatus2 = IntArray(1)
        GLES20.glGetProgramiv(isoProgramId, GLES20.GL_LINK_STATUS, linkStatus2, 0)
        if (linkStatus2[0] == 0) {
            Log.e(TAG, "Isosurface program link failed: " + GLES20.glGetProgramInfoLog(isoProgramId))
            return false
        }

        aIsoQuadPositionLoc = GLES20.glGetAttribLocation(isoProgramId, "a_QuadPosition")
        uIsoDensityTextureLoc = GLES20.glGetUniformLocation(isoProgramId, "u_DensityTexture")
        uIsoWaterColorLoc = GLES20.glGetUniformLocation(isoProgramId, "u_WaterColor")
        uIsoSecondaryColorLoc = GLES20.glGetUniformLocation(isoProgramId, "u_SecondaryColor")
        uIsoBackgroundColorLoc = GLES20.glGetUniformLocation(isoProgramId, "u_BackgroundColor")
        uIsoGlowIntensityLoc = GLES20.glGetUniformLocation(isoProgramId, "u_GlowIntensity")

        return true
    }

    fun release() {
        if (densityProgramId != 0) {
            GLES20.glDeleteProgram(densityProgramId)
            densityProgramId = 0
        }
        if (isoProgramId != 0) {
            GLES20.glDeleteProgram(isoProgramId)
            isoProgramId = 0
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
