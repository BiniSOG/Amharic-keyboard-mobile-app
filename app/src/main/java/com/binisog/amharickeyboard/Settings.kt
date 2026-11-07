package com.binisog.amharickeyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Snapshot of the user's keyboard preferences (see res/xml/preferences.xml). */
class Settings private constructor(p: SharedPreferences) {
    val layouts: List<String> = (p.getStringSet("layouts", null) ?: ALL_LAYOUTS.toSet())
        .let { set -> ALL_LAYOUTS.filter { it in set } }
        .ifEmpty { listOf(ALL_LAYOUTS.first()) }
    val numberRow = p.getBoolean("number_row", false)
    val hints = p.getBoolean("hints", true)
    val doubleSpacePeriod = p.getBoolean("double_space", true)
    val autoSpace = p.getBoolean("auto_space", true)
    val autoCap = p.getBoolean("auto_cap", true)
    val spaceCursor = p.getBoolean("space_cursor", true)
    val suggestions = p.getBoolean("suggestions", true)
    val nextWord = p.getBoolean("next_word", true)
    val emojiSuggestions = p.getBoolean("emoji_suggestions", true)
    val learn = p.getBoolean("learn", true)
    val theme: String = p.getString("theme", "system") ?: "system"
    val keyHeightDp: Int = p.getString("key_height", "54")?.toIntOrNull() ?: 54
    val haptic = p.getBoolean("haptic", true)
    val sound = p.getBoolean("sound", false)
    val preview = p.getBoolean("preview", true)

    companion object {
        const val LAYOUT_PHONETIC = "am_phonetic"
        const val LAYOUT_FIDEL = "am_fidel"
        const val LAYOUT_ENGLISH = "en"
        val ALL_LAYOUTS = listOf(LAYOUT_PHONETIC, LAYOUT_FIDEL, LAYOUT_ENGLISH)

        private const val KEY_LAST_LAYOUT = "last_layout"

        fun prefs(ctx: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        fun load(ctx: Context) = Settings(prefs(ctx))

        fun lastLayout(ctx: Context): String = prefs(ctx).getString(KEY_LAST_LAYOUT, LAYOUT_PHONETIC) ?: LAYOUT_PHONETIC
        fun setLastLayout(ctx: Context, id: String) = prefs(ctx).edit().putString(KEY_LAST_LAYOUT, id).apply()
    }
}
