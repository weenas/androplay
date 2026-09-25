#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <cstring>
#include <mutex>

#include "audio_sink.h"
#include "video_sink.h"

extern "C" {
#include "dnssd.h"
#include "dnssdint.h"
#include "global.h"
#include "raop.h"
#include "stream.h"
}

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AndroPlayProtocol", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AndroPlayProtocol", __VA_ARGS__)

namespace {
std::mutex g_server_mutex;
raop_t *g_raop = nullptr;
dnssd_t *g_dnssd = nullptr;
jclass g_native_class = nullptr;
jmethodID g_on_connection_started = nullptr;

JNIEnv *currentEnv() {
    JavaVM *vm = androplay::jvm();
    if (!vm) return nullptr;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    if (vm->AttachCurrentThread(&env, nullptr) == JNI_OK) return env;
    return nullptr;
}

void audioProcess(void *, raop_ntp_t *, aac_decode_struct *data) {
    if (!data) return;
    androplay::dispatchAudio(data->data, data->data_len, static_cast<int64_t>(data->pts));
}

void videoProcess(void *, raop_ntp_t *, h264_decode_struct *data) {
    if (!data) return;
    androplay::dispatchVideo(data->data, data->data_len, static_cast<int64_t>(data->pts));
}

void connectionStarted(void *) {
    LOGI("AirPlay sender connected");
    JNIEnv *env = currentEnv();
    if (!env || !g_native_class || !g_on_connection_started) return;
    env->CallStaticVoidMethod(g_native_class, g_on_connection_started);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

void connectionStopped(void *) {
    LOGI("AirPlay sender disconnected");
    androplay::dispatchSessionEnd();
}

void logCallback(void *, int level, const char *message) {
    const int priority = level <= RAOP_LOG_ERR ? ANDROID_LOG_ERROR :
        (level <= RAOP_LOG_WARNING ? ANDROID_LOG_WARN : ANDROID_LOG_DEBUG);
    __android_log_print(priority, "RPiPlay", "%s", message ? message : "");
}

/*
 * TXT records must match what the protocol core reports in /info and uses during
 * the handshake, so they come straight from RPiPlay's dnssdint.h / global.h.
 * "deviceid" is added by the Kotlin advertiser from the same hardware address.
 */
const char *const kAirPlayTxt[] = {
    "features=" AIRPLAY_FEATURES,
    "flags=" AIRPLAY_FLAGS,
    "model=" GLOBAL_MODEL,
    "pk=" AIRPLAY_PK,
    "pi=" AIRPLAY_PI,
    "srcvers=" AIRPLAY_SRCVERS,
    "vv=" AIRPLAY_VV,
};

const char *const kRaopTxt[] = {
    "ch=" RAOP_CH,
    "cn=" RAOP_CN,
    "da=" RAOP_DA,
    "et=" RAOP_ET,
    "vv=" RAOP_VV,
    "ft=" RAOP_FT,
    "am=" GLOBAL_MODEL,
    "md=" RAOP_MD,
    "rhd=" RAOP_RHD,
    "pw=false",
    "sr=" RAOP_SR,
    "ss=" RAOP_SS,
    "sv=" RAOP_SV,
    "tp=" RAOP_TP,
    "txtvers=" RAOP_TXTVERS,
    "sf=" RAOP_SF,
    "vs=" RAOP_VS,
    "vn=" RAOP_VN,
    "pk=" RAOP_PK,
};

template <size_t N>
jobjectArray toStringArray(JNIEnv *env, const char *const (&values)[N]) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) return nullptr;
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(N), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (!result) return nullptr;
    for (size_t i = 0; i < N; ++i) {
        jstring value = env->NewStringUTF(values[i]);
        if (!value) return nullptr;
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

void stopLocked() {
    if (g_raop) {
        raop_stop(g_raop);
        raop_destroy(g_raop);
        g_raop = nullptr;
    }
    if (g_dnssd) {
        dnssd_destroy(g_dnssd);
        g_dnssd = nullptr;
    }
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeStart(
    JNIEnv *env, jclass, jstring deviceName, jbyteArray hardwareAddress) {
    std::lock_guard<std::mutex> lock(g_server_mutex);
    stopLocked();
    if (!deviceName || !hardwareAddress || env->GetArrayLength(hardwareAddress) != 6) return 0;

    const char *name = env->GetStringUTFChars(deviceName, nullptr);
    if (!name) return 0;
    char address[6];
    env->GetByteArrayRegion(hardwareAddress, 0, 6, reinterpret_cast<jbyte *>(address));

    raop_callbacks_t callbacks{};
    callbacks.audio_process = audioProcess;
    callbacks.video_process = videoProcess;
    callbacks.conn_init = connectionStarted;
    callbacks.conn_destroy = connectionStopped;

    g_raop = raop_init(4, &callbacks);
    if (!g_raop) {
        env->ReleaseStringUTFChars(deviceName, name);
        LOGE("raop_init failed");
        return 0;
    }
    raop_set_log_callback(g_raop, logCallback, nullptr);
    raop_set_log_level(g_raop, RAOP_LOG_INFO);

    int dnsError = 0;
    g_dnssd = dnssd_init(name, static_cast<int>(strlen(name)), address, 6, &dnsError);
    env->ReleaseStringUTFChars(deviceName, name);
    if (!g_dnssd) {
        LOGE("dnssd_init failed: %d", dnsError);
        stopLocked();
        return 0;
    }
    raop_set_dnssd(g_raop, g_dnssd);

    unsigned short port = 0;
    if (raop_start(g_raop, &port) < 0) {
        LOGE("raop_start failed");
        stopLocked();
        return 0;
    }
    LOGI("AirPlay protocol listening on port %u", port);
    return static_cast<jint>(port);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeIsRunning(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_server_mutex);
    return g_raop && raop_is_running(g_raop) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeStop(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_server_mutex);
    stopLocked();
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeAirPlayTxtRecord(JNIEnv *env, jclass) {
    return toStringArray(env, kAirPlayTxt);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeRaopTxtRecord(JNIEnv *env, jclass) {
    return toStringArray(env, kRaopTxt);
}

extern "C" JNIEXPORT void JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeSetVideoSink(JNIEnv *env, jclass, jobject sink) {
    androplay::setVideoSink(env, sink);
}

extern "C" JNIEXPORT void JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeSetAudioSink(JNIEnv *env, jclass, jobject sink) {
    androplay::setAudioSink(env, sink);
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    androplay::initJvm(vm);
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = env->FindClass("com/androplay/protocol/AirPlayNative");
    if (!local) return JNI_ERR;
    g_native_class = reinterpret_cast<jclass>(env->NewGlobalRef(local));
    g_on_connection_started = env->GetStaticMethodID(local, "onConnectionStarted", "()V");
    env->DeleteLocalRef(local);
    return JNI_VERSION_1_6;
}
