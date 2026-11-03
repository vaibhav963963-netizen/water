package com.waterlivewallpaper.simulation

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

class FluidSimulationEngine(
    private var screenWidth: Int,
    private var screenHeight: Int,
    private var waterVolumeRatio: Float = 0.38f,
    private var physicsSensitivity: Float = 1.0f,
    private var shakeSensitivity: Float = 1.2f,
    private var flowSpeed: Float = 2.0f
) {
    private val particles = ArrayList<Particle>()
    private val particleRadius = 35f
    private val restDensity = 2.4f
    private val stiffness = 4600f
    private val viscosity = 16f // Low viscosity: water flows freely with energetic waves
    private val surfaceTensionCoeff = 44f
    private val boundaryDamping = 0.74f // High restitution: water splashes and surges energetically against boundaries
    private val maxVelocity = 4800f
    private val dropletMergeRadius = 50f

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

    fun updateSettings(volume: Float, sensitivity: Float, shakeSens: Float, speed: Float = flowSpeed) {
        this.physicsSensitivity = sensitivity
        this.shakeSensitivity = shakeSens
        this.flowSpeed = speed
        if (kotlin.math.abs(this.waterVolumeRatio - volume) > 0.04f) {
            this.waterVolumeRatio = volume
            initializeParticles(volume)
        }
    }

    fun initializeParticles(volume: Float) {
        synchronized(particles) {
            particles.clear()
            // High particle density to form rich continuous fluid body:
            // Scales with user volume setting so corner accumulation retains solid, deep water mass!
            val count = (160 + volume * 260).toInt().coerceIn(100, 480)
            val cols = max(8, kotlin.math.sqrt(count * (screenWidth.toFloat() / max(1f, screenHeight * 0.35f))).toInt())
            val spacing = particleRadius * 1.28f

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
        }
    }

    fun updateGravity(gx: Float, gy: Float) {
        // High-speed real-world scale: 520f base gives realistic, non-slomo snappy gravity
        val scale = 520f * physicsSensitivity * flowSpeed
        this.gravityX = gx * scale
        this.gravityY = gy * scale
    }

    fun applyImpulse(impulseX: Float, impulseY: Float) {
        synchronized(particles) {
            val mag = hypot(impulseX, impulseY)
            if (mag < 5f) return

            // Dynamic impulse scaling: fast violent shake generates massive kinetic slosh!
            val nonLinearFactor = 1.0f + (mag / 260f).coerceAtMost(4.0f)
            val dropThreshold = 700f / shakeSensitivity

            for (p in particles) {
                // Liquid surge driven by device inertia
                val heightFactor = (p.y / screenHeight.toFloat()).coerceIn(0.2f, 1f)
                val randJitter = (0.90f + Random.nextFloat() * 0.20f)
                p.vx += impulseX * shakeSensitivity * randJitter * heightFactor * flowSpeed * 1.35f * nonLinearFactor
                p.vy += impulseY * shakeSensitivity * randJitter * heightFactor * flowSpeed * 1.35f * nonLinearFactor

                // Energetic surface particles detach as realistic droplets ONLY if detached from dense liquid body
                val speed = hypot(p.vx, p.vy)
                if (speed > dropThreshold && !p.isDroplet && p.density < restDensity * 0.55f && Random.nextFloat() < 0.30f) {
                    p.isDroplet = true
                    p.radius = particleRadius * (0.60f + Random.nextFloat() * 0.35f)
                    p.lifetime = 0f
                }
            }
        }
    }

    fun addTouchDisturbance(touchX: Float, touchY: Float, strength: Float = 750f) {
        synchronized(particles) {
            val radius = 190f
            for (p in particles) {
                val dx = p.x - touchX
                val dy = p.y - touchY
                val dist = hypot(dx, dy)
                if (dist < radius && dist > 0.001f) {
                    val factor = (1f - dist / radius) * strength * flowSpeed * 0.6f
                    p.vx += (dx / dist) * factor
                    p.vy += (dy / dist) * factor
                }
            }
        }
    }

    fun step(rawDt: Float) {
        val dt = rawDt.coerceIn(0.001f, 0.033f)
        // 2 sub-steps for crisp high-speed stability
        val subDt = dt / 2f
        repeat(2) {
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
                    val r = hypot(dx, dy)

                    if (r < h && r > 0.001f) {
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
                        if (pi.isDroplet && !pj.isDroplet && r < dropletMergeRadius) {
                            pi.isDroplet = false
                            pi.radius = particleRadius
                            pi.vx = (pi.vx + pj.vx) * 0.5f
                            pi.vy = (pi.vy + pj.vy) * 0.5f
                        }
                    }
                }

                // Droplet detachment: ONLY particles flying through low-density air!
                // NEVER shrink particles in the dense pool (prevents corner volume collapse)
                if (!pi.isDroplet && pi.density < restDensity * 0.40f) {
                    val speed = hypot(pi.vx, pi.vy)
                    if (speed > dropThreshold && Random.nextFloat() < 0.25f) {
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

                // Velocity clamping
                var speed = hypot(pi.vx, pi.vy)
                if (speed > maxVelocity) {
                    val ratio = maxVelocity / speed
                    pi.vx *= ratio
                    pi.vy *= ratio
                    speed = maxVelocity
                }

                // Calm Resting State Stabilization:
                // Smoothly damp out micro-tremors only when nearly at rest (< 25 px/s)
                if (speed < 25f) {
                    pi.vx *= 0.88f
                    pi.vy *= 0.88f
                    if (speed < 2.5f) {
                        pi.vx = 0f
                        pi.vy = 0f
                    }
                }

                pi.x += pi.vx * dt
                pi.y += pi.vy * dt

                if (pi.isDroplet) {
                    pi.lifetime += dt
                    if (pi.lifetime > 2.0f && speed < 120f) {
                        pi.isDroplet = false
                        pi.radius = particleRadius
                    }
                }

                handleWallCollision(pi)
            }
        }
    }

    private fun handleWallCollision(p: Particle) {
        val r = p.radius
        if (p.x < r) {
            p.x = r
            p.vx = -p.vx * boundaryDamping
        } else if (p.x > screenWidth - r) {
            p.x = screenWidth - r
            p.vx = -p.vx * boundaryDamping
        }

        if (p.y < r) {
            p.y = r
            p.vy = -p.vy * boundaryDamping
        } else if (p.y > screenHeight - r) {
            p.y = screenHeight - r
            p.vy = -p.vy * boundaryDamping
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
}
