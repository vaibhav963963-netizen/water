package com.waterlivewallpaper.core.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object AnimationUtils {

    /**
     * Applies a tactile 3D spring-press animation to any View with subtle haptic click.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachTactilePress(view: View, onClick: (() -> Unit)? = null) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .alpha(0.88f)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(180)
                        .setInterpolator(OvershootInterpolator(2.0f))
                        .withEndAction {
                            onClick?.invoke()
                        }
                        .start()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(150)
                        .start()
                }
            }
            true
        }
    }

    /**
     * Subtle pulse glow for live badges (e.g. 60 FPS, Active status).
     */
    fun startPulseGlow(view: View, durationMs: Long = 1800) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.08f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.08f, 1.0f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 1.0f, 0.85f)

        ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha).apply {
            duration = durationMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Smoothly cross-fades out viewA and fades in viewB.
     */
    fun crossFade(viewToHide: View, viewToShow: View, durationMs: Long = 220) {
        viewToHide.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .withEndAction {
                viewToHide.visibility = View.GONE
                viewToShow.visibility = View.VISIBLE
                viewToShow.alpha = 0f
                viewToShow.animate()
                    .alpha(1f)
                    .setDuration(durationMs)
                    .start()
            }
            .start()
    }

    /**
     * Smoothly slides up a panel with overshoot damping.
     */
    fun slideUp(view: View, durationMs: Long = 320) {
        view.visibility = View.VISIBLE
        view.translationY = 200f
        view.alpha = 0f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    /**
     * Smoothly slides down and hides a panel.
     */
    fun slideDown(view: View, durationMs: Long = 240, onComplete: (() -> Unit)? = null) {
        view.animate()
            .translationY(200f)
            .alpha(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                onComplete?.invoke()
            }
            .start()
    }
}
