import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useWallpaper } from '../src/state/WallpaperContext';
import { WaterSimulationView } from '../src/components/WaterSimulationView';
import { Colors } from '../src/theme/colors';

export default function PreviewScreen() {
  const router = useRouter();
  const { settings, openWallpaperChooser } = useWallpaper();
  const [stats, setStats] = useState({ fps: 60, particles: 200, droplets: 0 });
  const [showHud, setShowHud] = useState(true);

  return (
    <View style={styles.container}>
      <StatusBar hidden />
      <WaterSimulationView
        settings={settings}
        enableInteractiveTouch={true}
        enableSensors={true}
        onStatsUpdate={setStats}
        style={StyleSheet.absoluteFill}
      />

      {/* Floating HUD & Controls */}
      <SafeAreaView style={styles.overlayContainer} pointerEvents="box-none">
        {/* Top bar */}
        <View style={styles.topBar}>
          <TouchableOpacity
            style={styles.circleButton}
            onPress={() => router.back()}
            activeOpacity={0.8}
          >
            <Text style={styles.circleButtonText}>✕</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.pillButton}
            onPress={() => setShowHud(!showHud)}
            activeOpacity={0.8}
          >
            <Text style={styles.pillButtonText}>{showHud ? 'Hide HUD' : 'Show HUD'}</Text>
          </TouchableOpacity>
        </View>

        {/* HUD Data */}
        {showHud && (
          <View style={styles.hudCard}>
            <Text style={styles.hudTitle}>Live Simulation Stats</Text>
            <View style={styles.statsRow}>
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{stats.fps}</Text>
                <Text style={styles.statLabel}>FPS</Text>
              </View>
              <View style={styles.statDivider} />
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{stats.particles}</Text>
                <Text style={styles.statLabel}>Particles</Text>
              </View>
              <View style={styles.statDivider} />
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{stats.droplets}</Text>
                <Text style={styles.statLabel}>Droplets</Text>
              </View>
            </View>
          </View>
        )}

        {/* Bottom Actions */}
        <View style={styles.bottomBar}>
          <TouchableOpacity
            style={styles.applyButton}
            onPress={async () => {
              await openWallpaperChooser();
            }}
            activeOpacity={0.85}
          >
            <Text style={styles.applyButtonText}>Set as Wallpaper</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F3F6FA',
  },
  overlayContainer: {
    flex: 1,
    justifyContent: 'space-between',
    padding: 16,
  },
  topBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 10,
  },
  circleButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(15, 23, 42, 0.65)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  circleButtonText: {
    color: '#FFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
  pillButton: {
    backgroundColor: 'rgba(15, 23, 42, 0.65)',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
  },
  pillButtonText: {
    color: '#FFF',
    fontSize: 13,
    fontWeight: '600',
  },
  hudCard: {
    alignSelf: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.88)',
    borderRadius: 18,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: Colors.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 10,
    elevation: 4,
  },
  hudTitle: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.textSecondary,
    textAlign: 'center',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginBottom: 8,
  },
  statsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 18,
    fontWeight: '800',
    color: Colors.textPrimary,
  },
  statLabel: {
    fontSize: 11,
    color: Colors.textSecondary,
  },
  statDivider: {
    width: 1,
    height: 24,
    backgroundColor: Colors.border,
  },
  bottomBar: {
    marginBottom: 20,
    paddingHorizontal: 12,
  },
  applyButton: {
    backgroundColor: Colors.primaryCyan,
    paddingVertical: 16,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#00E5FF',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
    elevation: 5,
  },
  applyButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
});
