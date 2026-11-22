package com.pgprime.voice.speech

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Keeps only the last phrase heard, as a WAV in the app's own folder on the box (Android/data/com.pgprime.voice/files),
 * so recognition can be checked on her real voice. Overwritten at every press; never sent anywhere.
 */
object LastPhrase {
    fun save(context: Context, samples: FloatArray) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return
            val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(data.capacity())
            }
            File(dir, "last.wav").outputStream().use { it.write(head.array()); it.write(data.array()) }
        }
    }
}
