package com.pgprime.voice.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records from the remote's mic until the person stops talking. No sound leaves the box: the samples
 * only go to [WhisperEngine].
 */
class MicRecorder(private val listener: Listener) {
    interface Listener {
        /** 0..1, for the listening dot. Called on the recording thread. */
        fun onLevel(level: Float)
        fun onSpeechStarted()
        fun onDone(samples: FloatArray)
        fun onFailed(reason: Failure)
    }

    enum class Failure { NO_MIC, MIC_SILENT, NO_SPEECH }

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::record, "pgvoice-mic").also { it.start() }
    }

    fun cancel() {
        running = false
    }

    /** Stop now and hand over what was heard so far (an app pressed "done"). */
    fun finishNow() {
        finishEarly = true
    }

    @Volatile private var finishEarly = false

    @SuppressLint("MissingPermission") // asked for before start()
    private fun record() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, FRAME * 8) * 2)
        }.getOrNull()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            finish(Failure.NO_MIC)
            return
        }
        val started = SystemClock.elapsedRealtime()
        val frame = ShortArray(FRAME)
        val kept = ArrayList<ShortArray>()
        var floorDb = Float.MAX_VALUE
        var loudFrames = 0
        var speechAt = -1L
        var lastLoud = 0L
        var lastLoudFrame = 0
        var anySound = false
        var peakDb = -120f
        val trace = StringBuilder()
        var traceMax = -120f
        try {
            recorder.startRecording()
            Log.d(TAG, "mic open after ${SystemClock.elapsedRealtime() - started} ms")
            while (running) {
                val read = recorder.read(frame, 0, FRAME)
                if (read <= 0) continue
                val now = SystemClock.elapsedRealtime()
                val db = levelDb(frame, read)
                if (db > SILENT_DB) anySound = true
                // Digital silence (the remote's mic still opening) says nothing about the room: skip it.
                if (db > SILENT_DB && (now - started < FLOOR_MS || speechAt < 0)) floorDb = minOf(floorDb, db)
                val loud = db > SILENT_DB && db > floorDb + RISE_DB
                traceMax = max(traceMax, db)
                if (kept.size % 5 == 4) { trace.append(traceMax.toInt()).append(' '); traceMax = -120f }
                listener.onLevel(((db - floorDb) / 30f).coerceIn(0f, 1f))
                kept += frame.copyOf(read)
                if (speechAt < 0) {
                    loudFrames = if (loud) loudFrames + 1 else 0
                    if (loudFrames >= START_FRAMES) {
                        speechAt = now
                        lastLoud = now
                        lastLoudFrame = kept.size
                        Log.d(TAG, "speech began after ${now - started} ms")
                        listener.onSpeechStarted()
                    } else if (finishEarly) {
                        finish(Failure.NO_SPEECH)
                        return
                    } else {
                        while (kept.size > PREROLL_FRAMES) kept.removeAt(0)
                        if (now - started > WAIT_FOR_SPEECH_MS) {
                            finish(if (anySound) Failure.NO_SPEECH else Failure.MIC_SILENT)
                            return
                        }
                    }
                } else {
                    // Between words the level dips; after the last word it stays well under the voice's peak.
                    peakDb = max(peakDb, db)
                    if (loud && db > peakDb - END_DROP_DB) { lastLoud = now; lastLoudFrame = kept.size }
                    if (finishEarly || now - lastLoud > END_SILENCE_MS || now - speechAt > LONGEST_MS) {
                        Log.d(TAG, "speech ended, ${now - speechAt} ms long; floor ${floorDb.toInt()} dB, peak ${peakDb.toInt()} dB")
                        Log.d(TAG, "levels per 100 ms: $trace")
                        running = false
                        // The silence after the last word is only waiting time: Whisper doesn't need it (shorter = faster).
                        listener.onDone(toFloats(kept.subList(0, minOf(kept.size, lastLoudFrame + TAIL_FRAMES))))
                        return
                    }
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun finish(failure: Failure) {
        running = false
        Log.d(TAG, "listening failed: $failure")
        listener.onFailed(failure)
    }

    private fun levelDb(frame: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) { val s = frame[i] / 32768.0; sum += s * s }
        val rms = sqrt(sum / count)
        return if (rms <= 1e-9) -120f else (20 * log10(rms)).toFloat()
    }

    private fun toFloats(frames: List<ShortArray>): FloatArray {
        val out = FloatArray(frames.sumOf { it.size })
        var i = 0
        for (f in frames) for (s in f) out[i++] = s / 32768f
        return out
    }

    private companion object {
        const val RATE = 16_000
        const val FRAME = 320 // 20 ms
        const val FLOOR_MS = 300L
        const val RISE_DB = 12f
        const val SILENT_DB = -70f
        const val START_FRAMES = 3
        const val PREROLL_FRAMES = 15 // keep 300 ms before speech, so the first word is whole
        const val TAIL_FRAMES = 12 // keep 240 ms after the last loud sound, so the last word is whole
        const val END_SILENCE_MS = 600L
        const val WAIT_FOR_SPEECH_MS = 6_000L
        const val LONGEST_MS = 8_000L
        const val END_DROP_DB = 25f
    }
}
