#include "airplay_engine.h"

namespace castbay {

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
    // UxPlay is not linked yet. Report failure until a real server can start.
    return false;
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

} // namespace castbay
