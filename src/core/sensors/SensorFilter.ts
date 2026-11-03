export interface RawSensorData {
  x: number;
  y: number;
  z: number;
}

export interface FilteredOrientation {
  gravityX: number;
  gravityY: number;
  gravityZ: number;
  pitch: number;
  roll: number;
  shakeMagnitude: number;
  isShakeTriggered: boolean;
}

export class SensorFilter {
  private gravityX: number = 0;
  private gravityY: number = 9.8;
  private gravityZ: number = 0;
  private lastShakeTimestamp: number = 0;
  private readonly alpha: number; // Low-pass filter smoothing coefficient
  private readonly shakeThreshold: number;
  private readonly shakeCooldownMs: number;

  constructor(
    alpha: number = 0.82,
    shakeThreshold: number = 14.0,
    shakeCooldownMs: number = 300
  ) {
    this.alpha = alpha;
    this.shakeThreshold = shakeThreshold;
    this.shakeCooldownMs = shakeCooldownMs;
  }

  public processAccelerometer(
    raw: RawSensorData,
    screenRotationDegrees: number = 0,
    timestamp: number = Date.now()
  ): FilteredOrientation {
    // 1. Remap axes based on screen orientation
    let ax = raw.x;
    let ay = raw.y;
    const az = raw.z;

    switch (screenRotationDegrees) {
      case 90:
        ax = -raw.y;
        ay = raw.x;
        break;
      case 180:
        ax = -raw.x;
        ay = -raw.y;
        break;
      case 270:
        ax = raw.y;
        ay = -raw.x;
        break;
    }

    // 2. Low-pass filter to isolate constant gravity
    this.gravityX = this.alpha * this.gravityX + (1 - this.alpha) * ax;
    this.gravityY = this.alpha * this.gravityY + (1 - this.alpha) * ay;
    this.gravityZ = this.alpha * this.gravityZ + (1 - this.alpha) * az;

    // 3. High-pass filter to isolate dynamic linear acceleration (shake)
    const linearX = ax - this.gravityX;
    const linearY = ay - this.gravityY;
    const linearZ = az - this.gravityZ;

    const shakeMagnitude = Math.sqrt(
      linearX * linearX + linearY * linearY + linearZ * linearZ
    );

    let isShakeTriggered = false;
    if (
      shakeMagnitude > this.shakeThreshold &&
      timestamp - this.lastShakeTimestamp > this.shakeCooldownMs
    ) {
      isShakeTriggered = true;
      this.lastShakeTimestamp = timestamp;
    }

    // 4. Calculate Pitch & Roll
    const norm = Math.sqrt(
      this.gravityX * this.gravityX +
      this.gravityY * this.gravityY +
      this.gravityZ * this.gravityZ
    );

    // Enhanced deadzone for physiological hand tremor & resting tilt
    let cleanGx = this.gravityX;
    let cleanGy = this.gravityY;
    if (Math.abs(cleanGx) < 0.45) cleanGx = 0;
    if (cleanGy > 3.5 && cleanGx === 0) {
      cleanGy = 9.8;
    } else if (Math.abs(cleanGy - 9.8) < 0.35) {
      cleanGy = 9.8;
    }

    const nx = norm > 0.0001 ? this.gravityX / norm : 0;
    const ny = norm > 0.0001 ? this.gravityY / norm : 0;
    const nz = norm > 0.0001 ? this.gravityZ / norm : 1;

    const pitch = Math.atan2(-ny, Math.sqrt(nx * nx + nz * nz));
    const roll = Math.atan2(nx, nz);

    return {
      gravityX: cleanGx,
      gravityY: cleanGy,
      gravityZ: this.gravityZ,
      pitch,
      roll,
      shakeMagnitude,
      isShakeTriggered,
    };
  }

  public reset(): void {
    this.gravityX = 0;
    this.gravityY = 9.8;
    this.gravityZ = 0;
    this.lastShakeTimestamp = 0;
  }
}
