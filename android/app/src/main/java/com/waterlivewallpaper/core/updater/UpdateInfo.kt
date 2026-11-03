package com.waterlivewallpaper.core.updater

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val sha256: String?,
    val sizeBytes: Long,
    val publishedAt: String? = null
)
