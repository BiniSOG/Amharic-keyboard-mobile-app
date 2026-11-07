package com.binisog.amharickeyboard.core

/**
 * Converts Arabic numerals to Ge'ez (Ethiopic) numerals, e.g. 2026 → ፳፻፳፮.
 *
 * Ge'ez numerals have no zero and no place value; numbers are built from units
 * (፩–፱), tens (፲–፺), hundred (፻) and ten-thousand (፼), recursively:
 *   n = a·10000 + b  →  geez(a)፼ geez(b)    (a "1" before ፼/፻ is omitted)
 */
object GeezNumerals {
    private const val ONES = 0x1369   // ፩
    private const val TENS = 0x1372   // ፲
    const val HUNDRED = '፻'
    const val TEN_THOUSAND = '፼'

    fun toGeez(n: Long): String? {
        if (n <= 0) return null
        return convert(n)
    }

    fun toGeez(digits: String): String? {
        if (digits.isEmpty() || digits.length > 18 || !digits.all { it in '0'..'9' }) return null
        return toGeez(digits.toLong())
    }

    private fun convert(n: Long): String {
        if (n >= 10_000) {
            val a = n / 10_000
            val b = n % 10_000
            val head = if (a == 1L) "" else convert(a)
            return head + TEN_THOUSAND + (if (b > 0) below10k(b.toInt()) else "")
        }
        return below10k(n.toInt())
    }

    private fun below10k(n: Int): String {
        if (n >= 100) {
            val c = n / 100
            val d = n % 100
            val head = if (c == 1) "" else pair(c)
            return head + HUNDRED + pair(d)
        }
        return pair(n)
    }

    private fun pair(n: Int): String {
        val sb = StringBuilder(2)
        val t = n / 10
        val o = n % 10
        if (t > 0) sb.append((TENS + t - 1).toChar())
        if (o > 0) sb.append((ONES + o - 1).toChar())
        return sb.toString()
    }

    /** Units, tens and the two multipliers, in keyboard order. */
    val keys: List<Char> = (0 until 9).map { (ONES + it).toChar() } + (0 until 9).map { (TENS + it).toChar() } + listOf(HUNDRED, TEN_THOUSAND)
}
