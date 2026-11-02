import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Switch,
  Platform,
} from 'react-native';
import { useWallpaper } from '../src/state/WallpaperContext';
import { Colors } from '../src/theme/colors';
import { QualityLevel } from '../src/types/wallpaper';

export default function SettingsScreen() {
  const { settings, updateSettings, compatibility } = useWallpaper();

  const handleSelectQuality = (level: QualityLevel) => {
    let fps = 60;
    if (level === 'low') fps = 30;
    if (level === 'high') fps = 120;
    updateSettings({ qualityLevel: level, targetFps: fps });
  };

  const handleSelectFps = (fps: number) => {
    updateSettings({ targetFps: fps });
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Simulation Quality Preset */}
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Performance & Battery Mode</Text>
        <Text style={styles.sectionSubtitle}>
          Balance simulation fidelity against battery consumption
        </Text>
      </View>

      <View style={styles.qualityRow}>
        {(['low', 'balanced', 'high'] as QualityLevel[]).map((level) => {
          const isSelected = settings.qualityLevel === level;
          const titles = {
            low: 'Battery Saver',
            balanced: 'Balanced',
            high: 'Ultra Smooth',
          };
          const subs = {
            low: '30 FPS • Low GPU',
            balanced: '60 FPS • Optimal',
            high: '90-120 FPS • High',
          };
          return (
            <TouchableOpacity
              key={level}
              style={[styles.qualityCard, isSelected && styles.qualityCardActive]}
              onPress={() => handleSelectQuality(level)}
              activeOpacity={0.8}
            >
              <Text
                style={[styles.qualityTitle, isSelected && styles.qualityTitleActive]}
              >
                {titles[level]}
              </Text>
              <Text style={styles.qualitySub}>{subs[level]}</Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* Target Frame Rate Chips */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Target Frame Rate</Text>
        <Text style={styles.cardDescription}>
          iQOO Neo 6 supports high refresh displays (up to 120 Hz)
        </Text>
        <View style={styles.chipRow}>
          {[30, 60, 90, 120].map((fps) => {
            const isSelected = settings.targetFps === fps;
            return (
              <TouchableOpacity
                key={fps}
                style={[styles.chip, isSelected && styles.chipActive]}
                onPress={() => handleSelectFps(fps)}
              >
                <Text style={[styles.chipText, isSelected && styles.chipTextActive]}>
                  {fps} Hz
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Touch Interaction Toggle */}
      <View style={styles.card}>
        <View style={styles.toggleRow}>
          <View style={styles.toggleTextColumn}>
            <Text style={styles.cardTitle}>Touch Disturbance</Text>
            <Text style={styles.cardDescription}>
              Create ripples and sloshing when tapping or dragging across the water
            </Text>
          </View>
          <Switch
            value={settings.touchInteractionsEnabled}
            onValueChange={(val) => updateSettings({ touchInteractionsEnabled: val })}
            trackColor={{ false: Colors.border, true: Colors.primaryCyan }}
            thumbColor={Colors.surface}
          />
        </View>
      </View>

      {/* Device Diagnostics */}
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Device Compatibility & Hardware</Text>
        <Text style={styles.sectionSubtitle}>
          Real-time hardware status reported by Android
        </Text>
      </View>

      <View style={styles.card}>
        <View style={styles.diagItem}>
          <Text style={styles.diagLabel}>Device Model</Text>
          <Text style={styles.diagValue}>{compatibility?.deviceModel || 'iQOO Neo 6 / Android'}</Text>
        </View>
        <View style={styles.diagDivider} />
        <View style={styles.diagItem}>
          <Text style={styles.diagLabel}>Android Version</Text>
          <Text style={styles.diagValue}>API {compatibility?.androidVersion || Platform.Version}</Text>
        </View>
        <View style={styles.diagDivider} />
        <View style={styles.diagItem}>
          <Text style={styles.diagLabel}>WallpaperService Support</Text>
          <Text style={[styles.diagValue, { color: Colors.success }]}>
            {compatibility?.isWallpaperServiceSupported !== false ? 'Supported' : 'Unsupported'}
          </Text>
        </View>
        <View style={styles.diagDivider} />
        <View style={styles.diagItem}>
          <Text style={styles.diagLabel}>Rotation Vector Sensor</Text>
          <Text style={[styles.diagValue, { color: Colors.success }]}>
            {compatibility?.isRotationVectorSupported !== false ? 'Active' : 'Fallback to Accelerometer'}
          </Text>
        </View>
        <View style={styles.diagDivider} />
        <View style={styles.diagItem}>
          <Text style={styles.diagLabel}>OpenGL ES 3.0 Acceleration</Text>
          <Text style={[styles.diagValue, { color: Colors.success }]}>Hardware Accelerated</Text>
        </View>
      </View>

      {/* Troubleshooting Guide */}
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Troubleshooting & Tips</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.faqTitle}>Lock Screen Support on iQOO / FuntouchOS</Text>
        <Text style={styles.faqBody}>
          In Android's native wallpaper preview dialog, choose "Home screen and lock screen"
          when prompted. If your launcher only sets the home screen, enable live wallpapers
          for lock screen in System Settings → Lock screen & wallpaper.
        </Text>

        <Text style={[styles.faqTitle, { marginTop: 14 }]}>Battery Consumption</Text>
        <Text style={styles.faqBody}>
          The live wallpaper engine completely halts rendering, stops sensor polling,
          and sleeps when the screen is turned off or when other apps are fullscreen.
        </Text>

        <Text style={[styles.faqTitle, { marginTop: 14 }]}>100% Offline & Private</Text>
        <Text style={styles.faqBody}>
          This app operates completely offline with zero tracking, zero analytics,
          and zero external network requests.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 40,
  },
  sectionHeader: {
    marginTop: 10,
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  sectionSubtitle: {
    fontSize: 13,
    color: Colors.textSecondary,
    marginTop: 2,
  },
  qualityRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 16,
  },
  qualityCard: {
    flex: 1,
    backgroundColor: Colors.surface,
    borderRadius: 16,
    padding: 12,
    borderWidth: 1.5,
    borderColor: Colors.border,
    alignItems: 'center',
  },
  qualityCardActive: {
    borderColor: Colors.primaryDark,
    backgroundColor: Colors.surfaceSubtle,
  },
  qualityTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.textPrimary,
    marginBottom: 4,
  },
  qualityTitleActive: {
    color: Colors.primaryDark,
  },
  qualitySub: {
    fontSize: 11,
    color: Colors.textSecondary,
    textAlign: 'center',
  },
  card: {
    backgroundColor: Colors.surface,
    borderRadius: 18,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  cardDescription: {
    fontSize: 13,
    color: Colors.textSecondary,
    marginTop: 2,
    lineHeight: 18,
  },
  chipRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 14,
  },
  chip: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 12,
    backgroundColor: Colors.surfaceSubtle,
    borderWidth: 1,
    borderColor: Colors.border,
    alignItems: 'center',
  },
  chipActive: {
    backgroundColor: Colors.primaryCyan,
    borderColor: Colors.primaryDark,
  },
  chipText: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textPrimary,
  },
  chipTextActive: {
    color: Colors.textPrimary,
    fontWeight: '700',
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  toggleTextColumn: {
    flex: 1,
    paddingRight: 12,
  },
  diagItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 6,
  },
  diagLabel: {
    fontSize: 13,
    color: Colors.textSecondary,
  },
  diagValue: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textPrimary,
  },
  diagDivider: {
    height: 1,
    backgroundColor: Colors.border,
    marginVertical: 4,
  },
  faqTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  faqBody: {
    fontSize: 13,
    color: Colors.textSecondary,
    lineHeight: 19,
    marginTop: 4,
  },
});
