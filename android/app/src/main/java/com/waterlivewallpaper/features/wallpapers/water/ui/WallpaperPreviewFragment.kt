package com.waterlivewallpaper.features.wallpapers.water.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R
import com.waterlivewallpaper.core.ui.AnimationUtils
import com.waterlivewallpaper.features.wallpapers.base.WallpaperRegistry
import com.waterlivewallpaper.features.wallpapers.sticks.ui.SticksSurfaceView

class WallpaperPreviewFragment : Fragment() {

    companion object {
        private const val ARG_WALLPAPER_ID = "wallpaper_id"

        fun newInstance(wallpaperId: String): WallpaperPreviewFragment {
            return WallpaperPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WALLPAPER_ID, wallpaperId)
                }
            }
        }
    }

    private var wallpaperId: String = WallpaperRegistry.WALLPAPER_ID_WATER
    private var waterSurfaceView: WaterSurfaceView? = null
    private var sticksSurfaceView: SticksSurfaceView? = null
    private var previewTopControls: View? = null
    private var previewBottomControls: View? = null
    private var isFullscreenMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallpaperId = arguments?.getString(ARG_WALLPAPER_ID) ?: WallpaperRegistry.WALLPAPER_ID_WATER
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallpaper_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity

        waterSurfaceView = view.findViewById(R.id.waterSurfaceView)
        sticksSurfaceView = view.findViewById(R.id.sticksSurfaceView)
        previewTopControls = view.findViewById(R.id.previewTopControls)
        previewBottomControls = view.findViewById(R.id.previewBottomControls)

        val isSticks = wallpaperId == WallpaperRegistry.WALLPAPER_ID_STICKS

        if (isSticks) {
            waterSurfaceView?.visibility = View.GONE
            sticksSurfaceView?.visibility = View.VISIBLE

            view.findViewById<TextView>(R.id.txtPreviewTopTitle)?.text = "Floating Sticks Water"
            view.findViewById<TextView>(R.id.txtPreviewBottomTitle)?.text = "Floating Sticks Water"
            view.findViewById<TextView>(R.id.txtPreviewBottomSubtitle)?.text =
                "Tilt to slosh & drift • Shake for splashes • Floating 3D wooden driftwood twigs"
        } else {
            waterSurfaceView?.visibility = View.VISIBLE
            sticksSurfaceView?.visibility = View.GONE

            view.findViewById<TextView>(R.id.txtPreviewTopTitle)?.text = "Liquid Fluid Wallpaper"
            view.findViewById<TextView>(R.id.txtPreviewBottomTitle)?.text = "Liquid Fluid Wallpaper"
            view.findViewById<TextView>(R.id.txtPreviewBottomSubtitle)?.text =
                "Tilt phone to slosh liquid • Shake for splashes • Tap to ripple"
        }

        view.findViewById<View>(R.id.btnPreviewBack).setOnClickListener {
            mainActivity?.onBackPressedDispatcher?.onBackPressed()
        }

        view.findViewById<ImageView>(R.id.btnToggleFullscreen).setOnClickListener {
            toggleFullscreen(!isFullscreenMode)
        }

        AnimationUtils.attachTactilePress(view.findViewById(R.id.btnPreviewCustomize)) {
            mainActivity?.navigateToWallpaperCustomize()
        }

        AnimationUtils.attachTactilePress(view.findViewById(R.id.btnPreviewApply)) {
            if (isSticks) {
                mainActivity?.openSticksWallpaperChooser()
            } else {
                mainActivity?.openWallpaperChooser()
            }
        }
    }

    private fun toggleFullscreen(enable: Boolean) {
        isFullscreenMode = enable
        if (enable) {
            previewTopControls?.visibility = View.GONE
            previewBottomControls?.visibility = View.GONE
            context?.let {
                Toast.makeText(it, "Fullscreen active • Tap back to exit", Toast.LENGTH_SHORT).show()
            }
        } else {
            previewTopControls?.visibility = View.VISIBLE
            previewBottomControls?.visibility = View.VISIBLE
        }
    }

    fun handleBackPress(): Boolean {
        if (isFullscreenMode) {
            toggleFullscreen(false)
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        if (wallpaperId == WallpaperRegistry.WALLPAPER_ID_STICKS) {
            sticksSurfaceView?.startSensors()
        } else {
            waterSurfaceView?.startSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        if (wallpaperId == WallpaperRegistry.WALLPAPER_ID_STICKS) {
            sticksSurfaceView?.stopSensors()
        } else {
            waterSurfaceView?.stopSensors()
        }
    }
}
