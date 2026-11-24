package com.binisog.amharickeyboard

import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
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

/** Space after suggestions, auto-correct with undo, and swipe-delete of whole words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestionSpaceTest {

    private fun connect(ime: InputMethodService, field: EditText): Boolean {
        val ic: InputConnection = field.onCreateInputConnection(EditorInfo()) ?: return false
        val f = runCatching { InputMethodService::class.java.getDeclaredField("mStartedInputConnection") }.getOrNull()
            ?: return false
        f.isAccessible = true
        f.set(ime, ic)
        return true
    }

    private fun start(): Pair<AmharicIME, EditText> {
        val ime = Robolectric.buildService(AmharicIME::class.java).create().get()
        ime.onCreateInputView()
        val field = EditText(RuntimeEnvironment.getApplication())
        assertTrue("cannot attach an input connection", connect(ime, field))
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
    fun spaceAutoCorrectsAndBackspaceUndoes() {
        val (ime, field) = start()
        waitForDictionary()
        type(ime, "selm")                 // ሰልም is not a word; ሰላም is
        ime.onKey(Key("␣", code = Code.SPACE))
        assertEquals("ሰላም ", field.text.toString())

        ime.onKey(Key("⌫", code = Code.DELETE))
        assertEquals("ሰልም", field.text.toString())

        ime.onKey(Key("␣", code = Code.SPACE)) // the user wants it as typed: no second correction
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
}
