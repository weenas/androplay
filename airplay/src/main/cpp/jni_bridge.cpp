#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "audio_sink.h"
#include "video_sink.h"

extern "C" {
#include "dnssd.h"
#include "logger.h"
#include "raop.h"
#include "stream.h"
}

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AndroPlayProtocol", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AndroPlayProtocol", __VA_ARGS__)

namespace {
/* AirPlay compression type (ct) reported by audio_get_format and in each audio packet. */
constexpr unsigned char kAudioCtAacEld = 8;

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

void audioProcess(void *, raop_ntp_t *, audio_decode_struct *data) {
    if (!data) return;
    // Only AAC-ELD (screen mirroring) has a decoder on the Kotlin side so far.
    if (data->ct != kAudioCtAacEld) return;
    androplay::dispatchAudio(data->data, data->data_len, static_cast<int64_t>(data->ntp_time_remote / 1000));
}

void videoProcess(void *, raop_ntp_t *, video_decode_struct *data) {
    if (!data) return;
    androplay::dispatchVideo(data->data, data->data_len, static_cast<int64_t>(data->ntp_time_remote / 1000));
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

void connectionReset(void *, int reason) {
    LOGI("AirPlay connection reset (reason %d)", reason);
    androplay::dispatchSessionEnd();
}

void videoReset(void *, reset_type_t type) {
    LOGI("AirPlay video reset (type %d)", static_cast<int>(type));
}

void audioGetFormat(void *, unsigned char *ct, unsigned short *spf, bool *usingScreen, bool *isMedia,
                    uint64_t *audioFormat) {
    LOGI("Audio format ct=%u spf=%u usingScreen=%d isMedia=%d audioFormat=0x%llx", *ct, *spf,
         *usingScreen, *isMedia, static_cast<unsigned long long>(*audioFormat));
    if (*ct != kAudioCtAacEld) LOGI("Audio format ct=%u has no decoder yet; audio will be silent", *ct);
}

/*
 * UxPlay invokes many callbacks without null checks, so every slot is populated.
 * The ones below have no Android behaviour yet.
 */
void noop(void *) {}
double audioSetClientVolume(void *) { return 0.0; /* dB; 0 is full volume */ }
void audioSetVolume(void *, float) {}
void audioSetMetadata(void *, const void *, int) {}
void audioSetCoverart(void *, const void *, int) {}
void audioRemoteControlId(void *, const char *, const char *) {}
void audioSetProgress(void *, uint32_t *, uint32_t *, uint32_t *) {}
void videoReportSize(void *, float *, float *, float *, float *) {}
void mirrorVideoRunning(void *, bool) {}
void reportClientRequest(void *, char *, char *, char *, bool *admit) { *admit = true; }
void displayPin(void *, char *) {}
void registerClient(void *, const char *, const char *, const char *) {}
bool checkRegister(void *, const char *) { return true; /* no registration list is kept */ }
const char *passwd(void *, int *len) { *len = 0; return nullptr; /* no password */ }
void exportDacp(void *, const char *, const char *) {}
int videoSetCodec(void *, video_codec_t) { return 0; }
void onVideoPlay(void *, const char *, const float) {}
void onVideoScrub(void *, const float) {}
void onVideoRate(void *, const float) {}
void onVideoAcquirePlaybackInfo(void *, playback_info_t *) {}
float onVideoPlaylistRemove(void *) { return 0.0f; }

void logCallback(void *, int level, const char *message) {
    const int priority = level <= LOGGER_ERR ? ANDROID_LOG_ERROR :
        (level <= LOGGER_WARNING ? ANDROID_LOG_WARN :
        (level <= LOGGER_INFO ? ANDROID_LOG_INFO : ANDROID_LOG_DEBUG));
    __android_log_print(priority, "UxPlay", "%s", message ? message : "");
}

/* Splits a DNS TXT record (length-prefixed entries) into "key=value" Java strings. */
jobjectArray txtToStringArray(JNIEnv *env, const char *txt, int length) {
    std::vector<std::string> entries;
    for (int offset = 0; txt && offset < length;) {
        int entry_len = static_cast<unsigned char>(txt[offset++]);
        if (offset + entry_len > length) break;
        entries.emplace_back(txt + offset, entry_len);
        offset += entry_len;
    }
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) return nullptr;
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(entries.size()), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (!result) return nullptr;
    for (size_t i = 0; i < entries.size(); ++i) {
        jstring value = env->NewStringUTF(entries[i].c_str());
        if (!value) return nullptr;
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

void stopLocked() {
    if (g_raop) {
        raop_stop_httpd(g_raop);
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
    JNIEnv *env, jclass, jstring deviceName, jbyteArray hardwareAddress, jstring keyFile) {
    std::lock_guard<std::mutex> lock(g_server_mutex);
    stopLocked();
    if (!deviceName || !hardwareAddress || !keyFile || env->GetArrayLength(hardwareAddress) != 6) return 0;

    static std::once_flag ntp_once;
    std::call_once(ntp_once, ntp_global_init);

    char address[6];
    env->GetByteArrayRegion(hardwareAddress, 0, 6, reinterpret_cast<jbyte *>(address));
    char device_id[18];
    snprintf(device_id, sizeof(device_id), "%02x:%02x:%02x:%02x:%02x:%02x",
             static_cast<unsigned char>(address[0]), static_cast<unsigned char>(address[1]),
             static_cast<unsigned char>(address[2]), static_cast<unsigned char>(address[3]),
             static_cast<unsigned char>(address[4]), static_cast<unsigned char>(address[5]));

    raop_callbacks_t callbacks{};
    callbacks.audio_process = audioProcess;
    callbacks.video_process = videoProcess;
    callbacks.video_pause = noop;
    callbacks.video_resume = noop;
    callbacks.conn_feedback = noop;
    callbacks.conn_reset = connectionReset;
    callbacks.video_reset = videoReset;
    callbacks.conn_init = connectionStarted;
    callbacks.conn_destroy = connectionStopped;
    callbacks.audio_flush = noop;
    callbacks.video_flush = noop;
    callbacks.audio_set_client_volume = audioSetClientVolume;
    callbacks.audio_set_volume = audioSetVolume;
    callbacks.audio_set_metadata = audioSetMetadata;
    callbacks.audio_set_coverart = audioSetCoverart;
    callbacks.audio_stop_coverart_rendering = noop;
    callbacks.audio_remote_control_id = audioRemoteControlId;
    callbacks.audio_set_progress = audioSetProgress;
    callbacks.audio_get_format = audioGetFormat;
    callbacks.video_report_size = videoReportSize;
    callbacks.mirror_video_running = mirrorVideoRunning;
    callbacks.report_client_request = reportClientRequest;
    callbacks.display_pin = displayPin;
    callbacks.register_client = registerClient;
    callbacks.check_register = checkRegister;
    callbacks.passwd = passwd;
    callbacks.export_dacp = exportDacp;
    callbacks.video_set_codec = videoSetCodec;
    callbacks.on_video_play = onVideoPlay;
    callbacks.on_video_scrub = onVideoScrub;
    callbacks.on_video_rate = onVideoRate;
    callbacks.on_video_stop = noop;
    callbacks.on_video_acquire_playback_info = onVideoAcquirePlaybackInfo;
    callbacks.on_video_playlist_remove = onVideoPlaylistRemove;

    g_raop = raop_init(&callbacks);
    if (!g_raop) {
        LOGE("raop_init failed");
        return 0;
    }
    raop_set_log_callback(g_raop, logCallback, nullptr);
#ifdef NDEBUG
    raop_set_log_level(g_raop, LOGGER_INFO);
#else
    raop_set_log_level(g_raop, LOGGER_DEBUG);
#endif

    // The key file keeps the pairing identity stable across restarts.
    const char *key_path = env->GetStringUTFChars(keyFile, nullptr);
    int init2 = key_path ? raop_init2(g_raop, 0, device_id, key_path) : -1;
    if (key_path) env->ReleaseStringUTFChars(keyFile, key_path);
    if (init2 != 0) {
        LOGE("raop_init2 failed");
        raop_destroy(g_raop);
        g_raop = nullptr;
        return 0;
    }

    const char *name = env->GetStringUTFChars(deviceName, nullptr);
    if (!name) {
        stopLocked();
        return 0;
    }
    int dnsError = 0;
    g_dnssd = dnssd_init(name, static_cast<int>(strlen(name)), address, 6, 0, &dnsError);
    env->ReleaseStringUTFChars(deviceName, name);
    if (!g_dnssd) {
        LOGE("dnssd_init failed: %d", dnsError);
        stopLocked();
        return 0;
    }

    // 0 = let the system pick free ports.
    unsigned short tcp[3] = {0, 0, 0};
    unsigned short udp[3] = {0, 0, 0};
    raop_set_tcp_ports(g_raop, tcp);
    raop_set_udp_ports(g_raop, udp);
    unsigned short port = raop_get_port(g_raop);
    if (raop_start_httpd(g_raop, &port) < 0) {
        LOGE("raop_start_httpd failed");
        stopLocked();
        return 0;
    }
    raop_set_port(g_raop, port);
    // Copies the pairing public key into the dnssd record, so it must precede building TXT.
    raop_set_dnssd(g_raop, g_dnssd);
    if (dnssd_register_raop(g_dnssd, port) != 0 || dnssd_register_airplay(g_dnssd, port) != 0) {
        LOGE("Could not build DNS-SD TXT records");
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
    std::lock_guard<std::mutex> lock(g_server_mutex);
    int length = 0;
    const char *txt = g_dnssd ? dnssd_get_airplay_txt(g_dnssd, &length) : nullptr;
    return txtToStringArray(env, txt, length);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_androplay_protocol_AirPlayNative_nativeRaopTxtRecord(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_server_mutex);
    int length = 0;
    const char *txt = g_dnssd ? dnssd_get_raop_txt(g_dnssd, &length) : nullptr;
    return txtToStringArray(env, txt, length);
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
