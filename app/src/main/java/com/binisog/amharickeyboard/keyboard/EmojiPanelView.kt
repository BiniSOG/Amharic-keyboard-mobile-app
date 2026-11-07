package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.binisog.amharickeyboard.core.EmojiData
import java.io.File

/**
 * Emoji panel shown in place of the keys: category tabs (Recent, the built-in categories,
 * text emoticons and the user's own packs), a scrolling grid, and ABC / space / delete.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmoji(text: String)
        fun onSticker(file: File)
        fun onStickerLongPress(file: File)
        fun onEmojiDelete()
        fun onEmojiSpace()
        fun onEmojiClose()
    }

    /** What a tab shows. */
    class Tab(val id: String, val icon: String, val texts: List<String>, val stickers: List<File> = emptyList())

    var listener: Listener? = null
    var theme: KeyboardTheme = KeyboardTheme.LIGHT

    private val density = resources.displayMetrics.density
    private val tabsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val tabsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(tabsRow)
    }
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private val gridScroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }

    private var tabs: List<Tab> = emptyList()
    var selectedTab: String = EmojiData.SMILEYS.id
        private set

    init {
        orientation = VERTICAL
        addView(tabsScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (40 * density).toInt()))
        addView(gridScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottom, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * density).toInt()))
    }

    fun show(newTabs: List<Tab>, select: String?) {
        tabs = newTabs
        setBackgroundColor(theme.background)
        if (select != null && tabs.any { it.id == select }) selectedTab = select
        if (tabs.none { it.id == selectedTab }) selectedTab = tabs.firstOrNull { it.texts.isNotEmpty() || it.stickers.isNotEmpty() }?.id ?: tabs.first().id
        renderTabs()
        renderGrid()
        renderBottom()
    }

    private fun renderTabs() {
        tabsRow.removeAllViews()
        for (t in tabs) {
            tabsRow.addView(TextView(context).apply {
                text = t.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                val p = (12 * density).toInt()
                setPadding(p, 0, p, 0)
                setTextColor(theme.text)
                if (t.id == selectedTab) background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(theme.key)
                }
                contentDescription = t.id
                setOnClickListener { selectedTab = t.id; renderTabs(); renderGrid() }
            }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun renderGrid() {
        grid.removeAllViews()
        gridScroll.scrollTo(0, 0)
        val tab = tabs.firstOrNull { it.id == selectedTab } ?: return
        if (tab.texts.isEmpty() && tab.stickers.isEmpty()) {
            grid.addView(TextView(context).apply {
                text = if (tab.id == RECENT) "🕘  …" else "⭐  …"
                setTextColor(theme.hint)
                gravity = Gravity.CENTER
                setPadding(0, (32 * density).toInt(), 0, 0)
            })
            return
        }
        // stickers: 4 per row, pictures
        val size = (64 * density).toInt()
        for (chunk in tab.stickers.chunked(4)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (f in chunk) {
                row.addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(com.binisog.amharickeyboard.CustomEmoticons.thumbnail(f, size))
                    val p = (4 * density).toInt()
                    setPadding(p, p, p, p)
                    setOnClickListener { listener?.onSticker(f) }
                    setOnLongClickListener { listener?.onStickerLongPress(f); true }
                }, LayoutParams(0, size, 1f))
            }
            repeat(4 - chunk.size) { row.addView(View(context), LayoutParams(0, 1, 1f)) }
            grid.addView(row)
        }
        // text: emoji 8 per row, longer emoticons fewer per row
        val longText = tab.texts.any { it.codePointCount(0, it.length) > 3 }
        val perRow = if (longText) 3 else 8
        for (chunk in tab.texts.chunked(perRow)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (e in chunk) {
                row.addView(TextView(context).apply {
                    text = e
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (longText) 15f else 24f)
                    setTextColor(theme.text)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setOnClickListener { listener?.onEmoji(e) }
                }, LayoutParams(0, (46 * density).toInt(), 1f))
            }
            repeat(perRow - chunk.size) { row.addView(View(context), LayoutParams(0, 1, 1f)) }
            grid.addView(row)
        }
    }

    private fun renderBottom() {
        bottom.removeAllViews()
        fun button(label: String, weight: Float, action: () -> Unit) = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.text)
            background = GradientDrawable().apply {
                cornerRadius = 7 * density
                setColor(theme.keyFunction)
            }
            setOnClickListener { action() }
        }.also {
            val m = (3 * density).toInt()
            bottom.addView(it, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { setMargins(m, m, m, m) })
        }
        button("ABC", 1.5f) { listener?.onEmojiClose() }
        button(" ", 5f) { listener?.onEmojiSpace() }.apply {
            background = GradientDrawable().apply { cornerRadius = 7 * density; setColor(theme.key) }
        }
        button("⌫", 1.5f) { listener?.onEmojiDelete() }
    }

    companion object {
        const val RECENT = "recent"
        const val MY_PACKS = "mine"
    }
}
