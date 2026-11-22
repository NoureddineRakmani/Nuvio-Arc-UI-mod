package com.pgprime.voice.speech

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.Executors

/**
 * The box's speech-to-text for every app (the mic buttons in SmartTube, PG Prime's search…), done on
 * the box with Whisper instead of Google's servers. Active once set as voice_recognition_service.
 */
class PgRecognitionService : RecognitionService() {
    private val work = Executors.newSingleThreadExecutor()
    private var recorder: MicRecorder? = null

    override fun onCreate() {
        super.onCreate()
        WhisperEngine.warm(applicationContext)
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        Log.d(TAG, "speech request from ${recognizerIntent?.getStringExtra("calling_package")}")
        recorder?.cancel()
        val rec = MicRecorder(object : MicRecorder.Listener {
            override fun onLevel(level: Float) {
                runCatching { listener.rmsChanged(level * 10f) }
            }
            override fun onSpeechStarted() {
                runCatching { listener.beginningOfSpeech() }
            }
            override fun onDone(samples: FloatArray) {
                runCatching { listener.endOfSpeech() }
                work.execute {
                    val heard = WhisperEngine.transcribe(applicationContext, samples)
                    val text = heard?.text?.trim()?.trimEnd('.', '!', '?')?.trim()
                    runCatching {
                        if (text.isNullOrEmpty()) listener.error(SpeechRecognizer.ERROR_NO_MATCH)
                        else listener.results(Bundle().apply {
                            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                            putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
                        })
                    }
                }
            }
            override fun onFailed(reason: MicRecorder.Failure) {
                runCatching {
                    listener.error(when (reason) {
                        MicRecorder.Failure.NO_MIC, MicRecorder.Failure.MIC_SILENT -> SpeechRecognizer.ERROR_AUDIO
                        MicRecorder.Failure.NO_SPEECH -> SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    })
                }
            }
        })
        recorder = rec
        runCatching { listener.readyForSpeech(Bundle()) }
        rec.start()
    }

    override fun onStopListening(listener: Callback) {
        recorder?.finishNow()
    }

    override fun onCancel(listener: Callback) {
        recorder?.cancel()
        recorder = null
    }
}
