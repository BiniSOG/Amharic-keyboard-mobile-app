package com.binisog.amharickeyboard

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** About page: app, version, developer, features, privacy and credits. */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#078930"))
            setPadding(pad, pad, pad, pad)
        }
        header.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) },
            LinearLayout.LayoutParams((72 * dp).toInt(), (72 * dp).toInt()))
        header.addView(text(getString(R.string.app_name), 24f, bold = true, color = Color.WHITE, center = true))
        @Suppress("DEPRECATION")
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        header.addView(text(getString(R.string.about_version, version), 14f, color = Color.WHITE, center = true))
        header.addView(text(getString(R.string.about_developer), 16f, bold = true, color = Color.parseColor("#FCDD09"), center = true))

        column.addView(text(getString(R.string.about_tagline), 16f))
        section(column, R.string.about_features_title, R.string.about_features)
        section(column, R.string.about_privacy_title, R.string.about_privacy)
        section(column, R.string.about_credits_title, R.string.about_credits)
        column.addView(text(getString(R.string.about_copyright), 13f, center = true).apply { setPadding(0, pad, 0, 0) })

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header)
        root.addView(ScrollView(this).apply { addView(column) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            header.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad)
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun section(parent: LinearLayout, title: Int, body: Int) {
        parent.addView(text(getString(title), 18f, bold = true).apply { setPadding(0, (20 * resources.displayMetrics.density).toInt(), 0, 0) })
        parent.addView(text(getString(body), 15f))
    }

    private fun text(s: String, sp: Float, bold: Boolean = false, color: Int? = null, center: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        color?.let(::setTextColor)
        if (center) gravity = Gravity.CENTER_HORIZONTAL
        setLineSpacing(0f, 1.15f)
        setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
    }
}
