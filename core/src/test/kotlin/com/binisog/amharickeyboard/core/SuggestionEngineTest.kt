package com.binisog.amharickeyboard.core

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionEngineTest {

    private val assets = listOf(File("../app/src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }

    private fun amharic(): SuggestionEngine {
        val d = Dictionary()
        d.loadText(File(assets, "amharic_words.tsv").inputStream(), File(assets, "amharic_bigrams.tsv").inputStream())
        return SuggestionEngine(d, learnFilter = { w -> w.length >= 2 && w.all(Ethiopic::isWordChar) })
    }

    private fun words(r: SuggestionEngine.Result) = r.suggestions.map { it.word }

    @Test fun shippedDictionaryLoads() {
        val e = amharic()
        assertTrue(e.dictionary.wordCount > 500)
        assertTrue(e.dictionary.bigramCount > 50)
        assertTrue("ሰላም" in e.dictionary)
    }

    @Test fun completesWhileLastSyllableIsOpen() {
        val e = amharic()
        // typing "sel" phonetically gives ሰል; ሰላም must still be suggested.
        assertTrue("ሰላም" in words(e.suggest(null, "ሰል")), words(e.suggest(null, "ሰል")).toString())
        assertTrue("ኢትዮጵያ" in words(e.suggest(null, "ኢትዮ")))
    }

    @Test fun homophoneSpellingsMatch() {
        val e = amharic()
        assertTrue("ሰላም" in words(e.suggest(null, "ሠላ")))
        assertTrue(words(e.suggest(null, "ፀሐ")).any { it == "ፀሐይ" || it == "ጸሀይ" })
    }

    @Test fun typedWordIsExcludedFromSuggestions() {
        val e = amharic()
        val r = e.suggest(null, "ሰላም")
        assertTrue(r.typedIsWord)
        assertFalse("ሰላም" in words(r))
    }

    @Test fun nextWordPrediction() {
        val e = amharic()
        val r = e.suggest("እንዴት", "")
        assertTrue(r.suggestions.isNotEmpty())
        assertTrue(words(r).take(4).containsAll(listOf("ነህ", "ነሽ")), words(r).toString())
        assertTrue(r.suggestions.all { it.kind == SuggestionEngine.Kind.PREDICTION })
    }

    @Test fun contextBoostsBigram() {
        val e = amharic()
        val r = e.suggest("አዲስ", "አ")
        assertEquals("አበባ", words(r).first(), words(r).toString())
    }

    @Test fun learningPromotesWordAndCacheIsInvalidated() {
        val e = amharic()
        val before = e.suggest(null, "ሰላ")
        assertFalse("ሰላምታችሁ" in words(before))
        repeat(3) { e.learn(null, "ሰላምታችሁ") }
        val after = e.suggest(null, "ሰላ")
        assertTrue("ሰላምታችሁ" in words(after).take(3), words(after).toString())
    }

    @Test fun learnedBigramPredicts() {
        val e = amharic()
        e.learn("ቡና", "እንጠጣ")
        assertEquals("እንጠጣ", words(e.suggest("ቡና", "")).first())
    }

    @Test fun learnFilterRejectsNonAmharic() {
        val e = amharic()
        assertFalse(e.learn(null, "hello"))
        assertFalse(e.learn(null, "ል"))
    }

    @Test fun forgetRemovesSuggestion() {
        val e = amharic()
        assertTrue("ሰላም" in words(e.suggest(null, "ሰላ")))
        e.forget("ሰላም")
        assertFalse("ሰላም" in words(e.suggest(null, "ሰላ")))
        e.learn(null, "ሰላም")
        assertTrue("ሰላም" in words(e.suggest(null, "ሰላ")))
    }

    @Test fun cacheServesRepeatedQueries() {
        val e = amharic()
        e.suggest(null, "ሰላ"); e.suggest(null, "ሰላ"); e.suggest(null, "ሰላ")
        assertEquals(2, e.cache.hits)
        assertEquals(1, e.cache.misses)
    }

    @Test fun numeralSuggestion() {
        val e = amharic()
        assertEquals("፳፻፳፮", e.suggest(null, "2026").suggestions.single().word)
    }

    @Test fun emojiSuggestion() {
        val e = amharic()
        assertTrue("☕" in words(e.suggest(null, "ቡና")))
        e.emojiSuggestions = false
        assertFalse("☕" in words(e.suggest(null, "ቡና")))
    }

    @Test fun englishCasing() {
        val d = Dictionary()
        d.loadText(File(assets, "english_words.tsv").inputStream(), File(assets, "english_bigrams.tsv").inputStream())
        val e = SuggestionEngine(d, fuzzy = false)
        assertTrue("Thanks" in words(e.suggest(null, "Tha")), words(e.suggest(null, "Tha")).toString())
        assertEquals("you", words(e.suggest("thank", "")).first())
        assertTrue("because" in words(e.suggest(null, "becuase")))
    }

    @Test fun userDataRoundTrip() {
        val e = amharic()
        e.learn("ቡና", "እንጠጣ")
        e.forget("ሰላም")
        val saved = e.dictionary.userDataToString()

        val e2 = amharic()
        e2.dictionary.readUserData(saved)
        assertEquals("እንጠጣ", words(e2.suggest("ቡና", "")).first())
        assertFalse("ሰላም" in e2.dictionary)

        e2.clearUserData()
        assertTrue("ሰላም" in e2.dictionary)
        assertFalse("እንጠጣ" in e2.dictionary)
    }

    @Test fun binaryCacheRoundTrip() {
        val dir = Files.createTempDirectory("amkb").toFile()
        val cache = File(dir, "dict.bin")
        val open = { File(assets, "amharic_words.tsv").inputStream() }
        val openBi = { File(assets, "amharic_bigrams.tsv").inputStream() }

        val d1 = Dictionary()
        assertEquals(Dictionary.LoadSource.TEXT, d1.loadShipped("v1", cache, open, openBi))
        assertTrue(cache.isFile)
        val d2 = Dictionary()
        assertEquals(Dictionary.LoadSource.BINARY_CACHE, d2.loadShipped("v1", cache, open, openBi))
        assertEquals(d1.wordCount, d2.wordCount)
        assertEquals(d1.bigramCount, d2.bigramCount)
        // A new version invalidates the cache.
        assertEquals(Dictionary.LoadSource.TEXT, Dictionary().loadShipped("v2", cache, open, openBi))
        dir.deleteRecursively()
    }
}

class TrieAndCacheTest {
    @Test fun topKOrderedByRank() {
        val t = Trie()
        t.insert("abc", 5); t.insert("abd", 50); t.insert("ab", 10); t.insert("b", 100)
        assertEquals(listOf("abd", "ab", "abc"), t.complete(PrefixPattern.exact("ab"), 10).map { it.word })
        assertEquals(listOf("abd"), t.complete(PrefixPattern.exact("a"), 1).map { it.word })
        assertTrue(t.complete(PrefixPattern.exact("x"), 5).isEmpty())
    }

    @Test fun lruEvicts() {
        val c = LruCache<String, Int>(2)
        c.put("a", 1); c.put("b", 2); c["a"]; c.put("c", 3)
        assertEquals(1, c["a"]); assertEquals(null, c["b"]); assertEquals(3, c["c"])
        assertEquals(1, c.evictions)
    }
}
