package com.binisog.amharickeyboard.core

/**
 * Latin → Fidel phonetic transliteration table (SERA-inspired, the scheme most Amharic
 * typists already know).
 *
 * | typed  | result | typed | result | typed | result |
 * |--------|--------|-------|--------|-------|--------|
 * | l      | ል      | le    | ለ      | lu    | ሉ      |
 * | li     | ሊ      | la    | ላ      | lE/lie| ሌ      |
 * | lo     | ሎ      | lua   | ሏ      | sh/x  | ሽ      |
 * | ch/c   | ች      | ny/N  | ኝ      | ts/S  | ጽ      |
 * | a      | አ      | e     | እ      | i     | ኢ      |
 * | aa     | ኣ      | E     | ኤ      | o     | ኦ      |
 * | A/`aa  | ዓ      | `s    | ሠ      | `h    | ኀ      |
 *
 * An apostrophe breaks a syllable: `l'e` → ልእ.
 *
 * Every prefix of every key is itself a key (prefix-closed), which is what lets
 * [PhoneticComposer] work greedily one keystroke at a time.
 */
class TransliterationTable private constructor(private val map: Map<String, String>) {

    operator fun get(latin: String): String? = map[latin]
    operator fun contains(latin: String): Boolean = latin in map
    val size: Int get() = map.size
    val entries: Map<String, String> get() = map

    /** Reverse lookup used for key hints: fidel character → shortest latin spelling. */
    val reverse: Map<Char, String> by lazy {
        val r = HashMap<Char, String>()
        for ((k, v) in map) {
            if (v.length != 1) continue
            val existing = r[v[0]]
            if (existing == null || k.length < existing.length) r[v[0]] = k
        }
        r
    }

    companion object {
        val DEFAULT: TransliterationTable by lazy { build() }

        fun build(): TransliterationTable {
            val m = LinkedHashMap<String, String>()
            fun put(k: String, c: Char) { m.putIfAbsent(k, c.toString()) }

            for (row in Ethiopic.rows) {
                when (row.base) {
                    0x12A0 -> vowelRowEntries(prefix = "", row = row, put = ::put)
                    0x12D0 -> vowelRowEntries(prefix = "`", row = row, put = ::put)
                    else -> for (key in listOf(row.key) + row.aliases) consonantEntries(key, row, ::put)
                }
            }
            // Capital vowels give the pharyngeal ዐ row, e.g. "Amet" → ዓመት.
            val ayn = Ethiopic.rows.first { it.base == 0x12D0 }
            put("A", ayn.form(Ethiopic.Order.FOURTH))
            put("U", ayn.form(Ethiopic.Order.SECOND))
            put("I", ayn.form(Ethiopic.Order.THIRD))
            put("O", ayn.form(Ethiopic.Order.SEVENTH))
            // Bare backtick is a prefix for the alternative letters (`s ሠ, `h ኀ, `S ፀ, `a ዐ).
            m.putIfAbsent("`", "`")

            // Ensure prefix closure: any missing intermediate prefix maps to the
            // transliteration of its longest valid prefix plus the remaining characters.
            for (key in m.keys.toList()) {
                for (i in 1 until key.length) {
                    val p = key.substring(0, i)
                    if (p !in m) m[p] = fallback(p, m)
                }
            }
            return TransliterationTable(m)
        }

        private fun fallback(p: String, m: Map<String, String>): String {
            for (i in p.length - 1 downTo 1) {
                val head = m[p.substring(0, i)] ?: continue
                return head + p.substring(i)
            }
            return p
        }

        private fun consonantEntries(key: String, row: Ethiopic.Row, put: (String, Char) -> Unit) {
            put(key, row.form(Ethiopic.Order.SIXTH))
            put(key + "e", row.form(Ethiopic.Order.FIRST))
            put(key + "u", row.form(Ethiopic.Order.SECOND))
            put(key + "i", row.form(Ethiopic.Order.THIRD))
            put(key + "a", row.form(Ethiopic.Order.FOURTH))
            put(key + "E", row.form(Ethiopic.Order.FIFTH))
            put(key + "ie", row.form(Ethiopic.Order.FIFTH))
            put(key + "ee", row.form(Ethiopic.Order.FIFTH))
            put(key + "o", row.form(Ethiopic.Order.SEVENTH))
            row.wa?.let { put(key + "ua", it.toChar()) }
        }

        private fun vowelRowEntries(prefix: String, row: Ethiopic.Row, put: (String, Char) -> Unit) {
            put(prefix + "a", row.form(Ethiopic.Order.FIRST))
            put(prefix + "u", row.form(Ethiopic.Order.SECOND))
            put(prefix + "i", row.form(Ethiopic.Order.THIRD))
            put(prefix + "aa", row.form(Ethiopic.Order.FOURTH))
            put(prefix + "E", row.form(Ethiopic.Order.FIFTH))
            put(prefix + "ie", row.form(Ethiopic.Order.FIFTH))
            put(prefix + "ee", row.form(Ethiopic.Order.FIFTH))
            put(prefix + "e", row.form(Ethiopic.Order.SIXTH))
            put(prefix + "o", row.form(Ethiopic.Order.SEVENTH))
        }
    }
}

/**
 * Stateful composer for the word currently being typed. Converts latin keystrokes into
 * Fidel, one keystroke at a time, by greedily extending the current syllable.
 *
 * Backspace is "undo-aware": deleting right after `la` (ላ) goes back to `l` (ል) rather than
 * removing the whole syllable, which is how experienced phonetic typists expect it to work.
 */
class PhoneticComposer(private val table: TransliterationTable = TransliterationTable.DEFAULT) {

    private data class Snapshot(val text: String, val syllable: String, val syllableOut: Int)

    private val history = ArrayDeque<Snapshot>()
    private val sb = StringBuilder()
    private var syllable = ""
    private var syllableOut = 0

    /** Current composed word in Fidel. */
    val text: String get() = sb.toString()


    val isEmpty: Boolean get() = sb.isEmpty()

    /** Whether the last syllable can still change with the next keystroke. */
    val hasOpenSyllable: Boolean get() = syllable.isNotEmpty()

    fun type(c: Char) {
        history.addLast(Snapshot(sb.toString(), syllable, syllableOut))
        if (history.size > MAX_HISTORY) history.removeFirst()

        if (c == '\'') {
            if (syllable.isNotEmpty()) {
                syllable = ""; syllableOut = 0
            } else {
                sb.append(c)
            }
            return
        }

        val candidate = syllable + c
        val extended = table[candidate]
        if (syllable.isNotEmpty() && extended != null) {
            sb.setLength(sb.length - syllableOut)
            sb.append(extended)
            syllable = candidate
            syllableOut = extended.length
            return
        }
        val fresh = table[c.toString()]
        if (fresh != null) {
            sb.append(fresh)
            syllable = c.toString()
            syllableOut = fresh.length
        } else {
            sb.append(c)
            syllable = ""
            syllableOut = 0
        }
    }

    fun typeAll(s: String) { for (c in s) type(c) }

    /** @return false when there was nothing to delete. */
    fun backspace(): Boolean {
        if (history.isNotEmpty()) {
            val s = history.removeLast()
            sb.setLength(0); sb.append(s.text)
            syllable = s.syllable; syllableOut = s.syllableOut
            return true
        }
        if (sb.isEmpty()) return false
        sb.setLength(sb.length - 1)
        syllable = ""; syllableOut = 0
        return true
    }

    /** Replaces the last character (used by the Fidel layout's vowel strip). */
    fun replaceLast(c: Char) {
        history.addLast(Snapshot(sb.toString(), syllable, syllableOut))
        if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
        sb.append(c)
        syllable = ""; syllableOut = 0
    }

    /** Appends a literal character (Fidel layout keys, numbers...). */
    fun appendLiteral(s: String) {
        history.addLast(Snapshot(sb.toString(), syllable, syllableOut))
        sb.append(s)
        syllable = ""; syllableOut = 0
    }

    /**
     * Starts composing on an existing word (e.g. after deleting back into it). The last
     * Fidel letter's syllable is restored, so typing continues naturally: ሰል + `a` → ሰላ.
     */
    fun setWord(word: String) {
        reset()
        sb.append(word)
        val last = word.lastOrNull() ?: return
        val latin = table.reverse[last] ?: return
        if (latin.isNotEmpty() && latin.all { it.isLetter() || it == '`' }) {
            syllable = latin
            syllableOut = 1
        }
    }

    fun reset() {
        history.clear()
        sb.setLength(0)
        syllable = ""; syllableOut = 0
    }

    companion object {
        private const val MAX_HISTORY = 64

        /** Convenience: transliterate a whole latin string, keeping non-letters as-is. */
        fun transliterate(latin: String, table: TransliterationTable = TransliterationTable.DEFAULT): String {
            val out = StringBuilder()
            val c = PhoneticComposer(table)
            for (ch in latin) {
                if (ch.isLetter() || ch == '\'' || ch == '`') {
                    c.type(ch)
                } else {
                    out.append(c.text).append(ch); c.reset()
                }
            }
            return out.append(c.text).toString()
        }
    }
}
