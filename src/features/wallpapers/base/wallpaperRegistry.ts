import { PresetTheme, PRESET_THEMES } from './wallpaper';

export interface WallpaperCatalogueItem {
  id: string;
  title: string;
  tagline: string;
  category: 'liquid' | 'space' | 'abstract' | 'nature';
  isInstalled: boolean;
  isAvailable: boolean;
  version: string;
  route: string;
  presets: PresetTheme[];
}

export const WALLPAPER_CATALOGUE: WallpaperCatalogueItem[] = [
  {
    id: 'live-waterpaper',
    title: 'Live WaterPaper',
    tagline: 'Realistic SPH fluid dynamics with device sensor tilt and incompressibility physics',
    category: 'liquid',
    isInstalled: true,
    isAvailable: true,
    version: '1.0.2',
    route: '/preview',
    presets: PRESET_THEMES,
  },
  {
    id: 'floating-sticks-wallpaper',
    title: 'Floating Sticks Wallpaper',
    tagline: 'Authentic 3D wooden driftwood twigs floating with tilt, shake, and collision physics',
    category: 'nature',
    isInstalled: true,
    isAvailable: true,
    version: '1.0.0',
    route: '/preview?type=sticks',
    presets: PRESET_THEMES,
  },
  {
    id: 'cosmic-nebula',
    title: 'Cosmic Nebula',
    tagline: 'Deep space interstellar dust clouds with volumetric star parallax',
    category: 'space',
    isInstalled: false,
    isAvailable: false,
    version: 'Upcoming',
    route: '/coming-soon',
    presets: [],
  },
  {
    id: 'molten-magma',
    title: 'Molten Magma',
    tagline: 'Viscous high-density volcanic flow with incandescent embers',
    category: 'liquid',
    isInstalled: false,
    isAvailable: false,
    version: 'Upcoming',
    route: '/coming-soon',
    presets: [],
  },
  {
    id: 'matrix-rain',
    title: 'Matrix Digital Rain',
    tagline: 'Cascading phosphorescent glyphs with gyro-reactive perspective shifts',
    category: 'abstract',
    isInstalled: false,
    isAvailable: false,
    version: 'Upcoming',
    route: '/coming-soon',
    presets: [],
  },
];
