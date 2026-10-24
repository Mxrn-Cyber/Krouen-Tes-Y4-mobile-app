package com.kroeungtes.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.View
import com.kroeungtes.app.R

/**
 * White bottom bar with a smooth curved dip ("cradle") for the green + button.
 * The dip can sit under any tab: change [notchCenterX] (MainActivity animates it).
 *
 *        ___________        ___________
 *       |           \______/           |   <- barTop
 *       |                              |
 */
class CurvedBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    /** Space above the bar (for the part of the + button that sticks out). */
    var barTop = 28f * density
        set(value) { field = value; requestLayout(); invalidate() }

    /** Size of the dip; it goes down 1.25 × this. */
    var notchRadius = 34f * density
        set(value) { field = value; invalidate() }

    /** X position of the dip's centre. Negative = middle of the bar. */
    var notchCenterX = -1f
        set(value) { field = value; invalidate() }

    val notchDepth: Float get() = notchRadius * 1.25f

    /** Height of the phone's gesture / button bar. The white bar grows down behind it (edge-to-edge). */
    var bottomInset = 0
        set(value) { if (field != value) { field = value; requestLayout(); invalidate() } }

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f * density, 0f, -1.5f * density, 0x26000000)
    }

    init {
        // Shadows on shapes need software drawing before Android 9
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** Only as tall as the space above the bar + the bar itself + the gesture bar (not the whole screen). */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = (barTop + resources.getDimension(R.dimen.bottom_bar_height)).toInt() + bottomInset
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(wanted, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = if (notchCenterX < 0f) w / 2f else notchCenterX
        val r = notchRadius
        val top = barTop
        val depth = notchDepth
        val startX = cx - r * 2f - r / 3f
        val endX = cx + r * 2f + r / 3f

        path.reset()
        path.moveTo(0f, top)
        path.lineTo(startX, top)
        path.cubicTo(startX + r + r / 4f, top, cx - r, top + depth, cx, top + depth)
        path.cubicTo(cx + r, top + depth, endX - (r + r / 4f), top, endX, top)
        path.lineTo(w, top)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)
    }
}
