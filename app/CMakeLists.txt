# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only

cmake_minimum_required(VERSION 3.6)

project(WizeFiles C CXX)
include(FetchContent)

add_library(hiddenapi SHARED
        src/main/jni/hiddenapi.c)
target_compile_options(hiddenapi
        PRIVATE
        -Wall
        -Werror)

find_library(LOG_LIBRARY log)

add_library(selinuxbridge SHARED
        src/main/jni/selinux_bridge.c)
target_compile_options(selinuxbridge
        PRIVATE
        -Wall
        -Werror)
find_library(DL_LIBRARY dl)
target_link_libraries(selinuxbridge
        PRIVATE
        ${LOG_LIBRARY}
        ${DL_LIBRARY})

add_library(syscall SHARED
        src/main/jni/syscall.c)
target_compile_options(syscall
        PRIVATE
        -Wall
        -Werror)
target_link_libraries(syscall
        PRIVATE
        ${LOG_LIBRARY})

add_library(fastops SHARED
        src/main/jni/fastops.c)
target_compile_options(fastops
        PRIVATE
        -Wall
        -Werror)
target_link_libraries(fastops
        PRIVATE
        ${LOG_LIBRARY})

add_library(archive-jni SHARED
        src/main/cpp/libarchive_jni/archive_jni.c)
target_compile_options(archive-jni
        PRIVATE
        -Wall
        -Werror)

set(ENABLE_TEST OFF CACHE BOOL "" FORCE)
set(ENABLE_CPIO OFF CACHE BOOL "" FORCE)
set(ENABLE_CAT OFF CACHE BOOL "" FORCE)
set(ENABLE_TAR OFF CACHE BOOL "" FORCE)
set(ENABLE_XATTR OFF CACHE BOOL "" FORCE)
set(ENABLE_ACL OFF CACHE BOOL "" FORCE)
set(ENABLE_BZip2 OFF CACHE BOOL "" FORCE)
set(ENABLE_EXPAT OFF CACHE BOOL "" FORCE)
set(ENABLE_ICONV OFF CACHE BOOL "" FORCE)
set(ENABLE_LIBB2 OFF CACHE BOOL "" FORCE)
set(ENABLE_LIBXML2 OFF CACHE BOOL "" FORCE)
set(ENABLE_LZ4 OFF CACHE BOOL "" FORCE)
set(ENABLE_LZMA OFF CACHE BOOL "" FORCE)
set(ENABLE_LZO OFF CACHE BOOL "" FORCE)
set(ENABLE_MBEDTLS OFF CACHE BOOL "" FORCE)
set(ENABLE_NETTLE OFF CACHE BOOL "" FORCE)
set(ENABLE_OPENSSL OFF CACHE BOOL "" FORCE)
set(ENABLE_PCSTAT OFF CACHE BOOL "" FORCE)
set(ENABLE_ZSTD OFF CACHE BOOL "" FORCE)
set(ENABLE_WERROR OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

FetchContent_Declare(
        libarchive
        URL https://github.com/libarchive/libarchive/releases/download/v3.8.9/libarchive-3.8.9.tar.gz
        URL_HASH SHA256=f5a6539059cf5e597dbeda37bfa4874b1e8dea063c8d93bf85a2b44af90a5bd4
)
FetchContent_MakeAvailable(libarchive)

FetchContent_Declare(
        sevenzip
        URL https://7-zip.org/a/7z2602-src.tar.xz
        URL_HASH SHA256=cf967c98bca02a4b8b16375f441825a8e141362f14be1969bbec8e1ca0bff9dd
)
FetchContent_GetProperties(sevenzip)
if (NOT sevenzip_POPULATED)
    FetchContent_Populate(sevenzip)
endif()

find_program(SEVENZIP_MAKE_EXECUTABLE NAMES make gmake)
if (NOT SEVENZIP_MAKE_EXECUTABLE)
    message(FATAL_ERROR "A host make executable is required to build 7-Zip")
endif()
if (NOT CMAKE_C_COMPILER_TARGET)
    message(FATAL_ERROR "The Android compiler target is required to build 7-Zip")
endif()

set(SEVENZIP_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/sevenzip-format")
set(SEVENZIP_UNPREFIXED_LIBRARY "${SEVENZIP_BUILD_DIR}/7z.so")
set(SEVENZIP_LIBRARY "${SEVENZIP_BUILD_DIR}/lib7z.so")
set(SEVENZIP_TARGET_FLAGS
        "--target=${CMAKE_C_COMPILER_TARGET} --sysroot=${CMAKE_SYSROOT}")
add_custom_command(
        OUTPUT "${SEVENZIP_LIBRARY}"
        BYPRODUCTS "${SEVENZIP_UNPREFIXED_LIBRARY}"
        COMMAND "${CMAKE_COMMAND}" -E make_directory "${SEVENZIP_BUILD_DIR}"
        COMMAND "${CMAKE_COMMAND}" -E env
                "CC=${CMAKE_C_COMPILER}"
                "CXX=${CMAKE_CXX_COMPILER}"
                "${SEVENZIP_MAKE_EXECUTABLE}"
                -f makefile.gcc
                -j2
                "O=${SEVENZIP_BUILD_DIR}"
                ST_MODE=1
                "MY_ARCH=${SEVENZIP_TARGET_FLAGS}"
                "LIB2=-ldl"
                "FLAGS_FLTO="
                "CFLAGS_WARN_WALL=-Wall -Wextra"
                "LDFLAGS=-shared -fPIC -Wl,-soname,lib7z.so"
        COMMAND "${CMAKE_COMMAND}" -E copy_if_different
                "${SEVENZIP_UNPREFIXED_LIBRARY}"
                "${SEVENZIP_LIBRARY}"
        WORKING_DIRECTORY "${sevenzip_SOURCE_DIR}/CPP/7zip/Bundles/Format7zF"
        DEPENDS
                "${sevenzip_SOURCE_DIR}/CPP/7zip/Bundles/Format7zF/makefile.gcc"
                "${sevenzip_SOURCE_DIR}/CPP/7zip/Bundles/Format7zF/Arc_gcc.mak"
        VERBATIM
)
add_custom_target(sevenzip-format-build DEPENDS "${SEVENZIP_LIBRARY}")
add_library(sevenzip-format SHARED IMPORTED)
set_target_properties(sevenzip-format PROPERTIES
        IMPORTED_LOCATION "${SEVENZIP_LIBRARY}"
        IMPORTED_SONAME "lib7z.so")
add_dependencies(sevenzip-format sevenzip-format-build)

add_library(sevenzip-jni SHARED
        src/main/cpp/sevenzip_jni/sevenzip_jni.cpp)
add_dependencies(sevenzip-jni sevenzip-format-build)
target_include_directories(sevenzip-jni
        PRIVATE
        "${sevenzip_SOURCE_DIR}")
target_compile_features(sevenzip-jni PRIVATE cxx_std_17)
target_compile_options(sevenzip-jni
        PRIVATE
        -Wall
        -Wextra
        -Werror)
target_link_libraries(sevenzip-jni
        PRIVATE
        sevenzip-format
        ${DL_LIBRARY})


FetchContent_Declare(
        libmobi
        URL https://github.com/bfabiszewski/libmobi/archive/refs/tags/v0.12.tar.gz
        URL_HASH SHA256=78826d161c02ce93ff1cd62838b4d749df754f52185474b82e4093badf4689c1
)
FetchContent_GetProperties(libmobi)
if (NOT libmobi_POPULATED)
    FetchContent_Populate(libmobi)
endif()

# The internal reader reconstructs unencrypted documents only. Do not compile libmobi's DRM code.
set(USE_ENCRYPTION OFF CACHE BOOL "" FORCE)
set(USE_LIBXML2 OFF CACHE BOOL "" FORCE)
set(USE_ZLIB OFF CACHE BOOL "" FORCE)
set(USE_MINIZ ON CACHE BOOL "" FORCE)
set(USE_XMLWRITER ON CACHE BOOL "" FORCE)
set(PACKAGE_VERSION "0.12")
set(PACKAGE_VERSION_MAJOR "0")
set(PACKAGE_VERSION_MINOR "12")
add_compile_definitions(
        PACKAGE_VERSION="0.12"
        USE_MINIZ
        USE_XMLWRITER
        HAVE_UNISTD_H
        HAVE_STRDUP
        MOBI_INLINE=inline)
add_subdirectory("${libmobi_SOURCE_DIR}/src" "${libmobi_BINARY_DIR}/src" EXCLUDE_FROM_ALL)
# Android packages this dependency as libmobi.so, so its SONAME must match that filename.
set_target_properties(mobi PROPERTIES
        VERSION ""
        SOVERSION "")

add_library(mobi-jni SHARED
        src/main/cpp/mobi_jni/mobi_jni.c)
target_include_directories(mobi-jni
        PRIVATE
        "${libmobi_SOURCE_DIR}/src")
target_compile_options(mobi-jni
        PRIVATE
        -Wall
        -Wextra
        -Werror)
target_link_libraries(mobi-jni
        PRIVATE
        mobi)

target_link_libraries(archive-jni
        PRIVATE
        archive
        ${DL_LIBRARY})
