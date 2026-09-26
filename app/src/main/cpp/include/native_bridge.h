#pragma once

#include "airplay_engine.h"
#include <jni.h>
#include <string>

namespace castbay {

class NativeBridge {
public:
    static bool registerJni(JavaVM* jvm);
    static void unregisterJni();

private:
    static JavaVM* jvm_;
    static JNIEnv* getEnv();

    static jmethodID onStreamStartedMethodId_;
    static jmethodID onStreamStoppedMethodId_;
    static jmethodID onStateChangedMethodId_;
    static jmethodID onErrorMethodId_;
    static jmethodID onFrameInfoMethodId_;

    static jobject callbackObject_;

    static void nativeOnStreamStarted(JNIEnv* env, jobject thiz,
        jstring sourceName, jstring sourceModel,
        jint videoWidth, jint videoHeight, jint videoFps,
        jint audioSampleRate, jint audioChannels, jboolean isMirroring);

    static void nativeOnStreamStopped(JNIEnv* env, jobject thiz);
    static void nativeOnStateChanged(JNIEnv* env, jobject thiz, jint state);
    static void nativeOnError(JNIEnv* env, jobject thiz, jstring error);
    static void nativeOnFrameInfo(JNIEnv* env, jobject thiz,
        jint fps, jint bitrate, jint frameCount);
};

} // namespace castbay
