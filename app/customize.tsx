import React, { useState } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  Platform,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useWallpaper } from '../src/state/WallpaperContext';
import { useToast } from '../src/components/Toast';
import { SettingSlider } from '../src/components/SettingSlider';
import { Colors } from '../src/theme/colors';
import { BackgroundType } from '../src/types/wallpaper';

const COLOR_SWATCHES = [
  { name: 'Milano Red', hex: '#A90E02' },
  { name: 'Deep Crimson', hex: '#800A01' },
  { name: 'Muted Gold', hex: '#C49A45' },
  { name: 'Lemon Chiffon', hex: '#FFFBD4' },
  { name: 'Charcoal', hex: '#292421' },
];

export default function CustomizeScreen() {
  const router = useRouter();
  const { settings, updateSettings, resetDefaults } = useWallpaper();
  const { showToast } = useToast();

  const handleSelectColor = (hex: string, name: string) => {
    updateSettings({
      waterColorHex: hex,
      waterColorSecondaryHex: hex === '#A90E02' ? '#800A01' : '#E5CD91',
    });
    showToast('Liquid Color Updated', `Applied ${name} fluid theme`, '💧', 'milano');
  };

  const handleSelectBackgroundType = (type: BackgroundType, label: string) => {
    let hex = settings.backgroundColorHex;
    if (type === 'light') hex = '#FAF5E0';
    if (type === 'dark') hex = '#171311';
    updateSettings({ backgroundType: type, backgroundColorHex: hex, backgroundImageUri: null });
    showToast('Backdrop Updated', `Canvas style set to ${label}`, '★', 'charcoal');
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
    showToast('Background Restored', 'Reverted to default Soft Cream canvas', '✓', 'charcoal');
  };

  const handleReset = () => {
    Alert.alert(
      'Reset Customization',
      'Restore all liquid physics and colors to authentic Milano Red defaults?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reset',
          style: 'destructive',
          onPress: async () => {
            await resetDefaults();
            updateSettings({
              waterColorHex: '#A90E02',
              waterColorSecondaryHex: '#800A01',
              backgroundColorHex: '#FAF5E0',
              backgroundType: 'light',
              backgroundImageUri: null,
            });
            showToast('Defaults Restored', 'Authentic Milano Red physics and colors active', '✓', 'milano');
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
        <Text style={styles.headerTitle}>Customize Liquid</Text>
        <Text style={styles.headerSubtitle}>
          Tailor fluid dynamics, colors, and boundary aesthetics
        </Text>
      </View>

      {/* Preset Swatches Section */}
      <View style={styles.cardSection}>
        <Text style={styles.cardSectionTitle}>LIQUID COLOR PALETTE</Text>
        <Text style={styles.cardSectionSub}>
          Select from the curated Milano Red & Lemon Chiffon palette
        </Text>

        <View style={styles.swatchesRow}>
          {COLOR_SWATCHES.map((swatch) => {
            const isSelected = settings.waterColorHex.toLowerCase() === swatch.hex.toLowerCase();
            return (
              <TouchableOpacity
                key={swatch.hex}
                style={[
                  styles.swatchItem,
                  isSelected && styles.swatchItemActive,
                ]}
                onPress={() => handleSelectColor(swatch.hex, swatch.name)}
                activeOpacity={0.85}
              >
                <View
                  style={[
                    styles.swatchCircle,
                    { backgroundColor: swatch.hex },
                    swatch.hex === '#FFFBD4' && { borderWidth: 1, borderColor: Colors.champagneGold },
                  ]}
                />
                <Text
                  style={[
                    styles.swatchLabel,
                    isSelected && styles.swatchLabelActive,
                  ]}
                  numberOfLines={1}
                >
                  {swatch.name}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Background Style Section */}
      <View style={styles.cardSection}>
        <Text style={styles.cardSectionTitle}>CANVAS BACKDROP</Text>
        <Text style={styles.cardSectionSub}>
          Surface style behind the liquid simulation
        </Text>

        <View style={styles.bgChoiceRow}>
          <TouchableOpacity
            style={[
              styles.bgChoiceCard,
              settings.backgroundType === 'light' && styles.bgChoiceCardActive,
            ]}
            onPress={() => handleSelectBackgroundType('light', 'Soft Cream')}
            activeOpacity={0.8}
          >
            <View style={[styles.bgPreviewBox, { backgroundColor: '#FAF5E0', borderColor: Colors.champagneGold, borderWidth: 1 }]} />
            <Text style={styles.bgChoiceTitle}>Soft Cream</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.bgChoiceCard,
              settings.backgroundType === 'dark' && styles.bgChoiceCardActive,
            ]}
            onPress={() => handleSelectBackgroundType('dark', 'Charcoal Night')}
            activeOpacity={0.8}
          >
            <View style={[styles.bgPreviewBox, { backgroundColor: '#171311' }]} />
            <Text style={styles.bgChoiceTitle}>Charcoal Night</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.bgChoiceCard,
              settings.backgroundType === 'custom_color' && styles.bgChoiceCardActive,
            ]}
            onPress={() => {
              updateSettings({ backgroundType: 'custom_color', backgroundColorHex: '#FFFBD4' });
              showToast('Backdrop Updated', 'Canvas style set to Lemon Chiffon', '★', 'charcoal');
            }}
            activeOpacity={0.8}
          >
            <View
              style={[
                styles.bgPreviewBox,
                { backgroundColor: '#FFFBD4', borderColor: Colors.champagneGold, borderWidth: 1 },
              ]}
            />
            <Text style={styles.bgChoiceTitle}>Lemon Chiffon</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Background Image Section */}
      <View style={styles.cardSection}>
        <Text style={styles.cardSectionTitle}>BACKGROUND IMAGE</Text>
        <Text style={styles.cardSectionSub}>
          Choose your own image as the wallpaper background
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
                ? 'Rendered behind fluid simulation'
                : 'Using selected backdrop theme'}
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
              {settings.backgroundImageUri ? 'Change Image' : 'Choose Background Image'}
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

      {/* Fluid Properties Sliders */}
      <View style={styles.cardSection}>
        <Text style={styles.cardSectionTitle}>PHYSICS PROPERTIES</Text>
        <Text style={styles.cardSectionSub}>
          Fine-tune Navier-Stokes simulation dynamics
        </Text>

        <SettingSlider
          label="Water Volume"
          description="Pool depth and liquid quantity"
          value={settings.waterVolume}
          min={0.15}
          max={0.7}
          step={0.05}
          unit="×"
          onValueChange={(v) => updateSettings({ waterVolume: v })}
        />

        <SettingSlider
          label="Gravity Sensitivity"
          description="Tilt response speed and inertial velocity"
          value={settings.physicsSensitivity}
          min={0.5}
          max={2.5}
          step={0.1}
          unit="×"
          onValueChange={(v) => updateSettings({ physicsSensitivity: v })}
        />

        <SettingSlider
          label="Shake & Splash Sensitivity"
          description="Threshold for droplets detaching from the pool"
          value={settings.shakeSensitivity}
          min={0.5}
          max={2.5}
          step={0.1}
          unit="×"
          onValueChange={(v) => updateSettings({ shakeSensitivity: v })}
        />
      </View>

      {/* Buttons */}
      <View style={styles.actionButtonsCol}>
        <TouchableOpacity
          style={styles.btnPrimary}
          onPress={() => router.push('/preview')}
          activeOpacity={0.85}
        >
          <Text style={styles.btnPrimaryText}>Test in Fullscreen Preview →</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.resetButton}
          onPress={handleReset}
          activeOpacity={0.8}
        >
          <Text style={styles.resetButtonText}>Restore Authentic Defaults</Text>
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
    marginBottom: 14,
  },
  swatchesRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  swatchItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 10,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  swatchItemActive: {
    borderColor: Colors.milanoRed,
    backgroundColor: Colors.softCream,
  },
  swatchCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    marginBottom: 6,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
    elevation: 2,
  },
  swatchLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: Colors.warmGrey,
    textAlign: 'center',
  },
  swatchLabelActive: {
    color: Colors.milanoRed,
    fontWeight: '800',
  },
  bgChoiceRow: {
    flexDirection: 'row',
    gap: 10,
  },
  bgChoiceCard: {
    flex: 1,
    backgroundColor: Colors.softCream,
    borderRadius: 14,
    padding: 10,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  bgChoiceCardActive: {
    borderColor: Colors.milanoRed,
    backgroundColor: Colors.warmWhite,
  },
  bgPreviewBox: {
    width: '100%',
    height: 40,
    borderRadius: 8,
    marginBottom: 6,
  },
  bgChoiceTitle: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.deepCharcoal,
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
