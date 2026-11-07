package com.binisog.amharickeyboard

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.text.InputType
import androidx.activity.SystemBarStyle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import java.util.concurrent.Executors
import androidx.preference.PreferenceFragmentCompat
import com.binisog.amharickeyboard.engine.LanguageModels

/**
 * Launcher activity: guides the user through enabling/selecting the keyboard, offers a
 * test field and hosts all preferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var enableButton: Button
    private lateinit var selectButton: Button
    private lateinit var setupHint: TextView

    /** Set while the user is in the system keyboard list; we bring them back once enabled. */
    private var waitingForEnable = false
    /** Show the keyboard chooser as soon as this screen has focus again. */
    private var pickerPending = false

    /**
     * Android does not let any app switch itself on as a keyboard (that protects users from
     * hidden keyloggers). We make that one step as short as possible: open the exact system
     * screen, notice the moment the switch is turned on, come straight back here and open
     * the "choose keyboard" dialog automatically.
     */
    private val enabledObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (waitingForEnable && isEnabled()) {
                waitingForEnable = false
                pickerPending = true
                startActivity(
                    Intent(this@SettingsActivity, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            refreshSetupState()
        }
    }

    private fun isEnabled(): Boolean =
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.any { it.packageName == packageName }

    private fun isSelected(): Boolean =
        SystemSettings.Secure.getString(contentResolver, SystemSettings.Secure.DEFAULT_INPUT_METHOD).orEmpty().startsWith("$packageName/")

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 draws apps edge-to-edge (behind the status and navigation bars).
        // Opt in on every version and pad for the system bars ourselves, so nothing
        // (like the "Enable" button) ends up hidden under a bar.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val appBar = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#078930"))
            setPadding(pad, pad, pad, pad)
        }
        root.addView(appBar)
        @Suppress("DEPRECATION")
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        appBar.text = getString(R.string.app_name) + (version?.let { "  ·  v$it" } ?: "")
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.setup_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        })
        enableButton = Button(this).apply {
            setOnClickListener {
                waitingForEnable = true
                startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS))
                Toast.makeText(this@SettingsActivity, R.string.setup_enable_toast, Toast.LENGTH_LONG).show()
            }
        }
        selectButton = Button(this).apply {
            setOnClickListener { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        }
        setupHint = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
        header.addView(setupHint)
        header.addView(enableButton)
        header.addView(selectButton)
        header.addView(EditText(this).apply {
            hint = getString(R.string.try_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(TextView(this).apply {
            text = getString(R.string.phonetic_help)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, pad / 2, 0, 0)
        })
        root.addView(header)

        val container = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            appBar.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad)
            v.setPadding(bars.left, 0, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(container.id, SettingsFragment()).commit()
        }
        LanguageModels.load(this)
        contentResolver.registerContentObserver(
            SystemSettings.Secure.getUriFor(SystemSettings.Secure.ENABLED_INPUT_METHODS), false, enabledObserver,
        )
        contentResolver.registerContentObserver(
            SystemSettings.Secure.getUriFor(SystemSettings.Secure.DEFAULT_INPUT_METHOD), false, enabledObserver,
        )
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(enabledObserver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        refreshSetupState()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The input method picker is a dialog; re-check once it closes.
        if (hasFocus) {
            refreshSetupState()
            if (pickerPending && isEnabled() && !isSelected()) {
                pickerPending = false
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
    }

    private fun refreshSetupState() {
        val enabled = isEnabled()
        val selected = isSelected()
        enableButton.text = getString(if (enabled) R.string.setup_enabled else R.string.setup_enable)
        selectButton.text = getString(if (selected) R.string.setup_selected else R.string.setup_select)
        selectButton.isEnabled = enabled
        // Once set up, the setup buttons step back and the hint explains quick settings.
        enableButton.visibility = if (enabled && selected) View.GONE else View.VISIBLE
        selectButton.visibility = if (enabled && selected) View.GONE else View.VISIBLE
        setupHint.text = getString(
            when {
                !enabled -> R.string.setup_hint_enable
                !selected -> R.string.setup_hint_select
                else -> R.string.setup_hint_done
            },
        )
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val io = Executors.newSingleThreadExecutor()

        /** System photo picker: no storage permission needed. */
        private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importBackground { ctx -> BackgroundImage.importFromUri(ctx, uri) }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Same device-protected store the keyboard reads (see Settings.prefs).
            Settings.prefs(requireContext())
            preferenceManager.setStorageDeviceProtected()
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Custom colours only matter for the custom theme.
            val customColors = findPreference<Preference>("custom_colors")
            findPreference<ListPreference>("theme")?.let { theme ->
                customColors?.isVisible = theme.value == "custom"
                theme.setOnPreferenceChangeListener { _, v -> customColors?.isVisible = v == "custom"; true }
            }

            findPreference<Preference>("bg_gallery")?.setOnPreferenceClickListener {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                true
            }
            findPreference<Preference>("bg_url")?.setOnPreferenceClickListener { askForImageLink(); true }
            findPreference<Preference>("bg_remove")?.setOnPreferenceClickListener {
                val app = requireContext().applicationContext
                io.execute {
                    BackgroundImage.remove(app)
                    activity?.runOnUiThread {
                        bumpBackgroundStamp()
                        updateBackgroundState()
                        toast(getString(R.string.bg_removed))
                    }
                }
                true
            }
            updateBackgroundState()
            findPreference<Preference>("history_clear")?.setOnPreferenceClickListener {
                LanguageModels.load(requireContext()) {
                    LanguageModels.clearHistory(requireContext())
                    toast(getString(R.string.history_cleared))
                }
                true
            }
            findPreference<Preference>("clear_learned")?.apply {
                updateLearnedSummary(this)
                setOnPreferenceClickListener {
                    AlertDialog.Builder(requireContext())
                        .setMessage(R.string.pref_clear_confirm)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            LanguageModels.load(requireContext()) {
                                LanguageModels.clearUserData(requireContext())
                                updateLearnedSummary(this)
                                Toast.makeText(requireContext(), R.string.cleared, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            }
        }

        private fun askForImageLink() {
            val ctx = requireContext()
            val pad = (20 * ctx.resources.displayMetrics.density).toInt()
            val field = EditText(ctx).apply {
                hint = getString(R.string.pref_bg_url_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                isSingleLine = true
            }
            val box = FrameLayout(ctx).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.pref_bg_url)
                .setView(box)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val link = field.text.toString().trim()
                    if (link.isNotEmpty()) importBackground { c -> BackgroundImage.importFromUrl(c, link) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        /** Runs an import off the main thread, then tells the keyboard to reload the picture. */
        private fun importBackground(block: (android.content.Context) -> Unit) {
            val app = requireContext().applicationContext
            toast(getString(R.string.bg_loading))
            io.execute {
                val error = runCatching { block(app) }.exceptionOrNull()
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (error == null) {
                        bumpBackgroundStamp()
                        updateBackgroundState()
                        toast(getString(R.string.bg_done))
                    } else {
                        toast(getString(R.string.bg_failed, error.message ?: error.javaClass.simpleName))
                    }
                }
            }
        }

        private fun bumpBackgroundStamp() {
            Settings.prefs(requireContext()).edit().putLong(Settings.KEY_BG_STAMP, System.currentTimeMillis()).apply()
        }

        private fun updateBackgroundState() {
            val has = BackgroundImage.exists(requireContext())
            findPreference<Preference>("bg_remove")?.apply {
                isEnabled = has
                summary = if (has) null else getString(R.string.bg_none)
            }
            findPreference<Preference>("bg_dim")?.isEnabled = has
            findPreference<Preference>("key_opacity")?.isEnabled = has
        }

        private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

        override fun onDestroy() {
            io.shutdown()
            super.onDestroy()
        }

        private fun updateLearnedSummary(p: Preference) {
            LanguageModels.load(requireContext().applicationContext) {
                if (!isAdded) return@load
                val (am, en) = LanguageModels.learnedCounts()
                p.summary = getString(R.string.pref_clear_summary, am, en)
            }
        }
    }
}
