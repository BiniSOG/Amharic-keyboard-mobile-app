package com.binisog.amharickeyboard.core

/**
 * Model of the Ethiopic (Ge'ez / Fidel) syllabary as used for Amharic.
 *
 * Each consonant "row" in Unicode occupies 8 consecutive code points:
 *   offset 0: 1st order (ä)   ለ
 *   offset 1: 2nd order (u)   ሉ
 *   offset 2: 3rd order (i)   ሊ
 *   offset 3: 4th order (a)   ላ
 *   offset 4: 5th order (e)   ሌ
 *   offset 5: 6th order (ə / bare consonant)  ል
 *   offset 6: 7th order (o)   ሎ
 *   offset 7: labialised (wa) ሏ  - only for some rows
 */
object Ethiopic {

    /** Vowel orders in the order they are shown in the vowel strip / popups. */
    enum class Order(val offset: Int, val latin: String) {
        FIRST(0, "e"), SECOND(1, "u"), THIRD(2, "i"), FOURTH(3, "a"),
        FIFTH(4, "E"), SIXTH(5, ""), SEVENTH(6, "o")
    }

    /**
     * @param key primary latin key used by the phonetic layout
     * @param aliases additional latin spellings of the same consonant
     * @param base code point of the 1st order form
     * @param wa code point of the labialised "-wa" form, or null when the row has none
     */
    data class Row(
        val key: String,
        val aliases: List<String>,
        val base: Int,
        val wa: Int?,
        val name: String,
    ) {
        fun form(order: Order): Char = (base + order.offset).toChar()
        val forms: List<Char> get() = Order.entries.map { form(it) }
        /** All forms including the labialised one; what a fidel-mode vowel strip shows. */
        val allForms: List<Char> get() = forms + listOfNotNull(wa?.toChar())
        val bare: Char get() = form(Order.SIXTH)
        val first: Char get() = form(Order.FIRST)
    }

    val rows: List<Row> = listOf(
        Row("h", emptyList(), 0x1200, null, "ሀ"),
        Row("l", emptyList(), 0x1208, 0x120F, "ለ"),
        Row("H", listOf("hh"), 0x1210, 0x1217, "ሐ"),
        Row("m", emptyList(), 0x1218, 0x121F, "መ"),
        Row("`s", listOf("sz"), 0x1220, 0x1227, "ሠ"),
        Row("r", emptyList(), 0x1228, 0x122F, "ረ"),
        Row("s", emptyList(), 0x1230, 0x1237, "ሰ"),
        Row("x", listOf("sh"), 0x1238, 0x123F, "ሸ"),
        Row("q", emptyList(), 0x1240, 0x124B, "ቀ"),
        Row("Q", emptyList(), 0x1250, 0x125B, "ቐ"),
        Row("b", emptyList(), 0x1260, 0x1267, "በ"),
        Row("v", emptyList(), 0x1268, 0x126F, "ቨ"),
        Row("t", emptyList(), 0x1270, 0x1277, "ተ"),
        Row("c", listOf("ch"), 0x1278, 0x127F, "ቸ"),
        Row("`h", emptyList(), 0x1280, 0x128B, "ኀ"),
        Row("n", emptyList(), 0x1290, 0x1297, "ነ"),
        Row("N", listOf("ny"), 0x1298, 0x129F, "ኘ"),
        Row("", emptyList(), 0x12A0, null, "አ"),
        Row("k", emptyList(), 0x12A8, 0x12B3, "ከ"),
        Row("K", listOf("kh"), 0x12B8, 0x12C3, "ኸ"),
        Row("w", emptyList(), 0x12C8, null, "ወ"),
        Row("`", emptyList(), 0x12D0, null, "ዐ"),
        Row("z", emptyList(), 0x12D8, 0x12DF, "ዘ"),
        Row("Z", listOf("zh"), 0x12E0, 0x12E7, "ዠ"),
        Row("y", emptyList(), 0x12E8, null, "የ"),
        Row("d", emptyList(), 0x12F0, 0x12F7, "ደ"),
        Row("j", emptyList(), 0x1300, 0x1307, "ጀ"),
        Row("g", emptyList(), 0x1308, 0x1313, "ገ"),
        Row("T", emptyList(), 0x1320, 0x1327, "ጠ"),
        Row("C", emptyList(), 0x1328, 0x132F, "ጨ"),
        Row("P", listOf("ph"), 0x1330, 0x1337, "ጰ"),
        Row("S", listOf("ts"), 0x1338, 0x133F, "ጸ"),
        Row("`S", listOf("tz"), 0x1340, null, "ፀ"),
        Row("f", emptyList(), 0x1348, 0x134F, "ፈ"),
        Row("p", emptyList(), 0x1350, 0x1357, "ፐ"),
    )

    /** The vowel-carrier row (አ ኡ ኢ ኣ ኤ እ ኦ). */
    val vowelRow: Row = rows.first { it.base == 0x12A0 }

    private val rowByCodePoint: Map<Int, Row> = buildMap {
        for (row in rows) {
            for (i in 0 until 7) put(row.base + i, row)
            row.wa?.let { put(it, row) }
        }
    }

    fun isEthiopic(c: Char): Boolean = c.code in 0x1200..0x137F || c.code in 0x2D80..0x2DDF || c.code in 0xAB00..0xAB2F

    fun isEthiopicLetter(c: Char): Boolean = c.code in 0x1200..0x135A || c.code in 0x2D80..0x2DDF || c.code in 0xAB00..0xAB2F

    /** Ethiopic word characters: letters plus combining marks. */
    fun isWordChar(c: Char): Boolean = isEthiopicLetter(c) || c.code in 0x135D..0x135F

    fun rowOf(c: Char): Row? = rowByCodePoint[c.code]

    fun orderOf(c: Char): Order? {
        val row = rowOf(c) ?: return null
        val off = c.code - row.base
        return Order.entries.firstOrNull { it.offset == off }
    }

    /** Every form of the row [c] belongs to, or just [c] itself when it is not a known syllable. */
    fun rowVariants(c: Char): List<Char> = rowOf(c)?.allForms ?: listOf(c)

    /**
     * Homophone groups: letters that are pronounced the same in modern Amharic and are
     * frequently interchanged in writing (ሀ/ሐ/ኀ, ሰ/ሠ, ጸ/ፀ, አ/ዐ). Used for fuzzy matching so
     * that "ሰላም" is found when typing "ሠላ", etc.
     */
    private val homophoneRowGroups: List<List<Int>> = listOf(
        listOf(0x1200, 0x1210, 0x1280),
        listOf(0x1230, 0x1220),
        listOf(0x1338, 0x1340),
        listOf(0x12A0, 0x12D0),
    )

    private val homophoneMap: Map<Char, List<Char>> = buildMap {
        for (group in homophoneRowGroups) {
            val groupRows = group.map { b -> rows.first { it.base == b } }
            for (order in Order.entries) {
                val chars = groupRows.map { it.form(order) }
                for (c in chars) put(c, chars.filter { it != c })
            }
            val waChars = groupRows.mapNotNull { it.wa?.toChar() }
            for (c in waChars) put(c, waChars.filter { it != c })
        }
    }

    /** Alternative spellings of [c] that sound the same (excluding [c]). */
    fun homophones(c: Char): List<Char> = homophoneMap[c] ?: emptyList()

    /** Normalises homophone letters to a canonical form (ሐ,ኀ→ሀ; ሠ→ሰ; ፀ→ጸ; ዐ→አ). */
    fun normalize(word: String): String = buildString(word.length) {
        for (c in word) {
            append(if (c in homophoneMap) canonicalOf(c) else c)
        }
    }

    private fun canonicalOf(c: Char): Char {
        val row = rowOf(c) ?: return c
        val group = homophoneRowGroups.firstOrNull { row.base in it } ?: return c
        val canonicalRow = rows.first { it.base == group.first() }
        if (row.wa != null && c.code == row.wa) return canonicalRow.wa?.toChar() ?: c
        return (canonicalRow.base + (c.code - row.base)).toChar()
    }

    // ---- punctuation ----
    const val WORDSPACE = '፡'
    const val FULL_STOP = '።'
    const val COMMA = '፣'
    const val SEMICOLON = '፤'
    const val COLON = '፥'
    const val PREFACE_COLON = '፦'
    const val QUESTION = '፧'
    const val PARAGRAPH = '፨'

    val punctuation: List<Char> = listOf(FULL_STOP, COMMA, SEMICOLON, COLON, PREFACE_COLON, QUESTION, WORDSPACE, PARAGRAPH)

    fun isSentenceEnd(c: Char): Boolean = c == FULL_STOP || c == QUESTION || c == '.' || c == '?' || c == '!'
}
