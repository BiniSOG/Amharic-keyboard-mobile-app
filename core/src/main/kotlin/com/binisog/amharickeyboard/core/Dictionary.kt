package com.binisog.amharickeyboard.core

import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Writer

/**
 * Word list (unigrams in a [Trie]) plus bigram statistics, combining the shipped
 * dictionary with what the user has taught the keyboard.
 */
class Dictionary {

    class Bigram(var base: Int = 0, var user: Int = 0) {
        val rank: Long get() = base.toLong() + Trie.USER_WEIGHT * user
    }

    val trie = Trie()
    private val bigrams = HashMap<String, HashMap<String, Bigram>>()

    val wordCount: Int get() = trie.wordCount
    val bigramCount: Int get() = bigrams.values.sumOf { it.size }

    fun addWord(word: String, freq: Int) = trie.insert(word, freq)

    fun addBigram(prev: String, word: String, base: Int) {
        val b = bigrams.getOrPut(prev) { HashMap() }.getOrPut(word) { Bigram() }
        if (base > b.base) b.base = base
    }

    fun bigram(prev: String?, word: String): Bigram? = prev?.let { bigrams[it]?.get(word) }

    fun successors(prev: String?): Map<String, Bigram> = prev?.let { bigrams[it] } ?: emptyMap()

    operator fun contains(word: String): Boolean = word in trie

    /** Records that the user typed [word] after [prev]. */
    fun learn(prev: String?, word: String) {
        trie.addUser(word, 1)
        if (prev != null && prev.isNotEmpty()) {
            val b = bigrams.getOrPut(prev) { HashMap() }.getOrPut(word) { Bigram() }
            b.user = (b.user + 1).coerceAtMost(Trie.MAX_USER_FREQ)
        }
    }

    /** Removes a word from suggestions and drops learned bigrams that predict it. */
    fun forget(word: String) {
        trie.block(word, true)
        for (m in bigrams.values) m[word]?.let { it.user = 0 }
    }


    /** Drops everything the user has taught (shipped data stays). */
    fun clearUserData() {
        val learned = ArrayList<String>()
        trie.forEachWord { w, _, u, blocked -> if (u > 0 || blocked) learned.add(w) }
        for (w in learned) { trie.setUser(w, 0); trie.block(w, false) }
        val emptyPrev = ArrayList<String>()
        for ((p, m) in bigrams) {
            m.values.forEach { it.user = 0 }
            m.entries.removeIf { it.value.base == 0 }
            if (m.isEmpty()) emptyPrev.add(p)
        }
        emptyPrev.forEach { bigrams.remove(it) }
    }

    // ------------------------------------------------------------------ shipped data

    /**
     * Loads the shipped word list (`word<TAB>freq`) and bigram list (`prev word<TAB>count`).
     * Blank lines and lines starting with `#` are ignored.
     */
    fun loadText(words: InputStream, bigramsIn: InputStream? = null) {
        reader(words).useLines { lines ->
            for (line in lines) parseWordLine(line)?.let { (w, f) -> addWord(w, f) }
        }
        bigramsIn?.let {
            reader(it).useLines { lines ->
                for (line in lines) parseBigramLine(line)?.let { (p, w, f) -> addBigram(p, w, f) }
            }
        }
    }

    /**
     * Loads shipped data through a compact binary cache. Parsing the text assets only
     * happens once per [versionKey] (e.g. app version); later starts read the binary
     * cache, which is several times faster and avoids string splitting on the UI thread.
     */
    fun loadShipped(
        versionKey: String,
        cacheFile: File?,
        openWords: () -> InputStream,
        openBigrams: (() -> InputStream)? = null,
    ): LoadSource {
        if (cacheFile != null && cacheFile.isFile) {
            val ok = runCatching { cacheFile.inputStream().buffered().use { readBinary(it, versionKey) } }.getOrDefault(false)
            if (ok) return LoadSource.BINARY_CACHE
        }
        val parsedWords = ArrayList<Pair<String, Int>>()
        val parsedBigrams = ArrayList<Triple<String, String, Int>>()
        openWords().use { s -> reader(s).useLines { ls -> ls.forEach { l -> parseWordLine(l)?.let(parsedWords::add) } } }
        openBigrams?.invoke()?.use { s -> reader(s).useLines { ls -> ls.forEach { l -> parseBigramLine(l)?.let(parsedBigrams::add) } } }
        for ((w, f) in parsedWords) addWord(w, f)
        for ((p, w, f) in parsedBigrams) addBigram(p, w, f)
        if (cacheFile != null) {
            runCatching {
                val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                DataOutputStream(tmp.outputStream().buffered()).use { out ->
                    out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeUTF(versionKey)
                    out.writeInt(parsedWords.size)
                    for ((w, f) in parsedWords) { out.writeUTF(w); out.writeInt(f) }
                    out.writeInt(parsedBigrams.size)
                    for ((p, w, f) in parsedBigrams) { out.writeUTF(p); out.writeUTF(w); out.writeInt(f) }
                }
                if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); tmp.renameTo(cacheFile) }
            }
        }
        return LoadSource.TEXT
    }

    enum class LoadSource { TEXT, BINARY_CACHE }

    private fun readBinary(input: InputStream, versionKey: String): Boolean {
        val din = DataInputStream(input)
        if (din.readInt() != MAGIC || din.readInt() != FORMAT || din.readUTF() != versionKey) return false
        val words = ArrayList<Pair<String, Int>>()
        repeat(din.readInt()) { words.add(din.readUTF() to din.readInt()) }
        val bis = ArrayList<Triple<String, String, Int>>()
        repeat(din.readInt()) { bis.add(Triple(din.readUTF(), din.readUTF(), din.readInt())) }
        // Only mutate once the whole file has been read successfully.
        for ((w, f) in words) addWord(w, f)
        for ((p, w, f) in bis) addBigram(p, w, f)
        return true
    }

    // ------------------------------------------------------------------ user data

    /**
     * Serialises what the user taught the keyboard as TSV:
     *   `U word count` learned word, `X word` blocked word, `B prev word count` learned bigram.
     * Keeps at most [maxWords]/[maxBigrams] of the most used entries.
     */
    fun writeUserData(out: Writer, maxWords: Int = 20_000, maxBigrams: Int = 50_000) {
        val words = ArrayList<Pair<String, Int>>()
        val blocked = ArrayList<String>()
        trie.forEachWord { w, _, u, b -> if (b) blocked.add(w) else if (u > 0) words.add(w to u) }
        out.write("# amharic-keyboard user dictionary v1\n")
        for ((w, u) in words.sortedByDescending { it.second }.take(maxWords)) out.write("U\t$w\t$u\n")
        for (w in blocked) out.write("X\t$w\n")
        val bis = ArrayList<Triple<String, String, Int>>()
        for ((p, m) in bigrams) for ((w, b) in m) if (b.user > 0) bis.add(Triple(p, w, b.user))
        for ((p, w, u) in bis.sortedByDescending { it.third }.take(maxBigrams)) out.write("B\t$p\t$w\t$u\n")
        out.flush()
    }

    fun userDataToString(): String = java.io.StringWriter().also { writeUserData(it) }.toString()

    fun readUserData(input: InputStream) {
        reader(input).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val p = line.split('\t')
                when (p[0]) {
                    "U" -> if (p.size >= 3) p[2].toIntOrNull()?.let { trie.setUser(p[1], it) }
                    "X" -> if (p.size >= 2) { trie.addUser(p[1], 0); trie.block(p[1], true) }
                    "B" -> if (p.size >= 4) p[3].toIntOrNull()?.let { u ->
                        bigrams.getOrPut(p[1]) { HashMap() }.getOrPut(p[2]) { Bigram() }.user = u
                    }
                }
            }
        }
    }

    fun readUserData(text: String) = readUserData(text.byteInputStream())

    companion object {
        private const val MAGIC = 0x414D4B42 // "AMKB"
        private const val FORMAT = 1

        private fun reader(s: InputStream) = BufferedReader(InputStreamReader(s, Charsets.UTF_8))

        internal fun parseWordLine(line: String): Pair<String, Int>? {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return null
            val parts = t.split('\t', ' ').filter { it.isNotEmpty() }
            val word = parts[0]
            val freq = parts.getOrNull(1)?.toIntOrNull() ?: 1
            return word to freq
        }

        internal fun parseBigramLine(line: String): Triple<String, String, Int>? {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return null
            val parts = t.split('\t', ' ').filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            return Triple(parts[0], parts[1], parts.getOrNull(2)?.toIntOrNull() ?: 1)
        }
    }
}
