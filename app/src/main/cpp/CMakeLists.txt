cmake_minimum_required(VERSION 3.22.1)
project(castbay-native VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# JNI header
find_library(log-lib log)

# CastBay native library
add_library(castbay-native SHARED
    src/airplay_engine.cpp
    src/native_bridge.cpp
)

target_link_libraries(castbay-native
    ${log-lib}
    android
    dl
    z
)
