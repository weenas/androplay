#include "video_sink.h"

#include <android/log.h>
#include <mutex>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AndroPlayVideoSink", __VA_ARGS__)

namespace {
JavaVM *g_vm = nullptr;
jobject g_sink = nullptr;
jmethodID g_on_video = nullptr;
jmethodID g_on_end = nullptr;
std::mutex g_mutex;

JNIEnv *currentEnv() {
    if (!g_vm) return nullptr;
    JNIEnv *env = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
        return env;
    }
    if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) return env;
    return nullptr;
}
}

namespace androplay {
void initJvm(JavaVM *vm) { g_vm = vm; }
JavaVM *jvm() { return g_vm; }

void setVideoSink(JNIEnv *env, jobject sink) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sink) {
        env->DeleteGlobalRef(g_sink);
        g_sink = nullptr;
        g_on_video = nullptr;
        g_on_end = nullptr;
    }
    if (!sink) return;
    g_sink = env->NewGlobalRef(sink);
    jclass cls = env->GetObjectClass(sink);
    g_on_video = env->GetMethodID(cls, "onVideoData", "([BJ)V");
    g_on_end = env->GetMethodID(cls, "onSessionEnd", "()V");
    env->DeleteLocalRef(cls);
    if (!g_on_video || !g_on_end) LOGE("Video sink methods were not found");
}

void dispatchVideo(const uint8_t *data, int length, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_sink || !g_on_video || !data || length <= 0) return;
    JNIEnv *env = currentEnv();
    if (!env) return;
    jbyteArray bytes = env->NewByteArray(length);
    if (!bytes) return;
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(data));
    env->CallVoidMethod(g_sink, g_on_video, bytes, static_cast<jlong>(ptsUs));
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(bytes);
}

void dispatchSessionEnd() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_sink || !g_on_end) return;
    JNIEnv *env = currentEnv();
    if (!env) return;
    env->CallVoidMethod(g_sink, g_on_end);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}
}

