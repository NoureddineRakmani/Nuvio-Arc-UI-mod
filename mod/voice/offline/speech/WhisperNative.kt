package com.pgprime.voice.speech

/** The calls into whisper.cpp (app/src/main/cpp/pgvoice_jni.c). */
internal object WhisperNative {
    init { System.loadLibrary("pgvoice") }

    @JvmStatic external fun load(modelPath: String): Long
    @JvmStatic external fun free(context: Long)
    @JvmStatic external fun transcribe(
        context: Long, samples: FloatArray, threads: Int, language: String, audioCtx: Int, maxTokens: Int, prompt: String?
    ): ByteArray?
}
