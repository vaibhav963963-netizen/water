export interface DashboardModule {
  id: string;
  title: string;
  subtitle: string;
  description: string;
  stickerKey: 'waterDrop' | 'notebook' | 'camera' | 'folder' | 'toolbox';
  badge: string;
  badgeType: 'active' | 'in_dev' | 'new';
  route: string;
  isAvailable: boolean;
}

export const DASHBOARD_MODULES: DashboardModule[] = [
  {
    id: 'wallpapers',
    title: 'Live Wallpapers',
    subtitle: 'SPH Fluid & Interactive Physics',
    description: 'Explore ultra-fluid interactive wallpapers with real-time gyroscopic response, incompressibility, and custom background options.',
    stickerKey: 'waterDrop',
    badge: 'LIVE ENGINE',
    badgeType: 'active',
    route: '/preview',
    isAvailable: true,
  },
  {
    id: 'notes',
    title: 'Personal Notes',
    subtitle: 'Private Memos & Thoughts',
    description: 'A private space for your thoughts, checklists, and quick memos with fluid visual dynamics is in active development.',
    stickerKey: 'notebook',
    badge: 'COMING SOON',
    badgeType: 'in_dev',
    route: '/coming-soon?module=notes',
    isAvailable: false,
  },
  {
    id: 'photos',
    title: 'Favourite Photos',
    subtitle: 'Curated Liquid Memories',
    description: 'Curate and preserve your favourite photo memories paired with realistic liquid distortion and depth effects.',
    stickerKey: 'camera',
    badge: 'COMING SOON',
    badgeType: 'in_dev',
    route: '/coming-soon?module=photos',
    isAvailable: false,
  },
  {
    id: 'collections',
    title: 'Personal Collections',
    subtitle: 'Saved Scenes & Presets',
    description: 'Organize customized fluid physics presets, saved scenes, and personal spaces in one central collection.',
    stickerKey: 'folder',
    badge: 'COMING SOON',
    badgeType: 'in_dev',
    route: '/coming-soon?module=collections',
    isAvailable: false,
  },
  {
    id: 'tools',
    title: 'More Tools',
    subtitle: 'Sensors, Calibrations & Utils',
    description: 'Useful daily personal utilities, sensors telemetry, and productivity enhancements are on the way.',
    stickerKey: 'toolbox',
    badge: 'COMING SOON',
    badgeType: 'in_dev',
    route: '/coming-soon?module=tools',
    isAvailable: false,
  },
];
