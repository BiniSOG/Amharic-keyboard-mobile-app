package com.binisog.amharickeyboard.core

import com.binisog.amharickeyboard.core.layout.Code
import com.binisog.amharickeyboard.core.layout.FieldKind
import com.binisog.amharickeyboard.core.layout.LayoutOptions
import com.binisog.amharickeyboard.core.layout.Layouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutsTest {
    private val all = listOf(
        Layouts.PHONETIC, Layouts.FIDEL, Layouts.ENGLISH, Layouts.NUMBER_PAD, Layouts.PHONE_PAD,
    ) + Layouts.SYMBOL_PAGES

    @Test fun everyPageHasDeleteAndNoEmptyRows() {
        for (id in all) {
            val l = Layouts.build(id)
            assertTrue(l.rows.all { it.isNotEmpty() }, id)
            assertTrue(l.rows.flatten().any { it.code == Code.DELETE }, "$id has no delete key")
        }
    }

    @Test fun letterPagesHaveAnEmojiKey() {
        for (id in listOf(Layouts.PHONETIC, Layouts.FIDEL, Layouts.ENGLISH)) {
            val bottom = Layouts.build(id).rows.last()
            assertTrue(bottom.any { it.code == Code.EMOJI }, "$id has no emoji key")
            assertEquals(10.0, Math.round(bottom.sumOf { it.width.toDouble() } * 1000) / 1000.0, "$id bottom row width")
        }
    }

    @Test fun fidelGridHasEveryConsonantRow() {
        val l = Layouts.build(Layouts.FIDEL)
        val rows = l.rows.flatten().filter { it.fidelRow >= 0 }.map { it.fidelRow }.toSet()
        assertEquals(Ethiopic.rows.map { it.base }.toSet(), rows)
        // all four letter rows are equally wide
        assertEquals(1, l.rows.take(4).map { r -> Math.round(r.sumOf { it.width.toDouble() } * 1000) }.toSet().size)
    }

    @Test fun phoneticHintsAndPopups() {
        val l = Layouts.build(Layouts.PHONETIC)
        val s = l.rows.flatten().first { it.label == "s" }
        assertEquals("ስ", s.hint)
        assertTrue(s.popup.map { it.label }.containsAll(listOf("ስ", "ሥ", "ሽ", "ጽ")))
        assertEquals("`s", s.popup.first { it.label == "ሥ" }.output)
        val t = Layouts.phoneticShifted(l.rows.flatten().first { it.label == "t" }, hints = true)
        assertEquals("T", t.output); assertEquals("ጥ", t.hint)
        val b = Layouts.phoneticShifted(l.rows.flatten().first { it.label == "b" }, hints = true)
        assertEquals("b", b.output)
    }

    @Test fun languageKeyShowsCurrentLanguage() {
        fun lang(id: String) = Layouts.build(id).rows.flatten().single { it.code == Code.LANGUAGE }
        assertEquals("አም", lang(Layouts.PHONETIC).label)
        assertEquals("አም", lang(Layouts.FIDEL).label)
        assertEquals("En", lang(Layouts.ENGLISH).label)
        assertEquals("🌐", lang(Layouts.ENGLISH).hint)
    }

    private fun rowWidths(id: String, opt: LayoutOptions = LayoutOptions()) =
        Layouts.build(id, opt).rows.map { r -> Math.round(r.sumOf { it.width.toDouble() } * 1000) / 1000.0 }

    @Test fun fidelRowsAreAsWideAsTheNumberRow() {
        // previously the letter rows were 9 keys wide under a 10-key number row
        assertEquals(setOf(10.0), rowWidths(Layouts.FIDEL, LayoutOptions(numberRow = true)).toSet())
        assertEquals(setOf(10.0), rowWidths(Layouts.FIDEL).toSet())
    }

    @Test fun symbolPagesAreBalancedAndChained() {
        assertEquals(listOf(Layouts.SYMBOLS, Layouts.SYMBOLS_2, Layouts.MATH), Layouts.SYMBOL_PAGES)
        val visited = mutableListOf<String>()
        var id = Layouts.SYMBOLS
        repeat(Layouts.SYMBOL_PAGES.size) {
            assertEquals(setOf(10.0), rowWidths(id).toSet(), "$id rows")
            val keys = Layouts.build(id).rows.flatten()
            val pageKey = keys.first { it.code == Code.PAGE && it.label.contains("/") }
            assertEquals(Layouts.SYMBOL_PAGES.size, pageKey.popup.size) // long-press: jump to any page
            visited += id
            id = pageKey.output
        }
        assertEquals(Layouts.SYMBOL_PAGES, visited)   // the page key visits every page once
        assertEquals(Layouts.SYMBOLS, id)              // and wraps around
    }

    @Test fun symbolPagesFollowSamsungLayout() {
        fun labels(id: String, row: Int) = Layouts.build(id).rows[row].map { it.label }
        // every page: number row, 10, 10, [n/3] 7 [⌫], bottom row; 5 rows like Samsung Keyboard
        for (id in Layouts.SYMBOL_PAGES) assertEquals(5, Layouts.build(id).rows.size, id)
        assertEquals("1234567890", labels(Layouts.SYMBOLS, 0).joinToString(""))
        assertEquals("+×÷=/_<>[]", labels(Layouts.SYMBOLS, 1).joinToString(""))
        assertEquals("!@#$%^&*()", labels(Layouts.SYMBOLS, 2).joinToString(""))
        assertEquals(listOf("1/3", "-", "'", "\"", ":", ";", ",", "?", "⌫"), labels(Layouts.SYMBOLS, 3))
        assertEquals(listOf("ABC", "አም", ",", " ", ".", "↵"), labels(Layouts.SYMBOLS, 4))
        assertEquals("`~\\|{}€£¥₩", labels(Layouts.SYMBOLS_2, 1).joinToString(""))
        assertEquals("°•○●□■♤♡◇♧", labels(Layouts.SYMBOLS_2, 2).joinToString(""))
        assertEquals("±≠≈≤≥∞√∑π∫", labels(Layouts.MATH, 1).joinToString(""))
        assertEquals("3/3", labels(Layouts.MATH, 3).first())
        assertEquals(listOf("2/3", "☆", "▪", "¤", "《", "》", "¡", "¿", "⌫"), labels(Layouts.SYMBOLS_2, 3))
        // the language key shows the language being typed
        assertEquals("En", Layouts.build(Layouts.SYMBOLS, LayoutOptions(amharic = false)).rows[4][1].label)
    }

    @Test fun emailFieldsGetAtKey() {
        val l = Layouts.build(Layouts.ENGLISH, LayoutOptions(fieldKind = FieldKind.EMAIL))
        assertTrue(l.rows.last().any { it.label == "@" })
    }

    @Test fun numberRowOption() {
        val l = Layouts.build(Layouts.PHONETIC, LayoutOptions(numberRow = true))
        assertEquals("1234567890", l.rows.first().joinToString("") { it.label })
        assertEquals("፩", l.rows.first().first().hint)
    }
}
