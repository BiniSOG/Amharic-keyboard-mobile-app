package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import kotlin.math.roundToInt

/** Colour palette for the keyboard, strip and popups. */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val keyFunction: Int,
    val keyPressed: Int,
    val keyShadow: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val onAccent: Int,
    val popup: Int,
    val popupSelected: Int,
    val strip: Int,
    val stripText: Int,
    val stripHighlight: Int,
    val divider: Int,
) {
    companion object {
        val LIGHT = KeyboardTheme(
            background = Color.parseColor("#E8EAED"), key = Color.WHITE, keyFunction = Color.parseColor("#C9CDD3"),
            keyPressed = Color.parseColor("#B0B6BE"), keyShadow = Color.parseColor("#9AA0A6"),
            text = Color.parseColor("#202124"), hint = Color.parseColor("#6B7076"),
            accent = Color.parseColor("#078930"), onAccent = Color.WHITE,
            popup = Color.WHITE, popupSelected = Color.parseColor("#078930"),
            strip = Color.parseColor("#E8EAED"), stripText = Color.parseColor("#202124"),
            stripHighlight = Color.parseColor("#078930"), divider = Color.parseColor("#C4C7CC"),
        )
        val DARK = KeyboardTheme(
            background = Color.parseColor("#1F1F22"), key = Color.parseColor("#3A3B3F"), keyFunction = Color.parseColor("#2B2C30"),
            keyPressed = Color.parseColor("#55575C"), keyShadow = Color.parseColor("#111113"),
            text = Color.parseColor("#ECEDEF"), hint = Color.parseColor("#9AA0A6"),
            accent = Color.parseColor("#2FA84F"), onAccent = Color.WHITE,
            popup = Color.parseColor("#45464B"), popupSelected = Color.parseColor("#2FA84F"),
            strip = Color.parseColor("#1F1F22"), stripText = Color.parseColor("#ECEDEF"),
            stripHighlight = Color.parseColor("#FCDD09"), divider = Color.parseColor("#3A3B3F"),
        )
        /** Green, gold and red of the Ethiopian flag on a deep green base. */
        val ETHIOPIA = KeyboardTheme(
            background = Color.parseColor("#0B3D20"), key = Color.parseColor("#14572F"), keyFunction = Color.parseColor("#0F4826"),
            keyPressed = Color.parseColor("#1E7A43"), keyShadow = Color.parseColor("#06210F"),
            text = Color.parseColor("#FFFFFF"), hint = Color.parseColor("#FCDD09"),
            accent = Color.parseColor("#DA121A"), onAccent = Color.WHITE,
            popup = Color.parseColor("#14572F"), popupSelected = Color.parseColor("#DA121A"),
            strip = Color.parseColor("#0B3D20"), stripText = Color.WHITE,
            stripHighlight = Color.parseColor("#FCDD09"), divider = Color.parseColor("#14572F"),
        )
        val MIDNIGHT = fromColors(bg = "#0D1B2A", key = "#1B263B", text = "#E0E1DD", accent = "#4CC9F0")
        val OCEAN = fromColors(bg = "#E0F2F1", key = "#FFFFFF", text = "#004D40", accent = "#00897B")
        val ROSE = fromColors(bg = "#FCE4EC", key = "#FFFFFF", text = "#4A1426", accent = "#D81B60")
        val PURPLE = fromColors(bg = "#1E1530", key = "#2E2248", text = "#EDE7F6", accent = "#9C6ADE")
        val HIGH_CONTRAST = fromColors(bg = "#000000", key = "#000000", text = "#FFFFFF", accent = "#FFD600")
            .copy(keyShadow = Color.WHITE, divider = Color.WHITE)

        /** Default colours offered for the custom theme. */
        const val DEFAULT_CUSTOM_BG = 0xFF263238.toInt()
        const val DEFAULT_CUSTOM_KEY = 0xFF37474F.toInt()
        const val DEFAULT_CUSTOM_TEXT = 0xFFFFFFFF.toInt()
        const val DEFAULT_CUSTOM_ACCENT = 0xFFFF9800.toInt()

        private fun fromColors(bg: String, key: String, text: String, accent: String) =
            custom(Color.parseColor(bg), Color.parseColor(key), Color.parseColor(text), Color.parseColor(accent))

        /** Derives a complete palette from four user-chosen colours. */
        fun custom(bg: Int, key: Int, text: Int, accent: Int): KeyboardTheme {
            val dark = luminance(bg) < 0.5
            return KeyboardTheme(
                background = bg,
                key = key,
                keyFunction = blend(key, bg, 0.5f),
                keyPressed = blend(key, text, 0.25f),
                keyShadow = blend(bg, Color.BLACK, if (dark) 0.5f else 0.25f),
                text = text,
                hint = blend(text, key, 0.4f),
                accent = accent,
                onAccent = if (luminance(accent) > 0.6) Color.BLACK else Color.WHITE,
                popup = key,
                popupSelected = accent,
                strip = bg,
                stripText = text,
                stripHighlight = accent,
                divider = blend(bg, text, 0.18f),
            )
        }

        fun blend(a: Int, b: Int, t: Float): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
            return Color.argb(255, ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
        }

        fun luminance(c: Int): Double = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0

        fun resolve(ctx: Context, name: String, customColors: IntArray? = null): KeyboardTheme = when (name) {
            "light" -> LIGHT
            "dark" -> DARK
            "ethiopia" -> ETHIOPIA
            "midnight" -> MIDNIGHT
            "ocean" -> OCEAN
            "rose" -> ROSE
            "purple" -> PURPLE
            "high_contrast" -> HIGH_CONTRAST
            "custom" -> {
                val c = customColors ?: intArrayOf(DEFAULT_CUSTOM_BG, DEFAULT_CUSTOM_KEY, DEFAULT_CUSTOM_TEXT, DEFAULT_CUSTOM_ACCENT)
                custom(c[0], c[1], c[2], c[3])
            }
            else -> {
                val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
            }
        }
    }
}
