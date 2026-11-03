package com.waterlivewallpaper.features.wallpapers.water.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R
import com.waterlivewallpaper.core.ui.AnimationUtils
import com.waterlivewallpaper.features.wallpapers.water.config.WallpaperPreferences
import java.io.File
import java.io.FileOutputStream

class WallpaperCustomizeFragment : Fragment() {

    private lateinit var preferences: WallpaperPreferences

    private lateinit var seekVolume: SeekBar
    private lateinit var textVolumeVal: TextView
    private lateinit var seekSpeed: SeekBar
    private lateinit var textSpeedVal: TextView
    private lateinit var seekSensitivity: SeekBar
    private lateinit var textSensitivityVal: TextView
    private lateinit var seekShake: SeekBar
    private lateinit var textShakeVal: TextView

    private lateinit var imgBgPreview: ImageView
    private lateinit var textBgStatusTitle: TextView
    private lateinit var textBgStatusSubtitle: TextView
    private lateinit var btnChooseBgImage: Button
    private lateinit var btnRemoveBgImage: Button

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            handleSelectedImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallpaper_customize, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        preferences = WallpaperPreferences(requireContext())

        view.findViewById<View>(R.id.btnCustomizeBack).setOnClickListener {
            mainActivity?.onBackPressedDispatcher?.onBackPressed()
        }

        seekVolume = view.findViewById(R.id.seekVolume)
        textVolumeVal = view.findViewById(R.id.textVolumeVal)
        seekSpeed = view.findViewById(R.id.seekSpeed)
        textSpeedVal = view.findViewById(R.id.textSpeedVal)
        seekSensitivity = view.findViewById(R.id.seekSensitivity)
        textSensitivityVal = view.findViewById(R.id.textSensitivityVal)
        seekShake = view.findViewById(R.id.seekShake)
        textShakeVal = view.findViewById(R.id.textShakeVal)

        imgBgPreview = view.findViewById(R.id.imgBgPreview)
        textBgStatusTitle = view.findViewById(R.id.textBgStatusTitle)
        textBgStatusSubtitle = view.findViewById(R.id.textBgStatusSubtitle)
        btnChooseBgImage = view.findViewById(R.id.btnChooseBgImage)
        btnRemoveBgImage = view.findViewById(R.id.btnRemoveBgImage)

        btnChooseBgImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnRemoveBgImage.setOnClickListener {
            removeCustomBackground()
        }

        updateBackgroundUi()

        // Reset Defaults
        AnimationUtils.attachTactilePress(view.findViewById(R.id.btnCustomizeReset)) {
            preferences.resetToDefaults()
            removeCustomBackground(showToast = false)
            updateUiFromPreferences()
            Toast.makeText(context, "Reset to authentic defaults", Toast.LENGTH_SHORT).show()
        }

        // Test Splash
        AnimationUtils.attachTactilePress(view.findViewById(R.id.btnCustomizeSplash)) {
            Toast.makeText(context, "⚡ Splash surge applied to active wallpaper!", Toast.LENGTH_SHORT).show()
        }

        // Set as Wallpaper
        AnimationUtils.attachTactilePress(view.findViewById(R.id.btnCustomizeApply)) {
            mainActivity?.openWallpaperChooser()
        }

        // Setup sliders
        val initialVol = (preferences.getWaterVolume() * 100).toInt().coerceIn(15, 70)
        seekVolume.progress = initialVol
        textVolumeVal.text = "$initialVol%"
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(15)
                textVolumeVal.text = "$clamped%"
                if (fromUser) {
                    val volFloat = clamped / 100f
                    preferences.saveFromJson("""{"waterVolume":$volFloat}""")
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val initialSpeed = (preferences.getFlowSpeed() * 100).toInt().coerceIn(50, 350)
        seekSpeed.progress = initialSpeed
        textSpeedVal.text = formatSpeedLabel(initialSpeed / 100f)
        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(40)
                val speedFloat = clamped / 100f
                textSpeedVal.text = formatSpeedLabel(speedFloat)
                if (fromUser) {
                    preferences.saveFromJson("""{"flowSpeed":$speedFloat}""")
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val initialSens = (preferences.getPhysicsSensitivity() * 100).toInt().coerceIn(30, 200)
        seekSensitivity.progress = initialSens
        textSensitivityVal.text = String.format("%.2fx", initialSens / 100f)
        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(30)
                val sensFloat = clamped / 100f
                textSensitivityVal.text = String.format("%.2fx", sensFloat)
                if (fromUser) {
                    preferences.saveFromJson("""{"physicsSensitivity":$sensFloat}""")
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val initialShake = (preferences.getShakeSensitivity() * 100).toInt().coerceIn(20, 160)
        seekShake.progress = initialShake
        textShakeVal.text = String.format("%.2fx", initialShake / 100f)
        seekShake.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(20)
                val shakeFloat = clamped / 100f
                textShakeVal.text = String.format("%.2fx", shakeFloat)
                if (fromUser) {
                    preferences.saveFromJson("""{"shakeSensitivity":$shakeFloat}""")
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Brand Milano Red & Lemon Chiffon Color Palette
        view.findViewById<View>(R.id.colorMilanoRed)?.let {
            AnimationUtils.attachTactilePress(it) {
                applyWaterColor("#A90E02", "#800A01", "Milano Red")
            }
        }
        view.findViewById<View>(R.id.colorCrimson)?.let {
            AnimationUtils.attachTactilePress(it) {
                applyWaterColor("#800A01", "#5C0000", "Deep Crimson")
            }
        }
        view.findViewById<View>(R.id.colorGold)?.let {
            AnimationUtils.attachTactilePress(it) {
                applyWaterColor("#C49A45", "#E5CD91", "Muted Gold")
            }
        }
        view.findViewById<View>(R.id.colorLemonChiffon)?.let {
            AnimationUtils.attachTactilePress(it) {
                applyWaterColor("#FFFBD4", "#F5EDB0", "Lemon Chiffon")
            }
        }
        view.findViewById<View>(R.id.colorCharcoal)?.let {
            AnimationUtils.attachTactilePress(it) {
                applyWaterColor("#292421", "#171311", "Deep Charcoal")
            }
        }
    }

    private fun formatSpeedLabel(speed: Float): String {
        return when {
            speed >= 2.8f -> String.format("%.1fx (Ultra Fast)", speed)
            speed >= 1.8f -> String.format("%.1fx (Real Water)", speed)
            speed >= 1.2f -> String.format("%.1fx (Moderate)", speed)
            else -> String.format("%.1fx (Slow-Mo)", speed)
        }
    }

    private fun applyWaterColor(primaryHex: String, secondaryHex: String, name: String) {
        preferences.saveFromJson("""{
            "waterColorHex": "$primaryHex",
            "waterColorSecondaryHex": "$secondaryHex"
        }""")
        (activity as? MainActivity)?.showCustomToast(
            "Liquid Color Updated",
            "$name fluid active",
            R.drawable.sticker_water_drop_3d
        )
    }

    private fun handleSelectedImage(uri: Uri) {
        try {
            val context = requireContext()
            val targetFile = File(context.filesDir, "custom_wallpaper_bg.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            preferences.setCustomImagePath(targetFile.absolutePath)
            updateBackgroundUi()
            (activity as? MainActivity)?.showCustomToast(
                "Background Image Set",
                "Personal photograph active behind the water effect",
                R.drawable.sticker_camera_3d
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Could not load selected image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeCustomBackground(showToast: Boolean = true) {
        val path = preferences.getCustomImagePath()
        if (path != null) {
            try { File(path).delete() } catch (ignored: Exception) {}
        }
        preferences.setCustomImagePath(null)
        updateBackgroundUi()
        if (showToast) {
            (activity as? MainActivity)?.showCustomToast(
                "Background Restored",
                "Original default wallpaper background active",
                R.drawable.sticker_water_drop_3d
            )
        }
    }

    private fun updateBackgroundUi() {
        val path = preferences.getCustomImagePath()
        if (path != null && File(path).exists()) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = BitmapFactory.decodeFile(path, opts)
                if (bmp != null) {
                    imgBgPreview.setImageBitmap(bmp)
                } else {
                    imgBgPreview.setImageResource(R.drawable.wallpaper_thumb)
                }
            } catch (e: Exception) {
                imgBgPreview.setImageResource(R.drawable.wallpaper_thumb)
            }
            textBgStatusTitle.text = "Personal Image Active"
            textBgStatusSubtitle.text = "Rendered behind animated water effect"
            btnChooseBgImage.text = "Change Image"
            btnRemoveBgImage.visibility = View.VISIBLE
        } else {
            imgBgPreview.setImageResource(R.drawable.wallpaper_thumb)
            textBgStatusTitle.text = "Default Canvas"
            textBgStatusSubtitle.text = "No personal photograph set"
            btnChooseBgImage.text = "Choose Background Image"
            btnRemoveBgImage.visibility = View.GONE
        }
    }

    private fun updateUiFromPreferences() {
        updateBackgroundUi()
        val vol = (preferences.getWaterVolume() * 100).toInt()
        seekVolume.progress = vol
        textVolumeVal.text = "$vol%"

        val spd = (preferences.getFlowSpeed() * 100).toInt()
        seekSpeed.progress = spd
        textSpeedVal.text = formatSpeedLabel(spd / 100f)

        val sens = (preferences.getPhysicsSensitivity() * 100).toInt()
        seekSensitivity.progress = sens
        textSensitivityVal.text = String.format("%.2fx", sens / 100f)

        val shake = (preferences.getShakeSensitivity() * 100).toInt()
        seekShake.progress = shake
        textShakeVal.text = String.format("%.2fx", shake / 100f)
    }
}
