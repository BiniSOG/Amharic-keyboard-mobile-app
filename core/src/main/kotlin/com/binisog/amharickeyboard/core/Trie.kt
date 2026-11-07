package com.binisog.amharickeyboard.core

import java.util.PriorityQueue
import kotlin.math.max

/**
 * A prefix pattern with per-position alternatives. Each alternative carries a
 * multiplicative weight in (0, 1]; the exact typed character has weight 1.
 */
class PrefixPattern(val positions: List<Map<Char, Float>>) {
    val length: Int get() = positions.size

    /** Combined weight if [word] starts with this pattern, otherwise null. */
    fun match(word: String): Float? {
        if (word.length < positions.size) return null
        var w = 1f
        for (i in positions.indices) {
            w *= positions[i][word[i]] ?: return null
        }
        return w
    }

    companion object {
        const val HOMOPHONE_WEIGHT = 0.55f
        const val OPEN_SYLLABLE_VARIANT_WEIGHT = 0.8f
        const val CLOSED_SYLLABLE_VARIANT_WEIGHT = 0.35f

        /**
         * Builds the fuzzy pattern for a typed Fidel prefix:
         *  - homophone letters (ሀ/ሐ/ኀ, ሰ/ሠ, ጸ/ፀ, አ/ዐ) are accepted at every position;
         *  - the last character also matches every vowel form of its row, because while
         *    typing phonetically `sel` (ሰል) the user is very likely heading for ሰላም.
         */
        fun forTyped(typed: String, lastSyllableOpen: Boolean = true, fuzzy: Boolean = true): PrefixPattern {
            val positions = typed.mapIndexed { i, c ->
                val m = LinkedHashMap<Char, Float>()
                m[c] = 1f
                if (fuzzy) {
                    for (h in Ethiopic.homophones(c)) m.putIfAbsent(h, HOMOPHONE_WEIGHT)
                }
                if (i == typed.lastIndex && Ethiopic.rowOf(c) != null) {
                    val isBare = Ethiopic.orderOf(c) == Ethiopic.Order.SIXTH
                    val vw = if (lastSyllableOpen && isBare) OPEN_SYLLABLE_VARIANT_WEIGHT else CLOSED_SYLLABLE_VARIANT_WEIGHT
                    for (v in Ethiopic.rowVariants(c)) m.putIfAbsent(v, vw)
                    if (fuzzy) for (h in Ethiopic.homophones(c)) {
                        for (v in Ethiopic.rowVariants(h)) m.putIfAbsent(v, vw * HOMOPHONE_WEIGHT)
                    }
                }
                m
            }
            return PrefixPattern(positions)
        }

        fun exact(typed: String): PrefixPattern = PrefixPattern(typed.map { mapOf(it to 1f) })
    }
}

/**
 * Frequency trie over dictionary words. Each word carries a static (shipped) frequency,
 * a learned user frequency and a "blocked" flag (user removed the suggestion).
 *
 * Every node tracks the maximum rank found in its subtree, so top-K completion is a
 * best-first search that never visits branches that cannot beat the current results.
 */
class Trie {

    class Node {
        internal var keys: CharArray = EMPTY_KEYS
        internal var children: Array<Node?> = EMPTY_CHILDREN
        internal var count = 0
        var freq: Int = 0; internal set
        var userFreq: Int = 0; internal set
        var isWord: Boolean = false; internal set
        var blocked: Boolean = false; internal set
        internal var maxRank: Long = 0

        internal fun child(c: Char): Node? {
            val i = indexOf(c)
            return if (i >= 0) children[i] else null
        }

        private fun indexOf(c: Char): Int {
            var lo = 0; var hi = count - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val k = keys[mid]
                when {
                    k < c -> lo = mid + 1
                    k > c -> hi = mid - 1
                    else -> return mid
                }
            }
            return -(lo + 1)
        }

        internal fun getOrCreate(c: Char): Node {
            val i = indexOf(c)
            if (i >= 0) return children[i]!!
            val ins = -(i + 1)
            if (count == keys.size) {
                val n = if (count == 0) 2 else count * 2
                keys = keys.copyOf(n)
                children = children.copyOf(n)
            }
            System.arraycopy(keys, ins, keys, ins + 1, count - ins)
            System.arraycopy(children, ins, children, ins + 1, count - ins)
            val node = Node()
            keys[ins] = c
            children[ins] = node
            count++
            return node
        }

        internal inline fun forEachChild(f: (Char, Node) -> Unit) {
            for (i in 0 until count) f(keys[i], children[i]!!)
        }

        /** A word is suggestible when it is not blocked and is either shipped or learned. */
        internal fun isActive(): Boolean = isWord && !blocked && (freq > 0 || userFreq > 0)

        internal fun rank(): Long = if (!isActive()) 0 else rankOf(freq, userFreq)
    }

    data class Entry(val word: String, val freq: Int, val userFreq: Int, val weight: Float) {
        val rank: Long get() = rankOf(freq, userFreq)
    }

    private val root = Node()
    var wordCount: Int = 0
        private set

    /** Adds or raises the shipped frequency of a word. */
    fun insert(word: String, freq: Int) {
        if (word.isEmpty()) return
        val path = pathFor(word)
        val node = path.last()
        if (!node.isWord) { node.isWord = true; wordCount++ }
        node.freq = max(node.freq, freq)
        bubbleUp(path, node.rank())
    }

    /** Learns a word typed by the user. Unblocks it if it was blocked. */
    fun addUser(word: String, delta: Int = 1): Int {
        if (word.isEmpty()) return 0
        val path = pathFor(word)
        val node = path.last()
        if (!node.isWord) { node.isWord = true; wordCount++ }
        node.blocked = false
        node.userFreq = (node.userFreq + delta).coerceIn(0, MAX_USER_FREQ)
        bubbleUp(path, node.rank())
        return node.userFreq
    }

    fun setUser(word: String, userFreq: Int) {
        if (word.isEmpty()) return
        val path = pathFor(word)
        val node = path.last()
        if (!node.isWord) { node.isWord = true; wordCount++ }
        node.userFreq = userFreq.coerceIn(0, MAX_USER_FREQ)
        bubbleUp(path, node.rank())
    }

    /** Hides a word from suggestions (user long-pressed → remove). */
    fun block(word: String, blocked: Boolean = true) {
        val node = find(word) ?: return
        node.blocked = blocked
        if (blocked) node.userFreq = 0
        // maxRank is an upper bound, so it is fine to leave ancestors untouched when lowering.
        if (!blocked) bubbleUp(pathFor(word), node.rank())
    }

    fun get(word: String): Entry? {
        val n = find(word) ?: return null
        if (!n.isActive()) return null
        return Entry(word, n.freq, n.userFreq, 1f)
    }

    fun isBlocked(word: String): Boolean = find(word)?.blocked == true

    operator fun contains(word: String): Boolean {
        return find(word)?.isActive() == true
    }

    private fun find(word: String): Node? {
        var n = root
        for (c in word) n = n.child(c) ?: return null
        return n
    }

    private fun pathFor(word: String): List<Node> {
        val path = ArrayList<Node>(word.length + 1)
        var n = root
        path.add(n)
        for (c in word) { n = n.getOrCreate(c); path.add(n) }
        return path
    }

    private fun bubbleUp(path: List<Node>, rank: Long) {
        for (n in path) if (n.maxRank < rank) n.maxRank = rank
    }

    /**
     * Top [limit] completions for [pattern], ordered by `rank * weight` descending.
     * The pattern may allow several characters per position (fuzzy matching).
     */
    private class Start(val node: Node, val prefix: String, val weight: Float)

    fun complete(pattern: PrefixPattern, limit: Int, minWeight: Float = 0.05f): List<Entry> {
        if (limit <= 0) return emptyList()
        // 1. Expand the (fuzzy) prefix into matching trie nodes.
        var frontier = listOf(Start(root, "", 1f))
        for (pos in pattern.positions) {
            val next = ArrayList<Start>()
            for (s in frontier) {
                for ((c, w) in pos) {
                    val weight = s.weight * w
                    if (weight < minWeight) continue
                    val child = s.node.child(c) ?: continue
                    next.add(Start(child, s.prefix + c, weight))
                }
            }
            if (next.isEmpty()) return emptyList()
            frontier = if (next.size > MAX_FRONTIER) next.sortedByDescending { it.weight * it.node.maxRank }.take(MAX_FRONTIER) else next
        }
        return bestFirst(frontier, limit)
    }

    /** Best-first search over all start nodes, bounded by subtree max rank. */
    private fun bestFirst(frontier: List<Start>, limit: Int): List<Entry> {
        class Item(val node: Node, val word: String, val weight: Float, val bound: Double)
        val queue = PriorityQueue<Item>(compareByDescending { it.bound })
        for (s in frontier) if (s.node.maxRank > 0) queue.add(Item(s.node, s.prefix, s.weight, s.node.maxRank * s.weight.toDouble()))

        val results = ArrayList<Entry>(limit)
        val seen = HashSet<String>()
        // Results pop in non-increasing score order because a word item's bound is its own score.
        val wordItems = PriorityQueue<Pair<Double, Entry>>(compareByDescending { it.first })
        while (queue.isNotEmpty() && results.size < limit) {
            val item = queue.poll()
            // Flush finished words whose score beats everything still in the queue.
            while (wordItems.isNotEmpty() && wordItems.peek().first >= item.bound && results.size < limit) {
                val e = wordItems.poll().second
                if (seen.add(e.word)) results.add(e)
            }
            if (results.size >= limit) break
            val n = item.node
            val r = n.rank()
            if (r > 0) wordItems.add(r * item.weight.toDouble() to Entry(item.word, n.freq, n.userFreq, item.weight))
            n.forEachChild { c, child ->
                if (child.maxRank > 0) queue.add(Item(child, item.word + c, item.weight, child.maxRank * item.weight.toDouble()))
            }
        }
        while (wordItems.isNotEmpty() && results.size < limit) {
            val e = wordItems.poll().second
            if (seen.add(e.word)) results.add(e)
        }
        return results
    }

    /** A word found by [fuzzy] together with its edit cost. */
    data class FuzzyHit(val word: String, val freq: Int, val userFreq: Int, val cost: Float)

    /**
     * Typo-tolerant search: weighted Damerau-Levenshtein distance (insert, delete, substitute,
     * swap neighbours) between [typed] and dictionary words, computed row by row while
     * walking the trie and pruned as soon as no path can stay within [maxCost].
     *
     * - [prefix] = true: [typed] may be the (misspelt) *beginning* of a word; matching subtrees
     *   are expanded best-first, so "kel" (wrong first letter) still completes to "hello".
     *   Each entry's weight is `penalty^cost`.
     * - [prefix] = false: whole-word matches only (corrections for unknown words).
     *
     * [subCost] gives the cost of reading typed char `a` where the word has `b` (≤ 1).
     */
    fun fuzzy(
        typed: String,
        maxCost: Float,
        prefix: Boolean,
        limit: Int,
        penalty: Float = 0.3f,
        subCost: (Char, Char) -> Float = { _, _ -> 1f },
    ): List<FuzzyHit> {
        val n = typed.length
        if (n == 0 || limit <= 0) return emptyList()
        val starts = HashMap<Node, Start>()
        val startCost = HashMap<Node, Float>()
        val hits = ArrayList<FuzzyHit>()
        val path = StringBuilder()
        var visited = 0

        fun walk(node: Node, prevRow: FloatArray, prevPrevRow: FloatArray?, prevChar: Char) {
            node.forEachChild { c, child ->
                if (child.maxRank <= 0 || visited > MAX_FUZZY_NODES) return@forEachChild
                visited++
                val row = FloatArray(n + 1)
                row[0] = prevRow[0] + 1f
                var rowMin = row[0]
                for (j in 1..n) {
                    val t = typed[j - 1]
                    val sub = if (t == c) 0f else subCost(t, c)
                    var v = minOf(prevRow[j] + 1f, row[j - 1] + 1f, prevRow[j - 1] + sub)
                    if (prevPrevRow != null && j > 1 && t == prevChar && typed[j - 2] == c && c != prevChar) {
                        v = minOf(v, prevPrevRow[j - 2] + 1f) // swapped neighbours
                    }
                    row[j] = v
                    if (v < rowMin) rowMin = v
                }
                path.append(c)
                val cost = row[n]
                if (prefix) {
                    if (cost <= maxCost) {
                        val prev = startCost[child]
                        if (prev == null || cost < prev) {
                            startCost[child] = cost
                            starts[child] = Start(child, path.toString(), Math.pow(penalty.toDouble(), cost.toDouble()).toFloat())
                        }
                    }
                } else if (cost <= maxCost && child.isActive()) {
                    hits.add(FuzzyHit(path.toString(), child.freq, child.userFreq, cost))
                }
                if (rowMin <= maxCost) walk(child, row, prevRow, c)
                path.setLength(path.length - 1)
            }
        }
        walk(root, FloatArray(n + 1) { it.toFloat() }, null, '\u0000')

        if (!prefix) {
            return hits.sortedWith(compareBy<FuzzyHit> { it.cost }.thenByDescending { rankOf(it.freq, it.userFreq) }).take(limit)
        }
        // Deeper starts lie inside shallower ones' subtrees; bestFirst keeps each word's best score.
        val frontier = starts.values.sortedByDescending { it.weight * it.node.maxRank }.take(MAX_FRONTIER * 4)
        return bestFirst(frontier, limit).map { e ->
            val cost = if (e.weight >= 1f) 0f else (Math.log(e.weight.toDouble()) / Math.log(penalty.toDouble())).toFloat()
            FuzzyHit(e.word, e.freq, e.userFreq, cost)
        }
    }

    /** Visits every non-blocked word (used for serialisation and diagnostics). */
    fun forEachWord(f: (word: String, freq: Int, userFreq: Int, blocked: Boolean) -> Unit) {
        val sb = StringBuilder()
        fun walk(n: Node) {
            if (n.isWord) f(sb.toString(), n.freq, n.userFreq, n.blocked)
            n.forEachChild { c, child ->
                sb.append(c); walk(child); sb.setLength(sb.length - 1)
            }
        }
        walk(root)
    }

    companion object {
        /** One learned use is worth this many "shipped" frequency points. */
        const val USER_WEIGHT = 40L
        const val MAX_USER_FREQ = 10_000
        private const val MAX_FRONTIER = 64
        /** Safety valve for fuzzy search on very large dictionaries. */
        private const val MAX_FUZZY_NODES = 60_000
        private val EMPTY_KEYS = CharArray(0)
        private val EMPTY_CHILDREN = arrayOfNulls<Node>(0)

        fun rankOf(freq: Int, userFreq: Int): Long = freq.toLong() + USER_WEIGHT * userFreq + 1
    }
}
