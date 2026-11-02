const test = require('node:test');
const assert = require('node:assert/strict');

// Standalone implementation of the fluid solver algorithm matching FluidEngine.ts
class TestFluidSolver {
  constructor(width = 360, height = 640) {
    this.width = width;
    this.height = height;
    this.particleRadius = 18;
    this.restDensity = 3.5;
    this.stiffness = 12000;
    this.viscosity = 80;
    this.damping = 0.35;
    this.dropletMergeRadius = 26;
    this.gravityX = 0;
    this.gravityY = 980;
    this.particles = [];
    this.init(0.4);
  }

  init(volumeRatio) {
    this.particles = [];
    const count = Math.floor(60 + volumeRatio * 100);
    const cols = 10;
    const spacing = this.particleRadius * 1.5;
    const startY = this.height - Math.ceil(count / cols) * spacing - 20;

    for (let i = 0; i < count; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      this.particles.push({
        x: Math.max(this.particleRadius, Math.min(this.width - this.particleRadius, 50 + col * spacing)),
        y: Math.max(this.particleRadius, Math.min(this.height - this.particleRadius, startY + row * spacing)),
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

  step(rawDt) {
    const dt = Math.min(rawDt, 0.033);
    const h = this.particleRadius * 2.2;
    const h2 = h * h;

    // Density
    for (let i = 0; i < this.particles.length; i++) {
      const pi = this.particles[i];
      let density = 0;
      for (let j = 0; j < this.particles.length; j++) {
        const pj = this.particles[j];
        const dx = pj.x - pi.x;
        const dy = pj.y - pi.y;
        const r2 = dx * dx + dy * dy;
        if (r2 < h2) {
          const factor = 1 - r2 / h2;
          density += factor * factor * factor;
        }
      }
      pi.density = Math.max(density, 0.5);
      pi.pressure = this.stiffness * (pi.density - this.restDensity);
    }

    // Forces
    for (let i = 0; i < this.particles.length; i++) {
      const pi = this.particles[i];
      let fPressureX = 0;
      let fPressureY = 0;
      let fViscosityX = 0;
      let fViscosityY = 0;

      for (let j = 0; j < this.particles.length; j++) {
        if (i === j) continue;
        const pj = this.particles[j];
        const dx = pj.x - pi.x;
        const dy = pj.y - pi.y;
        const r = Math.hypot(dx, dy);

        if (r < h && r > 0.001) {
          const normX = dx / r;
          const normY = dy / r;
          const q = 1 - r / h;

          const avgPressure = (pi.pressure + pj.pressure) / (2 * pj.density);
          const pScalar = avgPressure * q * q;
          fPressureX -= normX * pScalar * 18;
          fPressureY -= normY * pScalar * 18;

          const dvx = pj.vx - pi.vx;
          const dvy = pj.vy - pi.vy;
          const vScalar = (this.viscosity / pj.density) * q;
          fViscosityX += dvx * vScalar;
          fViscosityY += dvy * vScalar;

          // Merge droplets
          if (pi.isDroplet && !pj.isDroplet && r < this.dropletMergeRadius) {
            pi.isDroplet = false;
            pi.radius = this.particleRadius;
            pi.vx = (pi.vx + pj.vx) * 0.5;
            pi.vy = (pi.vy + pj.vy) * 0.5;
          }
        }
      }

      const ax = (fPressureX + fViscosityX) / pi.density + this.gravityX;
      const ay = (fPressureY + fViscosityY) / pi.density + this.gravityY;

      pi.vx = (pi.vx + ax * dt) * 0.995;
      pi.vy = (pi.vy + ay * dt) * 0.995;

      pi.x += pi.vx * dt;
      pi.y += pi.vy * dt;

      // Boundary collision
      const r = pi.radius;
      if (pi.x < r) { pi.x = r; pi.vx = -pi.vx * this.damping; }
      if (pi.x > this.width - r) { pi.x = this.width - r; pi.vx = -pi.vx * this.damping; }
      if (pi.y < r) { pi.y = r; pi.vy = -pi.vy * this.damping; }
      if (pi.y > this.height - r) { pi.y = this.height - r; pi.vy = -pi.vy * this.damping; }
    }
  }
}

test('FluidSolver initializes particles safely within screen bounds', () => {
  const solver = new TestFluidSolver(360, 640);
  assert.ok(solver.particles.length > 50, 'Must have particle population');
  for (const p of solver.particles) {
    assert.ok(p.x >= p.radius && p.x <= 360 - p.radius, `X (${p.x}) must be in bounds`);
    assert.ok(p.y >= p.radius && p.y <= 640 - p.radius, `Y (${p.y}) must be in bounds`);
    assert.equal(p.vx, 0);
    assert.equal(p.vy, 0);
  }
});

test('FluidSolver tilts water toward the low side when gravity changes', () => {
  const solver = new TestFluidSolver(360, 640);
  // Gravity pointing to the right (+X)
  solver.gravityX = 980;
  solver.gravityY = 0;

  // Run 60 frames
  for (let i = 0; i < 60; i++) {
    solver.step(0.016);
  }

  // Mean X position of particles must have shifted significantly to the right
  const meanX = solver.particles.reduce((sum, p) => sum + p.x, 0) / solver.particles.length;
  assert.ok(meanX > 200, `Mean X (${meanX}) should be toward right wall`);
});

test('Droplets merge back into fluid pool when within collision radius', () => {
  const solver = new TestFluidSolver(360, 640);
  // Place one main fluid particle at center
  solver.particles = [
    {
      x: 180,
      y: 500,
      vx: 0,
      vy: 0,
      radius: 18,
      density: 3.5,
      pressure: 0,
      isDroplet: false,
      lifetime: 0,
    },
    // Place a detached droplet right above it falling toward it
    {
      x: 180,
      y: 485,
      vx: 0,
      vy: 120,
      radius: 8,
      density: 1.0,
      pressure: 0,
      isDroplet: true,
      lifetime: 0.5,
    },
  ];

  // Step simulation
  solver.step(0.016);

  // Droplet should have merged back
  const droplet = solver.particles[1];
  assert.equal(droplet.isDroplet, false, 'Droplet must merge upon touching fluid');
  assert.equal(droplet.radius, solver.particleRadius, 'Restored to standard particle radius');
});

test('Long-running simulation maintains numerical stability without NaNs or overflows', () => {
  const solver = new TestFluidSolver(360, 640);

  // Run 500 frames with varying gravity and impulses
  for (let f = 0; f < 500; f++) {
    if (f % 50 === 0) {
      solver.gravityX = Math.sin(f * 0.1) * 800;
      solver.gravityY = Math.cos(f * 0.1) * 800;
    }
    solver.step(0.016);

    for (const p of solver.particles) {
      assert.ok(!Number.isNaN(p.x), 'Particle X must not be NaN');
      assert.ok(!Number.isNaN(p.y), 'Particle Y must not be NaN');
      assert.ok(!Number.isNaN(p.vx), 'Particle VX must not be NaN');
      assert.ok(!Number.isNaN(p.vy), 'Particle VY must not be NaN');
      assert.ok(Number.isFinite(p.x), 'Particle X must be finite');
      assert.ok(Number.isFinite(p.y), 'Particle Y must be finite');
      assert.ok(p.x >= 0 && p.x <= solver.width, 'Particle must stay inside boundary');
      assert.ok(p.y >= 0 && p.y <= solver.height, 'Particle must stay inside boundary');
    }
  }
});
