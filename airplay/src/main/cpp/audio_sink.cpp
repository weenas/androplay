#include "audio_sink.h"
#include "video_sink.h"

#include <android/log.h>
#include <mutex>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AndroPlayAudioSink", __VA_ARGS__)

namespace {
jobject g_sink = nullptr;
jmethodID g_on_audio = nullptr;
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
    }
    if (!sink) return;
    g_sink = env->NewGlobalRef(sink);
    jclass cls = env->GetObjectClass(sink);
    g_on_audio = env->GetMethodID(cls, "onAudioData", "([BJ)V");
    env->DeleteLocalRef(cls);
    if (!g_on_audio) LOGE("Audio sink method was not found");
}

void dispatchAudio(const uint8_t *data, int length, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_sink || !g_on_audio || !data || length <= 0) return;
    JNIEnv *env = currentEnv();
    if (!env) return;
    jbyteArray bytes = env->NewByteArray(length);
    if (!bytes) return;
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(data));
    env->CallVoidMethod(g_sink, g_on_audio, bytes, static_cast<jlong>(ptsUs));
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(bytes);
}
}

