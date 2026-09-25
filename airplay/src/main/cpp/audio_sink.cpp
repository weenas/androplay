#include "audio_sink.h"
#include "video_sink.h"

#include <android/log.h>
#include <mutex>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AndroPlayAudioSink", __VA_ARGS__)

namespace {
jobject g_sink = nullptr;
jmethodID g_on_audio = nullptr;
jmethodID g_on_pcm = nullptr;
jmethodID g_on_flush = nullptr;
std::mutex g_mutex;

JNIEnv *currentEnv() {
    JavaVM *vm = androplay::jvm();
    if (!vm) return nullptr;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    if (vm->AttachCurrentThread(&env, nullptr) == JNI_OK) return env;
    return nullptr;
}
}

namespace androplay {
void setAudioSink(JNIEnv *env, jobject sink) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sink) {
        env->DeleteGlobalRef(g_sink);
        g_sink = nullptr;
        g_on_audio = nullptr;
        g_on_pcm = nullptr;
        g_on_flush = nullptr;
    }
    if (!sink) return;
    g_sink = env->NewGlobalRef(sink);
    jclass cls = env->GetObjectClass(sink);
    g_on_audio = env->GetMethodID(cls, "onAudioData", "([BJ)V");
    g_on_pcm = env->GetMethodID(cls, "onPcmData", "([BJ)V");
    g_on_flush = env->GetMethodID(cls, "onAudioFlush", "()V");
    env->DeleteLocalRef(cls);
    if (!g_on_audio || !g_on_pcm || !g_on_flush) LOGE("Audio sink methods were not found");
}

namespace {
/* Caller holds g_mutex. */
void callWithBytes(jmethodID method, const void *data, int length, int64_t ptsUs) {
    if (!g_sink || !method || !data || length <= 0) return;
    JNIEnv *env = currentEnv();
    if (!env) return;
    jbyteArray bytes = env->NewByteArray(length);
    if (!bytes) return;
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(data));
    env->CallVoidMethod(g_sink, method, bytes, static_cast<jlong>(ptsUs));
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(bytes);
}
}  // namespace

void dispatchAudio(const uint8_t *data, int length, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(g_mutex);
    callWithBytes(g_on_audio, data, length, ptsUs);
}

void dispatchPcm(const int16_t *samples, int count, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(g_mutex);
    callWithBytes(g_on_pcm, samples, count * static_cast<int>(sizeof(int16_t)), ptsUs);
}

void dispatchAudioFlush() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_sink || !g_on_flush) return;
    JNIEnv *env = currentEnv();
    if (!env) return;
    env->CallVoidMethod(g_sink, g_on_flush);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}
}

