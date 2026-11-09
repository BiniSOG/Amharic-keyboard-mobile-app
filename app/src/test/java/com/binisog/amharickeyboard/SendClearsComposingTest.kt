package com.binisog.amharickeyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.binisog.amharickeyboard.core.layout.Key
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression test: a word still being typed must not come back after the app clears the
 * field with its own Send button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendClearsComposingTest {

    /** Connects the keyboard to [field] the way the system would (InputMethodService internals). */
    private fun connect(ime: InputMethodService, field: EditText): Boolean {
        val ic: InputConnection = field.onCreateInputConnection(EditorInfo()) ?: return false
        val f = runCatching { InputMethodService::class.java.getDeclaredField("mStartedInputConnection") }.getOrNull()
            ?: return false
        f.isAccessible = true
        f.set(ime, ic)
        return true
    }

    private fun type(ime: AmharicIME, latin: String) = latin.forEach { ime.onKey(Key(it.toString())) }

    @Test
    fun unknownWordDoesNotReappearAfterAppClearsField() {
        val ime = Robolectric.buildService(AmharicIME::class.java).create().get()
        ime.onCreateInputView()
        val field = EditText(RuntimeEnvironment.getApplication())
        assumeTrue("cannot attach an input connection on this SDK", connect(ime, field))
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)

        type(ime, "qzxw")                 // not a dictionary word, still being composed
        assertEquals("ቅዝሽው", field.text.toString())

        field.setText("")                 // the app sends the message and empties the field
        type(ime, "a")                    // start the next message

        assertEquals("አ", field.text.toString())
    }

    @Test
    fun composingContinuesNormallyWhenTextIsUntouched() {
        val ime = Robolectric.buildService(AmharicIME::class.java).create().get()
        ime.onCreateInputView()
        val field = EditText(RuntimeEnvironment.getApplication())
        assumeTrue(connect(ime, field))
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)

        type(ime, "selam")
        assertEquals("ሰላም", field.text.toString())
    }
}
