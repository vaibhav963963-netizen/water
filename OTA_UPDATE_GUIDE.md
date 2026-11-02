# Live WaterPaper — Production Native Android Auto-Update Guide

This guide describes the complete architecture, operation, and developer workflow for the native automatic APK update system in **Live WaterPaper**.

---

## 1. System Architecture Overview

```
┌────────────────────────────────────────────────────────┐
│                   DEVELOPER WORKFLOW                   │
│                                                        │
│  1. Modify Kotlin / Shaders / Physics code             │
│  2. Bump `versionCode` & `versionName` in build.gradle │
│  3. `git commit` & `git push origin main`              │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│              GITHUB ACTIONS CI/CD PIPELINE             │
│            (.github/workflows/android-release.yml)     │
│                                                        │
│  • Check out repository                                │
│  • Run unit test verification suite (14/14 tests)      │
│  • Set up JDK 17 & Android SDK 35                      │
│  • Assemble Release APK (`./gradlew assembleRelease`)  │
│  • Sign with established production keystore           │
│  • Calculate SHA-256 checksum                          │
│  • Generate versioned `update_manifest.json`           │
│  • Publish GitHub Release with assets & tags           │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│                   INSTALLED ANDROID APP                │
│                                                        │
│  • Background Check: Queries GitHub Releases API       │
│  • Checks: `remote.versionCode > BuildConfig.VERSION`  │
│  • UI Prompt: Displays sleek Glassmorphic update card  │
│  • Secure Download: HTTPS streaming to app cache       │
│  • Integrity Verification: Computes & matches SHA-256  │
│  • Seamless Install: Invokes Android `FileProvider`    │
│  • User taps "Update" -> Zero data/settings lost       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Signing Continuity & Security Verification

Android enforces that an installed application can **only** be upgraded if the new APK is signed with the **exact same signing certificate** (public key / fingerprint).

### Established Signing Identity

- **Keystore File**: `android/app/debug.keystore`
- **Key Alias**: `androiddebugkey`
- **Key Algorithm**: RSA 2048-bit
- **Signature Algorithm**: SHA384withRSA
- **Certificate Validity**: Valid until **March 20, 2054**
- **Certificate SHA-256 Fingerprint**:
  ```text
  5B:30:8F:51:AA:7E:3A:1B:05:39:9C:64:C2:BF:D3:D0:FA:92:5C:55:56:5D:A6:40:5B:E6:48:ED:BB:9D:BD:94
  ```
- **Certificate SHA-1 Fingerprint**:
  ```text
  6B:E8:7C:40:20:5F:16:BC:4A:A5:33:95:E5:DC:20:7F:AE:13:D7:EF
  ```

> [!IMPORTANT]
> Both `debug` and `release` build types in `android/app/build.gradle` are configured to use this signing identity. Because this keystore is tracked in the repository, GitHub Actions automatically builds release APKs with this exact certificate.
> 
> Existing installed instances of `LiveWaterPaper.apk` will update **without** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` errors and without losing user wallpaper preferences.

---

## 3. Zero-Manual-Touch Automated Release Workflow

You **never** need to manually edit `versionCode` or `versionName` for each release!

### The Developer Experience:
1. Develop features, tune fluid physics, add colors, or update UI.
2. Commit and push directly to `main`:
   ```bash
   git add .
   git commit -m "Update liquid flow speed and responsiveness"
   git push origin main
   ```
3. That's it!

### What GitHub Actions Automatically Does:
1. **Concurrency Lock**: Queues releases sequentially under `group: android-production-release` (preventing concurrent race conditions).
2. **Monotonic Version Resolution** ([`scripts/version-manager.js`](file:///scripts/version-manager.js)):
   - Queries the GitHub Releases API and git tags for the highest previously published release.
   - Computes `versionCode = highestVersionCode + 1` (mathematically guaranteed to be strictly greater than previous).
   - Computes semantic `versionName` (e.g. `1.0.0` -> `1.0.1` -> `1.0.2`).
   - Ensures release tag uniqueness (avoids collisions).
3. **Dynamic Gradle Injection**:
   - Compiles `./gradlew assembleRelease -PversionCode=<resolved> -PversionName=<resolved>`.
   - Embeds the new version into `BuildConfig.VERSION_CODE`, `BuildConfig.VERSION_NAME`, and Android Manifest.
4. **Integrity & Release**:
   - Computes SHA-256 checksum and produces `update_manifest.json`.
   - Publishes GitHub Release `v<versionName>` with `LiveWaterPaper-v<versionName>.apk` and metadata.
5. **Installed App Detection**:
   - Installed apps detect that `remote.versionCode > local.versionCode`.
   - Prompts the user with the glassmorphic update card.
   - Downloads and installs the new update with zero data or settings loss!

---

## 4. Manual Major / Minor Version Bumps (Optional)

If you ever wish to advance to a major release (e.g. version `2.0.0`) or minor release (e.g. `1.1.0`), simply set the base numbers in [`android/version.properties`](file:///android/version.properties):

```properties
MAJOR=2
MINOR=0
PATCH=0
MINIMUM_VERSION_CODE=1
```
The version resolver will automatically respect this baseline, transitioning releases to `2.0.0`, `2.0.1`, etc., while continuing to strictly increment `versionCode` monotonically.

---

## 4. Key Components Implemented

| Component | File Path | Description |
| :--- | :--- | :--- |
| **Data Model** | [`UpdateInfo.kt`](file:///android/app/src/main/java/com/waterlivewallpaper/updater/UpdateInfo.kt) | Encapsulates version code, version name, APK URL, SHA-256, and release notes. |
| **API Checker** | [`UpdateChecker.kt`](file:///android/app/src/main/java/com/waterlivewallpaper/updater/UpdateChecker.kt) | Queries GitHub Releases API on `Dispatchers.IO`, parses assets and `update_manifest.json`. |
| **Downloader** | [`UpdateDownloader.kt`](file:///android/app/src/main/java/com/waterlivewallpaper/updater/UpdateDownloader.kt) | Handles HTTP redirects, streams progress, and verifies SHA-256 hash. |
| **Installer** | [`UpdateInstaller.kt`](file:///android/app/src/main/java/com/waterlivewallpaper/updater/UpdateInstaller.kt) | Manages Android 8.0+ `REQUEST_INSTALL_PACKAGES` and `FileProvider` intents. |
| **Coordinator** | [`UpdateManager.kt`](file:///android/app/src/main/java/com/waterlivewallpaper/updater/UpdateManager.kt) | State machine managing check, download, caching, and install flows. |
| **UI Dialog** | [`dialog_update.xml`](file:///android/app/src/main/res/layout/dialog_update.xml) | Material card with title, release notes, progress bar, and action buttons. |
| **FileProvider** | [`file_paths.xml`](file:///android/app/src/main/res/xml/file_paths.xml) | Secure content provider path mapping for app cache directory. |
| **CI/CD** | [`android-release.yml`](file:///.github/workflows/android-release.yml) | GitHub Actions workflow for building, signing, and releasing APKs. |
| **Unit Tests** | [`updater.test.js`](file:///tests/updater/updater.test.js) | Automated validation for manifest parsing, version codes, and SHA-256. |

---

## 5. Permissions & Android 8.0+ (API 26+) Handling

- **Internet Access**: `<uses-permission android:name="android.permission.INTERNET" />` and `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` added to `AndroidManifest.xml`.
- **Install Permission**: `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`.
- **User Grant**: The first time an update is installed, Android prompts the user to allow installing apps from Live WaterPaper. `UpdateInstaller` automatically detects if this permission is needed and directs the user straight to the system toggle screen. Once toggled, the app resumes installation immediately.

---

## 6. Offline & Edge Cases

- **No Internet**: If the device is offline, `UpdateChecker` handles the connection timeout gracefully without crashing.
- **Corrupted Download**: If a network interruption corrupts the APK download, `UpdateDownloader` detects the SHA-256 checksum mismatch, deletes the corrupted file, and reports an error so the user can retry.
- **Rollback / Same Version**: If GitHub has a version with `versionCode <= local.versionCode`, the app indicates it is already up to date and does not trigger downloads.
- **Preserved Wallpaper State**: The wallpaper service runs independently from the main activity. Applying an update retains all custom colors, volume levels, and sensor preferences saved in `WallpaperPreferences`.
