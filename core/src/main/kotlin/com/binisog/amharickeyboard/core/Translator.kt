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
    /** Multi-word English meanings ("thank you", "good morning") → Amharic. */
    private val toAmPhrase = HashMap<String, MutableList<String>>()

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
            if (' ' in key) toAmPhrase.getOrPut(key) { ArrayList() }.let { if (amharic !in it) it.add(amharic) }
            for (k in listOf(key, key.removePrefix("to ").takeIf { key.startsWith("to ") })) {
                if (k == null || ' ' in k) continue // single English words are looked up here
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

    /**
     * Translates a whole text (a selection or sentence) into the other language. Known
     * phrases are matched first (longest first, up to [MAX_PHRASE] words), then single words;
     * unknown words are kept as typed. This is word-by-word: word order is not rearranged.
     */
    fun translateText(text: String): String {
        val toEnglish = text.any(Ethiopic::isEthiopic)
        val tokens = TOKEN.findAll(text).map { it.value }.toList()
        val out = StringBuilder()
        var i = 0
        fun isWord(t: String) = t.any { it.isLetterOrDigit() || Ethiopic.isWordChar(it) }
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.isBlank()) { i++; continue }
            if (!isWord(t)) {
                out.append(if (toEnglish) PUNCT_TO_EN[t] ?: t else PUNCT_TO_AM[t] ?: t)
                i++
                continue
            }
            // collect up to MAX_PHRASE consecutive words (separated by single spaces only)
            val words = ArrayList<Int>()
            var j = i
            while (j < tokens.size && words.size < MAX_PHRASE) {
                if (isWord(tokens[j])) { words.add(j); j++ } else if (tokens[j].isBlank()) j++ else break
            }
            var consumed = 1
            var translated: String? = null
            for (n in words.size downTo 2) {
                val phrase = words.take(n).joinToString(" ") { tokens[it] }
                val hit = if (toEnglish) toEn[phrase]?.firstOrNull() else toAmPhrase[phrase.lowercase()]?.firstOrNull()
                if (hit != null) { translated = hit; consumed = n; break }
            }
            if (translated == null) translated = translate(t, 1).firstOrNull() ?: t
            if (out.isNotEmpty() && !out.endsWith(" ") && !out.endsWith("(") && !out.endsWith("\"")) out.append(' ')
            out.append(translated)
            i = words[consumed - 1] + 1
        }
        val result = out.toString().replace(Regex(" +([.,!?:;።፣፤፥፧)])"), "$1").trim()
        return if (toEnglish) result.replaceFirstChar { it.uppercaseChar() } else result
    }

    private fun pluralize(g: String): String = when {
        ' ' in g -> g
        g.endsWith("s") || g.endsWith("x") || g.endsWith("ch") || g.endsWith("sh") -> g + "es"
        g.endsWith("y") && g.length > 2 && g[g.length - 2] !in "aeiou" -> g.dropLast(1) + "ies"
        else -> g + "s"
    }

    companion object {
        private const val MAX_PHRASE = 4
        private val TOKEN = Regex("[\\p{L}\\p{M}\\p{N}'’]+|\\s+|[^\\p{L}\\p{M}\\p{N}\\s]")
        private val PUNCT_TO_EN = mapOf("።" to ".", "፣" to ",", "፤" to ";", "፥" to ":", "፧" to "?", "፡" to " ")
        private val PUNCT_TO_AM = mapOf("." to "።", "," to "፣", ";" to "፤")
        private val PREFIX_EN = mapOf("የ" to "of", "በ" to "in", "ለ" to "for", "ከ" to "from")
        /** Amharic suffix → whether it marks a plural. */
        private val AM_SUFFIXES = listOf("" to false, "ዎች" to true, "ን" to false, "ም" to false, "ው" to false)
        private val EN_SUFFIXES = listOf(
            "ies" to "y", "es" to "", "s" to "", "ed" to "", "ed" to "e", "ing" to "", "ing" to "e",
        )
    }
}
