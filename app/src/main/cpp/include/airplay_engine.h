#pragma once

#include <string>
#include <atomic>
#include <mutex>
#include <functional>
#include <jni.h>

namespace androplay {

enum class ConnectionState {
    Idle,
    Discovering,
    Connecting,
    Connected,
    Streaming,
    Disconnected,
    Error
};

struct StreamInfo {
    std::string sourceName;
    std::string sourceModel;
    int videoWidth = 0;
    int videoHeight = 0;
    int videoFps = 0;
    int audioSampleRate = 44100;
    int audioChannels = 2;
    bool isMirroring = false;
    bool isPlaying = false;
};

class AirPlayEngine {
public:
    static AirPlayEngine& instance();

    ConnectionState state() const;
    StreamInfo currentStream() const;

    bool start(const std::string& deviceName, int port = 7000);
    void stop();

    void setVideoSurface(long surface);
    void setVolume(float volume);
    void setPin(const std::string& pin);

    void onStreamStarted(const std::function<void(const StreamInfo&)>& callback);
    void onStreamStopped(const std::function<void()>& callback);
    void onStateChanged(const std::function<void(ConnectionState)>& callback);
    void onError(const std::function<void(const std::string&)>& callback);

private:
    AirPlayEngine() = default;
    AirPlayEngine(const AirPlayEngine&) = delete;
    AirPlayEngine& operator=(const AirPlayEngine&) = delete;

    std::atomic<ConnectionState> state_{ConnectionState::Idle};
    StreamInfo currentStream_;
    std::mutex mutex_;

    std::function<void(const StreamInfo&)> streamStartedCallback_;
    std::function<void()> streamStoppedCallback_;
    std::function<void(ConnectionState)> stateChangedCallback_;
    std::function<void(const std::string&)> errorCallback_;

    std::string deviceName_;
    int port_ = 7000;
    long videoSurface_ = 0;
    float volume_ = 1.0f;
};

} // namespace androplay
