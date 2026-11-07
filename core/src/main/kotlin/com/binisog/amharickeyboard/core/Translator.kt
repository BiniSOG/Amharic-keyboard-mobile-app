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

    /** A translated piece of text plus what it is, for the grammar step. */
    internal enum class Kind { WORD, PUNCT, SUBJ_VERB, COPULA, BE, INTENSIFIER, QUESTION, PRONOUN, PREP }
    internal class Seg(var out: String, val kind: Kind, val src: String = "")

    /**
     * Translates a whole text (a selection or sentence) into the other language.
     *
     * 1. Known phrases are matched first (longest first, up to [MAX_PHRASE] words), then
     *    single words; unknown words are kept as typed.
     * 2. A grammar step rearranges each clause for the target language: Amharic puts the
     *    verb last (SOV) and "to be" at the end (ደህና ነኝ = I am fine); prepositions become
     *    Amharic prefixes/postpositions (in Addis → በአዲስ, on the table → ጠረጴዛ ላይ).
     *
     * These are rules for common sentence shapes, not full machine translation.
     */
    fun translateText(text: String): String {
        val toEnglish = text.any(Ethiopic::isEthiopic)
        val tokens = TOKEN.findAll(text).map { it.value }.filter { it.isNotBlank() }.toList()
        val segs = if (toEnglish) segmentAmharic(tokens) else segmentEnglish(tokens)
        val clauses = splitClauses(segs).map { if (toEnglish) Grammar.toEnglish(it) else Grammar.toAmharic(it) }
        val out = StringBuilder()
        for (c in clauses) for (seg in c) {
            if (seg.out.isEmpty()) continue
            if (seg.kind == Kind.PUNCT) { out.append(seg.out); continue }
            if (out.isNotEmpty() && !out.endsWith(" ") && !out.endsWith("(") && !out.endsWith("\"")) out.append(' ')
            out.append(seg.out)
        }
        var result = out.toString().replace(Regex(" +"), " ").trim()
        if (toEnglish) {
            result = result.replace(Regex("\\bi\\b"), "I")
            result = SENTENCE_START.replace(result) { it.value.uppercase() }
        }
        return result
    }

    private fun isWordToken(t: String) = t.any { it.isLetterOrDigit() || Ethiopic.isWordChar(it) }

    private fun splitClauses(segs: List<Seg>): List<MutableList<Seg>> {
        val out = ArrayList<MutableList<Seg>>()
        var cur = ArrayList<Seg>()
        for (s in segs) {
            cur.add(s)
            if (s.kind == Kind.PUNCT) { out.add(cur); cur = ArrayList() }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    /** Longest known phrase starting at word [i]; returns (translation, words consumed, matched key). */
    private fun matchPhrase(words: List<String>, i: Int, toEnglish: Boolean): Triple<String, Int, String>? {
        for (n in minOf(MAX_PHRASE, words.size - i) downTo 2) {
            val phrase = words.subList(i, i + n).joinToString(" ")
            val hit = if (toEnglish) toEn[phrase]?.firstOrNull() else toAmPhrase[phrase.lowercase()]?.firstOrNull()
            if (hit != null) return Triple(hit, n, phrase.lowercase())
        }
        return null
    }

    private fun segmentAmharic(tokens: List<String>): List<Seg> {
        val segs = ArrayList<Seg>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (!isWordToken(t)) { segs.add(Seg(PUNCT_TO_EN[t] ?: t, Kind.PUNCT)); i++; continue }
            // phrase over the following run of words
            var j = i
            while (j < tokens.size && isWordToken(tokens[j])) j++
            val run = tokens.subList(i, j)
            val phrase = matchPhrase(run, 0, toEnglish = true)
            if (phrase != null) {
                segs.add(Seg(phrase.first, classifyEnglish(phrase.first), run.take(phrase.second).joinToString(" ")))
                i += phrase.second
                continue
            }
            val copula = AM_COPULA[t]
            when {
                copula != null -> segs.add(Seg(copula.first, Kind.COPULA, t))
                t == "በጣም" -> segs.add(Seg("very", Kind.INTENSIFIER, t))
                else -> {
                    val en = translate(t, 1).firstOrNull() ?: t
                    segs.add(Seg(en, classifyEnglish(en), t))
                }
            }
            i++
        }
        return segs
    }

    private fun classifyEnglish(en: String): Kind {
        val l = en.lowercase()
        return when {
            SUBJECTS.any { l.startsWith("$it ") } || l.startsWith("thank") || l.startsWith("let's ") -> Kind.SUBJ_VERB
            l in PRONOUNS -> Kind.PRONOUN
            l in QUESTION_WORDS -> Kind.QUESTION
            else -> Kind.WORD
        }
    }

    private fun segmentEnglish(tokens: List<String>): List<Seg> {
        val segs = ArrayList<Seg>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (!isWordToken(t)) { segs.add(Seg(PUNCT_TO_AM[t] ?: t, Kind.PUNCT)); i++; continue }
            var j = i
            while (j < tokens.size && isWordToken(tokens[j])) j++
            val run = tokens.subList(i, j)
            val l = t.lowercase()
            // "very much" → በጣም
            if (l == "very" && run.getOrNull(1)?.lowercase() == "much") { segs.add(Seg("በጣም", Kind.INTENSIFIER, "very much")); i += 2; continue }
            val phrase = matchPhrase(run, 0, toEnglish = false)
            if (phrase != null) {
                val key = phrase.third
                val kind = if (SUBJECTS.any { key.startsWith("$it ") } || key.startsWith("thank") || key.startsWith("let's")) Kind.SUBJ_VERB else Kind.WORD
                segs.add(Seg(phrase.first, kind, key))
                i += phrase.second
                continue
            }
            when {
                l in ARTICLES -> Unit // Amharic has no separate articles
                l in DO_AUX && run.getOrNull(1)?.lowercase() in SUBJECTS -> Unit // "do you …?" question auxiliary
                l in BE_FORMS -> segs.add(Seg(l, Kind.BE, l))
                l in EN_PREPOSITIONS -> segs.add(Seg(l, Kind.PREP, l))
                l in SUBJECTS -> segs.add(Seg(EN_PRONOUN_AM[l] ?: (translate(t, 1).firstOrNull() ?: t), Kind.PRONOUN, l))
                l == "very" -> segs.add(Seg("በጣም", Kind.INTENSIFIER, l))
                else -> segs.add(Seg(translate(t, 1).firstOrNull() ?: t, Kind.WORD, l))
            }
            i++
        }
        return segs
    }

    /** Clause-level reordering rules. */
    internal object Grammar {

        fun toEnglish(clause: MutableList<Seg>): List<Seg> {
            val punct = clause.lastOrNull()?.takeIf { it.kind == Kind.PUNCT }
            val c = clause.filter { it.kind != Kind.PUNCT }.toMutableList()
            // በጣም + verb: "very thank you" → "thank you very much"
            var k = 0
            while (k < c.size - 1) {
                if (c[k].kind == Kind.INTENSIFIER && c[k + 1].kind == Kind.SUBJ_VERB) {
                    c[k + 1].out = c[k + 1].out + " very much"
                    c.removeAt(k)
                } else k++
            }
            val last = c.lastOrNull()
            val result: List<Seg> = when {
                last == null -> c
                // SOV → SVO: "ቡና እፈልጋለሁ" (coffee I-want) → "I want coffee"
                last.kind == Kind.SUBJ_VERB && c.size > 1 -> listOf(last) + addArticles(c.dropLast(1))
                last.kind == Kind.COPULA -> {
                    val be = last.out
                    val rest = c.dropLast(1)
                    val q = rest.firstOrNull { it.kind == Kind.QUESTION }
                    val pronoun = Seg(AM_COPULA[last.src]?.second ?: "it", Kind.PRONOUN)
                    when {
                        q != null -> {
                            val others = rest - q
                            listOf(q, Seg(be, Kind.WORD)) + (if (others.isEmpty()) listOf(pronoun) else others)
                        }
                        rest.isEmpty() -> listOf(pronoun, Seg(be, Kind.WORD))
                        rest.first().kind == Kind.PRONOUN -> listOf(rest.first(), Seg(be, Kind.WORD)) + rest.drop(1)
                        rest.size >= 2 -> listOf(rest.first(), Seg(be, Kind.WORD)) + rest.drop(1)
                        else -> listOf(pronoun, Seg(be, Kind.WORD)) + rest
                    }
                }
                else -> c
            }
            return if (punct != null) result + punct else result
        }

        /** "to market" → "to the market" after prepositions (not before names or possessives). */
        private fun addArticles(segs: List<Seg>): List<Seg> {
            val out = ArrayList<Seg>()
            for ((i, s) in segs.withIndex()) {
                out.add(s)
                val next = segs.getOrNull(i + 1) ?: continue
                val w = next.out
                if (s.kind == Kind.WORD && s.out.lowercase() in setOf("to", "in", "from", "for", "at", "of") &&
                    next.kind == Kind.WORD && w.firstOrNull()?.isLowerCase() == true &&
                    w.split(' ').first() !in DETERMINERS
                ) out.add(Seg("the", Kind.WORD))
            }
            return out
        }

        fun toAmharic(clause: MutableList<Seg>): List<Seg> {
            val punct = clause.lastOrNull()?.takeIf { it.kind == Kind.PUNCT }
            var c = clause.filter { it.kind != Kind.PUNCT }.toMutableList()

            // prepositions bind to the following word: in X → በX, on X → X ላይ, with X → ከX ጋር
            val bound = ArrayList<Seg>()
            var i = 0
            while (i < c.size) {
                val s = c[i]
                val next = c.getOrNull(i + 1)
                if (s.kind == Kind.PREP && next != null && next.kind != Kind.PREP && next.kind != Kind.BE) {
                    val w = next.out
                    next.out = when (s.src) {
                        "in", "at", "by" -> attach("በ", w)
                        "from" -> attach("ከ", w)
                        "for" -> attach("ለ", w)
                        "to", "towards" -> "ወደ $w"
                        "with" -> attach("ከ", w) + " ጋር"
                        "about" -> "ስለ $w"
                        "on" -> "$w ላይ"
                        "under" -> "$w ስር"
                        "inside" -> "$w ውስጥ"
                        else -> w
                    }
                    i++
                    continue
                }
                if (s.kind != Kind.PREP) bound.add(s)
                i++
            }
            c = bound

            // "to be": subject + am/is/are + predicate → predicate + ነኝ/ነው/…
            val b = c.indexOfFirst { it.kind == Kind.BE }
            if (b >= 0) {
                val be = c[b].src
                val subject = c.subList(0, b).toList()
                val predicate = c.subList(b + 1, c.size).toList()
                val pron = subject.singleOrNull()?.takeIf { it.kind == Kind.PRONOUN }?.src
                val copula = when {
                    pron != null -> COPULA_BY_PRONOUN[pron] ?: "ነው"
                    be == "was" || be == "were" -> "ነበር"
                    be == "are" -> "ናቸው"
                    else -> "ነው"
                }
                val keepSubject = if (pron != null) emptyList() else subject
                val q = predicate.filter { it.kind == Kind.QUESTION }
                val result = keepSubject + q + (predicate - q.toSet()) + Seg(copula, Kind.WORD)
                return if (punct != null) result + punct else result
            }

            // SVO → SOV: the verb (with its subject built in) goes last
            val v = c.indexOfFirst { it.kind == Kind.SUBJ_VERB }
            if (v >= 0 && v < c.size - 1) {
                val verb = c.removeAt(v)
                // a bare subject pronoun before the verb is part of the Amharic verb already
                c.add(verb)
            }
            val result = c.filterIndexed { idx, s -> !(s.kind == Kind.PRONOUN && c.getOrNull(idx + 1)?.kind == Kind.SUBJ_VERB) }
            return if (punct != null) result + punct else result
        }

        /** Attaches a one-letter Amharic prefix (በ ከ ለ) to the first word of [w]. */
        private fun attach(prefix: String, w: String): String = prefix + w
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
        private val SENTENCE_START = Regex("(^|[.!?]\\s+)\\p{Ll}")
        private val SUBJECTS = setOf("i", "you", "he", "she", "we", "they", "it")
        private val PRONOUNS = setOf("i", "me", "you", "he", "him", "she", "her", "we", "us", "they", "them", "it", "this", "that")
        private val QUESTION_WORDS = setOf("what", "where", "who", "when", "how", "why", "which", "how many", "how much", "whose")
        private val ARTICLES = setOf("a", "an", "the")
        private val DO_AUX = setOf("do", "does", "did")
        private val BE_FORMS = setOf("am", "is", "are", "was", "were")
        private val EN_PREPOSITIONS = setOf("in", "at", "by", "from", "for", "to", "towards", "with", "about", "on", "under", "inside")
        private val DETERMINERS = setOf("the", "a", "an", "my", "your", "his", "her", "our", "their", "this", "that", "these", "those")
        private val EN_PRONOUN_AM = mapOf("i" to "እኔ", "you" to "አንተ", "he" to "እሱ", "she" to "እሷ", "we" to "እኛ", "they" to "እነሱ", "it" to "እሱ")
        private val COPULA_BY_PRONOUN = mapOf("i" to "ነኝ", "you" to "ነህ", "he" to "ነው", "she" to "ናት", "it" to "ነው", "we" to "ነን", "they" to "ናቸው")
        /** Amharic "to be" endings → (English verb, implied subject). */
        private val AM_COPULA = mapOf(
            "ነው" to ("is" to "it"), "ነኝ" to ("am" to "I"), "ነህ" to ("are" to "you"), "ነሽ" to ("are" to "you"),
            "ናት" to ("is" to "she"), "ነን" to ("are" to "we"), "ናችሁ" to ("are" to "you"), "ናቸው" to ("are" to "they"),
            "ነበር" to ("was" to "it"), "ነበረ" to ("was" to "it"), "ኖት" to ("are" to "you"),
        )
        private val PREFIX_EN = mapOf("የ" to "of", "በ" to "in", "ለ" to "for", "ከ" to "from")
        /** Amharic suffix → whether it marks a plural. */
        private val AM_SUFFIXES = listOf("" to false, "ዎች" to true, "ን" to false, "ም" to false, "ው" to false)
        private val EN_SUFFIXES = listOf(
            "ies" to "y", "es" to "", "s" to "", "ed" to "", "ed" to "e", "ing" to "", "ing" to "e",
        )
    }
}
