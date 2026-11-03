package com.waterlivewallpaper.features.wallpapers.water.bridge

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class WaterPreviewViewManager : SimpleViewManager<WaterPreviewGlView>() {

    override fun getName(): String = "WaterPreviewGlView"

    override fun createViewInstance(reactContext: ThemedReactContext): WaterPreviewGlView {
        return WaterPreviewGlView(reactContext)
    }

    @ReactProp(name = "waterVolume")
    fun setWaterVolume(view: WaterPreviewGlView, volume: Float) {
        view.simulationEngine.initializeParticles(volume)
    }

    @ReactProp(name = "physicsSensitivity")
    fun setPhysicsSensitivity(view: WaterPreviewGlView, sensitivity: Float) {
        view.simulationEngine.updateSettings(
            volume = 0.38f,
            sensitivity = sensitivity,
            shakeSens = 1.1f
        )
    }
}
