import React, { createContext, useContext, useEffect, useState } from 'react';
import {
  DEFAULT_WALLPAPER_SETTINGS,
  DeviceCompatibility,
  PresetTheme,
  WallpaperSettings,
} from '../features/wallpapers/base/wallpaper';
import { WallpaperBridge } from '../core/services/WallpaperBridge';

interface WallpaperContextValue {
  settings: WallpaperSettings;
  compatibility: DeviceCompatibility | null;
  isLiveWallpaperActive: boolean;
  isLoading: boolean;
  updateSettings: (partial: Partial<WallpaperSettings>) => Promise<void>;
  applyPreset: (preset: PresetTheme) => Promise<void>;
  resetDefaults: () => Promise<void>;
  openWallpaperChooser: () => Promise<boolean>;
  refreshStatus: () => Promise<void>;
}

const WallpaperContext = createContext<WallpaperContextValue | undefined>(undefined);

export const WallpaperProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [settings, setSettings] = useState<WallpaperSettings>(DEFAULT_WALLPAPER_SETTINGS);
  const [compatibility, setCompatibility] = useState<DeviceCompatibility | null>(null);
  const [isLiveWallpaperActive, setIsLiveWallpaperActive] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const refreshStatus = async () => {
    try {
      const [saved, compat, active] = await Promise.all([
        WallpaperBridge.loadSettings(),
        WallpaperBridge.getCompatibility(),
        WallpaperBridge.isWallpaperActive(),
      ]);
      setSettings(saved);
      setCompatibility(compat);
      setIsLiveWallpaperActive(active);
    } catch (e) {
      console.warn('Error loading wallpaper context:', e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    refreshStatus();
  }, []);

  const updateSettings = async (partial: Partial<WallpaperSettings>) => {
    const updated = { ...settings, ...partial };
    setSettings(updated);
    await WallpaperBridge.saveSettings(updated);
  };

  const applyPreset = async (preset: PresetTheme) => {
    const updated: WallpaperSettings = {
      ...settings,
      waterColorHex: preset.waterColor,
      waterColorSecondaryHex: preset.secondaryColor,
      backgroundType: preset.backgroundType,
      backgroundColorHex: preset.backgroundColor,
      glowIntensity: preset.glowIntensity,
    };
    setSettings(updated);
    await WallpaperBridge.saveSettings(updated);
  };

  const resetDefaults = async () => {
    const reset = await WallpaperBridge.resetSettings();
    setSettings(reset);
  };

  const openWallpaperChooser = async (): Promise<boolean> => {
    const res = await WallpaperBridge.openWallpaperChooser();
    setTimeout(refreshStatus, 1500);
    return res;
  };

  return (
    <WallpaperContext.Provider
      value={{
        settings,
        compatibility,
        isLiveWallpaperActive,
        isLoading,
        updateSettings,
        applyPreset,
        resetDefaults,
        openWallpaperChooser,
        refreshStatus,
      }}
    >
      {children}
    </WallpaperContext.Provider>
  );
};

export const useWallpaper = (): WallpaperContextValue => {
  const ctx = useContext(WallpaperContext);
  if (!ctx) {
    throw new Error('useWallpaper must be used within a WallpaperProvider');
  }
  return ctx;
};
