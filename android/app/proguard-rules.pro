# Add project specific ProGuard rules here.

-keep class com.waterlivewallpaper.WaterWallpaperService { *; }
-keep class com.waterlivewallpaper.bridge.** { *; }
-keep class com.waterlivewallpaper.gl.** { *; }
-keep class com.waterlivewallpaper.simulation.** { *; }
-keep class com.waterlivewallpaper.sensors.** { *; }
-keep class com.waterlivewallpaper.config.** { *; }

# React Native rules
-keep class com.facebook.react.** { *; }
-keep class com.facebook.jni.** { *; }
