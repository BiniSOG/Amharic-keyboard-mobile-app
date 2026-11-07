package com.binisog.amharickeyboard

import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import com.binisog.amharickeyboard.keyboard.InputRootView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InputViewTest {

    private val screenW = 1080
    private val screenH = 2200

    private fun measure(v: View) {
        // The IME window offers the input view at most the full screen height.
        v.measure(
            View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST),
        )
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    @Test
    fun inputViewIsOnlyAsTallAsStripPlusKeys() {
        val ime = Robolectric.buildService(AmharicIME::class.java).create().get()
        val root = ime.onCreateInputView() as InputRootView
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        measure(root)

        val expected = root.strip.measuredHeight + root.keyboard.measuredHeight
        assertTrue("keyboard has height", root.keyboard.measuredHeight > 0)
        assertEquals(expected, root.measuredHeight)
        assertTrue("must not take the whole screen", root.measuredHeight < screenH / 2)
        assertEquals(root.measuredHeight, root.overlay.measuredHeight)
        assertEquals(screenW, root.overlay.measuredWidth)
    }

    @Test
    fun numberFieldShowsNumberPad() {
        val ime = Robolectric.buildService(AmharicIME::class.java).create().get()
        val root = ime.onCreateInputView() as InputRootView
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }, false)
        measure(root)
        assertTrue(root.measuredHeight in 1 until screenH / 2)
    }
}
