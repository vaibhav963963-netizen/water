import { FluidParticle, WallpaperSettings } from '../types/wallpaper';

export interface FluidSimulationParams {
  gravityX: number;
  gravityY: number;
  width: number;
  height: number;
  particleRadius: number;
  restDensity: number;
  stiffness: number;
  viscosity: number;
  surfaceTensionCoeff: number;
  damping: number;
  dropletSeparationThreshold: number;
  dropletMergeRadius: number;
}

export class FluidEngine {
  private particles: FluidParticle[] = [];
  private params: FluidSimulationParams;
  private width: number;
  private height: number;
  private isSettled: boolean = false;
  private maxVelocity: number = 2400;
  private shakeEnergy: number = 0;

  constructor(width: number, height: number, settings: WallpaperSettings) {
    this.width = width;
    this.height = height;
    this.params = {
      gravityX: 0,
      gravityY: 980 * settings.physicsSensitivity,
      width,
      height,
      particleRadius: 30,
      restDensity: 2.8,
      stiffness: 14000,
      viscosity: 28,
      surfaceTensionCoeff: 38,
      damping: 0.70,
      dropletSeparationThreshold: 750 / settings.shakeSensitivity,
      dropletMergeRadius: 40,
    };

    this.initializeParticles(settings.waterVolume);
  }

  public setDimensions(width: number, height: number): void {
    this.width = width;
    this.height = height;
    this.params.width = width;
    this.params.height = height;
    this.clampParticlesWithinBounds();
  }

  public initializeParticles(volumeRatio: number): void {
    this.particles = [];
    const baseCount = Math.floor(140 + volumeRatio * 240);
    const cols = Math.max(6, Math.floor(Math.sqrt(baseCount * (this.width / Math.max(1, this.height * 0.35)))));
    const spacing = this.params.particleRadius * 1.35;

    const startY = this.height - Math.ceil(baseCount / Math.max(1, cols)) * spacing - 25;
    const startX = (this.width - cols * spacing) / 2;

    for (let i = 0; i < baseCount; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      this.particles.push({
        x: Math.max(this.params.particleRadius, Math.min(this.width - this.params.particleRadius, startX + col * spacing + (Math.random() - 0.5) * 4)),
        y: Math.max(this.params.particleRadius, Math.min(this.height - this.params.particleRadius, startY + row * spacing + (Math.random() - 0.5) * 4)),
        vx: 0,
        vy: 0,
        radius: this.params.particleRadius,
        density: this.params.restDensity,
        pressure: 0,
        isDroplet: false,
        lifetime: 0,
      });
    }
    this.isSettled = false;
  }

  public updateGravity(gx: number, gy: number, sensitivity: number): void {
    const scale = 110 * sensitivity;
    this.params.gravityX = gx * scale;
    this.params.gravityY = gy * scale;
  }

  public applyImpulse(impulseX: number, impulseY: number): void {
    this.shakeEnergy += Math.hypot(impulseX, impulseY);
    const dropThreshold = this.params.dropletSeparationThreshold;

    for (let i = 0; i < this.particles.length; i++) {
      const p = this.particles[i];
      const factor = (p.y / this.height);
      p.vx += impulseX * (0.85 + Math.random() * 0.3) * factor;
      p.vy += impulseY * (0.85 + Math.random() * 0.3) * factor;

      const speed = Math.hypot(p.vx, p.vy);
      if (speed > dropThreshold && !p.isDroplet && Math.random() < 0.35) {
        p.isDroplet = true;
        p.radius = this.params.particleRadius * (0.42 + Math.random() * 0.3);
        p.lifetime = 0;
      }
    }
    this.isSettled = false;
  }

  public addTouchDisturbance(touchX: number, touchY: number, strength: number = 550): void {
    for (let i = 0; i < this.particles.length; i++) {
      const p = this.particles[i];
      const dx = p.x - touchX;
      const dy = p.y - touchY;
      const dist = Math.hypot(dx, dy);
      const influenceRadius = 160;
      if (dist < influenceRadius && dist > 0.001) {
        const factor = (1 - dist / influenceRadius) * strength;
        p.vx += (dx / dist) * factor;
        p.vy += (dy / dist) * factor;
        this.isSettled = false;
      }
    }
  }

  public step(dt: number): void {
    const clampedDt = Math.min(dt, 0.033);
    const h = this.params.particleRadius * 2.2;
    const h2 = h * h;

    this.shakeEnergy *= 0.92;

    // 1. Density
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
      pi.pressure = Math.max(0, this.params.stiffness * (pi.density - this.params.restDensity));
    }

    // 2. Internal forces: Repulsive Pressure + Cohesion Surface Tension + Viscosity
    const dropThreshold = this.params.dropletSeparationThreshold;

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

          // Repulsive pressure gradient
          const avgPressure = (pi.pressure + pj.pressure) / (2 * pj.density);
          const pScalar = avgPressure * q * q * 16;
          fPressureX -= normX * pScalar;
          fPressureY -= normY * pScalar;

          // Cohesion surface tension
          const cohesionScalar = this.params.surfaceTensionCoeff * q * q;
          fPressureX += normX * cohesionScalar;
          fPressureY += normY * cohesionScalar;

          // Viscous shear force
          const dvx = pj.vx - pi.vx;
          const dvy = pj.vy - pi.vy;
          const vScalar = (this.params.viscosity / pj.density) * q;
          fViscosityX += dvx * vScalar;
          fViscosityY += dvy * vScalar;

          // Droplet re-merging
          if (pi.isDroplet && !pj.isDroplet && r < this.params.dropletMergeRadius) {
            pi.isDroplet = false;
            pi.radius = this.params.particleRadius;
            pi.vx = (pi.vx + pj.vx) * 0.5;
            pi.vy = (pi.vy + pj.vy) * 0.5;
          }
        }
      }

      // Detachment of droplets under high velocity and low density
      if (!pi.isDroplet && pi.density < this.params.restDensity * 0.40) {
        const speed = Math.hypot(pi.vx, pi.vy);
        if (speed > dropThreshold) {
          pi.isDroplet = true;
          pi.radius = this.params.particleRadius * (0.42 + Math.random() * 0.3);
          pi.lifetime = 0;
        }
      }

      const ax = (fPressureX + fViscosityX) / pi.density + this.params.gravityX;
      const ay = (fPressureY + fViscosityY) / pi.density + this.params.gravityY;

      pi.vx += ax * clampedDt;
      pi.vy += ay * clampedDt;

      // Air drag
      pi.vx *= 0.996;
      pi.vy *= 0.996;

      // Clamp max velocity
      const currentSpeed = Math.hypot(pi.vx, pi.vy);
      if (currentSpeed > this.maxVelocity) {
        const ratio = this.maxVelocity / currentSpeed;
        pi.vx *= ratio;
        pi.vy *= ratio;
      }

      pi.x += pi.vx * clampedDt;
      pi.y += pi.vy * clampedDt;

      if (pi.isDroplet) {
        pi.lifetime += clampedDt;
        if (pi.lifetime > 4.0 && currentSpeed < 120) {
          pi.isDroplet = false;
          pi.radius = this.params.particleRadius;
        }
      }

      this.handleBoundaryCollision(pi);
    }

    // Settling check
    let totalKineticEnergy = 0;
    for (let i = 0; i < this.particles.length; i++) {
      const p = this.particles[i];
      totalKineticEnergy += (p.vx * p.vx + p.vy * p.vy);
    }
    const avgEnergy = totalKineticEnergy / this.particles.length;
    this.isSettled = avgEnergy < 120 && this.shakeEnergy < 10;
  }

  private handleBoundaryCollision(p: FluidParticle): void {
    const r = p.radius;
    const damping = this.params.damping;

    if (p.x < r) {
      p.x = r;
      p.vx = -p.vx * damping;
    } else if (p.x > this.width - r) {
      p.x = this.width - r;
      p.vx = -p.vx * damping;
    }

    if (p.y < r) {
      p.y = r;
      p.vy = -p.vy * damping;
    } else if (p.y > this.height - r) {
      p.y = this.height - r;
      p.vy = -p.vy * damping;
    }
  }

  private clampParticlesWithinBounds(): void {
    for (let i = 0; i < this.particles.length; i++) {
      this.handleBoundaryCollision(this.particles[i]);
    }
  }

  public getParticles(): readonly FluidParticle[] {
    return this.particles;
  }

  public getSettled(): boolean {
    return this.isSettled;
  }

  public getStats(): { particleCount: number; dropletCount: number; isSettled: boolean } {
    let droplets = 0;
    for (let i = 0; i < this.particles.length; i++) {
      if (this.particles[i].isDroplet) droplets++;
    }
    return {
      particleCount: this.particles.length,
      dropletCount: droplets,
      isSettled: this.isSettled,
    };
  }
}
