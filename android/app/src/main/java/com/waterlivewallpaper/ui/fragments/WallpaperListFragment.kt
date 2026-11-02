package com.waterlivewallpaper.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R
import com.waterlivewallpaper.ui.AnimationUtils

class WallpaperListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallpaper_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity

        view.findViewById<View>(R.id.btnWallpaperListBack)?.setOnClickListener {
            mainActivity?.onBackPressedDispatcher?.onBackPressed()
        }

        view.findViewById<View>(R.id.cardWallpaperCatalogItem)?.let { card ->
            AnimationUtils.attachTactilePress(card) {
                mainActivity?.navigateToWallpaperPreview()
            }
        }

        view.findViewById<View>(R.id.btnViewWallpaper)?.let { btn ->
            AnimationUtils.attachTactilePress(btn) {
                mainActivity?.navigateToWallpaperPreview()
            }
        }
    }
}
