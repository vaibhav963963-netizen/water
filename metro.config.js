// Learn more https://docs.expo.io/guides/customizing-metro
const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const config = getDefaultConfig(__dirname);

// Explicitly watch project root on Windows so Metro tracks node_modules/expo-router
config.watchFolders = [__dirname];

// Optimize for Windows file crawling and lazy hashing
config.resolver.useWatchman = false;
config.watcher.unstable_lazySha1 = true;

// Alias react-native to react-native-web when bundling for web
const defaultResolveRequest = config.resolver.resolveRequest;
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (platform === 'web') {
    if (moduleName === 'react-native') {
      return context.resolveRequest(context, 'react-native-web', platform);
    }
    if (moduleName.startsWith('react-native/')) {
      return context.resolveRequest(context, 'react-native-web', platform);
    }
  }
  if (defaultResolveRequest) {
    return defaultResolveRequest(context, moduleName, platform);
  }
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
