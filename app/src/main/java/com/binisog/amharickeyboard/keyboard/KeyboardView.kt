package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.binisog.amharickeyboard.core.layout.Code
import com.binisog.amharickeyboard.core.layout.Key
import com.binisog.amharickeyboard.core.layout.KeyboardLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * Canvas-drawn keyboard. Handles:
 *  - tap (fires on release, with roll-over when a second finger lands),
 *  - key preview bubbles and long-press popups (slide to choose, release to commit),
 *  - auto-repeating delete that accelerates to whole words,
 *  - space-bar swipe to move the cursor, long-press space for the keyboard picker,
 *  - swipe left from delete to remove whole words,
 *  - one-handed mode (keys squeezed to one side, with a side bar to switch or leave).
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyDown(key: Key)
        fun onKey(key: Key)
        /** Delete held down: [repeat] counts up from 1. */
        fun onDeleteRepeat(repeat: Int)
        fun onCursorMove(delta: Int)
        fun onSpaceLongPress()
        /** Long-press on the language (አም / En) key: open quick settings. */
        fun onLanguageLongPress() {}
        /** Swiped left from delete: remove [words] whole words before the cursor. */
        fun onDeleteSwipe(words: Int) {}
        /** One-handed side bar: [ONE_HANDED_SWITCH] or [ONE_HANDED_EXIT]. */
        fun onOneHandedAction(action: Int) {}
    }

    enum class ShiftState { OFF, ON, LOCKED }

    var listener: Listener? = null
    var overlay: PopupOverlay? = null

    var theme: KeyboardTheme = KeyboardTheme.LIGHT
        set(v) { field = v; invalidate() }
    var keyHeightDp = 54
        set(v) { field = v; requestLayout() }
    var showPreview = true
    var spaceSwipeEnabled = true
    /** 0 = full width, -1 = keys on the left, 1 = keys on the right. */
    var oneHanded = 0
        set(v) { if (field != v) { field = v; if (width > 0) placeKeys(width); invalidate() } }
    /** The empty side next to one-handed keys: top half switches sides, bottom half leaves. */
    private val sideBar = RectF()
    var shiftState = ShiftState.OFF
        set(v) { field = v; invalidate() }
    var enterLabel = "↵"
        set(v) { field = v; invalidate() }
    var spaceLabel = ""
        set(v) { field = v; invalidate() }
    /** When a background picture shows through: no background fill, translucent keys. */
    var transparentBackground = false
        set(v) { field = v; invalidate() }
    /** Key opacity 0–255, used when [transparentBackground] is on. */
    var keyAlpha = 255
        set(v) { field = v.coerceIn(0, 255); invalidate() }

    private var layout: KeyboardLayout? = null
    private data class Placed(val key: Key, val rect: RectF)
    private var placed: List<Placed> = emptyList()

    private val density = resources.displayMetrics.density
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val gap = 3f * density
    private val vPad = 4f * density
    private val corner = 7f * density

    private val uiHandler = Handler(Looper.getMainLooper())
    private val longPressTimeout = 330L
    private val repeatStart = 380L
    private val repeatInterval = 55L

    // touch state for the active pointer
    private var activePointer = -1
    private var downKey: Placed? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var repeatCount = 0
    private var cursorMoving = false
    private var cursorAnchorX = 0f
    private var deleteSwipeWords = 0
    private var sideAction = 0

    fun setLayout(l: KeyboardLayout) {
        val sameHeight = layout?.rows?.size == l.rows.size
        layout = l
        // Always re-place keys: a page with the same height (letters ↔ symbols) does not
        // trigger onSizeChanged, and stale key rectangles would map taps to old keys.
        if (width > 0) placeKeys(width)
        if (!sameHeight) requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout?.rows?.size ?: 4
        val h = (rows * keyHeightDp * density + 2 * vPad).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        placeKeys(w)
    }

    private fun placeKeys(fullW: Int) {
        val l = layout ?: return
        val rowH = keyHeightDp * density
        val w = if (oneHanded == 0) fullW else (fullW * ONE_HANDED_WIDTH).toInt()
        val offset = if (oneHanded > 0) (fullW - w).toFloat() else 0f
        if (oneHanded == 0) sideBar.setEmpty()
        else sideBar.set(if (oneHanded > 0) 0f else w.toFloat(), vPad, if (oneHanded > 0) offset else fullW.toFloat(), vPad + l.rows.size * rowH)
        val unit = (w - gap) / l.columns
        val out = ArrayList<Placed>()
        l.rows.forEachIndexed { r, row ->
            val rowW = row.sumOf { it.width.toDouble() }.toFloat() * unit
            var x = offset + (w - rowW) / 2f
            val top = vPad + r * rowH
            for (k in row) {
                val kw = k.width * unit
                out.add(Placed(k, RectF(x + gap / 2, top + gap / 2, x + kw - gap / 2, top + rowH - gap / 2)))
                x += kw
            }
        }
        placed = out
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        if (!transparentBackground) canvas.drawColor(theme.background)
        if (!sideBar.isEmpty) drawSideBar(canvas)
        val alpha = if (transparentBackground) keyAlpha else 255
        val pressed = downKey
        for (p in placed) {
            val k = p.key
            val isPressed = pressed === p
            val bg = when {
                isPressed -> theme.keyPressed
                k.code == Code.ENTER -> theme.accent
                k.code == Code.SHIFT && shiftState != ShiftState.OFF -> theme.accent
                k.isFunction && k.code != Code.SPACE -> theme.keyFunction
                else -> theme.key
            }
            keyPaint.color = theme.keyShadow
            keyPaint.alpha = alpha / 2
            canvas.drawRoundRect(p.rect.left, p.rect.top + density, p.rect.right, p.rect.bottom + density, corner, corner, keyPaint)
            keyPaint.color = bg
            // pressed/accent keys stay solid so they remain easy to see on a picture
            keyPaint.alpha = if (isPressed || bg == theme.accent) 255 else alpha
            canvas.drawRoundRect(p.rect, corner, corner, keyPaint)

            val onAccent = !isPressed && (k.code == Code.ENTER || (k.code == Code.SHIFT && shiftState != ShiftState.OFF))
            labelPaint.color = if (onAccent) theme.onAccent else theme.text
            val label = when (k.code) {
                Code.ENTER -> enterLabel
                Code.SPACE -> spaceLabel
                Code.SHIFT -> if (shiftState == ShiftState.LOCKED) "⇪" else "⇧"
                else -> k.label
            }
            val h = p.rect.height()
            labelPaint.textSize = when {
                k.code == Code.SPACE -> h * 0.30f
                label.length >= 3 -> h * 0.30f
                label.length == 2 && k.isFunction -> h * 0.34f
                else -> h * 0.45f
            }
            labelPaint.typeface = if (k.isFunction) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (k.code == Code.SPACE) labelPaint.color = theme.hint
            val fm = labelPaint.fontMetrics
            canvas.drawText(label, p.rect.centerX(), p.rect.centerY() - (fm.ascent + fm.descent) / 2, labelPaint)

            k.hint?.let { hint ->
                hintPaint.color = theme.hint
                hintPaint.textSize = h * 0.24f
                canvas.drawText(hint, p.rect.right - 4 * density, p.rect.top + hintPaint.textSize + 1 * density, hintPaint)
            }
            if (k.popup.isNotEmpty() && k.hint == null && !k.isFunction) {
                // subtle dot: long-press has alternatives
                keyPaint.color = theme.hint
                canvas.drawCircle(p.rect.right - 6 * density, p.rect.top + 6 * density, 1.5f * density, keyPaint)
            }
        }
    }

    private fun drawSideBar(canvas: Canvas) {
        val cx = sideBar.centerX()
        val third = sideBar.height() / 3
        labelPaint.color = theme.hint
        labelPaint.typeface = Typeface.DEFAULT_BOLD
        labelPaint.textSize = minOf(sideBar.width() * 0.5f, 28 * density)
        val fm = labelPaint.fontMetrics
        val dy = -(fm.ascent + fm.descent) / 2
        canvas.drawText(if (oneHanded > 0) "‹" else "›", cx, sideBar.top + third + dy, labelPaint)
        labelPaint.textSize = minOf(sideBar.width() * 0.4f, 22 * density)
        val fm2 = labelPaint.fontMetrics
        canvas.drawText("⤢", cx, sideBar.top + 2 * third - (fm2.ascent + fm2.descent) / 2, labelPaint)
    }

    // ------------------------------------------------------------------ touch

    private fun keyAt(x: Float, y: Float): Placed? {
        var best: Placed? = null
        var bestDist = Float.MAX_VALUE
        for (p in placed) {
            // expand hit area to cover gaps
            if (x >= p.rect.left - gap && x <= p.rect.right + gap && y >= p.rect.top - gap && y <= p.rect.bottom + gap) {
                val d = abs(p.rect.centerX() - x) + abs(p.rect.centerY() - y)
                if (d < bestDist) { bestDist = d; best = p }
            }
        }
        return best
    }

    private val longPressRunnable = Runnable { onLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val k = downKey ?: return
            if (k.key.code != Code.DELETE) return
            repeatCount++
            listener?.onDeleteRepeat(repeatCount)
            uiHandler.postDelayed(this, if (repeatCount > 20) repeatInterval * 2 else repeatInterval)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> startPress(e, 0)
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Roll-over typing: commit the key held by the previous finger right away.
                if (activePointer != -1 && downKey != null && !longPressed && !cursorMoving) release(fire = true)
                startPress(e, e.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activePointer)
                if (i >= 0) move(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activePointer) release(fire = true)
            }
            MotionEvent.ACTION_UP -> if (activePointer != -1) release(fire = true)
            MotionEvent.ACTION_CANCEL -> release(fire = false)
        }
        return true
    }

    private fun startPress(e: MotionEvent, index: Int) {
        val x = e.getX(index); val y = e.getY(index)
        if (!sideBar.isEmpty && sideBar.contains(x, y)) {
            sideAction = if (y < sideBar.centerY()) ONE_HANDED_SWITCH else ONE_HANDED_EXIT
            activePointer = e.getPointerId(index)
            return
        }
        val p = keyAt(x, y) ?: return
        activePointer = e.getPointerId(index)
        downKey = p; downX = x; downY = y
        longPressed = false; repeatCount = 0; cursorMoving = false; cursorAnchorX = x; deleteSwipeWords = 0
        listener?.onKeyDown(p.key)
        if (showPreview && !p.key.isFunction) overlay?.showPreview(p.key.label, toOverlay(p.rect))
        uiHandler.removeCallbacksAndMessages(null)
        if (p.key.code == Code.DELETE) {
            listener?.onKey(p.key) // delete fires on press, then repeats
            uiHandler.postDelayed(repeatRunnable, repeatStart)
        } else {
            uiHandler.postDelayed(longPressRunnable, longPressTimeout)
        }
        invalidate()
    }

    private fun move(x: Float, y: Float) {
        val p = downKey ?: return
        val ov = overlay
        if (longPressed && ov != null && ov.isPopupShowing) {
            ov.trackPopup(x + overlayOffsetX())
            return
        }
        if (p.key.code == Code.SPACE && spaceSwipeEnabled) {
            val step = p.rect.width() / 10f
            if (!cursorMoving && abs(x - downX) > step) {
                cursorMoving = true
                cursorAnchorX = x
                uiHandler.removeCallbacks(longPressRunnable)
            }
            if (cursorMoving) {
                val delta = ((x - cursorAnchorX) / step).toInt()
                if (delta != 0) {
                    listener?.onCursorMove(delta)
                    cursorAnchorX += delta * step
                }
            }
            return
        }
        if (p.key.code == Code.DELETE) {
            // Swipe left from delete: one more word for every key width travelled.
            val dist = downX - x
            val step = p.rect.width().coerceAtLeast(24 * density)
            val words = if (dist > step * 0.8f) 1 + ((dist - step * 0.8f) / step).toInt() else 0
            if (words != deleteSwipeWords) {
                if (deleteSwipeWords == 0) uiHandler.removeCallbacks(repeatRunnable)
                deleteSwipeWords = words
                if (words > 0) overlay?.showPreview("⌫ $words", toOverlay(p.rect)) else overlay?.hideAll()
                invalidate()
            }
            return
        }
        // Slide off a key onto another: follow the finger (feels better than cancelling).
        if (!p.rect.contains(x, y)) {
            val q = keyAt(x, y)
            if (q != null && q !== p && !q.key.isFunction && !p.key.isFunction) {
                downKey = q
                uiHandler.removeCallbacks(longPressRunnable)
                uiHandler.postDelayed(longPressRunnable, longPressTimeout)
                if (showPreview) overlay?.showPreview(q.key.label, toOverlay(q.rect))
                invalidate()
            }
        }
    }

    private fun onLongPress() {
        val p = downKey ?: return
        val k = p.key
        when {
            k.code == Code.SPACE -> { longPressed = true; listener?.onSpaceLongPress(); performHaptic() }
            k.code == Code.LANGUAGE -> { longPressed = true; listener?.onLanguageLongPress(); performHaptic() }
            k.popup.isNotEmpty() -> {
                longPressed = true
                performHaptic()
                overlay?.showPopup(k.popup, toOverlay(p.rect))
            }
        }
    }

    private fun release(fire: Boolean) {
        uiHandler.removeCallbacksAndMessages(null)
        if (sideAction != 0) {
            val a = sideAction
            sideAction = 0
            activePointer = -1
            if (fire) listener?.onOneHandedAction(a)
            return
        }
        val p = downKey
        val ov = overlay
        if (fire && p != null) {
            when {
                longPressed && ov != null && ov.isPopupShowing -> ov.selectedPopupKey()?.let { listener?.onKey(it) }
                longPressed -> Unit
                cursorMoving -> Unit
                p.key.code == Code.DELETE && deleteSwipeWords > 0 -> listener?.onDeleteSwipe(deleteSwipeWords)
                p.key.code == Code.DELETE -> Unit // already fired on press
                else -> listener?.onKey(p.key)
            }
        }
        ov?.hideAll()
        downKey = null
        activePointer = -1
        longPressed = false
        cursorMoving = false
        deleteSwipeWords = 0
        invalidate()
    }

    fun cancelTouch() = release(fire = false)

    private fun performHaptic() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun overlayOffsetX(): Float {
        val ov = overlay ?: return 0f
        val a = IntArray(2); val b = IntArray(2)
        getLocationInWindow(a); ov.getLocationInWindow(b)
        return (a[0] - b[0]).toFloat()
    }

    private fun toOverlay(r: RectF): RectF {
        val ov = overlay ?: return RectF(r)
        val a = IntArray(2); val b = IntArray(2)
        getLocationInWindow(a); ov.getLocationInWindow(b)
        return RectF(r).apply { offset((a[0] - b[0]).toFloat(), (a[1] - b[1]).toFloat()) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiHandler.removeCallbacksAndMessages(null)
    }

    /** Height in px this view will take with the current layout (used for the emoji page). */
    companion object {
        const val ONE_HANDED_SWITCH = 1
        const val ONE_HANDED_EXIT = 2
        /** Share of the width the keys keep in one-handed mode. */
        const val ONE_HANDED_WIDTH = 0.82f
    }

    fun preferredHeight(): Int = max(1, ((layout?.rows?.size ?: 4) * keyHeightDp * density + 2 * vPad).toInt())
}
