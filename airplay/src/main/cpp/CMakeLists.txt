cmake_minimum_required(VERSION 3.22.1)
project(androplay_protocol LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(THIRD_PARTY_ROOT ${CMAKE_SOURCE_DIR}/../../../../third_party)
set(UXPLAY_ROOT ${THIRD_PARTY_ROOT}/UxPlay)
set(LIBPLIST_ROOT ${THIRD_PARTY_ROOT}/libplist)

find_package(openssl REQUIRED CONFIG)

set(LIBPLIST_SRC
    ${LIBPLIST_ROOT}/src/plist.c
    ${LIBPLIST_ROOT}/src/bplist.c
    ${LIBPLIST_ROOT}/src/xplist.c
    ${LIBPLIST_ROOT}/src/jplist.c
    ${LIBPLIST_ROOT}/src/oplist.c
    ${LIBPLIST_ROOT}/src/base64.c
    ${LIBPLIST_ROOT}/src/bytearray.c
    ${LIBPLIST_ROOT}/src/hashtable.c
    ${LIBPLIST_ROOT}/src/ptrarray.c
    ${LIBPLIST_ROOT}/src/common.c
    ${LIBPLIST_ROOT}/src/out-default.c
    ${LIBPLIST_ROOT}/src/out-limd.c
    ${LIBPLIST_ROOT}/src/out-plutil.c
    ${LIBPLIST_ROOT}/src/time64.c
    ${LIBPLIST_ROOT}/src/jsmn.c
    ${LIBPLIST_ROOT}/libcnary/node.c
    ${LIBPLIST_ROOT}/libcnary/node_list.c
)

add_library(plist STATIC ${LIBPLIST_SRC})
target_include_directories(plist PUBLIC
    ${LIBPLIST_ROOT}/include
    ${LIBPLIST_ROOT}/src
    ${LIBPLIST_ROOT}/libcnary/include
)
target_compile_definitions(plist PRIVATE
    PACKAGE_VERSION="2.6.0"
    _GNU_SOURCE
    HAVE_STRNDUP=1
)
target_compile_options(plist PRIVATE
    -Wno-implicit-function-declaration
    -Wno-int-conversion
)

# UxPlay's protocol library (lib/ only: its GStreamer renderers are replaced by MediaCodec,
# and its dns_sd/ and mdnsd/ publishers by Kotlin NsdManager via dnssd_android.c).
set(UXPLAY_LIB ${UXPLAY_ROOT}/lib)
file(GLOB UXPLAY_SRC ${UXPLAY_LIB}/*.c)
file(GLOB PLAYFAIR_SRC ${UXPLAY_LIB}/playfair/*.c)
file(GLOB LLHTTP_SRC ${UXPLAY_LIB}/llhttp/*.c)

add_library(uxplay STATIC
    ${UXPLAY_SRC}
    ${PLAYFAIR_SRC}
    ${LLHTTP_SRC}
    ${CMAKE_CURRENT_SOURCE_DIR}/dnssd_android.c
)
target_include_directories(uxplay PUBLIC
    ${UXPLAY_LIB}
    ${UXPLAY_LIB}/playfair
    ${UXPLAY_LIB}/llhttp
)
target_compile_definitions(uxplay PRIVATE
    _GNU_SOURCE
    # The bundled libplist is 2.6.0 (see PACKAGE_VERSION above); UxPlay's own build
    # detects these via pkg-config.
    PLIST_210
    PLIST_230
)
target_compile_options(uxplay PRIVATE
    -Wno-deprecated-declarations
    -Wno-pointer-sign
)
target_link_libraries(uxplay PUBLIC plist openssl::crypto log)

add_library(androplay_protocol SHARED
    jni_bridge.cpp
    video_sink.cpp
    audio_sink.cpp
)
target_include_directories(androplay_protocol PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(androplay_protocol PRIVATE uxplay log android)
