const test = require('node:test');
const assert = require('node:assert/strict');

// JavaScript port of SensorFilter logic for unit testing
class TestSensorFilter {
  constructor(alpha = 0.82, shakeThreshold = 14.0, shakeCooldownMs = 300) {
    this.alpha = alpha;
    this.shakeThreshold = shakeThreshold;
    this.shakeCooldownMs = shakeCooldownMs;
    this.gravityX = 0;
    this.gravityY = 9.8;
    this.gravityZ = 0;
    this.lastShakeTimestamp = 0;
  }

  processAccelerometer(raw, screenRotationDegrees = 0, timestamp = Date.now()) {
    let rx = raw.x;
    let ry = raw.y;

    switch (screenRotationDegrees) {
      case 90:
        rx = -raw.y;
        ry = raw.x;
        break;
      case 180:
        rx = -raw.x;
        ry = -raw.y;
        break;
      case 270:
        rx = raw.y;
        ry = -raw.x;
        break;
      default:
        break;
    }

    this.gravityX = this.alpha * this.gravityX + (1 - this.alpha) * rx;
    this.gravityY = this.alpha * this.gravityY + (1 - this.alpha) * ry;
    this.gravityZ = this.alpha * this.gravityZ + (1 - this.alpha) * raw.z;

    const dynX = rx - this.gravityX;
    const dynY = ry - this.gravityY;
    const dynZ = raw.z - this.gravityZ;
    const shakeMagnitude = Math.hypot(dynX, dynY, dynZ);

    let isShakeTriggered = false;
    if (shakeMagnitude > this.shakeThreshold && timestamp - this.lastShakeTimestamp > this.shakeCooldownMs) {
      isShakeTriggered = true;
      this.lastShakeTimestamp = timestamp;
    }

    return {
      gravityX: this.gravityX,
      gravityY: this.gravityY,
      gravityZ: this.gravityZ,
      shakeMagnitude,
      isShakeTriggered,
    };
  }
}

test('SensorFilter isolates steady gravity from sudden high-frequency spike', () => {
  const filter = new TestSensorFilter(0.8);
  // Steady state: device upright (Y = 9.8)
  for (let i = 0; i < 20; i++) {
    filter.processAccelerometer({ x: 0, y: 9.8, z: 0 }, 0, 1000 + i * 20);
  }

  // Single extreme acceleration spike (user bumped phone)
  const result = filter.processAccelerometer({ x: 15, y: 9.8, z: 10 }, 0, 1500);

  // Gravity should only slightly change due to alpha low-pass filter
  assert.ok(result.gravityX < 4.0, 'GravityX should be heavily smoothed');
  assert.ok(result.shakeMagnitude > 10.0, 'Dynamic linear acceleration should be recognized as shake');
});

test('SensorFilter transforms gravity correctly across screen rotations', () => {
  const filter = new TestSensorFilter(0.0); // alpha 0 = immediate update for testing pure coordinate rotation

  // Portrait (0 deg): device held upright -> gravity down along Y
  const r0 = filter.processAccelerometer({ x: 0, y: 9.8, z: 0 }, 0);
  assert.equal(Math.round(r0.gravityX), 0);
  assert.equal(Math.round(r0.gravityY), 10);

  // Landscape (90 deg): raw Y becomes -X in landscape space
  const r90 = filter.processAccelerometer({ x: 0, y: 9.8, z: 0 }, 90);
  assert.equal(Math.round(r90.gravityX), -10);
  assert.equal(Math.round(r90.gravityY), 0);

  // Reverse Landscape (270 deg):
  const r270 = filter.processAccelerometer({ x: 0, y: 9.8, z: 0 }, 270);
  assert.equal(Math.round(r270.gravityX), 10);
  assert.equal(Math.round(r270.gravityY), 0);
});

test('SensorFilter enforces shake cooldown to avoid false jitter triggers', () => {
  const filter = new TestSensorFilter(0.8, 12.0, 300);
  let time = 10000;

  // First strong shake
  const first = filter.processAccelerometer({ x: 25, y: 30, z: 5 }, 0, time);
  assert.equal(first.isShakeTriggered, true, 'First shake must trigger');

  // Second rapid shake within 50ms (during cooldown)
  time += 50;
  const second = filter.processAccelerometer({ x: 25, y: 30, z: 5 }, 0, time);
  assert.equal(second.isShakeTriggered, false, 'Shake within cooldown must be suppressed');

  // Third shake after cooldown elapsed (350ms later)
  time += 350;
  const third = filter.processAccelerometer({ x: 25, y: 30, z: 5 }, 0, time);
  assert.equal(third.isShakeTriggered, true, 'Shake after cooldown must trigger');
});
