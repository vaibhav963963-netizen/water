import React from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { WallpaperProvider } from '../src/state/WallpaperContext';
import { Colors } from '../src/theme/colors';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <WallpaperProvider>
        <StatusBar style="dark" />
        <Stack
          screenOptions={{
            headerStyle: {
              backgroundColor: Colors.surface,
            },
            headerTintColor: Colors.textPrimary,
            headerTitleStyle: {
              fontWeight: '700',
              fontSize: 18,
            },
            headerShadowVisible: false,
            contentStyle: {
              backgroundColor: Colors.background,
            },
          }}
        >
          <Stack.Screen
            name="index"
            options={{
              title: 'Live WaterPaper',
              headerTitleAlign: 'center',
            }}
          />
          <Stack.Screen
            name="preview"
            options={{
              title: 'Interactive Preview',
              headerShown: false,
            }}
          />
          <Stack.Screen
            name="customize"
            options={{
              title: 'Customize Liquid',
              headerBackTitle: 'Back',
            }}
          />
          <Stack.Screen
            name="settings"
            options={{
              title: 'Settings & Performance',
              headerBackTitle: 'Back',
            }}
          />
        </Stack>
      </WallpaperProvider>
    </SafeAreaProvider>
  );
}
