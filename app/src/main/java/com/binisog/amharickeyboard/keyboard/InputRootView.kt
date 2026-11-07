package com.binisog.amharickeyboard.keyboard

import android.content.Context
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
        if (bottom != bottomInset) { bottomInset = bottom; requestLayout() }
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
        setMeasuredDimension(w, h + bottomInset)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        column.layout(0, 0, column.measuredWidth, column.measuredHeight)
        overlay.layout(0, 0, overlay.measuredWidth, overlay.measuredHeight)
    }

    /** Colours the navigation-bar strip below the keys to match the keyboard. */
    fun setKeyboardBackground(color: Int) = setBackgroundColor(color)

    /** For tests and debugging: the views in drawing order. */
    internal val layers: List<View> get() = listOf(column, overlay)
}
