package com.waterlivewallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.waterlivewallpaper.core.ui.AnimationUtils
import com.waterlivewallpaper.core.updater.UpdateInfo
import com.waterlivewallpaper.core.updater.UpdateInstaller
import com.waterlivewallpaper.core.updater.UpdateManager
import com.waterlivewallpaper.core.updater.UpdateState
import com.waterlivewallpaper.features.dashboard.ComingSoonFragment
import com.waterlivewallpaper.features.dashboard.HomeDashboardFragment
import com.waterlivewallpaper.features.settings.SettingsFragment
import com.waterlivewallpaper.features.wallpapers.base.WallpaperRegistry
import com.waterlivewallpaper.features.wallpapers.list.WallpaperListFragment
import com.waterlivewallpaper.features.wallpapers.water.ui.WallpaperCustomizeFragment
import com.waterlivewallpaper.features.wallpapers.water.ui.WallpaperPreviewFragment

class MainActivity : AppCompatActivity() {

    private lateinit var updateManager: UpdateManager

    private lateinit var bottomNavBar: LinearLayout
    private lateinit var navTabHome: LinearLayout
    private lateinit var navIconHome: ImageView
    private lateinit var navTextHome: TextView

    private lateinit var navTabWallpaper: LinearLayout
    private lateinit var navIconWallpaper: ImageView
    private lateinit var navTextWallpaper: TextView

    private lateinit var navTabSettings: LinearLayout
    private lateinit var navIconSettings: ImageView
    private lateinit var navTextSettings: TextView

    private var toastContainer: LinearLayout? = null
    private var toastTitle: TextView? = null
    private var toastSubtitle: TextView? = null
    private var toastIcon: ImageView? = null
    private var toastDismissRunnable: Runnable? = null
    private val toastHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun showCustomToast(
        title: String,
        subtitle: String? = null,
        iconRes: Int = R.drawable.sticker_water_drop_3d
    ) {
        runOnUiThread {
            if (toastContainer == null) {
                toastContainer = findViewById(R.id.toastBannerContainer)
                toastTitle = findViewById(R.id.toastTitle)
                toastSubtitle = findViewById(R.id.toastSubtitle)
                toastIcon = findViewById(R.id.toastIcon)
                toastContainer?.setOnClickListener {
                    hideCustomToast()
                }
            }

            toastDismissRunnable?.let { toastHandler.removeCallbacks(it) }

            toastTitle?.text = title
            if (subtitle.isNullOrEmpty()) {
                toastSubtitle?.visibility = View.GONE
            } else {
                toastSubtitle?.text = subtitle
                toastSubtitle?.visibility = View.VISIBLE
            }
            toastIcon?.setImageResource(iconRes)

            toastContainer?.let { container ->
                container.visibility = View.VISIBLE
                container.alpha = 0f
                container.translationY = -60f
                container.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()

                val runnable = Runnable { hideCustomToast() }
                toastDismissRunnable = runnable
                toastHandler.postDelayed(runnable, 2600)
            }
        }
    }

    fun hideCustomToast() {
        toastContainer?.let { container ->
            container.animate()
                .alpha(0f)
                .translationY(-60f)
                .setDuration(180)
                .withEndAction {
                    container.visibility = View.GONE
                }
                .start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateManager = UpdateManager(this)

        initBottomNavViews()
        setupBackStackListener()

        // ALWAYS cold launch into the Home Dashboard Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeDashboardFragment())
                .commit()
        }

        // Background update check on startup
        performUpdateCheck(isManual = false)
    }

    private fun initBottomNavViews() {
        bottomNavBar = findViewById(R.id.bottomNavBar)

        navTabHome = findViewById(R.id.navTabHome)
        navIconHome = findViewById(R.id.navIconHome)
        navTextHome = findViewById(R.id.navTextHome)

        navTabWallpaper = findViewById(R.id.navTabWallpaper)
        navIconWallpaper = findViewById(R.id.navIconWallpaper)
        navTextWallpaper = findViewById(R.id.navTextWallpaper)

        navTabSettings = findViewById(R.id.navTabSettings)
        navIconSettings = findViewById(R.id.navIconSettings)
        navTextSettings = findViewById(R.id.navTextSettings)

        AnimationUtils.attachTactilePress(navTabHome) {
            navigateToHome()
        }

        AnimationUtils.attachTactilePress(navTabWallpaper) {
            navigateToWallpaperList()
        }

        AnimationUtils.attachTactilePress(navTabSettings) {
            navigateToSettings()
        }
    }

    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            updateBottomNavState(currentFragment)
        }
        updateBottomNavState(supportFragmentManager.findFragmentById(R.id.fragmentContainer))
    }

    private fun updateBottomNavState(fragment: Fragment?) {
        val activeColor = ContextCompat.getColor(this, R.color.brand_milano_red)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_muted)

        when (fragment) {
            is HomeDashboardFragment, null -> {
                bottomNavBar.visibility = View.VISIBLE
                setTabSelected(isHome = true, isWallpaper = false, isSettings = false, activeColor, inactiveColor)
            }
            is WallpaperListFragment -> {
                bottomNavBar.visibility = View.VISIBLE
                setTabSelected(isHome = false, isWallpaper = true, isSettings = false, activeColor, inactiveColor)
            }
            is SettingsFragment -> {
                bottomNavBar.visibility = View.VISIBLE
                setTabSelected(isHome = false, isWallpaper = false, isSettings = true, activeColor, inactiveColor)
            }
            is WallpaperPreviewFragment,
            is WallpaperCustomizeFragment,
            is ComingSoonFragment -> {
                // Hide floating bottom nav for total immersion
                bottomNavBar.visibility = View.GONE
            }
            else -> {
                bottomNavBar.visibility = View.VISIBLE
            }
        }
    }

    private fun setTabSelected(
        isHome: Boolean,
        isWallpaper: Boolean,
        isSettings: Boolean,
        activeColor: Int,
        inactiveColor: Int
    ) {
        navIconHome.imageTintList = ColorStateList.valueOf(if (isHome) activeColor else inactiveColor)
        navTextHome.setTextColor(if (isHome) activeColor else inactiveColor)

        navIconWallpaper.imageTintList = ColorStateList.valueOf(if (isWallpaper) activeColor else inactiveColor)
        navTextWallpaper.setTextColor(if (isWallpaper) activeColor else inactiveColor)

        navIconSettings.imageTintList = ColorStateList.valueOf(if (isSettings) activeColor else inactiveColor)
        navTextSettings.setTextColor(if (isSettings) activeColor else inactiveColor)
    }

    fun navigateToHome() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current !is HomeDashboardFragment) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragmentContainer, HomeDashboardFragment())
                .commit()
        }
    }

    fun navigateToWallpaperList() {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is WallpaperListFragment) return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, WallpaperListFragment())
            .addToBackStack("WallpaperList")
            .commit()
    }

    fun navigateToWallpaperPreview() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, WallpaperPreviewFragment.newInstance(WallpaperRegistry.WALLPAPER_ID_WATER))
            .addToBackStack("WallpaperPreview")
            .commit()
    }

    fun navigateToSticksPreview() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, WallpaperPreviewFragment.newInstance(WallpaperRegistry.WALLPAPER_ID_STICKS))
            .addToBackStack("SticksPreview")
            .commit()
    }

    fun navigateToWallpaperCustomize() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, WallpaperCustomizeFragment())
            .addToBackStack("WallpaperCustomize")
            .commit()
    }

    fun navigateToComingSoon(title: String, stickerResId: Int, desc: String) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, ComingSoonFragment.newInstance(title, stickerResId, desc))
            .addToBackStack("ComingSoon_$title")
            .commit()
    }

    fun navigateToSettings() {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is SettingsFragment) return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, SettingsFragment())
            .addToBackStack("Settings")
            .commit()
    }

    fun openWallpaperChooser() {
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

    fun openSticksWallpaperChooser() {
        try {
            val component = ComponentName(this, SticksWallpaperService::class.java)
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

    fun performUpdateCheck(isManual: Boolean) {
        updateManager.checkForUpdates(BuildConfig.VERSION_CODE) { state ->
            when (state) {
                is UpdateState.Checking -> {}
                is UpdateState.UpdateAvailable -> {
                    showUpdateDialog(state.info)
                }
                is UpdateState.ReadyToInstall -> {
                    showUpdateDialog(state.info)
                }
                is UpdateState.UpToDate -> {
                    if (isManual) {
                        Toast.makeText(this, "Live WaterPaper is up to date!", Toast.LENGTH_SHORT).show()
                    }
                }
                is UpdateState.Error -> {
                    if (isManual) {
                        Toast.makeText(this, "Update check failed: ${state.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update, null)
        val textTitle = dialogView.findViewById<TextView>(R.id.textDialogTitle)
        val textVersion = dialogView.findViewById<TextView>(R.id.textDialogVersion)
        val textNotes = dialogView.findViewById<TextView>(R.id.textDialogNotes)
        val containerProgress = dialogView.findViewById<LinearLayout>(R.id.containerDownloadProgress)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressDownload)
        val textProgressStatus = dialogView.findViewById<TextView>(R.id.textDownloadStatus)
        val btnLater = dialogView.findViewById<Button>(R.id.btnDialogLater)
        val btnAction = dialogView.findViewById<Button>(R.id.btnDialogAction)

        textTitle.text = if (info.releaseName.isNotBlank()) info.releaseName else "Update Available"
        textVersion.text = "Version ${info.versionName} (Build ${info.versionCode})"
        textNotes.text = info.releaseNotes

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        fun updateUiForState(state: UpdateState) {
            when (state) {
                is UpdateState.UpdateAvailable -> {
                    btnAction.text = "UPDATE NOW"
                    btnAction.isEnabled = true
                    containerProgress.visibility = View.GONE
                }
                is UpdateState.Downloading -> {
                    btnAction.isEnabled = false
                    btnAction.text = "DOWNLOADING..."
                    containerProgress.visibility = View.VISIBLE
                    progressBar.progress = state.percentage
                    val mbDownloaded = state.downloadedBytes / (1024f * 1024f)
                    val mbTotal = state.totalBytes / (1024f * 1024f)
                    textProgressStatus.text = String.format(
                        "Downloading: %d%% (%.1f MB / %.1f MB)",
                        state.percentage,
                        mbDownloaded,
                        mbTotal
                    )
                }
                is UpdateState.ReadyToInstall -> {
                    btnAction.isEnabled = true
                    btnAction.text = "INSTALL NOW"
                    containerProgress.visibility = View.GONE
                    btnAction.setOnClickListener {
                        val installed = updateManager.installUpdate(this) {
                            Toast.makeText(
                                this,
                                "Please grant permission to install updates",
                                Toast.LENGTH_LONG
                            ).show()
                            UpdateInstaller.openInstallPermissionSettings(this)
                        }
                        if (installed) {
                            dialog.dismiss()
                        }
                    }
                }
                is UpdateState.Error -> {
                    btnAction.isEnabled = true
                    btnAction.text = "RETRY"
                    containerProgress.visibility = View.GONE
                    Toast.makeText(this, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        if (updateManager.currentState is UpdateState.ReadyToInstall) {
            updateUiForState(updateManager.currentState)
        } else {
            btnAction.setOnClickListener {
                updateManager.startDownload(info) { state ->
                    updateUiForState(state)
                    if (state is UpdateState.ReadyToInstall) {
                        val installed = updateManager.installUpdate(this) {
                            Toast.makeText(
                                this,
                                "Please grant permission to install updates",
                                Toast.LENGTH_LONG
                            ).show()
                            UpdateInstaller.openInstallPermissionSettings(this)
                        }
                        if (installed) {
                            dialog.dismiss()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UpdateInstaller.REQUEST_CODE_UNKNOWN_APPS) {
            if (UpdateInstaller.canRequestPackageInstalls(this)) {
                updateManager.installUpdate(this) {}
            } else {
                Toast.makeText(this, "Install permission not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (currentFragment is WallpaperPreviewFragment && currentFragment.handleBackPress()) {
            return
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}
