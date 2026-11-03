import React from 'react';
import { View, Image, StyleSheet } from 'react-native';

const STICKER_IMAGES = {
  waterDrop: require('../../../assets/stickers/sticker_water_drop_3d.png'),
  notebook: require('../../../assets/stickers/sticker_notebook_3d.png'),
  camera: require('../../../assets/stickers/sticker_camera_3d.png'),
  folder: require('../../../assets/stickers/sticker_folder_3d.png'),
  toolbox: require('../../../assets/stickers/sticker_toolbox_3d.png'),
};

/**
 * 3D Water Droplet Sticker
 * Glossy claymorphic Milano Red character with Lemon Chiffon specular shine.
 */
export const WaterDrop3D: React.FC<{ size?: number }> = ({ size = 64 }) => (
  <View style={[styles.stickerContainer, { width: size, height: size }]}>
    <Image
      source={STICKER_IMAGES.waterDrop}
      style={[styles.stickerImage, { width: size, height: size }]}
      resizeMode="contain"
    />
  </View>
);

/**
 * 3D Notebook Sticker
 * Lemon Chiffon personal journal with Milano Red spine and Gold star.
 */
export const Notebook3D: React.FC<{ size?: number }> = ({ size = 64 }) => (
  <View style={[styles.stickerContainer, { width: size, height: size }]}>
    <Image
      source={STICKER_IMAGES.notebook}
      style={[styles.stickerImage, { width: size, height: size }]}
      resizeMode="contain"
    />
  </View>
);

/**
 * 3D Camera Sticker
 * Warm cream retro camera with Milano Red plate and charcoal lens.
 */
export const Camera3D: React.FC<{ size?: number }> = ({ size = 64 }) => (
  <View style={[styles.stickerContainer, { width: size, height: size }]}>
    <Image
      source={STICKER_IMAGES.camera}
      style={[styles.stickerImage, { width: size, height: size }]}
      resizeMode="contain"
    />
  </View>
);

/**
 * 3D Folder Sticker
 * Deep charcoal document portfolio with Lemon Chiffon pages and Gold seal.
 */
export const Folder3D: React.FC<{ size?: number }> = ({ size = 64 }) => (
  <View style={[styles.stickerContainer, { width: size, height: size }]}>
    <Image
      source={STICKER_IMAGES.folder}
      style={[styles.stickerImage, { width: size, height: size }]}
      resizeMode="contain"
    />
  </View>
);

/**
 * 3D Toolbox Sticker
 * Milano Red portable utility case with charcoal handle and gold latch.
 */
export const Toolbox3D: React.FC<{ size?: number }> = ({ size = 64 }) => (
  <View style={[styles.stickerContainer, { width: size, height: size }]}>
    <Image
      source={STICKER_IMAGES.toolbox}
      style={[styles.stickerImage, { width: size, height: size }]}
      resizeMode="contain"
    />
  </View>
);

/**
 * 3D Clock / Placeholder (reuses WaterDrop or Toolbox)
 */
export const Clock3D: React.FC<{ size?: number }> = ({ size = 64 }) => (
  <View style={[styles.stickerContainer, { width: size, height: size }]}>
    <Image
      source={STICKER_IMAGES.toolbox}
      style={[styles.stickerImage, { width: size, height: size }]}
      resizeMode="contain"
    />
  </View>
);

const styles = StyleSheet.create({
  stickerContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: 'rgba(23, 19, 17, 0.28)',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 3,
  },
  stickerImage: {
    borderRadius: 14,
  },
});
