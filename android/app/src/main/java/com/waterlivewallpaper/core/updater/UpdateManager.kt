package com.waterlivewallpaper.core.updater

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val info: UpdateInfo, val percentage: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState()
    data class Error(val message: String, val throwable: Throwable? = null) : UpdateState()
}

class UpdateManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var downloadJob: Job? = null
    var currentState: UpdateState = UpdateState.Idle
        private set

    private var cachedApkFile: File? = null
    private var cachedUpdateInfo: UpdateInfo? = null

    companion object {
        private const val TAG = "UpdateManager"
    }

    /**
     * Checks GitHub for updates.
     */
    fun checkForUpdates(
        currentVersionCode: Int,
        onStateChange: (UpdateState) -> Unit
    ) {
        currentState = UpdateState.Checking
        onStateChange(currentState)

        scope.launch {
            val result = UpdateChecker.checkForUpdate(currentVersionCode)
            result.onSuccess { info ->
                if (info != null) {
                    cachedUpdateInfo = info
                    // Check if already downloaded and verified
                    val localApk = File(File(context.cacheDir, "updates"), info.apkFileName)
                    if (localApk.exists() && localApk.length() > 0) {
                        cachedApkFile = localApk
                        currentState = UpdateState.ReadyToInstall(info, localApk)
                    } else {
                        currentState = UpdateState.UpdateAvailable(info)
                    }
                } else {
                    currentState = UpdateState.UpToDate
                }
                onStateChange(currentState)
            }.onFailure { err ->
                currentState = UpdateState.Error(err.message ?: "Failed to check for updates", err)
                onStateChange(currentState)
            }
        }
    }

    /**
     * Starts downloading the APK.
     */
    fun startDownload(
        info: UpdateInfo,
        onStateChange: (UpdateState) -> Unit
    ) {
        if (downloadJob?.isActive == true) return

        currentState = UpdateState.Downloading(info, 0, 0, info.sizeBytes)
        onStateChange(currentState)

        downloadJob = scope.launch {
            val result = UpdateDownloader.downloadApk(context, info) { downloaded, total, percent ->
                scope.launch(Dispatchers.Main) {
                    currentState = UpdateState.Downloading(info, percent, downloaded, total)
                    onStateChange(currentState)
                }
            }

            result.onSuccess { apkFile ->
                cachedApkFile = apkFile
                currentState = UpdateState.ReadyToInstall(info, apkFile)
                onStateChange(currentState)
            }.onFailure { err ->
                currentState = UpdateState.Error(err.message ?: "Failed to download update", err)
                onStateChange(currentState)
            }
        }
    }

    /**
     * Prompts Android package installer for the downloaded APK.
     */
    fun installUpdate(activity: Activity, onPermissionNeeded: () -> Unit): Boolean {
        val apk = cachedApkFile ?: return false
        if (!UpdateInstaller.canRequestPackageInstalls(activity)) {
            onPermissionNeeded()
            return false
        }
        val result = UpdateInstaller.installApk(activity, apk)
        return result.isSuccess
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        currentState = UpdateState.Idle
    }
}
