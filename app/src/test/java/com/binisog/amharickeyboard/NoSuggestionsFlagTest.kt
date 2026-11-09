package com.binisog.amharickeyboard

import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.binisog.amharickeyboard.core.layout.Key
import com.binisog.amharickeyboard.engine.LanguageModels
import com.binisog.amharickeyboard.keyboard.InputRootView
import com.binisog.amharickeyboard.keyboard.SuggestionStripView.ItemKind
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Apps like TikTok mark ordinary text boxes "no suggestions"; we still suggest there by default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoSuggestionsFlagTest {

    @Test
    fun suggestionsAppearInBoxesFlaggedNoSuggestions() {
        val ime = Robolectric.buildService(AmharicIME::class.java).create().get()
        val root = ime.onCreateInputView() as InputRootView
        // wait for the dictionaries, which load on a background thread
        val deadline = System.currentTimeMillis() + 20_000
        while (LanguageModels.amharic == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assumeTrue("dictionaries did not load", LanguageModels.amharic != null)

        val field = EditText(RuntimeEnvironment.getApplication())
        val ic = field.onCreateInputConnection(EditorInfo())
        val f = runCatching { InputMethodService::class.java.getDeclaredField("mStartedInputConnection") }.getOrNull()
        assumeTrue(ic != null && f != null)
        f!!.isAccessible = true
        f.set(ime, ic)

        ime.onStartInputView(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }, false)
        "sel".forEach { ime.onKey(Key(it.toString())) }

        val items = root.strip.items
        assertTrue("no suggestions shown: $items", items.any { it.kind == ItemKind.BEST || it.kind == ItemKind.SUGGESTION })
        assertTrue("ሰላም expected among $items", items.any { it.value == "ሰላም" })
    }
}
