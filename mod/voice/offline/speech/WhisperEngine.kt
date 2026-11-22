package com.pgprime.voice.speech

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.ceil
import kotlin.math.min

const val TAG = "PgVoice"

/** What Whisper heard: the words and the language it recognised ("fr", "en", …). */
data class Heard(val text: String, val language: String)

/**
 * Offline speech-to-text on the box. The model is loaded once and kept while the app's process lives,
 * so only the first press after a restart pays for loading it.
 */
object WhisperEngine {
    private const val ASSET = "model.bin"
    private const val SAMPLE_RATE = 16_000
    /** Whisper's encoder covers 30 s in 1500 steps; short speech only needs the part it fills. */
    private const val CTX_PER_SECOND = 50
    private const val CTX_MARGIN = 64
    private const val FULL_CTX = 1500

    /** Names Whisper should spell right. Passed as context, not as words it must find. */
    private const val PROMPT = "YouTube, SmartTube, PG Prime, Nuvio, Scout, Arte, TF1, Orange."

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "pgvoice-whisper").apply { priority = Thread.MAX_PRIORITY } }
    @Volatile private var context = 0L
    private var loading: Future<Long>? = null

    /** Starts loading the model in the background; call as early as possible. */
    @Synchronized
    fun warm(appContext: Context): Future<Long> =
        loading ?: worker.submit<Long> {
            if (context != 0L) return@submit context
            val started = SystemClock.elapsedRealtime()
            val file = modelFile(appContext)
            context = WhisperNative.load(file.absolutePath)
            Log.d(TAG, "model loaded in ${SystemClock.elapsedRealtime() - started} ms (${file.length() / 1_000_000} MB), ok=${context != 0L}")
            context
        }.also { loading = it }

    /** Turns 16 kHz mono samples into text. Blocks; call off the main thread. */
    fun transcribe(appContext: Context, samples: FloatArray): Heard? {
        val ctx = warm(appContext).get()
        if (ctx == 0L) return null
        return worker.submit<Heard?> { run(ctx, samples) }.get()
    }

    private fun run(ctx: Long, samples: FloatArray): Heard? {
        val seconds = samples.size.toDouble() / SAMPLE_RATE
        val audioCtx = min(FULL_CTX, ceil(seconds * CTX_PER_SECOND).toInt() + CTX_MARGIN)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val started = SystemClock.elapsedRealtime()
        var heard = decode(ctx, samples, threads, "auto", audioCtx)
        // She speaks French and English; a short clip is sometimes taken for another language.
        if (heard != null && heard.language != "fr" && heard.language != "en") {
            Log.d(TAG, "heard as '${heard.language}', trying French")
            heard = decode(ctx, samples, threads, "fr", audioCtx)
        }
        Log.d(TAG, "transcribed %.1f s of speech in %d ms (ctx %d, %d threads)".format(
            seconds, SystemClock.elapsedRealtime() - started, audioCtx, threads))
        return heard?.takeUnless { isNoise(it.text) }
    }

    private fun decode(ctx: Long, samples: FloatArray, threads: Int, language: String, audioCtx: Int): Heard? {
        val raw = WhisperNative.transcribe(ctx, samples, threads, language, audioCtx, maxTokens(samples.size), PROMPT) ?: return null
        val out = String(raw, Charsets.UTF_8)
        val lang = out.substringBefore('\t')
        val text = tidy(out.substringAfter('\t').trim())
        return Heard(text, lang)
    }

    /** About 3 words a second is fast speech; a few tokens per word, plus room for a title. Stops runaway loops early. */
    private fun maxTokens(sampleCount: Int): Int = (sampleCount / SAMPLE_RATE * 6 + 12).coerceIn(16, 64)

    /**
     * With a shortened window Whisper sometimes runs on past the end: "dune dune dune",
     * "and so my fellow Americans. And...". Keeps the first copy and drops a dangling last word.
     */
    internal fun tidy(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val keys = words.map { it.lowercase().trim(',', '.', '!', '?', '…', ';', ':') }
        var kept = words
        // A phrase said twice in a row is a loop; one word needs four copies ("Bora Bora", "Tora! Tora! Tora!").
        for (k in 1..keys.size / 2) {
            val copies = if (k == 1) 4 else 2
            if (keys.size >= k * copies && (1 until copies).all { keys.subList(it * k, (it + 1) * k) == keys.subList(0, k) }) {
                kept = words.subList(0, k); break
            }
        }
        // A loop that changes slightly ("X on the view. X is a on the view."): cut where the first three words come back.
        if (kept.size >= 6) {
            val start = keys.subList(0, 3)
            val again = (3..kept.size - 3).firstOrNull { keys.subList(it, it + 3) == start }
            if (again != null) kept = words.subList(0, again)
        }
        var out = kept.joinToString(" ")
        val cut = Regex("^(.*[.!?])\\s+\\S+(\\.\\.\\.|…)$").find(out)
        if (cut != null) out = cut.groupValues[1]
        return out.trim()
    }

    /** Whisper's well-known inventions on near-silence. */
    private fun isNoise(text: String): Boolean {
        val t = text.lowercase().trim(' ', '.', '!', '?', '…')
        if (t.isEmpty()) return true
        return t.startsWith("[") || t.startsWith("(") || "amara.org" in t || "sous-titr" in t ||
            t == "merci" || t == "thank you" || t == "thanks for watching" || t == "you"
    }

    /** The model ships inside the APK; whisper.cpp reads a plain file, so it is copied out once per version. */
    private fun modelFile(appContext: Context): File {
        val file = File(appContext.noBackupFilesDir, ASSET)
        val version = appContext.packageManager.getPackageInfo(appContext.packageName, 0).lastUpdateTime
        val stamp = File(appContext.noBackupFilesDir, "$ASSET.stamp")
        if (file.exists() && stamp.exists() && stamp.readText() == version.toString()) return file
        appContext.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it, 1 shl 20) } }
        stamp.writeText(version.toString())
        return file
    }
}
