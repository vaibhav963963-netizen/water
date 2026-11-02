package com.waterlivewallpaper.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R
import com.waterlivewallpaper.ui.AnimationUtils
import com.waterlivewallpaper.view.WaterSurfaceView

class WallpaperPreviewFragment : Fragment() {

    private var waterSurfaceView: WaterSurfaceView? = null
    private var previewTopControls: View? = null
    private var previewBottomControls: View? = null
    private var isFullscreenMode = false

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
        previewTopControls = view.findViewById(R.id.previewTopControls)
        previewBottomControls = view.findViewById(R.id.previewBottomControls)

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
            mainActivity?.openWallpaperChooser()
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
        waterSurfaceView?.startSensors()
    }

    override fun onPause() {
        super.onPause()
        waterSurfaceView?.stopSensors()
    }
}
