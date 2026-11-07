package com.binisog.amharickeyboard.keyboard

import android.content.Context
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxH = MeasureSpec.getSize(heightMeasureSpec)
        val hSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
        }
        column.measure(widthMeasureSpec, hSpec)
        val w = column.measuredWidth
        val h = column.measuredHeight
        overlay.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        column.layout(0, 0, column.measuredWidth, column.measuredHeight)
        overlay.layout(0, 0, overlay.measuredWidth, overlay.measuredHeight)
    }

    /** For tests and debugging: the views in drawing order. */
    internal val layers: List<View> get() = listOf(column, overlay)
}
