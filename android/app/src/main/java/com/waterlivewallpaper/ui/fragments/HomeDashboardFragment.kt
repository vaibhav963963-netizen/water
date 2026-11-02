package com.waterlivewallpaper.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R
import com.waterlivewallpaper.ui.AnimationUtils

class HomeDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity

        // Settings Button in Header
        view.findViewById<View>(R.id.btnHeaderSettings)?.let { btn ->
            AnimationUtils.attachTactilePress(btn) {
                mainActivity?.navigateToSettings()
            }
        }

        // Module 1: Wallpaper (Active)
        view.findViewById<View>(R.id.moduleCardWallpaper)?.let { card ->
            AnimationUtils.attachTactilePress(card) {
                mainActivity?.navigateToWallpaperList()
            }
        }

        // Module 2: Personal Notes (Coming Soon)
        view.findViewById<View>(R.id.moduleCardNotes)?.let { card ->
            AnimationUtils.attachTactilePress(card) {
                mainActivity?.navigateToComingSoon(
                    title = "Personal Notes",
                    stickerResId = R.drawable.sticker_notebook_3d,
                    desc = "A private space for your thoughts, checklists, and quick memos with fluid visual dynamics is in development."
                )
            }
        }

        // Module 3: Favourite Photos (Coming Soon)
        view.findViewById<View>(R.id.moduleCardPhotos)?.let { card ->
            AnimationUtils.attachTactilePress(card) {
                mainActivity?.navigateToComingSoon(
                    title = "Favourite Photos",
                    stickerResId = R.drawable.sticker_camera_3d,
                    desc = "Curate and preserve your favourite photo memories paired with realistic liquid distortion and depth effects."
                )
            }
        }

        // Module 4: Personal Collections (Coming Soon)
        view.findViewById<View>(R.id.moduleCardCollections)?.let { card ->
            AnimationUtils.attachTactilePress(card) {
                mainActivity?.navigateToComingSoon(
                    title = "Personal Collections",
                    stickerResId = R.drawable.sticker_folder_3d,
                    desc = "Organize customized fluid physics presets, saved scenes, and personal spaces in one central collection."
                )
            }
        }

        // Module 5: More Tools (Coming Soon)
        view.findViewById<View>(R.id.moduleCardTools)?.let { card ->
            AnimationUtils.attachTactilePress(card) {
                mainActivity?.navigateToComingSoon(
                    title = "More Tools",
                    stickerResId = R.drawable.sticker_toolbox_3d,
                    desc = "Useful daily personal utilities, sensors telemetry, and productivity enhancements are on the way."
                )
            }
        }
    }
}
