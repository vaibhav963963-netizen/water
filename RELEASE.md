# Live WaterPaper - Release Engineering & APK Build Guide

This document describes how to generate release APKs and Android App Bundles (AAB) for Live WaterPaper.

---

## 1. Prerequisites

- **Java Development Kit**: JDK 17 (or JDK 21)
- **Android SDK**: `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`
- Environment variables configured:
  ```bash
  export ANDROID_HOME=$HOME/Android/Sdk
  export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
  ```

---

## 2. Release Signing Configuration

### 2.1 Generating a Production Keystore
If you do not already have an upload keystore, generate one using `keytool`:

```bash
keytool -genkeypair -v -keystore water-release.keystore -alias waterpaper-key -keyalg RSA -keysize 2048 -validity 10000
```

Store `water-release.keystore` securely in `android/app/` (or an external secret manager) and **never commit it to public version control**.

### 2.2 Configuring Gradle Signing
In `android/gradle.properties`:

```properties
WATER_RELEASE_STORE_FILE=water-release.keystore
WATER_RELEASE_KEY_ALIAS=waterpaper-key
WATER_RELEASE_STORE_PASSWORD=your_store_password
WATER_RELEASE_KEY_PASSWORD=your_key_password
```

In `android/app/build.gradle`:

```groovy
signingConfigs {
    release {
        storeFile file(WATER_RELEASE_STORE_FILE)
        storePassword WATER_RELEASE_STORE_PASSWORD
        keyAlias WATER_RELEASE_KEY_ALIAS
        keyPassword WATER_RELEASE_KEY_PASSWORD
    }
}
```

---

## 3. Build Commands

### Step 1: Clean Build
```bash
cd android
./gradlew clean
```

### Step 2: Build Debug APK
For rapid sideloading and testing on iQOO Neo 6:

```bash
./gradlew assembleDebug
```
Output location:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Step 3: Build Production Release APK
```bash
./gradlew assembleRelease
```
Output location:
`android/app/build/outputs/apk/release/app-release.apk`

### Step 4: Build Google Play Store App Bundle (AAB)
```bash
./gradlew bundleRelease
```
Output location:
`android/app/build/outputs/bundle/release/app-release.aab`

---

## 4. Installing on Physical Hardware (iQOO Neo 6)

1. Enable **Developer Options** and **USB Debugging** on your phone:
   - Go to **Settings → About phone → Software information**.
   - Tap **Build number** 7 times.
   - Go to **Settings → System → Developer options → Enable USB debugging**.
2. Connect your phone via USB and check connection:
   ```bash
   adb devices
   ```
3. Install the APK:
   ```bash
   adb install -r android/app/build/outputs/apk/release/app-release.apk
   ```

---

## 5. Release Verification Checklist

- [x] Application ID verified: `com.waterlivewallpaper`.
- [x] Version Name: `1.0.0`, Version Code: `1`.
- [x] `WaterWallpaperService` registered with `android.permission.BIND_WALLPAPER`.
- [x] Live wallpaper metadata defined in `res/xml/wallpaper.xml`.
- [x] Vector thumbnail available at `res/drawable/wallpaper_thumb.xml`.
- [x] Proguard rules applied for production release.
- [x] Offline compliance: Zero network permissions, zero tracking SDKs.
- [x] 100% automated test pass rate verified.
- [x] EGL context destruction & sensor unregister verified on display sleep.
