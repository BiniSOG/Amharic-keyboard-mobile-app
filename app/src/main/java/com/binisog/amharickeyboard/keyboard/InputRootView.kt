package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.WindowInsets
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * The keyboard's input view: suggestion strip and keys stacked vertically, with the
 * [PopupOverlay] drawn on top of both.
 *
 * Its height is exactly strip + keys. A plain FrameLayout with a MATCH_PARENT overlay
 * child would size itself to the *whole screen* (the IME window offers AT_MOST the
 * screen height), so the keyboard would cover the app it is typing into.
 */
class InputRootView(context: Context) : FrameLayout(context) {

    val strip = SuggestionStripView(context)
    val overlay = PopupOverlay(context)
    val keyboard = KeyboardView(context).also { it.overlay = overlay }
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        column.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(keyboard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(overlay, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Space kept free at the bottom for the navigation bar when the window is edge-to-edge. */
    private var bottomInset = 0

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        }
        // A navigation bar is never taller than ~100dp; ignore nonsense values.
        val clamped = bottom.coerceIn(0, (100 * resources.displayMetrics.density).toInt())
        if (clamped != bottomInset) { bottomInset = clamped; requestLayout() }
        return insets
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxH = (MeasureSpec.getSize(heightMeasureSpec) - bottomInset).coerceAtLeast(0)
        val hSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
        }
        column.measure(widthMeasureSpec, hSpec)
        val w = column.measuredWidth
        val h = column.measuredHeight
        overlay.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        // Normally we are exactly strip + keys (+ nav bar). If the window forces a taller,
        // exact height on us, accept it: onLayout then pins the keyboard to the bottom.
        val total = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            maxOf(MeasureSpec.getSize(heightMeasureSpec), h + bottomInset)
        } else {
            h + bottomInset
        }
        setMeasuredDimension(w, total)
    }

    /** Top of the visible keyboard (suggestion strip) inside this view. */
    val keyboardTop: Int get() = column.top

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // Always sit at the bottom, above the navigation bar; anything above stays transparent.
        val colTop = (bottom - top - bottomInset - column.measuredHeight).coerceAtLeast(0)
        column.layout(0, colTop, column.measuredWidth, colTop + column.measuredHeight)
        overlay.layout(0, colTop, overlay.measuredWidth, colTop + overlay.measuredHeight)
    }

    private val bgPaint = Paint()

    init { setWillNotDraw(false) }

    /** Background for the keyboard area and the navigation-bar strip below it (not above). */
    fun setKeyboardBackground(color: Int) { bgPaint.color = color; invalidate() }

    override fun onDraw(canvas: Canvas) {
        if (bgPaint.color != 0) canvas.drawRect(0f, column.top.toFloat(), width.toFloat(), height.toFloat(), bgPaint)
    }

    /** For tests and debugging: the views in drawing order. */
    internal val layers: List<View> get() = listOf(column, overlay)
}
