package com.pgprime.voice.route

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.pgprime.voice.speech.TAG

/** Carries out an [Action]. Returns false when it couldn't (app missing…). */
object Actions {
    /** PG Prime reads these from the intent (Nuvio Mod: ui/voice/ModVoiceInbox). */
    private const val EXTRA_QUERY = "pgvoice_query"
    private const val EXTRA_ASK = "pgvoice_ask"
    private const val PRIME_ACTIVITY = "com.nuvio.tv.MainActivity"

    fun run(context: Context, action: Action): Boolean {
        Log.d(TAG, "action ${action::class.simpleName}")
        return when (action) {
            is Action.OpenApp -> start(context, Apps.launchIntent(context, action.app.packageName))
            is Action.YouTube -> {
                val uri = if (action.query.isEmpty()) Uri.parse("https://www.youtube.com/")
                else Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(action.query))
                start(context, Intent(Intent.ACTION_VIEW, uri).setPackage(Apps.SMARTTUBE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            is Action.Prime -> {
                val pkg = Apps.prime(context) ?: return false
                start(context, Intent(Intent.ACTION_MAIN).setClassName(pkg, PRIME_ACTIVITY)
                    .putExtra(EXTRA_QUERY, action.query)
                    .putExtra(EXTRA_ASK, action.ask)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            is Action.Media -> { media(context, action.key); true }
            is Action.NoApp -> false
            Action.Home -> start(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun start(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "nothing opens $intent")
            false
        } catch (e: SecurityException) {
            Log.d(TAG, "not allowed to open $intent")
            false
        }
    }

    private fun media(context: Context, key: MediaKey) {
        val audio = context.getSystemService(AudioManager::class.java)
        when (key) {
            MediaKey.VOLUME_UP -> repeat(3) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
            MediaKey.VOLUME_DOWN -> repeat(3) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
            MediaKey.MUTE -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            else -> {
                val code = when (key) {
                    MediaKey.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                    MediaKey.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    MediaKey.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    else -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                }
                val now = SystemClock.uptimeMillis()
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
            }
        }
    }
}
