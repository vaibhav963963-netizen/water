import { NativeModules, Platform } from 'react-native';
import {
  DEFAULT_WALLPAPER_SETTINGS,
  DeviceCompatibility,
  WallpaperSettings,
} from '../types/wallpaper';

interface NativeWaterWallpaperInterface {
  isWallpaperServiceSupported(): Promise<boolean>;
  isLiveWallpaperActive(): Promise<boolean>;
  openWallpaperChooser(): Promise<boolean>;
  getSavedSettings(): Promise<string>;
  saveSettings(settingsJson: string): Promise<boolean>;
  resetToDefaults(): Promise<string>;
  getDeviceSensorInfo(): Promise<DeviceCompatibility>;
}

const { WaterWallpaperModule } = NativeModules as {
  WaterWallpaperModule?: NativeWaterWallpaperInterface;
};

class WallpaperBridgeService {
  private cachedSettings: WallpaperSettings = { ...DEFAULT_WALLPAPER_SETTINGS };

  public isNativeModuleAvailable(): boolean {
    return Platform.OS === 'android' && !!WaterWallpaperModule;
  }

  public async getCompatibility(): Promise<DeviceCompatibility> {
    if (this.isNativeModuleAvailable() && WaterWallpaperModule) {
      try {
        return await WaterWallpaperModule.getDeviceSensorInfo();
      } catch (err) {
        console.warn('Failed to query native device info:', err);
      }
    }

    return {
      isWallpaperServiceSupported: Platform.OS === 'android',
      isRotationVectorSupported: true,
      isAccelerometerSupported: true,
      isGLES3Supported: true,
      isLiveWallpaperCurrentlyActive: false,
      deviceModel: Platform.OS === 'android' ? 'Android Device' : 'Simulator / Web',
      androidVersion: Platform.OS === 'android' ? `${Platform.Version}` : 'N/A',
    };
  }

  public async isWallpaperActive(): Promise<boolean> {
    if (this.isNativeModuleAvailable() && WaterWallpaperModule) {
      try {
        return await WaterWallpaperModule.isLiveWallpaperActive();
      } catch (e) {
        return false;
      }
    }
    return false;
  }

  public async openWallpaperChooser(): Promise<boolean> {
    if (this.isNativeModuleAvailable() && WaterWallpaperModule) {
      try {
        return await WaterWallpaperModule.openWallpaperChooser();
      } catch (err) {
        console.error('Error opening wallpaper chooser:', err);
        return false;
      }
    }
    return false;
  }

  public async loadSettings(): Promise<WallpaperSettings> {
    if (this.isNativeModuleAvailable() && WaterWallpaperModule) {
      try {
        const json = await WaterWallpaperModule.getSavedSettings();
        if (json) {
          const parsed = JSON.parse(json);
          this.cachedSettings = { ...DEFAULT_WALLPAPER_SETTINGS, ...parsed };
          return this.cachedSettings;
        }
      } catch (err) {
        console.warn('Could not read native settings:', err);
      }
    }
    return this.cachedSettings;
  }

  public async saveSettings(settings: WallpaperSettings): Promise<boolean> {
    this.cachedSettings = { ...settings };
    if (this.isNativeModuleAvailable() && WaterWallpaperModule) {
      try {
        const json = JSON.stringify(settings);
        return await WaterWallpaperModule.saveSettings(json);
      } catch (err) {
        console.error('Could not save native settings:', err);
        return false;
      }
    }
    return true;
  }

  public async resetSettings(): Promise<WallpaperSettings> {
    this.cachedSettings = { ...DEFAULT_WALLPAPER_SETTINGS };
    if (this.isNativeModuleAvailable() && WaterWallpaperModule) {
      try {
        const json = await WaterWallpaperModule.resetToDefaults();
        if (json) {
          this.cachedSettings = JSON.parse(json);
        }
      } catch (err) {
        console.warn('Could not reset native settings:', err);
      }
    }
    return this.cachedSettings;
  }
}

export const WallpaperBridge = new WallpaperBridgeService();
