export interface FluidSample {
  density: number;
  velocityX: number;
  velocityY: number;
  isSubmerged: boolean;
}

export interface IFluidSampler {
  getGravityX(): number;
  getGravityY(): number;
  sampleFluidAt(x: number, y: number, radius: number): FluidSample;
}

export interface FloatingStick {
  id: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  angle: number;
  angularVelocity: number;
  length: number;
  radius: number;
  woodDensity: number;
  textureIndex: number;
  mass: number;
  momentOfInertia: number;
  submergedFraction: number;
}

/**
 * 2D Rigid body physics engine for floating wooden sticks / twigs coupled
 * with SPH fluid dynamics (buoyancy, fluid drag, torque, collisions).
 */
export class FloatingSticksEngine {
  private sticks: FloatingStick[] = [];
  private width: number;
  private height: number;

  private readonly linearDampingAir = 0.999;
  private readonly linearDampingWater = 0.985;
  private readonly angularDampingAir = 0.995;
  private readonly angularDampingWater = 0.940;
  private readonly wallRestitution = 0.38;
  private readonly stickRestitution = 0.42;
  private readonly buoyancyCoeff = 1.35;
  private readonly fluidDragCoeff = 2.80;

  private stickScale: number = 1.0;

  constructor(width: number, height: number, stickCount: number = 5, scale: number = 1.0) {
    this.width = width;
    this.height = height;
    this.stickScale = scale;
    this.initializeDefaultSticks(stickCount, scale);
  }

  public setDimensions(width: number, height: number): void {
    this.width = width;
    this.height = height;
    this.clampSticksToScreen();
  }

  public setConfig(count: number, scale: number): void {
    if (this.sticks.length !== count || Math.abs(this.stickScale - scale) > 0.05) {
      this.stickScale = scale;
      this.initializeDefaultSticks(count, scale);
    }
  }

  public initializeDefaultSticks(count: number = 5, scale: number = 1.0): void {
    this.sticks = [];
    this.stickScale = scale;
    const clampedCount = Math.max(2, Math.min(15, count));
    const cols = Math.min(clampedCount, 4);

    for (let i = 0; i < clampedCount; i++) {
      // Pick twig texture cyclically/randomly across available 6 textures
      const texIdx = (i % 6) + 1;
      const baseLen = texIdx === 1 ? 220 : texIdx === 2 ? 240 : texIdx === 3 ? 200 : texIdx === 4 ? 230 : texIdx === 5 ? 210 : 190;
      const baseRad = texIdx === 2 ? 20 : texIdx === 4 ? 16 : texIdx === 6 ? 14 : 18;

      const len = baseLen * scale;
      const rad = baseRad * scale;
      const woodDensity = 0.65;
      const mass = Math.max(1, len * rad * 2 * woodDensity * 0.05);
      const momentOfInertia = Math.max(10, (1 / 12) * mass * (len * len + 4 * rad * rad));

      const col = i % cols;
      const row = Math.floor(i / cols);
      const spacingX = this.width / (cols + 1);

      this.sticks.push({
        id: i + 1,
        x: spacingX * (col + 1) + (Math.random() - 0.5) * 20,
        y: this.height * 0.45 + row * 65 + (Math.random() - 0.5) * 30,
        vx: 0,
        vy: 0,
        angle: (Math.random() - 0.5) * 0.6,
        angularVelocity: 0,
        length: len,
        radius: rad,
        woodDensity,
        textureIndex: texIdx,
        mass,
        momentOfInertia,
        submergedFraction: 0,
      });
    }
  }

  public step(dtRaw: number, fluidEngine: IFluidSampler): void {
    const dt = Math.max(0.001, Math.min(0.033, dtRaw));
    const gx = fluidEngine.getGravityX();
    const gy = fluidEngine.getGravityY();

    for (let sIdx = 0; sIdx < this.sticks.length; sIdx++) {
      const s = this.sticks[sIdx];
      const samples = this.getSamplePoints(s, 5);

      let totalBuoyancyX = 0;
      let totalBuoyancyY = 0;
      let totalDragX = 0;
      let totalDragY = 0;
      let totalTorque = 0;
      let submergedCount = 0;

      for (let pIdx = 0; pIdx < samples.length; pIdx++) {
        const [px, py, rx, ry] = samples[pIdx];
        const fluid = fluidEngine.sampleFluidAt(px, py, s.radius * 2.8);

        if (fluid.isSubmerged) {
          submergedCount++;
          const subFactor = Math.max(0.35, Math.min(1.35, fluid.density));

          // Archimedes Buoyancy
          const buoyantScalar = (1.0 - s.woodDensity) * this.buoyancyCoeff * subFactor * (s.mass / samples.length);
          const fbX = -gx * buoyantScalar;
          const fbY = -gy * buoyantScalar;

          totalBuoyancyX += fbX;
          totalBuoyancyY += fbY;

          // Velocity at point
          const pvx = s.vx - s.angularVelocity * ry;
          const pvy = s.vy + s.angularVelocity * rx;

          // Drag force
          const relVx = fluid.velocityX - pvx;
          const relVy = fluid.velocityY - pvy;
          const dragScalar = this.fluidDragCoeff * subFactor * 0.20;
          const fdX = relVx * dragScalar;
          const fdY = relVy * dragScalar;

          totalDragX += fdX;
          totalDragY += fdY;

          // Torque
          const totalPointFx = fbX + fdX;
          const totalPointFy = fbY + fdY;
          totalTorque += (rx * totalPointFy - ry * totalPointFx);
        }
      }

      s.submergedFraction = submergedCount / samples.length;

      // Linear motion
      const totalFx = s.mass * gx + totalBuoyancyX + totalDragX;
      const totalFy = s.mass * gy + totalBuoyancyY + totalDragY;

      s.vx += (totalFx / s.mass) * dt;
      s.vy += (totalFy / s.mass) * dt;

      const linearDamping = s.submergedFraction > 0.2 ? this.linearDampingWater : this.linearDampingAir;
      s.vx *= linearDamping;
      s.vy *= linearDamping;

      s.x += s.vx * dt;
      s.y += s.vy * dt;

      // Angular motion
      const angularAccel = totalTorque / s.momentOfInertia;
      s.angularVelocity += angularAccel * dt;

      const angularDamping = s.submergedFraction > 0.2 ? this.angularDampingWater : this.angularDampingAir;
      s.angularVelocity *= angularDamping;
      s.angularVelocity = Math.max(-18, Math.min(18, s.angularVelocity));

      s.angle += s.angularVelocity * dt;
      if (s.angle > Math.PI) s.angle -= 2 * Math.PI;
      if (s.angle < -Math.PI) s.angle += 2 * Math.PI;
    }

    // Multi-pass constraint solver: guarantees sticks stack on top of each other without tunneling
    for (let iter = 0; iter < 3; iter++) {
      this.resolveStickCollisions();
      for (let i = 0; i < this.sticks.length; i++) {
        this.handleWallCollisions(this.sticks[i]);
      }
    }

    // Resting sleep deadzone: completely eliminates idle vibration when mobile is still
    for (let i = 0; i < this.sticks.length; i++) {
      const s = this.sticks[i];
      const spd2 = s.vx * s.vx + s.vy * s.vy;
      const w2 = s.angularVelocity * s.angularVelocity;
      if (spd2 < 18 && w2 < 0.015) {
        s.vx = 0;
        s.vy = 0;
        s.angularVelocity = 0;
      }
    }
  }

  private getSamplePoints(s: FloatingStick, numPoints: number = 5): [number, number, number, number][] {
    const halfL = s.length * 0.5;
    const cosA = Math.cos(s.angle);
    const sinA = Math.sin(s.angle);
    const step = s.length / Math.max(1, numPoints - 1);
    const points: [number, number, number, number][] = [];

    for (let i = 0; i < numPoints; i++) {
      const offset = -halfL + i * step;
      const rx = offset * cosA;
      const ry = offset * sinA;
      points.push([s.x + rx, s.y + ry, rx, ry]);
    }
    return points;
  }

  private handleWallCollisions(s: FloatingStick): void {
    const halfL = s.length * 0.5;
    const cosA = Math.cos(s.angle);
    const sinA = Math.sin(s.angle);
    const r = s.radius;

    const p1x = s.x - halfL * cosA;
    const p1y = s.y - halfL * sinA;
    const p2x = s.x + halfL * cosA;
    const p2y = s.y + halfL * sinA;

    // Left wall
    this.checkPointWall(s, p1x - r, 1, 0, -halfL * cosA, -halfL * sinA);
    this.checkPointWall(s, p2x - r, 1, 0, halfL * cosA, halfL * sinA);

    // Right wall
    this.checkPointWall(s, (this.width - r) - p1x, -1, 0, -halfL * cosA, -halfL * sinA);
    this.checkPointWall(s, (this.width - r) - p2x, -1, 0, halfL * cosA, halfL * sinA);

    // Top wall
    this.checkPointWall(s, p1y - r, 0, 1, -halfL * cosA, -halfL * sinA);
    this.checkPointWall(s, p2y - r, 0, 1, halfL * cosA, halfL * sinA);

    // Bottom wall
    this.checkPointWall(s, (this.height - r) - p1y, 0, -1, -halfL * cosA, -halfL * sinA);
    this.checkPointWall(s, (this.height - r) - p2y, 0, -1, halfL * cosA, halfL * sinA);

    s.x = Math.max(r, Math.min(this.width - r, s.x));
    s.y = Math.max(r, Math.min(this.height - r, s.y));
  }

  private checkPointWall(
    s: FloatingStick,
    dist: number,
    normalX: number,
    normalY: number,
    rx: number,
    ry: number
  ): void {
    if (dist < 0) {
      const penetration = -dist;
      s.x += normalX * penetration * 0.85;
      s.y += normalY * penetration * 0.85;

      const velDotNormal = s.vx * normalX + s.vy * normalY;
      if (velDotNormal < 0) {
        // Zero restitution for resting contact (< 40 px/s) to prevent bouncing
        const rest = Math.abs(velDotNormal) < 40 ? 0.0 : this.wallRestitution;
        s.vx -= (1 + rest) * velDotNormal * normalX;
        s.vy -= (1 + rest) * velDotNormal * normalY;

        // Wall friction stops continuous sliding
        const tanX = -normalY;
        const tanY = normalX;
        const vTan = s.vx * tanX + s.vy * tanY;
        s.vx -= vTan * 0.25 * tanX;
        s.vy -= vTan * 0.25 * tanY;

        const torque = rx * normalY - ry * normalX;
        s.angularVelocity += torque * 0.002;
        s.angularVelocity *= 0.85;
      }
    }
  }

  private resolveStickCollisions(): void {
    const num = this.sticks.length;
    for (let i = 0; i < num; i++) {
      const a = this.sticks[i];
      for (let j = i + 1; j < num; j++) {
        const b = this.sticks[j];
        const minDist = a.radius + b.radius;
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const maxReach = a.length * 0.5 + b.length * 0.5 + minDist;
        if (dx * dx + dy * dy > maxReach * maxReach) continue;

        const halfLa = a.length * 0.5;
        const halfLb = b.length * 0.5;
        const cosAa = Math.cos(a.angle);
        const sinAa = Math.sin(a.angle);
        const cosAb = Math.cos(b.angle);
        const sinAb = Math.sin(b.angle);

        const pa1x = a.x - halfLa * cosAa;
        const pa1y = a.y - halfLa * sinAa;
        const pa2x = a.x + halfLa * cosAa;
        const pa2y = a.y + halfLa * sinAa;

        const pb1x = b.x - halfLb * cosAb;
        const pb1y = b.y - halfLb * sinAb;
        const pb2x = b.x + halfLb * cosAb;
        const pb2y = b.y + halfLb * sinAb;

        const closest = this.closestPointsBetweenSegments(
          pa1x, pa1y, pa2x, pa2y,
          pb1x, pb1y, pb2x, pb2y
        );

        const cDx = closest[2] - closest[0];
        const cDy = closest[3] - closest[1];
        const dist2 = cDx * cDx + cDy * cDy;

        if (dist2 < minDist * minDist && dist2 > 0.0001) {
          const dist = Math.sqrt(dist2);
          const overlap = minDist - dist;
          const nx = cDx / dist;
          const ny = cDy / dist;

          // Push overlapping sticks apart (Position-Based Dynamics relaxation)
          const pushFraction = 0.5;
          a.x -= nx * overlap * pushFraction;
          a.y -= ny * overlap * pushFraction;
          b.x += nx * overlap * pushFraction;
          b.y += ny * overlap * pushFraction;

          const rAx = closest[0] - a.x;
          const rAy = closest[1] - a.y;
          const rBx = closest[2] - b.x;
          const rBy = closest[3] - b.y;

          const vAx = a.vx - a.angularVelocity * rAy;
          const vAy = a.vy + a.angularVelocity * rAx;
          const vBx = b.vx - b.angularVelocity * rBy;
          const vBy = b.vy + b.angularVelocity * rBx;

          const relVx = vBx - vAx;
          const relVy = vBy - vAy;
          const normalVel = relVx * nx + relVy * ny;

          if (normalVel < 0) {
            // Inelastic collision for resting contacts (< 40 px/s) to prevent micro-bounce jitter
            const rest = Math.abs(normalVel) < 40 ? 0.0 : this.stickRestitution;
            const impulse = -(1 + rest) * normalVel / (1 / a.mass + 1 / b.mass);
            const impX = nx * impulse;
            const impY = ny * impulse;

            a.vx -= impX / a.mass;
            a.vy -= impY / a.mass;
            b.vx += impX / b.mass;
            b.vy += impY / b.mass;

            a.angularVelocity -= (rAx * impY - rAy * impX) / a.momentOfInertia;
            b.angularVelocity += (rBx * impY - rBy * impX) / b.momentOfInertia;

            // Wood-on-wood tangent friction: allows sticks to grip and stack solidly!
            const tanX = -ny;
            const tanY = nx;
            const relVTan = relVx * tanX + relVy * tanY;
            const fImp = relVTan * 0.35;
            a.vx += tanX * fImp * 0.5;
            a.vy += tanY * fImp * 0.5;
            b.vx -= tanX * fImp * 0.5;
            b.vy -= tanY * fImp * 0.5;
          }
        }
      }
    }
  }

  private closestPointsBetweenSegments(
    p1x: number, p1y: number, q1x: number, q1y: number,
    p2x: number, p2y: number, q2x: number, q2y: number
  ): [number, number, number, number] {
    const d1x = q1x - p1x;
    const d1y = q1y - p1y;
    const d2x = q2x - p2x;
    const d2y = q2y - p2y;
    const rx = p1x - p2x;
    const ry = p1y - p2y;

    const a = d1x * d1x + d1y * d1y;
    const e = d2x * d2x + d2y * d2y;
    const f = d2x * rx + d2y * ry;

    let s: number;
    let t: number;

    if (a <= 0.0001 && e <= 0.0001) {
      return [p1x, p1y, p2x, p2y];
    }
    if (a <= 0.0001) {
      s = 0;
      t = Math.max(0, Math.min(1, f / e));
    } else {
      const c = d1x * rx + d1y * ry;
      if (e <= 0.0001) {
        t = 0;
        s = Math.max(0, Math.min(1, -c / a));
      } else {
        const b = d1x * d2x + d1y * d2y;
        const denom = a * e - b * b;
        s = denom !== 0 ? Math.max(0, Math.min(1, (b * f - c * e) / denom)) : 0;
        t = (b * s + f) / e;
        if (t < 0) {
          t = 0;
          s = Math.max(0, Math.min(1, -c / a));
        } else if (t > 1) {
          t = 1;
          s = Math.max(0, Math.min(1, (b - c) / a));
        }
      }
    }

    return [p1x + d1x * s, p1y + d1y * s, p2x + d2x * t, p2y + d2y * t];
  }

  public applyShakeImpulse(impulseX: number, impulseY: number): void {
    for (let i = 0; i < this.sticks.length; i++) {
      const s = this.sticks[i];
      const jitter = 0.9 + Math.random() * 0.2;
      s.vx += impulseX * 1.2 * jitter;
      s.vy += impulseY * 1.2 * jitter;
      s.angularVelocity += (Math.random() - 0.5) * 6.0;
    }
  }

  public addTouchDisturbance(touchX: number, touchY: number, strength: number = 700): void {
    const touchRadius = 160;
    const touchRadius2 = touchRadius * touchRadius;

    for (let i = 0; i < this.sticks.length; i++) {
      const s = this.sticks[i];
      const dx = s.x - touchX;
      const dy = s.y - touchY;
      const dist2 = dx * dx + dy * dy;
      if (dist2 < touchRadius2 && dist2 > 0.001) {
        const dist = Math.sqrt(dist2);
        const factor = (1 - dist / touchRadius) * strength;
        s.vx += (dx / dist) * factor;
        s.vy += (dy / dist) * factor;
        s.angularVelocity += (Math.random() - 0.5) * 4.0;
      }
    }
  }

  public getSticks(): readonly FloatingStick[] {
    return this.sticks;
  }

  private clampSticksToScreen(): void {
    for (let i = 0; i < this.sticks.length; i++) {
      this.handleWallCollisions(this.sticks[i]);
    }
  }
}
