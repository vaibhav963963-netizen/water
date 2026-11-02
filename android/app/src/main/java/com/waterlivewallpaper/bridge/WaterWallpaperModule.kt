package com.waterlivewallpaper.bridge

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.waterlivewallpaper.WaterWallpaperService
import com.waterlivewallpaper.config.WallpaperPreferences
import com.waterlivewallpaper.sensors.DeviceOrientationSensorManager

class WaterWallpaperModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val preferences = WallpaperPreferences(reactContext)

    override fun getName(): String = "WaterWallpaperModule"

    @ReactMethod
    fun isWallpaperServiceSupported(promise: Promise) {
        try {
            val supported = reactContext.packageManager.hasSystemFeature("android.software.live_wallpaper")
            promise.resolve(supported)
        } catch (e: Exception) {
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun isLiveWallpaperActive(promise: Promise) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(reactContext)
            val info = wallpaperManager.wallpaperInfo
            if (info != null) {
                val currentService = info.serviceName
                val myService = WaterWallpaperService::class.java.name
                promise.resolve(currentService == myService)
            } else {
                promise.resolve(false)
            }
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun openWallpaperChooser(promise: Promise) {
        try {
            val component = ComponentName(reactContext, WaterWallpaperService::class.java)

            // Primary intent: Direct live wallpaper preview intent
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(reactContext.packageManager) != null) {
                reactContext.startActivity(intent)
                promise.resolve(true)
                return
            }

            // Fallback 1: Live wallpaper chooser list
            val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (fallbackIntent.resolveActivity(reactContext.packageManager) != null) {
                reactContext.startActivity(fallbackIntent)
                promise.resolve(true)
                return
            }

            // Fallback 2: General wallpaper picker
            val generalIntent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactContext.startActivity(generalIntent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("WALLPAPER_INTENT_ERROR", e.localizedMessage, e)
        }
    }

    @ReactMethod
    fun getSavedSettings(promise: Promise) {
        try {
            promise.resolve(preferences.toJson())
        } catch (e: Exception) {
            promise.reject("SETTINGS_READ_ERROR", e.localizedMessage, e)
        }
    }

    @ReactMethod
    fun saveSettings(settingsJson: String, promise: Promise) {
        try {
            val success = preferences.saveFromJson(settingsJson)
            promise.resolve(success)
        } catch (e: Exception) {
            promise.reject("SETTINGS_SAVE_ERROR", e.localizedMessage, e)
        }
    }

    @ReactMethod
    fun resetToDefaults(promise: Promise) {
        try {
            promise.resolve(preferences.resetToDefaults())
        } catch (e: Exception) {
            promise.reject("SETTINGS_RESET_ERROR", e.localizedMessage, e)
        }
    }

    @ReactMethod
    fun getDeviceSensorInfo(promise: Promise) {
        try {
            val sensorManager = DeviceOrientationSensorManager(reactContext) { _, _, _, _, _ -> }
            val map = Arguments.createMap()
            map.putBoolean("isWallpaperServiceSupported", true)
            map.putBoolean("isRotationVectorSupported", sensorManager.hasRotationVector())
            map.putBoolean("isAccelerometerSupported", sensorManager.hasAccelerometer())
            map.putBoolean("isGLES3Supported", true)

            val wallpaperManager = WallpaperManager.getInstance(reactContext)
            val info = wallpaperManager.wallpaperInfo
            val isActive = info?.serviceName == WaterWallpaperService::class.java.name
            map.putBoolean("isLiveWallpaperCurrentlyActive", isActive)

            map.putString("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            map.putString("androidVersion", "${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")

            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("DIAGNOSTICS_ERROR", e.localizedMessage, e)
        }
    }
}
