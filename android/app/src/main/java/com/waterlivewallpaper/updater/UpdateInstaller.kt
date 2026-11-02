package com.waterlivewallpaper.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

object UpdateInstaller {

    private const val TAG = "UpdateInstaller"
    const val REQUEST_CODE_UNKNOWN_APPS = 1001

    /**
     * Checks if the app is authorized to request package installs on Android 8.0+ (API 26+).
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Directs the user to the system settings page to grant unknown source install permission.
     */
    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_UNKNOWN_APPS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open unknown app sources settings", e)
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                activity.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * Launches the system package installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(IllegalStateException("APK file does not exist or is empty"))
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            Log.i(TAG, "Launching package installer for URI: $contentUri")
            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting package installer", e)
            Result.failure(e)
        }
    }
}
