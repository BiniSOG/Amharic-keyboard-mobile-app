package com.binisog.amharickeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiDataTest {
    @Test fun categoriesAreFilledAndDistinct() {
        assertTrue(EmojiData.CATEGORIES.size >= 10)
        assertEquals(EmojiData.CATEGORIES.size, EmojiData.CATEGORIES.map { it.id }.toSet().size)
        for (c in EmojiData.CATEGORIES) {
            assertTrue(c.items.size >= 20, "${c.id} has only ${c.items.size}")
            assertTrue(c.items.none { it.isBlank() }, c.id)
        }
        assertTrue("🇪🇹" in EmojiData.FLAGS.items)
        assertTrue("¯\\_(ツ)_/¯" in EmojiData.KAOMOJI.items)
    }

    @Test fun recentListIsMostRecentFirstAndCapped() {
        var r = emptyList<String>()
        for (e in listOf("😀", "❤️", "😀", "☕")) r = EmojiData.pushRecent(r, e, max = 3)
        assertEquals(listOf("☕", "😀", "❤️"), r)
        r = EmojiData.pushRecent(r, "🎉", max = 3)
        assertEquals(listOf("🎉", "☕", "😀"), r)
    }
}
