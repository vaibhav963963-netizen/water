import React, { createContext, useContext, useState, useRef, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  TouchableOpacity,
  Dimensions,
} from 'react-native';
import { Colors } from '../theme/colors';

interface ToastData {
  id: number;
  title: string;
  subtitle?: string;
  icon?: string;
  variant?: 'milano' | 'gold' | 'charcoal';
}

interface ToastContextValue {
  showToast: (title: string, subtitle?: string, icon?: string, variant?: 'milano' | 'gold' | 'charcoal') => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toast, setToast] = useState<ToastData | null>(null);
  const translateY = useRef(new Animated.Value(-80)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const hideTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  const hideToast = useCallback(() => {
    Animated.parallel([
      Animated.timing(translateY, {
        toValue: -80,
        duration: 220,
        useNativeDriver: true,
      }),
      Animated.timing(opacity, {
        toValue: 0,
        duration: 180,
        useNativeDriver: true,
      }),
    ]).start(() => {
      setToast(null);
    });
  }, [translateY, opacity]);

  const showToast = useCallback(
    (
      title: string,
      subtitle?: string,
      icon = '✓',
      variant: 'milano' | 'gold' | 'charcoal' = 'charcoal'
    ) => {
      if (hideTimeoutRef.current) {
        clearTimeout(hideTimeoutRef.current);
      }

      setToast({
        id: Date.now(),
        title,
        subtitle,
        icon,
        variant,
      });

      translateY.setValue(-80);
      opacity.setValue(0);

      Animated.parallel([
        Animated.spring(translateY, {
          toValue: 0,
          friction: 7,
          tension: 65,
          useNativeDriver: true,
        }),
        Animated.timing(opacity, {
          toValue: 1,
          duration: 200,
          useNativeDriver: true,
        }),
      ]).start();

      hideTimeoutRef.current = setTimeout(() => {
        hideToast();
      }, 2600);
    },
    [translateY, opacity, hideToast]
  );

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {toast && (
        <Animated.View
          style={[
            styles.toastWrapper,
            {
              transform: [{ translateY }],
              opacity,
            },
          ]}
          pointerEvents="box-none"
        >
          <TouchableOpacity
            style={[
              styles.toastPill,
              toast.variant === 'milano' && styles.toastPillMilano,
              toast.variant === 'gold' && styles.toastPillGold,
            ]}
            onPress={hideToast}
            activeOpacity={0.9}
          >
            {/* Left Icon Badge */}
            <View
              style={[
                styles.iconBadge,
                toast.variant === 'milano' && styles.iconBadgeMilano,
              ]}
            >
              <Text
                style={[
                  styles.iconText,
                  toast.variant === 'milano' && styles.iconTextMilano,
                ]}
              >
                {toast.icon}
              </Text>
            </View>

            {/* Message Body */}
            <View style={styles.textColumn}>
              <Text
                style={[
                  styles.toastTitle,
                  toast.variant === 'milano' && styles.toastTitleMilano,
                ]}
                numberOfLines={1}
              >
                {toast.title}
              </Text>
              {toast.subtitle ? (
                <Text
                  style={[
                    styles.toastSubtitle,
                    toast.variant === 'milano' && styles.toastSubtitleMilano,
                  ]}
                  numberOfLines={2}
                >
                  {toast.subtitle}
                </Text>
              ) : null}
            </View>

            {/* Dismiss Cross */}
            <View style={styles.closeWrap}>
              <Text style={styles.closeText}>✕</Text>
            </View>
          </TouchableOpacity>
        </Animated.View>
      )}
    </ToastContext.Provider>
  );
};

const screenWidth = Dimensions.get('window').width;

const styles = StyleSheet.create({
  toastWrapper: {
    position: 'absolute',
    top: 50,
    left: 16,
    right: 16,
    alignItems: 'center',
    zIndex: 9999,
  },
  toastPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(41, 36, 33, 0.94)',
    borderRadius: 24,
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
    shadowColor: Colors.shadow,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.22,
    shadowRadius: 12,
    elevation: 8,
    maxWidth: 420,
    width: '100%',
  },
  toastPillMilano: {
    backgroundColor: Colors.milanoRed,
    borderColor: Colors.champagneGold,
  },
  toastPillGold: {
    backgroundColor: Colors.lemonChiffon,
    borderColor: Colors.champagneGold,
  },
  iconBadge: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: Colors.milanoRed,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
    borderWidth: 1,
    borderColor: Colors.champagneGold,
  },
  iconBadgeMilano: {
    backgroundColor: Colors.lemonChiffon,
  },
  iconText: {
    fontSize: 14,
    fontWeight: 'bold',
    color: Colors.lemonChiffon,
  },
  iconTextMilano: {
    color: Colors.milanoRed,
  },
  textColumn: {
    flex: 1,
    marginRight: 8,
  },
  toastTitle: {
    fontSize: 13,
    fontWeight: '800',
    color: Colors.lemonChiffon,
    letterSpacing: 0.2,
  },
  toastTitleMilano: {
    color: Colors.lemonChiffon,
  },
  toastSubtitle: {
    fontSize: 11,
    color: Colors.champagneGold,
    marginTop: 2,
    lineHeight: 15,
  },
  toastSubtitleMilano: {
    color: Colors.lemonChiffonMuted,
  },
  closeWrap: {
    padding: 4,
    justifyContent: 'center',
    alignItems: 'center',
  },
  closeText: {
    fontSize: 12,
    color: Colors.champagneGold,
    opacity: 0.7,
  },
});
