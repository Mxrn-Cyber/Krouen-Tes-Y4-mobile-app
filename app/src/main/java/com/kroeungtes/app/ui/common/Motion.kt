package com.kroeungtes.app.ui.common

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.ActivityOptionsCompat
import java.lang.ref.WeakReference
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

    /** Extra holding the shared photo's transition name (Recipe page reads it). */
    const val EXTRA_SHARED_NAME = "com.kroeungtes.app.sharedImage"

    /**
     * Open a recipe. If a card photo was just tapped, the photo "flies" from the card into the
     * big header of the Recipe page (shared-element transition); otherwise a normal slide.
     */
    fun openRecipe(activity: Activity, intent: Intent) {
        val image = SharedImage.take()
        if (image == null) {
            open(activity, intent)
            return
        }
        val name = "recipe_image_" + System.identityHashCode(image)
        image.transitionName = name
        intent.putExtra(EXTRA_SHARED_NAME, name)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, image, name)
        activity.startActivity(intent, options.toBundle())
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
                v.animate().scaleX(scale).scaleY(scale).setStartDelay(0).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160)
                    .setInterpolator(OvershootInterpolator(2f)).start()
        }
        false
    }
}

/** Quick "pop" — used when a recipe is saved or a step is ticked. */
fun View.pop() {
    // No cancel(): only scale is animated here, so a running spin (rotation) keeps going
    scaleX = 0.6f
    scaleY = 0.6f
    animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320)
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

/** Remembers the card photo that was just tapped, so [Motion.openRecipe] can animate it. */
object SharedImage {
    private var ref: WeakReference<View>? = null
    private var at = 0L

    fun mark(view: View) {
        ref = WeakReference(view)
        at = SystemClock.uptimeMillis()
    }

    /** The photo tapped in the last second, if it is still on screen. */
    fun take(): View? {
        val view = ref?.get()
        val fresh = SystemClock.uptimeMillis() - at < 1000
        ref = null
        return if (view != null && fresh && view.isAttachedToWindow && view.isShown) view else null
    }
}

/** Fades a view in while it rises a little (screens and cards appearing). */
fun View.riseIn(delay: Long = 0, distanceDp: Float = 24f, duration: Long = 420) {
    alpha = 0f
    translationY = distanceDp * resources.displayMetrics.density
    animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(duration)
        .setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
}

/** Shows a Snackbar above the bottom bar (not over the green + button). */
fun com.google.android.material.snackbar.Snackbar.aboveBar(): com.google.android.material.snackbar.Snackbar {
    view.rootView.findViewById<View>(R.id.bottom_bar)?.let { anchorView = it }
    return this
}

/** Turns this view round and round (slow decoration). Cancel the returned animator in onDestroy. */
fun View.spinForever(durationMs: Long = 30_000, clockwise: Boolean = true): ObjectAnimator =
    ObjectAnimator.ofFloat(this, View.ROTATION, 0f, if (clockwise) 360f else -360f).apply {
        duration = durationMs
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        start()
    }
