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

enum class SimulationState {
    ACTIVE,
    SETTLING,
    SLEEPING
}

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
    private val stiffness = 2800f
    private val viscosity = 35f // Water kinematic viscosity: flows freely with natural wave momentum
    private val surfaceTensionCoeff = 35f
    private val boundaryDamping = 0.50f
    private val maxVelocity = 4000f
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

    @Volatile var state: SimulationState = SimulationState.ACTIVE
        private set

    val isAsleep: Boolean
        get() = state == SimulationState.SLEEPING

    @Volatile private var maxVelocityObserved = 100f
    @Volatile private var avgVelocityObserved = 50f
    @Volatile private var settlingDuration = 0f

    private var sleepGravityX = 0f
    private var sleepGravityY = 9.8f * 520f * physicsSensitivity * flowSpeed

    fun wakeUp() {
        state = SimulationState.ACTIVE
        settlingDuration = 0f
    }

    fun isFluidCalm(): Boolean = isAsleep || maxVelocityObserved < 80f
    fun isFluidSettled(): Boolean = isAsleep || (state == SimulationState.SETTLING && settlingDuration > 0.15f)

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
            avgVelocityObserved = 20f
            settlingDuration = 0f
        }
    }

    fun updateGravity(gx: Float, gy: Float) {
        val scale = 520f * physicsSensitivity * flowSpeed
        val targetGx = gx * scale
        val targetGy = gy * scale

        if (state == SimulationState.SLEEPING) {
            val dGx = targetGx - sleepGravityX
            val dGy = targetGy - sleepGravityY
            val deltaGSq = dGx * dGx + dGy * dGy

            // Intentional tilt threshold: 0.35 m/s² (~2.2 degrees)
            val threshold = 0.35f * scale
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
            if (mag2 < 2.25f) return

            wakeUp()
            val impulseScale = flowSpeed * shakeSensitivity * 1.0f

            for (p in particles) {
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
                pi.pressure = max(0f, stiffness * (pi.density - restDensity))
            }

            // 2. Compute internal forces: Pressure + Surface Tension Cohesion + Viscosity
            val dropThreshold = 850f / shakeSensitivity
            val dropThreshold2 = dropThreshold * dropThreshold
            val maxVel2 = maxVelocity * maxVelocity
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
                        val r = kotlin.math.sqrt(r2)
                        val normX = dx / r
                        val normY = dy / r
                        val q = 1f - r / h

                        val avgPressure = (pi.pressure + pj.pressure) / (2f * pj.density)
                        val pScalar = avgPressure * q * q * 3.2f
                        fPressureX -= normX * pScalar
                        fPressureY -= normY * pScalar

                        val cohesionScalar = surfaceTensionCoeff * q * q
                        fPressureX += normX * cohesionScalar
                        fPressureY += normY * cohesionScalar

                        val dvx = pj.vx - pi.vx
                        val dvy = pj.vy - pi.vy
                        val vScalar = (viscosity / pj.density) * q
                        fViscosityX += dvx * vScalar
                        fViscosityY += dvy * vScalar

                        if (pi.isDroplet && !pj.isDroplet && r2 < dropletMergeRadius2) {
                            pi.isDroplet = false
                            pi.radius = particleRadius
                            pi.vx = (pi.vx + pj.vx) * 0.5f
                            pi.vy = (pi.vy + pj.vy) * 0.5f
                        }
                    }
                }

                // Droplet detachment: ONLY particles flying through low-density air
                if (!pi.isDroplet && pi.density < restDensity * 0.40f) {
                    val speed2 = pi.vx * pi.vx + pi.vy * pi.vy
                    if (speed2 > dropThreshold2 && Random.nextFloat() < 0.25f) {
                        pi.isDroplet = true
                        pi.radius = particleRadius * (0.60f + Random.nextFloat() * 0.35f)
                        pi.lifetime = 0f
                    }
                }

                if (pi.isDroplet && pi.density >= restDensity * 0.55f) {
                    pi.isDroplet = false
                    pi.radius = particleRadius
                }

                val ax = (fPressureX + fViscosityX) / pi.density + gravityX
                val ay = (fPressureY + fViscosityY) / pi.density + gravityY

                pi.vx += ax * dt
                pi.vy += ay * dt
            }

            // 3. Integration & Adaptive State Transitions
            var maxV2InStep = 0f
            var sumSpeed = 0f

            for (i in 0 until count) {
                val pi = particles[i]

                // Standard natural liquid momentum drag
                pi.vx *= 0.992f
                pi.vy *= 0.992f

                // In SETTLING state: progressively damp micro-velocities toward absolute rest
                if (state == SimulationState.SETTLING) {
                    pi.vx *= 0.85f
                    pi.vy *= 0.85f
                }

                val v2 = pi.vx * pi.vx + pi.vy * pi.vy
                val speed = kotlin.math.sqrt(v2)

                if (v2 > maxV2InStep) maxV2InStep = v2
                sumSpeed += speed

                if (v2 > maxVel2) {
                    val ratio = maxVelocity / speed
                    pi.vx *= ratio
                    pi.vy *= ratio
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
            avgVelocityObserved = sumSpeed / count

            // STATE MACHINE TRANSITIONS:
            if (state == SimulationState.ACTIVE) {
                if (maxVelocityObserved < 350f && avgVelocityObserved < 160f) {
                    settlingDuration += dt
                    if (settlingDuration > 0.20f) {
                        state = SimulationState.SETTLING
                        settlingDuration = 0f
                    }
                } else {
                    settlingDuration = 0f
                }
            } else if (state == SimulationState.SETTLING) {
                if (maxVelocityObserved > 450f) {
                    state = SimulationState.ACTIVE
                    settlingDuration = 0f
                } else {
                    settlingDuration += dt
                    // After 0.35s in settling, enter complete equilibrium SLEEP
                    if (settlingDuration > 0.35f) {
                        for (p in particles) {
                            p.vx = 0f
                            p.vy = 0f
                        }
                        state = SimulationState.SLEEPING
                        sleepGravityX = gravityX
                        sleepGravityY = gravityY
                        maxVelocityObserved = 0f
                        avgVelocityObserved = 0f
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
