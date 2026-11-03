import React, { useEffect, useRef, useState } from 'react';
import {
  View,
  Image,
  StyleSheet,
  PanResponder,
  GestureResponderEvent,
  LayoutChangeEvent,
} from 'react-native';
import { Accelerometer } from 'expo-sensors';
import { FloatingSticksEngine, FloatingStick } from './FloatingSticksEngine';
import { SensorFilter } from '../../../core/sensors/SensorFilter';
import { WallpaperSettings } from '../base/wallpaper';

const STICK_IMAGES: Record<number, any> = {
  1: require('../../../../assets/stickers/stick_twig_1.png'),
  2: require('../../../../assets/stickers/stick_twig_2.png'),
  3: require('../../../../assets/stickers/stick_twig_3.png'),
  4: require('../../../../assets/stickers/stick_twig_4.png'),
  5: require('../../../../assets/stickers/stick_twig_5.png'),
  6: require('../../../../assets/stickers/stick_twig_6.png'),
};

interface Ripple {
  id: number;
  x: number;
  y: number;
  radius: number;
  opacity: number;
}

interface SticksSimulationViewProps {
  settings: WallpaperSettings;
  enableInteractiveTouch?: boolean;
  enableSensors?: boolean;
  style?: any;
  onStatsUpdate?: (stats: { fps: number; sticks: number; kineticEnergy: number }) => void;
}

export const SticksSimulationView: React.FC<SticksSimulationViewProps> = ({
  settings,
  enableInteractiveTouch = true,
  enableSensors = true,
  style,
  onStatsUpdate,
}) => {
  const [dimensions, setDimensions] = useState({ width: 360, height: 640 });
  const [sticks, setSticks] = useState<readonly FloatingStick[]>([]);
  const [ripples, setRipples] = useState<Ripple[]>([]);
  const sticksEngineRef = useRef<FloatingSticksEngine | null>(null);
  const sensorFilterRef = useRef<SensorFilter>(new SensorFilter());
  const animationFrameRef = useRef<number | null>(null);
  const lastTimeRef = useRef<number>(Date.now());
  const ripplesRef = useRef<Ripple[]>([]);
  const rippleIdCounter = useRef(0);

  const gravityRef = useRef<{ gx: number; gy: number }>({ gx: 0, gy: 980 });

  const handleLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width > 0 && height > 0) {
      setDimensions({ width, height });
      if (!sticksEngineRef.current) {
        sticksEngineRef.current = new FloatingSticksEngine(
          width,
          height,
          settings.stickCount ?? 5,
          settings.stickSize ?? 1.0
        );
        setSticks([...sticksEngineRef.current.getSticks()]);
      } else {
        sticksEngineRef.current.setDimensions(width, height);
      }
    }
  };

  // Re-sync sticks when count or size changes in customization
  useEffect(() => {
    if (sticksEngineRef.current && dimensions.width > 0) {
      sticksEngineRef.current.setConfig(
        settings.stickCount ?? 5,
        settings.stickSize ?? 1.0
      );
      setSticks([...sticksEngineRef.current.getSticks()]);
    }
  }, [settings.stickCount, settings.stickSize, dimensions.width, dimensions.height]);

  const spawnRipple = (x: number, y: number) => {
    rippleIdCounter.current++;
    ripplesRef.current.push({
      id: rippleIdCounter.current,
      x,
      y,
      radius: 10,
      opacity: 0.6,
    });
    if (ripplesRef.current.length > 8) {
      ripplesRef.current.shift();
    }
  };

  // Sensor Listener: Only updates target gravity vector and handles shake impulse (ZERO step calls here!)
  useEffect(() => {
    if (!enableSensors) return;

    let subscription: { remove: () => void } | null = null;
    try {
      Accelerometer.setUpdateInterval(1000 / settings.sensorPollingRateHz);
      subscription = Accelerometer.addListener((data) => {
        if (!sticksEngineRef.current) return;
        const filtered = sensorFilterRef.current.processAccelerometer(
          { x: data.x * 9.8, y: -data.y * 9.8, z: data.z * 9.8 },
          0
        );

        // Update smoothed gravity direction with deadzone to eliminate idle tremor
        const speedMultiplier = settings.stickSpeed ?? 1.0;
        let gx = filtered.gravityX * 100 * speedMultiplier;
        let gy = filtered.gravityY * 100 * speedMultiplier;
        if (Math.abs(gx) < 14) gx = 0;
        if (Math.abs(gy - 980 * speedMultiplier) < 22) gy = 980 * speedMultiplier;

        gravityRef.current = { gx, gy };

        if (filtered.isShakeTriggered) {
          const impulse = filtered.shakeMagnitude * 35 * (settings.shakeSensitivity ?? 1.0);
          sticksEngineRef.current.applyShakeImpulse(
            (Math.random() - 0.5) * impulse,
            -impulse * 0.8
          );
        }
      });
    } catch (e) {
      console.warn('Sensors unavailable:', e);
    }

    return () => {
      subscription?.remove();
    };
  }, [enableSensors, settings.stickSpeed, settings.shakeSensitivity, settings.sensorPollingRateHz]);

  // Main Animation Loop
  useEffect(() => {
    let frameCount = 0;
    let lastFpsCheck = Date.now();

    const loop = () => {
      const now = Date.now();
      const dt = Math.min(0.033, Math.max(0.008, (now - lastTimeRef.current) / 1000));
      lastTimeRef.current = now;

      if (sticksEngineRef.current) {
        // Step sticks physics with single coherent gravity vector
        const currentGrav = gravityRef.current;
        const speed = settings.stickSpeed ?? 1.0;
        const fluidProxy = {
          getGravityX: () => currentGrav.gx,
          getGravityY: () => currentGrav.gy,
          sampleFluidAt: () => ({ density: 0, velocityX: 0, velocityY: 0, isSubmerged: false }),
        };

        sticksEngineRef.current.step(dt * speed, fluidProxy as any);
        setSticks([...sticksEngineRef.current.getSticks()]);

        // Update expanding ripples
        if (ripplesRef.current.length > 0) {
          for (let i = ripplesRef.current.length - 1; i >= 0; i--) {
            const r = ripplesRef.current[i];
            r.radius += dt * 90;
            r.opacity -= dt * 0.8;
            if (r.opacity <= 0) {
              ripplesRef.current.splice(i, 1);
            }
          }
          setRipples([...ripplesRef.current]);
        }

        frameCount++;
        if (now - lastFpsCheck >= 1000) {
          const fps = Math.round((frameCount * 1000) / (now - lastFpsCheck));
          let energy = 0;
          for (const s of sticksEngineRef.current.getSticks()) {
            energy += s.vx * s.vx + s.vy * s.vy;
          }
          onStatsUpdate?.({
            fps,
            sticks: sticksEngineRef.current.getSticks().length,
            kineticEnergy: Math.round(energy / 100),
          });
          frameCount = 0;
          lastFpsCheck = now;
        }
      }

      animationFrameRef.current = requestAnimationFrame(loop);
    };

    animationFrameRef.current = requestAnimationFrame(loop);
    return () => {
      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, [onStatsUpdate, settings.stickSpeed]);

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => enableInteractiveTouch,
      onMoveShouldSetPanResponder: () => enableInteractiveTouch,
      onPanResponderGrant: (evt: GestureResponderEvent) => {
        const { locationX, locationY } = evt.nativeEvent;
        sticksEngineRef.current?.addTouchDisturbance(locationX, locationY, 800);
        spawnRipple(locationX, locationY);
      },
      onPanResponderMove: (evt: GestureResponderEvent) => {
        const { locationX, locationY } = evt.nativeEvent;
        sticksEngineRef.current?.addTouchDisturbance(locationX, locationY, 600);
        if (Math.random() < 0.25) {
          spawnRipple(locationX, locationY);
        }
      },
    })
  ).current;

  const getBackgroundColor = () => {
    if (settings.backgroundType === 'dark') return '#1A1816';
    if (settings.backgroundType === 'custom_color') return settings.backgroundColorHex;
    return '#F5EFE6'; // Elegant natural warm cream wood canvas
  };

  return (
    <View
      style={[styles.container, { backgroundColor: getBackgroundColor() }, style]}
      onLayout={handleLayout}
      {...(enableInteractiveTouch ? panResponder.panHandlers : {})}
    >
      {settings.backgroundImageUri ? (
        <Image
          source={{ uri: settings.backgroundImageUri }}
          style={StyleSheet.absoluteFill}
          resizeMode="cover"
        />
      ) : null}

      {/* Surface Water Ripples */}
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        {ripples.map((r) => (
          <View
            key={r.id}
            style={{
              position: 'absolute',
              left: r.x - r.radius,
              top: r.y - r.radius,
              width: r.radius * 2,
              height: r.radius * 2,
              borderRadius: r.radius,
              borderWidth: 1.5,
              borderColor: 'rgba(180, 150, 120, ' + r.opacity + ')',
            }}
          />
        ))}
      </View>

      {/* 3D Floating Wooden Sticks / Driftwood Twigs (Clean, No Pill Shadows) */}
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        {sticks.map((s) => {
          const imgHeight = s.radius * 2.4;
          return (
            <Image
              key={s.id}
              source={STICK_IMAGES[s.textureIndex] || STICK_IMAGES[1]}
              style={{
                position: 'absolute',
                left: s.x - s.length / 2,
                top: s.y - imgHeight / 2,
                width: s.length,
                height: imgHeight,
                transform: [{ rotate: `${s.angle}rad` }],
              }}
              resizeMode="contain"
            />
          );
        })}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
  },
});
