import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Colors } from '../theme/colors';

interface SettingSliderProps {
  label: string;
  description?: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  unit?: string;
  onValueChange: (val: number) => void;
}

export const SettingSlider: React.FC<SettingSliderProps> = ({
  label,
  description,
  value,
  min,
  max,
  step = 0.05,
  unit = '',
  onValueChange,
}) => {
  const percentage = Math.min(100, Math.max(0, ((value - min) / (max - min)) * 100));

  const handleStepDown = () => {
    const next = Math.max(min, Math.round((value - step) * 100) / 100);
    onValueChange(next);
  };

  const handleStepUp = () => {
    const next = Math.min(max, Math.round((value + step) * 100) / 100);
    onValueChange(next);
  };

  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <View style={styles.textColumn}>
          <Text style={styles.label}>{label}</Text>
          {description ? <Text style={styles.description}>{description}</Text> : null}
        </View>
        <View style={styles.valueBadge}>
          <Text style={styles.valueText}>
            {typeof value === 'number' && Number.isInteger(value) ? value : value.toFixed(2)}
            {unit}
          </Text>
        </View>
      </View>

      {/* Interactive step bar */}
      <View style={styles.controlRow}>
        <TouchableOpacity
          style={styles.stepButton}
          onPress={handleStepDown}
          activeOpacity={0.7}
        >
          <Text style={styles.stepButtonText}>−</Text>
        </TouchableOpacity>

        <View style={styles.trackContainer}>
          <View style={[styles.filledTrack, { width: `${percentage}%` }]} />
        </View>

        <TouchableOpacity
          style={styles.stepButton}
          onPress={handleStepUp}
          activeOpacity={0.7}
        >
          <Text style={styles.stepButtonText}>+</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.surface,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  textColumn: {
    flex: 1,
    paddingRight: 12,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textPrimary,
  },
  description: {
    fontSize: 13,
    color: Colors.textSecondary,
    marginTop: 2,
    lineHeight: 18,
  },
  valueBadge: {
    backgroundColor: Colors.surfaceSubtle,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  valueText: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.primaryDark,
  },
  controlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  trackContainer: {
    flex: 1,
    height: 8,
    backgroundColor: Colors.surfaceSubtle,
    borderRadius: 4,
    overflow: 'hidden',
  },
  filledTrack: {
    height: '100%',
    backgroundColor: Colors.primaryCyan,
    borderRadius: 4,
  },
  stepButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: Colors.surfaceSubtle,
    borderWidth: 1,
    borderColor: Colors.border,
    justifyContent: 'center',
    alignItems: 'center',
  },
  stepButtonText: {
    fontSize: 20,
    fontWeight: 'bold',
    color: Colors.textPrimary,
    lineHeight: 22,
  },
});
