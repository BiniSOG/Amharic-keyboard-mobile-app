package com.binisog.amharickeyboard.core

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Offline word-level English ↔ Amharic translation for suggestions.
 *
 * Data format (one entry per line): `amharic<TAB>english1|english2|…`, primary meaning
 * first. The Amharic side may be a short phrase (ጤና ይስጥልኝ). English glosses like
 * "to go" are also indexed under "go".
 *
 * Amharic words are often built from a stem plus a prefix (የ of, በ in, ለ for, ከ from) and
 * suffixes (-ዎች plural, -ን object, -ም also/too, -ው the), so unknown words are retried
 * with those removed: በኢትዮጵያ → "in Ethiopia", መኪናዎች → "cars".
 */
class Translator {
    private val toEn = HashMap<String, MutableList<String>>()
    private val toAm = LinkedHashMap<String, MutableList<String>>()

    val size: Int get() = toEn.size

    fun load(input: InputStream) {
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                add(line.substring(0, tab).trim(), line.substring(tab + 1).split('|').map { it.trim() }.filter { it.isNotEmpty() })
            }
        }
    }

    fun add(amharic: String, english: List<String>) {
        if (amharic.isEmpty() || english.isEmpty()) return
        val list = toEn.getOrPut(amharic) { ArrayList() }
        for (e in english) if (e !in list) list.add(e)
        for (e in english) {
            val key = e.lowercase()
            for (k in listOf(key, key.removePrefix("to ").takeIf { key.startsWith("to ") })) {
                if (k == null || ' ' in k) continue // only single English words can be looked up
                val am = toAm.getOrPut(k) { ArrayList() }
                if (amharic !in am) am.add(amharic)
            }
        }
    }

    /** Translations of [word] into the other language (Ethiopic → English, otherwise → Amharic). */
    fun translate(word: String, limit: Int = 2): List<String> =
        if (word.any(Ethiopic::isEthiopic)) toEnglish(word, limit) else toAmharic(word, limit)

    fun toAmharic(english: String, limit: Int = 2): List<String> {
        val w = english.trim().lowercase()
        if (w.isEmpty()) return emptyList()
        toAm[w]?.let { return it.take(limit) }
        // simple English inflections: cars → car, wanted → want, going → go
        for ((suffix, repl) in EN_SUFFIXES) {
            if (w.length > suffix.length + 2 && w.endsWith(suffix)) {
                toAm[w.dropLast(suffix.length) + repl]?.let { return it.take(limit) }
            }
        }
        return emptyList()
    }

    fun toEnglish(amharic: String, limit: Int = 2): List<String> {
        val w = amharic.trim()
        if (w.isEmpty()) return emptyList()
        toEn[w]?.let { return it.take(limit) }
        for (prefix in listOf("", "የ", "በ", "ለ", "ከ")) {
            if (prefix.isNotEmpty() && (!w.startsWith(prefix) || w.length < prefix.length + 2)) continue
            val afterPrefix = w.removePrefix(prefix)
            for ((suffix, plural) in AM_SUFFIXES) {
                if (prefix.isEmpty() && suffix.isEmpty()) continue
                if (suffix.isNotEmpty() && (!afterPrefix.endsWith(suffix) || afterPrefix.length < suffix.length + 2)) continue
                val stem = afterPrefix.removeSuffix(suffix)
                val glosses = toEn[stem] ?: continue
                return glosses.take(limit).map { g ->
                    val noun = if (plural) pluralize(g) else g
                    PREFIX_EN[prefix]?.let { "$it $noun" } ?: noun
                }
            }
        }
        return emptyList()
    }

    private fun pluralize(g: String): String = when {
        ' ' in g -> g
        g.endsWith("s") || g.endsWith("x") || g.endsWith("ch") || g.endsWith("sh") -> g + "es"
        g.endsWith("y") && g.length > 2 && g[g.length - 2] !in "aeiou" -> g.dropLast(1) + "ies"
        else -> g + "s"
    }

    companion object {
        private val PREFIX_EN = mapOf("የ" to "of", "በ" to "in", "ለ" to "for", "ከ" to "from")
        /** Amharic suffix → whether it marks a plural. */
        private val AM_SUFFIXES = listOf("" to false, "ዎች" to true, "ን" to false, "ም" to false, "ው" to false)
        private val EN_SUFFIXES = listOf(
            "ies" to "y", "es" to "", "s" to "", "ed" to "", "ed" to "e", "ing" to "", "ing" to "e",
        )
    }
}
