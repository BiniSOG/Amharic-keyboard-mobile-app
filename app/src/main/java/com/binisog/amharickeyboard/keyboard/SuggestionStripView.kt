package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The row above the keys. Depending on state it shows:
 *  - word suggestions (typed word first, best suggestion highlighted),
 *  - the vowel forms of the last Fidel letter (Fidel layout),
 *  - clipboard history,
 *  - a small toolbar when there is nothing to suggest.
 */
class SuggestionStripView(context: Context) : FrameLayout(context) {

    enum class ItemKind { TYPED, SUGGESTION, BEST, VOWEL, CLIP, ACTION, INFO }

    data class Item(val label: String, val kind: ItemKind, val value: String = label, val tag: String = "")

    interface Listener {
        fun onStripItem(item: Item)
        fun onStripItemLongPress(item: Item): Boolean
    }

    var listener: Listener? = null
    var theme: KeyboardTheme = KeyboardTheme.LIGHT
        set(v) { field = v; setBackgroundColor(if (transparentBackground) 0 else v.strip); render() }
    /** Let a keyboard background picture show through. */
    var transparentBackground = false
        set(v) { field = v; setBackgroundColor(if (v) 0 else theme.strip) }

    private val density = resources.displayMetrics.density
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private var items: List<Item> = emptyList()

    init {
        scroll.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setBackgroundColor(theme.strip)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((44 * density).toInt(), MeasureSpec.EXACTLY))
    }

    fun show(newItems: List<Item>) {
        if (newItems == items) return
        items = newItems
        render()
        scroll.scrollTo(0, 0)
    }

    private fun render() {
        row.removeAllViews()
        val w = (width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels)
        val slots = when {
            items.any { it.kind == ItemKind.VOWEL } -> items.size.coerceAtLeast(1)
            items.any { it.kind == ItemKind.ACTION } -> items.size.coerceAtLeast(1)
            else -> 3
        }
        val minW = w / slots
        items.forEachIndexed { i, item ->
            if (i > 0 && item.kind != ItemKind.VOWEL && item.kind != ItemKind.ACTION) {
                row.addView(View(context).apply { setBackgroundColor(theme.divider) },
                    LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (20 * density).toInt()))
            }
            val chip = item.kind == ItemKind.VOWEL || item.kind == ItemKind.CLIP
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val m = (3 * density).toInt()
            if (chip) lp.setMargins(m, 2 * m, m, 2 * m)
            row.addView(makeView(item, if (chip) minW - 2 * m else minW), lp)
        }
    }

    private fun makeView(item: Item, minWidth: Int): TextView = TextView(context).apply {
        text = when (item.kind) {
            ItemKind.TYPED -> "“${item.label}”"
            else -> item.label
        }
        gravity = Gravity.CENTER
        minimumWidth = minWidth
        maxWidth = (resources.displayMetrics.widthPixels * 0.7f).toInt()
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        val pad = (10 * density).toInt()
        setPadding(pad, 0, pad, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, when (item.kind) {
            ItemKind.VOWEL -> 22f
            ItemKind.ACTION -> 19f
            ItemKind.CLIP, ItemKind.INFO -> 14f
            else -> 18f
        })
        setTextColor(if (item.kind == ItemKind.BEST) theme.stripHighlight else if (item.kind == ItemKind.INFO) theme.hint else theme.stripText)
        typeface = if (item.kind == ItemKind.BEST) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (item.kind == ItemKind.VOWEL || item.kind == ItemKind.CLIP) {
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(theme.key)
            }
        }
        isClickable = item.kind != ItemKind.INFO
        setOnClickListener { listener?.onStripItem(item) }
        setOnLongClickListener { listener?.onStripItemLongPress(item) ?: false }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) post { render() }
    }
}
