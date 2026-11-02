import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  TextInput,
} from 'react-native';
import { Colors } from '../theme/colors';

interface ColorPickerModalProps {
  visible: boolean;
  title: string;
  currentColor: string;
  onSelectColor: (color: string) => void;
  onClose: () => void;
}

const PALETTE = [
  '#00E5FF', // Live WaterPaper Cyan
  '#00B4D8', // Azure
  '#0077B6', // Deep Ocean
  '#00E676', // Emerald
  '#76FF03', // Neon Lime
  '#FF5252', // Coral Red
  '#FF7043', // Warm Sunset
  '#FFD600', // Liquid Gold
  '#D500F9', // Electric Magenta
  '#7C4DFF', // Deep Violet
  '#F2F5F8', // Pure Light
  '#0F172A', // Midnight Slate
];

export const ColorPickerModal: React.FC<ColorPickerModalProps> = ({
  visible,
  title,
  currentColor,
  onSelectColor,
  onClose,
}) => {
  const [customHex, setCustomHex] = useState(currentColor);

  const handleApplyCustom = () => {
    let clean = customHex.trim();
    if (!clean.startsWith('#')) clean = `#${clean}`;
    if (/^#[0-9A-Fa-f]{6}$/.test(clean)) {
      onSelectColor(clean);
      onClose();
    }
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.modalContent}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>Select from authentic liquid shades or enter custom hex</Text>

          {/* Palette Grid */}
          <View style={styles.paletteGrid}>
            {PALETTE.map((color) => {
              const isSelected = currentColor.toUpperCase() === color.toUpperCase();
              return (
                <TouchableOpacity
                  key={color}
                  style={[
                    styles.colorSwatch,
                    { backgroundColor: color },
                    isSelected && styles.colorSwatchSelected,
                  ]}
                  onPress={() => {
                    onSelectColor(color);
                    onClose();
                  }}
                  activeOpacity={0.8}
                >
                  {isSelected && <View style={styles.selectedIndicator} />}
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Custom Hex Input */}
          <View style={styles.hexInputRow}>
            <View style={[styles.previewSquare, { backgroundColor: customHex || currentColor }]} />
            <TextInput
              style={styles.input}
              value={customHex}
              onChangeText={setCustomHex}
              placeholder="#00E5FF"
              placeholderTextColor={Colors.textMuted}
              autoCapitalize="characters"
              maxLength={7}
            />
            <TouchableOpacity style={styles.applyButton} onPress={handleApplyCustom}>
              <Text style={styles.applyButtonText}>Apply</Text>
            </TouchableOpacity>
          </View>

          {/* Close button */}
          <TouchableOpacity style={styles.closeButton} onPress={onClose}>
            <Text style={styles.closeButtonText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  modalContent: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: Colors.surface,
    borderRadius: 24,
    padding: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.15,
    shadowRadius: 20,
    elevation: 8,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textPrimary,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: Colors.textSecondary,
    marginBottom: 20,
  },
  paletteGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  colorSwatch: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 2,
    borderColor: 'transparent',
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  colorSwatchSelected: {
    borderColor: Colors.textPrimary,
    transform: [{ scale: 1.1 }],
  },
  selectedIndicator: {
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: Colors.surface,
  },
  hexInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 16,
  },
  previewSquare: {
    width: 42,
    height: 42,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  input: {
    flex: 1,
    height: 42,
    backgroundColor: Colors.surfaceSubtle,
    borderRadius: 10,
    paddingHorizontal: 12,
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textPrimary,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  applyButton: {
    backgroundColor: Colors.primaryDark,
    paddingHorizontal: 16,
    height: 42,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  applyButtonText: {
    color: Colors.textInverse,
    fontWeight: '700',
    fontSize: 14,
  },
  closeButton: {
    paddingVertical: 12,
    alignItems: 'center',
  },
  closeButtonText: {
    color: Colors.textSecondary,
    fontWeight: '600',
    fontSize: 15,
  },
});
