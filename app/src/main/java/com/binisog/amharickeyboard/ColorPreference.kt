package com.binisog.amharickeyboard

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference

/**
 * A colour setting for the custom keyboard theme: a palette of swatches plus a hex field.
 * The colour is stored as an Int (ARGB, always opaque).
 */
class ColorPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var color: Int = Color.GRAY

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = parse(a.getString(index)) ?: Color.GRAY

    override fun onSetInitialValue(defaultValue: Any?) {
        color = getPersistedInt((defaultValue as? Int) ?: color)
        updateSummary()
    }

    private fun updateSummary() {
        val text = SpannableString("■■■  " + hex(color))
        text.setSpan(ForegroundColorSpan(color), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        summary = text
    }

    override fun onClick() {
        val ctx = context
        val dp = ctx.resources.displayMetrics.density
        var chosen = color

        val hexField = EditText(ctx).apply {
            setText(hex(color))
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(7))
        }
        val grid = GridLayout(ctx).apply { columnCount = 6 }
        val size = (40 * dp).toInt()
        val margin = (5 * dp).toInt()
        for (c in PALETTE) {
            grid.addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke((1 * dp).toInt(), Color.GRAY)
                }
                setOnClickListener { chosen = c; hexField.setText(hex(c)) }
            }, GridLayout.LayoutParams().apply { width = size; height = size; setMargins(margin, margin, margin, margin) })
        }
        val pad = (20 * dp).toInt()
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad / 2, pad, 0)
            addView(grid)
            addView(hexField, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val c = parse(hexField.text.toString()) ?: chosen
                if (callChangeListener(c)) {
                    color = c
                    persistInt(c)
                    updateSummary()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private val PALETTE = listOf(
            "#000000", "#212121", "#424242", "#757575", "#BDBDBD", "#FFFFFF",
            "#0D1B2A", "#1A237E", "#1565C0", "#0288D1", "#00838F", "#4DD0E1",
            "#0B3D20", "#078930", "#43A047", "#9CCC65", "#FCDD09", "#FFB300",
            "#E65100", "#DA121A", "#C2185B", "#F48FB1", "#6A1B9A", "#B39DDB",
        ).map { Color.parseColor(it) }

        fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

        fun parse(s: String?): Int? {
            val t = s?.trim()?.removePrefix("#") ?: return null
            if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return (0xFF000000.toInt() or t.toInt(16))
        }
    }
}
