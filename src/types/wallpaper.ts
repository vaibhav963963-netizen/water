export type BackgroundType = 'light' | 'dark' | 'custom_color' | 'custom_image';
export type QualityLevel = 'low' | 'balanced' | 'high';

export interface WallpaperSettings {
  waterColorHex: string;
  waterColorSecondaryHex: string;
  backgroundType: BackgroundType;
  backgroundColorHex: string;
  backgroundImageUri: string | null;
  waterVolume: number; // 0.1 to 1.0, fraction of screen capacity
  physicsSensitivity: number; // 0.5 to 2.5 multiplier
  shakeSensitivity: number; // 0.5 to 2.5 multiplier
  glowIntensity: number; // 0.0 to 1.0
  qualityLevel: QualityLevel;
  targetFps: number; // 30, 60, 90, 120
  touchInteractionsEnabled: boolean;
  sensorPollingRateHz: number; // 30, 60, 100
}

export interface PresetTheme {
  id: string;
  name: string;
  tagline: string;
  waterColor: string;
  secondaryColor: string;
  backgroundType: BackgroundType;
  backgroundColor: string;
  glowIntensity: number;
}

export interface DeviceCompatibility {
  isWallpaperServiceSupported: boolean;
  isRotationVectorSupported: boolean;
  isAccelerometerSupported: boolean;
  isGLES3Supported: boolean;
  isLiveWallpaperCurrentlyActive: boolean;
  deviceModel: string;
  androidVersion: string;
}

export interface FluidParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  density: number;
  pressure: number;
  isDroplet: boolean;
  lifetime: number;
}

export interface SimulationState {
  particleCount: number;
  dropletCount: number;
  gravityX: number;
  gravityY: number;
  fps: number;
  isSettled: boolean;
}

export const DEFAULT_WALLPAPER_SETTINGS: WallpaperSettings = {
  waterColorHex: '#00E5FF', // Vibrant reference cyan
  waterColorSecondaryHex: '#00B0FF',
  backgroundType: 'light',
  backgroundColorHex: '#F2F5F8', // Crisp clean light background
  backgroundImageUri: null,
  waterVolume: 0.38,
  physicsSensitivity: 1.25,
  shakeSensitivity: 1.1,
  glowIntensity: 0.65,
  qualityLevel: 'balanced',
  targetFps: 60,
  touchInteractionsEnabled: true,
  sensorPollingRateHz: 60,
};

export const PRESET_THEMES: PresetTheme[] = [
  {
    id: 'original-cyan',
    name: 'Original Cyan',
    tagline: 'Matches the authentic Live WaterPaper look',
    waterColor: '#00E5FF',
    secondaryColor: '#00B4D8',
    backgroundType: 'light',
    backgroundColor: '#F3F6FA',
    glowIntensity: 0.70,
  },
  {
    id: 'deep-ocean',
    name: 'Deep Ocean',
    tagline: 'Rich azure tones with midnight depth',
    waterColor: '#0288D1',
    secondaryColor: '#01579B',
    backgroundType: 'dark',
    backgroundColor: '#0F172A',
    glowIntensity: 0.55,
  },
  {
    id: 'neon-emerald',
    name: 'Neon Emerald',
    tagline: 'High-contrast bioluminescent green',
    waterColor: '#00E676',
    secondaryColor: '#00B0FF',
    backgroundType: 'dark',
    backgroundColor: '#111827',
    glowIntensity: 0.80,
  },
  {
    id: 'coral-sunset',
    name: 'Coral Sunset',
    tagline: 'Warm amber and liquid coral blend',
    waterColor: '#FF5252',
    secondaryColor: '#FF7043',
    backgroundType: 'light',
    backgroundColor: '#FFF8E1',
    glowIntensity: 0.60,
  },
  {
    id: 'amethyst-flow',
    name: 'Amethyst Glow',
    tagline: 'Mystic electric purple fluid',
    waterColor: '#D500F9',
    secondaryColor: '#7C4DFF',
    backgroundType: 'dark',
    backgroundColor: '#1E1B4B',
    glowIntensity: 0.75,
  },
];
