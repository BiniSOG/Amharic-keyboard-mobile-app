package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * In-keyboard quick settings: a header and a grid of tiles shown in place of the keys.
 * Each tile has an icon, a name and its current state; active tiles use the accent colour.
 */
class QuickSettingsView(context: Context) : LinearLayout(context) {

    data class Tile(
        val id: String,
        val icon: String,
        val title: String,
        val state: String? = null,
        val active: Boolean = false,
        /** Optional colour swatch (theme picker). */
        val swatch: KeyboardTheme? = null,
    )

    var onTile: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var theme: KeyboardTheme = KeyboardTheme.LIGHT

    private val density = resources.displayMetrics.density
    private val header = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val titleView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val closeView = TextView(context).apply {
        text = "⌨  ✕"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        val p = (10 * density).toInt()
        setPadding(p * 2, p, p * 2, p)
        setOnClickListener { onClose?.invoke() }
    }
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }

    init {
        orientation = VERTICAL
        val p = (8 * density).toInt()
        header.setPadding(p * 2, 0, 0, 0)
        header.addView(titleView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeView)
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setPadding(p / 2, 0, p / 2, p / 2)
    }

    fun show(title: String, tiles: List<Tile>, columns: Int = 3) {
        setBackgroundColor(theme.background)
        titleView.text = title
        titleView.setTextColor(theme.text)
        closeView.setTextColor(theme.text)
        grid.removeAllViews()
        val m = (3 * density).toInt()
        for (chunk in tiles.chunked(columns)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (t in chunk) {
                row.addView(tileView(t), LayoutParams(0, (64 * density).toInt(), 1f).apply { setMargins(m, m, m, m) })
            }
            repeat(columns - chunk.size) { row.addView(android.view.View(context), LayoutParams(0, 1, 1f)) }
            grid.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        scroll.scrollTo(0, 0)
    }

    private fun tileView(t: Tile): TextView = TextView(context).apply {
        val palette = t.swatch
        val bg = palette?.key ?: if (t.active) theme.accent else theme.key
        val fg = palette?.text ?: if (t.active) theme.onAccent else theme.text
        background = GradientDrawable().apply {
            cornerRadius = 10 * density
            setColor(palette?.background ?: bg)
            if (palette != null) setStroke((4 * density).toInt(), if (t.active) theme.accent else palette.key)
        }
        val name = listOf(t.icon, t.title).filter { it.isNotEmpty() }.joinToString("  ")
        text = if (t.state != null) "$name\n${t.state}" else name
        setTextColor(fg)
        if (palette != null) setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        val p = (4 * density).toInt()
        setPadding(p, p, p, p)
        setOnClickListener { onTile?.invoke(t.id) }
    }
}
