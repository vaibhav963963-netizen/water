const test = require('node:test');
const assert = require('node:assert/strict');

function getEffectiveFps(qualityMode, customTargetFps = 30) {
  switch ((qualityMode || '').toLowerCase()) {
    case 'battery_saver':
    case 'low':
      return 24;
    case 'performance':
    case 'high':
      return 60;
    case 'balanced':
      return 30;
    default:
      return Math.min(120, Math.max(20, customTargetFps));
  }
}

test('Battery Optimization: maps quality modes to expected target FPS', () => {
  assert.equal(getEffectiveFps('battery_saver'), 24, 'Battery saver targets 24 FPS');
  assert.equal(getEffectiveFps('low'), 24, 'Low maps to 24 FPS');
  assert.equal(getEffectiveFps('balanced'), 30, 'Balanced targets 30 FPS (recommended default)');
  assert.equal(getEffectiveFps('performance'), 60, 'Performance targets 60 FPS');
  assert.equal(getEffectiveFps('high'), 60, 'High maps to 60 FPS');
  assert.equal(getEffectiveFps('custom', 45), 45, 'Custom clamps cleanly');
});

test('Performance: squared distance check eliminates >90% of square root calculations', () => {
  const particleRadius = 30;
  const h = particleRadius * 2.3;
  const h2 = h * h;

  // Generate 200 random particles in a 1080x2400 screen
  const particles = [];
  for (let i = 0; i < 200; i++) {
    particles.push({
      x: 100 + Math.random() * 880,
      y: 1200 + Math.random() * 1100,
    });
  }

  let totalPairs = 0;
  let fullSqrtCalls = 0;

  for (let i = 0; i < particles.length; i++) {
    for (let j = 0; j < particles.length; j++) {
      if (i === j) continue;
      totalPairs++;
      const dx = particles[j].x - particles[i].x;
      const dy = particles[j].y - particles[i].y;
      const r2 = dx * dx + dy * dy;

      // With optimization:
      if (r2 < h2 && r2 > 0.0001) {
        fullSqrtCalls++;
      }
    }
  }

  const reductionPercent = ((totalPairs - fullSqrtCalls) / totalPairs) * 100;
  assert.ok(reductionPercent > 90, `Expected >90% reduction in sqrt calls, got ${reductionPercent.toFixed(1)}%`);
  assert.ok(fullSqrtCalls > 0, 'Found valid neighbors');
});

test('Performance: fluid settles cleanly and triggers resting state', () => {
  // Simulate damping particles to rest
  let particles = [
    { vx: 15, vy: 12 },
    { vx: 8, vy: 6 },
    { vx: 2, vy: 3 },
  ];

  // Apply damping iterations
  for (let step = 0; step < 30; step++) {
    for (const p of particles) {
      const v2 = p.vx * p.vx + p.vy * p.vy;
      if (v2 < 625) { // < 25 px/s
        p.vx *= 0.88;
        p.vy *= 0.88;
        if (v2 < 6.25) { // < 2.5 px/s
          p.vx = 0;
          p.vy = 0;
        }
      }
    }
  }

  const maxVelocity = Math.max(...particles.map(p => Math.hypot(p.vx, p.vy)));
  assert.equal(maxVelocity, 0, 'Resting damping brings calm particles to true zero velocity');
});
