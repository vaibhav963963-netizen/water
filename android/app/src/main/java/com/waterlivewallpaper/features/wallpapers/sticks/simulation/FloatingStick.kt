package com.waterlivewallpaper.features.wallpapers.sticks.simulation

import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D Rigid body representation of a floating wooden stick / twig in the fluid wallpaper.
 *
 * Physical modeling:
 * - Position (x, y) at center of mass
 * - Linear velocity (vx, vy)
 * - Orientation angle theta (radians)
 * - Angular velocity omega (rad/s)
 * - Length L, thickness radius R
 * - Wood density (relative to water, typically 0.60 to 0.70)
 * - Submerged depth / ratio
 */
data class FloatingStick(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var angle: Float = 0f,
    var angularVelocity: Float = 0f,
    var length: Float = 220f,
    var radius: Float = 16f,
    val woodDensity: Float = 0.65f, // ~0.65 g/cm³ relative to water (1.0)
    val textureIndex: Int = 1,      // 1..6
    var submergedFraction: Float = 0f
) {
    // Physical mass = volume proportional to L * 2R * density
    val mass: Float = (length * radius * 2f * woodDensity * 0.05f).coerceAtLeast(1f)

    // Moment of inertia for thin rod rotating about center: I = (1/12) * m * (L^2 + (2R)^2)
    val momentOfInertia: Float = (1f / 12f) * mass * (length * length + 4f * radius * radius).coerceAtLeast(10f)

    /**
     * Compute the two endpoint positions of the stick capsule.
     */
    fun getEndpoints(): Pair<FloatArray, FloatArray> {
        val halfL = length * 0.5f
        val cosA = cos(angle)
        val sinA = sin(angle)
        val p1 = floatArrayOf(x - halfL * cosA, y - halfL * sinA)
        val p2 = floatArrayOf(x + halfL * cosA, y + halfL * sinA)
        return Pair(p1, p2)
    }

    /**
     * Get sample points along the length of the stick for numerical fluid coupling.
     * Returns array of [px, py, rx, ry] where (px, py) is world coord and (rx, ry) is relative to CM.
     */
    fun getSamplePoints(numPoints: Int = 5): List<FloatArray> {
        val halfL = length * 0.5f
        val cosA = cos(angle)
        val sinA = sin(angle)
        val step = (length) / (numPoints - 1).coerceAtLeast(1)
        val points = ArrayList<FloatArray>(numPoints)

        for (i in 0 until numPoints) {
            val offset = -halfL + i * step
            val rx = offset * cosA
            val ry = offset * sinA
            points.add(floatArrayOf(x + rx, y + ry, rx, ry))
        }
        return points
    }
}
