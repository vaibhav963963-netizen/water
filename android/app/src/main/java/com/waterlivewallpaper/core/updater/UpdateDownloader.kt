package com.waterlivewallpaper.core.updater

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateDownloader {

    private const val TAG = "UpdateDownloader"

    /**
     * Downloads the APK file to the app's cache directory with progress reporting and SHA-256 verification.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percentage: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val targetFile = File(updatesDir, updateInfo.apkFileName)
        val tempFile = File(updatesDir, "${updateInfo.apkFileName}.tmp")

        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            var currentUrl = updateInfo.apkDownloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0
            val maxRedirects = 6

            // Follow redirects explicitly (GitHub releases redirect to AWS S3 CDN)
            while (true) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "LiveWaterPaper-Updater")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl == null || redirectCount++ >= maxRedirects) {
                        return@withContext Result.failure(Exception("Too many redirects or missing Location header"))
                    }
                    currentUrl = newUrl
                } else if (status == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    val msg = "Download failed with HTTP $status: ${connection.responseMessage}"
                    connection.disconnect()
                    return@withContext Result.failure(Exception(msg))
                }
            }

            val totalBytes = if (connection.contentLengthLong > 0) {
                connection.contentLengthLong
            } else {
                updateInfo.sizeBytes
            }

            val inputStream: InputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile)
            val digest = MessageDigest.getInstance("SHA-256")

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalDownloaded: Long = 0L
            var lastReportedPercent = -1

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(totalDownloaded, totalBytes, percent)
                        }
                    }
                }
            }

            connection.disconnect()

            // Verify SHA-256 checksum if provided by the release
            if (!updateInfo.sha256.isNullOrBlank()) {
                val computedBytes = digest.digest()
                val computedHash = computedBytes.joinToString("") { "%02x".format(it) }
                val expectedHash = updateInfo.sha256.trim().lowercase()

                Log.d(TAG, "Verifying SHA-256: computed=$computedHash, expected=$expectedHash")
                if (!computedHash.equals(expectedHash, ignoreCase = true)) {
                    tempFile.delete()
                    val errorMsg = "SHA-256 mismatch! Downloaded: $computedHash, Expected: $expectedHash"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(SecurityException(errorMsg))
                }
            }

            // Rename tempFile to targetFile
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(TAG, "Successfully downloaded and verified APK: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during APK download", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }
}
