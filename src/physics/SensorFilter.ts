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
    // 1. Map raw axes according to screen rotation
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
      case 0:
      default:
        break;
    }

    // 2. Low-pass filter to isolate steady gravity from dynamic motion
    this.gravityX = this.alpha * this.gravityX + (1 - this.alpha) * rx;
    this.gravityY = this.alpha * this.gravityY + (1 - this.alpha) * ry;
    this.gravityZ = this.alpha * this.gravityZ + (1 - this.alpha) * raw.z;

    // 3. Dynamic linear acceleration = raw - gravity
    const dynX = rx - this.gravityX;
    const dynY = ry - this.gravityY;
    const dynZ = raw.z - this.gravityZ;
    const shakeMagnitude = Math.sqrt(dynX * dynX + dynY * dynY + dynZ * dynZ);

    let isShakeTriggered = false;
    if (shakeMagnitude > this.shakeThreshold && timestamp - this.lastShakeTimestamp > this.shakeCooldownMs) {
      isShakeTriggered = true;
      this.lastShakeTimestamp = timestamp;
    }

    // 4. Calculate inclination angles
    const roll = Math.atan2(this.gravityX, Math.hypot(this.gravityY, this.gravityZ));
    const pitch = Math.atan2(this.gravityY, Math.hypot(this.gravityX, this.gravityZ));

    return {
      gravityX: this.gravityX,
      gravityY: this.gravityY,
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
