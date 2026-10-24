package com.kroeungtes.app.ui.common

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * Android 15+ draws every app "edge to edge" (behind the status bar and the gesture bar),
 * and Android 16 (API 36) no longer lets apps turn that off.
 * These helpers make the bars see-through and keep our content out from under them.
 */
object EdgeToEdge {

    /** Grey used behind the 3-button bar on old phones that can't show dark buttons (before Android 8). */
    private val OLD_NAV_SCRIM = 0x801B1B1B.toInt()

    /** See-through bars with dark icons — for our white / light screens. Call before setContentView. */
    fun light(activity: ComponentActivity) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, OLD_NAV_SCRIM)
        )
    }
}

private val barTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

/**
 * Adds the status bar / gesture bar (and the keyboard, when [ime] is true) to this view's own
 * padding from the XML. Use it on a screen's root view.
 */
fun View.padForSystemBars(top: Boolean = true, bottom: Boolean = true, ime: Boolean = false) {
    val left0 = paddingLeft
    val top0 = paddingTop
    val right0 = paddingRight
    val bottom0 = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(barTypes)
        val keyboard = if (ime) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
        view.setPadding(
            left0 + bars.left,
            top0 + if (top) bars.top else 0,
            right0 + bars.right,
            bottom0 + if (bottom) maxOf(bars.bottom, keyboard) else 0
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Same idea, but as margins — for a scroll view that sits on top of a full-screen
 * background photo (the photo keeps going under the bars, the form does not).
 */
fun View.marginForSystemBars(ime: Boolean = false) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(barTypes)
        val keyboard = if (ime) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = bars.left
            topMargin = bars.top
            rightMargin = bars.right
            bottomMargin = maxOf(bars.bottom, keyboard)
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
