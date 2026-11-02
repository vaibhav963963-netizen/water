import React, { useEffect, useRef, useState } from 'react';
import {
  View,
  StyleSheet,
  PanResponder,
  GestureResponderEvent,
  LayoutChangeEvent,
  Platform,
  requireNativeComponent,
  ViewProps,
} from 'react-native';
import { Accelerometer } from 'expo-sensors';
import { FluidEngine } from '../physics/FluidEngine';
import { SensorFilter } from '../physics/SensorFilter';
import { WallpaperSettings } from '../types/wallpaper';
import { WallpaperBridge } from '../services/WallpaperBridge';

interface WaterPreviewNativeProps extends ViewProps {
  waterColor?: string;
  backgroundColor?: string;
  waterVolume?: number;
  physicsSensitivity?: number;
  shakeSensitivity?: number;
  glowIntensity?: number;
}

let NativeWaterPreviewView: React.ComponentType<WaterPreviewNativeProps> | null = null;
if (Platform.OS === 'android') {
  try {
    NativeWaterPreviewView = requireNativeComponent<WaterPreviewNativeProps>('WaterPreviewGlView');
  } catch (e) {
    NativeWaterPreviewView = null;
  }
}

interface WaterSimulationViewProps {
  settings: WallpaperSettings;
  enableInteractiveTouch?: boolean;
  enableSensors?: boolean;
  style?: any;
  onStatsUpdate?: (stats: { fps: number; particles: number; droplets: number }) => void;
}

export const WaterSimulationView: React.FC<WaterSimulationViewProps> = ({
  settings,
  enableInteractiveTouch = true,
  enableSensors = true,
  style,
  onStatsUpdate,
}) => {
  const [dimensions, setDimensions] = useState({ width: 360, height: 640 });
  const engineRef = useRef<FluidEngine | null>(null);
  const sensorFilterRef = useRef<SensorFilter>(new SensorFilter());
  const animationFrameRef = useRef<number | null>(null);
  const lastTimeRef = useRef<number>(Date.now());
  const [, setTick] = useState(0);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const glowCanvasRef = useRef<HTMLCanvasElement | null>(null);

  const handleLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width > 0 && height > 0) {
      setDimensions({ width, height });
      if (!engineRef.current) {
        engineRef.current = new FluidEngine(width, height, settings);
      } else {
        engineRef.current.setDimensions(width, height);
      }
    }
  };

  useEffect(() => {
    if (engineRef.current) {
      engineRef.current.initializeParticles(settings.waterVolume);
    }
  }, [settings.waterVolume]);

  useEffect(() => {
    if (!enableSensors) return;

    let subscription: { remove: () => void } | null = null;
    try {
      Accelerometer.setUpdateInterval(1000 / settings.sensorPollingRateHz);
      subscription = Accelerometer.addListener((data) => {
        if (!engineRef.current) return;
        const filtered = sensorFilterRef.current.processAccelerometer(
          { x: data.x * 9.8, y: -data.y * 9.8, z: data.z * 9.8 },
          0
        );

        engineRef.current.updateGravity(
          filtered.gravityX,
          filtered.gravityY,
          settings.physicsSensitivity
        );

        if (filtered.isShakeTriggered) {
          const impulse = filtered.shakeMagnitude * 40 * settings.shakeSensitivity;
          engineRef.current.applyImpulse((Math.random() - 0.5) * impulse, -impulse * 0.7);
        }
      });
    } catch (e) {
      console.warn('Sensors unavailable:', e);
    }

    return () => {
      subscription?.remove();
    };
  }, [enableSensors, settings.physicsSensitivity, settings.shakeSensitivity, settings.sensorPollingRateHz]);

  useEffect(() => {
    let frameCount = 0;
    let lastFpsCheck = Date.now();

    const loop = () => {
      const now = Date.now();
      const dt = (now - lastTimeRef.current) / 1000;
      lastTimeRef.current = now;

      if (engineRef.current) {
        engineRef.current.step(dt);
        frameCount++;

        if (now - lastFpsCheck >= 1000) {
          const fps = Math.round((frameCount * 1000) / (now - lastFpsCheck));
          const stats = engineRef.current.getStats();
          onStatsUpdate?.({
            fps,
            particles: stats.particleCount,
            droplets: stats.dropletCount,
          });
          frameCount = 0;
          lastFpsCheck = now;
        }

        // If Web Canvas is available, draw continuous metaball liquid
        if (Platform.OS === 'web' && canvasRef.current) {
          const ctx = canvasRef.current.getContext('2d');
          const glowCtx = glowCanvasRef.current?.getContext('2d');
          const particles = engineRef.current.getParticles();

          if (ctx) {
            ctx.clearRect(0, 0, dimensions.width, dimensions.height);
            ctx.fillStyle = settings.waterColorHex;

            for (let i = 0; i < particles.length; i++) {
              const p = particles[i];
              ctx.beginPath();
              ctx.arc(p.x, p.y, p.radius * 1.25, 0, Math.PI * 2);
              ctx.fill();
            }
          }

          if (glowCtx) {
            glowCtx.clearRect(0, 0, dimensions.width, dimensions.height);
            if (settings.glowIntensity > 0.05) {
              glowCtx.fillStyle = settings.waterColorHex;
              for (let i = 0; i < particles.length; i++) {
                const p = particles[i];
                glowCtx.beginPath();
                glowCtx.arc(p.x, p.y, p.radius * 1.55, 0, Math.PI * 2);
                glowCtx.fill();
              }
            }
          }
        }

        setTick((t) => (t + 1) % 100000);
      }

      animationFrameRef.current = requestAnimationFrame(loop);
    };

    animationFrameRef.current = requestAnimationFrame(loop);
    return () => {
      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, [onStatsUpdate, dimensions, settings.waterColorHex, settings.glowIntensity]);

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => enableInteractiveTouch,
      onMoveShouldSetPanResponder: () => enableInteractiveTouch,
      onPanResponderGrant: (evt: GestureResponderEvent) => {
        const { locationX, locationY } = evt.nativeEvent;
        engineRef.current?.addTouchDisturbance(locationX, locationY, 650);
      },
      onPanResponderMove: (evt: GestureResponderEvent) => {
        const { locationX, locationY } = evt.nativeEvent;
        engineRef.current?.addTouchDisturbance(locationX, locationY, 450);
      },
    })
  ).current;

  const getBackgroundColor = () => {
    if (settings.backgroundType === 'dark') return '#0F172A';
    if (settings.backgroundType === 'custom_color') return settings.backgroundColorHex;
    return settings.backgroundColorHex || '#F4F5F7';
  };

  return (
    <View
      style={[styles.container, { backgroundColor: getBackgroundColor() }, style]}
      onLayout={handleLayout}
      {...(enableInteractiveTouch ? panResponder.panHandlers : {})}
    >
      {/* If native GL view is active on Android build, render it */}
      {NativeWaterPreviewView && WallpaperBridge.isNativeModuleAvailable() ? (
        <NativeWaterPreviewView
          style={StyleSheet.absoluteFill}
          waterColor={settings.waterColorHex}
          backgroundColor={getBackgroundColor()}
          waterVolume={settings.waterVolume}
          physicsSensitivity={settings.physicsSensitivity}
          shakeSensitivity={settings.shakeSensitivity}
          glowIntensity={settings.glowIntensity}
        />
      ) : Platform.OS === 'web' ? (
        /* Web Canvas continuous metaball liquid with SVG threshold filter */
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <svg style={{ position: 'absolute', width: 0, height: 0 }} aria-hidden="true">
            <defs>
              <filter id="rnMetaballFilter" colorInterpolationFilters="sRGB">
                <feGaussianBlur in="SourceGraphic" stdDeviation="11" result="blur" />
                <feColorMatrix in="blur" mode="matrix" values="
                  1 0 0 0 0
                  0 1 0 0 0
                  0 0 1 0 0
                  0 0 0 26 -9.2" result="metaball" />
                <feComposite in="SourceGraphic" in2="metaball" operator="atop" />
              </filter>
            </defs>
          </svg>
          <canvas
            ref={glowCanvasRef as any}
            width={dimensions.width}
            height={dimensions.height}
            style={{
              position: 'absolute',
              width: '100%',
              height: '100%',
              filter: 'blur(12px)',
              opacity: settings.glowIntensity * 0.7,
            }}
          />
          <canvas
            ref={canvasRef as any}
            width={dimensions.width}
            height={dimensions.height}
            style={{
              position: 'absolute',
              width: '100%',
              height: '100%',
              filter: 'url(#rnMetaballFilter)',
            }}
          />
        </View>
      ) : (
        /* Fallback container */
        <View style={StyleSheet.absoluteFill} pointerEvents="none" />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
  },
});
