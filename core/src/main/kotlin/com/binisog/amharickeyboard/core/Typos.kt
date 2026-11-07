package com.binisog.amharickeyboard.core

/**
 * How "close" two letters are when one was typed instead of the other. Smaller = more
 * likely a slip, so such words rank higher among corrections.
 */
object Typos {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val neighbours: Map<Char, Set<Char>> = buildMap {
        for ((r, row) in rows.withIndex()) for ((i, c) in row.withIndex()) {
            val n = HashSet<Char>()
            row.getOrNull(i - 1)?.let(n::add); row.getOrNull(i + 1)?.let(n::add)
            // keys diagonally above/below on a staggered QWERTY layout
            rows.getOrNull(r - 1)?.let { up -> up.getOrNull(i)?.let(n::add); up.getOrNull(i + 1)?.let(n::add) }
            rows.getOrNull(r + 1)?.let { down -> down.getOrNull(i)?.let(n::add); down.getOrNull(i - 1)?.let(n::add) }
            put(c, n)
        }
    }

    /** Substitution cost of reading typed [a] where the dictionary word has [b]. */
    fun cost(a: Char, b: Char): Float {
        if (a == b) return 0f
        val la = a.lowercaseChar(); val lb = b.lowercaseChar()
        if (la == lb) return 0.1f
        if (neighbours[la]?.contains(lb) == true) return 0.6f
        val ra = Ethiopic.rowOf(a); val rb = Ethiopic.rowOf(b)
        if (ra != null && rb != null) {
            if (ra == rb) return 0.5f                          // same consonant, other vowel: ለ/ላ
            if (b in Ethiopic.homophones(a)) return 0.3f       // same sound: ሀ/ሐ, ሰ/ሠ
            if (Ethiopic.homophones(a).any { Ethiopic.rowOf(it) == rb }) return 0.6f
        }
        return 1f
    }

    /** 1.0 = identical; 0.6 = the "60 % similar" threshold. */
    fun similarity(typedLength: Int, wordLength: Int, cost: Float): Float =
        1f - cost / maxOf(typedLength, wordLength, 1)
}
