package com.binisog.amharickeyboard.core

import kotlin.math.ln

/**
 * Produces word completions, corrections and next-word predictions, memoised in an
 * LRU cache, and learns from what the user commits.
 *
 * Scoring (higher is better):
 *   ln(1 + freq + 40·userFreq)            unigram, learned words weigh heavily
 * + 2.5 · ln(1 + bigram + 40·userBigram)  context from the previous word
 * + ln(matchWeight)                       fuzzy-match penalty (vowel form / homophones)
 */
class SuggestionEngine(
    val dictionary: Dictionary,
    cacheSize: Int = 256,
    /** Which committed words are worth learning (e.g. only Ethiopic words for Amharic). */
    private val learnFilter: (String) -> Boolean = { w -> w.length in 2..MAX_WORD && w.any { it.isLetter() } },
    /** Whether fuzzy homophone matching applies (Amharic: yes). */
    private val fuzzy: Boolean = true,
) {
    enum class Kind { COMPLETION, CORRECTION, PREDICTION, NUMERAL, EMOJI }

    data class Suggestion(val word: String, val score: Double, val kind: Kind)

    data class Result(
        val typed: String,
        /** Whether [typed] is itself a dictionary word. */
        val typedIsWord: Boolean,
        /** Best suggestions, never containing [typed] itself. */
        val suggestions: List<Suggestion>,
    ) {
        companion object { val EMPTY = Result("", false, emptyList()) }
    }

    data class CacheKey(val prev: String?, val typed: String, val open: Boolean, val limit: Int)

    val cache = LruCache<CacheKey, Result>(cacheSize)

    var emojiSuggestions: Boolean = true
        set(value) { if (field != value) { field = value; cache.clear() } }

    fun suggest(prev: String?, typed: String, lastSyllableOpen: Boolean = true, limit: Int = 8): Result {
        val key = CacheKey(prev?.takeIf { it.isNotEmpty() }, typed, lastSyllableOpen, limit)
        return cache.getOrPut(key) { compute(key) }
    }

    private fun compute(key: CacheKey): Result {
        val (prev, typedRaw, open, limit) = key
        if (typedRaw.isEmpty()) return Result("", false, predict(prev, limit))

        if (typedRaw.all { it in '0'..'9' }) {
            val geez = GeezNumerals.toGeez(typedRaw)
            return Result(typedRaw, false, listOfNotNull(geez?.let { Suggestion(it, 0.0, Kind.NUMERAL) }))
        }

        // Latin-script words: match case-insensitively and restore the typed casing.
        val capitalized = typedRaw[0].isUpperCase()
        val allCaps = typedRaw.length > 1 && typedRaw.all { !it.isLetter() || it.isUpperCase() }
        val typed = if (capitalized) typedRaw.lowercase() else typedRaw
        fun recase(w: String): String = when {
            allCaps -> w.uppercase()
            capitalized -> w.replaceFirstChar { it.uppercaseChar() }
            else -> w
        }

        val pattern = PrefixPattern.forTyped(typed, open, fuzzy)
        val scored = HashMap<String, Suggestion>()
        fun offer(word: String, s: Double, kind: Kind) {
            val existing = scored[word]
            if (existing == null || existing.score < s) scored[word] = Suggestion(word, s, kind)
        }

        for (e in dictionary.trie.complete(pattern, CANDIDATES)) {
            val kind = if (e.weight >= 1f || e.word.length > typed.length) Kind.COMPLETION else Kind.CORRECTION
            offer(e.word, score(prev, e.word, e.freq, e.userFreq) + ln(e.weight.toDouble()), kind)
        }
        // Context words that match the prefix but are too rare to make the global top list.
        for ((w, _) in dictionary.successors(prev)) {
            if (w in scored) continue
            val weight = pattern.match(w) ?: continue
            val e = dictionary.trie.get(w) ?: continue
            offer(w, score(prev, w, e.freq, e.userFreq) + ln(weight.toDouble()), Kind.COMPLETION)
        }
        val typedIsWord = typed in dictionary
        // How many real completions of what was typed exist; typo matches must not crowd them out.
        val exactCompletions = scored.size
        if (typed.length >= 4 || (typed.length == 3 && exactCompletions < 3)) {
            // Misspelt beginning, including a wrong FIRST letter: "kel" → hello, "ዐበባ" → አበባ.
            // One edit is allowed anywhere in what was typed so far.
            for (h in dictionary.trie.fuzzy(typed, maxCost = 1f, prefix = true, limit = CANDIDATES, subCost = Typos::cost)) {
                if (h.cost <= 0f) continue // exact prefixes were already offered above
                val kind = if (h.word.length > typed.length) Kind.COMPLETION else Kind.CORRECTION
                offer(h.word, score(prev, h.word, h.freq, h.userFreq) + ln(FUZZY_PREFIX_PENALTY) * h.cost, kind)
            }
        }
        if (!typedIsWord && typed.length >= 3) {
            // Unknown word: suggest dictionary words that are at least 60 % similar.
            val maxCost = minOf(MAX_CORRECTION_COST, (1f - MIN_SIMILARITY) * typed.length + 0.01f)
            for (h in dictionary.trie.fuzzy(typed, maxCost, prefix = false, limit = CANDIDATES, subCost = Typos::cost)) {
                val sim = Typos.similarity(typed.length, h.word.length, h.cost)
                if (sim < MIN_SIMILARITY) continue
                val crowded = if (exactCompletions >= 3) CROWDED_PENALTY else 0.0
                offer(h.word, score(prev, h.word, h.freq, h.userFreq) + ln(CORRECTION_PENALTY) + 3 * ln(sim.toDouble()) - crowded, Kind.CORRECTION)
            }
        }

        val list = scored.values
            .filter { it.word != typed }
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.word.length })
            .take(limit)
            .map { if (capitalized) it.copy(word = recase(it.word)) else it }
            .toMutableList()

        if (emojiSuggestions) {
            val target = if (typedIsWord) typed else list.firstOrNull()?.word
            EMOJI[target]?.let { emoji -> if (list.size >= limit) list.removeAt(list.lastIndex); list.add(Suggestion(emoji, 0.0, Kind.EMOJI)) }
        }
        return Result(typedRaw, typedIsWord, list)
    }

    private fun predict(prev: String?, limit: Int): List<Suggestion> {
        if (prev == null) return emptyList()
        val succ = dictionary.successors(prev)
        if (succ.isEmpty()) return emptyList()
        return succ.entries.asSequence()
            .mapNotNull { (w, b) ->
                val e = dictionary.trie.get(w) ?: return@mapNotNull null
                if (b.rank <= 0) return@mapNotNull null
                Suggestion(w, score(prev, w, e.freq, e.userFreq), Kind.PREDICTION)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    private fun score(prev: String?, word: String, freq: Int, userFreq: Int): Double {
        val uni = ln(1.0 + Trie.rankOf(freq, userFreq))
        val b = dictionary.bigram(prev, word)
        val bi = if (b == null) 0.0 else ln(1.0 + b.rank)
        return uni + BIGRAM_WEIGHT * bi
    }

    /**
     * Learns a committed word (and its bigram with the previous word). Cached results are
     * dropped: with typo-tolerant matching almost any query may now rank differently.
     */
    fun learn(prev: String?, word: String): Boolean {
        val w = word.trim()
        if (!learnFilter(w)) return false
        val p = prev?.takeIf { learnFilter(it) }
        dictionary.learn(p, w)
        invalidateFor(p, w)
        return true
    }

    /** Removes a suggestion permanently (until the user types it again). */
    fun forget(word: String) {
        dictionary.forget(word)
        cache.clear()
    }

    fun clearUserData() {
        dictionary.clearUserData()
        cache.clear()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun invalidateFor(prev: String?, word: String) = cache.clear()

    companion object {
        const val MAX_WORD = 48
        private const val CANDIDATES = 24
        private const val BIGRAM_WEIGHT = 2.5
        /** Words at least this similar to an unknown typed word are suggested. */
        const val MIN_SIMILARITY = 0.6f
        private const val MAX_CORRECTION_COST = 3f
        private const val CORRECTION_PENALTY = 0.35
        private const val FUZZY_PREFIX_PENALTY = 0.03
        /** Extra penalty for corrections while real completions of the typed prefix exist. */
        private const val CROWDED_PENALTY = 4.0

        /** A handful of everyday Amharic words with an obvious emoji. */
        val EMOJI: Map<String, String> = mapOf(
            "ፍቅር" to "❤️", "ልብ" to "❤️", "ደስ" to "😊", "ደስታ" to "😄", "ሳቅ" to "😂",
            "ቡና" to "☕", "ፀሐይ" to "☀️", "ጸሀይ" to "☀️", "ዝናብ" to "🌧️", "አበባ" to "🌼",
            "ኢትዮጵያ" to "🇪🇹", "እንኳን" to "🎉", "ልደት" to "🎂", "እንጀራ" to "🫓", "ሙዚቃ" to "🎵",
            "ስልክ" to "📱", "መኪና" to "🚗", "ቤት" to "🏠", "ውሃ" to "💧", "ኳስ" to "⚽",
            "እሳት" to "🔥", "ጸሎት" to "🙏", "አመሰግናለሁ" to "🙏", "እናመሰግናለን" to "🙏",
            "ሀዘን" to "😢", "ቁጣ" to "😠", "ገንዘብ" to "💰", "መጽሐፍ" to "📖", "ትምህርት" to "📚",
            "ጊዜ" to "⏰", "ምግብ" to "🍽️", "ውሻ" to "🐕", "ድመት" to "🐈", "ልጅ" to "👶",
        )
    }
}
