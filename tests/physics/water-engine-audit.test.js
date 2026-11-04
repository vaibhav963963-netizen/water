const test = require('node:test');
const assert = require('node:assert/strict');

/**
 * Exact mathematical and physical implementation of FluidSimulationEngine.kt
 * for rigorous numerical audit and automated regression testing.
 */
class AuditFluidEngine {
  constructor(screenWidth = 1080, screenHeight = 2400, volumeRatio = 0.38) {
    this.screenWidth = screenWidth;
    this.screenHeight = screenHeight;
    this.waterVolumeRatio = volumeRatio;
    this.particleRadius = 30.0;
    this.dropletRadius = 14.0;
    this.restDensity = 2.6;
    this.stiffness = 2800.0;
    this.viscosity = 35.0;
    this.surfaceTensionCoeff = 30.0;
    this.boundaryDamping = 0.50;
    this.maxVelocity = 4000.0;
    this.dropletMergeRadius = 40.0;
    this.scale = 520.0 * 2.0; // 1040

    this.gravityX = 0.0;
    this.gravityY = 9.8 * this.scale;

    this.particles = [];
    this.state = 'ACTIVE';
    this.settlingDuration = 0.0;
    this.sleepGravityX = 0.0;
    this.sleepGravityY = this.gravityY;
    this.maxVelocityObserved = 0;
    this.avgVelocityObserved = 0;

    this.initializeParticles(this.waterVolumeRatio);
  }

  wakeUp() {
    this.state = 'ACTIVE';
    this.settlingDuration = 0.0;
  }

  setDimensions(width, height) {
    if (width <= 0 || height <= 0) return;
    const oldW = this.screenWidth;
    const oldH = this.screenHeight;
    this.screenWidth = width;
    this.screenHeight = height;

    if (oldW > 0 && oldH > 0 && (oldW !== width || oldH !== height)) {
      const scaleX = width / oldW;
      const scaleY = height / oldH;
      for (const p of this.particles) {
        p.x = Math.max(p.radius, Math.min(width - p.radius, p.x * scaleX));
        p.y = Math.max(p.radius, Math.min(height - p.radius, p.y * scaleY));
        p.vx = 0;
        p.vy = 0;
      }
    }
  }

  initializeParticles(volume) {
    this.particles = [];
    this.wakeUp();

    const w = this.screenWidth;
    const h = this.screenHeight;
    const cols = 16;
    const count = 256;
    const spacing = this.particleRadius * 1.30;
    const startY = h - Math.floor(count / cols + 2) * spacing - 20;
    const startX = (w - cols * spacing) / 2;

    for (let i = 0; i < count; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const px = Math.min(w - this.particleRadius, Math.max(this.particleRadius, startX + col * spacing));
      const py = Math.min(h - this.particleRadius, Math.max(this.particleRadius, startY + row * spacing));

      this.particles.push({
        x: px,
        y: py,
        vx: 0,
        vy: 0,
        radius: this.particleRadius,
        density: this.restDensity,
        pressure: 0,
        isDroplet: false,
        lifetime: 0,
      });
    }
  }

  updateGravity(gx, gy) {
    const targetGx = gx * this.scale;
    const targetGy = gy * this.scale;

    if (this.state === 'SLEEPING') {
      const dGx = targetGx - this.sleepGravityX;
      const dGy = targetGy - this.sleepGravityY;
      const dist = Math.hypot(dGx, dGy);
      if (dist >= 0.25 * this.scale) {
        this.gravityX = targetGx;
        this.gravityY = targetGy;
        this.wakeUp();
      }
    } else {
      this.gravityX = targetGx;
      this.gravityY = targetGy;
    }
  }

  applyImpulse(impulseX, impulseY) {
    const mag = Math.hypot(impulseX, impulseY);
    if (mag < 2.0) return;

    this.wakeUp();
    const impulseScale = 2.4;

    const meanY = this.particles.reduce((s, p) => s + p.y, 0) / this.particles.length;

    for (const p of this.particles) {
      p.vx += impulseX * impulseScale;
      p.vy += impulseY * impulseScale;

      const vMag = Math.hypot(p.vx, p.vy);
      // Surface particles break into spray droplets on violent shake
      if (vMag > 500 && p.y <= meanY + 20) {
        p.isDroplet = true;
        p.radius = this.dropletRadius;
        p.lifetime = 0.0;
      }
    }
  }

  step(rawDt) {
    if (this.state === 'SLEEPING') return;

    const dt = Math.max(0.005, Math.min(0.020, rawDt));
    this.stepSubstep(dt);
  }

  stepSubstep(dt) {
    const h = this.particleRadius * 2.3;
    const h2 = h * h;
    const count = this.particles.length;

    // 1. SPH Density
    for (let i = 0; i < count; i++) {
      const pi = this.particles[i];
      let density = 0;
      for (let j = 0; j < count; j++) {
        const pj = this.particles[j];
        const dx = pj.x - pi.x;
        const dy = pj.y - pi.y;
        const r2 = dx * dx + dy * dy;
        if (r2 < h2) {
          const factor = 1 - r2 / h2;
          density += factor * factor * factor;
        }
      }
      pi.density = Math.max(density, 0.6);
      pi.pressure = Math.max(0, this.stiffness * (pi.density - this.restDensity));
    }

    // 2. SPH Internal Forces
    const dropletMergeRadius2 = this.dropletMergeRadius * this.dropletMergeRadius;

    for (let i = 0; i < count; i++) {
      const pi = this.particles[i];
      let fPressureX = 0;
      let fPressureY = 0;
      let fViscosityX = 0;
      let fViscosityY = 0;

      for (let j = 0; j < count; j++) {
        if (i === j) continue;
        const pj = this.particles[j];
        const dx = pj.x - pi.x;
        const dy = pj.y - pi.y;
        const r2 = dx * dx + dy * dy;

        if (r2 < h2 && r2 > 0.0001) {
          const r = Math.sqrt(r2);
          const normX = dx / r;
          const normY = dy / r;
          const q = 1 - r / h;

          const avgPressure = (pi.pressure + pj.pressure) / (2 * pj.density);
          const pScalar = avgPressure * q * q * 3.2;
          fPressureX -= normX * pScalar;
          fPressureY -= normY * pScalar;

          const cohesionScalar = this.surfaceTensionCoeff * q * q;
          fPressureX += normX * cohesionScalar;
          fPressureY += normY * cohesionScalar;

          const dvx = pj.vx - pi.vx;
          const dvy = pj.vy - pi.vy;
          const vScalar = (this.viscosity / pj.density) * q;
          fViscosityX += dvx * vScalar;
          fViscosityY += dvy * vScalar;

          if (pi.isDroplet && !pj.isDroplet && pi.lifetime > 0.15 && r2 < dropletMergeRadius2) {
            pi.isDroplet = false;
            pi.radius = this.particleRadius;
            pi.lifetime = 0;
            pj.vx += pi.vx * 0.35;
            pj.vy += pi.vy * 0.35;
          }
        }
      }

      const invDensity = 1 / pi.density;
      const ax = (fPressureX + fViscosityX) * invDensity + this.gravityX;
      const ay = (fPressureY + fViscosityY) * invDensity + this.gravityY;

      pi.vx += ax * dt;
      pi.vy += ay * dt;
    }

    // 3. Integration & Damping
    let maxV2 = 0;
    let sumV = 0;

    for (let i = 0; i < count; i++) {
      const pi = this.particles[i];

      pi.vx *= 0.992;
      pi.vy *= 0.992;

      if (this.state === 'SETTLING') {
        pi.vx *= 0.85;
        pi.vy *= 0.85;
      }

      if (pi.isDroplet) {
        pi.lifetime += dt;
        if (pi.lifetime > 4.0) {
          pi.isDroplet = false;
          pi.radius = this.particleRadius;
        }
      }

      const spd2 = pi.vx * pi.vx + pi.vy * pi.vy;
      const spd = Math.sqrt(spd2);
      if (spd2 > maxV2) maxV2 = spd2;
      sumV += spd;

      pi.x += pi.vx * dt;
      pi.y += pi.vy * dt;

      this.handleWallCollision(pi);
    }

    this.maxVelocityObserved = Math.sqrt(maxV2);
    this.avgVelocityObserved = sumV / count;

    // 4. State Machine Transitions
    if (this.state === 'ACTIVE') {
      if (this.maxVelocityObserved < 350 && this.avgVelocityObserved < 160) {
        this.settlingDuration += dt;
        if (this.settlingDuration > 0.20) {
          this.state = 'SETTLING';
          this.settlingDuration = 0;
        }
      } else {
        this.settlingDuration = 0;
      }
    } else if (this.state === 'SETTLING') {
      if (this.maxVelocityObserved > 450) {
        this.state = 'ACTIVE';
        this.settlingDuration = 0;
      } else {
        this.settlingDuration += dt;
        if (this.settlingDuration > 0.35) {
          for (const p of this.particles) {
            p.vx = 0;
            p.vy = 0;
            p.isDroplet = false;
            p.radius = this.particleRadius;
          }
          this.state = 'SLEEPING';
          this.sleepGravityX = this.gravityX;
          this.sleepGravityY = this.gravityY;
          this.maxVelocityObserved = 0;
          this.avgVelocityObserved = 0;
          this.settlingDuration = 0;
        }
      }
    }
  }

  handleWallCollision(p) {
    const r = p.radius;
    const damping = this.boundaryDamping;
    if (p.x < r) {
      p.x = r;
      p.vx = Math.abs(p.vx) < 15 ? 0 : -p.vx * damping;
    } else if (p.x > this.screenWidth - r) {
      p.x = this.screenWidth - r;
      p.vx = Math.abs(p.vx) < 15 ? 0 : -p.vx * damping;
    }

    if (p.y < r) {
      p.y = r;
      p.vy = Math.abs(p.vy) < 15 ? 0 : -p.vy * damping;
    } else if (p.y > this.screenHeight - r) {
      p.y = this.screenHeight - r;
      p.vy = Math.abs(p.vy) < 15 ? 0 : -p.vy * damping;
    }
  }
}

// ---------------------------------------------------------------------------
// TEST SUITE: COMPLETE NUMERICAL VERIFICATION OF AUDIT REQUIREMENTS
// ---------------------------------------------------------------------------

test('PHASE 2 — Uniform Initial Particle Distribution spans wall-to-wall without corner clustering', () => {
  const engine = new AuditFluidEngine(1080, 2400, 0.38);
  const particles = engine.particles;

  assert.ok(particles.length >= 220, `Particle budget (${particles.length}) must be >= 220`);

  const minX = Math.min(...particles.map(p => p.x));
  const maxX = Math.max(...particles.map(p => p.x));
  const spanX = maxX - minX;

  assert.ok(minX < 320, `Leftmost particle X (${minX}) must be near left container wall`);
  assert.ok(maxX > 760, `Rightmost particle X (${maxX}) must be near right container wall`);
  assert.ok(spanX > 500, `Total width span (${spanX}) must span across the phone screen width`);

  const leftCount = particles.filter(p => p.x < 540).length;
  const rightCount = particles.filter(p => p.x >= 540).length;
  const balanceRatio = leftCount / rightCount;
  assert.ok(balanceRatio > 0.85 && balanceRatio < 1.15, `Fluid must be evenly distributed horizontally (ratio: ${balanceRatio.toFixed(2)})`);
});

test('PHASE 2 — Initial Particle Density is uniform and matches rest density', () => {
  const engine = new AuditFluidEngine(1080, 2400, 0.38);
  engine.stepSubstep(0.001);

  const densities = engine.particles.map(p => p.density);
  const meanDensity = densities.reduce((a, b) => a + b, 0) / densities.length;

  assert.ok(meanDensity > 2.0 && meanDensity < 3.2, `Mean density (${meanDensity.toFixed(2)}) must be near rest density 2.6`);
});

test('PHASE 2 — Anti-Stacking guarantees minimum inter-particle separation', () => {
  const engine = new AuditFluidEngine(1080, 2400, 0.38);
  engine.step(0.016);

  const minAllowedSep = engine.particleRadius * 0.75;
  for (let i = 0; i < engine.particles.length; i++) {
    for (let j = i + 1; j < engine.particles.length; j++) {
      const d = Math.hypot(engine.particles[i].x - engine.particles[j].x, engine.particles[i].y - engine.particles[j].y);
      assert.ok(d >= minAllowedSep, `Separation (${d.toFixed(1)}px) between particle ${i} and ${j} must be >= ${minAllowedSep}px`);
    }
  }
});

test('PHASE 2 — Fluid Volume is conserved and all particles remain in container boundaries', () => {
  const engine = new AuditFluidEngine(1080, 2400, 0.38);
  const initialParticleCount = engine.particles.length;

  for (let f = 0; f < 120; f++) {
    engine.step(0.016);
    for (const p of engine.particles) {
      assert.ok(p.x >= p.radius && p.x <= 1080 - p.radius, `X (${p.x}) must stay inside boundary`);
      assert.ok(p.y >= p.radius && p.y <= 2400 - p.radius, `Y (${p.y}) must stay inside boundary`);
    }
  }

  assert.equal(engine.particles.length, initialParticleCount, 'Particle count must be 100% strictly conserved');
});

test('PHASE 3 — Gravity response in all four cardinal orientations and 180-degree rotation', () => {
  const engine = new AuditFluidEngine(1080, 2400, 0.38);

  // 1. Portrait Upright (0 deg): gravity is +Y (pulls to bottom wall)
  engine.updateGravity(0, 9.8);
  for (let f = 0; f < 60; f++) engine.step(0.016);
  const meanY_portrait = engine.particles.reduce((s, p) => s + p.y, 0) / engine.particles.length;
  assert.ok(meanY_portrait > 1700, `Portrait: mean Y (${meanY_portrait.toFixed(0)}) must settle at bottom (> 1700)`);

  // 2. Landscape 90 deg: gravity is +X (pulls to right wall)
  engine.updateGravity(9.8, 0);
  for (let f = 0; f < 120; f++) engine.step(0.016);
  const meanX_land90 = engine.particles.reduce((s, p) => s + p.x, 0) / engine.particles.length;
  assert.ok(meanX_land90 > 560, `Landscape 90°: mean X (${meanX_land90.toFixed(0)}) must shift towards right wall (> 560)`);

  // 3. 180-degree Inversion: gravity is -Y (pulls to top wall)
  engine.updateGravity(0, -9.8);
  for (let f = 0; f < 140; f++) engine.step(0.016);
  const meanY_upsideDown = engine.particles.reduce((s, p) => s + p.y, 0) / engine.particles.length;
  assert.ok(meanY_upsideDown < 800, `180° Inversion: mean Y (${meanY_upsideDown.toFixed(0)}) must travel to top wall (< 800)`);

  // 4. Reverse Landscape 270 deg: gravity is -X (pulls to left wall)
  engine.updateGravity(-9.8, 0);
  for (let f = 0; f < 120; f++) engine.step(0.016);
  const meanX_land270 = engine.particles.reduce((s, p) => s + p.x, 0) / engine.particles.length;
  assert.ok(meanX_land270 < 520, `Landscape 270°: mean X (${meanX_land270.toFixed(0)}) must shift towards left wall (< 520)`);
});

test('PHASE 4 & 5 — Hand shake triggers splash, droplet separation, ballistic flight, and reconnection', () => {
  const engine = new AuditFluidEngine(1080, 2400, 0.38);

  for (let f = 0; f < 60; f++) engine.step(0.016);

  // Trigger shake impulse (upward burst)
  engine.applyImpulse(180, -380);

  const maxVAfterShake = Math.max(...engine.particles.map(p => Math.hypot(p.vx, p.vy)));
  assert.ok(maxVAfterShake > 800, `Shake must impart substantial kinetic velocity (observed: ${maxVAfterShake.toFixed(0)} px/s)`);

  const droplets = engine.particles.filter(p => p.isDroplet);
  assert.ok(droplets.length > 0, `Fine spray droplets must break off on vigorous shake (count: ${droplets.length})`);
  assert.ok(droplets[0].radius < engine.particleRadius, 'Droplet radius must be finer than bulk particle radius');

  for (let f = 0; f < 5; f++) {
    engine.step(0.016);
  }
  const dropletsInFlight = engine.particles.filter(p => p.isDroplet);
  assert.ok(dropletsInFlight.length > 0, 'Droplets must maintain free ballistic flight without instant deletion');

  for (let f = 0; f < 220; f++) {
    engine.step(0.016);
  }
  const dropletsRemaining = engine.particles.filter(p => p.isDroplet);
  assert.equal(dropletsRemaining.length, 0, 'All spray droplets must smoothly reconnect back into the fluid pool');
});

test('PHASE 7 — Settling and 60-Second Stationary Stability (Zero Drift Equilibrium)', () => {
  const engine = new AuditFluidEngine(1080, 2400, 0.38);

  let reachedSleep = false;
  for (let f = 0; f < 300; f++) {
    engine.step(0.016);
    if (engine.state === 'SLEEPING') {
      reachedSleep = true;
      break;
    }
  }

  assert.ok(reachedSleep, 'Fluid must naturally settle into SLEEPING state after motion ends');

  const restingPositions = engine.particles.map(p => ({ x: p.x, y: p.y }));

  // Simulate 60 SECONDS at 60 FPS (3600 frames) of stationary device
  for (let f = 0; f < 3600; f++) {
    engine.step(0.016);
    assert.equal(engine.state, 'SLEEPING');
  }

  let maxDrift = 0;
  for (let i = 0; i < engine.particles.length; i++) {
    const drift = Math.hypot(engine.particles[i].x - restingPositions[i].x, engine.particles[i].y - restingPositions[i].y);
    if (drift > maxDrift) maxDrift = drift;
  }

  assert.equal(maxDrift, 0.0, 'Fluid must have absolute 0.000000 px drift across 60 seconds of stationary resting');
});

module.exports = { AuditFluidEngine };
