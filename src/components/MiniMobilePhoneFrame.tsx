import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  Animated,
  Easing,
  TouchableOpacity,
} from 'react-native';
import { WallpaperSettings } from '../features/wallpapers/base/wallpaper';

const STICK_IMAGES: Record<number, any> = {
  1: require('../../assets/stickers/stick_twig_1.png'),
  2: require('../../assets/stickers/stick_twig_2.png'),
  3: require('../../assets/stickers/stick_twig_3.png'),
  4: require('../../assets/stickers/stick_twig_4.png'),
  5: require('../../assets/stickers/stick_twig_5.png'),
  6: require('../../assets/stickers/stick_twig_6.png'),
};

interface MiniMobilePhoneFrameProps {
  type: 'water' | 'sticks';
  settings: WallpaperSettings;
  width?: number;
  height?: number;
  onPress?: () => void;
}

export const MiniMobilePhoneFrame: React.FC<MiniMobilePhoneFrameProps> = ({
  type,
  settings,
  width = 136,
  height = 226,
  onPress,
}) => {
  // Wave & stick bobbing animations
  const waveAnim = useRef(new Animated.Value(0)).current;
  const stickBobAnim = useRef(new Animated.Value(0)).current;
  const rippleAnim = useRef(new Animated.Value(0)).current;
  const bubbleAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    // 1. Continuous wave loop
    const waveLoop = Animated.loop(
      Animated.timing(waveAnim, {
        toValue: 1,
        duration: 3200,
        easing: Easing.linear,
        useNativeDriver: true,
      })
    );
    waveLoop.start();

    // 2. Harmonic stick bobbing loop
    const stickLoop = Animated.loop(
      Animated.sequence([
        Animated.timing(stickBobAnim, {
          toValue: 1,
          duration: 1800,
          easing: Easing.inOut(Easing.sin),
          useNativeDriver: true,
        }),
        Animated.timing(stickBobAnim, {
          toValue: 0,
          duration: 1800,
          easing: Easing.inOut(Easing.sin),
          useNativeDriver: true,
        }),
      ])
    );
    stickLoop.start();

    // 3. Gentle bubble rise loop
    const bubbleLoop = Animated.loop(
      Animated.timing(bubbleAnim, {
        toValue: 1,
        duration: 2600,
        easing: Easing.out(Easing.quad),
        useNativeDriver: true,
      })
    );
    bubbleLoop.start();

    return () => {
      waveLoop.stop();
      stickLoop.stop();
      bubbleLoop.stop();
    };
  }, [waveAnim, stickBobAnim, bubbleAnim]);

  const handlePress = () => {
    // Trigger interactive ripple pulse
    rippleAnim.setValue(0);
    Animated.timing(rippleAnim, {
      toValue: 1,
      duration: 650,
      easing: Easing.out(Easing.ease),
      useNativeDriver: true,
    }).start();

    onPress?.();
  };

  // Interpolations
  const waveTranslateX1 = waveAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [-24, 24],
  });
  const waveTranslateX2 = waveAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [20, -20],
  });
  const waveBreathing = waveAnim.interpolate({
    inputRange: [0, 0.5, 1],
    outputRange: [0, -3, 0],
  });

  const stick1Y = stickBobAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [-3, 4],
  });
  const stick1Rot = stickBobAnim.interpolate({
    inputRange: [0, 1],
    outputRange: ['-16deg', '-9deg'],
  });

  const stick2Y = stickBobAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [4, -3],
  });
  const stick2Rot = stickBobAnim.interpolate({
    inputRange: [0, 1],
    outputRange: ['28deg', '36deg'],
  });

  const stick3Y = stickBobAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [-2, 3],
  });

  const bubble1Y = bubbleAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [20, -50],
  });
  const bubble1Opacity = bubbleAnim.interpolate({
    inputRange: [0, 0.2, 0.8, 1],
    outputRange: [0, 0.8, 0.8, 0],
  });

  const rippleScale = rippleAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [0.2, 2.4],
  });
  const rippleOpacity = rippleAnim.interpolate({
    inputRange: [0, 0.7, 1],
    outputRange: [0.65, 0.25, 0],
  });

  const waterColor = settings.waterColorHex || '#1FA2FF';
  const waterSecColor = settings.waterColorSecondaryHex || '#12D8FA';
  const bgColor = settings.backgroundColorHex || '#F7F4EB';
  const imageUri = type === 'sticks' 
    ? (settings.stickBackgroundImageUri || settings.backgroundImageUri) 
    : settings.backgroundImageUri;
  const hasCustomBg = !!imageUri;

  return (
    <TouchableOpacity
      activeOpacity={0.92}
      onPress={handlePress}
      style={[
        styles.outerChassis,
        {
          width,
          height,
          borderRadius: Math.round(width * 0.17),
        },
      ]}
    >
      {/* Dynamic Island / Top Notch Speaker */}
      <View style={styles.dynamicIsland}>
        <View style={styles.cameraDot} />
        <View style={styles.speakerSlit} />
      </View>

      {/* Screen Area inside Bezel */}
      <View
        style={[
          styles.screenSurface,
          {
            borderRadius: Math.round(width * 0.14),
            backgroundColor: bgColor,
          },
        ]}
      >
        {/* Custom Background Image if active */}
        {hasCustomBg && (
          <Image
            source={{ uri: imageUri! }}
            style={StyleSheet.absoluteFillObject}
            resizeMode="cover"
          />
        )}

        {/* Status Bar: Time & Battery / Signal */}
        <View style={styles.statusBar}>
          <Text style={styles.statusTimeText}>9:41</Text>
          <View style={styles.statusIconsRow}>
            <View style={styles.signalBar} />
            <View style={[styles.signalBar, { height: 5 }]} />
            <View style={[styles.signalBar, { height: 7 }]} />
            <View style={styles.batteryPill}>
              <View style={styles.batteryLevel} />
            </View>
          </View>
        </View>

        {/* -------------------------------------------------------------
            A. WATER WALLPAPER LIVE SIMULATION PREVIEW
            ------------------------------------------------------------- */}
        {type === 'water' && (
          <View style={StyleSheet.absoluteFillObject} pointerEvents="none">
            {/* Ambient Water Drop Sticker Icon in Air */}
            <View style={styles.floatingWaterStickerWrap}>
              <Image
                source={require('../../assets/stickers/sticker_water_drop_3d.png')}
                style={{ width: width * 0.28, height: width * 0.28 }}
                resizeMode="contain"
              />
            </View>

            {/* Rising Bubbles */}
            <Animated.View
              style={[
                styles.bubble,
                {
                  left: width * 0.35,
                  bottom: height * 0.22,
                  transform: [{ translateY: bubble1Y }],
                  opacity: bubble1Opacity,
                },
              ]}
            />
            <Animated.View
              style={[
                styles.bubble,
                {
                  left: width * 0.65,
                  bottom: height * 0.26,
                  width: 5,
                  height: 5,
                  transform: [{ translateY: bubble1Y }],
                  opacity: bubble1Opacity,
                },
              ]}
            />

            {/* Bottom Liquid Reservoir */}
            <View style={[styles.liquidPoolContainer, { height: height * 0.44 }]}>
              {/* Back Wave Layer (Secondary Color) */}
              <Animated.View
                style={[
                  styles.waveCrestLayer,
                  {
                    backgroundColor: waterSecColor,
                    opacity: 0.62,
                    transform: [
                      { translateX: waveTranslateX2 },
                      { translateY: waveBreathing },
                    ],
                  },
                ]}
              />

              {/* Front Wave Layer (Primary Water Color) */}
              <Animated.View
                style={[
                  styles.waveCrestLayer,
                  {
                    backgroundColor: waterColor,
                    opacity: 0.88,
                    transform: [
                      { translateX: waveTranslateX1 },
                    ],
                  },
                ]}
              />

              {/* Luminous Surface Highlight Rim */}
              <View
                style={[
                  styles.surfaceHighlightRim,
                  {
                    borderColor: 'rgba(255, 255, 255, 0.75)',
                    shadowColor: waterSecColor,
                    opacity: Math.min(1.0, 0.6 + settings.glowIntensity * 0.4),
                  },
                ]}
              />

              {/* Liquid Solid Core */}
              <View
                style={[
                  styles.liquidCore,
                  { backgroundColor: waterColor, opacity: 0.94 },
                ]}
              />
            </View>
          </View>
        )}

        {/* -------------------------------------------------------------
            B. FLOATING STICKS WALLPAPER LIVE SIMULATION PREVIEW
            ------------------------------------------------------------- */}
        {type === 'sticks' && (
          <View style={StyleSheet.absoluteFillObject} pointerEvents="none">
            {/* Translucent water depth base at bottom */}
            <View
              style={[
                styles.sticksWaterBase,
                {
                  height: height * 0.50,
                  backgroundColor: 'rgba(125, 200, 225, 0.25)',
                },
              ]}
            >
              {/* Water surface line */}
              <View style={styles.sticksWaterLine} />
            </View>

            {/* Twig 1 (Top Center Driftwood) */}
            <Animated.View
              style={[
                styles.floatingStickWrap,
                {
                  top: height * 0.28,
                  left: width * 0.15,
                  transform: [{ translateY: stick1Y }, { rotate: stick1Rot }],
                },
              ]}
            >
              {/* Soft Water Shadow */}
              <View style={[styles.stickWaterShadow, { width: width * 0.52, height: 8 }]} />
              <Image
                source={STICK_IMAGES[1]}
                style={{ width: width * 0.52, height: width * 0.18 }}
                resizeMode="contain"
              />
            </Animated.View>

            {/* Twig 2 (Mid Right Driftwood) */}
            <Animated.View
              style={[
                styles.floatingStickWrap,
                {
                  top: height * 0.48,
                  right: width * 0.10,
                  transform: [{ translateY: stick2Y }, { rotate: stick2Rot }],
                },
              ]}
            >
              <View style={[styles.stickWaterShadow, { width: width * 0.46, height: 7 }]} />
              <Image
                source={STICK_IMAGES[2]}
                style={{ width: width * 0.46, height: width * 0.16 }}
                resizeMode="contain"
              />
            </Animated.View>

            {/* Twig 3 (Bottom Left Small Twig) */}
            <Animated.View
              style={[
                styles.floatingStickWrap,
                {
                  top: height * 0.68,
                  left: width * 0.18,
                  transform: [{ translateY: stick3Y }, { rotate: '14deg' }],
                },
              ]}
            >
              <View style={[styles.stickWaterShadow, { width: width * 0.38, height: 6 }]} />
              <Image
                source={STICK_IMAGES[4]}
                style={{ width: width * 0.38, height: width * 0.14 }}
                resizeMode="contain"
              />
            </Animated.View>
          </View>
        )}

        {/* Interactive Touch Splash Ripple */}
        <Animated.View
          style={[
            styles.touchRippleCircle,
            {
              left: width * 0.5 - 30,
              top: height * 0.5 - 30,
              transform: [{ scale: rippleScale }],
              opacity: rippleOpacity,
            },
          ]}
        />

        {/* Glass Screen Diagonal Glare Sheen */}
        <View style={styles.glassGlareOverlay} pointerEvents="none" />

        {/* Bottom Home Indicator Bar */}
        <View style={styles.homeIndicatorBar} />
      </View>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  outerChassis: {
    backgroundColor: '#1E1B18', // Sleek Titanium/Dark Bronze Chassis
    borderWidth: 2.5,
    borderColor: '#3D352E',
    borderTopColor: '#584E44',
    padding: 3.5,
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.28,
    shadowRadius: 10,
    elevation: 6,
    position: 'relative',
    alignSelf: 'center',
  },
  dynamicIsland: {
    position: 'absolute',
    top: 6,
    alignSelf: 'center',
    width: 34,
    height: 7.5,
    borderRadius: 4,
    backgroundColor: '#0D0B0A',
    zIndex: 30,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 5,
  },
  cameraDot: {
    width: 3,
    height: 3,
    borderRadius: 1.5,
    backgroundColor: '#1E293B',
    borderWidth: 0.5,
    borderColor: '#0F172A',
  },
  speakerSlit: {
    width: 12,
    height: 1.5,
    borderRadius: 1,
    backgroundColor: '#1C1917',
  },
  screenSurface: {
    flex: 1,
    overflow: 'hidden',
    position: 'relative',
  },
  statusBar: {
    height: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
    paddingTop: 2,
    zIndex: 20,
  },
  statusTimeText: {
    fontSize: 7.5,
    fontWeight: '800',
    color: '#292421',
  },
  statusIconsRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 2,
  },
  signalBar: {
    width: 1.5,
    height: 3.5,
    backgroundColor: '#292421',
    borderRadius: 0.5,
  },
  batteryPill: {
    width: 9,
    height: 4.5,
    borderRadius: 1.5,
    borderWidth: 0.8,
    borderColor: '#292421',
    padding: 0.5,
    marginLeft: 1,
  },
  batteryLevel: {
    flex: 1,
    backgroundColor: '#292421',
    borderRadius: 0.5,
  },
  glassGlareOverlay: {
    position: 'absolute',
    top: -40,
    right: -40,
    width: 110,
    height: 110,
    backgroundColor: 'rgba(255, 255, 255, 0.07)',
    transform: [{ rotate: '35deg' }],
    zIndex: 25,
  },
  homeIndicatorBar: {
    position: 'absolute',
    bottom: 4,
    alignSelf: 'center',
    width: 32,
    height: 2.5,
    borderRadius: 1.5,
    backgroundColor: 'rgba(255, 255, 255, 0.55)',
    zIndex: 25,
  },
  touchRippleCircle: {
    position: 'absolute',
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 2,
    borderColor: 'rgba(255, 255, 255, 0.9)',
    zIndex: 22,
  },

  /* Water Specific Styles */
  floatingWaterStickerWrap: {
    position: 'absolute',
    top: '18%',
    alignSelf: 'center',
    opacity: 0.88,
  },
  bubble: {
    position: 'absolute',
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.75)',
    borderWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.9)',
  },
  liquidPoolContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    overflow: 'hidden',
  },
  waveCrestLayer: {
    position: 'absolute',
    top: -14,
    left: -20,
    right: -20,
    height: 38,
    borderRadius: 22,
  },
  surfaceHighlightRim: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 2,
    borderTopWidth: 1.5,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.8,
    shadowRadius: 3,
  },
  liquidCore: {
    position: 'absolute',
    top: 10,
    bottom: 0,
    left: 0,
    right: 0,
  },

  /* Sticks Specific Styles */
  sticksWaterBase: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
  },
  sticksWaterLine: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.65)',
  },
  floatingStickWrap: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stickWaterShadow: {
    position: 'absolute',
    bottom: -3,
    backgroundColor: 'rgba(35, 25, 18, 0.18)',
    borderRadius: 4,
  },
});
