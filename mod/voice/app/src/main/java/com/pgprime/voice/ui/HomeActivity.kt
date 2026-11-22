package com.pgprime.voice.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pgprime.voice.R
import com.pgprime.voice.route.Apps
import com.pgprime.voice.route.VoiceAi
import com.pgprime.voice.speech.GoogleHearing

/** PG Voice's page in the app list: is it set up, and a button to try it. */
class HomeActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        }
        status = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setLineSpacing(0f, 1.3f)
        }
        val tryIt = Button(this).apply {
            text = getString(R.string.try_it)
            setOnClickListener { startActivity(Intent(this@HomeActivity, VoiceActivity::class.java)) }
        }
        // Her Gemini key for AI search on YouTube: typed here, kept only in PG Voice's private storage, never shown.
        val keyField = EditText(this).apply {
            hint = getString(R.string.gemini_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x88FFFFFF.toInt())
        }
        val saveKey = Button(this).apply {
            text = getString(R.string.save_key)
            setOnClickListener {
                if (keyField.text.isBlank()) return@setOnClickListener
                VoiceAi.saveKey(applicationContext, keyField.text.toString())
                keyField.text.clear()
                Toast.makeText(this@HomeActivity, R.string.key_saved, Toast.LENGTH_SHORT).show()
                onResume()
            }
        }
        val removeKey = Button(this).apply {
            text = getString(R.string.remove_key)
            setOnClickListener {
                VoiceAi.saveKey(applicationContext, null)
                Toast.makeText(this@HomeActivity, R.string.key_removed, Toast.LENGTH_SHORT).show()
                onResume()
            }
        }
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(keyField, LinearLayout.LayoutParams(dp(420), -2))
            addView(saveKey, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            addView(removeKey, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        }
        val help = TextView(this).apply {
            text = getString(R.string.examples)
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.3f)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(48), dp(64), dp(48))
            addView(title)
            addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
            addView(tryIt, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24); gravity = Gravity.START })
            addView(keyRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
            addView(help, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(32) })
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF0E0E10.toInt()); addView(column) })
        tryIt.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        val me = packageName
        fun yesNo(ok: Boolean) = getString(if (ok) R.string.yes else R.string.no)
        val assistant = Settings.Secure.getString(contentResolver, "assistant").orEmpty().startsWith("$me/")
        val speech = runCatching { packageManager.getApplicationInfo(GoogleHearing.KATNISS, 0).enabled }.getOrDefault(false)
        val prime = Apps.prime(this)
        status.text = getString(R.string.status, yesNo(assistant), yesNo(speech), prime ?: getString(R.string.no),
            yesNo(VoiceAi.key(this) != null))
    }
}
