// PG Voice: the bridge between Kotlin and whisper.cpp.
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "PgVoiceNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

static void quiet_log(enum ggml_log_level level, const char *text, void *user) {
    (void) user;
    if (level >= GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

JNIEXPORT jlong JNICALL
Java_com_pgprime_voice_speech_WhisperNative_load(JNIEnv *env, jclass cls, jstring path) {
    (void) cls;
    whisper_log_set(quiet_log, NULL);
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    struct whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong) (intptr_t) ctx;
}

JNIEXPORT void JNICALL
Java_com_pgprime_voice_speech_WhisperNative_free(JNIEnv *env, jclass cls, jlong ptr) {
    (void) env; (void) cls;
    if (ptr) whisper_free((struct whisper_context *) (intptr_t) ptr);
}

/*
 * language: "fr", "en" or "auto". audioCtx: 0 = full 30 s window; smaller = faster for short speech.
 * Returns UTF-8 bytes of "<lang>\t<text>" (bytes, since Whisper can emit text the JNI string call rejects), or null.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_pgprime_voice_speech_WhisperNative_transcribe(JNIEnv *env, jclass cls, jlong ptr, jfloatArray samples,
                                                       jint threads, jstring language, jint audioCtx, jint maxTokens, jstring prompt) {
    (void) cls;
    struct whisper_context *ctx = (struct whisper_context *) (intptr_t) ptr;
    if (!ctx) return NULL;
    jsize n = (*env)->GetArrayLength(env, samples);
    jfloat *pcm = (*env)->GetFloatArrayElements(env, samples, NULL);
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);
    const char *pr = prompt ? (*env)->GetStringUTFChars(env, prompt, NULL) : NULL;

    struct whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads;
    wp.language = lang;
    wp.translate = false;
    wp.no_context = true;
    wp.no_timestamps = true;
    wp.single_segment = true;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_special = false;
    wp.print_timestamps = false;
    wp.suppress_blank = true;
    wp.suppress_nst = true;
    wp.audio_ctx = audioCtx;
    wp.max_tokens = maxTokens;
    wp.initial_prompt = pr;
    wp.temperature_inc = 0.0f; // no slow retries; a short command is either heard or not

    int rc = whisper_full(ctx, wp, pcm, n);
    (*env)->ReleaseFloatArrayElements(env, samples, pcm, JNI_ABORT);
    if (pr) (*env)->ReleaseStringUTFChars(env, prompt, pr);
    (*env)->ReleaseStringUTFChars(env, language, lang);
    if (rc != 0) return NULL;

    char out[2048];
    const char *found = whisper_lang_str(whisper_full_lang_id(ctx));
    snprintf(out, sizeof(out), "%s\t", found ? found : "?");
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        const char *t = whisper_full_get_segment_text(ctx, i);
        if (t && strlen(out) + strlen(t) < sizeof(out) - 1) strcat(out, t);
    }
    jsize len = (jsize) strlen(out);
    jbyteArray bytes = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (const jbyte *) out);
    return bytes;
}
