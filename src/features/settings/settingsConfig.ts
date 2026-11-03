import { QualityLevel } from '../wallpapers/base/wallpaper';

export interface PerformanceOption {
  key: QualityLevel;
  label: string;
  fps: number;
  particles: number;
  description: string;
}

export const PERFORMANCE_OPTIONS: PerformanceOption[] = [
  {
    key: 'low',
    label: 'Battery Saver',
    fps: 30,
    particles: 60,
    description: 'Reduced particle count and 30 FPS cap for maximum battery longevity',
  },
  {
    key: 'balanced',
    label: 'Balanced',
    fps: 60,
    particles: 100,
    description: 'Silky 60 FPS fluid simulation optimized for everyday performance',
  },
  {
    key: 'high',
    label: 'Ultra Physics',
    fps: 90,
    particles: 140,
    description: 'Maximum droplet fidelity, high-density metaball rendering and high refresh rate',
  },
];
