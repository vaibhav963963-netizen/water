package com.waterlivewallpaper.features.wallpapers.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R
import com.waterlivewallpaper.core.ui.AnimationUtils
import com.waterlivewallpaper.features.wallpapers.base.WallpaperRegistry

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

        // Active Water Wallpaper Card
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

        // Floating Sticks Water Card
        view.findViewById<View>(R.id.cardWallpaperSticksItem)?.let { card ->
            AnimationUtils.attachTactilePress(card) {
                mainActivity?.navigateToSticksPreview()
            }
        }

        view.findViewById<View>(R.id.btnViewSticksWallpaper)?.let { btn ->
            AnimationUtils.attachTactilePress(btn) {
                mainActivity?.navigateToSticksPreview()
            }
        }
    }
}
