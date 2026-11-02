import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
} from 'react-native';
import { useWallpaper } from '../src/state/WallpaperContext';
import { SettingSlider } from '../src/components/SettingSlider';
import { ColorPickerModal } from '../src/components/ColorPickerModal';
import { Colors } from '../src/theme/colors';
import { BackgroundType } from '../src/types/wallpaper';

export default function CustomizeScreen() {
  const { settings, updateSettings, resetDefaults } = useWallpaper();

  const [activePicker, setActivePicker] = useState<'water' | 'secondary' | 'background' | null>(
    null
  );

  const handleSelectBackgroundType = (type: BackgroundType) => {
    let hex = settings.backgroundColorHex;
    if (type === 'light') hex = '#F2F5F8';
    if (type === 'dark') hex = '#0F172A';
    updateSettings({ backgroundType: type, backgroundColorHex: hex });
  };

  const handleReset = () => {
    Alert.alert(
      'Reset Customization',
      'Restore all liquid physics, colors, and backgrounds to original reference defaults?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reset',
          style: 'destructive',
          onPress: async () => {
            await resetDefaults();
          },
        },
      ]
    );
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Live Color Bar indicator */}
      <View style={styles.colorIndicatorCard}>
        <View style={styles.colorIndicatorRow}>
          <TouchableOpacity
            style={styles.colorTile}
            onPress={() => setActivePicker('water')}
            activeOpacity={0.8}
          >
            <View
              style={[
                styles.swatchPreview,
                { backgroundColor: settings.waterColorHex },
              ]}
            />
            <View>
              <Text style={styles.colorTileTitle}>Liquid Color</Text>
              <Text style={styles.colorTileHex}>{settings.waterColorHex}</Text>
            </View>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.colorTile}
            onPress={() => setActivePicker('secondary')}
            activeOpacity={0.8}
          >
            <View
              style={[
                styles.swatchPreview,
                { backgroundColor: settings.waterColorSecondaryHex },
              ]}
            />
            <View>
              <Text style={styles.colorTileTitle}>Highlight Edge</Text>
              <Text style={styles.colorTileHex}>{settings.waterColorSecondaryHex}</Text>
            </View>
          </TouchableOpacity>
        </View>
      </View>

      {/* Background Style Section */}
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Background Style</Text>
        <Text style={styles.sectionSubtitle}>
          Liquid flows over light, dark, or custom backdrops
        </Text>
      </View>

      <View style={styles.bgChoiceRow}>
        <TouchableOpacity
          style={[
            styles.bgChoiceCard,
            settings.backgroundType === 'light' && styles.bgChoiceCardActive,
          ]}
          onPress={() => handleSelectBackgroundType('light')}
          activeOpacity={0.8}
        >
          <View style={[styles.bgPreviewBox, { backgroundColor: '#F2F5F8' }]} />
          <Text style={styles.bgChoiceTitle}>Original Light</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.bgChoiceCard,
            settings.backgroundType === 'dark' && styles.bgChoiceCardActive,
          ]}
          onPress={() => handleSelectBackgroundType('dark')}
          activeOpacity={0.8}
        >
          <View style={[styles.bgPreviewBox, { backgroundColor: '#0F172A' }]} />
          <Text style={styles.bgChoiceTitle}>Midnight Dark</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.bgChoiceCard,
            settings.backgroundType === 'custom_color' && styles.bgChoiceCardActive,
          ]}
          onPress={() => {
            handleSelectBackgroundType('custom_color');
            setActivePicker('background');
          }}
          activeOpacity={0.8}
        >
          <View
            style={[
              styles.bgPreviewBox,
              { backgroundColor: settings.backgroundColorHex || '#E0F7FA' },
            ]}
          />
          <Text style={styles.bgChoiceTitle}>Custom Tint</Text>
        </TouchableOpacity>
      </View>

      {/* Physics Sliders */}
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Fluid Properties</Text>
        <Text style={styles.sectionSubtitle}>
          Shape how the water deforms, stretches, and splashes
        </Text>
      </View>

      <SettingSlider
        label="Water Volume"
        description="Total liquid quantity in the pool"
        value={settings.waterVolume}
        min={0.15}
        max={0.7}
        step={0.05}
        unit="×"
        onValueChange={(v) => updateSettings({ waterVolume: v })}
      />

      <SettingSlider
        label="Gravity Sensitivity"
        description="Speed and force of water flowing towards phone tilt"
        value={settings.physicsSensitivity}
        min={0.5}
        max={2.5}
        step={0.1}
        unit="×"
        onValueChange={(v) => updateSettings({ physicsSensitivity: v })}
      />

      <SettingSlider
        label="Shake & Splash Sensitivity"
        description="Tendency of energetic movements to detach droplets"
        value={settings.shakeSensitivity}
        min={0.5}
        max={2.5}
        step={0.1}
        unit="×"
        onValueChange={(v) => updateSettings({ shakeSensitivity: v })}
      />

      <SettingSlider
        label="Luminous Glow Intensity"
        description="Soft neon bloom radiating from water boundaries"
        value={settings.glowIntensity}
        min={0.0}
        max={1.0}
        step={0.05}
        unit=""
        onValueChange={(v) => updateSettings({ glowIntensity: v })}
      />

      {/* Reset button */}
      <TouchableOpacity
        style={styles.resetButton}
        onPress={handleReset}
        activeOpacity={0.8}
      >
        <Text style={styles.resetButtonText}>Restore Reference Defaults</Text>
      </TouchableOpacity>

      {/* Color picker modal */}
      <ColorPickerModal
        visible={activePicker !== null}
        title={
          activePicker === 'water'
            ? 'Select Liquid Hue'
            : activePicker === 'secondary'
            ? 'Select Highlight Edge Shade'
            : 'Select Custom Background Tint'
        }
        currentColor={
          activePicker === 'water'
            ? settings.waterColorHex
            : activePicker === 'secondary'
            ? settings.waterColorSecondaryHex
            : settings.backgroundColorHex
        }
        onSelectColor={(color) => {
          if (activePicker === 'water') {
            updateSettings({ waterColorHex: color });
          } else if (activePicker === 'secondary') {
            updateSettings({ waterColorSecondaryHex: color });
          } else if (activePicker === 'background') {
            updateSettings({ backgroundColorHex: color, backgroundType: 'custom_color' });
          }
        }}
        onClose={() => setActivePicker(null)}
      />
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
  colorIndicatorCard: {
    backgroundColor: Colors.surface,
    borderRadius: 18,
    padding: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  colorIndicatorRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  colorTile: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: Colors.surfaceSubtle,
    padding: 12,
    borderRadius: 14,
  },
  swatchPreview: {
    width: 38,
    height: 38,
    borderRadius: 19,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  colorTileTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  colorTileHex: {
    fontSize: 12,
    color: Colors.textSecondary,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
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
  bgChoiceRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  bgChoiceCard: {
    flex: 1,
    backgroundColor: Colors.surface,
    borderRadius: 16,
    padding: 12,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: Colors.border,
  },
  bgChoiceCardActive: {
    borderColor: Colors.primaryDark,
    backgroundColor: Colors.surfaceSubtle,
  },
  bgPreviewBox: {
    width: '100%',
    height: 48,
    borderRadius: 10,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  bgChoiceTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.textPrimary,
    textAlign: 'center',
  },
  resetButton: {
    marginTop: 16,
    paddingVertical: 14,
    borderRadius: 14,
    backgroundColor: Colors.surface,
    borderWidth: 1,
    borderColor: Colors.borderStrong,
    alignItems: 'center',
  },
  resetButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.error,
  },
});
