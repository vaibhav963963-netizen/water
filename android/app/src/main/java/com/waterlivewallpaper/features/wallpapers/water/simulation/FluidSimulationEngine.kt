package com.waterlivewallpaper.features.wallpapers.water.simulation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var density: Float,
    var pressure: Float,
    var isDroplet: Boolean,
    var lifetime: Float
)

data class FluidSample(
    val density: Float,
    val velocityX: Float,
    val velocityY: Float,
    val isSubmerged: Boolean
)

class FluidSimulationEngine(
    private var screenWidth: Int,
    private var screenHeight: Int,
    private var waterVolumeRatio: Float = 0.38f,
    private var physicsSensitivity: Float = 1.0f,
    private var shakeSensitivity: Float = 1.2f,
    private var flowSpeed: Float = 2.0f
) {
    private val particles = ArrayList<Particle>()
    private val particleRadius = 30f
    private val restDensity = 2.6f
    private val stiffness = 4200f
    private val viscosity = 20f // Water kinematic viscosity: flows freely with natural wave momentum
    private val surfaceTensionCoeff = 40f
    private val boundaryDamping = 0.70f // Natural elastic container boundary reflection
    private val maxVelocity = 4500f
    private val dropletMergeRadius = 46f

    private var gravityX = 0f
    private var gravityY = 9.8f * 520f * physicsSensitivity * flowSpeed

    // Float buffer for OpenGL ES rendering: [x, y, radius, isDroplet]
    private var renderBuffer: FloatBuffer = ByteBuffer.allocateDirect(1500 * 4 * 4)
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

    private var isBatterySaver: Boolean = false

    @Volatile var isAsleep: Boolean = false
        private set

    @Volatile private var maxVelocityObserved = 100f
    @Volatile private var calmDuration = 0f

    private var lastAwakeGx = 0f
    private var lastAwakeGy = 9.8f * 520f

    fun wakeUp() {
        isAsleep = false
        calmDuration = 0f
    }

    fun isFluidCalm(): Boolean = isAsleep || maxVelocityObserved < 40f
    fun isFluidSettled(): Boolean = isAsleep || (maxVelocityObserved < 12f && calmDuration > 0.20f)

    fun updateSettings(
        volume: Float,
        sensitivity: Float,
        shakeSens: Float,
        speed: Float = flowSpeed,
        batterySaver: Boolean = isBatterySaver
    ) {
        val volumeChanged = kotlin.math.abs(this.waterVolumeRatio - volume) > 0.04f
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

    fun initializeParticles(volume: Float, batterySaver: Boolean = isBatterySaver) {
        synchronized(particles) {
            particles.clear()
            wakeUp()
            // High particle density to form rich continuous fluid body:
            // In Battery Saver mode, uses slightly leaner particle budget (110 - 240) to reduce O(N^2) calculations
            val baseMin = if (batterySaver) 110 else 160
            val baseScale = if (batterySaver) 180 else 280
            val maxCap = if (batterySaver) 260 else 440
            val count = (baseMin + volume * baseScale).toInt().coerceIn(baseMin, maxCap)
            val cols = max(8, kotlin.math.sqrt(count * (screenWidth.toFloat() / max(1f, screenHeight * 0.35f))).toInt())
            val spacing = particleRadius * 1.30f

            val startY = screenHeight - (count / cols + 2) * spacing - 20f
            val startX = (screenWidth - cols * spacing) / 2f

            for (i in 0 until count) {
                val col = i % cols
                val row = i / cols
                val px = (startX + col * spacing + (Random.nextFloat() - 0.5f) * 2f)
                    .coerceIn(particleRadius, screenWidth - particleRadius)
                val py = (startY + row * spacing + (Random.nextFloat() - 0.5f) * 2f)
                    .coerceIn(particleRadius, screenHeight - particleRadius)

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
            calmDuration = 0f
        }
    }

    fun updateGravity(gx: Float, gy: Float) {
        // High-speed real-world scale: 520f base gives realistic, non-slomo snappy gravity
        val scale = 520f * physicsSensitivity * flowSpeed
        val targetGx = gx * scale
        val targetGy = gy * scale

        val dGx = targetGx - lastAwakeGx
        val dGy = targetGy - lastAwakeGy
        val deltaGSq = dGx * dGx + dGy * dGy

        // Wake up simulation if intentional tilt occurred (> 180 px/s² ~ 0.35 m/s²)
        if (deltaGSq > 32400f) {
            this.gravityX = targetGx
            this.gravityY = targetGy
            lastAwakeGx = targetGx
            lastAwakeGy = targetGy
            wakeUp()
        } else if (!isAsleep) {
            this.gravityX = targetGx
            this.gravityY = targetGy
        }
    }

    fun applyImpulse(impulseX: Float, impulseY: Float) {
        synchronized(particles) {
            val mag2 = impulseX * impulseX + impulseY * impulseY
            if (mag2 < 2.25f) return

            wakeUp()
            maxVelocityObserved = 180f

            val impulseScale = flowSpeed * shakeSensitivity * 1.0f

            for (p in particles) {
                // Free surface particles slosh with slightly larger wave amplitude
                val heightFactor = (p.y / screenHeight.toFloat()).coerceIn(0.4f, 1.0f)
                val jitter = (0.96f + Random.nextFloat() * 0.08f)
                p.vx += impulseX * impulseScale * heightFactor * jitter
                p.vy += impulseY * impulseScale * heightFactor * jitter
            }
        }
    }

    fun addTouchDisturbance(touchX: Float, touchY: Float, strength: Float = 750f) {
        synchronized(particles) {
            val radius = 190f
            val radius2 = radius * radius
            wakeUp()
            maxVelocityObserved = 220f

            for (p in particles) {
                val dx = p.x - touchX
                val dy = p.y - touchY
                val dist2 = dx * dx + dy * dy
                if (dist2 < radius2 && dist2 > 0.001f) {
                    val dist = kotlin.math.sqrt(dist2)
                    val factor = (1f - dist / radius) * strength * flowSpeed * 0.6f
                    p.vx += (dx / dist) * factor
                    p.vy += (dy / dist) * factor
                }
            }
        }
    }

    fun step(rawDt: Float) {
        if (isAsleep) return

        val dt = rawDt.coerceIn(0.001f, 0.033f)
        if (isFluidCalm()) {
            // When fluid velocity is calm, 1 substep provides numerical stability while halving CPU cycles
            stepSubstep(dt)
        } else {
            // 2 sub-steps for energetic wave dynamics and splash integrity
            val subDt = dt * 0.5f
            stepSubstep(subDt)
            stepSubstep(subDt)
        }
    }

    private fun stepSubstep(dt: Float) {
        val h = particleRadius * 2.3f
        val h2 = h * h

        synchronized(particles) {
            val count = particles.size
            if (count == 0) return

            // 1. Compute local densities
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
                // Smooth positive pressure repulsion
                pi.pressure = max(0f, stiffness * (pi.density - restDensity))
            }

            // 2. Compute internal forces: Pressure + Surface Tension Cohesion + Viscosity
            val dropThreshold = 850f / shakeSensitivity
            val dropThreshold2 = dropThreshold * dropThreshold
            val maxVel2 = maxVelocity * maxVelocity
            val dropletMergeRadius2 = dropletMergeRadius * dropletMergeRadius
            var maxV2InStep = 0f

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

                    // Optimization: Check r2 < h2 first to eliminate >95% of hypot square root calls!
                    if (r2 < h2 && r2 > 0.0001f) {
                        val r = kotlin.math.sqrt(r2)
                        val normX = dx / r
                        val normY = dy / r
                        val q = 1f - r / h

                        // Repulsive pressure gradient to maintain liquid volume
                        val avgPressure = (pi.pressure + pj.pressure) / (2f * pj.density)
                        val pScalar = avgPressure * q * q * 4.5f
                        fPressureX -= normX * pScalar
                        fPressureY -= normY * pScalar

                        // Cohesive surface tension attraction pulling fluid into smooth continuous liquid
                        val cohesionScalar = surfaceTensionCoeff * q * q
                        fPressureX += normX * cohesionScalar
                        fPressureY += normY * cohesionScalar

                        // Viscous damping between neighbors for smooth liquid flow
                        val dvx = pj.vx - pi.vx
                        val dvy = pj.vy - pi.vy
                        val vScalar = (viscosity / pj.density) * q
                        fViscosityX += dvx * vScalar
                        fViscosityY += dvy * vScalar

                        // Seamless droplet merging when falling back into liquid body
                        if (pi.isDroplet && !pj.isDroplet && r2 < dropletMergeRadius2) {
                            pi.isDroplet = false
                            pi.radius = particleRadius
                            pi.vx = (pi.vx + pj.vx) * 0.5f
                            pi.vy = (pi.vy + pj.vy) * 0.5f
                        }
                    }
                }

                // Droplet detachment: ONLY particles flying through low-density air!
                if (!pi.isDroplet && pi.density < restDensity * 0.40f) {
                    val speed2 = pi.vx * pi.vx + pi.vy * pi.vy
                    if (speed2 > dropThreshold2 && Random.nextFloat() < 0.25f) {
                        pi.isDroplet = true
                        pi.radius = particleRadius * (0.60f + Random.nextFloat() * 0.35f)
                        pi.lifetime = 0f
                    }
                }

                // If a droplet is in or near the liquid body, restore full radius immediately!
                if (pi.isDroplet && pi.density >= restDensity * 0.55f) {
                    pi.isDroplet = false
                    pi.radius = particleRadius
                }

                val ax = (fPressureX + fViscosityX) / pi.density + gravityX
                val ay = (fPressureY + fViscosityY) / pi.density + gravityY

                pi.vx += ax * dt
                pi.vy += ay * dt

                // Low natural drag for authentic water momentum
                pi.vx *= 0.9984f
                pi.vy *= 0.9984f

                // Velocity damping & settling
                val v2 = pi.vx * pi.vx + pi.vy * pi.vy
                if (v2 > maxV2InStep) maxV2InStep = v2

                if (v2 > maxVel2) {
                    val speed = kotlin.math.sqrt(v2)
                    val ratio = maxVelocity / speed
                    pi.vx *= ratio
                    pi.vy *= ratio
                } else if (v2 < 2025f) { // speed < 45 px/s: progressive settling friction
                    pi.vx *= 0.82f
                    pi.vy *= 0.82f
                    if (v2 < 100f) { // speed < 10 px/s
                        pi.vx *= 0.60f
                        pi.vy *= 0.60f
                    }
                    if (v2 < 9f) { // speed < 3 px/s
                        pi.vx = 0f
                        pi.vy = 0f
                    }
                }

                pi.x += pi.vx * dt
                pi.y += pi.vy * dt

                if (pi.isDroplet) {
                    pi.lifetime += dt
                    if (pi.lifetime > 2.0f && v2 < 14400f) {
                        pi.isDroplet = false
                        pi.radius = particleRadius
                    }
                }

                handleWallCollision(pi)
            }

            maxVelocityObserved = kotlin.math.sqrt(maxV2InStep)
            if (maxVelocityObserved < 18f) {
                calmDuration += dt
                // Once fluid has settled for 0.20s, enter deep sleep:
                if (calmDuration > 0.20f) {
                    for (p in particles) {
                        p.vx = 0f
                        p.vy = 0f
                    }
                    isAsleep = true
                }
            } else {
                calmDuration = 0f
            }

            // 3. True Anti-Penetration Hard Core Constraint (fires ONLY on severe overlap, NOT on resting fluid)
            val hardCoreDist = particleRadius * 1.15f
            val hardCoreDist2 = hardCoreDist * hardCoreDist

            if (maxVelocityObserved > 4f || gravityX != 0f) {
                for (iter in 0 until 2) {
                    for (i in 0 until count) {
                        val pi = particles[i]
                        for (j in i + 1 until count) {
                            val pj = particles[j]
                            val dx = pj.x - pi.x
                            val dy = pj.y - pi.y
                            val r2 = dx * dx + dy * dy

                            if (r2 < hardCoreDist2 && r2 > 0.0001f) {
                                val r = kotlin.math.sqrt(r2)
                                val overlap = hardCoreDist - r
                                val normX = dx / r
                                val normY = dy / r

                                // Relax particles apart along collision normal
                                val correction = overlap * 0.5f
                                pi.x -= normX * correction
                                pi.y -= normY * correction
                                pj.x += normX * correction
                                pj.y += normY * correction

                                // Inelastic contact velocity damping along normal (prevents artificial bounce jitter)
                                val rvx = pi.vx - pj.vx
                                val rvy = pi.vy - pj.vy
                                val normalVel = rvx * normX + rvy * normY
                                if (normalVel > 0f) {
                                    val impulseVel = normalVel * 0.45f
                                    pi.vx -= normX * impulseVel
                                    pi.vy -= normY * impulseVel
                                    pj.vx += normX * impulseVel
                                    pj.vy += normY * impulseVel
                                }
                            }
                        }
                        handleWallCollision(pi)
                    }
                }
            }
        }
    }

    private fun handleWallCollision(p: Particle) {
        val r = p.radius
        if (p.x < r) {
            p.x = r
            p.vx = if (kotlin.math.abs(p.vx) < 25f) 0f else -p.vx * boundaryDamping
        } else if (p.x > screenWidth - r) {
            p.x = screenWidth - r
            p.vx = if (kotlin.math.abs(p.vx) < 25f) 0f else -p.vx * boundaryDamping
        }

        if (p.y < r) {
            p.y = r
            p.vy = if (kotlin.math.abs(p.vy) < 25f) 0f else -p.vy * boundaryDamping
        } else if (p.y > screenHeight - r) {
            p.y = screenHeight - r
            p.vy = if (kotlin.math.abs(p.vy) < 25f) 0f else -p.vy * boundaryDamping
        }
    }

    private fun clampParticles() {
        synchronized(particles) {
            for (p in particles) {
                handleWallCollision(p)
            }
        }
    }

    fun populateRenderBuffer(): Int {
        synchronized(particles) {
            val count = particles.size
            if (renderBuffer.capacity() < count * 4) {
                renderBuffer = ByteBuffer.allocateDirect(count * 4 * 4 * 2)
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
                    val dist = kotlin.math.sqrt(d2)
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
