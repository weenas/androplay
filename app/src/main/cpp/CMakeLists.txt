cmake_minimum_required(VERSION 3.22.1)
project(androplay-native VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# JNI header
find_library(log-lib log)

# AndroPlay native library
add_library(androplay-native SHARED
    src/airplay_engine.cpp
    src/native_bridge.cpp
)

target_link_libraries(androplay-native
    ${log-lib}
    android
    dl
    z
)
