cmake_minimum_required(VERSION 3.22.1)
project(androplay-native VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# UxPlay library path
set(UxPlay_ROOT "${CMAKE_SOURCE_DIR}/../../native/uxplay" CACHE PATH "UxPlay root directory")

# Include directories
include_directories(
    ${UxPlay_ROOT}/lib
    ${UxPlay_ROOT}/lib/external
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

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
    ssl
    crypto
)

# Prebuilt UxPlay (if available)
if(EXISTS "${UxPlay_ROOT}/build/libuxplay.so")
    add_library(uxplay SHARED IMPORTED)
    set_target_properties(uxplay PROPERTIES IMPORTED_LOCATION
        "${UxPlay_ROOT}/build/libuxplay.so")
    target_link_libraries(androplay-native uxplay)
endif()
