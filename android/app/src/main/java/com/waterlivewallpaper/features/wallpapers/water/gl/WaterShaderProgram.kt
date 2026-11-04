package com.waterlivewallpaper.features.wallpapers.water.gl

import android.opengl.GLES20
import android.util.Log

class WaterShaderProgram {

    companion object {
        private const val TAG = "WaterShaderProgram"
    }

    // 1. Background Shader (Aspect-ratio preserving cover mode, zero stretching)
    var bgProgramId: Int = 0
        private set
    var aBgPositionLoc: Int = -1
        private set
    var aBgTexCoordLoc: Int = -1
        private set
    var uBgTextureLoc: Int = -1
        private set
    var uBgScreenResLoc: Int = -1
        private set
    var uBgResLoc: Int = -1
        private set
    var uBgHasTextureLoc: Int = -1
        private set
    var uBgSolidColorLoc: Int = -1
        private set

    // 2. Container Water Mesh Shader (Renders crystal-clear liquid filling container up to wave surface)
    var waterMeshProgramId: Int = 0
        private set
    var aWaterPositionLoc: Int = -1
        private set
    var aWaterDepthLoc: Int = -1
        private set
    var uWaterResolutionLoc: Int = -1
        private set
    var uWaterColorLoc: Int = -1
        private set
    var uWaterSecondaryColorLoc: Int = -1
        private set
    var uWaterGlowIntensityLoc: Int = -1
        private set

    // 3. Splash Spray Droplets Shader
    var dropletProgramId: Int = 0
        private set
    var aDropletPositionLoc: Int = -1
        private set
    var aDropletRadiusLoc: Int = -1
        private set
    var uDropletResolutionLoc: Int = -1
        private set
    var uDropletColorLoc: Int = -1
        private set

    // Legacy Density & Isosurface stubs
    var densityProgramId: Int = 0
        private set
    var aDensityPositionLoc: Int = -1
        private set
    var aDensityRadiusLoc: Int = -1
        private set
    var uDensityResolutionLoc: Int = -1
        private set
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
    var uIsoBackgroundTextureLoc: Int = -1
        private set
    var uIsoHasCustomBgLoc: Int = -1
        private set
    var uIsoScreenResLoc: Int = -1
        private set
    var uIsoBgResLoc: Int = -1
        private set

    fun initialize(): Boolean {
        // 1. Build Background Program
        val bgVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.BG_VERTEX_SHADER)
        val bgFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.BG_FRAGMENT_SHADER)
        if (bgVert != 0 && bgFrag != 0) {
            bgProgramId = GLES20.glCreateProgram()
            GLES20.glAttachShader(bgProgramId, bgVert)
            GLES20.glAttachShader(bgProgramId, bgFrag)
            GLES20.glLinkProgram(bgProgramId)

            aBgPositionLoc = GLES20.glGetAttribLocation(bgProgramId, "a_Position")
            aBgTexCoordLoc = GLES20.glGetAttribLocation(bgProgramId, "a_TexCoord")
            uBgTextureLoc = GLES20.glGetUniformLocation(bgProgramId, "u_Texture")
            uBgScreenResLoc = GLES20.glGetUniformLocation(bgProgramId, "u_ScreenResolution")
            uBgResLoc = GLES20.glGetUniformLocation(bgProgramId, "u_BgResolution")
            uBgHasTextureLoc = GLES20.glGetUniformLocation(bgProgramId, "u_HasTexture")
            uBgSolidColorLoc = GLES20.glGetUniformLocation(bgProgramId, "u_SolidColor")

            GLES20.glDeleteShader(bgVert)
            GLES20.glDeleteShader(bgFrag)
        }

        // 2. Build Container Water Mesh Program
        val waterVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.WATER_MESH_VERTEX_SHADER)
        val waterFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.WATER_MESH_FRAGMENT_SHADER)
        if (waterVert == 0 || waterFrag == 0) return false

        waterMeshProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(waterMeshProgramId, waterVert)
        GLES20.glAttachShader(waterMeshProgramId, waterFrag)
        GLES20.glLinkProgram(waterMeshProgramId)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(waterMeshProgramId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Water mesh program link failed: " + GLES20.glGetProgramInfoLog(waterMeshProgramId))
            return false
        }

        aWaterPositionLoc = GLES20.glGetAttribLocation(waterMeshProgramId, "a_Position")
        aWaterDepthLoc = GLES20.glGetAttribLocation(waterMeshProgramId, "a_Depth")
        uWaterResolutionLoc = GLES20.glGetUniformLocation(waterMeshProgramId, "u_Resolution")
        uWaterColorLoc = GLES20.glGetUniformLocation(waterMeshProgramId, "u_WaterColor")
        uWaterSecondaryColorLoc = GLES20.glGetUniformLocation(waterMeshProgramId, "u_SecondaryColor")
        uWaterGlowIntensityLoc = GLES20.glGetUniformLocation(waterMeshProgramId, "u_GlowIntensity")

        GLES20.glDeleteShader(waterVert)
        GLES20.glDeleteShader(waterFrag)

        // 3. Build Droplet Program
        val dropVert = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.DROPLET_VERTEX_SHADER)
        val dropFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.DROPLET_FRAGMENT_SHADER)
        if (dropVert != 0 && dropFrag != 0) {
            dropletProgramId = GLES20.glCreateProgram()
            GLES20.glAttachShader(dropletProgramId, dropVert)
            GLES20.glAttachShader(dropletProgramId, dropFrag)
            GLES20.glLinkProgram(dropletProgramId)

            aDropletPositionLoc = GLES20.glGetAttribLocation(dropletProgramId, "a_Position")
            aDropletRadiusLoc = GLES20.glGetAttribLocation(dropletProgramId, "a_Radius")
            uDropletResolutionLoc = GLES20.glGetUniformLocation(dropletProgramId, "u_Resolution")
            uDropletColorLoc = GLES20.glGetUniformLocation(dropletProgramId, "u_Color")

            GLES20.glDeleteShader(dropVert)
            GLES20.glDeleteShader(dropFrag)
        }

        return true
    }

    fun release() {
        if (bgProgramId != 0) {
            GLES20.glDeleteProgram(bgProgramId)
            bgProgramId = 0
        }
        if (waterMeshProgramId != 0) {
            GLES20.glDeleteProgram(waterMeshProgramId)
            waterMeshProgramId = 0
        }
        if (dropletProgramId != 0) {
            GLES20.glDeleteProgram(dropletProgramId)
            dropletProgramId = 0
        }
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
