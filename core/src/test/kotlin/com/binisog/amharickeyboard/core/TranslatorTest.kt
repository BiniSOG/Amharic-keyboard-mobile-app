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

    @Test fun loads() = assertTrue(t.size > 400)

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
        assertEquals("መኪና", t.translate("cars").first())
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
