package com.waterlivewallpaper.features.wallpapers.sticks.simulation

import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * High-performance 2D rigid body physics engine for floating wooden sticks / twigs
 * interacting with SPH fluid dynamics (Archimedes buoyancy, viscous fluid drag,
 * rotational torque, boundary collisions, stick-to-stick capsule collisions, and touch/shake).
 */
class FloatingSticksEngine(
    private var screenWidth: Int = 1080,
    private var screenHeight: Int = 2400
) {
    val sticks = ArrayList<FloatingStick>()

    private val linearDampingAir = 0.999f
    private val linearDampingWater = 0.985f
    private val angularDampingAir = 0.995f
    private val angularDampingWater = 0.940f
    private val wallRestitution = 0.38f
    private val stickRestitution = 0.42f
    private val buoyancyCoeff = 1.35f
    private val fluidDragCoeff = 2.80f

    init {
        initializeDefaultSticks()
    }

    fun setDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        this.screenWidth = width
        this.screenHeight = height
        clampSticksToScreen()
    }

    fun initializeDefaultSticks(stickCount: Int = 4) {
        synchronized(sticks) {
            sticks.clear()
            val count = stickCount.coerceIn(2, 6)
            val spacingX = screenWidth / (count + 1f)

            for (i in 0 until count) {
                val texIdx = (i % 6) + 1
                // Natural variety in stick length and thickness
                val len = when (texIdx) {
                    1 -> 220f
                    2 -> 240f
                    3 -> 200f
                    4 -> 230f
                    5 -> 210f
                    else -> 190f
                }
                val rad = when (texIdx) {
                    2 -> 20f
                    4 -> 16f
                    6 -> 14f
                    else -> 18f
                }
                val initialX = spacingX * (i + 1) + (Random.nextFloat() - 0.5f) * 40f
                val initialY = screenHeight * 0.65f + (Random.nextFloat() - 0.5f) * 120f
                val initialAngle = (Random.nextFloat() - 0.5f) * 0.8f

                sticks.add(
                    FloatingStick(
                        id = i + 1,
                        x = initialX,
                        y = initialY,
                        vx = (Random.nextFloat() - 0.5f) * 10f,
                        vy = 0f,
                        angle = initialAngle,
                        angularVelocity = (Random.nextFloat() - 0.5f) * 0.2f,
                        length = len,
                        radius = rad,
                        woodDensity = 0.65f,
                        textureIndex = texIdx
                    )
                )
            }
        }
    }

    fun step(
        dtRaw: Float,
        fluidEngine: FluidSimulationEngine
    ) {
        val dt = dtRaw.coerceIn(0.001f, 0.033f)
        val gx = fluidEngine.getGravityX()
        val gy = fluidEngine.getGravityY()

        synchronized(sticks) {
            val numSticks = sticks.size
            if (numSticks == 0) return

            for (s in sticks) {
                // 1. Numerical coupling with Fluid: Sample points along stick
                val samples = s.getSamplePoints(5)
                var totalBuoyancyX = 0f
                var totalBuoyancyY = 0f
                var totalDragX = 0f
                var totalDragY = 0f
                var totalTorque = 0f
                var submergedPointsCount = 0

                for (p in samples) {
                    val px = p[0]
                    val py = p[1]
                    val rx = p[2]
                    val ry = p[3]

                    val fluidSample = fluidEngine.sampleFluidAt(px, py, sampleRadius = s.radius * 2.8f)

                    if (fluidSample.isSubmerged) {
                        submergedPointsCount++
                        val subFactor = (fluidSample.density).coerceIn(0.35f, 1.35f)

                        // Archimedes Buoyancy: acts opposite to gravity vector
                        // Wood density (0.65) vs water (1.0) -> net upward force
                        val buoyantScalar = (1.0f - s.woodDensity) * buoyancyCoeff * subFactor * (s.mass / samples.size)
                        val fbX = -gx * buoyantScalar
                        val fbY = -gy * buoyantScalar

                        totalBuoyancyX += fbX
                        totalBuoyancyY += fbY

                        // Local point velocity = stick CM velocity + omega x r
                        val pvx = s.vx - s.angularVelocity * ry
                        val pvy = s.vy + s.angularVelocity * rx

                        // Viscous fluid drag: relative velocity between water and stick point
                        val relVx = fluidSample.velocityX - pvx
                        val relVy = fluidSample.velocityY - pvy

                        val dragScalar = fluidDragCoeff * subFactor * 0.20f
                        val fdX = relVx * dragScalar
                        val fdY = relVy * dragScalar

                        totalDragX += fdX
                        totalDragY += fdY

                        // Torque = rx * Fy - ry * Fx
                        val totalPointFx = fbX + fdX
                        val totalPointFy = fbY + fdY
                        val pointTorque = rx * totalPointFy - ry * totalPointFx
                        totalTorque += pointTorque
                    }
                }

                s.submergedFraction = submergedPointsCount.toFloat() / samples.size

                // 2. Linear motion integration
                // Gravity applies to total stick mass
                val totalFx = s.mass * gx + totalBuoyancyX + totalDragX
                val totalFy = s.mass * gy + totalBuoyancyY + totalDragY

                val ax = totalFx / s.mass
                val ay = totalFy / s.mass

                s.vx += ax * dt
                s.vy += ay * dt

                // Damping
                val linearDamping = if (s.submergedFraction > 0.2f) linearDampingWater else linearDampingAir
                s.vx *= linearDamping
                s.vy *= linearDamping

                // Clamp extreme velocities for numerical stability
                val v2 = s.vx * s.vx + s.vy * s.vy
                if (v2 > 3500f * 3500f) {
                    val spd = sqrt(v2)
                    s.vx = (s.vx / spd) * 3500f
                    s.vy = (s.vy / spd) * 3500f
                }

                s.x += s.vx * dt
                s.y += s.vy * dt

                // 3. Angular motion integration
                val angularAccel = totalTorque / s.momentOfInertia
                s.angularVelocity += angularAccel * dt

                val angularDamping = if (s.submergedFraction > 0.2f) angularDampingWater else angularDampingAir
                s.angularVelocity *= angularDamping

                // Clamp angular velocity
                s.angularVelocity = s.angularVelocity.coerceIn(-18f, 18f)
                s.angle += s.angularVelocity * dt

                // Normalize angle
                if (s.angle > Math.PI) s.angle -= (2 * Math.PI).toFloat()
                if (s.angle < -Math.PI) s.angle += (2 * Math.PI).toFloat()

                // 4. Boundary collision check
                handleWallCollisions(s)
            }

            // 5. Stick-to-stick capsule collisions
            resolveStickCollisions()
        }
    }

    private fun handleWallCollisions(s: FloatingStick) {
        val halfL = s.length * 0.5f
        val cosA = cos(s.angle)
        val sinA = sin(s.angle)
        val r = s.radius

        val p1x = s.x - halfL * cosA
        val p1y = s.y - halfL * sinA
        val p2x = s.x + halfL * cosA
        val p2y = s.y + halfL * sinA

        // Check Left wall
        checkPointAgainstWall(s, p1x, p1y, -halfL * cosA, -halfL * sinA, wallNormalX = 1f, wallNormalY = 0f, wallDist = p1x - r)
        checkPointAgainstWall(s, p2x, p2y, halfL * cosA, halfL * sinA, wallNormalX = 1f, wallNormalY = 0f, wallDist = p2x - r)

        // Check Right wall
        checkPointAgainstWall(s, p1x, p1y, -halfL * cosA, -halfL * sinA, wallNormalX = -1f, wallNormalY = 0f, wallDist = (screenWidth - r) - p1x)
        checkPointAgainstWall(s, p2x, p2y, halfL * cosA, halfL * sinA, wallNormalX = -1f, wallNormalY = 0f, wallDist = (screenWidth - r) - p2x)

        // Check Top wall
        checkPointAgainstWall(s, p1x, p1y, -halfL * cosA, -halfL * sinA, wallNormalX = 0f, wallNormalY = 1f, wallDist = p1y - r)
        checkPointAgainstWall(s, p2x, p2y, halfL * cosA, halfL * sinA, wallNormalX = 0f, wallNormalY = 1f, wallDist = p2y - r)

        // Check Bottom wall
        checkPointAgainstWall(s, p1x, p1y, -halfL * cosA, -halfL * sinA, wallNormalX = 0f, wallNormalY = -1f, wallDist = (screenHeight - r) - p1y)
        checkPointAgainstWall(s, p2x, p2y, halfL * cosA, halfL * sinA, wallNormalX = 0f, wallNormalY = -1f, wallDist = (screenHeight - r) - p2y)

        // Center clamp
        s.x = s.x.coerceIn(r, screenWidth - r)
        s.y = s.y.coerceIn(r, screenHeight - r)
    }

    private fun checkPointAgainstWall(
        s: FloatingStick,
        px: Float,
        py: Float,
        rx: Float,
        ry: Float,
        wallNormalX: Float,
        wallNormalY: Float,
        wallDist: Float
    ) {
        if (wallDist < 0f) {
            // Penetration detected!
            val penetration = -wallDist
            s.x += wallNormalX * penetration * 0.7f
            s.y += wallNormalY * penetration * 0.7f

            val velDotNormal = s.vx * wallNormalX + s.vy * wallNormalY
            if (velDotNormal < 0f) {
                // Reflect velocity
                s.vx -= (1f + wallRestitution) * velDotNormal * wallNormalX
                s.vy -= (1f + wallRestitution) * velDotNormal * wallNormalY

                // Contact friction exerts rotational torque
                val torque = rx * wallNormalY - ry * wallNormalX
                s.angularVelocity += torque * 0.003f
            }
        }
    }

    private fun resolveStickCollisions() {
        val num = sticks.size
        for (i in 0 until num) {
            val a = sticks[i]
            for (j in i + 1 until num) {
                val b = sticks[j]
                val minDist = a.radius + b.radius
                val minDist2 = minDist * minDist

                // Fast AABB pre-check
                val dx = b.x - a.x
                val dy = b.y - a.y
                val maxReach = a.length * 0.5f + b.length * 0.5f + minDist
                if (dx * dx + dy * dy > maxReach * maxReach) continue

                // Precise Segment-to-Segment distance
                val (pa1, pa2) = a.getEndpoints()
                val (pb1, pb2) = b.getEndpoints()

                val closest = closestPointsBetweenSegments(
                    pa1[0], pa1[1], pa2[0], pa2[1],
                    pb1[0], pb1[1], pb2[0], pb2[1]
                )

                val cAx = closest[0]
                val cAy = closest[1]
                val cBx = closest[2]
                val cBy = closest[3]

                val cDx = cBx - cAx
                val cDy = cBy - cAy
                val dist2 = cDx * cDx + cDy * cDy

                if (dist2 < minDist2 && dist2 > 0.0001f) {
                    val dist = sqrt(dist2)
                    val overlap = minDist - dist
                    val nx = cDx / dist
                    val ny = cDy / dist

                    // Separate sticks
                    a.x -= nx * overlap * 0.5f
                    a.y -= ny * overlap * 0.5f
                    b.x += nx * overlap * 0.5f
                    b.y += ny * overlap * 0.5f

                    // Relative velocity at contact points
                    val rAx = cAx - a.x
                    val rAy = cAy - a.y
                    val rBx = cBx - b.x
                    val rBy = cBy - b.y

                    val vAx = a.vx - a.angularVelocity * rAy
                    val vAy = a.vy + a.angularVelocity * rAx
                    val vBx = b.vx - b.angularVelocity * rBy
                    val vBy = b.vy + b.angularVelocity * rBx

                    val relVx = vBx - vAx
                    val relVy = vBy - vAy
                    val normalVel = relVx * nx + relVy * ny

                    if (normalVel < 0f) {
                        val impulse = -(1f + stickRestitution) * normalVel / (1f / a.mass + 1f / b.mass)
                        val impX = nx * impulse
                        val impY = ny * impulse

                        a.vx -= impX / a.mass
                        a.vy -= impY / a.mass
                        b.vx += impX / b.mass
                        b.vy += impY / b.mass

                        a.angularVelocity -= (rAx * impY - rAy * impX) / a.momentOfInertia
                        b.angularVelocity += (rBx * impY - rBy * impX) / b.momentOfInertia
                    }
                }
            }
        }
    }

    /**
     * Exact closest points between two 2D line segments [p1-q1] and [p2-q2].
     * Returns [closest1X, closest1Y, closest2X, closest2Y].
     */
    private fun closestPointsBetweenSegments(
        p1x: Float, p1y: Float, q1x: Float, q1y: Float,
        p2x: Float, p2y: Float, q2x: Float, q2y: Float
    ): FloatArray {
        val d1x = q1x - p1x
        val d1y = q1y - p1y
        val d2x = q2x - p2x
        val d2y = q2y - p2y
        val rx = p1x - p2x
        val ry = p1y - p2y

        val a = d1x * d1x + d1y * d1y
        val e = d2x * d2x + d2y * d2y
        val f = d2x * rx + d2y * ry

        var s: Float
        var t: Float

        if (a <= 0.0001f && e <= 0.0001f) {
            return floatArrayOf(p1x, p1y, p2x, p2y)
        }
        if (a <= 0.0001f) {
            s = 0f
            t = (f / e).coerceIn(0f, 1f)
        } else {
            val c = d1x * rx + d1y * ry
            if (e <= 0.0001f) {
                t = 0f
                s = (-c / a).coerceIn(0f, 1f)
            } else {
                val b = d1x * d2x + d1y * d2y
                val denom = a * e - b * b
                s = if (denom != 0f) {
                    ((b * f - c * e) / denom).coerceIn(0f, 1f)
                } else {
                    0f
                }
                t = (b * s + f) / e
                if (t < 0f) {
                    t = 0f
                    s = (-c / a).coerceIn(0f, 1f)
                } else if (t > 1f) {
                    t = 1f
                    s = ((b - c) / a).coerceIn(0f, 1f)
                }
            }
        }

        val c1x = p1x + d1x * s
        val c1y = p1y + d1y * s
        val c2x = p2x + d2x * t
        val c2y = p2y + d2y * t

        return floatArrayOf(c1x, c1y, c2x, c2y)
    }

    fun applyShakeImpulse(impulseX: Float, impulseY: Float) {
        synchronized(sticks) {
            for (s in sticks) {
                val jitter = (0.9f + Random.nextFloat() * 0.2f)
                s.vx += impulseX * 1.2f * jitter
                s.vy += impulseY * 1.2f * jitter
                s.angularVelocity += (Random.nextFloat() - 0.5f) * 6.0f
            }
        }
    }

    fun addTouchDisturbance(touchX: Float, touchY: Float, strength: Float = 700f) {
        synchronized(sticks) {
            val touchRadius = 160f
            val touchRadius2 = touchRadius * touchRadius

            for (s in sticks) {
                val dx = s.x - touchX
                val dy = s.y - touchY
                val dist2 = dx * dx + dy * dy
                if (dist2 < touchRadius2 && dist2 > 0.001f) {
                    val dist = sqrt(dist2)
                    val factor = (1f - dist / touchRadius) * strength
                    val nx = dx / dist
                    val ny = dy / dist

                    s.vx += nx * factor
                    s.vy += ny * factor
                    s.angularVelocity += (Random.nextFloat() - 0.5f) * 4.0f
                }
            }
        }
    }

    private fun clampSticksToScreen() {
        synchronized(sticks) {
            for (s in sticks) {
                s.x = s.x.coerceIn(s.radius, screenWidth - s.radius)
                s.y = s.y.coerceIn(s.radius, screenHeight - s.radius)
            }
        }
    }
}
