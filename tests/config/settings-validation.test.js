const test = require('node:test');
const assert = require('node:assert/strict');

const DEFAULT_SETTINGS = {
  waterColorHex: '#00E5FF',
  waterColorSecondaryHex: '#00B0FF',
  backgroundType: 'light',
  backgroundColorHex: '#F2F5F8',
  waterVolume: 0.38,
  physicsSensitivity: 1.25,
  shakeSensitivity: 1.1,
  glowIntensity: 0.65,
  qualityLevel: 'balanced',
  targetFps: 60,
  touchInteractionsEnabled: true,
  sensorPollingRateHz: 60,
};

function validateAndSanitizeSettings(raw) {
  const sanitized = { ...DEFAULT_SETTINGS };

  if (typeof raw.waterColorHex === 'string' && /^#[0-9A-Fa-f]{6}$/.test(raw.waterColorHex)) {
    sanitized.waterColorHex = raw.waterColorHex;
  }
  if (typeof raw.backgroundColorHex === 'string' && /^#[0-9A-Fa-f]{6}$/.test(raw.backgroundColorHex)) {
    sanitized.backgroundColorHex = raw.backgroundColorHex;
  }
  if (['light', 'dark', 'custom_color', 'custom_image'].includes(raw.backgroundType)) {
    sanitized.backgroundType = raw.backgroundType;
  }
  if (typeof raw.waterVolume === 'number') {
    sanitized.waterVolume = Math.max(0.15, Math.min(0.75, raw.waterVolume));
  }
  if (typeof raw.physicsSensitivity === 'number') {
    sanitized.physicsSensitivity = Math.max(0.5, Math.min(2.5, raw.physicsSensitivity));
  }
  if (typeof raw.targetFps === 'number') {
    sanitized.targetFps = [30, 60, 90, 120].includes(raw.targetFps) ? raw.targetFps : 60;
  }

  return sanitized;
}

test('Settings validation accepts valid authentic Live WaterPaper settings', () => {
  const result = validateAndSanitizeSettings({
    waterColorHex: '#00E5FF',
    waterVolume: 0.45,
    physicsSensitivity: 1.5,
    targetFps: 120,
  });

  assert.equal(result.waterColorHex, '#00E5FF');
  assert.equal(result.waterVolume, 0.45);
  assert.equal(result.physicsSensitivity, 1.5);
  assert.equal(result.targetFps, 120);
});

test('Settings validation safely clamps out-of-bounds parameters', () => {
  const result = validateAndSanitizeSettings({
    waterVolume: 50.0, // Exorbitant volume
    physicsSensitivity: -10, // Negative sensitivity
    targetFps: 999, // Unsupported FPS
  });

  assert.equal(result.waterVolume, 0.75, 'Should clamp to max 0.75');
  assert.equal(result.physicsSensitivity, 0.5, 'Should clamp to min 0.5');
  assert.equal(result.targetFps, 60, 'Should fallback to 60 FPS');
});

test('Settings rejects invalid color strings and retains default cyan', () => {
  const result = validateAndSanitizeSettings({
    waterColorHex: 'not-a-color',
  });

  assert.equal(result.waterColorHex, '#00E5FF');
});
