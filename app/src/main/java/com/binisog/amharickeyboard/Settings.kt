package com.binisog.amharickeyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.binisog.amharickeyboard.keyboard.KeyboardTheme

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
    val translate = p.getBoolean("translate", true)
    /** Suggest even in boxes where the app asks for no suggestions (e.g. TikTok). */
    val suggestEverywhere = p.getBoolean("suggest_everywhere", true)
    /** Keep a private log of typed sentences and suggest from it. */
    val history = p.getBoolean("history", true)
    val learn = p.getBoolean("learn", true)
    val theme: String = p.getString("theme", "system") ?: "system"
    val keyHeightDp: Int = p.getString("key_height", "54")?.toIntOrNull() ?: 54
    val haptic = p.getBoolean("haptic", true)
    val sound = p.getBoolean("sound", false)
    val preview = p.getBoolean("preview", true)
    val customColors: IntArray = intArrayOf(
        p.getInt("custom_bg", KeyboardTheme.DEFAULT_CUSTOM_BG),
        p.getInt("custom_key", KeyboardTheme.DEFAULT_CUSTOM_KEY),
        p.getInt("custom_text", KeyboardTheme.DEFAULT_CUSTOM_TEXT),
        p.getInt("custom_accent", KeyboardTheme.DEFAULT_CUSTOM_ACCENT),
    )
    /** Changes whenever the background image is replaced or removed. */
    val bgStamp: Long = p.getLong(KEY_BG_STAMP, 0L)
    /** Dark overlay over the background image, 0–80 %. */
    val bgDim: Int = p.getInt("bg_dim", 30)
    /** Key opacity on top of a background image, 20–100 %. */
    val keyOpacity: Int = p.getInt("key_opacity", 75)

    companion object {
        const val LAYOUT_PHONETIC = "am_phonetic"
        const val LAYOUT_FIDEL = "am_fidel"
        const val LAYOUT_ENGLISH = "en"
        val ALL_LAYOUTS = listOf(LAYOUT_PHONETIC, LAYOUT_FIDEL, LAYOUT_ENGLISH)

        private const val KEY_LAST_LAYOUT = "last_layout"
        const val KEY_BG_STAMP = "bg_stamp"

        @Volatile private var migrated = false

        /**
         * Preferences live in device-protected storage so they can be read before the first
         * unlock after a reboot. Settings saved by older versions are moved over once.
         */
        fun prefs(ctx: Context): SharedPreferences {
            val de = DirectBoot.deviceProtected(ctx)
            if (!migrated && DirectBoot.isUnlocked(ctx)) {
                migrated = true
                runCatching { de.moveSharedPreferencesFrom(ctx, defaultPrefsName(ctx)) }
            }
            return PreferenceManager.getDefaultSharedPreferences(de)
        }

        fun defaultPrefsName(ctx: Context) = ctx.packageName + "_preferences"
        fun load(ctx: Context) = Settings(prefs(ctx))

        fun lastLayout(ctx: Context): String = prefs(ctx).getString(KEY_LAST_LAYOUT, LAYOUT_PHONETIC) ?: LAYOUT_PHONETIC
        fun setLastLayout(ctx: Context, id: String) = prefs(ctx).edit().putString(KEY_LAST_LAYOUT, id).apply()
    }
}
