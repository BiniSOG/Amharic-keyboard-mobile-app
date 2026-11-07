package com.binisog.amharickeyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import com.binisog.amharickeyboard.core.Ethiopic
import com.binisog.amharickeyboard.core.PhoneticComposer
import com.binisog.amharickeyboard.core.SuggestionEngine
import com.binisog.amharickeyboard.core.layout.Code
import com.binisog.amharickeyboard.core.layout.FieldKind
import com.binisog.amharickeyboard.core.layout.Key
import com.binisog.amharickeyboard.core.layout.KeyboardLayout
import com.binisog.amharickeyboard.core.layout.LayoutOptions
import com.binisog.amharickeyboard.core.layout.Layouts
import com.binisog.amharickeyboard.engine.LanguageModels
import com.binisog.amharickeyboard.keyboard.InputRootView
import com.binisog.amharickeyboard.keyboard.KeyboardTheme
import com.binisog.amharickeyboard.keyboard.KeyboardView
import com.binisog.amharickeyboard.keyboard.KeyboardView.ShiftState
import com.binisog.amharickeyboard.keyboard.PopupOverlay
import com.binisog.amharickeyboard.keyboard.SuggestionStripView
import com.binisog.amharickeyboard.keyboard.SuggestionStripView.Item
import com.binisog.amharickeyboard.keyboard.SuggestionStripView.ItemKind
import java.text.BreakIterator

/**
 * Amharic keyboard input method.
 *
 * The word being typed is kept as *composing text* in the editor (underlined) and mirrored
 * in [composer]; it is committed on space/punctuation/suggestion pick, at which point the
 * engines learn it. Suggestions come from [LanguageModels] and are LRU-cached per query.
 */
class AmharicIME : InputMethodService(), KeyboardView.Listener, SuggestionStripView.Listener {

    private enum class InputKind { TEXT, NUMBER, PHONE, RAW }

    private lateinit var settings: Settings
    private var keyboardView: KeyboardView? = null
    private var strip: SuggestionStripView? = null
    private var overlay: PopupOverlay? = null

    /** Alphabetic layout chosen by the user (phonetic / fidel / english). */
    private var alphaLayout = Layouts.PHONETIC
    /** Page currently shown (the alpha layout or a symbols/emoji/number page). */
    private var page = Layouts.PHONETIC
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L

    private val composer = PhoneticComposer()
    private var prevWord: String? = null
    private var vowelRowBase = -1

    // editor state
    private var inputKind = InputKind.TEXT
    private var fieldKind = FieldKind.TEXT
    private var isPassword = false
    private var suggestionsAllowed = true
    private var learningAllowed = true
    private var editorInfo: EditorInfo? = null

    // spacing state
    private var lastSpaceTime = 0L
    private var spaceFollowsWord = false
    private var justAutoSpaced = false
    /** Time of our own last edit, to tell our cursor updates from the user's. */
    private var lastEditTime = 0L

    // clipboard
    private val clipHistory = ArrayDeque<String>()
    private var showingClipboard = false
    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private val layoutCache = HashMap<String, KeyboardLayout>()

    private val isAmharic: Boolean get() = alphaLayout != Layouts.ENGLISH
    private val engine: SuggestionEngine?
        get() = LanguageModels.engine(if (isAmharic) LanguageModels.Language.AMHARIC else LanguageModels.Language.ENGLISH)

    // ================================================================ lifecycle

    override fun onCreate() {
        super.onCreate()
        settings = Settings.load(this)
        alphaLayout = Settings.lastLayout(this).takeIf { it in settings.layouts } ?: settings.layouts.first()
        page = alphaLayout
        LanguageModels.load(this) { applyEngineSettings(); updateSuggestions() }
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        LanguageModels.save(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = InputRootView(this)
        root.strip.listener = this
        root.keyboard.listener = this
        strip = root.strip; keyboardView = root.keyboard; overlay = root.overlay
        applySettings()
        // A recreated view (e.g. after rotation) must show the current page right away.
        if (::settings.isInitialized) { renderKeyboard(); updateSuggestions() }
        return root
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorInfo = info
        val newSettings = Settings.load(this)
        val settingsChanged = !::settings.isInitialized || newSettings.theme != settings.theme ||
            newSettings.numberRow != settings.numberRow || newSettings.hints != settings.hints ||
            newSettings.keyHeightDp != settings.keyHeightDp || newSettings.layouts != settings.layouts
        settings = newSettings
        if (settingsChanged) layoutCache.clear()
        if (alphaLayout !in settings.layouts) alphaLayout = settings.layouts.first()
        applySettings()
        applyEngineSettings()

        LanguageModels.ensureUserData(this)
        analyseEditor(info)
        if (!restarting) {
            composer.reset()
            vowelRowBase = -1
            showingClipboard = false
            shift = ShiftState.OFF
            justAutoSpaced = false
            spaceFollowsWord = false
        }
        page = when (inputKind) {
            InputKind.NUMBER -> Layouts.NUMBER_PAD
            InputKind.PHONE -> Layouts.PHONE_PAD
            else -> if (isPassword) Layouts.ENGLISH else alphaLayout
        }
        prevWord = readPrevWord()
        updateAutoShift()
        renderKeyboard()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        commitComposing(learn = true)
        keyboardView?.cancelTouch()
        LanguageModels.save(this)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        composer.reset()
        vowelRowBase = -1
        super.onFinishInput()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        @Suppress("DEPRECATION")
        val tag = newSubtype?.languageTag?.ifEmpty { newSubtype.locale }.orEmpty()
        val wantEnglish = tag.startsWith("en")
        if (wantEnglish && alphaLayout != Layouts.ENGLISH) switchAlpha(Layouts.ENGLISH)
        else if (!wantEnglish && alphaLayout == Layouts.ENGLISH) {
            switchAlpha(settings.layouts.firstOrNull { it != Layouts.ENGLISH } ?: Layouts.PHONETIC)
        }
    }

    private fun applyEngineSettings() {
        LanguageModels.amharic?.emojiSuggestions = settings.emojiSuggestions
        LanguageModels.english?.emojiSuggestions = settings.emojiSuggestions
    }

    private fun applySettings() {
        val theme = KeyboardTheme.resolve(this, settings.theme)
        keyboardView?.apply {
            this.theme = theme
            keyHeightDp = settings.keyHeightDp
            showPreview = settings.preview
            spaceSwipeEnabled = settings.spaceCursor
        }
        strip?.theme = theme
        overlay?.theme = theme
        (keyboardView?.parent?.parent as? InputRootView)?.setKeyboardBackground(theme.background)
    }

    private fun analyseEditor(info: EditorInfo) {
        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        inputKind = when {
            type == InputType.TYPE_NULL -> InputKind.RAW
            cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME -> InputKind.NUMBER
            cls == InputType.TYPE_CLASS_PHONE -> InputKind.PHONE
            else -> InputKind.TEXT
        }
        isPassword = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        ) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        fieldKind = when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
            InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            else -> FieldKind.TEXT
        }.takeIf { cls == InputType.TYPE_CLASS_TEXT } ?: FieldKind.TEXT
        val noSuggest = (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        suggestionsAllowed = settings.suggestions && !isPassword && !noSuggest && fieldKind == FieldKind.TEXT && inputKind == InputKind.TEXT
        val incognito = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        // Before the first unlock nothing learned could be saved, so do not learn at all.
        learningAllowed = settings.learn && !incognito && !isPassword && inputKind == InputKind.TEXT &&
            DirectBoot.isUnlocked(this)
    }

    // ================================================================ keyboard rendering

    private fun renderKeyboard() {
        val kv = keyboardView ?: return
        val l = currentLayout()
        kv.setLayout(l)
        kv.shiftState = if (l.shiftable) shift else ShiftState.OFF
        kv.spaceLabel = if (page == alphaLayout || page == Layouts.ENGLISH) l.spaceLabel else ""
        kv.enterLabel = enterLabel()
    }

    private fun currentLayout(): KeyboardLayout {
        val opts = LayoutOptions(numberRow = settings.numberRow, hints = settings.hints, fieldKind = fieldKind)
        val shifted = shift != ShiftState.OFF && (page == Layouts.PHONETIC || page == Layouts.ENGLISH)
        val cacheKey = "$page|$opts|$shifted"
        return layoutCache.getOrPut(cacheKey) {
            val base = Layouts.build(page, opts)
            if (!shifted) base else base.copy(rows = base.rows.map { row -> row.map { shiftKey(it) } })
        }
    }

    private fun shiftKey(k: Key): Key = when (page) {
        Layouts.PHONETIC -> Layouts.phoneticShifted(k, settings.hints)
        else -> if (!k.isFunction && k.label.length == 1 && k.label[0].isLetter()) {
            k.copy(label = k.label.uppercase(), output = k.output.uppercase(),
                popup = k.popup.map { p -> p.copy(label = p.label.uppercase(), output = p.output.uppercase()) })
        } else k
    }

    private fun enterLabel(): String {
        val info = editorInfo ?: return "↵"
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "↵"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "→"
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_NEXT -> "⇥"
            EditorInfo.IME_ACTION_DONE -> "✓"
            EditorInfo.IME_ACTION_PREVIOUS -> "⇤"
            else -> "↵"
        }
    }

    private fun switchAlpha(id: String) {
        commitComposing(learn = true)
        alphaLayout = id
        page = id
        shift = ShiftState.OFF
        vowelRowBase = -1
        Settings.setLastLayout(this, id)
        updateAutoShift()
        renderKeyboard()
        updateSuggestions()
    }

    private fun setPage(id: String) {
        page = id
        renderKeyboard()
    }

    // ================================================================ KeyboardView.Listener

    override fun onKeyDown(key: Key) {
        val kv = keyboardView ?: return
        if (settings.haptic) kv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (settings.sound) {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val fx = when (key.code) {
                Code.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                Code.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Code.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            am?.playSoundEffect(fx, -1f)
        }
    }

    override fun onKey(key: Key) {
        when (key.code) {
            Code.CHAR -> {
                handleText(key.output)
                if (shift == ShiftState.ON) { shift = ShiftState.OFF; renderKeyboard() }
            }
            Code.SHIFT -> toggleShift()
            Code.DELETE -> handleDelete(repeat = 0)
            Code.ENTER -> handleEnter()
            Code.SPACE -> handleSpace()
            Code.SYMBOLS -> setPage(Layouts.SYMBOLS)
            Code.SYMBOLS_2 -> setPage(Layouts.SYMBOLS_2)
            Code.GEEZ_NUMBERS -> setPage(Layouts.GEEZ_NUMBERS)
            Code.ALPHA -> setPage(if (isPassword) Layouts.ENGLISH else alphaLayout)
            Code.EMOJI -> setPage(Layouts.EMOJI)
            Code.LANGUAGE -> cycleLayout()
        }
    }

    override fun onDeleteRepeat(repeat: Int) {
        if (repeat > 18) deleteWordBefore() else handleDelete(repeat)
    }

    override fun onCursorMove(delta: Int) {
        commitComposing(learn = false)
        val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(delta)) { sendDownUpKeyEvents(code) }
    }

    override fun onSpaceLongPress() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when (shift) {
            ShiftState.LOCKED -> ShiftState.OFF
            ShiftState.ON -> if (now - lastShiftTap < 400) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.OFF -> ShiftState.ON
        }
        lastShiftTap = now
        renderKeyboard()
    }

    private fun cycleLayout() {
        val list = settings.layouts
        val next = list[(list.indexOf(alphaLayout) + 1) % list.size]
        if (next == alphaLayout && list.size == 1) {
            // Only one layout enabled: fall through to the next input method.
            if (Build.VERSION.SDK_INT >= 28) switchToNextInputMethod(false)
            return
        }
        switchAlpha(next)
    }

    // ================================================================ text input

    private fun handleText(text: String) {
        if (text.isEmpty()) return
        val singleCodePoint = text.codePointCount(0, text.length) == 1
        val latinSequence = text.all { it.isLetter() || it == '`' || it == '\'' } && text.none { Character.isSurrogate(it) }
        when {
            singleCodePoint && text.length == 1 -> handleChar(text[0])
            latinSequence && page == alphaLayout -> text.forEach(::handleChar)
            else -> { // emoji, ".com", multi-char symbols
                commitComposing(learn = true)
                currentInputConnection?.commitText(text, 1)
                justAutoSpaced = false
                afterCommit()
            }
        }
    }

    private fun canCompose(): Boolean = inputKind == InputKind.TEXT && !(isPassword && page == Layouts.ENGLISH)

    private fun handleChar(c: Char) {
        val ic = currentInputConnection ?: return
        showingClipboard = false
        val composing = canCompose()
        val phonetic = alphaLayout == Layouts.PHONETIC && page == Layouts.PHONETIC
        when {
            composing && phonetic && (isAsciiLetter(c) || c == '`' || (c == '\'' && !composer.isEmpty)) -> {
                if (composer.text.all { it.isDigit() } && !composer.isEmpty) commitComposing(learn = false)
                composer.type(c)
                updateComposing()
            }
            composing && isAmharic && Ethiopic.isWordChar(c) -> {
                if (composer.text.all { it.isDigit() } && !composer.isEmpty) commitComposing(learn = false)
                composer.appendLiteral(c.toString())
                vowelRowBase = if (alphaLayout == Layouts.FIDEL) Ethiopic.rowOf(c)?.base ?: -1 else -1
                updateComposing()
            }
            composing && !isAmharic && (c.isLetter() || (c == '\'' && !composer.isEmpty)) -> {
                composer.appendLiteral(c.toString())
                updateComposing()
            }
            composing && isAmharic && c in '0'..'9' && (composer.isEmpty || composer.text.all { it.isDigit() }) && page != Layouts.NUMBER_PAD -> {
                // Digits are composed so the strip can offer the Ge'ez numeral (2026 → ፳፻፳፮).
                composer.appendLiteral(c.toString())
                updateComposing()
            }
            else -> {
                val isPunct = c in ".,;:!?)]}" || c in Ethiopic.punctuation
                if (justAutoSpaced && isPunct && composer.isEmpty) {
                    // "word ።" → "word።": punctuation swaps with the space a suggestion added.
                    val before = ic.getTextBeforeCursor(1, 0)?.toString()
                    if (before == " ") ic.deleteSurroundingText(1, 0)
                }
                commitComposing(learn = true)
                if (inputKind == InputKind.RAW) sendKeyChar(c) else ic.commitText(c.toString(), 1)
                justAutoSpaced = false
                spaceFollowsWord = false
                afterCommit()
            }
        }
    }

    private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        lastEditTime = SystemClock.uptimeMillis()
        justAutoSpaced = false
        if (composer.isEmpty) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            vowelRowBase = -1
        } else {
            ic.setComposingText(composer.text, 1)
        }
        updateSuggestions()
    }

    /** Commits the composing word as typed. */
    private fun commitComposing(learn: Boolean) {
        if (composer.isEmpty) return
        val word = composer.text
        currentInputConnection?.finishComposingText()
        composer.reset()
        vowelRowBase = -1
        if (learn) learnWord(word)
        prevWord = word
    }

    private fun learnWord(word: String) {
        if (!learningAllowed || word.all { it.isDigit() }) return
        val isEthiopic = word.any(Ethiopic::isEthiopic)
        val e = LanguageModels.engine(if (isEthiopic) LanguageModels.Language.AMHARIC else LanguageModels.Language.ENGLISH) ?: return
        val normalized = if (!isEthiopic && word.length > 1 && word[0].isUpperCase() && word.drop(1).none { it.isUpperCase() }) {
            word.lowercase()
        } else word
        val p = prevWord?.let { if (!isEthiopic && it.length > 1 && it[0].isUpperCase() && it.drop(1).none { c -> c.isUpperCase() }) it.lowercase() else it }
        if (e.learn(p, normalized)) LanguageModels.noteLearned(this)
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        showingClipboard = false
        if (!composer.isEmpty) {
            commitComposing(learn = true)
            ic.commitText(" ", 1)
            lastSpaceTime = now
            spaceFollowsWord = true
        } else if (settings.doubleSpacePeriod && spaceFollowsWord && now - lastSpaceTime < 600 &&
            ic.getTextBeforeCursor(1, 0)?.toString() == " " && page != Layouts.EMOJI
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(if (isAmharic) "${Ethiopic.FULL_STOP} " else ". ", 1)
            spaceFollowsWord = false
            lastSpaceTime = 0
        } else {
            if (inputKind == InputKind.RAW) sendKeyChar(' ') else ic.commitText(" ", 1)
            spaceFollowsWord = false
            lastSpaceTime = now
        }
        justAutoSpaced = false
        if (page != alphaLayout && (page == Layouts.SYMBOLS || page == Layouts.GEEZ_NUMBERS)) setPage(alphaLayout)
        afterCommit()
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        commitComposing(learn = true)
        justAutoSpaced = false
        val info = editorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        when {
            inputKind == InputKind.RAW -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            !noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED ->
                ic.performEditorAction(action)
            else -> ic.commitText("\n", 1)
        }
        afterCommit()
    }

    private fun handleDelete(repeat: Int) {
        val ic = currentInputConnection ?: return
        justAutoSpaced = false
        spaceFollowsWord = false
        showingClipboard = false
        if (!composer.isEmpty) {
            composer.backspace()
            if (alphaLayout == Layouts.FIDEL && !composer.isEmpty) vowelRowBase = -1
            updateComposing()
            return
        }
        val selected = ic.getSelectedText(0)
        when {
            !selected.isNullOrEmpty() -> ic.commitText("", 1)
            inputKind == InputKind.RAW -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            else -> {
                val before = ic.getTextBeforeCursor(32, 0)
                if (before.isNullOrEmpty()) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) // let the app handle e.g. chip deletion
                } else {
                    ic.deleteSurroundingText(lastGraphemeLength(before.toString()), 0)
                }
            }
        }
        if (repeat == 0) resumeComposingWordBeforeCursor(ic)
        afterCommit(updateContext = composer.isEmpty)
    }

    /** Length in UTF-16 units of the last user-perceived character (emoji, flags, ZWJ sequences). */
    private fun lastGraphemeLength(s: String): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(s)
        val end = it.last()
        val start = it.previous()
        return if (start == BreakIterator.DONE) 1 else (end - start).coerceAtLeast(1)
    }

    /**
     * After deleting back into a word, make it composing again so suggestions reappear and
     * the word can be completed or corrected.
     */
    private fun resumeComposingWordBeforeCursor(ic: InputConnection) {
        if (!suggestionsAllowed || !canCompose()) return
        val after = ic.getTextAfterCursor(1, 0)
        if (!after.isNullOrEmpty() && isWordChar(after[0])) return
        val before = ic.getTextBeforeCursor(SuggestionEngine.MAX_WORD + 1, 0)?.toString() ?: return
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        val word = before.substring(i)
        if (word.isEmpty() || word.length > SuggestionEngine.MAX_WORD || i == 0 && before.length > SuggestionEngine.MAX_WORD) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.setComposingText(word, 1)
        ic.endBatchEdit()
        composer.setWord(word)
        prevWord = readPrevWord()
        updateSuggestions()
    }

    private fun isWordChar(c: Char): Boolean = if (isAmharic) Ethiopic.isWordChar(c) else (c.isLetter() || c == '\'')

    private fun deleteWordBefore() {
        val ic = currentInputConnection ?: return
        if (!composer.isEmpty) {
            composer.reset()
            ic.setComposingText("", 1)
            ic.finishComposingText()
            updateSuggestions()
            return
        }
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        val wordEnd = i
        while (i > 0 && (Ethiopic.isWordChar(before[i - 1]) || before[i - 1].isLetterOrDigit())) i--
        if (i == wordEnd && i > 0) i-- // a lone symbol
        ic.deleteSurroundingText(before.length - i, 0)
        afterCommit()
    }

    /** Re-reads the context after text was committed or deleted. */
    private fun afterCommit(updateContext: Boolean = true) {
        lastEditTime = SystemClock.uptimeMillis()
        if (updateContext && composer.isEmpty) prevWord = readPrevWord()
        updateAutoShift()
        updateSuggestions()
    }

    /** The word right before the cursor, if it is separated by spaces only (bigram context). */
    private fun readPrevWord(): String? {
        if (!suggestionsAllowed) return null
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString() ?: return null
        val s = if (composer.isEmpty) before else before.dropLast(composer.text.length)
        val trimmed = s.trimEnd(' ')
        if (trimmed.isEmpty() || trimmed.length == s.length) return null
        var i = trimmed.length
        while (i > 0 && (Ethiopic.isWordChar(trimmed[i - 1]) || trimmed[i - 1].isLetter() || trimmed[i - 1] == '\'')) i--
        return trimmed.substring(i).takeIf { it.isNotEmpty() }
    }

    private fun updateAutoShift() {
        if (shift == ShiftState.LOCKED) return
        val wasOn = shift == ShiftState.ON
        val want = page == Layouts.ENGLISH && settings.autoCap && composer.isEmpty && inputKind == InputKind.TEXT &&
            !isPassword && fieldKind == FieldKind.TEXT && editorInfo?.let { info ->
                (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
            } == true
        shift = if (want) ShiftState.ON else ShiftState.OFF
        if (want != wasOn) renderKeyboard()
    }

    // ================================================================ cursor tracking

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!composer.isEmpty && (candidatesEnd == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // The user moved the cursor away from the word being composed.
            composer.reset()
            vowelRowBase = -1
            currentInputConnection?.finishComposingText()
            prevWord = readPrevWord()
            updateAutoShift()
            updateSuggestions()
        } else if (composer.isEmpty && SystemClock.uptimeMillis() - lastEditTime > OWN_EDIT_WINDOW_MS &&
            (newSelStart != oldSelStart || newSelEnd != oldSelEnd)
        ) {
            // Jumps (taps elsewhere): refresh the context word.
            justAutoSpaced = false
            spaceFollowsWord = false
            prevWord = readPrevWord()
            updateAutoShift()
            updateSuggestions()
        }
    }

    // ================================================================ suggestions strip

    private fun updateSuggestions() {
        val s = strip ?: return
        if (showingClipboard) { s.show(clipItems()); return }
        if (alphaLayout == Layouts.FIDEL && vowelRowBase >= 0 && !composer.isEmpty) {
            val row = Ethiopic.rows.firstOrNull { it.base == vowelRowBase }
            if (row != null) { s.show(row.allForms.map { Item(it.toString(), ItemKind.VOWEL) }); return }
        }
        if (!suggestionsAllowed) { s.show(toolbar()); return }
        val e = engine
        if (e == null) {
            s.show(if (!composer.isEmpty) listOf(Item(getString(R.string.loading), ItemKind.INFO)) else toolbar())
            return
        }
        val typed = composer.text
        if (typed.isEmpty()) {
            val predictions = if (settings.nextWord && prevWord != null) e.suggest(prevWord, "").suggestions else emptyList()
            s.show(if (predictions.isEmpty()) toolbar() else predictions.take(MAX_STRIP).mapIndexed { i, sg -> toItem(sg, i == 0) })
            return
        }
        val r = e.suggest(prevWord, typed, composer.hasOpenSyllable, MAX_STRIP)
        val items = ArrayList<Item>()
        if (!typed.all { it.isDigit() }) items.add(Item(typed, if (r.typedIsWord) ItemKind.SUGGESTION else ItemKind.TYPED, tag = TAG_TYPED))
        r.suggestions.forEachIndexed { i, sg -> items.add(toItem(sg, i == 0)) }
        s.show(items)
    }

    private fun toItem(sg: SuggestionEngine.Suggestion, best: Boolean): Item {
        val tag = when (sg.kind) {
            SuggestionEngine.Kind.EMOJI -> TAG_EMOJI
            SuggestionEngine.Kind.NUMERAL -> TAG_NUMERAL
            else -> ""
        }
        return Item(sg.word, if (best && sg.kind != SuggestionEngine.Kind.EMOJI) ItemKind.BEST else ItemKind.SUGGESTION, tag = tag)
    }

    private fun toolbar(): List<Item> = listOf(
        Item("😊", ItemKind.ACTION, ACTION_EMOJI),
        Item("📋", ItemKind.ACTION, ACTION_CLIPBOARD),
        Item(if (isAmharic) "ሀ ⇄ A" else "A ⇄ ሀ", ItemKind.ACTION, ACTION_LANGUAGE),
        Item("⚙", ItemKind.ACTION, ACTION_SETTINGS),
        Item("⌄", ItemKind.ACTION, ACTION_HIDE),
    )

    private fun clipItems(): List<Item> {
        val close = Item("✕", ItemKind.ACTION, ACTION_CLOSE_CLIPS)
        if (clipHistory.isEmpty()) return listOf(close, Item(getString(R.string.clipboard_empty), ItemKind.INFO))
        return listOf(close) + clipHistory.map { clip ->
            val oneLine = clip.replace('\n', ' ')
            Item(if (oneLine.length > 28) oneLine.take(27) + "…" else oneLine, ItemKind.CLIP, clip)
        }
    }

    override fun onStripItem(item: Item) {
        val ic = currentInputConnection ?: return
        when (item.kind) {
            ItemKind.ACTION -> onToolbar(item.value)
            ItemKind.CLIP -> {
                commitComposing(learn = true)
                ic.commitText(item.value, 1)
                showingClipboard = false
                afterCommit()
            }
            ItemKind.VOWEL -> {
                composer.replaceLast(item.value[0])
                vowelRowBase = -1
                updateComposing()
            }
            ItemKind.TYPED, ItemKind.SUGGESTION, ItemKind.BEST -> pickSuggestion(item, ic)
            ItemKind.INFO -> Unit
        }
    }

    private fun pickSuggestion(item: Item, ic: InputConnection) {
        val autoSpace = settings.autoSpace && fieldKind == FieldKind.TEXT
        when (item.tag) {
            TAG_EMOJI -> {
                val hadWord = !composer.isEmpty
                commitComposing(learn = true)
                ic.commitText((if (hadWord) " " else "") + item.value + (if (autoSpace) " " else ""), 1)
            }
            TAG_NUMERAL -> {
                composer.reset()
                ic.commitText(item.value, 1)
                if (autoSpace) ic.commitText(" ", 1)
            }
            else -> {
                ic.beginBatchEdit()
                ic.commitText(item.value, 1) // replaces the composing region
                composer.reset()
                vowelRowBase = -1
                learnWord(item.value)
                prevWord = item.value
                if (autoSpace) ic.commitText(" ", 1)
                ic.endBatchEdit()
            }
        }
        justAutoSpaced = autoSpace
        spaceFollowsWord = autoSpace
        lastSpaceTime = SystemClock.uptimeMillis()
        val keepJustAutoSpaced = justAutoSpaced
        afterCommit()
        justAutoSpaced = keepJustAutoSpaced
    }

    override fun onStripItemLongPress(item: Item): Boolean {
        if (item.kind != ItemKind.SUGGESTION && item.kind != ItemKind.BEST) return false
        if (item.tag.isNotEmpty()) return false
        val e = LanguageModels.engine(
            if (item.value.any(Ethiopic::isEthiopic)) LanguageModels.Language.AMHARIC else LanguageModels.Language.ENGLISH,
        ) ?: return false
        e.forget(item.value)
        e.forget(item.value.lowercase())
        LanguageModels.save(this)
        Toast.makeText(this, getString(R.string.removed_suggestion, item.value), Toast.LENGTH_SHORT).show()
        updateSuggestions()
        return true
    }

    private fun onToolbar(action: String) {
        when (action) {
            ACTION_EMOJI -> setPage(Layouts.EMOJI)
            ACTION_CLIPBOARD -> { showingClipboard = true; updateSuggestions() }
            ACTION_CLOSE_CLIPS -> { showingClipboard = false; updateSuggestions() }
            ACTION_LANGUAGE -> cycleLayout()
            ACTION_HIDE -> requestHideSelf(0)
            ACTION_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ================================================================ clipboard

    private fun captureClip() {
        val clip = runCatching { clipboard?.primaryClip }.getOrNull() ?: return
        // Password managers mark copied secrets as sensitive; never keep those.
        if (clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return
        if (isPassword) return
        val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: return
        if (text.isEmpty() || text.length > 5000) return
        clipHistory.remove(text)
        clipHistory.addFirst(text)
        while (clipHistory.size > MAX_CLIPS) clipHistory.removeLast()
        if (showingClipboard) updateSuggestions()
    }

    companion object {
        private const val MAX_STRIP = 8
        private const val OWN_EDIT_WINDOW_MS = 250L
        private const val MAX_CLIPS = 12
        private const val TAG_TYPED = "typed"
        private const val TAG_EMOJI = "emoji"
        private const val TAG_NUMERAL = "numeral"
        private const val ACTION_EMOJI = "emoji"
        private const val ACTION_CLIPBOARD = "clipboard"
        private const val ACTION_CLOSE_CLIPS = "close_clips"
        private const val ACTION_LANGUAGE = "language"
        private const val ACTION_SETTINGS = "settings"
        private const val ACTION_HIDE = "hide"
    }
}
