import React, { useState } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Alert,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { useWallpaper } from '../src/state/WallpaperContext';
import { useToast } from '../src/core/components/Toast';
import { SettingSlider } from '../src/core/components/SettingSlider';
import { Colors } from '../src/core/theme/colors';

const WATER_COLOR_PALETTE = [
  { name: 'Milano Red', hex: '#A90E02', secHex: '#800A01' },
  { name: 'Pure Cyan', hex: '#00E5FF', secHex: '#00B4D8' },
  { name: 'Deep Ocean', hex: '#0288D1', secHex: '#01579B' },
  { name: 'Emerald Green', hex: '#00E676', secHex: '#00B0FF' },
  { name: 'Golden Honey', hex: '#F59E0B', secHex: '#B45309' },
  { name: 'Lemon Chiffon', hex: '#FFFBD4', secHex: '#E5CD91' },
  { name: 'Electric Amethyst', hex: '#D500F9', secHex: '#7C4DFF' },
  { name: 'Coral Sunset', hex: '#FF5252', secHex: '#FF7043' },
  { name: 'Midnight Charcoal', hex: '#292421', secHex: '#171311' },
];

export default function CustomizeScreen() {
  const router = useRouter();
  const { type } = useLocalSearchParams<{ type?: string }>();
  const initialTab = type === 'sticks' ? 'sticks' : 'water';
  const [selectedTab, setSelectedTab] = useState<'water' | 'sticks'>(initialTab);

  const { settings, updateSettings, resetDefaults } = useWallpaper();
  const { showToast } = useToast();

  const [customHex, setCustomHex] = useState('');

  const handleSelectWaterColor = (hex: string, secHex: string, name: string) => {
    updateSettings({
      waterColorHex: hex,
      waterColorSecondaryHex: secHex,
    });
    showToast('Liquid Color Updated', `Applied ${name} color`, '💧', 'milano');
  };

  const handleApplyCustomHex = () => {
    let cleanHex = customHex.trim();
    if (!cleanHex.startsWith('#')) cleanHex = '#' + cleanHex;
    if (!/^#[0-9A-Fa-f]{6}$/.test(cleanHex)) {
      Alert.alert('Invalid Color', 'Please enter a valid 6-character hex code (e.g. #00E5FF)');
      return;
    }
    updateSettings({
      waterColorHex: cleanHex,
      waterColorSecondaryHex: cleanHex,
    });
    showToast('Custom Color Applied', `Liquid set to ${cleanHex.toUpperCase()}`, '🎨', 'milano');
    setCustomHex('');
  };

  const handlePickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        allowsEditing: false,
        quality: 0.9,
      });
      if (!result.canceled && result.assets && result.assets.length > 0) {
        const uri = result.assets[0].uri;
        updateSettings({
          backgroundType: 'custom_image',
          backgroundImageUri: uri,
        });
        showToast('Background Image Set', 'Photo selected as wallpaper backdrop', '🖼', 'milano');
      }
    } catch (e) {
      Alert.alert('Image Picker', 'Unable to open photo picker on this device.');
    }
  };

  const handleRemoveImage = () => {
    updateSettings({
      backgroundType: 'light',
      backgroundImageUri: null,
    });
    showToast('Background Restored', 'Reverted to default soft canvas backdrop', '✓', 'charcoal');
  };

  const handleReset = () => {
    Alert.alert(
      'Reset Customization',
      `Restore all settings for ${selectedTab === 'water' ? 'Liquid Fluid' : 'Floating Sticks'} to defaults?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reset',
          style: 'destructive',
          onPress: async () => {
            if (selectedTab === 'water') {
              updateSettings({
                waterColorHex: '#00E5FF',
                waterColorSecondaryHex: '#00B4D8',
                waterLevel: 0.38,
                waterVolume: 0.38,
                waterSpeed: 1.25,
                physicsSensitivity: 1.25,
                backgroundImageUri: null,
                backgroundType: 'light',
              });
              showToast('Water Reset', 'Default Cyan water settings restored', '✓', 'milano');
            } else {
              updateSettings({
                stickSize: 1.0,
                stickCount: 5,
                stickSpeed: 1.0,
                backgroundImageUri: null,
                backgroundType: 'light',
              });
              showToast('Sticks Reset', 'Default Floating Sticks settings restored', '✓', 'milano');
            }
          },
        },
      ]
    );
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Top Header Card */}
      <View style={styles.headerCard}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          activeOpacity={0.8}
        >
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Customize Wallpaper</Text>
        <Text style={styles.headerSubtitle}>
          Personalize physics parameters, dimensions, and backgrounds
        </Text>
      </View>

      {/* Wallpaper Switcher Segmented Tabs */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tabButton, selectedTab === 'water' && styles.tabButtonActive]}
          onPress={() => setSelectedTab('water')}
          activeOpacity={0.85}
        >
          <Text style={[styles.tabButtonText, selectedTab === 'water' && styles.tabButtonTextActive]}>
            💧 Liquid Water
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.tabButton, selectedTab === 'sticks' && styles.tabButtonActive]}
          onPress={() => setSelectedTab('sticks')}
          activeOpacity={0.85}
        >
          <Text style={[styles.tabButtonText, selectedTab === 'sticks' && styles.tabButtonTextActive]}>
            🪵 Floating Sticks
          </Text>
        </TouchableOpacity>
      </View>

      {/* ============================================================ */}
      {/* WATER WALLPAPER CUSTOMIZATION (Simple, Clean, Complete) */}
      {/* ============================================================ */}
      {selectedTab === 'water' ? (
        <>
          {/* Section 1: Water Level & Flow Speed */}
          <View style={styles.cardSection}>
            <Text style={styles.cardSectionTitle}>WATER LEVEL &amp; FLOW SPEED</Text>
            <Text style={styles.cardSectionSub}>
              Adjust volume capacity and movement responsiveness
            </Text>

            <SettingSlider
              label="Water Level"
              description="Quantity of water inside the phone"
              value={settings.waterLevel ?? settings.waterVolume ?? 0.38}
              min={0.15}
              max={0.80}
              step={0.05}
              unit="×"
              onValueChange={(v) => updateSettings({ waterLevel: v, waterVolume: v })}
            />

            <SettingSlider
              label="Flow Speed"
              description="Water momentum and tilt responsiveness"
              value={settings.waterSpeed ?? settings.physicsSensitivity ?? 1.25}
              min={0.5}
              max={2.5}
              step={0.1}
              unit="×"
              onValueChange={(v) => updateSettings({ waterSpeed: v, physicsSensitivity: v })}
            />
          </View>

          {/* Section 2: All Colours (Presets + Custom Hex Picker) */}
          <View style={styles.cardSection}>
            <Text style={styles.cardSectionTitle}>LIQUID COLOR</Text>
            <Text style={styles.cardSectionSub}>
              Select from vibrant presets or enter your own custom hex color
            </Text>

            {/* Presets Grid */}
            <View style={styles.paletteGrid}>
              {WATER_COLOR_PALETTE.map((item) => {
                const isSelected = settings.waterColorHex.toLowerCase() === item.hex.toLowerCase();
                return (
                  <TouchableOpacity
                    key={item.hex}
                    style={[styles.paletteItem, isSelected && styles.paletteItemActive]}
                    onPress={() => handleSelectWaterColor(item.hex, item.secHex, item.name)}
                    activeOpacity={0.85}
                  >
                    <View style={[styles.paletteCircle, { backgroundColor: item.hex }]} />
                    <Text style={[styles.paletteLabel, isSelected && styles.paletteLabelActive]} numberOfLines={1}>
                      {item.name}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            {/* Custom Hex Color Input */}
            <View style={styles.customColorRow}>
              <View
                style={[
                  styles.customColorSwatch,
                  { backgroundColor: /^#[0-9A-Fa-f]{6}$/.test(customHex) ? customHex : settings.waterColorHex },
                ]}
              />
              <TextInput
                style={styles.customColorInput}
                placeholder="Custom Hex (e.g. #FF0055)"
                placeholderTextColor={Colors.warmGrey}
                value={customHex}
                onChangeText={setCustomHex}
                autoCapitalize="characters"
                maxLength={7}
              />
              <TouchableOpacity
                style={styles.customColorBtn}
                onPress={handleApplyCustomHex}
                activeOpacity={0.8}
              >
                <Text style={styles.customColorBtnText}>Apply</Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* Section 3: Background Image */}
          <View style={styles.cardSection}>
            <Text style={styles.cardSectionTitle}>BACKGROUND IMAGE</Text>
            <Text style={styles.cardSectionSub}>
              Custom photo rendered behind the water (center-cropped, never stretched)
            </Text>

            <View style={styles.bgImagePreviewContainer}>
              {settings.backgroundImageUri ? (
                <Image
                  source={{ uri: settings.backgroundImageUri }}
                  style={styles.bgImagePreviewThumbnail}
                  resizeMode="cover"
                />
              ) : (
                <View style={styles.bgImagePlaceholder}>
                  <Text style={styles.bgPlaceholderIcon}>🖼</Text>
                  <Text style={styles.bgPlaceholderText}>No image</Text>
                </View>
              )}

              <View style={styles.bgImageMetaCol}>
                <Text style={styles.bgImageStatusTitle}>
                  {settings.backgroundImageUri ? 'Custom Background Active' : 'Default Canvas Active'}
                </Text>
                <Text style={styles.bgImageStatusSub}>
                  {settings.backgroundImageUri
                    ? 'Proportionally scaled to fill entire screen'
                    : 'Clean light background'}
                </Text>
              </View>
            </View>

            <View style={styles.bgImageButtonsRow}>
              <TouchableOpacity
                style={styles.btnChooseImage}
                onPress={handlePickImage}
                activeOpacity={0.85}
              >
                <Text style={styles.btnChooseImageText}>
                  {settings.backgroundImageUri ? 'Change Photo' : 'Choose Background Photo'}
                </Text>
              </TouchableOpacity>

              {settings.backgroundImageUri && (
                <TouchableOpacity
                  style={styles.btnRemoveImage}
                  onPress={handleRemoveImage}
                  activeOpacity={0.8}
                >
                  <Text style={styles.btnRemoveImageText}>Remove</Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        </>
      ) : (
        /* ============================================================ */
        /* STICKS WALLPAPER CUSTOMIZATION (Size, Count, Speed, Image)  */
        /* ============================================================ */
        <>
          {/* Section 1: Stick Size & Sticks Count */}
          <View style={styles.cardSection}>
            <Text style={styles.cardSectionTitle}>STICK SIZE &amp; COUNT</Text>
            <Text style={styles.cardSectionSub}>
              Control driftwood scale, quantity, and stacking physics
            </Text>

            <SettingSlider
              label="Stick Size"
              description="Scale factor of 3D driftwood twigs"
              value={settings.stickSize ?? 1.0}
              min={0.6}
              max={1.6}
              step={0.1}
              unit="×"
              onValueChange={(v) => updateSettings({ stickSize: v })}
            />

            <SettingSlider
              label="Sticks Count"
              description="Total number of wooden sticks (2 to 15)"
              value={settings.stickCount ?? 5}
              min={2}
              max={15}
              step={1}
              unit=" twigs"
              onValueChange={(v) => updateSettings({ stickCount: Math.round(v) })}
            />

            <View style={styles.infoNoteBox}>
              <Text style={styles.infoNoteText}>
                💡 If count is higher than 6, unique authentic twig textures are dynamically distributed across all sticks.
              </Text>
            </View>
          </View>

          {/* Section 2: Speed */}
          <View style={styles.cardSection}>
            <Text style={styles.cardSectionTitle}>SIMULATION SPEED</Text>
            <Text style={styles.cardSectionSub}>
              Adjust physics responsiveness, roll rate, and gravity reaction
            </Text>

            <SettingSlider
              label="Speed Multiplier"
              description="Motion speed and tilt reaction velocity"
              value={settings.stickSpeed ?? 1.0}
              min={0.5}
              max={2.5}
              step={0.1}
              unit="×"
              onValueChange={(v) => updateSettings({ stickSpeed: v })}
            />
          </View>

          {/* Section 3: Background Image */}
          <View style={styles.cardSection}>
            <Text style={styles.cardSectionTitle}>BACKGROUND IMAGE</Text>
            <Text style={styles.cardSectionSub}>
              Custom photo backdrop behind the floating sticks (center-cropped, never stretched)
            </Text>

            <View style={styles.bgImagePreviewContainer}>
              {settings.backgroundImageUri ? (
                <Image
                  source={{ uri: settings.backgroundImageUri }}
                  style={styles.bgImagePreviewThumbnail}
                  resizeMode="cover"
                />
              ) : (
                <View style={styles.bgImagePlaceholder}>
                  <Text style={styles.bgPlaceholderIcon}>🖼</Text>
                  <Text style={styles.bgPlaceholderText}>No image</Text>
                </View>
              )}

              <View style={styles.bgImageMetaCol}>
                <Text style={styles.bgImageStatusTitle}>
                  {settings.backgroundImageUri ? 'Custom Background Active' : 'Default Canvas Active'}
                </Text>
                <Text style={styles.bgImageStatusSub}>
                  {settings.backgroundImageUri
                    ? 'Proportionally scaled to fill entire screen'
                    : 'Natural warm cream wood canvas'}
                </Text>
              </View>
            </View>

            <View style={styles.bgImageButtonsRow}>
              <TouchableOpacity
                style={styles.btnChooseImage}
                onPress={handlePickImage}
                activeOpacity={0.85}
              >
                <Text style={styles.btnChooseImageText}>
                  {settings.backgroundImageUri ? 'Change Photo' : 'Choose Background Photo'}
                </Text>
              </TouchableOpacity>

              {settings.backgroundImageUri && (
                <TouchableOpacity
                  style={styles.btnRemoveImage}
                  onPress={handleRemoveImage}
                  activeOpacity={0.8}
                >
                  <Text style={styles.btnRemoveImageText}>Remove</Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        </>
      )}

      {/* Action Buttons */}
      <View style={styles.actionButtonsCol}>
        <TouchableOpacity
          style={styles.btnPrimary}
          onPress={() => router.push({ pathname: '/preview', params: { type: selectedTab } })}
          activeOpacity={0.85}
        >
          <Text style={styles.btnPrimaryText}>
            Open Live {selectedTab === 'water' ? 'Water' : 'Sticks'} Preview →
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.resetButton}
          onPress={handleReset}
          activeOpacity={0.8}
        >
          <Text style={styles.resetButtonText}>Restore Defaults</Text>
        </TouchableOpacity>
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
    marginBottom: 14,
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
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: Colors.softCream,
    borderRadius: 16,
    padding: 4,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  tabButton: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    borderRadius: 12,
  },
  tabButtonActive: {
    backgroundColor: Colors.milanoRed,
    shadowColor: Colors.milanoRed,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 2,
  },
  tabButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.deepCharcoal,
  },
  tabButtonTextActive: {
    color: Colors.lemonChiffon,
    fontWeight: '800',
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
    marginBottom: 14,
  },
  paletteGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 12,
  },
  paletteItem: {
    width: '31%',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 4,
    borderRadius: 14,
    backgroundColor: Colors.softCream,
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  paletteItemActive: {
    borderColor: Colors.milanoRed,
    backgroundColor: Colors.warmWhite,
  },
  paletteCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    marginBottom: 6,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.1)',
  },
  paletteLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: Colors.warmGrey,
    textAlign: 'center',
  },
  paletteLabelActive: {
    color: Colors.milanoRed,
    fontWeight: '800',
  },
  customColorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 6,
  },
  customColorSwatch: {
    width: 36,
    height: 36,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  customColorInput: {
    flex: 1,
    height: 40,
    backgroundColor: Colors.softCream,
    borderRadius: 12,
    paddingHorizontal: 12,
    fontSize: 13,
    fontWeight: '600',
    color: Colors.deepBlack,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  customColorBtn: {
    backgroundColor: Colors.milanoRed,
    paddingHorizontal: 14,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  customColorBtnText: {
    fontSize: 12,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },
  infoNoteBox: {
    backgroundColor: Colors.softCream,
    borderRadius: 12,
    padding: 10,
    marginTop: 10,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  infoNoteText: {
    fontSize: 11,
    color: Colors.deepCharcoal,
    lineHeight: 16,
  },
  bgImagePreviewContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    backgroundColor: Colors.softCream,
    borderRadius: 14,
    padding: 12,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    marginBottom: 14,
  },
  bgImagePreviewThumbnail: {
    width: 60,
    height: 60,
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: Colors.milanoRed,
  },
  bgImagePlaceholder: {
    width: 60,
    height: 60,
    borderRadius: 10,
    backgroundColor: Colors.warmWhite,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bgPlaceholderIcon: {
    fontSize: 22,
  },
  bgPlaceholderText: {
    fontSize: 9,
    fontWeight: '600',
    color: Colors.warmGrey,
    marginTop: 2,
    textAlign: 'center',
  },
  bgImageMetaCol: {
    flex: 1,
  },
  bgImageStatusTitle: {
    fontSize: 13,
    fontWeight: '800',
    color: Colors.deepBlack,
  },
  bgImageStatusSub: {
    fontSize: 11,
    color: Colors.warmGrey,
    marginTop: 2,
    lineHeight: 15,
  },
  bgImageButtonsRow: {
    flexDirection: 'row',
    gap: 10,
  },
  btnChooseImage: {
    flex: 1,
    backgroundColor: Colors.milanoRed,
    paddingVertical: 12,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnChooseImageText: {
    fontSize: 13,
    fontWeight: '800',
    color: Colors.lemonChiffon,
  },
  btnRemoveImage: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 14,
    backgroundColor: Colors.warmWhite,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnRemoveImageText: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.milanoRed,
  },
  actionButtonsCol: {
    gap: 12,
    marginTop: 8,
  },
  btnPrimary: {
    backgroundColor: Colors.milanoRed,
    paddingVertical: 16,
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
  resetButton: {
    paddingVertical: 14,
    borderRadius: 16,
    backgroundColor: Colors.warmWhite,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    alignItems: 'center',
  },
  resetButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.milanoRed,
  },
});
