# Live WaterPaper - Real Water Live Wallpaper for Android

A production-ready Android Live Wallpaper application reproducing the organic, gravity-driven fluid dynamics of the original **Live WaterPaper** app. Featuring vibrant cyan/turquoise liquid, soft luminous boundaries, real-time sloshing, splash droplet detachment, merging, surface tension approximations, and native Android `WallpaperService` integration.

---

## 🌊 Overview & Reference Aesthetic

- **Visual Look**: Glowing cyan/turquoise liquid (`#00E5FF`) on a clean light background (`#F2F5F8`) matching the reference screenshots.
- **Physical Dynamics**: Real-time fluid simulation driven by device orientation (accelerometer + rotation vector), tilting slosh, dynamic linear acceleration (inertia), splash detachment of droplets, and seamless droplet merging.
- **Hybrid Architecture**:
  - **App Layer**: React Native + Expo with TypeScript & Expo Router for settings, live interactive preview, and configuration.
  - **Native Engine**: Kotlin + Android `WallpaperService` + OpenGL ES shaders running on a dedicated thread independent of the React Native Activity.

---

## 📱 Features

1. **True Android Live Wallpaper (`WallpaperService`)**:
   - Native service registration with `@xml/wallpaper` metadata and thumbnail.
   - Home screen and lock screen support via Android's official wallpaper picker intent (`ACTION_CHANGE_LIVE_WALLPAPER`).
   - Runs independently when the React Native app is closed.
2. **Real-time Fluid Simulation Engine**:
   - SPH (Smoothed Particle Hydrodynamics) with Tait equation for quasi-incompressible liquid.
   - Spatial interaction, viscosity, and surface tension approximations.
   - Dynamic droplet detachment on vigorous tilting, shaking, or high surface shear.
   - Droplet ballistic motion and momentum-conserving re-merging into the main pool.
3. **Hardware-Accelerated OpenGL ES Rendering**:
   - Custom GLSL vertex and fragment shaders.
   - Luminous soft glow bloom around water boundaries.
   - Directional specular highlights for realistic water glint.
   - Multisampling anti-aliasing and smooth alpha blending.
4. **Interactive In-App Preview**:
   - Real-time simulation preview in the app with interactive touch sloshing and device tilt.
   - Fullscreen preview mode with HUD showing real-time FPS, particle count, and droplet count.
5. **Deep Customization**:
   - **Liquid Color**: Authentic Live WaterPaper Cyan, Ocean Blue, Neon Emerald, Coral Sunset, Amethyst Glow, or custom HEX.
   - **Backgrounds**: Light (reference), Dark Slate, or Custom Color Tint.
   - **Physics Sliders**: Water Volume, Gravity Sensitivity, Shake & Splash Sensitivity, Luminous Glow Intensity.
6. **Battery & Resource Safeguards**:
   - Stops 100% of GPU rendering and sensor listeners when the display is off or covered by another application.
   - Configurable quality profiles: **Battery Saver** (30 FPS), **Balanced** (60 FPS), and **Ultra Smooth** (90–120 FPS high refresh rate targeting iQOO Neo 6).

---

## 🛠 Project Structure

```
├── android/
│   ├── app/
│   │   ├── src/main/AndroidManifest.xml      # WallpaperService & permissions
│   │   ├── src/main/res/xml/wallpaper.xml    # Android wallpaper metadata
│   │   └── src/main/java/com/waterlivewallpaper/
│   │       ├── WaterWallpaperService.kt      # Native WallpaperService & Engine
│   │       ├── gl/                           # OpenGL ES renderer & GLSL shaders
│   │       ├── simulation/                   # Native SPH fluid simulation engine
│   │       ├── sensors/                      # Rotation vector & accelerometer manager
│   │       ├── config/                       # SharedPreferences configuration store
│   │       └── bridge/                       # React Native native module & GL preview
│   └── build.gradle
├── app/                                      # Expo Router screens
│   ├── _layout.tsx                           # Global provider & navigation stack
│   ├── index.tsx                             # Home screen with live preview & actions
│   ├── preview.tsx                           # Fullscreen interactive preview & HUD
│   ├── customize.tsx                         # Liquid color, background, & physics sliders
│   └── settings.tsx                          # Performance modes, diagnostics, & tips
├── src/
│   ├── components/                           # WaterSimulationView, SettingSlider, ColorPicker
│   ├── physics/                              # TypeScript FluidEngine & SensorFilter
│   ├── services/                             # WallpaperBridge (NativeModule bridge)
│   ├── state/                                # WallpaperContext (React Context)
│   ├── theme/                                # Design system & colors
│   └── types/                                # Strict TypeScript interfaces
└── tests/                                    # Unit & simulation tests
```

---

## 🚀 Getting Started

### Prerequisites

- **Node.js**: v18+ (tested on Node v22)
- **Java Development Kit**: JDK 17 or JDK 21
- **Android SDK**: API level 34 or 35 with Android Build Tools
- **Physical Device**: Android phone with accelerometer (e.g. iQOO Neo 6) or Android Emulator

### Installation

```bash
# 1. Install dependencies
npm install

# 2. Run unit and simulation tests
npm test
```

### Running Development Build

```bash
# Start Metro bundler
npm run start

# Launch on connected Android device
npm run android
```

---

## 📦 Building the Android APK

### Building Debug APK

```bash
cd android
./gradlew assembleDebug
```
The output APK is generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Building Release APK

```bash
cd android
./gradlew assembleRelease
```
The output APK is generated at:
`android/app/build/outputs/apk/release/app-release.apk`

### Installing to iQOO Neo 6 via ADB

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 📲 How to Apply Live WaterPaper

1. Open the **Live WaterPaper** app on your phone.
2. Tilt your phone and tap the screen to test the water simulation in the interactive preview.
3. Tap **Set as Wallpaper**.
4. The system Android Live Wallpaper preview dialog will appear.
5. Tap **Apply** (or **Set wallpaper**).
6. Select **Home screen** or **Home screen and lock screen**.
7. Return to your home screen — the water is now live! You can close the app completely; the native engine runs independently.

---

## 💡 Troubleshooting on iQOO / FuntouchOS / OriginOS

- **Lock Screen Support**: On some FuntouchOS / OriginOS launchers, third-party live wallpapers on the lock screen require confirming "Home screen and lock screen" during the initial Android prompt. If it only applies to the home screen, open phone **Settings → Lock screen & wallpaper → Live wallpapers** and select Live WaterPaper.
- **Battery Optimization**: The wallpaper is already designed with strict lifecycle awareness (0% CPU/GPU when screen is off). Ensure background battery restrictions are set to "Optimized" or "Don't restrict" in App Info so the wallpaper service is not killed by aggressive task managers.

---

## 📄 License & Privacy

- **100% Offline**: No network permissions or internet access required.
- **Privacy First**: Zero analytics, zero trackers, and no personal data collection.
