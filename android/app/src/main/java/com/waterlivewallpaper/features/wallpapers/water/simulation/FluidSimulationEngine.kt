package com.waterlivewallpaper.features.wallpapers.water.simulation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var density: Float = 1.0f,
    var pressure: Float = 0.0f,
    var isDroplet: Boolean = false,
    var lifetime: Float = 0.0f
)

data class FluidSample(
    val density: Float,
    val velocityX: Float,
    val velocityY: Float,
    val isSubmerged: Boolean
)

enum class SimulationState {
    ACTIVE,
    SETTLING,
    SLEEPING
}

/**
 * High-Density 360-Degree Continuous Container Fluid Engine.
 *
 * Simulates real fluid stored in a closed 2D container [0, screenWidth] x [0, screenHeight]:
 * 1. 360-Degree Gravity: Responds to any phone orientation (upright, landscape, upside-down 180°, diagonal).
 * 2. Fine Particle Size: Small, high-density particles (radius 10-12px) that flow like water and pack
 *    flat against container walls instead of balling into giant jelly lumps.
 * 3. Wall-to-Wall Incompressible Fluid: Covers the entire container floor/walls without corner voids.
 * 4. Realistic Low Cohesion & Viscosity: Water flows freely and levels out flat under gravity.
 * 5. Settling & Absolute Sleep State Machine: Smoothly dissipates wave energy and locks to zero movement at rest.
 * 6. Dynamic Splash & Shake: High inertial impulses break off fine, realistic spray droplets with true ballistic trajectories.
 */
class FluidSimulationEngine(
    private var screenWidth: Int,
    private var screenHeight: Int,
    private var waterVolumeRatio: Float = 0.38f,
    private var physicsSensitivity: Float = 1.0f,
    private var shakeSensitivity: Float = 1.2f,
    private var flowSpeed: Float = 2.0f
) {
    // Dense particle budget with small, realistic droplet radius (10-12px)
    private val particles = ArrayList<Particle>()
    private val particleRadius = 11.0f
    private val restDensity = 3.2f
    private val stiffness = 4200f
    private val viscosity = 22f
    private val surfaceTensionCoeff = 1.8f // Ultra-low cohesion: flows flat like real water instead of contracting into a rubber jelly ball
    private val boundaryDamping = 0.40f
    private val maxVelocity = 3500f
    private val dropletMergeRadius = 24f

    private var gravityX = 0f
    private var gravityY = 9.8f * 520f * physicsSensitivity * flowSpeed

    private var isBatterySaver: Boolean = false

    @Volatile var state: SimulationState = SimulationState.ACTIVE
        private set

    val isAsleep: Boolean
        get() = state == SimulationState.SLEEPING

    @Volatile private var maxVelocityObserved = 100f
    @Volatile private var avgVelocityObserved = 50f
    @Volatile private var settlingDuration = 0f

    private var sleepGravityX = 0f
    private var sleepGravityY = 9.8f * 520f * physicsSensitivity * flowSpeed

    // OpenGL ES vertex buffer: [x, y, radius, isDroplet] -> 4 floats per particle
    private var renderBuffer: FloatBuffer = ByteBuffer.allocateDirect(1200 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    init {
        initializeParticles(waterVolumeRatio)
    }

    fun setDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        this.screenWidth = width
        this.screenHeight = height
        clampParticles()
    }

    fun wakeUp() {
        state = SimulationState.ACTIVE
        settlingDuration = 0f
    }

    fun isFluidCalm(): Boolean = isAsleep || maxVelocityObserved < 60f
    fun isFluidSettled(): Boolean = isAsleep || (state == SimulationState.SETTLING && settlingDuration > 0.15f)

    fun updateSettings(
        volume: Float,
        sensitivity: Float,
        shakeSens: Float,
        speed: Float = flowSpeed,
        batterySaver: Boolean = isBatterySaver
    ) {
        val volumeChanged = abs(this.waterVolumeRatio - volume) > 0.03f
        val modeChanged = this.isBatterySaver != batterySaver

        this.physicsSensitivity = sensitivity
        this.shakeSensitivity = shakeSens
        this.flowSpeed = speed
        this.isBatterySaver = batterySaver

        wakeUp()

        if (volumeChanged || modeChanged) {
            this.waterVolumeRatio = volume
            initializeParticles(volume, batterySaver)
        }
    }

    /**
     * Spawns particles spanning the full width across the container bottom so water starts as a
     * complete, level pool with full corner coverage.
     */
    fun initializeParticles(volume: Float, batterySaver: Boolean = isBatterySaver) {
        synchronized(particles) {
            particles.clear()
            wakeUp()

            val w = if (screenWidth > 0) screenWidth.toFloat() else 1080f
            val h = if (screenHeight > 0) screenHeight.toFloat() else 1920f

            // Budget: 280 to 480 fine particles for high resolution and realistic liquid density
            val baseMin = if (batterySaver) 220 else 320
            val baseScale = if (batterySaver) 240 else 360
            val maxCap = if (batterySaver) 380 else 520
            val count = (baseMin + volume * baseScale).toInt().coerceIn(baseMin, maxCap)

            val spacing = particleRadius * 1.65f
            val cols = max(12, ((w - particleRadius * 2) / spacing).toInt())
            val rows = (count + cols - 1) / cols

            val startX = (w - (cols - 1) * spacing) * 0.5f
            val startY = h - particleRadius - 15f - (rows - 1) * spacing

            for (i in 0 until count) {
                val col = i % cols
                val row = i / cols
                val px = (startX + col * spacing + (Random.nextFloat() - 0.5f) * 2f)
                    .coerceIn(particleRadius + 2f, w - particleRadius - 2f)
                val py = (startY + row * spacing + (Random.nextFloat() - 0.5f) * 2f)
                    .coerceIn(particleRadius + 2f, h - particleRadius - 2f)

                particles.add(
                    Particle(
                        x = px,
                        y = py,
                        vx = 0f,
                        vy = 0f,
                        radius = particleRadius,
                        density = restDensity,
                        pressure = 0f,
                        isDroplet = false,
                        lifetime = 0f
                    )
                )
            }

            maxVelocityObserved = 50f
            avgVelocityObserved = 20f
            settlingDuration = 0f
        }
    }

    /**
     * 360-Degree Continuous Gravity Vector.
     * gx and gy are the true sensor gravity components.
     * When upright: gy > 0.
     * When upside-down 180°: gy < 0.
     * When landscape: gx != 0.
     */
    fun updateGravity(gx: Float, gy: Float) {
        val scale = 520f * physicsSensitivity * flowSpeed
        val targetGx = gx * scale
        val targetGy = gy * scale

        if (state == SimulationState.SLEEPING) {
            val dGx = targetGx - sleepGravityX
            val dGy = targetGy - sleepGravityY
            val deltaGSq = dGx * dGx + dGy * dGy

            // 0.16 m/s² (~1.0°) intentional tilt threshold to wake from rest
            val threshold = 0.16f * scale
            if (deltaGSq >= threshold * threshold) {
                this.gravityX = targetGx
                this.gravityY = targetGy
                wakeUp()
            }
        } else {
            this.gravityX = targetGx
            this.gravityY = targetGy
        }
    }

    fun applyImpulse(impulseX: Float, impulseY: Float) {
        synchronized(particles) {
            val mag2 = impulseX * impulseX + impulseY * impulseY
            if (mag2 < 2.0f) return

            wakeUp()
            val impulseScale = flowSpeed * shakeSensitivity * 1.15f
            val h = if (screenHeight > 0) screenHeight.toFloat() else 1920f

            for (p in particles) {
                val jitter = (0.95f + Random.nextFloat() * 0.10f)
                p.vx += impulseX * impulseScale * jitter
                p.vy += impulseY * impulseScale * jitter

                // If violent shake, break surface particles into spray droplets with realistic small radius
                val vMag = hypot(p.vx, p.vy)
                if (vMag > 950f && Random.nextFloat() < 0.12f) {
                    p.isDroplet = true
                    p.radius = (particleRadius * (0.65f + Random.nextFloat() * 0.35f)).coerceIn(6f, 11f)
                    p.lifetime = 0f
                }
            }
        }
    }

    fun addTouchDisturbance(touchX: Float, touchY: Float, strength: Float = 700f) {
        synchronized(particles) {
            val radius = 220f
            val radius2 = radius * radius
            wakeUp()

            for (p in particles) {
                val dx = p.x - touchX
                val dy = p.y - touchY
                val dist2 = dx * dx + dy * dy
                if (dist2 < radius2 && dist2 > 0.001f) {
                    val dist = sqrt(dist2)
                    val factor = (1f - dist / radius) * strength * flowSpeed * 0.75f
                    p.vx += (dx / dist) * factor
                    p.vy += (dy / dist) * factor
                }
            }
        }
    }

    fun step(rawDt: Float) {
        if (state == SimulationState.SLEEPING) return

        val dt = rawDt.coerceIn(0.005f, 0.020f)
        stepSubstep(dt)
    }

    private fun stepSubstep(dt: Float) {
        val h = particleRadius * 2.3f
        val h2 = h * h

        synchronized(particles) {
            val count = particles.size
            if (count == 0) return

            // 1. Local SPH Density Calculation
            for (i in 0 until count) {
                val pi = particles[i]
                var density = 0f

                for (j in 0 until count) {
                    val pj = particles[j]
                    val dx = pj.x - pi.x
                    val dy = pj.y - pi.y
                    val r2 = dx * dx + dy * dy

                    if (r2 < h2) {
                        val factor = 1f - r2 / h2
                        density += factor * factor * factor
                    }
                }

                pi.density = max(density, 0.6f)
                pi.pressure = max(0f, stiffness * (pi.density - restDensity))
            }

            // 2. Compute Internal Forces: Pressure + Low Cohesion + Viscosity
            val dropletMergeRadius2 = dropletMergeRadius * dropletMergeRadius

            for (i in 0 until count) {
                val pi = particles[i]
                var fPressureX = 0f
                var fPressureY = 0f
                var fViscosityX = 0f
                var fViscosityY = 0f

                for (j in 0 until count) {
                    if (i == j) continue
                    val pj = particles[j]
                    val dx = pj.x - pi.x
                    val dy = pj.y - pi.y
                    val r2 = dx * dx + dy * dy

                    if (r2 < h2 && r2 > 0.0001f) {
                        val r = sqrt(r2)
                        val normX = dx / r
                        val normY = dy / r
                        val q = 1f - r / h

                        // Incompressible pressure repulsion
                        val avgPressure = (pi.pressure + pj.pressure) / (2f * pj.density)
                        val pScalar = avgPressure * q * q * 3.0f
                        fPressureX -= normX * pScalar
                        fPressureY -= normY * pScalar

                        // Realistic low cohesion (prevents rubber jelly balling)
                        val cohesionScalar = surfaceTensionCoeff * q * q
                        fPressureX += normX * cohesionScalar
                        fPressureY += normY * cohesionScalar

                        // Viscous momentum transfer
                        val dvx = pj.vx - pi.vx
                        val dvy = pj.vy - pi.vy
                        val vScalar = (viscosity / pj.density) * q
                        fViscosityX += dvx * vScalar
                        fViscosityY += dvy * vScalar

                        // Re-absorb spray droplets back into main fluid body
                        if (pi.isDroplet && !pj.isDroplet && r2 < dropletMergeRadius2) {
                            pi.isDroplet = false
                            pi.radius = particleRadius
                            pi.lifetime = 0f
                        }
                    }
                }

                // 3. Integrate Accelerations under 360-Degree Gravity Vector
                val invDensity = 1f / pi.density
                val ax = (fPressureX + fViscosityX) * invDensity + gravityX
                val ay = (fPressureY + fViscosityY) * invDensity + gravityY

                pi.vx += ax * dt
                pi.vy += ay * dt

                // Progressive Viscous Damping in SETTLING state
                if (state == SimulationState.SETTLING) {
                    pi.vx *= 0.92f
                    pi.vy *= 0.92f
                    // Sub-pixel velocity deadband: snaps to absolute rest
                    if (pi.vx * pi.vx + pi.vy * pi.vy < 64f) {
                        pi.vx = 0f
                        pi.vy = 0f
                    }
                }

                // Velocity clamping
                val v2 = pi.vx * pi.vx + pi.vy * pi.vy
                if (v2 > maxVelocity * maxVelocity) {
                    val scale = maxVelocity / sqrt(v2)
                    pi.vx *= scale
                    pi.vy *= scale
                }

                // Droplet lifetime handling
                if (pi.isDroplet) {
                    pi.lifetime += dt
                    if (pi.lifetime > 3.0f) {
                        pi.isDroplet = false
                        pi.radius = particleRadius
                    }
                }

                pi.x += pi.vx * dt
                pi.y += pi.vy * dt

                handleWallCollision(pi)
            }

            // 4. Soft particle anti-stacking push
            val minSeparation = particleRadius * 1.15f
            val minSep2 = minSeparation * minSeparation
            for (i in 0 until count) {
                val pi = particles[i]
                for (j in (i + 1) until count) {
                    val pj = particles[j]
                    val dx = pj.x - pi.x
                    val dy = pj.y - pi.y
                    val d2 = dx * dx + dy * dy
                    if (d2 < minSep2 && d2 > 0.001f) {
                        val d = sqrt(d2)
                        val push = (minSeparation - d) * 0.15f
                        val nx = dx / d
                        val ny = dy / d
                        pi.x -= nx * push
                        pi.y -= ny * push
                        pj.x += nx * push
                        pj.y += ny * push
                    }
                }
            }

            // 5. Monitor Kinetic Energy for State Machine
            var maxV = 0f
            var sumV = 0f
            for (p in particles) {
                val v = hypot(p.vx, p.vy)
                if (v > maxV) maxV = v
                sumV += v
            }
            maxVelocityObserved = maxV
            avgVelocityObserved = sumV / count

            // 6. Transition State Machine: ACTIVE -> SETTLING -> SLEEPING
            if (state == SimulationState.ACTIVE) {
                if (maxVelocityObserved < 90f && avgVelocityObserved < 35f) {
                    settlingDuration += dt
                    if (settlingDuration > 0.30f) {
                        state = SimulationState.SETTLING
                        settlingDuration = 0f
                    }
                } else {
                    settlingDuration = 0f
                }
            } else if (state == SimulationState.SETTLING) {
                if (maxVelocityObserved > 160f || avgVelocityObserved > 55f) {
                    state = SimulationState.ACTIVE
                    settlingDuration = 0f
                } else {
                    settlingDuration += dt
                    // Once fluid motion decays to microscopic levels, enter absolute SLEEP
                    if (settlingDuration > 0.45f && maxVelocityObserved < 20f && avgVelocityObserved < 8f) {
                        for (p in particles) {
                            p.vx = 0f
                            p.vy = 0f
                            p.isDroplet = false
                            p.radius = particleRadius
                        }
                        sleepGravityX = gravityX
                        sleepGravityY = gravityY
                        state = SimulationState.SLEEPING
                        settlingDuration = 0f
                    }
                }
            }
        }
    }

    /**
     * Boundary collisions on ALL 4 walls [0, screenWidth] x [0, screenHeight].
     * Supports full 360-degree rotation (upside down, portrait, landscape, diagonal).
     */
    private fun handleWallCollision(p: Particle) {
        val r = p.radius
        val w = if (screenWidth > 0) screenWidth.toFloat() else 1080f
        val h = if (screenHeight > 0) screenHeight.toFloat() else 1920f

        // Left / Right walls
        if (p.x < r) {
            p.x = r
            p.vx = if (abs(p.vx) < 20f) 0f else -p.vx * boundaryDamping
        } else if (p.x > w - r) {
            p.x = w - r
            p.vx = if (abs(p.vx) < 20f) 0f else -p.vx * boundaryDamping
        }

        // Top / Bottom walls (Top wall handles 180° upside-down phone inversion!)
        if (p.y < r) {
            p.y = r
            p.vy = if (abs(p.vy) < 20f) 0f else -p.vy * boundaryDamping
        } else if (p.y > h - r) {
            p.y = h - r
            p.vy = if (abs(p.vy) < 20f) 0f else -p.vy * boundaryDamping
        }
    }

    private fun clampParticles() {
        synchronized(particles) {
            for (p in particles) {
                handleWallCollision(p)
            }
        }
    }

    /**
     * Populates OpenGL ES render buffer for continuous fluid particles.
     * Format: [x, y, radius, isDroplet]
     */
    fun populateRenderBuffer(): Int {
        synchronized(particles) {
            val count = particles.size
            val requiredCapacity = count * 4
            if (renderBuffer.capacity() < requiredCapacity) {
                renderBuffer = ByteBuffer.allocateDirect(requiredCapacity * 4 * 2)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            }
            renderBuffer.clear()
            for (i in 0 until count) {
                val p = particles[i]
                renderBuffer.put(p.x)
                renderBuffer.put(p.y)
                renderBuffer.put(p.radius)
                renderBuffer.put(if (p.isDroplet) 1.0f else 0.0f)
            }
            renderBuffer.flip()
            return count
        }
    }

    fun getRenderBuffer(): FloatBuffer = renderBuffer

    fun getParticlesCount(): Int = particles.size
    fun getGravityX(): Float = gravityX
    fun getGravityY(): Float = gravityY

    /**
     * Samples fluid for physical interaction with Floating Sticks live wallpaper.
     */
    fun sampleFluidAt(x: Float, y: Float, sampleRadius: Float = 55f): FluidSample {
        val r2Max = sampleRadius * sampleRadius
        var countNearby = 0
        var sumVx = 0f
        var sumVy = 0f
        var densityAccum = 0f

        synchronized(particles) {
            val size = particles.size
            for (i in 0 until size) {
                val p = particles[i]
                val dx = p.x - x
                val dy = p.y - y
                val d2 = dx * dx + dy * dy
                if (d2 < r2Max) {
                    val dist = sqrt(d2)
                    val weight = 1f - dist / sampleRadius
                    densityAccum += weight * (p.density / restDensity)
                    sumVx += p.vx * weight
                    sumVy += p.vy * weight
                    countNearby++
                }
            }
        }

        val avgVx = if (!isAsleep && countNearby > 0) sumVx / countNearby else 0f
        val avgVy = if (!isAsleep && countNearby > 0) sumVy / countNearby else 0f
        val isSubmerged = densityAccum > 0.35f

        return FluidSample(
            density = densityAccum,
            velocityX = avgVx,
            velocityY = avgVy,
            isSubmerged = isSubmerged
        )
    }
}
