package com.binisog.amharickeyboard.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.binisog.amharickeyboard.core.Dictionary
import com.binisog.amharickeyboard.core.Ethiopic
import com.binisog.amharickeyboard.core.SuggestionEngine
import java.io.File
import java.util.concurrent.Executors

/**
 * Process-wide holder for the suggestion engines.
 *
 * - Shipped dictionaries are loaded off the main thread; after the first run they come
 *   from a binary cache in `noBackupFilesDir` keyed by the app version.
 * - What the user teaches the keyboard is persisted to private TSV files. Engines are only
 *   touched on the main thread; serialisation happens there (it is fast) and the disk
 *   write happens on a background thread with an atomic rename.
 */
object LanguageModels {
    private const val TAG = "LanguageModels"
    private const val SAVE_EVERY = 15

    enum class Language { AMHARIC, ENGLISH }

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "amkb-io").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())

    var amharic: SuggestionEngine? = null
        private set
    var english: SuggestionEngine? = null
        private set

    private var loading = false
    private val waiters = ArrayList<() -> Unit>()
    private var unsaved = 0

    fun engine(lang: Language): SuggestionEngine? = if (lang == Language.AMHARIC) amharic else english

    /** Loads both engines once; [onReady] runs on the main thread. */
    fun load(context: Context, onReady: () -> Unit = {}) {
        if (amharic != null && english != null) { onReady(); return }
        waiters.add(onReady)
        if (loading) return
        loading = true
        val app = context.applicationContext
        io.execute {
            val t0 = System.nanoTime()
            val am = runCatching { build(app, "amharic", amharicFilter) }.onFailure { Log.e(TAG, "amharic load failed", it) }.getOrNull()
            val en = runCatching { build(app, "english", englishFilter) }.onFailure { Log.e(TAG, "english load failed", it) }.getOrNull()
            Log.i(TAG, "dictionaries ready in ${(System.nanoTime() - t0) / 1_000_000} ms")
            main.post {
                amharic = am; english = en
                loading = false
                val w = waiters.toList(); waiters.clear()
                w.forEach { it() }
            }
        }
    }

    private val amharicFilter: (String) -> Boolean = { w -> w.length in 2..SuggestionEngine.MAX_WORD && w.all(Ethiopic::isWordChar) }
    private val englishFilter: (String) -> Boolean = { w ->
        w.length in 2..SuggestionEngine.MAX_WORD && w.all { it.isLetter() || it == '\'' } && w.any { it.isLetter() }
    }

    private fun build(ctx: Context, name: String, filter: (String) -> Boolean): SuggestionEngine {
        val dict = Dictionary()
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val versionKey = "${pkg.versionCode}-${pkg.lastUpdateTime}"
        val cacheDir = File(ctx.noBackupFilesDir, "dict-cache").apply { mkdirs() }
        val source = dict.loadShipped(
            versionKey = versionKey,
            cacheFile = File(cacheDir, "$name.bin"),
            openWords = { ctx.assets.open("${name}_words.tsv") },
            openBigrams = { ctx.assets.open("${name}_bigrams.tsv") },
        )
        val user = userFile(ctx, name)
        if (user.isFile) runCatching { user.inputStream().use(dict::readUserData) }.onFailure { Log.w(TAG, "user dict unreadable", it) }
        Log.i(TAG, "$name: ${dict.wordCount} words, ${dict.bigramCount} bigrams from $source")
        return SuggestionEngine(dict, cacheSize = 384, learnFilter = filter, fuzzy = name == "amharic")
    }

    private fun userFile(ctx: Context, name: String) = File(ctx.filesDir, "user_$name.tsv")

    /** Call after learning; persists every few words so little is lost if the process dies. */
    fun noteLearned(context: Context) {
        if (++unsaved >= SAVE_EVERY) save(context)
    }

    fun save(context: Context) {
        unsaved = 0
        val app = context.applicationContext
        val snapshots = listOfNotNull(
            amharic?.let { "amharic" to it.dictionary.userDataToString() },
            english?.let { "english" to it.dictionary.userDataToString() },
        )
        io.execute {
            for ((name, text) in snapshots) {
                runCatching {
                    val f = userFile(app, name)
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    tmp.writeText(text, Charsets.UTF_8)
                    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                }.onFailure { Log.w(TAG, "saving $name failed", it) }
            }
        }
    }

    fun learnedCounts(): Pair<Int, Int> {
        fun count(e: SuggestionEngine?): Int {
            var n = 0
            e?.dictionary?.trie?.forEachWord { _, _, u, _ -> if (u > 0) n++ }
            return n
        }
        return count(amharic) to count(english)
    }

    fun clearUserData(context: Context) {
        amharic?.clearUserData()
        english?.clearUserData()
        save(context)
    }
}
