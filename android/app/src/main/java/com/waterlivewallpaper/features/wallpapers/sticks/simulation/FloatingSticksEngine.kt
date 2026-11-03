package com.waterlivewallpaper.features.wallpapers.sticks.simulation

import com.waterlivewallpaper.features.wallpapers.water.simulation.FluidSimulationEngine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class StickSimulationState {
    ACTIVE,
    SETTLING,
    SLEEPING
}

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

    private val wallRestitution = 0.28f
    private val stickRestitution = 0.32f

    @Volatile var state: StickSimulationState = StickSimulationState.ACTIVE
        private set

    val isAsleep: Boolean
        get() = state == StickSimulationState.SLEEPING

    @Volatile private var settlingDuration = 0f
    @Volatile private var maxVelocityObserved = 0f
    @Volatile private var maxAngularVelocityObserved = 0f

    fun wakeUp() {
        state = StickSimulationState.ACTIVE
        settlingDuration = 0f
    }

    init {
        initializeDefaultSticks()
    }

    fun setDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        this.screenWidth = width
        this.screenHeight = height
        clampSticksToScreen()
    }

    private var currentScale: Float = 1.0f

    fun initializeDefaultSticks(stickCount: Int = 5, scale: Float = 1.0f) {
        synchronized(sticks) {
            sticks.clear()
            wakeUp()
            currentScale = scale
            val count = stickCount.coerceIn(1, 15)
            val cols = kotlin.math.min(count, 4)

            for (i in 0 until count) {
                val texIdx = (i % 6) + 1
                // Natural wood dimensions: ~35% to 42% of 1080p screen width for realistic presence
                val baseLen = when (texIdx) {
                    1 -> 420f
                    2 -> 460f
                    3 -> 380f
                    4 -> 440f
                    5 -> 410f
                    else -> 370f
                }
                val baseRad = when (texIdx) {
                    2 -> 40f
                    4 -> 35f
                    6 -> 32f
                    else -> 36f
                }

                val len = baseLen * scale
                val rad = baseRad * scale

                val col = i % cols
                val row = i / cols
                val spacingX = screenWidth / (cols + 1f)
                val initialX = spacingX * (col + 1f) + (Random.nextFloat() - 0.5f) * 15f
                val initialY = screenHeight * 0.45f + row * (rad * 3.2f) + (Random.nextFloat() - 0.5f) * 20f
                val initialAngle = (Random.nextFloat() - 0.5f) * 0.5f

                sticks.add(
                    FloatingStick(
                        id = i + 1,
                        x = initialX,
                        y = initialY,
                        vx = 0f,
                        vy = 0f,
                        angle = initialAngle,
                        angularVelocity = 0f,
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
        if (!fluidEngine.isAsleep && isAsleep) {
            wakeUp()
        }

        if (state == StickSimulationState.SLEEPING) return

        val dt = dtRaw.coerceIn(0.005f, 0.020f)
        val gx = fluidEngine.getGravityX()
        val gy = fluidEngine.getGravityY()

        synchronized(sticks) {
            val numSticks = sticks.size
            if (numSticks == 0) return

            var maxV2 = 0f
            var maxW2 = 0f

            for (s in sticks) {
                val samples = s.getSamplePoints(5)
                var totalBuoyancyX = 0f
                var totalBuoyancyY = 0f
                var totalDragX = 0f
                var totalDragY = 0f
                var totalTorque = 0f
                var sumImmersion = 0f

                for (p in samples) {
                    val px = p[0]
                    val py = p[1]
                    val rx = p[2]
                    val ry = p[3]

                    val fluidSample = fluidEngine.sampleFluidAt(px, py, sampleRadius = s.radius * 2.5f)

                    if (fluidSample.isSubmerged) {
                        val immersion = (fluidSample.density).coerceIn(0.2f, 1.0f)
                        sumImmersion += immersion

                        // Archimedes Buoyancy directly balances gravity when submerged
                        val buoyantScalar = (1.0f / s.woodDensity) * immersion * (s.mass / samples.size)
                        val fbX = -gx * buoyantScalar
                        val fbY = -gy * buoyantScalar

                        totalBuoyancyX += fbX
                        totalBuoyancyY += fbY

                        // Local point velocity
                        val pvx = s.vx - s.angularVelocity * ry
                        val pvy = s.vy + s.angularVelocity * rx

                        val fluidVx = if (fluidEngine.isAsleep) 0f else fluidSample.velocityX
                        val fluidVy = if (fluidEngine.isAsleep) 0f else fluidSample.velocityY
                        val relVx = fluidVx - pvx
                        val relVy = fluidVy - pvy

                        val dragCoeff = 8.0f * immersion
                        val fdX = relVx * dragCoeff
                        val fdY = relVy * dragCoeff

                        totalDragX += fdX
                        totalDragY += fdY

                        totalTorque += (rx * (fbY + fdY) - ry * (fbX + fdX))
                    }
                }

                s.submergedFraction = sumImmersion / samples.size

                val totalFx = s.mass * gx + totalBuoyancyX + totalDragX
                val totalFy = s.mass * gy + totalBuoyancyY + totalDragY

                s.vx += (totalFx / s.mass) * dt
                s.vy += (totalFy / s.mass) * dt

                val linearDamping = if (s.submergedFraction > 0.2f) 0.94f else 0.97f
                s.vx *= linearDamping
                s.vy *= linearDamping

                if (state == StickSimulationState.SETTLING) {
                    s.vx *= 0.88f
                    s.vy *= 0.88f
                    s.angularVelocity *= 0.85f
                }

                s.x += s.vx * dt
                s.y += s.vy * dt

                val angularAccel = totalTorque / s.momentOfInertia
                s.angularVelocity += angularAccel * dt

                val angularDamping = if (s.submergedFraction > 0.2f) 0.90f else 0.95f
                s.angularVelocity *= angularDamping

                s.angularVelocity = s.angularVelocity.coerceIn(-18f, 18f)
                s.angle += s.angularVelocity * dt

                if (s.angle > Math.PI) s.angle -= (2 * Math.PI).toFloat()
                if (s.angle < -Math.PI) s.angle += (2 * Math.PI).toFloat()

                val spd2 = s.vx * s.vx + s.vy * s.vy
                val w2 = s.angularVelocity * s.angularVelocity
                if (spd2 > maxV2) maxV2 = spd2
                if (w2 > maxW2) maxW2 = w2
            }

            // Multi-pass constraint solver
            for (iter in 0 until 3) {
                resolveStickCollisions()
                for (s in sticks) {
                    handleWallCollisions(s)
                }
            }

            maxVelocityObserved = kotlin.math.sqrt(maxV2)
            maxAngularVelocityObserved = kotlin.math.sqrt(maxW2)

            // State Transitions
            if (state == StickSimulationState.ACTIVE) {
                if (maxVelocityObserved < 150f && maxAngularVelocityObserved < 1.0f) {
                    settlingDuration += dt
                    if (settlingDuration > 0.20f) {
                        state = StickSimulationState.SETTLING
                        settlingDuration = 0f
                    }
                } else {
                    settlingDuration = 0f
                }
            } else if (state == StickSimulationState.SETTLING) {
                if (maxVelocityObserved > 250f) {
                    state = StickSimulationState.ACTIVE
                    settlingDuration = 0f
                } else {
                    settlingDuration += dt
                    if (settlingDuration > 0.35f && fluidEngine.isAsleep) {
                        for (s in sticks) {
                            s.vx = 0f
                            s.vy = 0f
                            s.angularVelocity = 0f
                        }
                        state = StickSimulationState.SLEEPING
                        maxVelocityObserved = 0f
                        maxAngularVelocityObserved = 0f
                    }
                }
            }
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
            s.x += wallNormalX * penetration * 0.85f
            s.y += wallNormalY * penetration * 0.85f

            val velDotNormal = s.vx * wallNormalX + s.vy * wallNormalY
            if (velDotNormal < 0f) {
                // Zero restitution for resting contact (< 40 px/s) to prevent bouncing
                val rest = if (kotlin.math.abs(velDotNormal) < 40f) 0.0f else wallRestitution
                s.vx -= (1f + rest) * velDotNormal * wallNormalX
                s.vy -= (1f + rest) * velDotNormal * wallNormalY

                // Wall friction stops continuous sliding
                val tanX = -wallNormalY
                val tanY = wallNormalX
                val vTan = s.vx * tanX + s.vy * tanY
                s.vx -= vTan * 0.25f * tanX
                s.vy -= vTan * 0.25f * tanY

                val torque = rx * wallNormalY - ry * wallNormalX
                s.angularVelocity += torque * 0.002f
                s.angularVelocity *= 0.85f
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
                        // Inelastic collision for resting contacts (< 40 px/s) to prevent micro-bounce jitter
                        val rest = if (kotlin.math.abs(normalVel) < 40f) 0.0f else stickRestitution
                        val impulse = -(1f + rest) * normalVel / (1f / a.mass + 1f / b.mass)
                        val impX = nx * impulse
                        val impY = ny * impulse

                        a.vx -= impX / a.mass
                        a.vy -= impY / a.mass
                        b.vx += impX / b.mass
                        b.vy += impY / b.mass

                        a.angularVelocity -= (rAx * impY - rAy * impX) / a.momentOfInertia
                        b.angularVelocity += (rBx * impY - rBy * impX) / b.momentOfInertia

                        // Wood-on-wood tangent friction: allows sticks to grip and stack solidly!
                        val tanX = -ny
                        val tanY = nx
                        val relVTan = relVx * tanX + relVy * tanY
                        val fImp = relVTan * 0.35f
                        a.vx += tanX * fImp * 0.5f
                        a.vy += tanY * fImp * 0.5f
                        b.vx -= tanX * fImp * 0.5f
                        b.vy -= tanY * fImp * 0.5f
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
            wakeUp()
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
            wakeUp()
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
