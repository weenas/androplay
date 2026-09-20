#include "airplay_engine.h"
#include <unistd.h>
#include <cstring>
#include <cstdlib>
#include <thread>

namespace androplay {

AirPlayEngine& AirPlayEngine::instance() {
    static AirPlayEngine engine;
    return engine;
}

ConnectionState AirPlayEngine::state() const {
    return state_.load();
}

StreamInfo AirPlayEngine::currentStream() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return currentStream_;
}

bool AirPlayEngine::start(const std::string& deviceName, int port) {
    std::lock_guard<std::mutex> lock(mutex_);
    deviceName_ = deviceName;
    port_ = port;
    state_.store(ConnectionState::Discovering);

    if (stateChangedCallback_) {
        stateChangedCallback_(state_.load());
    }

    // Start background thread for server operations
    // In production, this would initialize UxPlay server here
    std::thread([this]() {
        // Simulate connection establishment
        usleep(500000); // 500ms

        state_.store(ConnectionState::Connected);
        if (stateChangedCallback_) {
            stateChangedCallback_(ConnectionState::Connected);
        }

        state_.store(ConnectionState::Streaming);
        if (stateChangedCallback_) {
            stateChangedCallback_(ConnectionState::Streaming);
        }

        StreamInfo info;
        info.sourceName = "iPhone";
        info.sourceModel = "iPhone15,2";
        info.videoWidth = 1920;
        info.videoHeight = 1080;
        info.videoFps = 30;
        info.isMirroring = true;
        info.isPlaying = true;

        {
            std::lock_guard<std::mutex> lock(mutex_);
            currentStream_ = info;
        }

        if (streamStartedCallback_) {
            streamStartedCallback_(info);
        }
    }).detach();

    return true;
}

void AirPlayEngine::stop() {
    ConnectionState prev = state_.load();
    if (prev != ConnectionState::Idle) {
        state_.store(ConnectionState::Idle);
        if (stateChangedCallback_) {
            stateChangedCallback_(ConnectionState::Idle);
        }

        StreamInfo oldStream;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            oldStream = currentStream_;
            currentStream_ = StreamInfo();
        }

        if (streamStoppedCallback_) {
            streamStoppedCallback_();
        }
    }
}

void AirPlayEngine::setVideoSurface(long surface) {
    std::lock_guard<std::mutex> lock(mutex_);
    videoSurface_ = surface;
}

void AirPlayEngine::setVolume(float volume) {
    volume_ = volume;
}

void AirPlayEngine::setPin(const std::string& pin) {
    // PIN handling for authentication
}

void AirPlayEngine::onStreamStarted(const std::function<void(const StreamInfo&)>& callback) {
    streamStartedCallback_ = callback;
}

void AirPlayEngine::onStreamStopped(const std::function<void()>& callback) {
    streamStoppedCallback_ = callback;
}

void AirPlayEngine::onStateChanged(const std::function<void(ConnectionState)>& callback) {
    stateChangedCallback_ = callback;
}

void AirPlayEngine::onError(const std::function<void(const std::string&)>& callback) {
    errorCallback_ = callback;
}

} // namespace androplay
