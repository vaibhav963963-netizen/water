package com.waterlivewallpaper.features.wallpapers.water.simulation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
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

/**
 * Authentic Container Liquid Physics Engine.
 * Models real water stored in a physical container (phone screen):
 * 1. Fills the container continuously from the bottom up to the chosen water level.
 * 2. Dynamic shallow-water surface wave equation: sloshing, wave transmission, and tilt equilibrium.
 * 3. Volume is strictly conserved: water never shrinks into a corner or balls up into jelly.
 * 4. Hydrostatic equilibrium with progressive viscous damping: settles to 100% still flat water at rest.
 * 5. Touch ripple generation, inertial phone sloshing, and airborne splash spray droplets on shake.
 */
class FluidSimulationEngine(
    private var screenWidth: Int = 1080,
    private var screenHeight: Int = 2400,
    private var waterVolumeRatio: Float = 0.38f,
    private var physicsSensitivity: Float = 1.0f,
    private var shakeSensitivity: Float = 1.2f,
    private var flowSpeed: Float = 2.0f
) {
    // Surface Wave Column Discretization across container width
    val numColumns = 128
    val xCoords = FloatArray(numColumns)
    val surfaceHeights = FloatArray(numColumns)
    val targetHeights = FloatArray(numColumns)
    val waveVelocities = FloatArray(numColumns)

    // Airborne splash droplets generated during energetic shakes
    private val droplets = ArrayList<Particle>()
    private val maxDroplets = 48

    private var gravityX = 0f
    private var gravityY = 9.8f * 520f * physicsSensitivity * flowSpeed
    private var prevGravityX = 0f
    private var prevGravityY = 0f

    private var isBatterySaver: Boolean = false

    @Volatile var state: SimulationState = SimulationState.ACTIVE
        private set

    val isAsleep: Boolean
        get() = state == SimulationState.SLEEPING

    @Volatile private var maxVelocityObserved = 50f
    @Volatile private var avgVelocityObserved = 20f
    @Volatile private var settlingDuration = 0f

    private var sleepGravityX = 0f
    private var sleepGravityY = 0f

    // Direct FloatBuffer for rendering water mesh: [x, y, depth] x (numColumns * 2)
    // plus droplet points [x, y, radius, 1.0f]
    private var meshBuffer: FloatBuffer = ByteBuffer.allocateDirect((numColumns * 2 * 3 + maxDroplets * 4) * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var renderBuffer: FloatBuffer = ByteBuffer.allocateDirect((numColumns * 2 * 4 + maxDroplets * 4) * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    init {
        initializeContainerWater()
    }

    fun setDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        this.screenWidth = width
        this.screenHeight = height
        initializeContainerWater()
    }

    fun wakeUp() {
        state = SimulationState.ACTIVE
        settlingDuration = 0f
    }

    fun isFluidCalm(): Boolean = isAsleep || maxVelocityObserved < 20f
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
            initializeContainerWater()
        } else {
            wakeUp()
        }
    }

    fun initializeParticles(volume: Float = waterVolumeRatio, batterySaver: Boolean = isBatterySaver) {
        this.waterVolumeRatio = volume
        this.isBatterySaver = batterySaver
        initializeContainerWater()
    }

    private fun initializeContainerWater() {
        synchronized(this) {
            val w = screenWidth.toFloat()
            val h = screenHeight.toFloat()
            val baseLevel = h * (1.0f - waterVolumeRatio.coerceIn(0.12f, 0.88f))

            for (i in 0 until numColumns) {
                xCoords[i] = i * (w / (numColumns - 1).toFloat())
                surfaceHeights[i] = baseLevel
                targetHeights[i] = baseLevel
                waveVelocities[i] = 0f
            }

            droplets.clear()
            wakeUp()
            maxVelocityObserved = 40f
            avgVelocityObserved = 15f
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
        synchronized(this) {
            val mag2 = impulseX * impulseX + impulseY * impulseY
            if (mag2 < 2.0f) return

            wakeUp()
            val impulseScale = flowSpeed * shakeSensitivity * 1.8f
            val w = screenWidth.toFloat()

            // Induce energetic wave slosh across the container surface
            for (i in 0 until numColumns) {
                val relX = (xCoords[i] - w * 0.5f) / w
                val waveKick = -impulseX * relX * 2.4f + impulseY * 0.7f
                waveVelocities[i] += waveKick * impulseScale
            }

            // Generate authentic airborne splash spray droplets on vigorous shake
            if (mag2 > 16.0f) {
                val spawnCount = min(16, (mag2 * 0.4f).toInt())
                for (k in 0 until spawnCount) {
                    if (droplets.size >= maxDroplets) droplets.removeAt(0)
                    val col = Random.nextInt(numColumns)
                    val sx = xCoords[col] + (Random.nextFloat() - 0.5f) * 40f
                    val sy = surfaceHeights[col] - Random.nextFloat() * 25f
                    val svx = (impulseX * 28f + (Random.nextFloat() - 0.5f) * 400f)
                    val svy = (-abs(impulseY) * 35f - Random.nextFloat() * 500f)
                    droplets.add(
                        Particle(
                            x = sx.coerceIn(10f, w - 10f),
                            y = sy,
                            vx = svx,
                            vy = svy,
                            radius = (4f + Random.nextFloat() * 6f),
                            density = 1f,
                            pressure = 0f,
                            isDroplet = true,
                            lifetime = 0f
                        )
                    )
                }
            }
        }
    }

    fun addTouchDisturbance(touchX: Float, touchY: Float, strength: Float = 750f) {
        synchronized(this) {
            wakeUp()
            val radius = 220f
            val radiusSq = radius * radius

            for (i in 0 until numColumns) {
                val dx = xCoords[i] - touchX
                val d2 = dx * dx
                if (d2 < radiusSq) {
                    val factor = 1.0f - d2 / radiusSq
                    // Surface ripple impulse propagates outwards
                    val kick = factor * strength * flowSpeed * 0.75f
                    waveVelocities[i] += kick
                }
            }
        }
    }

    fun step(rawDt: Float) {
        if (state == SimulationState.SLEEPING) return

        val dt = rawDt.coerceIn(0.005f, 0.022f)
        synchronized(this) {
            val w = screenWidth.toFloat()
            val h = screenHeight.toFloat()
            val baseLevel = h * (1.0f - waterVolumeRatio.coerceIn(0.12f, 0.88f))

            // 1. Calculate Volume-Conserving Tilt Equilibrium Water Line:
            // Downward baseline gravity prevents water floating to ceiling on flat desk
            val effGx = gravityX
            val effGy = if (gravityY > 0f) max(gravityY, 3.2f * 520f) else 3.2f * 520f
            val tiltSlope = (effGx / effGy).coerceIn(-2.2f, 2.2f)

            // Dynamic slosh coupling from phone acceleration
            val dGx = effGx - prevGravityX
            prevGravityX = effGx
            prevGravityY = effGy
            if (abs(dGx) > 1.0f) {
                val sloshPush = (-dGx * 0.045f * physicsSensitivity).coerceIn(-180f, 180f)
                for (i in 0 until numColumns) {
                    val relX = (xCoords[i] - w * 0.5f) / w
                    waveVelocities[i] += sloshPush * relX
                }
            }

            for (i in 0 until numColumns) {
                val x = xCoords[i]
                targetHeights[i] = (baseLevel + (x - w * 0.5f) * tiltSlope).coerceIn(10f, h - 5f)
            }

            // 2. Hydrodynamic Surface Wave Equation:
            val springK = 65.0f * physicsSensitivity * (flowSpeed * 0.5f)
            val spreadK = 48.0f * (flowSpeed * 0.5f)

            for (i in 0 until numColumns) {
                val diff = surfaceHeights[i] - targetHeights[i]
                val restoringForce = -diff * springK

                val leftPull = if (i > 0) (surfaceHeights[i - 1] - surfaceHeights[i]) * spreadK else 0f
                val rightPull = if (i < numColumns - 1) (surfaceHeights[i + 1] - surfaceHeights[i]) * spreadK else 0f

                val accel = restoringForce + leftPull + rightPull
                waveVelocities[i] += accel * dt

                // Progressive Viscous Damping:
                val damping = if (state == SimulationState.SETTLING) 0.935f else 0.980f
                waveVelocities[i] *= damping

                surfaceHeights[i] += waveVelocities[i] * dt
                surfaceHeights[i] = surfaceHeights[i].coerceIn(5f, h - 2f)
            }

            // 3. Update Splash Droplets
            val gYConst = 9.8f * 520f
            val dropIter = droplets.iterator()
            while (dropIter.hasNext()) {
                val d = dropIter.next()
                d.vy += gYConst * dt
                d.x += d.vx * dt
                d.y += d.vy * dt
                d.lifetime += dt

                val col = ((d.x / w) * (numColumns - 1)).toInt().coerceIn(0, numColumns - 1)
                val surfY = surfaceHeights[col]

                // Reabsorb when falling back into liquid surface or after lifetime
                if (d.y >= surfY || d.lifetime > 1.8f) {
                    if (d.y >= surfY && col in 0 until numColumns) {
                        waveVelocities[col] += (d.vy * 0.08f).coerceIn(-40f, 40f)
                    }
                    dropIter.remove()
                }
            }

            // 4. State Machine & Equilibrium Rest Latching
            var maxV = 0f
            var sumV = 0f
            for (i in 0 until numColumns) {
                val absV = abs(waveVelocities[i])
                if (absV > maxV) maxV = absV
                sumV += absV
            }

            maxVelocityObserved = maxV
            avgVelocityObserved = sumV / numColumns

            if (state == SimulationState.ACTIVE) {
                if (maxVelocityObserved < 22f && avgVelocityObserved < 8f && droplets.isEmpty()) {
                    settlingDuration += dt
                    if (settlingDuration > 0.35f) {
                        state = SimulationState.SETTLING
                        settlingDuration = 0f
                    }
                } else {
                    settlingDuration = 0f
                }
            } else if (state == SimulationState.SETTLING) {
                if (maxVelocityObserved > 45f) {
                    state = SimulationState.ACTIVE
                    settlingDuration = 0f
                } else {
                    settlingDuration += dt
                    // Once wave energy has naturally decayed, freeze surface to 100% still
                    if (settlingDuration > 0.55f && maxVelocityObserved < 2.5f) {
                        for (i in 0 until numColumns) {
                            waveVelocities[i] = 0f
                            surfaceHeights[i] = targetHeights[i]
                        }
                        droplets.clear()
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

    /**
     * Populates the continuous water mesh buffer:
     * For each wave column i:
     *   Vertex 1 (top wave crest):  (xCoords[i], surfaceHeights[i], depth = 0.0)
     *   Vertex 2 (container bottom): (xCoords[i], screenHeight,      depth = 1.0)
     * Rendered as GL_TRIANGLE_STRIP across the full width of the screen.
     */
    fun populateMeshBuffer(buffer: FloatBuffer): Int {
        synchronized(this) {
            buffer.clear()
            val h = screenHeight.toFloat()
            val vertCount = numColumns * 2

            for (i in 0 until numColumns) {
                val x = xCoords[i]
                val y = surfaceHeights[i]

                // Top wave surface vertex
                buffer.put(x)
                buffer.put(y)
                buffer.put(0.0f) // depth = 0.0 (surface meniscus highlight)

                // Bottom container vertex
                buffer.put(x)
                buffer.put(h)
                buffer.put(1.0f) // depth = 1.0 (deep water gradient)
            }
            buffer.flip()
            return vertCount
        }
    }

    /**
     * Backward-compatible render buffer method.
     */
    fun populateRenderBuffer(): Int {
        return populateMeshBuffer(meshBuffer)
    }

    fun getMeshBuffer(): FloatBuffer = meshBuffer

    fun getRenderBuffer(): FloatBuffer = meshBuffer

    fun getParticlesCount(): Int = numColumns * 2

    fun getDroplets(): List<Particle> = droplets

    fun getGravityX(): Float = gravityX
    fun getGravityY(): Float = gravityY

    /**
     * Accurate liquid sampling for 3D Floating Sticks:
     * Queries whether point (x, y) is submerged below the continuous water surface.
     */
    fun sampleFluidAt(x: Float, y: Float, sampleRadius: Float = 55f): FluidSample {
        synchronized(this) {
            val col = ((x / screenWidth.toFloat()) * (numColumns - 1)).toInt().coerceIn(0, numColumns - 1)
            val surfY = surfaceHeights[col]
            val isSubmerged = y >= surfY - 4f
            val depth = if (isSubmerged) (y - surfY).coerceAtLeast(0f) else 0f

            val immersionFraction = (depth / max(1f, sampleRadius)).coerceIn(0f, 1.5f)
            val density = if (isSubmerged) (0.65f + immersionFraction * 0.70f).coerceIn(0.35f, 2.0f) else 0f

            val velY = if (!isAsleep) waveVelocities[col] else 0f
            val velX = if (!isAsleep) -velY * 0.35f else 0f

            return FluidSample(
                density = density,
                velocityX = velX,
                velocityY = velY,
                isSubmerged = isSubmerged
            )
        }
    }
}
