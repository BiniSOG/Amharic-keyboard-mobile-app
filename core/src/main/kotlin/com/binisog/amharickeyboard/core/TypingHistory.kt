package com.binisog.amharickeyboard.core

/**
 * Private log of sentences the user has typed (most recent first), used to
 *  - complete a sentence the user typed before ("እንኳን ደህ…" → "ደህና መጣህ ወንድሜ"),
 *  - list recent sentences for quick re-use.
 *
 * Nothing leaves the device; the keyboard never records password or incognito fields.
 */
class TypingHistory(private val maxEntries: Int = 500) {

    private val entries = ArrayDeque<String>()

    val size: Int get() = entries.size

    /** All entries, most recent first. */
    fun all(): List<String> = entries.toList()

    /**
     * Records a typed sentence. Whitespace is normalised; very short or very long texts and
     * texts without letters are ignored. Re-typing a sentence moves it to the front.
     */
    fun record(text: String): Boolean {
        val t = text.trim().replace(WHITESPACE, " ")
        if (t.length < 3 || t.length > MAX_LENGTH || t.none { it.isLetter() }) return false
        entries.remove(t)
        entries.addFirst(t)
        while (entries.size > maxEntries) entries.removeLast()
        return true
    }

    fun remove(text: String) = entries.remove(text)

    fun clear() = entries.clear()

    /**
     * The continuation of a previously typed sentence that matches what is being typed now.
     *
     * @param context the words already typed in the current sentence (before the current word)
     * @param typed the (partial) current word; may be empty right after a space
     * @return the current word completed plus the following words (at most [maxWords]),
     *         or null. With an empty [typed], only the following words.
     */
    fun continuation(context: List<String>, typed: String, maxWords: Int = 5): String? {
        val ctx = context.map(::bare).filter { it.isNotEmpty() }.takeLast(2)
        val prefix = bare(typed).lowercase()
        if (ctx.isEmpty() && prefix.length < 2) return null
        for (entry in entries) {
            val words = entry.split(' ')
            val keys = words.map { bare(it).lowercase() }
            for (i in words.indices) {
                // the words before position i must equal the typed context
                if (ctx.isNotEmpty()) {
                    if (i < ctx.size) continue
                    var ok = true
                    for (k in ctx.indices) if (keys[i - ctx.size + k] != ctx[k].lowercase()) { ok = false; break }
                    if (!ok) continue
                } else if (i != 0) {
                    continue // no context: only match sentence starts
                }
                if (!keys[i].startsWith(prefix)) continue
                val end = minOf(words.size, i + maxWords)
                val result = words.subList(i, end).joinToString(" ")
                // nothing to add if it would only repeat what was typed
                if (prefix.isNotEmpty() && end - i == 1 && keys[i] == prefix) continue
                return result
            }
        }
        return null
    }

    fun serialize(): String = buildString {
        append("# amharic-keyboard typing history v1\n")
        for (e in entries) append(e).append('\n')
    }

    fun load(text: String) {
        entries.clear()
        for (line in text.lineSequence()) {
            if (line.isBlank() || line.startsWith("#")) continue
            if (entries.size < maxEntries) entries.addLast(line.trim())
        }
    }

    companion object {
        private const val MAX_LENGTH = 400
        private val WHITESPACE = Regex("\\s+")
        private val EDGE_PUNCT = Regex("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$")

        /** A word without surrounding punctuation (።, ፣, ?, quotes…). */
        fun bare(word: String): String = word.replace(EDGE_PUNCT, "")

        /** Splits the end of a text into the words of its last, unfinished sentence. */
        fun currentSentenceWords(textBeforeCursor: String): List<String> {
            val sentence = textBeforeCursor.split(SENTENCE_END).last()
            return sentence.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        }

        /** The last complete or partial sentence of a text (for recording). */
        fun lastSentence(text: String): String {
            val parts = text.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
            return parts.lastOrNull() ?: ""
        }

        private val SENTENCE_END = Regex("[።.!?፧\\n]+")
    }
}
