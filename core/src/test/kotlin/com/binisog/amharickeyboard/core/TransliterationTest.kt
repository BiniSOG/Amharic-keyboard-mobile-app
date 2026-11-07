package com.binisog.amharickeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransliterationTest {

    private fun t(latin: String) = PhoneticComposer.transliterate(latin)

    @Test fun vowelOrders() {
        assertEquals("ለ", t("le"))
        assertEquals("ሉ", t("lu"))
        assertEquals("ሊ", t("li"))
        assertEquals("ላ", t("la"))
        assertEquals("ሌ", t("lE"))
        assertEquals("ሌ", t("lie"))
        assertEquals("ል", t("l"))
        assertEquals("ሎ", t("lo"))
        assertEquals("ሏ", t("lua"))
    }

    @Test fun commonWords() {
        assertEquals("ሰላም", t("selam"))
        assertEquals("ኢትዮጵያ", t("ityoPya"))
        assertEquals("አመሰግናለሁ", t("amesegnalehu"))
        assertEquals("እንዴት ነህ", t("endEt neh"))
        assertEquals("ጤና ይስጥልኝ", t("TEna ysTlN"))
        assertEquals("ጤና ይስጥልኝ", t("Tiena ysTlN"))
        assertEquals("ጤ", t("Tie"))
        assertEquals("ቴ", t("tie"))
        assertEquals("ሻይ", t("shay"))
        assertEquals("ቡና", t("buna"))
        assertEquals("ጸሎት", t("tselot"))
        assertEquals("ቸኮለ", t("chekole"))
        assertEquals("ዓመት", t("Amet"))
        assertEquals("ኳስ", t("kuas"))
        assertEquals("ሠላም", t("`selam"))
    }

    @Test fun apostropheBreaksSyllable() {
        assertEquals("ልእ", t("l'e"))
        assertEquals("ትስ", t("t's"))
    }

    @Test fun tableIsPrefixClosed() {
        val table = TransliterationTable.DEFAULT
        for (k in table.entries.keys) for (i in 1 until k.length) {
            assertTrue(k.substring(0, i) in table, "missing prefix ${k.substring(0, i)} of $k")
        }
    }

    @Test fun backspaceUndoesVowel() {
        val c = PhoneticComposer()
        c.typeAll("sela")
        assertEquals("ሰላ", c.text)
        assertTrue(c.backspace())
        assertEquals("ሰል", c.text)
        c.type('a'); c.type('m')
        assertEquals("ሰላም", c.text)
        repeat(5) { c.backspace() }
        assertEquals("", c.text)
        assertFalse(c.backspace())
    }

    @Test fun resumedWordContinuesSyllable() {
        val c = PhoneticComposer()
        c.setWord("ሰል")
        c.type('a'); c.type('m')
        assertEquals("ሰላም", c.text)
        c.setWord("ቡና")
        c.type('w')
        assertEquals("ቡናው", c.text)
    }

    @Test fun replaceLastForFidelLayout() {
        val c = PhoneticComposer()
        c.appendLiteral("ል")
        c.replaceLast('ላ')
        assertEquals("ላ", c.text)
        c.backspace()
        assertEquals("ል", c.text)
    }

    @Test fun ethiopicHelpers() {
        assertEquals(Ethiopic.Order.FOURTH, Ethiopic.orderOf('ላ'))
        assertEquals(listOf('ለ', 'ሉ', 'ሊ', 'ላ', 'ሌ', 'ል', 'ሎ', 'ሏ'), Ethiopic.rowVariants('ል'))
        assertTrue('ሐ' in Ethiopic.homophones('ሀ'))
        assertEquals("ሰላም", Ethiopic.normalize("ሠላም"))
        assertEquals("ጸሀይ", Ethiopic.normalize("ፀሐይ"))
    }
}

class GeezNumeralsTest {
    @Test fun conversions() {
        assertEquals("፩", GeezNumerals.toGeez(1))
        assertEquals("፲", GeezNumerals.toGeez(10))
        assertEquals("፲፩", GeezNumerals.toGeez(11))
        assertEquals("፻", GeezNumerals.toGeez(100))
        assertEquals("፻፩", GeezNumerals.toGeez(101))
        assertEquals("፳፻፳፮", GeezNumerals.toGeez(2026))
        assertEquals("፼", GeezNumerals.toGeez(10_000))
        assertEquals("፲፪፼፴፬፻፶፮", GeezNumerals.toGeez(123_456))
        assertEquals("፻፼", GeezNumerals.toGeez(1_000_000))
        assertEquals("፼፼", GeezNumerals.toGeez(100_000_000))
        assertEquals(null, GeezNumerals.toGeez(0))
        assertEquals("፳፻፳፮", GeezNumerals.toGeez("2026"))
    }
}
