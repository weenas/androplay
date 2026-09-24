#include "native_bridge.h"
#include "airplay_engine.h"
#include <jni.h>
#include <android/log.h>

#define LOG_TAG "AndroPlayNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace androplay {

JavaVM* NativeBridge::jvm_ = nullptr;
JNIEnv* NativeBridge::getEnv() {
    JNIEnv* env = nullptr;
    if (jvm_->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return nullptr;
    }
    return env;
}

jmethodID NativeBridge::onStreamStartedMethodId_ = nullptr;
jmethodID NativeBridge::onStreamStoppedMethodId_ = nullptr;
jmethodID NativeBridge::onStateChangedMethodId_ = nullptr;
jmethodID NativeBridge::onErrorMethodId_ = nullptr;
jmethodID NativeBridge::onFrameInfoMethodId_ = nullptr;
jobject NativeBridge::callbackObject_ = nullptr;

bool NativeBridge::registerJni(JavaVM* jvm) {
    jvm_ = jvm;
    LOGI("NativeBridge registered");
    return true;
}

void NativeBridge::unregisterJni() {
    JNIEnv* env = getEnv();
    if (env && callbackObject_) {
        env->DeleteGlobalRef(callbackObject_);
        callbackObject_ = nullptr;
    }
    jvm_ = nullptr;
}

void NativeBridge::nativeOnStreamStarted(JNIEnv* env, jobject thiz,
    jstring sourceName, jstring sourceModel,
    jint videoWidth, jint videoHeight, jint videoFps,
    jint audioSampleRate, jint audioChannels, jboolean isMirroring) {
    LOGI("nativeOnStreamStarted");
    // This would forward to AirPlayEngine callbacks
}

void NativeBridge::nativeOnStreamStopped(JNIEnv* env, jobject thiz) {
    LOGI("nativeOnStreamStopped");
}

void NativeBridge::nativeOnStateChanged(JNIEnv* env, jobject thiz, jint state) {
    LOGI("nativeOnStateChanged: %d", state);
}

void NativeBridge::nativeOnError(JNIEnv* env, jobject thiz, jstring error) {
    LOGI("nativeOnError");
}

void NativeBridge::nativeOnFrameInfo(JNIEnv* env, jobject thiz,
    jint fps, jint bitrate, jint frameCount) {
    // Frame info callback for debug overlay
}

} // namespace androplay

extern "C" JNIEXPORT jboolean JNICALL
Java_com_androplay_service_NativeBridge_nativeStart(
    JNIEnv* env, jobject, jstring deviceName) {
    if (deviceName == nullptr) return JNI_FALSE;
    const char* name = env->GetStringUTFChars(deviceName, nullptr);
    if (name == nullptr) return JNI_FALSE;
    const bool started = androplay::AirPlayEngine::instance().start(name);
    env->ReleaseStringUTFChars(deviceName, name);
    return started ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_androplay_service_NativeBridge_nativeStop(JNIEnv*, jobject) {
    androplay::AirPlayEngine::instance().stop();
}
