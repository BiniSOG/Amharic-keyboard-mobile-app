package com.binisog.amharickeyboard.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslatorTest {
    private val t = Translator().apply {
        val assets = listOf(File("../app/src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        load(File(assets, "translations.tsv").inputStream())
    }

    @Test fun loads() = assertTrue(t.size >= 5000, "only ${t.size} entries")

    @Test fun dailyVocabulary() {
        assertEquals("my house", t.translate("ቤቴ").first())
        assertEquals("our money", t.translate("ገንዘባችን").first())
        assertEquals("her phone", t.translate("ስልኳ").first())
        assertEquals("I am hungry", t.translate("ርቦኛል").first())
        assertEquals("I am tired", t.translate("ደክሞኛል").first())
        assertTrue("ሂሳብ" in t.translate("bill", limit = 5))
        assertTrue("ጉንፋን" in t.translate("flu", limit = 5))
        assertTrue("ማሽከርከር" in t.translate("drive", limit = 5))
    }

    @Test fun amharicToEnglish() {
        assertEquals("hello", t.translate("ሰላም").first())
        assertEquals("water", t.translate("ውሃ").first())
        assertEquals(listOf("thank you", "thanks"), t.translate("አመሰግናለሁ"))
    }

    @Test fun englishToAmharic() {
        assertEquals("ውሃ", t.translate("water").first())
        assertEquals("ሰላም", t.translate("Hello").first())
        assertEquals("መሄድ", t.translate("go").first()) // indexed from "to go"
        assertTrue("ቤት" in t.translate("house"))
    }

    @Test fun englishInflections() {
        assertEquals("መኪኖች", t.translate("cars").first()) // has its own plural entry
        assertEquals("መኪና", t.translate("car").first())
        assertEquals("ጓደኛ", t.translate("friend").first())
        assertEquals("ፈለገ", t.translate("wanted").first())
    }

    @Test fun amharicAffixes() {
        assertEquals("in Ethiopia", t.translate("በኢትዮጵያ").first())
        assertEquals("of water", t.translate("የውሃ").first())
        assertEquals("cars", t.translate("መኪናዎች").first())
        assertEquals("for children", t.translate("ለልጆች").first())
    }

    @Test fun unknownWordsGiveNothing() {
        assertTrue(t.translate("qwertyzz").isEmpty())
        assertTrue(t.translate("ቅቅቅቅ").isEmpty())
    }
}
