# Live WaterPaper - System Architecture

This document describes the architectural design and separation of responsibilities between the **Application Layer (React Native + Expo)**, the **Native Android Wallpaper Service (Kotlin)**, the **Fluid Simulation Engine**, and the **OpenGL ES Graphics Pipeline**.

---

## 1. High-Level Architectural Diagram

```
+-------------------------------------------------------------------------+
|                         USER INTERACTION LAYER                          |
|  +--------------------+  +--------------------+  +--------------------+ |
|  |     Home Screen    |  | Interactive Preview|  |  Customize Screen  | |
|  | (Stats HUD, Actions)|  | (Fullscreen Touch) |  | (Colors & Sliders) | |
|  +--------------------+  +--------------------+  +--------------------+ |
|                                   |                                     |
|                                   v                                     |
|                       React WallpaperContext State                      |
+-----------------------------------|-------------------------------------+
                                    | Native Bridge
                                    v
+-------------------------------------------------------------------------+
|                      NATIVE ANDROID BRIDGE LAYER                        |
|  - WaterWallpaperModule: Handles IPC, Intent dispatch, Diagnostics      |
|  - WallpaperPreferences: SharedPreferences store (Syncs RN <-> Native) |
|  - WaterPreviewGlView / Manager: Embeds hardware GL inside RN UI        |
+-----------------------------------|-------------------------------------+
                                    | OnSharedPreferenceChangeListener
                                    v
+-------------------------------------------------------------------------+
|                  NATIVE ANDROID LIVE WALLPAPER ENGINE                   |
|                                                                         |
|  +-------------------------------------------------------------------+  |
|  | WaterWallpaperService (Android WallpaperService & Engine)         |  |
|  | - Observes Surface lifecycle: onSurfaceCreated/Changed/Destroyed  |  |
|  | - Manages display power: onVisibilityChanged (Screen off -> sleep)|  |
|  | - Touch event routing: onTouchEvent                              |  |
|  +-------------------------------------------------------------------+  |
|               |                                      |                  |
|               v                                      v                  |
|  +-------------------------+            +----------------------------+  |
|  | DeviceOrientationSensor |            |     WaterGlRenderer        |  |
|  | - TYPE_ROTATION_VECTOR  |            | - Dedicated Render Thread  |  |
|  | - TYPE_ACCELEROMETER    |            | - EGL14 Surface & Context  |  |
|  | - Low-Pass Filter       |            | - Rate-limited to target   |  |
|  | - Coordinate Transform  |            |   FPS (30, 60, 90, 120 Hz) |  |
|  | - Dynamic Shake Impulse |            +--------------|-------------+  |
|  +------------|------------+                           |                |
|               | (gravity & impulse)                    |                |
|               v                                        v                |
|  +-------------------------------------------------------------------+  |
|  |                    FluidSimulationEngine                          |  |
|  | - SPH Density & Pressure Solver (Tait Equation)                   |  |
|  | - Internal Viscosity & Boundary Damping                           |  |
|  | - Dynamic Droplet Separation on high shear or splash              |  |
|  | - Droplet Ballistic Trajectory & Collision Merging                |  |
|  | - Direct FloatBuffer population for OpenGL ES                     |  |
|  +-------------------------------------------------------------------+  |
|                                    |                                    |
|                                    v                                    |
|  +-------------------------------------------------------------------+  |
|  |                   OpenGL ES 3.0 / 2.0 Shaders                     |  |
|  | - Vertex Shader: Clip-space point sizing & UV projection          |  |
|  | - Fragment Shader: Radial metaball core, specular highlight,      |  |
|  |   and exponential luminous edge bloom                             |  |
|  +-------------------------------------------------------------------+  |
+-------------------------------------------------------------------------+
```

---

## 2. Separation of Responsibilities

### A. React Native Application (`app/` & `src/`)
- **Single Source of UI State**: Provides an intuitive, responsive interface for customizing fluid parameters (water color, secondary accent, background type, water volume, physics sensitivity, shake sensitivity, glow intensity, target frame rate).
- **Interactive In-App Preview**:
  - Automatically selects `WaterPreviewGlView` (native OpenGL view) when compiled into Android.
  - Transparently falls back to a high-performance JavaScript/Canvas simulation when running in Expo Go or Web.
- **Intent Dispatch**: Invokes `WallpaperBridge.openWallpaperChooser()` to launch Android's system wallpaper dialog.
- **Zero Heavy Math on JS Thread**: All continuous physics calculations and rendering in the live wallpaper run 100% inside native Kotlin and OpenGL threads.

### B. Native Android Wallpaper Service (`WaterWallpaperService.kt`)
- Extends `android.service.wallpaper.WallpaperService`.
- Maintains the Android live wallpaper lifecycle:
  - `onSurfaceCreated(holder)`: Obtains the native window surface.
  - `onSurfaceChanged(holder, format, width, height)`: Informs the fluid simulation of screen dimensions and triggers EGL viewport resize.
  - `onSurfaceDestroyed(holder)`: Gracefully stops the render thread and releases EGL resources.
  - `onVisibilityChanged(visible)`:
    - **Visible (`true`)**: Registers sensor listeners, starts the EGL render thread, aligns coordinate orientation to the display rotation.
    - **Hidden (`false`)**: Unregisters all sensor listeners and suspends the render loop immediately. This guarantees **0% CPU and 0% GPU usage** when the screen is turned off or when another app is in the foreground.
  - `onTouchEvent(event)`: Propagates touch slosh events to the fluid solver when user touches their home screen.

### C. Fluid Simulation Engine (`FluidSimulationEngine.kt`)
- **Particle State**: Tracks position $(x, y)$, velocity $(v_x, v_y)$, radius $r$, density $\rho$, pressure $p$, droplet flag, and lifetime.
- **Quasi-Incompressible Fluid**:
  1. Computes local particle densities using a cubic polynomial kernel.
  2. Solves pressure using the Tait equation:
     $$p = k \cdot (\rho - \rho_0)$$
  3. Evaluates pressure gradients and symmetric viscous shear forces between particle pairs.
- **Splashes & Droplet Formation**:
  - Droplets are dynamically formed when local velocity exceeds the detachment threshold or when surface tension shears off energetic liquid.
  - Droplets fly independently under gravity and air resistance.
- **Droplet Merging**:
  - When a droplet touches a nearby fluid particle within `dropletMergeRadius`, it seamlessly rejoins the fluid pool, transferring its momentum and restoring its full liquid radius.
- **Direct GPU Buffer Pipeline**:
  - Directly populates a pre-allocated native `FloatBuffer` with 4 floats per particle `[x, y, radius, isDroplet]`, avoiding intermediate object allocations in the hot loop.

### D. Rendering Engine & Shaders (`WaterGlRenderer.kt` & `Shaders.kt`)
- **EGL14 Architecture**: Creates an OpenGL ES 2.0/3.0 context and window surface bound directly to the Android `SurfaceHolder`.
- **GLSL Shaders**:
  - **Vertex Shader**: Maps pixel coordinates $(0 \dots W, 0 \dots H)$ to OpenGL clip space $(-1 \dots 1)$, adjusting point sizes dynamically based on radius and glow intensity.
  - **Fragment Shader**:
    - **Core**: Evaluates radial distance from center. Within the core radius ($dist \le 1.0$), renders anti-aliased liquid with a directional specular highlight at $(-0.25, -0.25)$ and smooth color gradient from secondary to primary cyan.
    - **Bloom**: Outside the core ($dist > 1.0$), computes an exponential decay curve to render the luminous cyan glow characteristic of Live WaterPaper.
- **V-Sync & Frame-Pacing**: Synchronizes with the target refresh rate (30, 60, 90, or 120 FPS on iQOO Neo 6).

---

## 3. Communication Bridge (`WaterWallpaperModule.kt`)

The bridge connects React Native and native Android via asynchronous promises:

| Method | Return Type | Description |
| :--- | :--- | :--- |
| `isWallpaperServiceSupported()` | `Promise<boolean>` | Verifies `FEATURE_LIVE_WALLPAPER` availability on the device. |
| `isLiveWallpaperActive()` | `Promise<boolean>` | Checks `WallpaperManager.wallpaperInfo` to verify if Live WaterPaper is set. |
| `openWallpaperChooser()` | `Promise<boolean>` | Dispatches `ACTION_CHANGE_LIVE_WALLPAPER` with fallback to `ACTION_LIVE_WALLPAPER_CHOOSER`. |
| `getSavedSettings()` | `Promise<string>` | Retrieves persisted JSON configuration from `SharedPreferences`. |
| `saveSettings(json)` | `Promise<boolean>` | Persists updated settings; notifies active wallpaper engine immediately. |
| `resetToDefaults()` | `Promise<string>` | Restores authentic Live WaterPaper default settings. |
| `getDeviceSensorInfo()` | `Promise<object>` | Returns hardware diagnostics (sensors, OpenGL support, device model, Android OS). |

---

## 4. Configuration Synchronization

Settings are persisted in Android's private `SharedPreferences` (`live_waterpaper_settings`). Both the React Native UI and the background `WaterWallpaperService` share this store.

When a user modifies a setting in the React Native UI:
1. `WallpaperBridge.saveSettings(json)` updates the `SharedPreferences`.
2. The `WaterWallpaperService`'s `OnSharedPreferenceChangeListener` triggers on the main thread.
3. The running simulation engine updates its parameters (e.g. water volume, sensitivity, color) on the fly without restarting the service or causing frame stutters.
