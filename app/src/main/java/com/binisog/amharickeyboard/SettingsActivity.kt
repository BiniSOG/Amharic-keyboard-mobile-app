package com.binisog.amharickeyboard

import android.content.Context
import android.content.Intent
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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.binisog.amharickeyboard.engine.LanguageModels

/**
 * Launcher activity: guides the user through enabling/selecting the keyboard, offers a
 * test field and hosts all preferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var enableButton: Button
    private lateinit var selectButton: Button

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
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.setup_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        })
        enableButton = Button(this).apply {
            setOnClickListener { startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        selectButton = Button(this).apply {
            setOnClickListener { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        }
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
    }

    override fun onResume() {
        super.onResume()
        refreshSetupState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The input method picker is a dialog; re-check once it closes.
        if (hasFocus) refreshSetupState()
    }

    private fun refreshSetupState() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = SystemSettings.Secure.getString(contentResolver, SystemSettings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val selected = current.startsWith("$packageName/")
        enableButton.text = getString(if (enabled) R.string.setup_enabled else R.string.setup_enable)
        selectButton.text = getString(if (selected) R.string.setup_selected else R.string.setup_select)
        selectButton.isEnabled = enabled
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Same device-protected store the keyboard reads (see Settings.prefs).
            Settings.prefs(requireContext())
            preferenceManager.setStorageDeviceProtected()
            setPreferencesFromResource(R.xml.preferences, rootKey)
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

        private fun updateLearnedSummary(p: Preference) {
            LanguageModels.load(requireContext().applicationContext) {
                if (!isAdded) return@load
                val (am, en) = LanguageModels.learnedCounts()
                p.summary = getString(R.string.pref_clear_summary, am, en)
            }
        }
    }
}
