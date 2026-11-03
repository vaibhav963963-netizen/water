import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { useWallpaper } from '../src/state/WallpaperContext';
import { useToast } from '../src/core/components/Toast';
import { WaterSimulationView } from '../src/features/wallpapers/water/WaterSimulationView';
import { SticksSimulationView } from '../src/features/wallpapers/sticks/SticksSimulationView';
import { Colors } from '../src/core/theme/colors';

export default function PreviewScreen() {
  const router = useRouter();
  const { type } = useLocalSearchParams<{ type?: string }>();
  const isSticks = type === 'sticks';
  const { settings, openWallpaperChooser } = useWallpaper();
  const { showToast } = useToast();
  const [stats, setStats] = useState({ fps: 60, particles: isSticks ? 5 : 200, droplets: 0 });
  const [showHud, setShowHud] = useState(true);

  return (
    <View style={styles.container}>
      <StatusBar hidden />
      {isSticks ? (
        <SticksSimulationView
          settings={settings}
          enableInteractiveTouch={true}
          enableSensors={true}
          onStatsUpdate={(s) => setStats({ fps: s.fps, particles: s.sticks, droplets: s.kineticEnergy })}
          style={StyleSheet.absoluteFill}
        />
      ) : (
        <WaterSimulationView
          settings={settings}
          enableInteractiveTouch={true}
          enableSensors={true}
          onStatsUpdate={setStats}
          style={StyleSheet.absoluteFill}
        />
      )}

      {/* Floating HUD & Controls Overlay */}
      <SafeAreaView style={styles.overlayContainer} pointerEvents="box-none">
        {/* Top Minimal Navigation Bar */}
        <View style={styles.topBar}>
          <TouchableOpacity
            style={styles.circleButton}
            onPress={() => router.back()}
            activeOpacity={0.8}
          >
            <Text style={styles.circleButtonText}>←</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.pillButton}
            onPress={() => setShowHud(!showHud)}
            activeOpacity={0.8}
          >
            <Text style={styles.pillButtonText}>{showHud ? 'Hide Info' : 'Show Info'}</Text>
          </TouchableOpacity>
        </View>

        {/* HUD Data Card */}
        {showHud && (
          <View style={styles.hudCard}>
            <Text style={styles.hudTitle}>SIMULATION TELEMETRY</Text>
            <View style={styles.statsRow}>
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{stats.fps}</Text>
                <Text style={styles.statLabel}>FPS</Text>
              </View>
              <View style={styles.statDivider} />
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{stats.particles}</Text>
                <Text style={styles.statLabel}>{isSticks ? 'STICKS' : 'PARTICLES'}</Text>
              </View>
              <View style={styles.statDivider} />
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{stats.droplets}</Text>
                <Text style={styles.statLabel}>{isSticks ? 'ENERGY' : 'SPLASHES'}</Text>
              </View>
            </View>
          </View>
        )}

        {/* Bottom Floating Charcoal Action Card */}
        <View style={styles.bottomCardContainer}>
          <View style={styles.bottomFloatingCard}>
            <View style={styles.bottomMetaRow}>
              <View>
                <Text style={styles.bottomCardTitle}>
                  {isSticks ? 'Floating Sticks Wallpaper' : 'Liquid Fluid Wallpaper'}
                </Text>
                <Text style={styles.bottomCardSub}>
                  {isSticks
                    ? 'Tilt to drift & roll • Shake to fling • 3D driftwood'
                    : 'Tilt to slosh • Tap to ripple'}
                </Text>
              </View>
              <View style={styles.badgeActive}>
                <Text style={styles.badgeActiveText}>LIVE</Text>
              </View>
            </View>

            <View style={styles.bottomButtonsRow}>
              <TouchableOpacity
                style={styles.btnSecondary}
                onPress={() => router.push({ pathname: '/customize', params: { type: isSticks ? 'sticks' : 'water' } })}
                activeOpacity={0.8}
              >
                <Text style={styles.btnSecondaryText}>Customize ⚙</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.btnPrimary}
                onPress={async () => {
                  showToast('Live Wallpaper Launcher', 'Opening Android Live Wallpaper Chooser...', '💧', 'milano');
                  await openWallpaperChooser();
                }}
                activeOpacity={0.85}
              >
                <Text style={styles.btnPrimaryText}>Set as Wallpaper</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.canvas,
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
    marginTop: 8,
  },
  circleButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(41, 36, 33, 0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 251, 212, 0.2)',
  },
  circleButtonText: {
    color: Colors.lemonChiffon,
    fontSize: 20,
    fontWeight: 'bold',
  },
  pillButton: {
    backgroundColor: 'rgba(41, 36, 33, 0.75)',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 251, 212, 0.2)',
  },
  pillButtonText: {
    color: Colors.lemonChiffon,
    fontSize: 12,
    fontWeight: '700',
  },
  hudCard: {
    alignSelf: 'center',
    backgroundColor: 'rgba(255, 254, 247, 0.92)',
    borderRadius: 18,
    paddingHorizontal: 22,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 10,
    elevation: 4,
  },
  hudTitle: {
    fontSize: 10,
    fontWeight: '800',
    color: Colors.milanoRed,
    textAlign: 'center',
    letterSpacing: 1.1,
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
    fontWeight: '900',
    color: Colors.deepBlack,
  },
  statLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: Colors.warmGrey,
    marginTop: 1,
  },
  statDivider: {
    width: 1,
    height: 22,
    backgroundColor: 'rgba(41, 36, 33, 0.1)',
  },
  bottomCardContainer: {
    marginBottom: 10,
    paddingHorizontal: 4,
  },
  bottomFloatingCard: {
    backgroundColor: 'rgba(41, 36, 33, 0.92)',
    borderRadius: 22,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 251, 212, 0.2)',
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.25,
    shadowRadius: 12,
    elevation: 6,
  },
  bottomMetaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  bottomCardTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },
  bottomCardSub: {
    fontSize: 12,
    color: Colors.champagneGold,
    marginTop: 2,
  },
  badgeActive: {
    backgroundColor: Colors.milanoRed,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeActiveText: {
    fontSize: 10,
    fontWeight: '800',
    color: Colors.lemonChiffon,
    letterSpacing: 0.5,
  },
  bottomButtonsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  btnSecondary: {
    flex: 1,
    backgroundColor: Colors.softCream,
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  btnSecondaryText: {
    fontSize: 13,
    fontWeight: '800',
    color: Colors.deepCharcoal,
  },
  btnPrimary: {
    flex: 1.3,
    backgroundColor: Colors.milanoRed,
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: Colors.milanoRed,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
    elevation: 3,
  },
  btnPrimaryText: {
    fontSize: 13,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },
});
