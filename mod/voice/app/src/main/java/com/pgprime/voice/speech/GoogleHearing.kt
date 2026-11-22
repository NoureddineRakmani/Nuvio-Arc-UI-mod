package com.pgprime.voice.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

const val TAG = "PgVoice"

/**
 * Hears the remote's mic through Google's speech recognition (Katniss), the same engine PG Prime's mic buttons
 * use: her choice (2026-11-22), the offline Whisper engine (now in pg-voice/offline/) heard her too badly.
 * The voice goes to Google for each press. Call everything on the main thread.
 */
class GoogleHearing(private val context: Context, private val listener: Listener) {
    interface Listener {
        /** 0..1, for the listening dot. */
        fun onLevel(level: Float)
        /** The words so far, while she talks. */
        fun onPartial(words: String)
        fun onHeard(words: String)
        fun onFailed(reason: Failure)
    }

    enum class Failure { NO_ENGINE, NO_SPEECH, NO_NETWORK, BUSY }

    private var recognizer: SpeechRecognizer? = null
    private var startedAt = 0L
    private var done = false
    private var lastWords: String? = null

    /** [language]: "fr-FR" or "en-US". Google's TV recognizer hears one language per request (see below). */
    fun start(language: String) {
        cancel()
        done = false
        lastWords = null
        val engine = engine()
        if (engine == null && !SpeechRecognizer.isRecognitionAvailable(context)) { fail(Failure.NO_ENGINE); return }
        val r = runCatching {
            if (engine != null) SpeechRecognizer.createSpeechRecognizer(context, engine) else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: run { fail(Failure.NO_ENGINE); return }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "google ($language) ready after ${SystemClock.elapsedRealtime() - startedAt} ms") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "speech began after ${SystemClock.elapsedRealtime() - startedAt} ms") }
            override fun onRmsChanged(rmsdB: Float) { listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { Log.d(TAG, "speech ended after ${SystemClock.elapsedRealtime() - startedAt} ms") }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onLanguageDetection(results: Bundle) {
                Log.d(TAG, "google detected ${results.getString(SpeechRecognizer.DETECTED_LANGUAGE)} " +
                    "(level ${results.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL, -1)})")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let { lastWords = it; listener.onPartial(it) }
            }
            override fun onSegmentResults(segmentResults: Bundle) {
                first(segmentResults)?.let { lastWords = it }
            }
            override fun onResults(results: Bundle?) {
                if (done) return
                done = true
                // Google sometimes sends the words early and then an empty final result: keep the last words heard.
                val words = first(results) ?: lastWords
                Log.d(TAG, "google heard in ${SystemClock.elapsedRealtime() - startedAt} ms")
                if (words == null) listener.onFailed(Failure.NO_SPEECH) else listener.onHeard(words)
            }
            override fun onError(error: Int) {
                Log.d(TAG, "google error $error")
                if (done) return
                done = true
                // "No match" although words showed while she talked (short English titles): use those words.
                val shown = lastWords
                if (error == SpeechRecognizer.ERROR_NO_MATCH && shown != null) {
                    Log.d(TAG, "no match, using the words shown")
                    listener.onHeard(shown)
                    return
                }
                listener.onFailed(when (error) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> Failure.NO_NETWORK
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Failure.BUSY
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Failure.NO_ENGINE
                    else -> Failure.NO_SPEECH
                })
            }
        })
        startedAt = SystemClock.elapsedRealtime()
        logSupportOnce(r)
        // Same request as PG Prime's mic (Scout's AskScreen), with a shorter pause: these are short commands.
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // No silence length: Google's TV recognizer applies it before she starts talking too, and ended English
            // sessions after 1 s ("NO_SPEECH_DETECTED", box log 2026-11-22). Its own end-of-speech detection is used.
            // She speaks French and English, mixed; the box's own language (French) alone turned English into French
            // words ("vous êtes jolie"). Android 14's language detection, plus Google's own extra-languages list.
            // One language per press (her choice 2026-11-22: press again for English): online, Google's TV recognizer
            // ignores extra languages, and it detects the language only offline, which this box has no packs for.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            if (Build.VERSION.SDK_INT >= 34) {
                // Ignored by Google's TV recognizer online (it detects only offline, and this box has no offline packs:
                // "Soda recognizer failed to initialize: ConfigStatus 3"); kept for engines that do it online.
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, LANGUAGES)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, LANGUAGES)
            }
        })
    }

    /** Which languages Google's engine says it can do, once per run of the app: to know what to ask for. */
    private fun logSupportOnce(r: SpeechRecognizer) {
        if (supportLogged || Build.VERSION.SDK_INT < 33) return
        supportLogged = true
        runCatching {
            r.checkRecognitionSupport(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        Log.d(TAG, "google languages: installed ${support.installedOnDeviceLanguages}, " +
                            "online ${support.onlineLanguages.size} (en-US ${"en-US" in support.onlineLanguages})")
                    }
                    override fun onError(error: Int) { Log.d(TAG, "google languages: error $error") }
                })
        }
    }

    fun cancel() {
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }

    private fun fail(reason: Failure) {
        done = true
        Log.d(TAG, "listening failed: $reason")
        listener.onFailed(reason)
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)

    /** Google's engine by name, so a box whose default speech engine is something else still uses Google's. */
    private fun engine(): ComponentName? {
        val google = ComponentName(KATNISS, "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService")
        return runCatching { context.packageManager.getServiceInfo(google, 0); google }.getOrNull()
    }

    companion object {
        const val KATNISS = "com.google.android.katniss"
        private val LANGUAGES = arrayListOf("fr-FR", "en-US")
        @Volatile private var supportLogged = false
    }
}
