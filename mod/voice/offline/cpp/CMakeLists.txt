# PG Voice: whisper.cpp (offline speech-to-text) for the TV box (32-bit ARM, NEON).
cmake_minimum_required(VERSION 3.22)
project(pgvoice)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=neon-vfpv4)
endif ()
add_compile_options(-O3 -ffunction-sections -fdata-sections)

add_subdirectory(${WHISPER_DIR} whisper)

add_library(pgvoice SHARED pgvoice_jni.c)
find_library(LOG_LIB log)
target_link_libraries(pgvoice whisper ${LOG_LIB})
target_link_options(pgvoice PRIVATE -Wl,--gc-sections)
