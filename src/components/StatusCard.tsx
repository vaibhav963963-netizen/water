import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Colors } from '../theme/colors';

interface StatusCardProps {
  isActive: boolean;
  onSetWallpaperPress: () => void;
}

export const StatusCard: React.FC<StatusCardProps> = ({
  isActive,
  onSetWallpaperPress,
}) => {
  return (
    <View style={styles.card}>
      <View style={styles.content}>
        <View style={styles.statusIndicatorRow}>
          <View
            style={[
              styles.statusDot,
              { backgroundColor: isActive ? Colors.success : Colors.warning },
            ]}
          />
          <Text style={styles.statusTitle}>
            {isActive ? 'Live Wallpaper Active' : 'Wallpaper Not Applied'}
          </Text>
        </View>
        <Text style={styles.statusSubtitle}>
          {isActive
            ? 'Water is currently flowing on your Android home screen'
            : 'Tap below to launch Android Live Wallpaper picker'}
        </Text>
      </View>

      {!isActive && (
        <TouchableOpacity
          style={styles.actionButton}
          onPress={onSetWallpaperPress}
          activeOpacity={0.8}
        >
          <Text style={styles.actionButtonText}>Set Now</Text>
        </TouchableOpacity>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.surface,
    borderRadius: 16,
    padding: 16,
    marginHorizontal: 16,
    marginVertical: 8,
    borderWidth: 1,
    borderColor: Colors.border,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 1,
    shadowRadius: 6,
    elevation: 2,
  },
  content: {
    flex: 1,
    paddingRight: 12,
  },
  statusIndicatorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  statusTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  statusSubtitle: {
    fontSize: 12,
    color: Colors.textSecondary,
    lineHeight: 16,
  },
  actionButton: {
    backgroundColor: Colors.primaryCyan,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
  },
  actionButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
});
