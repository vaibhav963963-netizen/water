package com.waterlivewallpaper.config

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONObject

class WallpaperPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "live_waterpaper_settings"

        const val KEY_WATER_COLOR = "water_color"
        const val KEY_WATER_SECONDARY_COLOR = "water_secondary_color"
        const val KEY_BACKGROUND_TYPE = "background_type"
        const val KEY_BACKGROUND_COLOR = "background_color"
        const val KEY_WATER_VOLUME = "water_volume"
        const val KEY_PHYSICS_SENSITIVITY = "physics_sensitivity"
        const val KEY_FLOW_SPEED = "flow_speed"
        const val KEY_SHAKE_SENSITIVITY = "shake_sensitivity"
        const val KEY_GLOW_INTENSITY = "glow_intensity"
        const val KEY_QUALITY_LEVEL = "quality_level"
        const val KEY_TARGET_FPS = "target_fps"
        const val KEY_TOUCH_ENABLED = "touch_enabled"
        const val KEY_CUSTOM_IMAGE_PATH = "custom_image_path"
        const val KEY_CUSTOM_IMAGE_ENABLED = "custom_image_enabled"

        // Defaults matching original Live WaterPaper screenshots and real-world water physics
        const val DEFAULT_WATER_COLOR = "#00E5FF" // Cyan
        const val DEFAULT_WATER_SECONDARY = "#00B4D8"
        const val DEFAULT_BACKGROUND_TYPE = "light"
        const val DEFAULT_BACKGROUND_COLOR = "#F4F7F9"
        const val DEFAULT_WATER_VOLUME = 0.38f
        const val DEFAULT_PHYSICS_SENSITIVITY = 1.0f
        const val DEFAULT_FLOW_SPEED = 2.0f // Real-time snappy fluid motion (no slow-mo)
        const val DEFAULT_SHAKE_SENSITIVITY = 1.2f
        const val DEFAULT_GLOW_INTENSITY = 0.65f
        const val DEFAULT_QUALITY_LEVEL = "balanced"
        const val DEFAULT_TARGET_FPS = 60
        const val DEFAULT_TOUCH_ENABLED = true
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getWaterColor(): Int {
        val hex = prefs.getString(KEY_WATER_COLOR, DEFAULT_WATER_COLOR) ?: DEFAULT_WATER_COLOR
        return try { Color.parseColor(hex) } catch (e: Exception) { Color.parseColor(DEFAULT_WATER_COLOR) }
    }

    fun getWaterColorHex(): String = prefs.getString(KEY_WATER_COLOR, DEFAULT_WATER_COLOR) ?: DEFAULT_WATER_COLOR

    fun getWaterSecondaryColor(): Int {
        val hex = prefs.getString(KEY_WATER_SECONDARY_COLOR, DEFAULT_WATER_SECONDARY) ?: DEFAULT_WATER_SECONDARY
        return try { Color.parseColor(hex) } catch (e: Exception) { Color.parseColor(DEFAULT_WATER_SECONDARY) }
    }

    fun getBackgroundType(): String = prefs.getString(KEY_BACKGROUND_TYPE, DEFAULT_BACKGROUND_TYPE) ?: DEFAULT_BACKGROUND_TYPE

    fun getBackgroundColor(): Int {
        val type = getBackgroundType()
        if (type == "dark") return Color.parseColor("#0F172A")
        val hex = prefs.getString(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR) ?: DEFAULT_BACKGROUND_COLOR
        return try { Color.parseColor(hex) } catch (e: Exception) { Color.parseColor(DEFAULT_BACKGROUND_COLOR) }
    }

    fun getBackgroundColorHex(): String = prefs.getString(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR) ?: DEFAULT_BACKGROUND_COLOR

    fun getWaterVolume(): Float = prefs.getFloat(KEY_WATER_VOLUME, DEFAULT_WATER_VOLUME)

    fun getPhysicsSensitivity(): Float = prefs.getFloat(KEY_PHYSICS_SENSITIVITY, DEFAULT_PHYSICS_SENSITIVITY)

    fun getFlowSpeed(): Float = prefs.getFloat(KEY_FLOW_SPEED, DEFAULT_FLOW_SPEED)

    fun getShakeSensitivity(): Float = prefs.getFloat(KEY_SHAKE_SENSITIVITY, DEFAULT_SHAKE_SENSITIVITY)

    fun getGlowIntensity(): Float = prefs.getFloat(KEY_GLOW_INTENSITY, DEFAULT_GLOW_INTENSITY)

    fun getQualityLevel(): String = prefs.getString(KEY_QUALITY_LEVEL, DEFAULT_QUALITY_LEVEL) ?: DEFAULT_QUALITY_LEVEL

    fun getTargetFps(): Int = prefs.getInt(KEY_TARGET_FPS, DEFAULT_TARGET_FPS)

    fun isTouchEnabled(): Boolean = prefs.getBoolean(KEY_TOUCH_ENABLED, DEFAULT_TOUCH_ENABLED)

    fun getCustomImagePath(): String? {
        val enabled = prefs.getBoolean(KEY_CUSTOM_IMAGE_ENABLED, false)
        if (!enabled) return null
        val path = prefs.getString(KEY_CUSTOM_IMAGE_PATH, null)
        return if (path != null && java.io.File(path).exists()) path else null
    }

    fun setCustomImagePath(path: String?) {
        val editor = prefs.edit()
        if (path != null) {
            editor.putString(KEY_CUSTOM_IMAGE_PATH, path)
            editor.putBoolean(KEY_CUSTOM_IMAGE_ENABLED, true)
            editor.putString(KEY_BACKGROUND_TYPE, "custom_image")
        } else {
            editor.remove(KEY_CUSTOM_IMAGE_PATH)
            editor.putBoolean(KEY_CUSTOM_IMAGE_ENABLED, false)
            editor.putString(KEY_BACKGROUND_TYPE, DEFAULT_BACKGROUND_TYPE)
        }
        editor.apply()
    }

    fun saveFromJson(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            val editor = prefs.edit()

            if (json.has("waterColorHex")) editor.putString(KEY_WATER_COLOR, json.getString("waterColorHex"))
            if (json.has("waterColorSecondaryHex")) editor.putString(KEY_WATER_SECONDARY_COLOR, json.getString("waterColorSecondaryHex"))
            if (json.has("backgroundType")) editor.putString(KEY_BACKGROUND_TYPE, json.getString("backgroundType"))
            if (json.has("backgroundColorHex")) editor.putString(KEY_BACKGROUND_COLOR, json.getString("backgroundColorHex"))
            if (json.has("waterVolume")) editor.putFloat(KEY_WATER_VOLUME, json.getDouble("waterVolume").toFloat())
            if (json.has("physicsSensitivity")) editor.putFloat(KEY_PHYSICS_SENSITIVITY, json.getDouble("physicsSensitivity").toFloat())
            if (json.has("flowSpeed")) editor.putFloat(KEY_FLOW_SPEED, json.getDouble("flowSpeed").toFloat())
            if (json.has("shakeSensitivity")) editor.putFloat(KEY_SHAKE_SENSITIVITY, json.getDouble("shakeSensitivity").toFloat())
            if (json.has("glowIntensity")) editor.putFloat(KEY_GLOW_INTENSITY, json.getDouble("glowIntensity").toFloat())
            if (json.has("qualityLevel")) editor.putString(KEY_QUALITY_LEVEL, json.getString("qualityLevel"))
            if (json.has("targetFps")) editor.putInt(KEY_TARGET_FPS, json.getInt("targetFps"))
            if (json.has("touchInteractionsEnabled")) editor.putBoolean(KEY_TOUCH_ENABLED, json.getBoolean("touchInteractionsEnabled"))
            if (json.has("customImagePath")) {
                val path = if (json.isNull("customImagePath")) null else json.getString("customImagePath")
                if (path != null) {
                    editor.putString(KEY_CUSTOM_IMAGE_PATH, path)
                    editor.putBoolean(KEY_CUSTOM_IMAGE_ENABLED, true)
                } else {
                    editor.remove(KEY_CUSTOM_IMAGE_PATH)
                    editor.putBoolean(KEY_CUSTOM_IMAGE_ENABLED, false)
                }
            }
            if (json.has("backgroundImageUri")) {
                val uri = if (json.isNull("backgroundImageUri")) null else json.getString("backgroundImageUri")
                if (uri != null) {
                    editor.putString(KEY_CUSTOM_IMAGE_PATH, uri)
                    editor.putBoolean(KEY_CUSTOM_IMAGE_ENABLED, true)
                } else {
                    editor.remove(KEY_CUSTOM_IMAGE_PATH)
                    editor.putBoolean(KEY_CUSTOM_IMAGE_ENABLED, false)
                }
            }

            editor.apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("waterColorHex", getWaterColorHex())
        json.put("waterColorSecondaryHex", prefs.getString(KEY_WATER_SECONDARY_COLOR, DEFAULT_WATER_SECONDARY))
        json.put("backgroundType", getBackgroundType())
        json.put("backgroundColorHex", getBackgroundColorHex())
        json.put("customImagePath", getCustomImagePath())
        json.put("backgroundImageUri", getCustomImagePath())
        json.put("waterVolume", getWaterVolume().toDouble())
        json.put("physicsSensitivity", getPhysicsSensitivity().toDouble())
        json.put("flowSpeed", getFlowSpeed().toDouble())
        json.put("shakeSensitivity", getShakeSensitivity().toDouble())
        json.put("glowIntensity", getGlowIntensity().toDouble())
        json.put("qualityLevel", getQualityLevel())
        json.put("targetFps", getTargetFps())
        json.put("touchInteractionsEnabled", isTouchEnabled())
        return json.toString()
    }

    fun resetToDefaults(): String {
        val editor = prefs.edit()
        editor.putString(KEY_WATER_COLOR, DEFAULT_WATER_COLOR)
        editor.putString(KEY_WATER_SECONDARY_COLOR, DEFAULT_WATER_SECONDARY)
        editor.putString(KEY_BACKGROUND_TYPE, DEFAULT_BACKGROUND_TYPE)
        editor.putString(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)
        editor.remove(KEY_CUSTOM_IMAGE_PATH)
        editor.putBoolean(KEY_CUSTOM_IMAGE_ENABLED, false)
        editor.putFloat(KEY_WATER_VOLUME, DEFAULT_WATER_VOLUME)
        editor.putFloat(KEY_PHYSICS_SENSITIVITY, DEFAULT_PHYSICS_SENSITIVITY)
        editor.putFloat(KEY_FLOW_SPEED, DEFAULT_FLOW_SPEED)
        editor.putFloat(KEY_SHAKE_SENSITIVITY, DEFAULT_SHAKE_SENSITIVITY)
        editor.putFloat(KEY_GLOW_INTENSITY, DEFAULT_GLOW_INTENSITY)
        editor.putString(KEY_QUALITY_LEVEL, DEFAULT_QUALITY_LEVEL)
        editor.putInt(KEY_TARGET_FPS, DEFAULT_TARGET_FPS)
        editor.putBoolean(KEY_TOUCH_ENABLED, DEFAULT_TOUCH_ENABLED)
        editor.apply()
        return toJson()
    }
}
