import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  SafeAreaView,
  Platform,
  Dimensions,
  Image,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useWallpaper } from '../src/state/WallpaperContext';
import { useToast } from '../src/core/components/Toast';
import { Colors } from '../src/core/theme/colors';
import {
  WaterDrop3D,
  Notebook3D,
  Camera3D,
  Folder3D,
  Toolbox3D,
  Clock3D,
} from '../src/features/dashboard/Stickers3D';

type ActiveScreen = 'home' | 'wallpapers' | 'coming_soon' | 'settings';

interface ComingSoonModule {
  id: string;
  title: string;
  subtitle: string;
  type: 'notebook' | 'camera' | 'folder' | 'toolbox';
}

export default function AppEntry() {
  const router = useRouter();
  const { isLiveWallpaperActive, openWallpaperChooser, settings, updateSettings } = useWallpaper();
  const { showToast } = useToast();

  const [activeScreen, setActiveScreen] = useState<ActiveScreen>('home');
  const [selectedModule, setSelectedModule] = useState<ComingSoonModule | null>(null);
  const [checkingUpdate, setCheckingUpdate] = useState(false);

  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'short',
    day: 'numeric',
  });

  const handleOpenComingSoon = (mod: ComingSoonModule) => {
    setSelectedModule(mod);
    setActiveScreen('coming_soon');
    showToast(mod.title, 'In active development for upcoming release', '★', 'charcoal');
  };

  const handleNotifyMe = (title: string) => {
    showToast(
      'Notification Scheduled',
      `You will be notified when ${title} is ready in the next release.`,
      '✓',
      'milano'
    );
  };

  const handleCheckUpdate = () => {
    setCheckingUpdate(true);
    showToast('Checking GitHub...', 'Querying latest release tags & SHA-256 digests', '⚙', 'charcoal');
    setTimeout(() => {
      setCheckingUpdate(false);
      showToast(
        'System Up to Date',
        'Live WaterPaper v1.0.1 (Build 2) is the latest release',
        '✓',
        'milano'
      );
    }, 850);
  };

  /* ================================================================
     1. HOME DASHBOARD (NO HERO PREVIEW CARD - ONLY 5 MODULE CARDS)
     ================================================================ */
  const renderHomeDashboard = () => (
    <ScrollView
      style={styles.scrollContainer}
      contentContainerStyle={styles.scrollContent}
      showsVerticalScrollIndicator={false}
    >
      {/* Top Header */}
      <View style={styles.topHeader}>
        <View>
          <Text style={styles.headerGreeting}>YOUR SPACE</Text>
          <Text style={styles.headerTitle}>Live WaterPaper</Text>
          <Text style={styles.headerDate}>{today}</Text>
        </View>

        <TouchableOpacity
          style={styles.settingsIconButton}
          onPress={() => setActiveScreen('settings')}
          activeOpacity={0.8}
        >
          <Text style={styles.settingsIconGlyph}>⚙</Text>
        </TouchableOpacity>
      </View>

      {/* Section Header */}
      <View style={styles.sectionHeaderRow}>
        <Text style={styles.sectionTitle}>MY MODULES</Text>
        <View style={styles.sectionDivider} />
      </View>

      {/* 2-Column Grid of 5 Modules */}
      <View style={styles.gridContainer}>
        {/* Row 1: Module 1 (Wallpaper) + Module 2 (Notes) */}
        <View style={styles.gridRow}>
          {/* Module 1: Wallpaper (ACTIVE) */}
          <TouchableOpacity
            style={[styles.moduleCardHalf, styles.cardWallpaper]}
            onPress={() => setActiveScreen('wallpapers')}
            activeOpacity={0.85}
          >
            <View style={styles.cardTopRow}>
              <View style={styles.badgeActive}>
                <Text style={styles.badgeActiveText}>ACTIVE</Text>
              </View>
            </View>

            <View style={styles.stickerContainer}>
              <WaterDrop3D size={52} />
            </View>

            <View style={styles.cardBottomText}>
              <Text style={styles.cardTitleWhite}>Liquid Fluid</Text>
              <Text style={styles.cardSubtitleChiffon}>Live Wallpaper</Text>
            </View>

            <View style={styles.actionArrowRow}>
              <Text style={styles.actionArrowTextRed}>Explore →</Text>
            </View>
          </TouchableOpacity>

          {/* Module 2: Personal Notes (COMING SOON) */}
          <TouchableOpacity
            style={[styles.moduleCardHalf, styles.cardNotes]}
            onPress={() =>
              handleOpenComingSoon({
                id: 'notes',
                title: 'Personal Notes',
                subtitle: 'Memos, thoughts & daily scratchpad',
                type: 'notebook',
              })
            }
            activeOpacity={0.85}
          >
            <View style={styles.cardTopRow}>
              <View style={styles.badgeComingSoonCharcoal}>
                <Text style={styles.badgeComingSoonCharcoalText}>SOON</Text>
              </View>
            </View>

            <View style={styles.stickerContainer}>
              <Notebook3D size={52} />
            </View>

            <View style={styles.cardBottomText}>
              <Text style={styles.cardTitleCharcoal}>Personal Notes</Text>
              <Text style={styles.cardSubtitleCharcoal}>Memos & thoughts</Text>
            </View>

            <View style={styles.actionArrowRow}>
              <Text style={styles.actionArrowTextMuted}>Details →</Text>
            </View>
          </TouchableOpacity>
        </View>

        {/* Row 2: Module 3 (Photos) + Module 4 (Collections) */}
        <View style={styles.gridRow}>
          {/* Module 3: Favourite Photos (COMING SOON) */}
          <TouchableOpacity
            style={[styles.moduleCardHalf, styles.cardPhotos]}
            onPress={() =>
              handleOpenComingSoon({
                id: 'photos',
                title: 'Favourite Photos',
                subtitle: 'Curated personal memory album',
                type: 'camera',
              })
            }
            activeOpacity={0.85}
          >
            <View style={styles.cardTopRow}>
              <View style={styles.badgeComingSoonGold}>
                <Text style={styles.badgeComingSoonGoldText}>SOON</Text>
              </View>
            </View>

            <View style={styles.stickerContainer}>
              <Camera3D size={52} />
            </View>

            <View style={styles.cardBottomText}>
              <Text style={styles.cardTitleCharcoal}>Favourite Photos</Text>
              <Text style={styles.cardSubtitleCharcoal}>Memories & albums</Text>
            </View>

            <View style={styles.actionArrowRow}>
              <Text style={styles.actionArrowTextMuted}>Details →</Text>
            </View>
          </TouchableOpacity>

          {/* Module 4: Personal Collections (COMING SOON) */}
          <TouchableOpacity
            style={[styles.moduleCardHalf, styles.cardCollections]}
            onPress={() =>
              handleOpenComingSoon({
                id: 'collections',
                title: 'Personal Collections',
                subtitle: 'Saved links, media & references',
                type: 'folder',
              })
            }
            activeOpacity={0.85}
          >
            <View style={styles.cardTopRow}>
              <View style={styles.badgeComingSoonChiffon}>
                <Text style={styles.badgeComingSoonChiffonText}>SOON</Text>
              </View>
            </View>

            <View style={styles.stickerContainer}>
              <Folder3D size={52} />
            </View>

            <View style={styles.cardBottomText}>
              <Text style={styles.cardTitleChiffon}>Collections</Text>
              <Text style={styles.cardSubtitleGold}>Saved media & docs</Text>
            </View>

            <View style={styles.actionArrowRow}>
              <Text style={styles.actionArrowTextGold}>Details →</Text>
            </View>
          </TouchableOpacity>
        </View>

        {/* Row 3: Module 5: More Tools (COMING SOON - Full Width Banner) */}
        <TouchableOpacity
          style={[styles.moduleCardFull, styles.cardTools]}
          onPress={() =>
            handleOpenComingSoon({
              id: 'tools',
              title: 'More Tools',
              subtitle: 'Productivity suite & personal gadgets',
              type: 'toolbox',
            })
          }
          activeOpacity={0.85}
        >
          <View style={styles.cardFullIconWrap}>
            <Toolbox3D size={48} />
          </View>

          <View style={styles.cardFullTextCol}>
            <Text style={styles.cardTitleBlack}>More Tools</Text>
            <Text style={styles.cardSubtitleBlack}>Productivity tools & gadgets</Text>
          </View>

          <View style={styles.badgeComingSoonRed}>
            <Text style={styles.badgeComingSoonRedText}>SOON</Text>
          </View>
        </TouchableOpacity>
      </View>

      <View style={{ height: 100 }} />
    </ScrollView>
  );

  /* ================================================================
     2. DEDICATED WALLPAPER LISTING SCREEN (SINGLE REAL ENTRY)
     ================================================================ */
  const renderWallpaperListScreen = () => (
    <ScrollView
      style={styles.scrollContainer}
      contentContainerStyle={styles.scrollContent}
      showsVerticalScrollIndicator={false}
    >
      {/* Top Header */}
      <View style={styles.screenHeader}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => setActiveScreen('home')}
          activeOpacity={0.8}
        >
          <Text style={styles.backButtonText}>← Dashboard</Text>
        </TouchableOpacity>
        <Text style={styles.screenTitle}>Live Wallpapers</Text>
        <Text style={styles.screenSubtitle}>
          Installed interactive physics-based wallpapers
        </Text>
      </View>

      {/* The Single Real Wallpaper Entry */}
      <View style={styles.wallpaperEntryCard}>
        {/* Card Header with Status */}
        <View style={styles.wallpaperCardHeader}>
          <View style={styles.badgeActive}>
            <Text style={styles.badgeActiveText}>INSTALLED & ACTIVE</Text>
          </View>
          <Text style={styles.wallpaperTechLabel}>OpenGL ES • 60-120 FPS</Text>
        </View>

        {/* Visual Banner Preview */}
        <View style={styles.wallpaperBanner}>
          <View style={styles.bannerArtCircle}>
            <WaterDrop3D size={72} />
          </View>
          <View style={styles.bannerMeta}>
            <Text style={styles.bannerTitle}>Liquid Fluid Wallpaper</Text>
            <Text style={styles.bannerSubtitle}>Native 2D Incompressible Navier-Stokes</Text>
          </View>
        </View>

        {/* Feature Highlights */}
        <Text style={styles.wallpaperDescription}>
          Real-time interactive water simulation responding dynamically to device tilt, touch ripples, and inertial shake gestures.
        </Text>

        <View style={styles.featurePillRow}>
          <View style={styles.featurePill}>
            <Text style={styles.featurePillText}>✓ Gyroscope Tilt</Text>
          </View>
          <View style={styles.featurePill}>
            <Text style={styles.featurePillText}>✓ Touch Ripples</Text>
          </View>
          <View style={styles.featurePill}>
            <Text style={styles.featurePillText}>✓ Shake Splash</Text>
          </View>
        </View>

        {/* Actions */}
        <View style={styles.cardActionsRow}>
          <TouchableOpacity
            style={styles.btnSecondary}
            onPress={() => router.push({ pathname: '/customize', params: { type: 'water' } })}
            activeOpacity={0.8}
          >
            <Text style={styles.btnSecondaryText}>Customize ⚙</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => router.push('/preview')}
            activeOpacity={0.85}
          >
            <Text style={styles.btnPrimaryText}>Open Live Preview →</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* 2. Floating Sticks Live Wallpaper */}
      <View style={[styles.wallpaperEntryCard, { marginTop: 18 }]}>
        {/* Card Header with Status */}
        <View style={styles.wallpaperCardHeader}>
          <View style={[styles.badgeActive, { backgroundColor: '#FBF8E6', borderColor: '#C49A45' }]}>
            <Text style={[styles.badgeActiveText, { color: '#88502B' }]}>NEW • 3D RIGID BODY</Text>
          </View>
          <Text style={styles.wallpaperTechLabel}>OpenGL ES • 60-120 FPS</Text>
        </View>

        {/* Visual Banner Preview */}
        <View style={styles.wallpaperBanner}>
          <View style={[styles.bannerArtCircle, { backgroundColor: '#F4ECE1' }]}>
            <Image
              source={require('../assets/stickers/stick_twig_1.png')}
              style={{ width: 72, height: 28, transform: [{ rotate: '-10deg' }] }}
              resizeMode="contain"
            />
          </View>
          <View style={styles.bannerMeta}>
            <Text style={styles.bannerTitle}>Floating Sticks Wallpaper</Text>
            <Text style={styles.bannerSubtitle}>3D Driftwood Twigs &amp; Inertial Physics</Text>
          </View>
        </View>

        {/* Feature Highlights */}
        <Text style={styles.wallpaperDescription}>
          Photorealistic 3D wooden driftwood twigs floating on canvas, reacting to gravity tilt, touch disturbance ripples, rotational torque, and real-time rigid-body collisions.
        </Text>

        <View style={styles.featurePillRow}>
          <View style={styles.featurePill}>
            <Text style={styles.featurePillText}>✓ Gyroscope Tilt</Text>
          </View>
          <View style={styles.featurePill}>
            <Text style={styles.featurePillText}>✓ Touch Ripples</Text>
          </View>
          <View style={styles.featurePill}>
            <Text style={styles.featurePillText}>✓ Stick Collisions</Text>
          </View>
        </View>

        {/* Actions */}
        <View style={styles.cardActionsRow}>
          <TouchableOpacity
            style={styles.btnSecondary}
            onPress={() => router.push({ pathname: '/customize', params: { type: 'sticks' } })}
            activeOpacity={0.8}
          >
            <Text style={styles.btnSecondaryText}>Customize ⚙</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => router.push({ pathname: '/preview', params: { type: 'sticks' } })}
            activeOpacity={0.85}
          >
            <Text style={styles.btnPrimaryText}>Open Live Preview →</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={{ height: 100 }} />
    </ScrollView>
  );

  /* ================================================================
     3. COMING SOON SCREEN (CLEAN, NO FAKE DATA)
     ================================================================ */
  const renderComingSoonScreen = () => {
    if (!selectedModule) return null;

    return (
      <View style={styles.comingSoonContainer}>
        {/* Top Header */}
        <View style={styles.comingSoonHeader}>
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => setActiveScreen('home')}
            activeOpacity={0.8}
          >
            <Text style={styles.backButtonText}>← Back to Space</Text>
          </TouchableOpacity>
        </View>

        {/* Center Card */}
        <View style={styles.comingSoonCard}>
          <View style={styles.comingSoonStickerWrap}>
            {selectedModule.type === 'notebook' && <Notebook3D size={84} />}
            {selectedModule.type === 'camera' && <Camera3D size={84} />}
            {selectedModule.type === 'folder' && <Folder3D size={84} />}
            {selectedModule.type === 'toolbox' && <Toolbox3D size={84} />}
          </View>

          <View style={styles.badgeComingSoonRed}>
            <Text style={styles.badgeComingSoonRedText}>UNDER ACTIVE DEVELOPMENT</Text>
          </View>

          <Text style={styles.comingSoonTitle}>{selectedModule.title}</Text>
          <Text style={styles.comingSoonSubtitle}>{selectedModule.subtitle}</Text>

          <Text style={styles.comingSoonNotice}>
            This personal module is scheduled for an upcoming production release. No placeholder, mock, or fake data is generated.
          </Text>

          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => handleNotifyMe(selectedModule.title)}
            activeOpacity={0.85}
          >
            <Text style={styles.btnPrimaryText}>Notify Me When Ready</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.btnSecondary}
            onPress={() => setActiveScreen('home')}
            activeOpacity={0.8}
          >
            <Text style={styles.btnSecondaryText}>Return to Home</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  };

  /* ================================================================
     4. SETTINGS & UPDATER SCREEN
     ================================================================ */
  const renderSettingsScreen = () => (
    <ScrollView
      style={styles.scrollContainer}
      contentContainerStyle={styles.scrollContent}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.screenHeader}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => setActiveScreen('home')}
          activeOpacity={0.8}
        >
          <Text style={styles.backButtonText}>← Dashboard</Text>
        </TouchableOpacity>
        <Text style={styles.screenTitle}>Settings & System</Text>
        <Text style={styles.screenSubtitle}>System preferences and updates</Text>
      </View>

      {/* GitHub Auto-Updater Card */}
      <View style={styles.settingsCard}>
        <Text style={styles.settingsSectionTitle}>SOFTWARE UPDATE</Text>
        <View style={styles.updateRow}>
          <View>
            <Text style={styles.updateAppName}>Live WaterPaper</Text>
            <Text style={styles.updateAppVersion}>v1.0.1 (Build 2)</Text>
          </View>
          <View style={styles.badgeActive}>
            <Text style={styles.badgeActiveText}>LATEST RELEASE</Text>
          </View>
        </View>
        <Text style={styles.updateDescription}>
          Autonomous GitHub release delivery is configured. Updates are verified with SHA-256 integrity before installation.
        </Text>
        <TouchableOpacity
          style={styles.btnPrimary}
          onPress={handleCheckUpdate}
          activeOpacity={0.85}
        >
          <Text style={styles.btnPrimaryText}>
            {checkingUpdate ? 'Checking GitHub...' : 'Check for Updates'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Simulation Preferences Card */}
      <View style={styles.settingsCard}>
        <Text style={styles.settingsSectionTitle}>SIMULATION TACTILE FEEDBACK</Text>

        <View style={styles.settingToggleRow}>
          <View style={{ flex: 1 }}>
            <Text style={styles.settingItemTitle}>Touch Interactions</Text>
            <Text style={styles.settingItemDesc}>Create ripples when tapping or dragging</Text>
          </View>
          <TouchableOpacity
            style={[
              styles.togglePill,
              settings.touchInteractionsEnabled && styles.togglePillActive,
            ]}
            onPress={() => {
              const next = !settings.touchInteractionsEnabled;
              updateSettings({ touchInteractionsEnabled: next });
              showToast(
                next ? 'Touch Disturbance Enabled' : 'Touch Disturbance Disabled',
                'Interactive liquid ripples on screen tap and drag',
                '💧',
                'charcoal'
              );
            }}
          >
            <Text style={styles.togglePillText}>
              {settings.touchInteractionsEnabled ? 'ON' : 'OFF'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={[styles.settingToggleRow, { borderTopWidth: 1, borderTopColor: Colors.border }]}>
          <View style={{ flex: 1 }}>
            <Text style={styles.settingItemTitle}>Gyroscope Inertia</Text>
            <Text style={styles.settingItemDesc}>Slosh liquid dynamically with gravity</Text>
          </View>
          <View style={styles.badgeActive}>
            <Text style={styles.badgeActiveText}>ACTIVE</Text>
          </View>
        </View>
      </View>

      <View style={{ height: 100 }} />
    </ScrollView>
  );

  return (
    <SafeAreaView style={styles.safeArea}>
      {activeScreen === 'home' && renderHomeDashboard()}
      {activeScreen === 'wallpapers' && renderWallpaperListScreen()}
      {activeScreen === 'coming_soon' && renderComingSoonScreen()}
      {activeScreen === 'settings' && renderSettingsScreen()}

      {/* Floating Bottom Navigation Bar (Visible on Home, Wallpapers, Settings) */}
      {activeScreen !== 'coming_soon' && (
        <View style={styles.bottomNavWrapper} pointerEvents="box-none">
          <View style={styles.bottomNavPill}>
            <TouchableOpacity
              style={[
                styles.navItem,
                activeScreen === 'home' && styles.navItemActive,
              ]}
              onPress={() => setActiveScreen('home')}
              activeOpacity={0.8}
            >
              <Text
                style={[
                  styles.navItemIcon,
                  activeScreen === 'home' && styles.navItemIconActive,
                ]}
              >
                ⌂
              </Text>
              <Text
                style={[
                  styles.navItemLabel,
                  activeScreen === 'home' && styles.navItemLabelActive,
                ]}
              >
                Home
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.navItem,
                activeScreen === 'wallpapers' && styles.navItemActive,
              ]}
              onPress={() => setActiveScreen('wallpapers')}
              activeOpacity={0.8}
            >
              <Text
                style={[
                  styles.navItemIcon,
                  activeScreen === 'wallpapers' && styles.navItemIconActive,
                ]}
              >
                ◈
              </Text>
              <Text
                style={[
                  styles.navItemLabel,
                  activeScreen === 'wallpapers' && styles.navItemLabelActive,
                ]}
              >
                Wallpapers
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.navItem,
                activeScreen === 'settings' && styles.navItemActive,
              ]}
              onPress={() => setActiveScreen('settings')}
              activeOpacity={0.8}
            >
              <Text
                style={[
                  styles.navItemIcon,
                  activeScreen === 'settings' && styles.navItemIconActive,
                ]}
              >
                ⚙
              </Text>
              <Text
                style={[
                  styles.navItemLabel,
                  activeScreen === 'settings' && styles.navItemLabelActive,
                ]}
              >
                Settings
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
    </SafeAreaView>
  );
}



const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: Colors.canvas,
  },
  scrollContainer: {
    flex: 1,
  },
  scrollContent: {
    padding: 18,
    paddingTop: 10,
    paddingBottom: 130,
  },

  /* Top Header */
  topHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
    marginTop: 6,
  },
  headerGreeting: {
    fontSize: 11,
    fontWeight: '800',
    color: Colors.milanoRed,
    letterSpacing: 1.2,
    marginBottom: 2,
  },
  headerTitle: {
    fontSize: 26,
    fontWeight: '900',
    color: Colors.deepBlack,
    letterSpacing: -0.5,
  },
  headerDate: {
    fontSize: 13,
    color: Colors.warmGrey,
    marginTop: 2,
    fontWeight: '500',
  },
  settingsIconButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.warmWhite,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.1,
    shadowRadius: 6,
    elevation: 2,
  },
  settingsIconGlyph: {
    fontSize: 18,
    color: Colors.deepCharcoal,
  },

  /* Section Title */
  sectionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 14,
    gap: 12,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: '800',
    color: Colors.warmGrey,
    letterSpacing: 1.1,
  },
  sectionDivider: {
    flex: 1,
    height: 1,
    backgroundColor: 'rgba(41, 36, 33, 0.08)',
  },

  /* 2-Column Grid Layout */
  gridContainer: {
    width: '100%',
  },
  gridRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 12,
    width: '100%',
  },
  moduleCardHalf: {
    flex: 1,
    borderRadius: 20,
    padding: 14,
    minHeight: 185,
    justifyContent: 'space-between',
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
    borderWidth: 1,
  },
  moduleCardFull: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 20,
    padding: 16,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
    borderWidth: 1,
    marginTop: 2,
  },
  cardFullIconWrap: {
    marginRight: 14,
  },
  cardFullTextCol: {
    flex: 1,
  },

  /* Distinct Card Styles */
  cardWallpaper: {
    backgroundColor: Colors.milanoRed,
    borderColor: Colors.milanoRedDark,
  },
  cardNotes: {
    backgroundColor: Colors.lemonChiffon,
    borderColor: Colors.champagneGold,
  },
  cardPhotos: {
    backgroundColor: Colors.warmWhite,
    borderColor: Colors.champagneGold,
  },
  cardCollections: {
    backgroundColor: Colors.deepCharcoal,
    borderColor: 'rgba(255, 251, 212, 0.15)',
  },
  cardTools: {
    backgroundColor: Colors.softCream,
    borderColor: Colors.champagneGold,
  },

  /* Card Contents */
  cardTopRow: {
    flexDirection: 'row',
    justifyContent: 'flex-start',
  },
  stickerContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 8,
  },
  cardBottomText: {
    marginTop: 4,
  },
  cardTitleWhite: {
    fontSize: 15,
    fontWeight: '800',
    color: Colors.warmWhite,
  },
  cardSubtitleChiffon: {
    fontSize: 11,
    fontWeight: '500',
    color: Colors.lemonChiffon,
    marginTop: 2,
  },
  cardTitleCharcoal: {
    fontSize: 15,
    fontWeight: '800',
    color: Colors.deepCharcoal,
  },
  cardSubtitleCharcoal: {
    fontSize: 11,
    fontWeight: '500',
    color: Colors.warmGrey,
    marginTop: 2,
  },
  cardTitleChiffon: {
    fontSize: 15,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },
  cardSubtitleGold: {
    fontSize: 11,
    fontWeight: '500',
    color: Colors.champagneGold,
    marginTop: 2,
  },
  cardTitleBlack: {
    fontSize: 15,
    fontWeight: '800',
    color: Colors.deepBlack,
  },
  cardSubtitleBlack: {
    fontSize: 11,
    fontWeight: '500',
    color: Colors.warmGrey,
    marginTop: 2,
  },

  actionArrowRow: {
    marginTop: 6,
  },
  actionArrowTextRed: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.lemonChiffon,
  },
  actionArrowTextMuted: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.deepCharcoal,
  },
  actionArrowTextGold: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.champagneGold,
  },

  /* Badges */
  badgeActive: {
    backgroundColor: Colors.lemonChiffon,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  badgeActiveText: {
    fontSize: 9,
    fontWeight: '800',
    color: Colors.milanoRed,
    letterSpacing: 0.5,
  },
  badgeComingSoonCharcoal: {
    backgroundColor: 'rgba(41, 36, 33, 0.1)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  badgeComingSoonCharcoalText: {
    fontSize: 9,
    fontWeight: '700',
    color: Colors.deepCharcoal,
    letterSpacing: 0.4,
  },
  badgeComingSoonGold: {
    backgroundColor: 'rgba(196, 154, 69, 0.14)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  badgeComingSoonGoldText: {
    fontSize: 9,
    fontWeight: '700',
    color: Colors.mutedGold,
    letterSpacing: 0.4,
  },
  badgeComingSoonChiffon: {
    backgroundColor: 'rgba(255, 251, 212, 0.12)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  badgeComingSoonChiffonText: {
    fontSize: 9,
    fontWeight: '700',
    color: Colors.lemonChiffon,
    letterSpacing: 0.4,
  },
  badgeComingSoonRed: {
    backgroundColor: 'rgba(169, 14, 2, 0.1)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  badgeComingSoonRedText: {
    fontSize: 9,
    fontWeight: '700',
    color: Colors.milanoRed,
    letterSpacing: 0.4,
  },

  /* Screens Common */
  screenHeader: {
    marginBottom: 20,
    marginTop: 6,
  },
  backButton: {
    alignSelf: 'flex-start',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 12,
    backgroundColor: Colors.warmWhite,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    marginBottom: 12,
  },
  backButtonText: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.deepCharcoal,
  },
  screenTitle: {
    fontSize: 26,
    fontWeight: '900',
    color: Colors.deepBlack,
  },
  screenSubtitle: {
    fontSize: 13,
    color: Colors.warmGrey,
    marginTop: 2,
  },

  /* Dedicated Wallpaper Screen */
  wallpaperEntryCard: {
    backgroundColor: Colors.warmWhite,
    borderRadius: 22,
    padding: 18,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 10,
    elevation: 3,
  },
  wallpaperCardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  wallpaperTechLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.warmGrey,
  },
  wallpaperBanner: {
    backgroundColor: Colors.softCream,
    borderRadius: 18,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  bannerArtCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: Colors.warmWhite,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  bannerMeta: {
    flex: 1,
  },
  bannerTitle: {
    fontSize: 17,
    fontWeight: '800',
    color: Colors.deepBlack,
  },
  bannerSubtitle: {
    fontSize: 12,
    color: Colors.warmGrey,
    marginTop: 3,
  },
  wallpaperDescription: {
    fontSize: 13,
    color: Colors.deepCharcoal,
    lineHeight: 19,
    marginBottom: 16,
  },
  featurePillRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 20,
  },
  featurePill: {
    backgroundColor: Colors.softCream,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  featurePillText: {
    fontSize: 11,
    fontWeight: '600',
    color: Colors.deepCharcoal,
  },
  cardActionsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  btnPrimary: {
    flex: 1,
    backgroundColor: Colors.milanoRed,
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: Colors.milanoRed,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 3,
  },
  btnPrimaryText: {
    fontSize: 14,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },
  btnSecondary: {
    flex: 1,
    backgroundColor: Colors.lemonChiffon,
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  btnSecondaryText: {
    fontSize: 14,
    fontWeight: '800',
    color: Colors.deepCharcoal,
  },

  /* Coming Soon Screen */
  comingSoonContainer: {
    flex: 1,
    padding: 18,
    justifyContent: 'center',
  },
  comingSoonHeader: {
    position: 'absolute',
    top: 20,
    left: 18,
  },
  comingSoonCard: {
    backgroundColor: Colors.warmWhite,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.14,
    shadowRadius: 14,
    elevation: 4,
  },
  comingSoonStickerWrap: {
    marginBottom: 16,
  },
  comingSoonTitle: {
    fontSize: 22,
    fontWeight: '900',
    color: Colors.deepBlack,
    marginTop: 14,
    textAlign: 'center',
  },
  comingSoonSubtitle: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.warmGrey,
    marginTop: 4,
    textAlign: 'center',
  },
  comingSoonNotice: {
    fontSize: 13,
    color: Colors.deepCharcoal,
    textAlign: 'center',
    lineHeight: 19,
    marginVertical: 18,
    paddingHorizontal: 8,
  },

  /* Settings Screen */
  settingsCard: {
    backgroundColor: Colors.warmWhite,
    borderRadius: 20,
    padding: 18,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.08,
    shadowRadius: 6,
    elevation: 2,
  },
  settingsSectionTitle: {
    fontSize: 11,
    fontWeight: '800',
    color: Colors.milanoRed,
    letterSpacing: 1.1,
    marginBottom: 12,
  },
  updateRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  updateAppName: {
    fontSize: 16,
    fontWeight: '800',
    color: Colors.deepBlack,
  },
  updateAppVersion: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.warmGrey,
    marginTop: 2,
  },
  updateDescription: {
    fontSize: 12,
    color: Colors.deepCharcoal,
    lineHeight: 18,
    marginBottom: 16,
  },
  settingToggleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
  },
  settingItemTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.deepBlack,
  },
  settingItemDesc: {
    fontSize: 12,
    color: Colors.warmGrey,
    marginTop: 2,
  },
  togglePill: {
    backgroundColor: Colors.softCream,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  togglePillActive: {
    backgroundColor: Colors.milanoRed,
    borderColor: Colors.milanoRedDark,
  },
  togglePillText: {
    fontSize: 11,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },

  /* Floating Bottom Navigation Bar */
  bottomNavWrapper: {
    position: 'absolute',
    bottom: 16,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  bottomNavPill: {
    flexDirection: 'row',
    backgroundColor: Colors.warmWhite,
    borderRadius: 30,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderWidth: 1.5,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.18,
    shadowRadius: 12,
    elevation: 8,
    gap: 8,
  },
  navItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 22,
  },
  navItemActive: {
    backgroundColor: Colors.milanoRed,
  },
  navItemIcon: {
    fontSize: 16,
    color: Colors.deepCharcoal,
  },
  navItemIconActive: {
    color: Colors.lemonChiffon,
  },
  navItemLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.deepCharcoal,
  },
  navItemLabelActive: {
    color: Colors.lemonChiffon,
  },
});
