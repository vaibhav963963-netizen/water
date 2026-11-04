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
import kotlin.math.tan
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var density: Float = 1.0f,
    var pressure: Float = 0.0f,
    var isDroplet: Boolean = true,
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
 * Authentic Container Water Simulation Engine.
 *
 * Models real liquid stored in a 2D glass container:
 * 1. Continuous fluid body spanning the full container width (zero voids, zero corner collapse).
 * 2. Coupled harmonic wave surface with shallow water wave propagation (c = sqrt(gh)).
 * 3. Gravitational equilibrium alignment: surface tilts naturally perpendicular to gravity.
 * 4. Slosh inertia & momentum: rapid device tilts drive realistic sloshing waves that bounce off walls.
 * 5. Vigorous shake spray: high acceleration launches fine micro-droplets (4-8px) that follow ballistic
 *    trajectories under gravity and create surface splash ripples upon re-entry.
 * 6. Viscous wave damping: settles to absolute, mathematically still equilibrium when phone is motionless.
 */
class FluidSimulationEngine(
    private var screenWidth: Int,
    private var screenHeight: Int,
    private var waterVolumeRatio: Float = 0.38f,
    private var physicsSensitivity: Float = 1.0f,
    private var shakeSensitivity: Float = 1.2f,
    private var flowSpeed: Float = 2.0f
) {
    companion object {
        private const val COLUMN_COUNT = 96
        private const val MAX_DROPLETS = 48
    }

    // Surface wave nodes
    private val nodeX = FloatArray(COLUMN_COUNT)
    private val nodeY = FloatArray(COLUMN_COUNT)
    private val nodeV = FloatArray(COLUMN_COUNT)
    private val nodeEqY = FloatArray(COLUMN_COUNT)

    // Fine spray droplets launched on vigorous shake
    private val droplets = ArrayList<Particle>()

    // Gravity state
    private var gravityX = 0f
    private var gravityY = 9.8f * 520f * physicsSensitivity * flowSpeed
    private var targetSlope = 0f
    private var currentSlope = 0f
    private var lastSlope = 0f

    private var waterBaseHeight = 0f
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

    // Vertex buffer for fluid body mesh: GL_TRIANGLE_STRIP
    // Each vertex: [x, y, depth, slope] -> 4 floats per vertex
    // 2 vertices per column (top surface + bottom of screen) -> COLUMN_COUNT * 2 * 4 floats
    private var fluidMeshBuffer: FloatBuffer = ByteBuffer.allocateDirect(COLUMN_COUNT * 2 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // Vertex buffer for fine spray droplets: GL_POINTS
    // Each droplet: [x, y, radius, 1.0f] -> 4 floats
    private var dropletBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_DROPLETS * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    init {
        recomputeGeometry()
        initializeSurface(waterVolumeRatio)
    }

    private fun recomputeGeometry() {
        val w = if (screenWidth > 0) screenWidth.toFloat() else 1080f
        val h = if (screenHeight > 0) screenHeight.toFloat() else 1920f
        val dx = w / (COLUMN_COUNT - 1).toFloat()
        for (i in 0 until COLUMN_COUNT) {
            nodeX[i] = i * dx
        }
        waterBaseHeight = h * (1.0f - waterVolumeRatio.coerceIn(0.12f, 0.85f))
    }

    fun setDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        this.screenWidth = width
        this.screenHeight = height
        recomputeGeometry()
        initializeSurface(waterVolumeRatio)
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
        this.physicsSensitivity = sensitivity
        this.shakeSensitivity = shakeSens
        this.flowSpeed = speed
        this.isBatterySaver = batterySaver

        if (volumeChanged) {
            this.waterVolumeRatio = volume
            recomputeGeometry()
            initializeSurface(volume, batterySaver)
        }
        wakeUp()
    }

    fun initializeParticles(volume: Float, batterySaver: Boolean = isBatterySaver) {
        initializeSurface(volume, batterySaver)
    }

    fun initializeSurface(volume: Float, batterySaver: Boolean = isBatterySaver) {
        synchronized(this) {
            this.waterVolumeRatio = volume
            val h = if (screenHeight > 0) screenHeight.toFloat() else 1920f
            waterBaseHeight = h * (1.0f - volume.coerceIn(0.12f, 0.85f))

            for (i in 0 until COLUMN_COUNT) {
                nodeEqY[i] = waterBaseHeight
                nodeY[i] = waterBaseHeight
                nodeV[i] = 0f
            }
            droplets.clear()
            currentSlope = 0f
            targetSlope = 0f
            lastSlope = 0f
            wakeUp()
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

        // Compute equilibrium surface slope perpendicular to gravity vector
        // In screen space: Y points down.
        // If device tilts right (gx > 0), water pools on the right (higher X has lower Y, i.e., higher water level).
        val effectiveGy = max(1.2f, abs(gy))
        val rawSlope = (gx / effectiveGy) * 0.95f * physicsSensitivity
        // Clamp maximum slope to prevent surface clipping past container top/bottom margins
        val maxSafeSlope = ((screenHeight * 0.70f) / max(1f, screenWidth.toFloat()))
        this.targetSlope = rawSlope.coerceIn(-maxSafeSlope, maxSafeSlope)
    }

    fun applyImpulse(impulseX: Float, impulseY: Float) {
        synchronized(this) {
            val mag2 = impulseX * impulseX + impulseY * impulseY
            if (mag2 < 2.0f) return

            wakeUp()
            val midX = screenWidth * 0.5f
            val sloshScale = flowSpeed * shakeSensitivity * 3.2f

            // Inject slosh momentum: horizontal jerk drives opposite-sign waves across left/right walls
            for (i in 0 until COLUMN_COUNT) {
                val relX = (nodeX[i] - midX) / midX
                val sloshV = -impulseX * relX * sloshScale * 2.8f
                val verticalV = -impulseY * sloshScale * 1.4f
                nodeV[i] += sloshV + verticalV
            }

            // Vigorous shake: spawn realistic small spray droplets (4-8px) from wave crests
            val impulseMag = sqrt(mag2)
            if (impulseMag > 4.5f && droplets.size < MAX_DROPLETS) {
                val spawnCount = min(12, ((impulseMag - 4.0f) * 2.5f * shakeSensitivity).toInt().coerceAtLeast(3))
                val peakIndex = if (impulseX > 0) COLUMN_COUNT - 8 else 8

                for (s in 0 until spawnCount) {
                    if (droplets.size >= MAX_DROPLETS) break
                    val col = (peakIndex + Random.nextInt(-6, 7)).coerceIn(2, COLUMN_COUNT - 3)
                    val px = nodeX[col] + (Random.nextFloat() - 0.5f) * 16f
                    val py = nodeY[col] - 8f - Random.nextFloat() * 12f
                    val dropRadius = Random.nextFloat() * 3.5f + 4.0f // 4.0 to 7.5px realistic droplet radius

                    val launchVx = -impulseX * sloshScale * 0.6f + (Random.nextFloat() - 0.5f) * 140f
                    val launchVy = -abs(impulseY * sloshScale * 0.8f) - (Random.nextFloat() * 220f + 160f)

                    droplets.add(
                        Particle(
                            x = px.coerceIn(dropRadius, screenWidth - dropRadius),
                            y = py.coerceIn(dropRadius, screenHeight - dropRadius),
                            vx = launchVx,
                            vy = launchVy,
                            radius = dropRadius,
                            isDroplet = true,
                            lifetime = 0f
                        )
                    )
                }
            }
        }
    }

    fun addTouchDisturbance(touchX: Float, touchY: Float, strength: Float = 600f) {
        synchronized(this) {
            wakeUp()
            val rippleRadius = 220f
            val rippleScale = strength * flowSpeed * 0.95f

            for (i in 0 until COLUMN_COUNT) {
                val dist = abs(nodeX[i] - touchX)
                if (dist < rippleRadius) {
                    val factor = 1.0f - dist / rippleRadius
                    // Smooth cosine ripple profile
                    val profile = (cos(dist / rippleRadius * Math.PI.toFloat()) + 1.0f) * 0.5f
                    nodeV[i] += profile * factor * rippleScale
                }
            }

            // Small tactile splash droplet on hard tap near the surface
            val surfY = getSurfaceHeightAt(touchX)
            if (abs(touchY - surfY) < 60f && droplets.size < MAX_DROPLETS) {
                for (d in 0 until 3) {
                    if (droplets.size >= MAX_DROPLETS) break
                    val r = Random.nextFloat() * 2.5f + 4.0f
                    droplets.add(
                        Particle(
                            x = touchX + (Random.nextFloat() - 0.5f) * 20f,
                            y = surfY - 6f,
                            vx = (Random.nextFloat() - 0.5f) * 180f,
                            vy = -(Random.nextFloat() * 190f + 120f),
                            radius = r,
                            isDroplet = true
                        )
                    )
                }
            }
        }
    }

    fun step(rawDt: Float) {
        if (state == SimulationState.SLEEPING) return

        val dt = rawDt.coerceIn(0.005f, 0.022f)
        synchronized(this) {
            stepSimulation(dt)
        }
    }

    private fun stepSimulation(dt: Float) {
        val midX = screenWidth * 0.5f

        // Smoothly interpolate current surface slope toward target slope with natural fluid lag
        val slopeResponsiveness = 14.0f * flowSpeed * physicsSensitivity
        val deltaSlope = targetSlope - currentSlope
        currentSlope += deltaSlope * min(1.0f, slopeResponsiveness * dt)

        // Inertial slosh injection on fast tilt changes
        val slopeVelocity = (currentSlope - lastSlope) / max(0.001f, dt)
        lastSlope = currentSlope

        if (abs(slopeVelocity) > 0.08f) {
            val sloshKick = -slopeVelocity * 45f * flowSpeed
            for (i in 0 until COLUMN_COUNT) {
                val relX = (nodeX[i] - midX) / midX
                nodeV[i] += sloshKick * relX * dt
            }
        }

        // Recompute equilibrium heights according to tilted container plane (volume conserving)
        for (i in 0 until COLUMN_COUNT) {
            val relX = nodeX[i] - midX
            // Equilibrium plane: passes through center (midX, waterBaseHeight)
            nodeEqY[i] = (waterBaseHeight - currentSlope * relX).coerceIn(screenHeight * 0.06f, screenHeight * 0.94f)
        }

        // 1. Wave Equation Integration (Coupled Harmonic Oscillators with Shallow Water Dispersion)
        val springK = 145f * flowSpeed
        val waveSpread = 8800f * flowSpeed
        val dx = screenWidth / (COLUMN_COUNT - 1).toFloat()
        val dx2 = dx * dx

        // In SETTLING state: progressively damp down wave energy
        val damping = when (state) {
            SimulationState.SETTLING -> 0.915f
            SimulationState.ACTIVE -> 0.965f
            SimulationState.SLEEPING -> 0.0f
        }

        var maxV = 0f
        var sumV = 0f
        var maxDisplacement = 0f

        val tempV = FloatArray(COLUMN_COUNT)

        for (i in 0 until COLUMN_COUNT) {
            val displacement = nodeY[i] - nodeEqY[i]
            val dispAbs = abs(displacement)
            if (dispAbs > maxDisplacement) maxDisplacement = dispAbs

            val restoringForce = -springK * displacement

            // 1D Laplacian for wave spread between adjacent surface columns
            val left = if (i > 0) nodeY[i - 1] else nodeY[i]
            val right = if (i < COLUMN_COUNT - 1) nodeY[i + 1] else nodeY[i]
            val laplacian = (left + right - 2f * nodeY[i]) / dx2
            val spreadForce = waveSpread * laplacian

            var v = (nodeV[i] + (restoringForce + spreadForce) * dt) * damping

            // Deadband cutoff in settling state: sub-pixel velocities snap cleanly to zero
            if (state == SimulationState.SETTLING && abs(v) < 6.0f && dispAbs < 2.0f) {
                v = 0f
            }

            tempV[i] = v
            val vAbs = abs(v)
            if (vAbs > maxV) maxV = vAbs
            sumV += vAbs
        }

        for (i in 0 until COLUMN_COUNT) {
            nodeV[i] = tempV[i]
            nodeY[i] = (nodeY[i] + nodeV[i] * dt).coerceIn(screenHeight * 0.04f, screenHeight * 0.96f)
        }

        maxVelocityObserved = maxV
        avgVelocityObserved = sumV / COLUMN_COUNT

        // 2. Advance Fine Spray Droplets
        val gravityAccY = 9.8f * 520f * physicsSensitivity
        val dropIterator = droplets.iterator()
        while (dropIterator.hasNext()) {
            val d = dropIterator.next()
            d.lifetime += dt
            d.vx += (gravityX * 0.4f) * dt
            d.vy += (gravityAccY * 0.85f) * dt
            d.vx *= 0.985f // air drag
            d.vy *= 0.992f

            d.x += d.vx * dt
            d.y += d.vy * dt

            // Container wall collisions
            if (d.x < d.radius) {
                d.x = d.radius
                d.vx = -d.vx * 0.5f
            } else if (d.x > screenWidth - d.radius) {
                d.x = screenWidth - d.radius
                d.vx = -d.vx * 0.5f
            }

            // Top boundary
            if (d.y < d.radius) {
                d.y = d.radius
                d.vy = -d.vy * 0.5f
            }

            // Check re-entry into surface line (absorption + splash impulse)
            val surfY = getSurfaceHeightAt(d.x)
            if (d.y >= surfY) {
                // Splash surface node nearest to droplet entry
                val col = ((d.x / screenWidth) * (COLUMN_COUNT - 1)).toInt().coerceIn(0, COLUMN_COUNT - 1)
                nodeV[col] += d.vy * 0.22f
                dropIterator.remove()
            } else if (d.lifetime > 3.0f || d.y > screenHeight) {
                dropIterator.remove()
            }
        }

        // 3. State Machine: Smooth transition from ACTIVE -> SETTLING -> SLEEPING
        if (state == SimulationState.ACTIVE) {
            if (maxVelocityObserved < 80f && maxDisplacement < 12f && droplets.isEmpty()) {
                settlingDuration += dt
                if (settlingDuration > 0.35f) {
                    state = SimulationState.SETTLING
                    settlingDuration = 0f
                }
            } else {
                settlingDuration = 0f
            }
        } else if (state == SimulationState.SETTLING) {
            if (maxVelocityObserved > 120f || maxDisplacement > 25f || droplets.isNotEmpty()) {
                state = SimulationState.ACTIVE
                settlingDuration = 0f
            } else {
                settlingDuration += dt
                // Transition to absolute SLEEP when waves have completely subsided
                if (settlingDuration > 0.45f && maxVelocityObserved < 15f && maxDisplacement < 3f) {
                    for (i in 0 until COLUMN_COUNT) {
                        nodeV[i] = 0f
                        nodeY[i] = nodeEqY[i]
                    }
                    droplets.clear()
                    sleepGravityX = gravityX
                    sleepGravityY = gravityY
                    state = SimulationState.SLEEPING
                    settlingDuration = 0f
                }
            }
        }
    }

    /**
     * Returns surface height Y at any screen horizontal coordinate X.
     */
    fun getSurfaceHeightAt(x: Float): Float {
        val w = if (screenWidth > 0) screenWidth.toFloat() else 1080f
        val clampedX = x.coerceIn(0f, w)
        val ratio = clampedX / w
        val indexF = ratio * (COLUMN_COUNT - 1)
        val i0 = indexF.toInt().coerceIn(0, COLUMN_COUNT - 1)
        val i1 = (i0 + 1).coerceIn(0, COLUMN_COUNT - 1)
        val frac = indexF - i0
        return nodeY[i0] * (1f - frac) + nodeY[i1] * frac
    }

    fun getSurfaceVelocityXAt(x: Float): Float = 0f

    fun getSurfaceVelocityYAt(x: Float): Float {
        val w = if (screenWidth > 0) screenWidth.toFloat() else 1080f
        val clampedX = x.coerceIn(0f, w)
        val ratio = clampedX / w
        val indexF = ratio * (COLUMN_COUNT - 1)
        val i0 = indexF.toInt().coerceIn(0, COLUMN_COUNT - 1)
        val i1 = (i0 + 1).coerceIn(0, COLUMN_COUNT - 1)
        val frac = indexF - i0
        return nodeV[i0] * (1f - frac) + nodeV[i1] * frac
    }

    /**
     * Samples fluid for physical interaction with Floating Sticks live wallpaper.
     */
    fun sampleFluidAt(x: Float, y: Float, sampleRadius: Float = 55f): FluidSample {
        val surfY = getSurfaceHeightAt(x)
        val isSubmerged = y >= surfY - 4f
        val depth = (y - surfY).coerceAtLeast(0f)
        val density = if (isSubmerged) (1.0f + depth / screenHeight.toFloat() * 1.5f) else 0f
        val vy = if (isSubmerged && !isAsleep) getSurfaceVelocityYAt(x) else 0f

        return FluidSample(
            density = density,
            velocityX = 0f,
            velocityY = vy,
            isSubmerged = isSubmerged
        )
    }

    /**
     * Populates OpenGL ES vertex buffer for the continuous fluid body mesh (GL_TRIANGLE_STRIP).
     *
     * Generates 2 * COLUMN_COUNT vertices:
     * For each surface column:
     *   Vertex 0 (Surface): [x, surfaceY, depth = 0.0, slope]
     *   Vertex 1 (Bottom):  [x, screenHeight, depth = 1.0, slope = 0.0]
     */
    fun populateRenderBuffer(): Int {
        synchronized(this) {
            val vertexCount = COLUMN_COUNT * 2
            val requiredCapacity = vertexCount * 4
            if (fluidMeshBuffer.capacity() < requiredCapacity) {
                fluidMeshBuffer = ByteBuffer.allocateDirect(requiredCapacity * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            }
            fluidMeshBuffer.clear()

            val bottomY = screenHeight.toFloat()
            val dx = screenWidth / (COLUMN_COUNT - 1).toFloat()

            for (i in 0 until COLUMN_COUNT) {
                val x = nodeX[i]
                val y = nodeY[i]
                val prevY = if (i > 0) nodeY[i - 1] else y
                val nextY = if (i < COLUMN_COUNT - 1) nodeY[i + 1] else y
                val slope = (nextY - prevY) / (2f * dx)

                // Top surface vertex
                fluidMeshBuffer.put(x)
                fluidMeshBuffer.put(y)
                fluidMeshBuffer.put(0.0f) // depth = 0.0 (surface)
                fluidMeshBuffer.put(slope)

                // Bottom container vertex
                fluidMeshBuffer.put(x)
                fluidMeshBuffer.put(bottomY)
                fluidMeshBuffer.put(1.0f) // depth = 1.0 (bottom)
                fluidMeshBuffer.put(0.0f)
            }

            fluidMeshBuffer.flip()
            return vertexCount
        }
    }

    fun getRenderBuffer(): FloatBuffer = fluidMeshBuffer

    /**
     * Populates OpenGL ES vertex buffer for fine spray droplets (GL_POINTS).
     * [x, y, radius, 1.0f]
     */
    fun populateDropletBuffer(): Int {
        synchronized(this) {
            val count = droplets.size
            if (count == 0) return 0

            val requiredCapacity = count * 4
            if (dropletBuffer.capacity() < requiredCapacity) {
                dropletBuffer = ByteBuffer.allocateDirect(requiredCapacity * 4 * 2)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            }
            dropletBuffer.clear()
            for (i in 0 until count) {
                val d = droplets[i]
                dropletBuffer.put(d.x)
                dropletBuffer.put(d.y)
                dropletBuffer.put(d.radius)
                dropletBuffer.put(1.0f)
            }
            dropletBuffer.flip()
            return count
        }
    }

    fun getDropletBuffer(): FloatBuffer = dropletBuffer

    fun getParticlesCount(): Int = COLUMN_COUNT + droplets.size
    fun getGravityX(): Float = gravityX
    fun getGravityY(): Float = gravityY
}
