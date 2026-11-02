import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Dimensions,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useWallpaper } from '../src/state/WallpaperContext';
import { WaterSimulationView } from '../src/components/WaterSimulationView';
import { StatusCard } from '../src/components/StatusCard';
import { Colors } from '../src/theme/colors';
import { PRESET_THEMES } from '../src/types/wallpaper';

export default function HomeScreen() {
  const router = useRouter();
  const { settings, isLiveWallpaperActive, openWallpaperChooser, applyPreset } = useWallpaper();
  const [stats, setStats] = useState({ fps: 60, particles: 180, droplets: 0 });

  const handleSetWallpaper = async () => {
    await openWallpaperChooser();
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Live Wallpaper Status Card */}
      <StatusCard
        isActive={isLiveWallpaperActive}
        onSetWallpaperPress={handleSetWallpaper}
      />

      {/* Main Interactive Preview Card */}
      <View style={styles.previewCard}>
        <View style={styles.previewContainer}>
          <WaterSimulationView
            settings={settings}
            enableInteractiveTouch={true}
            enableSensors={true}
            onStatsUpdate={setStats}
            style={styles.waterView}
          />
          {/* Subtle overlay HUD on preview */}
          <View style={styles.hudOverlay}>
            <View style={styles.hudBadge}>
              <Text style={styles.hudText}>{stats.fps} FPS</Text>
            </View>
            <TouchableOpacity
              style={styles.expandButton}
              onPress={() => router.push('/preview')}
              activeOpacity={0.8}
            >
              <Text style={styles.expandButtonText}>⤢ Fullscreen</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.previewFooter}>
          <Text style={styles.previewHint}>
            Tilt phone to slosh liquid • Shake for splashes • Tap to disturb
          </Text>
        </View>
      </View>

      {/* Primary Action Buttons */}
      <View style={styles.actionButtonsRow}>
        <TouchableOpacity
          style={styles.primaryButton}
          onPress={handleSetWallpaper}
          activeOpacity={0.85}
        >
          <Text style={styles.primaryButtonText}>Set as Wallpaper</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.secondaryButton}
          onPress={() => router.push('/customize')}
          activeOpacity={0.8}
        >
          <Text style={styles.secondaryButtonText}>Customize</Text>
        </TouchableOpacity>
      </View>

      {/* Quick Presets Selection */}
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Presets</Text>
        <Text style={styles.sectionSubtitle}>Select instant fluid themes</Text>
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.presetsList}
      >
        {PRESET_THEMES.map((preset) => {
          const isSelected =
            settings.waterColorHex.toUpperCase() === preset.waterColor.toUpperCase();
          return (
            <TouchableOpacity
              key={preset.id}
              style={[
                styles.presetCard,
                isSelected && styles.presetCardSelected,
              ]}
              onPress={() => applyPreset(preset)}
              activeOpacity={0.8}
            >
              <View
                style={[
                  styles.presetColorCircle,
                  { backgroundColor: preset.waterColor },
                ]}
              />
              <Text style={styles.presetName}>{preset.name}</Text>
              <Text style={styles.presetTagline} numberOfLines={2}>
                {preset.tagline}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {/* Additional Navigation Links */}
      <View style={styles.navRow}>
        <TouchableOpacity
          style={styles.navTile}
          onPress={() => router.push('/customize')}
        >
          <Text style={styles.navTileTitle}>Liquid & Background</Text>
          <Text style={styles.navTileDesc}>Colors, volume, and glow intensity</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.navTile}
          onPress={() => router.push('/settings')}
        >
          <Text style={styles.navTileTitle}>Physics & Battery</Text>
          <Text style={styles.navTileDesc}>Frame rates, sensors, diagnostics</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const screenWidth = Dimensions.get('window').width;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  contentContainer: {
    paddingBottom: 36,
  },
  previewCard: {
    marginHorizontal: 16,
    marginVertical: 10,
    backgroundColor: Colors.surface,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: Colors.border,
    overflow: 'hidden',
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 1,
    shadowRadius: 12,
    elevation: 3,
  },
  previewContainer: {
    width: '100%',
    height: 380,
    position: 'relative',
    backgroundColor: '#F3F6FA',
  },
  waterView: {
    width: '100%',
    height: '100%',
  },
  hudOverlay: {
    position: 'absolute',
    top: 14,
    left: 14,
    right: 14,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  hudBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.85)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
  },
  hudText: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  expandButton: {
    backgroundColor: 'rgba(15, 23, 42, 0.75)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 14,
  },
  expandButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.textInverse,
  },
  previewFooter: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: Colors.surface,
    borderTopWidth: 1,
    borderTopColor: Colors.border,
  },
  previewHint: {
    fontSize: 12,
    color: Colors.textSecondary,
    textAlign: 'center',
  },
  actionButtonsRow: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginVertical: 12,
    gap: 12,
  },
  primaryButton: {
    flex: 2,
    backgroundColor: Colors.primaryCyan,
    paddingVertical: 16,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: Colors.primaryCyan,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
    elevation: 4,
  },
  primaryButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  secondaryButton: {
    flex: 1.2,
    backgroundColor: Colors.surface,
    paddingVertical: 16,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
  },
  secondaryButtonText: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textPrimary,
  },
  sectionHeader: {
    marginHorizontal: 16,
    marginTop: 20,
    marginBottom: 10,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  sectionSubtitle: {
    fontSize: 13,
    color: Colors.textSecondary,
  },
  presetsList: {
    paddingHorizontal: 16,
    gap: 12,
    paddingBottom: 6,
  },
  presetCard: {
    width: 140,
    backgroundColor: Colors.surface,
    borderRadius: 18,
    padding: 14,
    borderWidth: 1.5,
    borderColor: Colors.border,
  },
  presetCardSelected: {
    borderColor: Colors.primaryDark,
    backgroundColor: Colors.surfaceSubtle,
  },
  presetColorCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    marginBottom: 10,
  },
  presetName: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.textPrimary,
    marginBottom: 4,
  },
  presetTagline: {
    fontSize: 11,
    color: Colors.textSecondary,
    lineHeight: 15,
  },
  navRow: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginTop: 18,
    gap: 12,
  },
  navTile: {
    flex: 1,
    backgroundColor: Colors.surface,
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  navTileTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.textPrimary,
    marginBottom: 4,
  },
  navTileDesc: {
    fontSize: 12,
    color: Colors.textSecondary,
    lineHeight: 16,
  },
});
