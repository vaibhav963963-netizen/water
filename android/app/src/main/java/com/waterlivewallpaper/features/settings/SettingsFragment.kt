package com.waterlivewallpaper.features.settings

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
import com.waterlivewallpaper.core.ui.AnimationUtils
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences

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

        // Quality & Battery Mode Selector
        val textQualitySubtitle = view.findViewById<TextView>(R.id.textQualitySubtitle)
        val btnModeSaver = view.findViewById<TextView>(R.id.btnModeSaver)
        val btnModeBalanced = view.findViewById<TextView>(R.id.btnModeBalanced)
        val btnModePerformance = view.findViewById<TextView>(R.id.btnModePerformance)

        fun updateQualityUi(mode: String) {
            val isSaver = mode == WallpaperPreferences.QUALITY_BATTERY_SAVER || mode == "low"
            val isPerf = mode == WallpaperPreferences.QUALITY_PERFORMANCE || mode == "high"
            val isBalanced = !isSaver && !isPerf

            btnModeSaver.setBackgroundResource(if (isSaver) R.drawable.btn_secondary_cream else R.drawable.btn_pill_outline)
            btnModeSaver.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), if (isSaver) R.color.milano_red else R.color.text_secondary))

            btnModeBalanced.setBackgroundResource(if (isBalanced) R.drawable.btn_secondary_cream else R.drawable.btn_pill_outline)
            btnModeBalanced.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), if (isBalanced) R.color.milano_red else R.color.text_secondary))

            btnModePerformance.setBackgroundResource(if (isPerf) R.drawable.btn_secondary_cream else R.drawable.btn_pill_outline)
            btnModePerformance.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), if (isPerf) R.color.milano_red else R.color.text_secondary))

            textQualitySubtitle?.text = when {
                isSaver -> "Battery Saver (24 FPS) • Lean particle load & maximum battery endurance"
                isPerf -> "Performance (60 FPS) • Silky high-refresh rate fluid simulation"
                else -> "Balanced (30 FPS) • Optimal battery & authentic water dynamics"
            }
        }

        updateQualityUi(preferences.getQualityLevel())

        AnimationUtils.attachTactilePress(btnModeSaver) {
            preferences.saveFromJson("""{"qualityLevel":"battery_saver","targetFps":24}""")
            updateQualityUi("battery_saver")
            mainActivity?.showCustomToast("Battery Saver Enabled", "24 FPS target • Optimized for longevity", R.drawable.sticker_water_drop_3d)
        }

        AnimationUtils.attachTactilePress(btnModeBalanced) {
            preferences.saveFromJson("""{"qualityLevel":"balanced","targetFps":30}""")
            updateQualityUi("balanced")
            mainActivity?.showCustomToast("Balanced Mode Active", "30 FPS target • Authentic water flow", R.drawable.sticker_water_drop_3d)
        }

        AnimationUtils.attachTactilePress(btnModePerformance) {
            preferences.saveFromJson("""{"qualityLevel":"performance","targetFps":60}""")
            updateQualityUi("performance")
            mainActivity?.showCustomToast("Performance Mode Active", "60 FPS target • High refresh dynamics", R.drawable.sticker_water_drop_3d)
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
