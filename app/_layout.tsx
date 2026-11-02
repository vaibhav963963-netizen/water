import React from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { WallpaperProvider } from '../src/state/WallpaperContext';
import { ToastProvider } from '../src/components/Toast';
import { Colors } from '../src/theme/colors';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <WallpaperProvider>
        <ToastProvider>
          <StatusBar style="dark" backgroundColor={Colors.canvas} />
          <Stack
            screenOptions={{
              headerStyle: {
                backgroundColor: Colors.canvas,
              },
              headerTintColor: Colors.deepBlack,
              headerTitleStyle: {
                fontWeight: '800',
                fontSize: 18,
              },
              headerShadowVisible: false,
              contentStyle: {
                backgroundColor: Colors.canvas,
              },
            }}
          >
            <Stack.Screen
              name="index"
              options={{
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="preview"
              options={{
                title: 'Live Preview',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="customize"
              options={{
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="settings"
              options={{
                headerShown: false,
              }}
            />
          </Stack>
        </ToastProvider>
      </WallpaperProvider>
    </SafeAreaProvider>
  );
}
