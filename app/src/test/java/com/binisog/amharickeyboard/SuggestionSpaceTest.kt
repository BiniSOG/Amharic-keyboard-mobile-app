package com.binisog.amharickeyboard

import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.binisog.amharickeyboard.core.layout.Code
import com.binisog.amharickeyboard.core.layout.Key
import com.binisog.amharickeyboard.engine.LanguageModels
import com.binisog.amharickeyboard.keyboard.SuggestionStripView.Item
import com.binisog.amharickeyboard.keyboard.SuggestionStripView.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Space after suggestions, no auto-correct, and swipe-delete of whole words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestionSpaceTest {

    /** Like some apps: drops the trailing space of a committed "word ". */
    private class SpaceDroppingConnection(target: InputConnection) : InputConnectionWrapper(target, true) {
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean =
            super.commitText(if (text.length > 1 && text.endsWith(" ")) text.dropLast(1) else text, newCursorPosition)
    }

    private fun connect(ime: InputMethodService, field: EditText, dropSpaces: Boolean = false): Boolean {
        val base: InputConnection = field.onCreateInputConnection(EditorInfo()) ?: return false
        val ic: InputConnection = if (dropSpaces) SpaceDroppingConnection(base) else base
        val f = runCatching { InputMethodService::class.java.getDeclaredField("mStartedInputConnection") }.getOrNull()
            ?: return false
        f.isAccessible = true
        f.set(ime, ic)
        return true
    }

    private fun start(dropSpaces: Boolean = false): Pair<AmharicIME, EditText> {
        val ime = Robolectric.buildService(AmharicIME::class.java).create().get()
        ime.onCreateInputView()
        val field = EditText(RuntimeEnvironment.getApplication())
        assertTrue("cannot attach an input connection", connect(ime, field, dropSpaces))
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        shadowOf(Looper.getMainLooper()).idle()
        return ime to field
    }

    private fun type(ime: AmharicIME, latin: String) = latin.forEach { ime.onKey(Key(it.toString())) }

    @Test
    fun pickingASuggestionAddsASpace() {
        val (ime, field) = start()
        type(ime, "sela")
        ime.onStripItem(Item("ሰላም", ItemKind.BEST))
        assertEquals("ሰላም ", field.text.toString())
        assertEquals(field.text.length, field.selectionStart)

        type(ime, "selam")                // the next word goes after the space
        assertEquals("ሰላም ሰላም", field.text.toString())
    }

    @Test
    fun punctuationReplacesTheAutomaticSpace() {
        val (ime, field) = start()
        type(ime, "sela")
        ime.onStripItem(Item("ሰላም", ItemKind.BEST))
        ime.onKey(Key("።"))
        assertEquals("ሰላም።", field.text.toString())
    }

    @Test
    fun backspaceAfterACorrectionBringsBackTheTypedWord() {
        val (ime, field) = start()
        type(ime, "qzxw")
        val typed = field.text.toString()
        ime.onStripItem(Item("ሰላም", ItemKind.BEST))
        assertEquals("ሰላም ", field.text.toString())
        ime.onKey(Key("⌫", code = Code.DELETE))
        assertEquals(typed, field.text.toString())
    }

    private fun waitForDictionary() {
        val deadline = System.currentTimeMillis() + 20_000
        while (LanguageModels.amharic == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue("dictionaries did not load", LanguageModels.amharic != null)
    }

    @Test
    fun spaceKeepsTheWordAsTyped() {
        val (ime, field) = start()
        waitForDictionary()
        type(ime, "selm")                 // ሰልም is not a word, but it must not be replaced
        ime.onKey(Key("␣", code = Code.SPACE))
        assertEquals("ሰልም ", field.text.toString())
    }

    @Test
    fun swipeLeftOnDeleteRemovesWholeWords() {
        val (ime, field) = start()
        field.setText("ሰላም ቤተሰብ ሰላም")
        field.setSelection(field.text.length)
        ime.onDeleteSwipe(2)
        assertEquals("ሰላም ", field.text.toString())
    }

    @Test
    fun spaceIsAddedEvenWhenTheAppDropsIt() {
        val (ime, field) = start(dropSpaces = true)
        type(ime, "sela")
        ime.onStripItem(Item("ሰላም", ItemKind.BEST))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals("ሰላም ", field.text.toString())
    }
}
