# 映湾 CastBay: AirPlay and DLNA Receiver for Android TV

**Website: [castbay.weenas.com](https://castbay.weenas.com)** · **Download: [Releases](https://github.com/weenas/castbay/releases)** · [Privacy policy](https://castbay.weenas.com/privacy)

CastBay (Chinese: 映湾) turns an Android TV into a receiver for iPhone, iPad and Mac: AirPlay screen mirroring, music and video, plus the cast button in video and music apps (DLNA). Free, open source, no ads.

## Features

- **Screen mirroring** from iPhone, iPad and Mac, up to 60 fps; H.265 up to 4K on 4K TVs with hardware HEVC decoding.
- **Music**: a blurred-cover backdrop, optional synced lyrics (via lrclib.net) and round playback controls; AirPlay music is lossless ALAC.
- **Video casting**: apps' AirPlay video (e.g. YouTube, iQiyi) plays straight from the source, with audio track and subtitle choices.
- **DLNA**: the cast button in apps such as Bilibili, iQiyi, NetEase Cloud Music and QQ Music, from iPhone and Android phones.
- **Made for the remote**: a quick menu while playing (picture fit, playback stats, audio/subtitles), Back twice to stop, Home keeps playing.
- **Private**: optional casting password, refuse or allow a second device, no account and no data collection.
- English and Chinese.

## Requirements

- An Android TV or Google TV device on Android 8.0 (API 26) or later
- The sender on the same local network

Tested on TCL (Android 9) and Sony BRAVIA (Android 12) TVs with iPhones.

AirPlay and DLNA video are fetched by the TV itself (as on an Apple TV), so the TV must be able to reach the video source directly. For YouTube that means `googlevideo.com`; on networks where the phone only reaches it through a proxy or VPN, the TV needs one too.

## Technical Implementation

- **AirPlay Protocol**: UxPlay's `lib/` (RAOP/RTSP/RTP, pairing, FairPlay, AirPlay video with FCUP)
- **JNI Bridge**: Native C code interfaces with Android Java/Kotlin layer
- **Video Decoding**: Android MediaCodec (hardware accelerated)
- **Audio Output**: AudioTrack for low-latency audio
- **Video casting**: Media3/ExoPlayer for AirPlay (HLS) and DLNA video and music
- **DLNA**: an in-app UPnP media renderer (SSDP, AVTransport/RenderingControl, GENA events)
- **mDNS Discovery**: Android NsdManager, publishing the TXT records UxPlay builds

## Building from Source

```bash
# Clone protocol dependencies with the repository
git clone --recurse-submodules git@github.com:weenas/castbay.git
cd castbay

# Build (requires JDK 17-21, Android SDK 35, NDK 27.0.12077973, and CMake 3.22.1;
# JDK 26 breaks AGP 8.7.3's prefab step)
./gradlew assembleDebug
```

For an existing clone, initialize the pinned dependencies before building:

```bash
git submodule update --init --recursive
```

## Project Structure

```
castbay/
├── app/                    # Android application
│   ├── src/main/
│   │   ├── java/com/weenas/castbay/
│   │   │   ├── MainActivity.kt
│   │   │   ├── service/       # Service lifecycle and bridge
│   │   │   ├── ui/            # Compose UI screens
│   │   │   ├── viewmodel/     # MVVM ViewModels
│   │   │   └── receiver/      # BroadcastReceivers
│   │   ├── cpp/              # C++ native code (CMake)
│   │   └── res/              # Android resources
│   └── build.gradle.kts
├── airplay/                # Android library, JNI bridge, and native protocol build
├── third_party/            # Pinned UxPlay, libplist and ALAC submodules
├── website/                # castbay.weenas.com (Cloudflare Pages)
└── README.md
```

## License

This project is licensed under the GNU General Public License v3.0.
See the LICENSE file for details.

## Credits

- [UxPlay](https://github.com/FDH2/UxPlay) - AirPlay protocol library (mirroring, audio, HLS video)
- [RPiPlay](https://github.com/FD-/RPiPlay) - the original core UxPlay's library grew from
- [libplist](https://github.com/libimobiledevice/libplist) - binary plist support
- [Apple ALAC](https://github.com/macosforge/alac) - the ALAC decoder for AirPlay music
- [Media3 ExoPlayer](https://github.com/androidx/media) - video and DLNA playback
- [LRCLIB](https://lrclib.net) - synced lyrics
- Android Open Source Project - Base platform

## Disclaimer

This project is not affiliated with or endorsed by Apple Inc. or Google LLC.
AirPlay, iPhone, iPad and Mac are trademarks of Apple Inc.; Android TV and Google TV are trademarks of Google LLC.
