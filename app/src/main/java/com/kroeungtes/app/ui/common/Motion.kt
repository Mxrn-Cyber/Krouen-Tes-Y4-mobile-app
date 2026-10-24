package com.kroeungtes.app.ui.common

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.kroeungtes.app.R

/**
 * Small animation helpers used across the app so every screen "feels" the same.
 */
object Motion {

    /** Open a screen with a slide-in from the right. */
    fun open(activity: Activity, intent: Intent) {
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /** Open a screen sliding up from the bottom (used for Create Recipe). */
    fun openFromBottom(activity: Activity, intent: Intent) {
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.slide_up_in, R.anim.stay)
    }

    /** Call right after finish() so closing slides back to the right. */
    fun closeToRight(activity: Activity) {
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    fun closeToBottom(activity: Activity) {
        activity.overridePendingTransition(R.anim.stay, R.anim.slide_down_out)
    }
}

/** Shrinks a view a little while it is pressed (cards, buttons). Clicks still work. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressScale(scale: Float = 0.96f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                    .setInterpolator(OvershootInterpolator(2f)).start()
        }
        false
    }
}

/** Quick "pop" — used when a recipe is saved or a step is ticked. */
fun View.pop() {
    animate().cancel()
    scaleX = 0.6f
    scaleY = 0.6f
    animate().scaleX(1f).scaleY(1f).setDuration(320)
        .setInterpolator(OvershootInterpolator(3f)).start()
}

/** Soft pulsing for skeleton (loading) placeholders. Returns the animator so it can be stopped. */
fun View.startSkeletonPulse(): ValueAnimator =
    ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.45f).apply {
        duration = 700
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        start()
    }

/** Fade a view in/out instead of popping its visibility. */
fun View.fadeVisible(show: Boolean, duration: Long = 220) {
    if (show) {
        if (visibility == View.VISIBLE && alpha == 1f) return
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(duration).setListener(null).start()
    } else {
        if (visibility != View.VISIBLE) return
        animate().alpha(0f).setDuration(duration).withEndAction { visibility = View.GONE }.start()
    }
}
