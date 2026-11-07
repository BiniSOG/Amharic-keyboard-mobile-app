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
        Layouts.PHONETIC, Layouts.FIDEL, Layouts.ENGLISH, Layouts.SYMBOLS, Layouts.SYMBOLS_2,
        Layouts.GEEZ_NUMBERS, Layouts.EMOJI, Layouts.NUMBER_PAD, Layouts.PHONE_PAD,
    )

    @Test fun everyPageHasDeleteAndNoEmptyRows() {
        for (id in all) {
            val l = Layouts.build(id)
            assertTrue(l.rows.all { it.isNotEmpty() }, id)
            assertTrue(l.rows.flatten().any { it.code == Code.DELETE }, "$id has no delete key")
        }
    }

    @Test fun fidelGridHasEveryConsonantRow() {
        val l = Layouts.build(Layouts.FIDEL)
        val rows = l.rows.flatten().filter { it.fidelRow >= 0 }.map { it.fidelRow }.toSet()
        assertEquals(Ethiopic.rows.map { it.base }.toSet(), rows)
        // all four letter rows are equally wide
        assertEquals(1, l.rows.take(4).map { r -> r.sumOf { it.width.toDouble() } }.toSet().size)
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
