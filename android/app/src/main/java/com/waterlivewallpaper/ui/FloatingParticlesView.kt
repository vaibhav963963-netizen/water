package com.waterlivewallpaper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random

/**
 * Lightweight, battery-friendly ambient floating water droplets and micro-particles.
 * Automatically halts when detached or invisible.
 */
class FloatingParticlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedY: Float,
        var alpha: Int,
        var waveOffset: Float,
        var waveSpeed: Float
    )

    private val random = Random()
    private val particles = ArrayList<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }
    private var isAnimating = false
    private val particleCount = 18

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        particles.clear()
        if (w > 0 && h > 0) {
            for (i in 0 until particleCount) {
                particles.add(
                    Particle(
                        x = random.nextFloat() * w,
                        y = random.nextFloat() * h,
                        radius = 2.0f + random.nextFloat() * 4.5f,
                        speedY = 0.4f + random.nextFloat() * 0.9f,
                        alpha = 30 + random.nextInt(90),
                        waveOffset = random.nextFloat() * 6.28f,
                        waveSpeed = 0.02f + random.nextFloat() * 0.03f
                    )
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        for (p in particles) {
            paint.alpha = p.alpha
            val waveX = p.x + (Math.sin(p.waveOffset.toDouble()) * 12.0).toFloat()
            canvas.drawCircle(waveX, p.y, p.radius, paint)

            // Update particle state
            p.y -= p.speedY
            p.waveOffset += p.waveSpeed
            if (p.y + p.radius < 0) {
                p.y = height + p.radius
                p.x = random.nextFloat() * width
            }
        }

        if (isAnimating && visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAnimating = true
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAnimating = false
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        isAnimating = (visibility == VISIBLE && isAttachedToWindow)
        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }
}
