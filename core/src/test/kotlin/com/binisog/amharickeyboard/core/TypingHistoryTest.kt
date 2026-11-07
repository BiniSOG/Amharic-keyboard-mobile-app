package com.binisog.amharickeyboard.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypingHistoryTest {

    @Test fun recordsMostRecentFirstWithoutDuplicates() {
        val h = TypingHistory(maxEntries = 3)
        assertTrue(h.record("ሰላም ነህ ወንድሜ"))
        assertTrue(h.record("I will call you later"))
        assertTrue(h.record("ሰላም   ነህ  ወንድሜ")) // same text, extra spaces → moved to front
        assertEquals(listOf("ሰላም ነህ ወንድሜ", "I will call you later"), h.all())
        assertFalse(h.record("ok")) // too short
        assertFalse(h.record("123 456"))
        h.record("one two"); h.record("three four")
        assertEquals(3, h.size) // capped
    }

    @Test fun completesASentenceTypedBefore() {
        val h = TypingHistory()
        h.record("እንኳን ደህና መጣህ ወንድሜ")
        h.record("I will call you later tonight")
        // context + partial word
        assertEquals("ደህና መጣህ ወንድሜ", h.continuation(listOf("እንኳን"), "ደህ"))
        // right after a space: the next words
        assertEquals("call you later tonight", h.continuation(listOf("I", "will"), ""))
        // start of a sentence, no context yet
        assertEquals("እንኳን ደህና መጣህ ወንድሜ", h.continuation(emptyList(), "እንኳ"))
    }

    @Test fun sentenceStartAndCaseInsensitive() {
        val h = TypingHistory()
        h.record("Good morning my friend.")
        assertEquals("Good morning my friend.", h.continuation(emptyList(), "goo"))
        assertEquals("morning my friend.", h.continuation(listOf("good"), "mor"))
        assertNull(h.continuation(listOf("bad"), "mor"))
        assertNull(h.continuation(emptyList(), "g")) // one letter is too little
    }

    @Test fun persistsAndHelpers() {
        val h = TypingHistory()
        h.record("ቡና እንጠጣ"); h.record("see you tomorrow")
        val h2 = TypingHistory().apply { load(h.serialize()) }
        assertEquals(h.all(), h2.all())
        assertEquals(listOf("እንዴት", "ነህ"), TypingHistory.currentSentenceWords("ሰላም። እንዴት ነህ "))
        assertEquals("እንዴት ነህ", TypingHistory.lastSentence("ሰላም። እንዴት ነህ"))
    }
}

class TranslateTextTest {
    private val t = Translator().apply {
        val assets = listOf(File("../app/src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        load(File(assets, "translations.tsv").inputStream())
    }

    @Test fun amharicSentenceToEnglish() {
        assertEquals("Hello how are you?", t.translateText("ሰላም እንዴት ነህ?"))
        assertEquals("Thank you very much.", t.translateText("በጣም አመሰግናለሁ።"))
        assertEquals("My house.", t.translateText("ቤቴ።"))
    }

    @Test fun englishSentenceToAmharic() {
        assertEquals("ሰላም", t.translateText("hello"))
        assertEquals("አመሰግናለሁ", t.translateText("thank you")) // phrase, not two words
        assertEquals("ደህና እደር", t.translateText("good night"))
        assertTrue(t.translateText("I love you").contains("እወድ"), t.translateText("I love you"))
        assertEquals("ውሃ፣ ቡና።", t.translateText("water, coffee."))
    }

    @Test fun grammarAmharicToEnglish() {
        assertEquals("I want coffee", t.translateText("ቡና እፈልጋለሁ"))           // SOV → SVO
        assertEquals("I am going to the market", t.translateText("ወደ ገበያ እሄዳለሁ"))
        assertEquals("The house is big.", t.translateText("ቤቱ ትልቅ ነው።"))   // copula moves
        assertEquals("What is this?", t.translateText("ይህ ምንድን ነው?"))        // question word first
        assertEquals("It is good", t.translateText("ጥሩ ነው"))                  // implied subject
        assertEquals("I am fine", t.translateText("ደህና ነኝ"))
    }

    @Test fun grammarEnglishToAmharic() {
        assertEquals("ቡና እፈልጋለሁ", t.translateText("I want coffee"))           // verb last
        assertEquals("ደህና ነኝ", t.translateText("I am fine"))                   // copula, no pronoun
        assertEquals("ቤቱ ትልቅ ነው", t.translateText("the house is big")) // "the house" → definite ቤቱ
        assertEquals("በጣም አመሰግናለሁ", t.translateText("thank you very much"))
        assertEquals("በአዲስ አበባ እኖራለሁ", t.translateText("I live in Addis Ababa")) // in → በ
        assertEquals("ወደ ገበያው እሄዳለሁ።", t.translateText("I am going to the market.")) // the market → ገበያው
        assertEquals("ከእኔ ጋር", t.translateText("with me"))
        assertEquals("ጠረጴዛ ላይ", t.translateText("on the table"))
        assertEquals("የት ነህ?", t.translateText("where are you?"))
    }

    @Test fun unknownWordsAreKept() {
        assertEquals("Hello Abebe", t.translateText("ሰላም Abebe"))
    }
}
