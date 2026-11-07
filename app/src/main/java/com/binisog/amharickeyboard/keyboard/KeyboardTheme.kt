package com.binisog.amharickeyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

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

        fun resolve(ctx: Context, name: String): KeyboardTheme = when (name) {
            "light" -> LIGHT
            "dark" -> DARK
            "ethiopia" -> ETHIOPIA
            else -> {
                val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
            }
        }
    }
}
