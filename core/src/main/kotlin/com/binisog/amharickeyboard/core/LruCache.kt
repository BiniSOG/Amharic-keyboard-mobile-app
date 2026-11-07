package com.binisog.amharickeyboard.core

/**
 * Small thread-safe LRU cache with hit/miss statistics.
 *
 * Used to memoise suggestion queries: while typing, the same (previous word, prefix)
 * pair is requested again on every backspace / re-type, and common prefixes recur
 * constantly across words.
 */
class LruCache<K : Any, V : Any>(val maxSize: Int) {

    init { require(maxSize > 0) { "maxSize must be > 0" } }

    private val map = object : LinkedHashMap<K, V>(maxSize * 4 / 3 + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            val evict = size > maxSize
            if (evict) evictions++
            return evict
        }
    }

    var hits: Long = 0; private set
    var misses: Long = 0; private set
    var evictions: Long = 0; private set

    @Synchronized
    operator fun get(key: K): V? {
        val v = map[key]
        if (v != null) hits++ else misses++
        return v
    }

    @Synchronized
    fun put(key: K, value: V) { map[key] = value }

    /** Not atomic: two threads may both compute, which is harmless for memoisation. */
    inline fun getOrPut(key: K, compute: () -> V): V {
        get(key)?.let { return it }
        val v = compute()
        put(key, v)
        return v
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    /** Removes every entry whose key matches [predicate]. */
    @Synchronized
    fun removeIf(predicate: (K) -> Boolean): Int {
        val it = map.keys.iterator()
        var n = 0
        while (it.hasNext()) if (predicate(it.next())) { it.remove(); n++ }
        return n
    }

    @Synchronized
    fun clear() { map.clear() }

    val size: Int @Synchronized get() = map.size


    override fun toString(): String = "LruCache(size=$size/$maxSize, hits=$hits, misses=$misses, evictions=$evictions)"
}
