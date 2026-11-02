package com.waterlivewallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.waterlivewallpaper.config.WallpaperPreferences
import com.waterlivewallpaper.view.WaterSurfaceView

class MainActivity : AppCompatActivity() {

    private lateinit var waterSurfaceView: WaterSurfaceView
    private lateinit var preferences: WallpaperPreferences

    private lateinit var panelSettings: LinearLayout
    private lateinit var btnToggleSettings: Button
    private lateinit var btnSetWallpaper: Button
    private lateinit var btnSimulateShake: Button
    private lateinit var btnResetDefaults: Button

    private lateinit var seekVolume: SeekBar
    private lateinit var textVolumeVal: TextView

    private lateinit var seekSpeed: SeekBar
    private lateinit var textSpeedVal: TextView

    private lateinit var seekSensitivity: SeekBar
    private lateinit var textSensitivityVal: TextView

    private lateinit var seekShake: SeekBar
    private lateinit var textShakeVal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = WallpaperPreferences(this)
        waterSurfaceView = findViewById(R.id.waterSurfaceView)

        panelSettings = findViewById(R.id.panelSettings)
        btnToggleSettings = findViewById(R.id.btnToggleSettings)
        btnSetWallpaper = findViewById(R.id.btnSetWallpaper)
        btnSimulateShake = findViewById(R.id.btnSimulateShake)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)

        seekVolume = findViewById(R.id.seekVolume)
        textVolumeVal = findViewById(R.id.textVolumeVal)

        seekSpeed = findViewById(R.id.seekSpeed)
        textSpeedVal = findViewById(R.id.textSpeedVal)

        seekSensitivity = findViewById(R.id.seekSensitivity)
        textSensitivityVal = findViewById(R.id.textSensitivityVal)

        seekShake = findViewById(R.id.seekShake)
        textShakeVal = findViewById(R.id.textShakeVal)

        initControls()
        initColorPalette()
    }

    private fun initControls() {
        // Toggle Settings Card
        btnToggleSettings.setOnClickListener {
            if (panelSettings.visibility == View.VISIBLE) {
                panelSettings.visibility = View.GONE
            } else {
                panelSettings.visibility = View.VISIBLE
            }
        }

        // Set as Live Wallpaper
        btnSetWallpaper.setOnClickListener {
            openWallpaperChooser()
        }

        // Simulate Shake impulse
        btnSimulateShake.setOnClickListener {
            waterSurfaceView.simulationEngine.applyImpulse(
                (Math.random().toFloat() - 0.5f) * 750f,
                -650f
            )
            Toast.makeText(this, "Water surged!", Toast.LENGTH_SHORT).show()
        }

        // Reset to Defaults
        btnResetDefaults.setOnClickListener {
            preferences.resetToDefaults()
            updateUiFromPreferences()
            waterSurfaceView.simulationEngine.updateSettings(
                WallpaperPreferences.DEFAULT_WATER_VOLUME,
                WallpaperPreferences.DEFAULT_PHYSICS_SENSITIVITY,
                WallpaperPreferences.DEFAULT_SHAKE_SENSITIVITY,
                WallpaperPreferences.DEFAULT_FLOW_SPEED
            )
            Toast.makeText(this, "Reset to authentic Cyan defaults", Toast.LENGTH_SHORT).show()
        }

        // Volume SeekBar (20% to 70%)
        val initialVol = (preferences.getWaterVolume() * 100).toInt().coerceIn(20, 70)
        seekVolume.progress = initialVol
        textVolumeVal.text = "$initialVol%"
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(15)
                textVolumeVal.text = "$clamped%"
                if (fromUser) {
                    val volFloat = clamped / 100f
                    preferences.saveFromJson("""{"waterVolume":$volFloat}""")
                    waterSurfaceView.simulationEngine.initializeParticles(volFloat)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Water Speed / Gravity SeekBar (0.5x to 3.5x, default 2.0x = Real-Time Instant Gravity)
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
                    waterSurfaceView.simulationEngine.updateSettings(
                        preferences.getWaterVolume(),
                        preferences.getPhysicsSensitivity(),
                        preferences.getShakeSensitivity(),
                        speedFloat
                    )
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Tilt Sensitivity (0.5x to 2.5x)
        val initialSens = (preferences.getPhysicsSensitivity() * 100).toInt().coerceIn(50, 250)
        seekSensitivity.progress = initialSens
        textSensitivityVal.text = String.format("%.2fx", initialSens / 100f)
        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(30)
                val sensFloat = clamped / 100f
                textSensitivityVal.text = String.format("%.2fx", sensFloat)
                if (fromUser) {
                    preferences.saveFromJson("""{"physicsSensitivity":$sensFloat}""")
                    waterSurfaceView.simulationEngine.updateSettings(
                        preferences.getWaterVolume(),
                        sensFloat,
                        preferences.getShakeSensitivity(),
                        preferences.getFlowSpeed()
                    )
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Shake Surge (0.5x to 2.0x)
        val initialShake = (preferences.getShakeSensitivity() * 100).toInt().coerceIn(50, 200)
        seekShake.progress = initialShake
        textShakeVal.text = String.format("%.2fx", initialShake / 100f)
        seekShake.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(20)
                val shakeFloat = clamped / 100f
                textShakeVal.text = String.format("%.2fx", shakeFloat)
                if (fromUser) {
                    preferences.saveFromJson("""{"shakeSensitivity":$shakeFloat}""")
                    waterSurfaceView.simulationEngine.updateSettings(
                        preferences.getWaterVolume(),
                        preferences.getPhysicsSensitivity(),
                        shakeFloat,
                        preferences.getFlowSpeed()
                    )
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun formatSpeedLabel(speed: Float): String {
        return when {
            speed >= 2.8f -> String.format("%.1fx (Ultra Fast)", speed)
            speed >= 1.8f -> String.format("%.1fx (Real Water)", speed)
            speed >= 1.2f -> String.format("%.1fx (Moderate)", speed)
            else -> String.format("%.1fx (Slow-Mo)", speed)
        }
    }

    private fun initColorPalette() {
        findViewById<View>(R.id.colorCyan).setOnClickListener {
            applyWaterColor("#00E5FF", "#00B4D8", "Cyan")
        }
        findViewById<View>(R.id.colorAzure).setOnClickListener {
            applyWaterColor("#00B4D8", "#0077B6", "Azure")
        }
        findViewById<View>(R.id.colorViolet).setOnClickListener {
            applyWaterColor("#7B2CBF", "#5A189A", "Violet")
        }
        findViewById<View>(R.id.colorSunset).setOnClickListener {
            applyWaterColor("#FF6B6B", "#EE5253", "Sunset")
        }
        findViewById<View>(R.id.colorEmerald).setOnClickListener {
            applyWaterColor("#00E676", "#00C853", "Emerald")
        }
    }

    private fun applyWaterColor(primaryHex: String, secondaryHex: String, name: String) {
        preferences.saveFromJson("""{
            "waterColorHex": "$primaryHex",
            "waterColorSecondaryHex": "$secondaryHex"
        }""")
        Toast.makeText(this, "$name color applied", Toast.LENGTH_SHORT).show()
    }

    private fun updateUiFromPreferences() {
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

    private fun openWallpaperChooser() {
        try {
            val component = ComponentName(this, WaterWallpaperService::class.java)
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }

            val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (fallbackIntent.resolveActivity(packageManager) != null) {
                startActivity(fallbackIntent)
                return
            }

            val generalIntent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(generalIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open wallpaper settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        waterSurfaceView.startSensors()
    }

    override fun onPause() {
        super.onPause()
        waterSurfaceView.stopSensors()
    }
}
