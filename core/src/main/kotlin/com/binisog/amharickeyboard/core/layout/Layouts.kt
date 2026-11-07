package com.binisog.amharickeyboard.core.layout

import com.binisog.amharickeyboard.core.Ethiopic
import com.binisog.amharickeyboard.core.GeezNumerals
import com.binisog.amharickeyboard.core.TransliterationTable

/** A key on a keyboard page. */
data class Key(
    /** Text drawn on the key. */
    val label: String,
    /** Text produced (defaults to [label]); ignored for function keys. */
    val output: String = label,
    /** [Code] of a function key, or [Code.CHAR] for keys that produce text. */
    val code: Int = Code.CHAR,
    /** Small secondary label (top-right corner). */
    val hint: String? = null,
    /** Long-press alternatives. */
    val popup: List<Key> = emptyList(),
    /** Relative width; a normal letter key is 1. */
    val width: Float = 1f,
    /** For Fidel keys: the Ethiopic row, so the vowel strip can offer its forms. */
    val fidelRow: Int = -1,
) {
    val isFunction: Boolean get() = code != Code.CHAR
}

object Code {
    const val CHAR = 0
    const val SHIFT = -1
    const val SYMBOLS = -2
    const val ALPHA = -3
    const val ENTER = -4
    const val DELETE = -5
    const val LANGUAGE = -6
    const val EMOJI = -7
    const val SYMBOLS_2 = -8
    const val GEEZ_NUMBERS = -9
    const val SPACE = -10
    const val SPACER = -11
}

/** One keyboard page. [columns] is the width (in key units) all rows are laid out against. */
data class KeyboardLayout(
    val id: String,
    val rows: List<List<Key>>,
    /** Whether the shift key changes letter case on this page. */
    val shiftable: Boolean = false,
    /** Short name drawn on the space bar. */
    val spaceLabel: String = "",
) {
    val columns: Float = rows.maxOf { r -> r.sumOf { it.width.toDouble() }.toFloat() }
}

/** Contextual tweaks to the bottom row (email/URL fields) and options. */
data class LayoutOptions(
    val numberRow: Boolean = false,
    val hints: Boolean = true,
    val fieldKind: FieldKind = FieldKind.TEXT,
)

enum class FieldKind { TEXT, EMAIL, URL }

object Layouts {
    const val PHONETIC = "am_phonetic"
    const val FIDEL = "am_fidel"
    const val ENGLISH = "en"
    const val SYMBOLS = "symbols"
    const val SYMBOLS_2 = "symbols2"
    const val GEEZ_NUMBERS = "geez_numbers"
    const val EMOJI = "emoji"
    const val NUMBER_PAD = "number_pad"
    const val PHONE_PAD = "phone_pad"

    private val qwerty = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val digits = "1234567890"

    private val table get() = TransliterationTable.DEFAULT

    fun build(id: String, opt: LayoutOptions = LayoutOptions()): KeyboardLayout = when (id) {
        PHONETIC -> phonetic(opt)
        FIDEL -> fidel(opt)
        ENGLISH -> english(opt)
        SYMBOLS -> symbols(amharic = true)
        SYMBOLS_2 -> symbols2()
        GEEZ_NUMBERS -> geezNumbers()
        EMOJI -> emoji()
        NUMBER_PAD -> numberPad()
        PHONE_PAD -> phonePad()
        else -> phonetic(opt)
    }

    // ---------------------------------------------------------------- alphabetic pages

    /** Ethiopic letters whose latin key (or alias) starts with [c], as 6th-order forms. */
    internal fun fidelAlternatives(c: Char): List<Key> {
        val out = ArrayList<Key>()
        for (row in Ethiopic.rows) {
            if (row.base == 0x12A0 || row.base == 0x12D0) continue
            val spellings = (listOf(row.key) + row.aliases).map { it.removePrefix("`") }
            val keyFor = (listOf(row.key) + row.aliases).firstOrNull { it.removePrefix("`").firstOrNull()?.lowercaseChar() == c }
            if (spellings.any { it.firstOrNull()?.lowercaseChar() == c } && keyFor != null) {
                out.add(Key(row.bare.toString(), output = keyFor))
            }
        }
        return out
    }

    private fun phonetic(opt: LayoutOptions): KeyboardLayout {
        val rows = ArrayList<List<Key>>()
        if (opt.numberRow) rows.add(numberRow(geez = true))
        qwerty.forEachIndexed { r, letters ->
            val keys = letters.mapIndexed { i, ch ->
                val popup = ArrayList<Key>()
                if (r == 0 && !opt.numberRow) popup.add(Key(digits[i].toString()))
                popup.addAll(fidelAlternatives(ch))
                // vowel keys offer the vowel-carrier and ዐ forms
                when (ch) {
                    'a' -> popup.addAll(listOf(Key("አ", "a"), Key("ኣ", "aa"), Key("ዐ", "`a"), Key("ዓ", "A")))
                    'e' -> popup.addAll(listOf(Key("እ", "e"), Key("ኤ", "E"), Key("ዕ", "`e")))
                    'i' -> popup.addAll(listOf(Key("ኢ", "i"), Key("ዒ", "I")))
                    'o' -> popup.addAll(listOf(Key("ኦ", "o"), Key("ዖ", "O")))
                    'u' -> popup.addAll(listOf(Key("ኡ", "u"), Key("ዑ", "U")))
                }
                Key(ch.toString(), hint = if (opt.hints) table[ch.toString()]?.takeIf { it != ch.toString() } else null,
                    popup = popup.distinctBy { it.label })
            }.toMutableList()
            if (r == 2) {
                keys.add(0, Key("⇧", code = Code.SHIFT, width = 1.5f))
                keys.add(Key("⌫", code = Code.DELETE, width = 1.5f))
            }
            rows.add(keys)
        }
        rows.add(bottomRow(amharic = true, opt.fieldKind, symbolsLabel = "?123", langLabel = "🌐"))
        return KeyboardLayout(PHONETIC, rows, shiftable = true, spaceLabel = "አማርኛ")
    }

    /** Shifted label/hint for the phonetic page: only letters with a capital mapping change. */
    fun phoneticShifted(key: Key, hints: Boolean): Key {
        if (key.isFunction || key.label.length != 1 || !key.label[0].isLetter()) return key
        val up = key.label.uppercase()
        val mapped = table[up] ?: return key
        return key.copy(label = up, output = up, hint = if (hints) mapped else null)
    }

    private fun english(opt: LayoutOptions): KeyboardLayout {
        val rows = ArrayList<List<Key>>()
        if (opt.numberRow) rows.add(numberRow(geez = false))
        val accents = mapOf(
            'e' to "éèêë", 'a' to "áàâäã", 'i' to "íìîï", 'o' to "óòôöõ", 'u' to "úùûü", 'c' to "ç", 'n' to "ñ", 's' to "ß",
        )
        qwerty.forEachIndexed { r, letters ->
            val keys = letters.mapIndexed { i, ch ->
                val popup = ArrayList<Key>()
                if (r == 0 && !opt.numberRow) popup.add(Key(digits[i].toString()))
                accents[ch]?.forEach { popup.add(Key(it.toString())) }
                Key(ch.toString(), hint = if (r == 0 && !opt.numberRow && opt.hints) digits[i].toString() else null, popup = popup)
            }.toMutableList()
            if (r == 2) {
                keys.add(0, Key("⇧", code = Code.SHIFT, width = 1.5f))
                keys.add(Key("⌫", code = Code.DELETE, width = 1.5f))
            }
            rows.add(keys)
        }
        rows.add(bottomRow(amharic = false, opt.fieldKind, symbolsLabel = "?123", langLabel = "🌐"))
        return KeyboardLayout(ENGLISH, rows, shiftable = true, spaceLabel = "English")
    }

    /**
     * Fidel grid: one key per consonant in traditional ሀ-ለ-ሐ-መ order (1st-order label).
     * Tapping inserts the letter and the strip offers its vowel forms; long-press shows them too.
     */
    private fun fidel(opt: LayoutOptions): KeyboardLayout {
        val ordered = Ethiopic.rows.filter { it.base != 0x1250 } + Ethiopic.rows.filter { it.base == 0x1250 }
        val keys = ordered.map { row ->
            Key(
                label = row.first.toString(),
                hint = if (!opt.hints) null else when (row.base) {
                    0x12A0 -> "a"
                    0x12D0 -> "A"
                    else -> row.key.removePrefix("`")
                },
                popup = row.allForms.map { Key(it.toString()) },
                fidelRow = row.base,
            )
        }
        val rows = ArrayList<List<Key>>()
        if (opt.numberRow) rows.add(numberRow(geez = true))
        rows.add(keys.subList(0, 9))
        rows.add(keys.subList(9, 18))
        rows.add(keys.subList(18, 27))
        rows.add(keys.subList(27, keys.size) + Key("⌫", code = Code.DELETE, width = 9f - (keys.size - 27)))
        rows.add(bottomRow(amharic = true, opt.fieldKind, symbolsLabel = "?123", langLabel = "🌐", widthScale = 0.9f))
        return KeyboardLayout(FIDEL, rows, shiftable = false, spaceLabel = "ፊደል")
    }

    private fun numberRow(geez: Boolean): List<Key> = digits.map { d ->
        val g = if (d == '0') null else GeezNumerals.toGeez(d.toString().toLong())
        Key(d.toString(), popup = listOfNotNull(g?.let { Key(it) }), hint = if (geez) g else null)
    }

    private fun bottomRow(amharic: Boolean, kind: FieldKind, symbolsLabel: String, langLabel: String, widthScale: Float = 1f): List<Key> {
        val comma = when (kind) {
            FieldKind.EMAIL -> Key("@", popup = listOf(Key(".com"), Key(".et"), Key("_"), Key("-")))
            FieldKind.URL -> Key("/", popup = listOf(Key(".com"), Key(".et"), Key("www."), Key("https://")))
            FieldKind.TEXT -> if (amharic) Key("፣", popup = listOf(Key(","), Key("፤"), Key("፥"), Key("፦"), Key("፡")))
            else Key(",", popup = listOf(Key(";"), Key(":"), Key("'"), Key("\"")))
        }
        val period = if (amharic && kind == FieldKind.TEXT) {
            Key("።", popup = listOf(Key("."), Key("?"), Key("!"), Key("፧"), Key("፨"), Key("…")))
        } else {
            Key(".", popup = listOf(Key("?"), Key("!"), Key(","), Key("…"), Key("።")))
        }
        val s = widthScale
        return listOf(
            Key(symbolsLabel, code = Code.SYMBOLS, width = 1.5f * s),
            Key(langLabel, code = Code.LANGUAGE, width = 1f * s),
            comma.copy(width = 1f * s),
            Key(" ", code = Code.SPACE, width = 4f * s),
            period.copy(width = 1f * s),
            Key("↵", code = Code.ENTER, width = 1.5f * s),
        )
    }

    // ---------------------------------------------------------------- symbol pages

    private fun charKeys(s: String): List<Key> = s.map { Key(it.toString()) }

    private fun symbols(amharic: Boolean): KeyboardLayout = KeyboardLayout(
        SYMBOLS,
        listOf(
            numberRow(geez = amharic),
            charKeys("@#$%&-+()").plus(Key("ብር", popup = listOf(Key("$"), Key("€"), Key("£"), Key("¥")))),
            listOf(Key("=\\<", code = Code.SYMBOLS_2, width = 1.5f)) + charKeys("*\"':;!?") + Key("⌫", code = Code.DELETE, width = 1.5f),
            listOf(Key("ABC", code = Code.ALPHA, width = 1.5f), Key("፩፪", code = Code.GEEZ_NUMBERS, width = 1f),
                Key("፣", popup = listOf(Key(","), Key("፤"), Key("፥"), Key("፦"))),
                Key(" ", code = Code.SPACE, width = 4f),
                Key("።", popup = listOf(Key("."), Key("፧"), Key("፨"))),
                Key("↵", code = Code.ENTER, width = 1.5f)),
        ),
    )

    private fun symbols2(): KeyboardLayout = KeyboardLayout(
        SYMBOLS_2,
        listOf(
            charKeys("~`|•√π÷×¶∆"),
            charKeys("£€¥^°={}\\_"),
            listOf(Key("?123", code = Code.SYMBOLS, width = 1.5f)) + charKeys("%©®™✓[]") + Key("⌫", code = Code.DELETE, width = 1.5f),
            listOf(Key("ABC", code = Code.ALPHA, width = 1.5f), Key("<", width = 1f), Key(">", width = 1f),
                Key(" ", code = Code.SPACE, width = 4f), Key("…", width = 1f), Key("↵", code = Code.ENTER, width = 1.5f)),
        ),
    )

    /** Ge'ez numerals ፩–፱, ፲–፺, ፻, ፼ plus Ethiopic punctuation. */
    private fun geezNumbers(): KeyboardLayout {
        val k = GeezNumerals.keys
        return KeyboardLayout(
            GEEZ_NUMBERS,
            listOf(
                k.subList(0, 9).map { Key(it.toString()) } + Key(GeezNumerals.HUNDRED.toString()),
                k.subList(9, 18).map { Key(it.toString()) } + Key(GeezNumerals.TEN_THOUSAND.toString()),
                listOf(Key("?123", code = Code.SYMBOLS, width = 1.5f)) +
                    Ethiopic.punctuation.take(7).map { Key(it.toString()) } + Key("⌫", code = Code.DELETE, width = 1.5f),
                listOf(Key("ABC", code = Code.ALPHA, width = 1.5f), Key("፨"), Key("፣"),
                    Key(" ", code = Code.SPACE, width = 4f), Key("።"), Key("↵", code = Code.ENTER, width = 1.5f)),
            ),
        )
    }

    val EMOJI_LIST: List<String> = (
        "😀 😂 🤣 😊 😍 🥰 😘 😎 🤔 😢 😭 😡 😴 🥳 😇 🙄 " +
            "👍 👎 👏 🙏 💪 👋 🤝 ✌️ 👌 🤲 ❤️ 💚 💛 🧡 💔 💯 " +
            "🔥 ✨ 🎉 🎂 🎁 🌹 🌼 ☀️ 🌧️ ⭐ ☕ 🫓 🍞 🍽️ ⚽ 🎵 " +
            "📱 💻 📚 ✏️ 🏠 🚗 ✈️ ⏰ 💰 🇪🇹 ✅ ❌ ❗ ❓ 👶 🐕"
        ).split(' ')

    private fun emoji(): KeyboardLayout {
        val rows = EMOJI_LIST.chunked(8).map { chunk -> chunk.map { Key(it) } }.toMutableList()
        rows.add(listOf(Key("ABC", code = Code.ALPHA, width = 2f), Key(" ", code = Code.SPACE, width = 4f), Key("⌫", code = Code.DELETE, width = 2f)))
        return KeyboardLayout(EMOJI, rows)
    }

    private fun numberPad(): KeyboardLayout = KeyboardLayout(
        NUMBER_PAD,
        listOf(
            listOf(Key("1"), Key("2"), Key("3"), Key("-", popup = listOf(Key("+")))),
            listOf(Key("4"), Key("5"), Key("6"), Key(",")),
            listOf(Key("7"), Key("8"), Key("9"), Key("⌫", code = Code.DELETE)),
            listOf(Key("ABC", code = Code.ALPHA), Key("0"), Key("."), Key("↵", code = Code.ENTER)),
        ),
    )

    private fun phonePad(): KeyboardLayout = KeyboardLayout(
        PHONE_PAD,
        listOf(
            listOf(Key("1"), Key("2"), Key("3"), Key("+", popup = listOf(Key("251")))),
            listOf(Key("4"), Key("5"), Key("6"), Key(" ", code = Code.SPACE)),
            listOf(Key("7"), Key("8"), Key("9"), Key("⌫", code = Code.DELETE)),
            listOf(Key("*"), Key("0"), Key("#"), Key("↵", code = Code.ENTER)),
        ),
    )
}
