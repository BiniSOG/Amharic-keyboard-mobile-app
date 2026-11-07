# Amharic Keyboard · አማርኛ ኪቦርድ

An Android keyboard (IME) for Amharic with phonetic typing, a Fidel grid layout, word
completion, next-word prediction, on-device learning and caching.

## Features

**Typing**
- **Phonetic layout (QWERTY → ፊደል).** Type `selam` and get **ሰላም**. Each keystroke updates the current syllable as you type (`l` ል → `la` ላ).
- **Undo-aware backspace.** Backspace after `la` (ላ) goes back to `l` (ል), so you can fix a vowel without retyping the consonant.
- **Fidel grid layout.** Every consonant has its own key in ሀ ለ ሐ መ order. Tap a letter, then choose its vowel form (ለ ሉ ሊ ላ ሌ ል ሎ ሏ) from the strip. You can also long-press a letter to get its forms.
- **Long-press alternatives.** Long-press `s` for ስ ሥ ሽ ጽ ፅ, a vowel key for አ/ዐ forms, or `.` and `,` for Ethiopic punctuation.
- **English layout** with accent popups, auto-capitalisation, and suggestions from a 20,000-word frequency dictionary.
- **Ethiopic punctuation and numerals:** ። ፣ ፤ ፥ ፦ ፧ ፡ ፨. There's a Ge'ez numeral page (፩–፱, ፲–፺, ፻, ፼), and typing digits suggests the Ge'ez form (`2026` → **፳፻፳፮**).
- **Double space** inserts `። ` (or `. ` in English).
- **Field-aware.** Number and phone fields get number pads. Email and URL fields get `@`, `/` and `.com` keys. Password fields get no suggestions, no learning and no composing. The keyboard also respects "no personalised learning" (incognito) fields.
- The enter key shows the editor's action (Go, Search, Send, Next, Done).
- **Space-bar swipe** moves the cursor, and long-pressing space opens the keyboard picker.
- Delete auto-repeats when held and speeds up to deleting whole words. Multi-finger roll-over typing works too.
- Key preview bubbles, haptic and sound feedback (configurable), and an optional number row.

**Suggestions and learning**
- **Completions while the syllable is still open.** `sel` (ሰል) already suggests ሰላም, because the last letter matches every vowel form of its row.
- **Homophone-tolerant matching.** ሀ/ሐ/ኀ, ሰ/ሠ, ጸ/ፀ and አ/ዐ are interchangeable, so `ሠላ` still finds ሰላም.
- **Context.** Next-word predictions and ranking use bigrams: after **እንዴት** you get ነህ, ነሽ, ናችሁ, and after **አዲስ** you get አበባ.
- **Typo-tolerant suggestions.** A wrong *first* letter or one slip anywhere in the start of a word still completes it (`ymport` → important, ሸላም → ሰላም). An unknown word gets dictionary words that are at least **60 % similar** (`tommorow` → tomorrow, ኢትዮፕያ → ኢትዮጵያ). Neighbouring QWERTY keys, another vowel form of the same Fidel letter, and sound-alike letters count as smaller mistakes.
- **Typing history.** The keyboard privately remembers sentences you type, stored on the device only. It never records password or incognito fields, or anything before the phone is first unlocked. When you start a sentence you've typed before, the strip offers the rest of it (📜 …) in one tap. Open 📜 in the toolbar to see recent sentences and tap one to insert it. You can turn history off or clear it in quick settings or in Settings.
- **Translate several words at once.** Select text, and the strip shows **⇄ its translation**: tap it to replace the selection, or 📋 to copy it. The **⇄** toolbar button translates the sentence you just typed. Known phrases are matched first ("thank you" → አመሰግናለሁ), and everything else is translated word by word, keeping the original word order.
- **Delete and keep going.** Backspacing into a word, or tapping at the end of one, makes it editable again with suggestions, and phonetic typing continues the last syllable (ሰል + `a` → ሰላ).
- Emoji suggestions (ቡና → ☕).
- **Translation suggestions (offline).** The strip shows the translation of the word you are typing, marked ⇄: `selam` → ⇄ hello, `water` → ⇄ ውሃ. Tap it to insert the translation. The lexicon has **5,053 Amharic entries** for everyday communication: greetings, chat phrases, family, food, health, shopping, money, phone and internet, school and work, travel, feelings, and verbs in their common past, present, negative and command forms. It also has possessive forms such as ቤቴ (my house) and ስልክህ (your phone). Prefixes and suffixes are handled too (በኢትዮጵያ → in Ethiopia). It translates single words only.
- **Learns as you type.** Words you commit and the word pairs they form get boosted. Long-press a suggestion to remove it. Learned data stays on the device and can be cleared in settings.
- **Re-correction.** If you backspace into a word, it becomes editable again with suggestions.
- **Clipboard history** in the toolbar. Clips marked sensitive, such as copied passwords, are never stored.

**Performance and caching**
- **LRU suggestion cache.** It stores results per (previous word, typed prefix), so backspacing and retyping inside a word is instant. The cache is cleared whenever the keyboard learns a new word.
- **Binary dictionary cache.** The shipped TSV word lists are parsed once per app version and stored in a compact binary file, so later starts skip text parsing.
- **Fast lookups.** The frequency trie uses sorted child arrays and tracks the highest rank in each subtree. Top-K completion is a best-first search that skips branches that can't win.
- **Background work.** Dictionaries load off the UI thread, and the personal dictionary is written in the background with atomic file replacement.

**Look and feel**
- **Themes:** light, dark, follow-system, Ethiopian (green/gold/red), Midnight blue, Ocean, Rose, Purple, High contrast, and **Custom**, where you pick the background, key, letter and accent colours.
- **Background picture:** choose one from your gallery or download it from a web link. You can darken the picture and set how see-through the keys are.
- The 🌐 key shows the current language: **አም** for Amharic, **En** for English.
- Key height is adjustable.
- **Quick settings inside the keyboard.** Tap ⚙ in the top bar, or long-press the **አም/En** key, to open a panel in place of the keys. It has a theme picker with live preview, key height, layout, sound, vibration, key popup, number row, suggestions, translation, emoji, auto-space, double-space ።, learning, background picture, and a link to all settings.

## Phonetic scheme (SERA-style)

| Type | Get | Type | Get | Type | Get |
|---|---|---|---|---|---|
| `le` | ለ | `lu` | ሉ | `li` | ሊ |
| `la` | ላ | `lie` / `lE` | ሌ | `l` | ል |
| `lo` | ሎ | `lua` | ሏ | `kua` | ኳ |
| `sh` / `x` | ሽ | `ch` / `c` | ች | `ny` / `N` | ኝ |
| `ts` / `S` | ጽ | `tz` / `` `S `` | ፅ | `zh` / `Z` | ዥ |
| `T` | ጥ | `C` | ጭ | `P` / `ph` | ጵ |
| `H` / `hh` | ሕ | `` `h `` | ኅ | `` `s `` / `sz` | ሥ |
| `a` | አ | `u` | ኡ | `i` | ኢ |
| `aa` | ኣ | `E` | ኤ | `e` | እ |
| `o` | ኦ | `A` | ዓ | `` `a `` | ዐ |

Use an apostrophe to split syllables: `l'e` → ልእ, `t's` → ትስ.

Examples: `selam` ሰላም · `ityoPya` ኢትዮጵያ · `amesegnalehu` አመሰግናለሁ · `Tiena ysTlN` ጤና ይስጥልኝ · `endEt neh` እንዴት ነህ

## Project layout

```
core/   Pure Kotlin engine (no Android dependencies, unit tested)
  Ethiopic.kt         syllabary model: rows, vowel orders, homophones, punctuation
  Transliteration.kt  latin → fidel table + PhoneticComposer (undo-aware)
  Trie.kt             frequency trie, fuzzy PrefixPattern, best-first top-K
  Dictionary.kt       unigrams + bigrams, binary cache, user-data persistence
  SuggestionEngine.kt scoring, predictions, corrections, LRU cache, learning
  LruCache.kt         thread-safe LRU with hit/miss stats
  GeezNumerals.kt     2026 → ፳፻፳፮
  layout/Layouts.kt   all keyboard pages (phonetic, fidel, English, symbols, Ge'ez numerals, emoji, pads)
app/    Android IME
  AmharicIME.kt              InputMethodService: composing, commit, learning, clipboard
  keyboard/KeyboardView.kt   canvas-drawn keys, long-press, repeat, space swipe
  keyboard/PopupOverlay.kt   key previews and long-press popups
  keyboard/SuggestionStripView.kt  suggestions / vowel forms / clipboard / toolbar
  engine/LanguageModels.kt   async loading, binary cache, persistence
  SettingsActivity.kt        setup steps, test field, preferences
tools/  curated word lists + build_dictionaries.py → app/src/main/assets/*.tsv
```

## Building

Requirements: JDK 17+ and the Android SDK (compileSdk 35). The minimum Android version is 7.0 (API 24).

```bash
./gradlew :core:test          # engine unit tests (no Android SDK needed)
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/
```

The `:app` module is only included when an Android SDK is configured (`local.properties`
`sdk.dir`, `ANDROID_HOME` or `ANDROID_SDK_ROOT`). Without one, the engine still builds
and tests on its own. CI (`.github/workflows/build.yml`) runs the tests and uploads a
debug APK.

After installing, open **Amharic Keyboard** and tap **Enable**. Android shows the system keyboard list, where you switch Amharic Keyboard on once. No app can skip this step, because it protects against hidden keyloggers. As soon as it is on, the app brings you back by itself and opens the "choose keyboard" dialog. The app needs no permissions you have to approve: internet (used only for background pictures from a web link) and vibration are granted automatically when it is installed.

### Dictionary data

The English word list comes from [wordfreq](https://github.com/rspeer/wordfreq) by Robyn Speer, licensed **CC BY-SA 4.0**, and is regenerated with `tools/export_english_wordfreq.py`. The Amharic list is curated by hand in `tools/`.

### Translation vocabulary

- `tools/translations.tsv` and `tools/vocab/*.tsv` are hand-written, one entry per line: `Amharic<TAB>meaning|meaning`.
- `tools/vocab/20_possessives.tsv` is generated by `tools/gen_possessives.py` from about 180 common nouns.
- Every single Amharic word in these files is also added to the suggestion dictionary.
- The vocabulary was written without a native-speaker review, so please have a fluent speaker check it before a wide release.

### Extending the dictionary

Add words to `tools/amharic_words_ranked.txt` (one per line, most frequent first) and
phrases to `tools/amharic_bigrams.txt`, then run:

```bash
python3 tools/build_dictionaries.py
```

The binary cache rebuilds automatically on the next app version.
