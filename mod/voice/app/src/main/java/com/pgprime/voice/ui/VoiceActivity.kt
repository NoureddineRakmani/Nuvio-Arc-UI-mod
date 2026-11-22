package com.pgprime.voice.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.pgprime.voice.R
import com.pgprime.voice.route.Action
import com.pgprime.voice.route.Actions
import com.pgprime.voice.route.Apps
import com.pgprime.voice.route.VoiceAi
import com.pgprime.voice.route.Router
import com.pgprime.voice.speech.GoogleHearing
import com.pgprime.voice.speech.TAG
import java.util.concurrent.Executors

/**
 * What the remote's mic button opens: a small bar over whatever is on screen. Listens (Google's speech
 * recognition), then opens the right place.
 */
class VoiceActivity : Activity(), GoogleHearing.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor()
    private lateinit var dot: View
    private lateinit var text: TextView
    private var hearing: GoogleHearing? = null
    private var pressedAt = 0L
    @Volatile private var closed = false
    /** First press = French (the box's language), each further press switches: her choice for French + English. */
    private var english = false
    private var listening = false
    /** One quiet retry when Google gives up at once (it sometimes ends on a short sound: box log 2026-11-22). */
    private var retried = false
    private var listenedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pressedAt = SystemClock.elapsedRealtime()
        Log.d(TAG, "opened by ${intent?.action}")
        setContentView(bar())
        listen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Mic pressed again while the bar is open: start over, in French. English is OK on the bar (onKeyDown):
        // her remote drops a quick second mic press, so a double press can't be the switch.
        english = false
        retried = false
        Log.d(TAG, "mic again: French")
        listen()
    }

    private fun switchLanguage(by: String) {
        english = !english
        Log.d(TAG, "$by: ${if (english) "English" else "French"}")
        listen()
    }

    /** OK while the bar listens = the other language. Her remote can't double-press the mic (box log 2026-11-22). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (ok && listening) {
            // OK only goes to English (pressing it twice must not flip back to French: box log 2026-11-22)
            if (event.repeatCount == 0 && !english) switchLanguage("ok")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), ASK_MIC)
            return
        }
        listening = true
        listenedAt = SystemClock.elapsedRealtime()
        show(getString(if (english) R.string.listening_en else R.string.listening), dim = !english)
        (dot.background as? GradientDrawable)?.setColor(if (english) ENGLISH_DOT else 0xFFFFFFFF.toInt())
        (hearing ?: GoogleHearing(this, this).also { hearing = it }).start(if (english) "en-US" else "fr-FR")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != ASK_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen() else fail(getString(R.string.no_mic_permission))
    }

    override fun onLevel(level: Float) {
        dot.scaleX = 1f + level; dot.scaleY = 1f + level; dot.alpha = 0.5f + level / 2
    }

    override fun onPartial(words: String) = show(words, dim = true)

    override fun onHeard(words: String) {
        listening = false
        Log.d(TAG, "press to text: ${SystemClock.elapsedRealtime() - pressedAt} ms")
        show(words, dim = true); dot.alpha = 0.35f; dot.scaleX = 1f; dot.scaleY = 1f
        val heard = words
        work.execute {
            if (closed) return@execute
            val apps = Apps.launchable(applicationContext)
            var action = Router.route(heard, apps)
            // AI only for PG Prime: her call (2026-11-22), YouTube gets the words as heard.
            val searches = action is Action.Prime
            if (searches && VoiceAi.key(applicationContext) != null) {
                action = VoiceAi.route(applicationContext, heard, action!!, apps)
            }
            Log.d(TAG, "heard \"$heard\" -> $action")
            main.post { act(heard, action) }
        }
    }

    override fun onFailed(reason: GoogleHearing.Failure) {
        listening = false
        if (reason == GoogleHearing.Failure.NO_SPEECH && !retried && !closed &&
            SystemClock.elapsedRealtime() - listenedAt < QUICK_GIVE_UP_MS) {
            retried = true
            Log.d(TAG, "google gave up at once: listening again")
            listen()
            return
        }
        fail(getString(when (reason) {
            GoogleHearing.Failure.NO_ENGINE -> R.string.no_engine
            GoogleHearing.Failure.NO_NETWORK -> R.string.no_network
            GoogleHearing.Failure.BUSY -> R.string.no_mic
            GoogleHearing.Failure.NO_SPEECH -> R.string.not_understood
        }))
    }

    private fun act(said: String, action: Action?) {
        if (action == null) { fail(getString(R.string.not_understood)); return }
        show(said, dim = false)
        if (Actions.run(this, action)) {
            main.postDelayed(::close, if (action is Action.Media) SHOW_COMMAND_MS else 0L)
        } else {
            fail(when (action) {
                is Action.Prime -> getString(R.string.no_prime)
                is Action.NoApp -> getString(R.string.no_app, action.name)
                else -> getString(R.string.cant_open)
            })
        }
    }

    private fun fail(message: String) {
        show(message, dim = false)
        dot.alpha = 0.35f
        main.postDelayed(::close, SHOW_PROBLEM_MS)
    }

    private fun close() {
        if (closed) return
        closed = true
        hearing?.cancel()
        finish()
    }

    @Deprecated("Back closes the bar")
    override fun onBackPressed() = close()

    override fun onStop() {
        super.onStop()
        // Something else came on screen: stop listening, nothing to do any more.
        if (!isChangingConfigurations) close()
    }

    private fun show(message: String, dim: Boolean) {
        text.text = message
        text.alpha = if (dim) 0.7f else 1f
    }

    private fun bar(): View {
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
        dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) }
            alpha = 0.5f
        }
        text = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(100).toFloat(); setColor(BAR_COLOR) }
            setPadding(dp(28), dp(18), dp(32), dp(18))
            minimumWidth = dp(360)
            addView(dot, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(18) })
            addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return FrameLayout(this).apply {
            addView(pill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(56) })
        }
    }

    private companion object {
        const val ASK_MIC = 1
        const val SHOW_PROBLEM_MS = 2_500L
        const val QUICK_GIVE_UP_MS = 2_500L
        const val SHOW_COMMAND_MS = 700L
        /** Same dark glass as PG Prime's (ModLook.GlassTint at its plain strength). */
        const val BAR_COLOR = 0xF0161618.toInt()
        /** The dot turns blue while listening in English, so the switch is visible at a glance. */
        const val ENGLISH_DOT = 0xFF4DA3FF.toInt()
    }
}
