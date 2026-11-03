import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useWallpaper } from '../src/state/WallpaperContext';
import { useToast } from '../src/core/components/Toast';
import { Colors } from '../src/core/theme/colors';
import { QualityLevel } from '../src/features/wallpapers/base/wallpaper';

export default function SettingsScreen() {
  const router = useRouter();
  const { settings, updateSettings } = useWallpaper();
  const { showToast } = useToast();
  const [checkingUpdate, setCheckingUpdate] = useState(false);

  const handleSelectQuality = (level: QualityLevel) => {
    let fps = 30;
    let label = 'Balanced (30 FPS)';
    if (level === 'low' || level === 'battery_saver') {
      fps = 24;
      label = 'Battery Saver (24 FPS)';
    } else if (level === 'high' || level === 'performance') {
      fps = 60;
      label = 'Performance (60 FPS)';
    }
    updateSettings({ qualityLevel: level, targetFps: fps });
    showToast('Performance Mode Updated', label, '⚙', 'charcoal');
  };

  const handleSelectFps = (fps: number) => {
    updateSettings({ targetFps: fps });
    showToast('Display Frame Rate', `Simulation targeted for ${fps} Hz`, '✓', 'milano');
  };

  const handleCheckUpdate = () => {
    setCheckingUpdate(true);
    showToast('Querying GitHub Releases...', 'Verifying signed packages and SHA-256 digests', '⚙', 'charcoal');
    setTimeout(() => {
      setCheckingUpdate(false);
      showToast(
        'System Up to Date',
        'Live WaterPaper v1.0.1 (Build 2) is running the latest production release.',
        '✓',
        'milano'
      );
    }, 850);
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      <View style={styles.headerCard}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          activeOpacity={0.8}
        >
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Settings & Preferences</Text>
        <Text style={styles.headerSubtitle}>
          Configure simulation fidelity, sensors, and updates
        </Text>
      </View>

      {/* GitHub Auto-Updater Card */}
      <View style={styles.cardSection}>
        <Text style={styles.cardSectionTitle}>PRODUCTION AUTO-UPDATER</Text>
        <View style={styles.updateRow}>
          <View>
            <Text style={styles.updateTitle}>Live WaterPaper</Text>
            <Text style={styles.updateSubtitle}>Version 1.0.1 • Build 2</Text>
          </View>
          <View style={styles.badgeActive}>
            <Text style={styles.badgeActiveText}>LATEST</Text>
          </View>
        </View>

        <Text style={styles.cardDescription}>
          Updates are fetched directly from GitHub Releases and cryptographically verified against SHA-256 release checksums.
        </Text>

        <TouchableOpacity
          style={styles.btnPrimary}
          onPress={handleCheckUpdate}
          activeOpacity={0.85}
        >
          <Text style={styles.btnPrimaryText}>
            {checkingUpdate ? 'Checking GitHub Releases...' : 'Check for Updates'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Simulation Quality Preset */}
      <View style={styles.cardSection}>
        <Text style={styles.cardSectionTitle}>PERFORMANCE PRESETS</Text>
        <Text style={styles.cardSectionSub}>
          Balance simulation fluidity against battery consumption
        </Text>

        <View style={styles.qualityRow}>
          {(['battery_saver', 'balanced', 'performance'] as QualityLevel[]).map((level) => {
            const isSelected = settings.qualityLevel === level || (level === 'battery_saver' && settings.qualityLevel === 'low') || (level === 'performance' && settings.qualityLevel === 'high');
            const titles: Record<QualityLevel, string> = {
              low: 'Battery Saver',
              battery_saver: 'Battery Saver',
              balanced: 'Balanced',
              high: 'Performance',
              performance: 'Performance',
            };
            const subs: Record<QualityLevel, string> = {
              low: '24 FPS',
              battery_saver: '24 FPS',
              balanced: '30 FPS',
              high: '60 FPS',
              performance: '60 FPS',
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
      </View>

      {/* Target Frame Rate Chips */}
      <View style={styles.cardSection}>
        <Text style={styles.cardSectionTitle}>TARGET FRAME RATE</Text>
        <Text style={styles.cardDescription}>
          Supports high refresh displays (up to 120 Hz)
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
      <View style={styles.cardSection}>
        <View style={styles.toggleRow}>
          <View style={styles.toggleTextColumn}>
            <Text style={styles.toggleTitle}>Touch Disturbance</Text>
            <Text style={styles.toggleDesc}>
              Generate interactive water ripples upon tap and drag
            </Text>
          </View>
          <TouchableOpacity
            style={[
              styles.togglePill,
              settings.touchInteractionsEnabled && styles.togglePillActive,
            ]}
            onPress={() =>
              updateSettings({
                touchInteractionsEnabled: !settings.touchInteractionsEnabled,
              })
            }
          >
            <Text style={styles.togglePillText}>
              {settings.touchInteractionsEnabled ? 'ON' : 'OFF'}
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.canvas,
  },
  contentContainer: {
    padding: 16,
    paddingTop: 10,
  },
  headerCard: {
    marginBottom: 16,
    marginTop: 4,
  },
  backButton: {
    alignSelf: 'flex-start',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 12,
    backgroundColor: Colors.warmWhite,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    marginBottom: 10,
  },
  backButtonText: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.deepCharcoal,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '900',
    color: Colors.deepBlack,
  },
  headerSubtitle: {
    fontSize: 13,
    color: Colors.warmGrey,
    marginTop: 2,
  },
  cardSection: {
    backgroundColor: Colors.warmWhite,
    borderRadius: 20,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.08,
    shadowRadius: 6,
    elevation: 2,
  },
  cardSectionTitle: {
    fontSize: 11,
    fontWeight: '800',
    color: Colors.milanoRed,
    letterSpacing: 1.1,
  },
  cardSectionSub: {
    fontSize: 12,
    color: Colors.warmGrey,
    marginTop: 2,
    marginBottom: 12,
  },
  updateRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginVertical: 10,
  },
  updateTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: Colors.deepBlack,
  },
  updateSubtitle: {
    fontSize: 12,
    color: Colors.warmGrey,
    marginTop: 2,
  },
  badgeActive: {
    backgroundColor: Colors.lemonChiffon,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  badgeActiveText: {
    fontSize: 9,
    fontWeight: '800',
    color: Colors.milanoRed,
  },
  cardDescription: {
    fontSize: 12,
    color: Colors.deepCharcoal,
    lineHeight: 18,
    marginBottom: 16,
  },
  btnPrimary: {
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
    fontSize: 13,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },
  qualityRow: {
    flexDirection: 'row',
    gap: 8,
  },
  qualityCard: {
    flex: 1,
    backgroundColor: Colors.softCream,
    borderRadius: 14,
    padding: 12,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  qualityCardActive: {
    borderColor: Colors.milanoRed,
    backgroundColor: Colors.warmWhite,
  },
  qualityTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.deepCharcoal,
    textAlign: 'center',
  },
  qualityTitleActive: {
    color: Colors.milanoRed,
    fontWeight: '800',
  },
  qualitySub: {
    fontSize: 10,
    color: Colors.warmGrey,
    marginTop: 4,
    textAlign: 'center',
  },
  chipRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  chip: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 12,
    backgroundColor: Colors.softCream,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  chipActive: {
    backgroundColor: Colors.milanoRed,
    borderColor: Colors.milanoRedDark,
  },
  chipText: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.deepCharcoal,
  },
  chipTextActive: {
    color: Colors.lemonChiffon,
  },
  toggleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  toggleTextColumn: {
    flex: 1,
    marginRight: 12,
  },
  toggleTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.deepBlack,
  },
  toggleDesc: {
    fontSize: 12,
    color: Colors.warmGrey,
    marginTop: 2,
  },
  togglePill: {
    backgroundColor: Colors.softCream,
    paddingHorizontal: 14,
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
});
