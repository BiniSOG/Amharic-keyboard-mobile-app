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
    /** Switches to the page whose id is the key's [Key.output]. */
    const val PAGE = -8
    const val SPACE = -10
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
    /** Current language, shown on the symbol pages' language key (አም / En). */
    val amharic: Boolean = true,
)

enum class FieldKind { TEXT, EMAIL, URL }

object Layouts {
    const val PHONETIC = "am_phonetic"
    const val FIDEL = "am_fidel"
    const val ENGLISH = "en"
    const val SYMBOLS = "symbols"
    const val SYMBOLS_2 = "symbols2"
    const val GEEZ_NUMBERS = "geez_numbers"
    const val MATH = "math"
    const val ARROWS = "arrows"
    const val PUNCTUATION = "punctuation"
    const val ACCENTS = "accents"
    const val ETHIOPIC_EXTRA = "ethiopic_extra"

    /** Symbol pages in the order the page key (1/8 ▸) steps through them. */
    val SYMBOL_PAGES = listOf(SYMBOLS, SYMBOLS_2, MATH, ARROWS, PUNCTUATION, ACCENTS, ETHIOPIC_EXTRA, GEEZ_NUMBERS)

    /** Short label per page, used in the page key's long-press menu. */
    private val PAGE_ICONS = mapOf(
        SYMBOLS to "?123", SYMBOLS_2 to "~|", MATH to "±∑", ARROWS to "→★", PUNCTUATION to "«»",
        ACCENTS to "é", ETHIOPIC_EXTRA to "ቈ", GEEZ_NUMBERS to "፩፪",
    )
    const val NUMBER_PAD = "number_pad"
    const val PHONE_PAD = "phone_pad"

    /** Language-key labels: current language of the layout. */
    const val LANG_AMHARIC = "አም"
    const val LANG_ENGLISH = "En"

    private val qwerty = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val digits = "1234567890"

    private val table get() = TransliterationTable.DEFAULT

    fun build(id: String, opt: LayoutOptions = LayoutOptions()): KeyboardLayout = when (id) {
        PHONETIC -> phonetic(opt)
        FIDEL -> fidel(opt)
        ENGLISH -> english(opt)
        in SYMBOL_PAGES -> symbolPage(id, if (opt.amharic) LANG_AMHARIC else LANG_ENGLISH)
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
        rows.add(bottomRow(amharic = true, opt.fieldKind, symbolsLabel = "?123", langLabel = LANG_AMHARIC))
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
        rows.add(bottomRow(amharic = false, opt.fieldKind, symbolsLabel = "?123", langLabel = LANG_ENGLISH))
        return KeyboardLayout(ENGLISH, rows, shiftable = true, spaceLabel = "English")
    }

    /**
     * Fidel grid: one key per consonant in traditional ሀ-ለ-ሐ-መ order (1st-order label).
     * Tapping inserts the letter and the strip offers its vowel forms; long-press shows them too.
     */
    private fun fidel(opt: LayoutOptions): KeyboardLayout {
        val ordered = Ethiopic.rows.filter { it.base != 0x1250 } + Ethiopic.rows.filter { it.base == 0x1250 }
        // 9 letters per row, widened so every row spans the same 10 key-widths as the number row
        val w = 10f / 9f
        val keys = ordered.map { row ->
            Key(
                width = w,
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
        rows.add(keys.subList(27, keys.size) + Key("⌫", code = Code.DELETE, width = 10f - (keys.size - 27) * w))
        rows.add(bottomRow(amharic = true, opt.fieldKind, symbolsLabel = "?123", langLabel = LANG_AMHARIC))
        return KeyboardLayout(FIDEL, rows, shiftable = false, spaceLabel = "ፊደል")
    }

    private fun numberRow(geez: Boolean): List<Key> = digits.map { d ->
        val g = if (d == '0') null else GeezNumerals.toGeez(d.toString().toLong())
        Key(d.toString(), popup = listOfNotNull(g?.let { Key(it) }), hint = if (geez) g else null)
    }

    private fun bottomRow(amharic: Boolean, kind: FieldKind, symbolsLabel: String, langLabel: String): List<Key> {
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
        return listOf(
            Key(symbolsLabel, code = Code.SYMBOLS, width = 1.5f),
            // Shows the current language; the small globe marks it as the switch key.
            Key(langLabel, code = Code.LANGUAGE, hint = "🌐", width = 1f),
            comma.copy(width = 1f),
            Key(" ", code = Code.SPACE, width = 4f),
            period.copy(width = 1f),
            Key("↵", code = Code.ENTER, width = 1.5f),
        )
    }

    // ---------------------------------------------------------------- symbol pages

    private fun charKeys(s: String): List<Key> = s.map { Key(it.toString()) }

    private fun keys(vararg labels: String): List<Key> = labels.map { Key(it) }

    /** Each page under the number row: two rows of 10 characters and 7 beside the page key and ⌫. */
    private class PageContent(val row1: List<Key>, val row2: List<Key>, val row3: List<Key>)

    private fun content(id: String): PageContent = when (id) {
        // Pages 1 and 2 follow the familiar Samsung Keyboard layout.
        SYMBOLS -> PageContent(charKeys("+×÷=/_<>[]"), charKeys("!@#$%^&*()"), charKeys("-'\":;,?"))
        SYMBOLS_2 -> PageContent(charKeys("`~\\|{}€£¥₩"), charKeys("°•○●□■♤♡◇♧"), charKeys("☆▪¤《》¡¿"))
        MATH -> PageContent(charKeys("±≠≈≤≥∞√∑π∫"), charKeys("½¼¾²³‰∆µ¢₹"), keys("₦", "₵", "₽", "₺", "₿", "ብር", "℃"))
        ARROWS -> PageContent(charKeys("←→↑↓↔↕⇐⇒⇑⇓"), charKeys("★✦●◆▲▼►◄♪♫"), charKeys("♥♦♣♠✓✗※"))
        PUNCTUATION -> PageContent(charKeys("«»‹›“”‘’„‚"), charKeys("—–…·§¶†‡№™"), charKeys("©®′″‽¦¬"))
        ACCENTS -> PageContent(charKeys("áàâäãåæçéè"), charKeys("êëíìîïñóòô"), charKeys("öøœßúùü"))
        // Labialised Ethiopic letters that phonetic typing does not reach easily (ቈ ኈ ጐ ዀ ኰ…).
        ETHIOPIC_EXTRA -> PageContent(
            charKeys("ቈቊቋቌቍኈኊኋኌኍ"),
            charKeys("ጐጒጓጔጕዀዂዃዄዅ"),
            charKeys("ኰኲኳኴኵፘ") + Key("ፙ", popup = listOf(Key("ፚ"), Key("ሇ"), Key("ፙ"))),
        )
        GEEZ_NUMBERS -> GeezNumerals.keys.let { k ->
            PageContent(
                k.subList(0, 9).map { Key(it.toString()) } + Key(GeezNumerals.HUNDRED.toString()),
                k.subList(9, 18).map { Key(it.toString()) } + Key(GeezNumerals.TEN_THOUSAND.toString()),
                Ethiopic.punctuation.take(7).map { Key(it.toString()) },
            )
        }
        else -> error("not a symbol page: $id")
    }

    /**
     * A symbol page, Samsung-style; every row is 10 key-widths and every page has 5 rows:
     *   1 2 3 4 5 6 7 8 9 0
     *   10 symbols
     *   10 symbols
     *   [1/8] 7 symbols [⌫]
     *   [ABC] [አም/En] [,] [space] [.] [↵]
     * The page key steps to the next page; long-press it to jump to any page.
     */
    private fun symbolPage(id: String, langLabel: String = LANG_AMHARIC): KeyboardLayout {
        val c = content(id)
        val i = SYMBOL_PAGES.indexOf(id)
        val next = SYMBOL_PAGES[(i + 1) % SYMBOL_PAGES.size]
        val pageKey = Key(
            "${i + 1}/${SYMBOL_PAGES.size}", output = next, code = Code.PAGE, width = 1.5f,
            popup = SYMBOL_PAGES.map { Key(PAGE_ICONS.getValue(it), output = it, code = Code.PAGE) },
        )
        val digitsRow = digits.map { d ->
            Key(d.toString(), popup = listOfNotNull(GeezNumerals.toGeez(d.toString().toLong())?.let { Key(it) }))
        }
        return KeyboardLayout(
            id,
            listOf(
                digitsRow,
                c.row1,
                c.row2,
                listOf(pageKey) + c.row3 + Key("⌫", code = Code.DELETE, width = 1.5f),
                listOf(
                    Key("ABC", code = Code.ALPHA, width = 1.5f),
                    Key(langLabel, code = Code.LANGUAGE, hint = "🌐", width = 1f),
                    Key(",", popup = listOf(Key("፣"), Key("፤"), Key("፥"), Key("፦"))),
                    Key(" ", code = Code.SPACE, width = 4f),
                    Key(".", popup = listOf(Key("።"), Key("፧"), Key("፨"))),
                    Key("↵", code = Code.ENTER, width = 1.5f),
                ),
            ),
        )
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
