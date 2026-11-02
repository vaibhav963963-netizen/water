package com.waterlivewallpaper.updater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_OWNER = "vaibhav963963-netizen"
    private const val GITHUB_REPO = "water"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /**
     * Checks GitHub Releases API for a newer version than currentVersionCode.
     * Runs strictly on Dispatchers.IO.
     */
    suspend fun checkForUpdate(currentVersionCode: Int): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "LiveWaterPaper-Updater")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.d(TAG, "No releases published yet on GitHub repository.")
                return@withContext Result.success(null)
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = "GitHub API returned HTTP $responseCode: ${connection.responseMessage}"
                Log.w(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseJson = JSONObject(responseBody)

            val tagName = releaseJson.optString("tag_name", "")
            val releaseName = releaseJson.optString("name", tagName)
            val releaseNotes = releaseJson.optString("body", "Bug fixes and performance improvements.")
            val publishedAt = if (releaseJson.has("published_at")) releaseJson.optString("published_at") else null

            val assets = releaseJson.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkFileName: String? = null
            var apkSizeBytes: Long = 0L
            var manifestUrl: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)

                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = downloadUrl
                    apkFileName = name
                    apkSizeBytes = size
                } else if (name.equals("update_manifest.json", ignoreCase = true)) {
                    manifestUrl = downloadUrl
                }
            }

            if (apkUrl == null) {
                Log.d(TAG, "Release $tagName contains no APK asset.")
                return@withContext Result.success(null)
            }

            // Determine remote versionCode, versionName, and SHA256
            var remoteVersionCode = -1
            var remoteVersionName = tagName.removePrefix("v").trim()
            var expectedSha256: String? = null

            // Try reading update_manifest.json if present
            if (manifestUrl != null) {
                try {
                    val manifestConn = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("User-Agent", "LiveWaterPaper-Updater")
                    }
                    if (manifestConn.responseCode == HttpURLConnection.HTTP_OK) {
                        val manifestText = manifestConn.inputStream.bufferedReader().use { it.readText() }
                        val manifestJson = JSONObject(manifestText)
                        remoteVersionCode = manifestJson.optInt("versionCode", -1)
                        if (manifestJson.has("versionName")) {
                            remoteVersionName = manifestJson.getString("versionName")
                        }
                        if (manifestJson.has("sha256")) {
                            expectedSha256 = manifestJson.getString("sha256")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch update_manifest.json: ${e.message}")
                }
            }

            // Fallback parsing from release body or tag name
            if (remoteVersionCode == -1) {
                val codeMatcher = Pattern.compile("(?i)(?:versionCode|build)[\\s:=]+([0-9]+)").matcher(releaseNotes)
                if (codeMatcher.find()) {
                    remoteVersionCode = codeMatcher.group(1)?.toIntOrNull() ?: -1
                }
            }

            if (remoteVersionCode == -1) {
                // Try semantic version parsing from tag: v1.0.2 -> 102
                val semverMatcher = Pattern.compile("v?([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?").matcher(tagName)
                if (semverMatcher.find()) {
                    val major = semverMatcher.group(1)?.toIntOrNull() ?: 0
                    val minor = semverMatcher.group(2)?.toIntOrNull() ?: 0
                    val patch = semverMatcher.group(3)?.toIntOrNull() ?: 0
                    remoteVersionCode = (major * 10000) + (minor * 100) + patch
                }
            }

            if (expectedSha256 == null) {
                val shaMatcher = Pattern.compile("(?i)(?:sha256|hash)[\\s:=]+([a-f0-9]{64})").matcher(releaseNotes)
                if (shaMatcher.find()) {
                    expectedSha256 = shaMatcher.group(1)
                }
            }

            Log.d(TAG, "Current versionCode: $currentVersionCode, Remote versionCode: $remoteVersionCode")

            if (remoteVersionCode > currentVersionCode) {
                val info = UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = remoteVersionName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkFileName ?: "LiveWaterPaper.apk",
                    sha256 = expectedSha256,
                    sizeBytes = apkSizeBytes,
                    publishedAt = publishedAt
                )
                Result.success(info)
            } else {
                Result.success(null) // Already up to date
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            Result.failure(e)
        }
    }
}
