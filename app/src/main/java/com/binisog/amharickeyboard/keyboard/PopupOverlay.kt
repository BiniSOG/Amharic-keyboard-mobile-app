package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.binisog.amharickeyboard.core.layout.Key
import kotlin.math.max
import kotlin.math.min

/**
 * Transparent layer above the suggestion strip and keyboard that draws key previews and
 * long-press popups. It lives in the same window as the keyboard, so previews for the top
 * row can extend over the strip instead of being clipped. It never takes touches; the
 * keyboard view keeps tracking the finger and tells the overlay what to highlight.
 */
class PopupOverlay(context: Context) : View(context) {

    var theme: KeyboardTheme = KeyboardTheme.LIGHT
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Preview bubble
    private var previewLabel: String? = null
    private val previewRect = RectF()

    // Long-press popup
    private var popupKeys: List<Key> = emptyList()
    private val popupRect = RectF()
    private var cellWidth = 0f
    var popupSelection = -1
        private set

    init {
        isClickable = false
        isFocusable = false
    }

    /** [keyRect] is in this overlay's coordinates. */
    fun showPreview(label: String, keyRect: RectF) {
        val w = max(keyRect.width() * 1.25f, 44 * density)
        val h = keyRect.height() * 1.35f
        val cx = keyRect.centerX()
        val left = (cx - w / 2).coerceIn(0f, max(0f, width - w))
        val bottom = keyRect.top + keyRect.height() * 0.15f
        previewRect.set(left, max(0f, bottom - h), left + w, max(h, bottom))
        previewLabel = label
        invalidate()
    }

    fun hidePreview() {
        if (previewLabel != null) { previewLabel = null; invalidate() }
    }

    val isPopupShowing: Boolean get() = popupKeys.isNotEmpty()

    fun showPopup(keys: List<Key>, keyRect: RectF, initialIndex: Int = 0) {
        hidePreview()
        popupKeys = keys
        cellWidth = max(keyRect.width(), 40 * density)
        val totalW = min(cellWidth * keys.size, width.toFloat())
        cellWidth = totalW / keys.size
        val h = keyRect.height() * 1.1f
        val left = (keyRect.centerX() - cellWidth / 2 - initialIndex * cellWidth).coerceIn(0f, max(0f, width - totalW))
        val top = max(0f, keyRect.top - h - 4 * density)
        popupRect.set(left, top, left + totalW, top + h)
        popupSelection = initialIndex.coerceIn(0, keys.lastIndex)
        invalidate()
    }

    /** Updates the highlighted popup cell from the finger's x position (overlay coordinates). */
    fun trackPopup(x: Float) {
        if (popupKeys.isEmpty()) return
        val i = ((x - popupRect.left) / cellWidth).toInt().coerceIn(0, popupKeys.lastIndex)
        if (i != popupSelection) { popupSelection = i; invalidate() }
    }

    fun selectedPopupKey(): Key? = popupKeys.getOrNull(popupSelection)

    fun hidePopup() {
        if (popupKeys.isNotEmpty()) { popupKeys = emptyList(); popupSelection = -1; invalidate() }
    }

    fun hideAll() { hidePreview(); hidePopup() }

    override fun onDraw(canvas: Canvas) {
        val r = 8 * density
        previewLabel?.let { label ->
            drawBubble(canvas, previewRect, r)
            textPaint.color = theme.text
            textPaint.textSize = previewRect.height() * 0.48f
            drawCentered(canvas, label, previewRect.centerX(), previewRect.centerY())
        }
        if (popupKeys.isNotEmpty()) {
            drawBubble(canvas, popupRect, r)
            textPaint.textSize = popupRect.height() * 0.42f
            for ((i, k) in popupKeys.withIndex()) {
                val cell = RectF(popupRect.left + i * cellWidth, popupRect.top, popupRect.left + (i + 1) * cellWidth, popupRect.bottom)
                if (i == popupSelection) {
                    bgPaint.color = theme.popupSelected
                    canvas.drawRoundRect(cell.apply { inset(2 * density, 2 * density) }, r, r, bgPaint)
                    textPaint.color = theme.onAccent
                } else {
                    textPaint.color = theme.text
                }
                val size = if (k.label.length > 2) popupRect.height() * 0.28f else popupRect.height() * 0.42f
                textPaint.textSize = size
                drawCentered(canvas, k.label, cell.centerX(), cell.centerY())
            }
        }
    }

    private fun drawBubble(canvas: Canvas, rect: RectF, r: Float) {
        shadowPaint.color = theme.keyShadow
        canvas.drawRoundRect(RectF(rect).apply { offset(0f, 2 * density) }, r, r, shadowPaint)
        bgPaint.color = theme.popup
        canvas.drawRoundRect(rect, r, r, bgPaint)
    }

    private fun drawCentered(canvas: Canvas, s: String, cx: Float, cy: Float) {
        val fm = textPaint.fontMetrics
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }
}
