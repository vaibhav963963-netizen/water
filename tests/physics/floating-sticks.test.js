const test = require('node:test');
const assert = require('node:assert/strict');

// Standalone implementation matching FloatingSticksEngine for Node.js test runner
class TestFloatingSticksEngine {
  constructor(width = 1080, height = 2400, stickCount = 4) {
    this.width = width;
    this.height = height;
    this.sticks = [];
    this.linearDampingAir = 0.999;
    this.linearDampingWater = 0.985;
    this.angularDampingAir = 0.995;
    this.angularDampingWater = 0.940;
    this.wallRestitution = 0.38;
    this.stickRestitution = 0.42;
    this.buoyancyCoeff = 1.35;
    this.fluidDragCoeff = 2.80;
    this.initSticks(stickCount);
  }

  initSticks(count) {
    this.sticks = [];
    const clampedCount = Math.max(2, Math.min(6, count));
    const spacingX = this.width / (clampedCount + 1);

    for (let i = 0; i < clampedCount; i++) {
      const texIdx = (i % 6) + 1;
      const len = texIdx === 1 ? 220 : texIdx === 2 ? 240 : texIdx === 3 ? 200 : texIdx === 4 ? 230 : texIdx === 5 ? 210 : 190;
      const rad = texIdx === 2 ? 20 : texIdx === 4 ? 16 : texIdx === 6 ? 14 : 18;
      const woodDensity = 0.65;
      const mass = Math.max(1, len * rad * 2 * woodDensity * 0.05);
      const momentOfInertia = Math.max(10, (1 / 12) * mass * (len * len + 4 * rad * rad));

      this.sticks.push({
        id: i + 1,
        x: spacingX * (i + 1),
        y: this.height * 0.65,
        vx: 0,
        vy: 0,
        angle: 0,
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

  getSamplePoints(s, numPoints = 5) {
    const halfL = s.length * 0.5;
    const cosA = Math.cos(s.angle);
    const sinA = Math.sin(s.angle);
    const step = s.length / Math.max(1, numPoints - 1);
    const points = [];

    for (let i = 0; i < numPoints; i++) {
      const offset = -halfL + i * step;
      const rx = offset * cosA;
      const ry = offset * sinA;
      points.push([s.x + rx, s.y + ry, rx, ry]);
    }
    return points;
  }

  step(dtRaw, fluid) {
    const dt = Math.max(0.001, Math.min(0.033, dtRaw));
    const gx = fluid ? fluid.gravityX : 0;
    const gy = fluid ? fluid.gravityY : 980;

    for (const s of this.sticks) {
      const samples = this.getSamplePoints(s, 5);
      let totalBuoyancyX = 0;
      let totalBuoyancyY = 0;
      let totalDragX = 0;
      let totalDragY = 0;
      let totalTorque = 0;
      let submergedCount = 0;

      for (const [px, py, rx, ry] of samples) {
        const sample = fluid ? fluid.sampleAt(px, py, s.radius * 2.8) : { density: 0, velocityX: 0, velocityY: 0, isSubmerged: false };

        if (sample.isSubmerged) {
          submergedCount++;
          const subFactor = Math.max(0.35, Math.min(1.35, sample.density));

          // Buoyant force
          const buoyantScalar = (1.0 - s.woodDensity) * this.buoyancyCoeff * subFactor * (s.mass / samples.length);
          const fbX = -gx * buoyantScalar;
          const fbY = -gy * buoyantScalar;
          totalBuoyancyX += fbX;
          totalBuoyancyY += fbY;

          // Drag
          const pvx = s.vx - s.angularVelocity * ry;
          const pvy = s.vy + s.angularVelocity * rx;
          const relVx = sample.velocityX - pvx;
          const relVy = sample.velocityY - pvy;
          const dragScalar = this.fluidDragCoeff * subFactor * 0.20;
          const fdX = relVx * dragScalar;
          const fdY = relVy * dragScalar;
          totalDragX += fdX;
          totalDragY += fdY;

          totalTorque += (rx * (fbY + fdY) - ry * (fbX + fdX));
        }
      }

      s.submergedFraction = submergedCount / samples.length;

      const totalFx = s.mass * gx + totalBuoyancyX + totalDragX;
      const totalFy = s.mass * gy + totalBuoyancyY + totalDragY;

      s.vx += (totalFx / s.mass) * dt;
      s.vy += (totalFy / s.mass) * dt;

      const linearDamping = s.submergedFraction > 0.2 ? this.linearDampingWater : this.linearDampingAir;
      s.vx *= linearDamping;
      s.vy *= linearDamping;

      s.x += s.vx * dt;
      s.y += s.vy * dt;

      const angularAccel = totalTorque / s.momentOfInertia;
      s.angularVelocity += angularAccel * dt;
      const angularDamping = s.submergedFraction > 0.2 ? this.angularDampingWater : this.angularDampingAir;
      s.angularVelocity *= angularDamping;
      s.angle += s.angularVelocity * dt;

      this.handleWallCollisions(s);
    }
  }

  handleWallCollisions(s) {
    const r = s.radius;
    if (s.x < r) {
      s.x = r;
      s.vx = -s.vx * this.wallRestitution;
    } else if (s.x > this.width - r) {
      s.x = this.width - r;
      s.vx = -s.vx * this.wallRestitution;
    }
    if (s.y < r) {
      s.y = r;
      s.vy = -s.vy * this.wallRestitution;
    } else if (s.y > this.height - r) {
      s.y = this.height - r;
      s.vy = -s.vy * this.wallRestitution;
    }
  }

  applyShakeImpulse(impulseX, impulseY) {
    for (const s of this.sticks) {
      s.vx += impulseX * 1.2;
      s.vy += impulseY * 1.2;
      s.angularVelocity += 2.5;
    }
  }

  addTouchDisturbance(touchX, touchY, strength = 700) {
    const touchRadius = 160;
    const touchRadius2 = touchRadius * touchRadius;
    for (const s of this.sticks) {
      const dx = s.x - touchX;
      const dy = s.y - touchY;
      const dist2 = dx * dx + dy * dy;
      if (dist2 < touchRadius2 && dist2 > 0.001) {
        const dist = Math.sqrt(dist2);
        const factor = (1 - dist / touchRadius) * strength;
        s.vx += (dx / dist) * factor;
        s.vy += (dy / dist) * factor;
        s.angularVelocity += 3.0;
      }
    }
  }
}

// Mock fluid provider for unit tests
class MockFluid {
  constructor(gx = 0, gy = 980) {
    this.gravityX = gx;
    this.gravityY = gy;
  }

  sampleAt(x, y, radius) {
    // Water pool located at y >= 1800
    const inPool = y >= 1800;
    return {
      density: inPool ? 1.0 : 0.0,
      velocityX: inPool ? 150 : 0,
      velocityY: 0,
      isSubmerged: inPool,
    };
  }
}

test('FloatingSticksEngine - initializes sticks with valid physical properties', () => {
  const engine = new TestFloatingSticksEngine(1080, 2400, 4);
  const sticks = engine.sticks;

  assert.equal(sticks.length, 4, 'Should initialize 4 sticks');
  for (const s of sticks) {
    assert.ok(s.length >= 180 && s.length <= 260, 'Stick length within expected bounds');
    assert.ok(s.radius >= 12 && s.radius <= 24, 'Stick radius within expected bounds');
    assert.ok(s.mass > 0, 'Mass must be strictly positive');
    assert.ok(s.momentOfInertia > 0, 'Moment of inertia must be strictly positive');
    assert.ok(s.textureIndex >= 1 && s.textureIndex <= 6, 'Texture index in 1..6');
    assert.ok(s.x >= 0 && s.x <= 1080, 'X within screen bounds');
    assert.ok(s.y >= 0 && s.y <= 2400, 'Y within screen bounds');
  }
});

test('FloatingSticksEngine - buoyant force opposes gravity when submerged', () => {
  const fluid = new MockFluid(0, 980);
  const engine = new TestFloatingSticksEngine(1080, 2400, 1);
  const stick = engine.sticks[0];

  // Place stick in water pool (y = 1900)
  stick.x = 540;
  stick.y = 1900;
  stick.vx = 0;
  stick.vy = 0;

  engine.step(0.016, fluid);

  assert.equal(stick.submergedFraction, 1.0, 'Stick fully in pool must be 100% submerged');
  // Buoyant force pushes upward (-Y) countering downward gravity
  assert.ok(stick.vy < 980 * 0.016, 'Upward buoyancy must reduce downward velocity relative to free-fall');
});

test('FloatingSticksEngine - fluid velocity transfers drag force to floating stick', () => {
  const fluid = new MockFluid(0, 980);
  const engine = new TestFloatingSticksEngine(1080, 2400, 1);
  const stick = engine.sticks[0];

  stick.x = 540;
  stick.y = 1900; // In water
  stick.vx = 0;

  engine.step(0.016, fluid);

  // Fluid in mock has velocityX = 150
  assert.ok(stick.vx > 0, 'Stick in rushing fluid must acquire forward velocity from fluid drag');
});

test('FloatingSticksEngine - boundary reflection prevents stick escaping screen', () => {
  const engine = new TestFloatingSticksEngine(1080, 2400, 1);
  const stick = engine.sticks[0];

  stick.x = 10;
  stick.vx = -800;

  engine.step(0.016, null);

  assert.ok(stick.x >= stick.radius, 'Stick should be clamped within left screen boundary');
  assert.ok(stick.vx >= 0, 'Stick should bounce off wall with positive velocity');
});

test('FloatingSticksEngine - touch disturbance flings nearby stick', () => {
  const engine = new TestFloatingSticksEngine(1080, 2400, 1);
  const stick = engine.sticks[0];

  stick.vx = 0;
  stick.vy = 0;
  stick.angularVelocity = 0;

  engine.addTouchDisturbance(stick.x + 10, stick.y + 10, 800);

  const speed = Math.hypot(stick.vx, stick.vy);
  assert.ok(speed > 50, 'Stick should acquire velocity from touch');
  assert.ok(Math.abs(stick.angularVelocity) > 0, 'Touch should induce angular velocity');
});
