cmake_minimum_required(VERSION 3.22.1)
project(androplay_protocol LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(THIRD_PARTY_ROOT ${CMAKE_SOURCE_DIR}/../../../../third_party)
set(RPIPLAY_ROOT ${THIRD_PARTY_ROOT}/RPiPlay)
set(RPIPLAY_LIB ${RPIPLAY_ROOT}/lib)
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

# Patch raop_rtp_mirror.c without touching the submodule: raop_rtp_mirror_stop() closes
# mirror_data_sock and sets it to -1 while the mirror thread may be between select() and
# FD_ISSET(). glibc tolerates FD_ISSET(-1); bionic's FORTIFY aborts the process, which
# happened every time a sender stopped mirroring. Snapshot the fd once per iteration.
file(READ ${RPIPLAY_LIB}/raop_rtp_mirror.c RAOP_RTP_MIRROR_C)
# A function rather than a list: the snippets contain ';', which CMake lists split on.
function(patch_raop_rtp_mirror from to)
    string(FIND "${RAOP_RTP_MIRROR_C}" "${from}" at)
    if(at EQUAL -1)
        message(FATAL_ERROR "raop_rtp_mirror.c no longer matches the Android fd-race patch; update it")
    endif()
    string(REPLACE "${from}" "${to}" patched "${RAOP_RTP_MIRROR_C}")
    set(RAOP_RTP_MIRROR_C "${patched}" PARENT_SCOPE)
endfunction()
patch_raop_rtp_mirror(
    "        FD_ZERO(&rfds);\n        if (stream_fd == -1) {\n            FD_SET(raop_rtp_mirror->mirror_data_sock, &rfds);\n            nfds = raop_rtp_mirror->mirror_data_sock+1;"
    "        FD_ZERO(&rfds);\n        int data_sock = raop_rtp_mirror->mirror_data_sock;\n        if (stream_fd == -1 && data_sock == -1) break;\n        if (stream_fd == -1) {\n            FD_SET(data_sock, &rfds);\n            nfds = data_sock+1;")
patch_raop_rtp_mirror(
    "if (stream_fd == -1 && FD_ISSET(raop_rtp_mirror->mirror_data_sock, &rfds))"
    "if (stream_fd == -1 && FD_ISSET(data_sock, &rfds))")
patch_raop_rtp_mirror(
    "stream_fd = accept(raop_rtp_mirror->mirror_data_sock,"
    "stream_fd = accept(data_sock,")
set(RAOP_RTP_MIRROR_PATCHED ${CMAKE_CURRENT_BINARY_DIR}/patched/raop_rtp_mirror.c)
file(WRITE ${RAOP_RTP_MIRROR_PATCHED} "${RAOP_RTP_MIRROR_C}")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${RPIPLAY_LIB}/raop_rtp_mirror.c)

file(GLOB RPIPLAY_SRC ${RPIPLAY_LIB}/*.c)
list(FILTER RPIPLAY_SRC EXCLUDE REGEX ".*/(dnssd|raop_rtp_mirror)\\.c$")
list(APPEND RPIPLAY_SRC ${RAOP_RTP_MIRROR_PATCHED})
file(GLOB PLAYFAIR_SRC
    ${RPIPLAY_LIB}/playfair/*.c
    ${RPIPLAY_LIB}/playfair/*.cpp
)
file(GLOB LLHTTP_SRC ${RPIPLAY_LIB}/llhttp/*.c)

add_library(rpiplay STATIC
    ${RPIPLAY_SRC}
    ${PLAYFAIR_SRC}
    ${LLHTTP_SRC}
    ${CMAKE_CURRENT_SOURCE_DIR}/dnssd_stub.c
)
target_include_directories(rpiplay PUBLIC
    ${RPIPLAY_LIB}
    ${RPIPLAY_LIB}/playfair
    ${RPIPLAY_LIB}/llhttp
)
target_compile_definitions(rpiplay PRIVATE
    _GNU_SOURCE
    OPENSSL_API_COMPAT=0x10101000L
)
target_compile_options(rpiplay PRIVATE
    -Wno-deprecated-declarations
    -Wno-pointer-sign
    -Wno-implicit-function-declaration
)
target_link_libraries(rpiplay PUBLIC plist openssl::crypto)

add_library(androplay_protocol SHARED
    jni_bridge.cpp
    video_sink.cpp
    audio_sink.cpp
)
target_include_directories(androplay_protocol PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(androplay_protocol PRIVATE rpiplay log android)
