package com.waterlivewallpaper.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.BuildConfig
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R
import com.waterlivewallpaper.config.WallpaperPreferences
import com.waterlivewallpaper.ui.AnimationUtils

class SettingsFragment : Fragment() {

    private lateinit var preferences: WallpaperPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        preferences = WallpaperPreferences(requireContext())

        view.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            mainActivity?.onBackPressedDispatcher?.onBackPressed()
        }

        view.findViewById<TextView>(R.id.textAppVersion).text =
            "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"

        val btnCheckUpdate = view.findViewById<Button>(R.id.btnCheckUpdate)
        AnimationUtils.attachTactilePress(btnCheckUpdate) {
            mainActivity?.performUpdateCheck(isManual = true)
        }

        val switchTouch = view.findViewById<SwitchCompat>(R.id.switchTouchInteraction)
        switchTouch.isChecked = preferences.isTouchEnabled()
        switchTouch.setOnCheckedChangeListener { _, isChecked ->
            preferences.saveFromJson("""{"touchEnabled":$isChecked}""")
            mainActivity?.showCustomToast(
                "Touch Disturbance",
                if (isChecked) "Interactive ripples enabled" else "Touch ripples disabled",
                R.drawable.sticker_water_drop_3d
            )
        }

        val switchHaptics = view.findViewById<SwitchCompat>(R.id.switchHaptics)
        switchHaptics.isChecked = true
        switchHaptics.setOnCheckedChangeListener { _, isChecked ->
            mainActivity?.showCustomToast(
                "Tactile Feedback",
                if (isChecked) "Haptic micro-vibrations enabled" else "Haptic feedback disabled",
                R.drawable.sticker_water_drop_3d
            )
        }
    }
}
